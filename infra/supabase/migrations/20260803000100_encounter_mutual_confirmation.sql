begin;

alter table public.nearby_encounters
  add column if not exists confirmed_at timestamptz;

create index if not exists nearby_encounters_confirmed_low_idx
  on public.nearby_encounters (user_low)
  where confirmed_at is not null;

create index if not exists nearby_encounters_confirmed_high_idx
  on public.nearby_encounters (user_high)
  where confirmed_at is not null;

update public.nearby_encounters
set confirmed_at = created_at
where confirmed_at is null;

alter table private.nearby_receipts
  drop constraint nearby_receipts_pkey;

alter table private.nearby_receipts
  add constraint nearby_receipts_pkey primary key (encounter_id, reporter_id);

create or replace function public.submit_nearby_encounter(
  p_encounter_id uuid,
  p_reporter_operation_id uuid,
  p_own_token uuid,
  p_peer_token uuid,
  p_own_signing_public_key text,
  p_peer_signing_public_key text,
  p_transcript_hash text,
  p_own_signature text,
  p_peer_signature text,
  p_occurred_at timestamptz
)
returns table (
  encounter_id uuid,
  remote_user_id uuid,
  display_name text,
  bio text,
  avatar_path text,
  age smallint,
  country_code text,
  location_label text,
  last_seen_at timestamptz,
  profile_updated_at timestamptz,
  occurred_at timestamptz,
  resolved_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_reporter uuid := auth.uid();
  v_peer uuid;
  v_own_credential private.nearby_credentials;
  v_peer_credential private.nearby_credentials;
  v_low uuid;
  v_high uuid;
  v_existing public.nearby_encounters;
  v_encounter public.nearby_encounters;
  v_peer_name text;
  v_reporter_name text;
  v_created boolean := false;
begin
  if v_reporter is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_encounter_id is null
    or p_reporter_operation_id is null
    or p_own_token is null
    or p_peer_token is null
    or p_own_signing_public_key is null
    or p_peer_signing_public_key is null
    or p_transcript_hash is null
    or p_own_signature is null
    or p_peer_signature is null
    or p_occurred_at is null
  then
    raise exception 'Encounter receipt is incomplete' using errcode = '22004';
  end if;

  if p_occurred_at < now() - interval '30 days'
    or p_occurred_at > now() + interval '5 minutes'
  then
    raise exception 'Encounter time is outside the accepted window' using errcode = '22023';
  end if;

  if (
    select count(*)
    from private.nearby_receipts as receipt
    where receipt.reporter_id = v_reporter
      and receipt.created_at >= now() - interval '1 hour'
  ) >= 120 then
    raise exception 'Encounter receipt rate limit reached' using errcode = 'PT429';
  end if;

  select credential.*
  into v_own_credential
  from private.nearby_credentials as credential
  where credential.token = p_own_token
    and credential.owner_id = v_reporter;

  select credential.*
  into v_peer_credential
  from private.nearby_credentials as credential
  where credential.token = p_peer_token;

  if v_own_credential.token is null
    or v_peer_credential.token is null
    or v_own_credential.owner_id = v_peer_credential.owner_id
    or v_own_credential.signing_public_key <> p_own_signing_public_key
    or v_peer_credential.signing_public_key <> p_peer_signing_public_key
    or p_occurred_at < v_own_credential.created_at - interval '5 minutes'
    or p_occurred_at < v_peer_credential.created_at - interval '5 minutes'
    or p_occurred_at > v_own_credential.expires_at
    or p_occurred_at > v_peer_credential.expires_at
    or char_length(p_transcript_hash) not between 40 and 128
    or char_length(p_own_signature) not between 64 and 512
    or char_length(p_peer_signature) not between 64 and 512
  then
    raise exception 'Encounter receipt is invalid' using errcode = '22023';
  end if;

  v_peer := v_peer_credential.owner_id;
  if private.has_block_between(v_reporter, v_peer) then
    raise exception 'Encounter is unavailable' using errcode = '42501';
  end if;

  v_low := least(v_reporter, v_peer);
  v_high := greatest(v_reporter, v_peer);
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_low::text || ':' || v_high::text, 0)
  );

  select encounter.*
  into v_existing
  from public.nearby_encounters as encounter
  where encounter.reported_by = v_reporter
    and encounter.reporter_operation_id = p_reporter_operation_id
    and encounter.user_low = v_low
    and encounter.user_high = v_high;

  if not found then
    select encounter.*
    into v_existing
    from public.nearby_encounters as encounter
    where encounter.user_low = v_low
      and encounter.user_high = v_high
      and encounter.occurred_at >= p_occurred_at - interval '24 hours'
      and encounter.occurred_at <= p_occurred_at + interval '24 hours'
    order by encounter.occurred_at desc
    limit 1;
  end if;

  if found then
    v_encounter := v_existing;
  else
    if v_own_credential.consumed_at is not null
      or v_peer_credential.consumed_at is not null
    then
      raise exception 'Encounter receipt is invalid' using errcode = '22023';
    end if;

    insert into public.nearby_encounters (
      id,
      user_low,
      user_high,
      reported_by,
      reporter_operation_id,
      occurred_at
    )
    values (
      p_encounter_id,
      v_low,
      v_high,
      v_reporter,
      p_reporter_operation_id,
      p_occurred_at
    )
    returning * into v_encounter;

    v_created := true;

    update private.nearby_credentials as credential
    set consumed_at = now()
    where credential.token in (p_own_token, p_peer_token)
      and credential.consumed_at is null;
  end if;

  insert into private.nearby_receipts (
    encounter_id,
    reporter_id,
    own_token,
    peer_token,
    transcript_hash,
    own_signature,
    peer_signature
  )
  values (
    v_encounter.id,
    v_reporter,
    p_own_token,
    p_peer_token,
    p_transcript_hash,
    p_own_signature,
    p_peer_signature
  )
  on conflict (encounter_id, reporter_id) do nothing;

  update public.nearby_encounters as encounter
  set confirmed_at = now()
  where encounter.id = v_encounter.id
    and encounter.confirmed_at is null
    and exists (
      select 1
      from private.nearby_receipts as low_receipt
      join private.nearby_receipts as high_receipt
        on high_receipt.encounter_id = low_receipt.encounter_id
        and high_receipt.transcript_hash = low_receipt.transcript_hash
      where low_receipt.encounter_id = encounter.id
        and low_receipt.reporter_id = encounter.user_low
        and high_receipt.reporter_id = encounter.user_high
    );

  if v_created then
    select profile.display_name
    into v_peer_name
    from public.profiles as profile
    where profile.user_id = v_peer;

    select profile.display_name
    into v_reporter_name
    from public.profiles as profile
    where profile.user_id = v_reporter;

    insert into public.notifications (
      recipient_id,
      kind,
      actor_id,
      title,
      body
    )
    values
      (
        v_reporter,
        'nearby_encounter',
        v_peer,
        'Nearby encounter',
        coalesce(v_peer_name, 'Someone') || ' passed nearby'
      ),
      (
        v_peer,
        'nearby_encounter',
        v_reporter,
        'Nearby encounter',
        coalesce(v_reporter_name, 'Someone') || ' passed nearby'
      );

    insert into public.interaction_events (
      actor_id,
      subject_user_id,
      event_type,
      client_operation_id,
      payload,
      occurred_at
    )
    values
      (
        v_reporter,
        v_peer,
        'nearby_encounter',
        p_reporter_operation_id,
        pg_catalog.jsonb_build_object('encounter_id', v_encounter.id),
        p_occurred_at
      ),
      (
        v_peer,
        v_reporter,
        'nearby_encounter',
        gen_random_uuid(),
        pg_catalog.jsonb_build_object('encounter_id', v_encounter.id),
        p_occurred_at
      );
  end if;

  return query
  select
    v_encounter.id,
    profile.user_id,
    profile.display_name,
    profile.bio,
    profile.avatar_path,
    profile.age,
    profile.country_code,
    null::text,
    profile.last_seen_at,
    profile.updated_at,
    v_encounter.occurred_at,
    v_encounter.created_at
  from public.profiles as profile
  where profile.user_id = v_peer;
