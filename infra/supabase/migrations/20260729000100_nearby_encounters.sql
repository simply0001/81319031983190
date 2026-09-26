begin;

create table private.nearby_credentials (
  token uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (user_id) on delete cascade,
  signing_public_key text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '7 days'),
  consumed_at timestamptz,
  constraint nearby_credentials_public_key_length check (
    char_length(signing_public_key) between 80 and 1024
  ),
  constraint nearby_credentials_expiry_order check (expires_at > created_at)
);

create index nearby_credentials_owner_available_idx
  on private.nearby_credentials (owner_id, expires_at)
  where consumed_at is null;

create table public.nearby_encounters (
  id uuid primary key,
  user_low uuid not null references public.profiles (user_id) on delete cascade,
  user_high uuid not null references public.profiles (user_id) on delete cascade,
  reported_by uuid not null references public.profiles (user_id) on delete cascade,
  reporter_operation_id uuid not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  constraint nearby_encounters_ordered_pair check (user_low < user_high),
  constraint nearby_encounters_reporter_participant check (
    reported_by in (user_low, user_high)
  ),
  constraint nearby_encounters_reporter_operation_unique
    unique (reported_by, reporter_operation_id)
);

create index nearby_encounters_low_time_idx
  on public.nearby_encounters (user_low, occurred_at desc);

create index nearby_encounters_high_time_idx
  on public.nearby_encounters (user_high, occurred_at desc);

create table private.nearby_receipts (
  encounter_id uuid primary key references public.nearby_encounters (id) on delete cascade,
  reporter_id uuid not null references public.profiles (user_id) on delete cascade,
  own_token uuid not null references private.nearby_credentials (token) on delete restrict,
  peer_token uuid not null references private.nearby_credentials (token) on delete restrict,
  transcript_hash text not null,
  own_signature text not null,
  peer_signature text not null,
  created_at timestamptz not null default now(),
  constraint nearby_receipts_distinct_tokens check (own_token <> peer_token),
  constraint nearby_receipts_transcript_length check (
    char_length(transcript_hash) between 40 and 128
  ),
  constraint nearby_receipts_signature_lengths check (
    char_length(own_signature) between 64 and 512
    and char_length(peer_signature) between 64 and 512
  )
);

alter table public.nearby_encounters enable row level security;

create policy nearby_encounters_select_participant
on public.nearby_encounters
for select
to authenticated
using (auth.uid() in (user_low, user_high));

revoke all on table public.nearby_encounters from anon, authenticated;
grant select on table public.nearby_encounters to authenticated;
revoke all on table private.nearby_credentials from public, anon, authenticated;
revoke all on table private.nearby_receipts from public, anon, authenticated;

alter table public.notifications
  drop constraint notifications_kind_check;

alter table public.notifications
  add constraint notifications_kind_check check (
    kind in ('friend_request', 'friend_accepted', 'message', 'system', 'nearby_encounter')
  );

alter table public.notifications
  drop constraint notifications_shape_check;

alter table public.notifications
  add constraint notifications_shape_check check (
    (kind = 'friend_request' and friend_request_id is not null)
    or (kind = 'friend_accepted' and friend_request_id is not null)
    or (kind = 'message' and conversation_id is not null)
    or (kind = 'nearby_encounter' and actor_id is not null)
    or kind = 'system'
  );

create or replace function public.issue_nearby_credentials(
  p_signing_public_keys text[]
)
returns table (
  token uuid,
  signing_public_key text,
  expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_key text;
  v_active_count integer;
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_signing_public_keys is null
    or cardinality(p_signing_public_keys) not between 1 and 32
  then
    raise exception 'Between 1 and 32 public keys are required' using errcode = '22023';
  end if;

  delete from private.nearby_credentials
  where owner_id = v_user_id
    and (
      expires_at <= now() - interval '1 day'
      or consumed_at <= now() - interval '1 day'
    );

  select count(*)
  into v_active_count
  from private.nearby_credentials
  where owner_id = v_user_id
    and consumed_at is null
    and expires_at > now();

  if v_active_count + cardinality(p_signing_public_keys) > 64 then
    raise exception 'Credential inventory limit reached' using errcode = '54000';
  end if;

  foreach v_key in array p_signing_public_keys loop
    if v_key is null or char_length(v_key) not between 80 and 1024 then
      raise exception 'Invalid signing public key' using errcode = '22023';
    end if;
    return query
    insert into private.nearby_credentials (owner_id, signing_public_key)
    values (v_user_id, v_key)
    returning
      nearby_credentials.token,
      nearby_credentials.signing_public_key,
      nearby_credentials.expires_at;
  end loop;
end;
$$;

create or replace function public.get_nearby_encounters()
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
language sql
stable
security definer
set search_path = ''
as $$
  select
    encounter.id,
    profile.user_id,
    profile.display_name,
    profile.bio,
    profile.avatar_path,
    profile.age,
    profile.country_code,
    null::text as location_label,
    profile.last_seen_at,
    profile.updated_at,
    encounter.occurred_at,
    encounter.created_at
  from public.nearby_encounters as encounter
  join public.profiles as profile
    on profile.user_id = case
      when encounter.user_low = auth.uid() then encounter.user_high
      else encounter.user_low
    end
  where auth.uid() in (encounter.user_low, encounter.user_high)
    and not private.has_block_between(auth.uid(), profile.user_id)
  order by encounter.occurred_at desc, encounter.id
  limit 500
$$;

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
    from private.nearby_receipts
    where reporter_id = v_reporter
      and created_at >= now() - interval '1 hour'
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
    and encounter.reporter_operation_id = p_reporter_operation_id;

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
    );

    update private.nearby_credentials
    set consumed_at = now()
    where token in (p_own_token, p_peer_token)
      and consumed_at is null;

    select display_name into v_peer_name
    from public.profiles where user_id = v_peer;
    select display_name into v_reporter_name
    from public.profiles where user_id = v_reporter;

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

revoke all on function public.issue_nearby_credentials(text[]) from public;
revoke all on function public.get_nearby_encounters() from public;
revoke all on function public.submit_nearby_encounter(
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
) from public;

grant execute on function public.issue_nearby_credentials(text[]) to authenticated;
grant execute on function public.get_nearby_encounters() to authenticated;
grant execute on function public.submit_nearby_encounter(
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
) to authenticated;

commit;
