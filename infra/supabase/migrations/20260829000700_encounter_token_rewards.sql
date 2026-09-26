begin;

create table private.encounter_token_rewards (
  encounter_id uuid primary key references public.nearby_encounters (id) on delete cascade,
  user_low uuid not null references public.profiles (user_id) on delete cascade,
  user_high uuid not null references public.profiles (user_id) on delete cascade,
  rewarded_on date not null,
  amount integer not null,
  created_at timestamptz not null default now(),
  constraint encounter_token_rewards_ordered_pair check (user_low < user_high),
  constraint encounter_token_rewards_amount_positive check (amount > 0),
  constraint encounter_token_rewards_one_per_pair_day unique (user_low, user_high, rewarded_on)
);

revoke all on table private.encounter_token_rewards from public;

comment on table private.encounter_token_rewards is 'One row per confirmed encounter that paid out tokens; the pair/day unique constraint is the daily cap and the primary key makes retries idempotent.';

create or replace function private.reward_confirmed_encounter(p_encounter_id uuid)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_encounter public.nearby_encounters;
  v_amount integer;
  v_low_name text;
  v_high_name text;
begin
  select encounter.*
  into v_encounter
  from public.nearby_encounters as encounter
  where encounter.id = p_encounter_id
    and encounter.confirmed_at is not null;

  if not found then
    return 0;
  end if;

  v_amount := case
    when exists (
      select 1
      from private.encounter_token_rewards as reward
      where reward.user_low = v_encounter.user_low
        and reward.user_high = v_encounter.user_high
    ) then 5
    else 30
  end;

  insert into private.encounter_token_rewards (
    encounter_id,
    user_low,
    user_high,
    rewarded_on,
    amount
  )
  values (
    v_encounter.id,
    v_encounter.user_low,
    v_encounter.user_high,
    (v_encounter.confirmed_at at time zone 'UTC')::date,
    v_amount
  )
  on conflict do nothing;

  if not found then
    return 0;
  end if;

  perform private.ensure_token_balance(v_encounter.user_low);
  perform private.ensure_token_balance(v_encounter.user_high);

  update public.token_balances as token_balance
  set balance = token_balance.balance + v_amount,
      updated_at = now()
  where token_balance.user_id in (v_encounter.user_low, v_encounter.user_high);

  select profile.display_name
  into v_low_name
  from public.profiles as profile
  where profile.user_id = v_encounter.user_low;

  select profile.display_name
  into v_high_name
  from public.profiles as profile
  where profile.user_id = v_encounter.user_high;

  insert into public.notifications (
    recipient_id,
    kind,
    actor_id,
    title,
    body
  )
  values
    (
      v_encounter.user_low,
      'system',
      v_encounter.user_high,
      'Tokens earned',
      '+' || v_amount || ' tokens for meeting ' || coalesce(v_high_name, 'someone')
        || case when v_amount = 5 then ' again' else '' end
    ),
    (
      v_encounter.user_high,
      'system',
      v_encounter.user_low,
      'Tokens earned',
      '+' || v_amount || ' tokens for meeting ' || coalesce(v_low_name, 'someone')
        || case when v_amount = 5 then ' again' else '' end
    );

  return v_amount;
end;
$$;

revoke all on function private.reward_confirmed_encounter(uuid) from public;

comment on function private.reward_confirmed_encounter(uuid) is 'Pays both participants of a confirmed encounter: 30 tokens the first time a pair is rewarded, 5 on later days, at most once per pair per UTC day. Returns the amount credited, 0 when nothing was paid.';

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
  on conflict on constraint nearby_receipts_pkey do nothing;

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

  if found then
    perform private.reward_confirmed_encounter(v_encounter.id);
  end if;

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

commit;