end;
$$;

comment on function public.submit_nearby_encounter(
  uuid,
  uuid,
  uuid,
  uuid,
  text,
  text,
  text,
  text,
  text,
  timestamptz
) is
  'Records one party''s receipt for a nearby encounter. Both parties compute an identical transcript hash, so an encounter only becomes confirmed once user_low and user_high have each filed a receipt sharing that hash. Signatures cannot be verified in Postgres, so mutual confirmation is what stops a caller from claiming an encounter with someone whose credential token they merely observed.';

create or replace function public.get_leaderboard()
returns table (
  user_id uuid,
  display_name text,
  avatar_path text,
  trophy_count bigint,
  encounter_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  return query
  with subjects as (
    select v_actor_id as subject_id
    union
    select
      case
        when friendship.user_low = v_actor_id then friendship.user_high
        else friendship.user_low
      end
    from public.friendships as friendship
    where v_actor_id in (friendship.user_low, friendship.user_high)
  ),
  tallies as (
    select
      subject.subject_id,
      (
        select count(*)
        from public.nearby_encounters as encounter
        where subject.subject_id in (encounter.user_low, encounter.user_high)
          and encounter.confirmed_at is not null
      ) as encounter_total,
      (
        select count(
          distinct case
            when encounter.user_low = subject.subject_id then encounter.user_high
            else encounter.user_low
          end
        )
        from public.nearby_encounters as encounter
        where subject.subject_id in (encounter.user_low, encounter.user_high)
          and encounter.confirmed_at is not null
      ) as trophy_total
    from subjects as subject
    where private.can_view_profile(v_actor_id, subject.subject_id)
  )
  select
    profile.user_id,
    profile.display_name,
    profile.avatar_path,
    tally.trophy_total,
    tally.encounter_total
  from tallies as tally
  join public.profiles as profile on profile.user_id = tally.subject_id
  order by tally.trophy_total desc, tally.encounter_total desc, profile.display_name asc;
end;
$$;

comment on function public.get_leaderboard() is
  'Leaderboard rows for the caller and their accepted friends, highest trophies first. trophy_count is the distinct people that subject has met and encounter_count is that subject''s total nearby encounters, so every row measures the same thing. Only encounters confirmed by both parties count. Blocked accounts drop out through private.can_view_profile.';

create or replace function public.get_friend_profile_stats(p_friend_user_id uuid)
returns table (
  friend_user_id uuid,
  encounter_count bigint,
  trophy_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_friend_user_id is null then
    raise exception 'A profile is required' using errcode = '22004';
  end if;
  if not private.can_view_profile(v_actor_id, p_friend_user_id) then
    raise exception 'Profile is unavailable' using errcode = 'P0002';
  end if;

  return query
  select
    p_friend_user_id,
    (
      select count(*)
      from public.nearby_encounters as encounter
      where encounter.user_low = least(v_actor_id, p_friend_user_id)
        and encounter.user_high = greatest(v_actor_id, p_friend_user_id)
        and encounter.confirmed_at is not null
    ),
    (
      select count(
        distinct case
          when encounter.user_low = p_friend_user_id then encounter.user_high
          else encounter.user_low
        end
      )
      from public.nearby_encounters as encounter
      where p_friend_user_id in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
    );
end;
$$;

comment on function public.get_friend_profile_stats(uuid) is
  'Profile-card counters: encounter_count is nearby encounters between the caller and the subject; trophy_count is the distinct people the subject has met. Only encounters confirmed by both parties count. Visibility follows private.can_view_profile, so blocked accounts are rejected.';

revoke update (username) on table public.profiles from authenticated;

create or replace function public.mark_notification_read(
  p_notification_id uuid,
  p_read_at timestamptz default now()
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  update public.notifications
  set read_at = coalesce(read_at, least(coalesce(p_read_at, now()), now()))
  where id = p_notification_id
    and recipient_id = auth.uid()
    and deleted_at is null;
end;
$$;

create or replace function public.mark_all_notifications_read(
  p_read_at timestamptz default now()
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  update public.notifications
  set read_at = coalesce(read_at, least(coalesce(p_read_at, now()), now()))
  where recipient_id = auth.uid()
    and deleted_at is null;
end;
$$;

create or replace function public.delete_notification(
  p_notification_id uuid,
  p_deleted_at timestamptz default now()
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_notification public.notifications;
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  select notification.*
  into v_notification
  from public.notifications as notification
  where notification.id = p_notification_id
    and notification.recipient_id = auth.uid()
  for update;

  if not found then
    return;
  end if;

  if v_notification.kind = 'friend_request'
    and v_notification.friend_request_status = 'pending'
  then
    raise sqlstate 'PT409' using
      message = 'respond_to_friend_request_before_delete';
  end if;

  update public.notifications
  set deleted_at = coalesce(deleted_at, least(coalesce(p_deleted_at, now()), now()))
  where id = p_notification_id;
end;
$$;

commit;
