begin;

alter table public.profile_miis
  add column if not exists slot smallint not null default 1,
  add column if not exists is_active boolean not null default false;

update public.profile_miis set is_active = true;

alter table public.profile_miis
  add constraint profile_miis_slot_range check (slot between 1 and 3);

alter table public.profile_miis drop constraint profile_miis_pkey;
alter table public.profile_miis add constraint profile_miis_pkey primary key (user_id, slot);

create unique index if not exists profile_miis_single_active
  on public.profile_miis (user_id)
  where is_active;

create or replace function private.write_profile_mii(
  p_actor_id uuid,
  p_slot integer,
  p_client_operation_id uuid,
  p_revision bigint,
  p_schema_version integer,
  p_appearance jsonb,
  p_avatar_path text,
  p_canonical_miic_base64 text
)
returns public.profile_miis
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_expected_avatar_path text;
  v_canonical_miic bytea;
  v_existing public.profile_miis;
  v_row public.profile_miis;
  v_has_active boolean;
  v_updated_at timestamptz := clock_timestamp();
begin
  if p_client_operation_id is null
    or p_revision is null
    or p_schema_version is null
    or p_appearance is null
    or p_avatar_path is null
    or p_slot is null
  then
    raise exception 'Incomplete Mii publication' using errcode = '22004';
  end if;
  if p_slot not between 1 and 3 then
    raise exception 'Mii slot must be between 1 and 3' using errcode = '22023';
  end if;
  if p_revision <= 0 then
    raise exception 'Mii revision must be positive' using errcode = '22023';
  end if;
  if not private.is_sanitized_mii_appearance(p_appearance, p_schema_version) then
    raise exception 'Mii appearance is invalid or contains unsupported fields'
      using errcode = '22023';
  end if;

  v_expected_avatar_path := (
    p_actor_id::text
    || '/mii-r'
    || p_revision::text
    || '-'
    || p_client_operation_id::text
    || '.png'
  );
  if p_avatar_path <> v_expected_avatar_path then
    raise exception 'Mii avatar path does not belong to this publication'
      using errcode = '22023';
  end if;
  if not exists (
    select 1
    from storage.objects as object
    where object.bucket_id = 'avatars'
      and object.name = p_avatar_path
  ) then
    raise exception 'Mii portrait upload is missing' using errcode = '22023';
  end if;

  if p_canonical_miic_base64 is not null then
    begin
      v_canonical_miic := decode(p_canonical_miic_base64, 'base64');
    exception
      when others then
        raise exception 'Canonical Mii data is not valid base64'
          using errcode = '22023';
    end;
    if octet_length(v_canonical_miic) not between 1 and 4096 then
      raise exception 'Canonical Mii data is too large' using errcode = '22023';
    end if;
  end if;

  perform 1
  from public.profiles as profile
  where profile.user_id = p_actor_id
  for update;
  if not found then
    raise exception 'Profile is unavailable' using errcode = 'P0002';
  end if;

  select mii.* into v_existing
  from public.profile_miis as mii
  where mii.user_id = p_actor_id
    and mii.slot = p_slot
  for update;
  if found and p_revision <= v_existing.revision then
    raise exception 'Mii revision must increase' using errcode = '22023';
  end if;

  select exists (
    select 1
    from public.profile_miis as mii
    where mii.user_id = p_actor_id
      and mii.is_active
  ) into v_has_active;

  insert into public.profile_miis (
    user_id,
    slot,
    schema_version,
    appearance,
    canonical_miic,
    revision,
    avatar_path,
    client_operation_id,
    is_active,
    updated_at
  )
  values (
    p_actor_id,
    p_slot,
    p_schema_version,
    p_appearance,
    v_canonical_miic,
    p_revision,
    p_avatar_path,
    p_client_operation_id,
    coalesce(v_existing.is_active, not v_has_active),
    v_updated_at
  )
  on conflict on constraint profile_miis_pkey do update
  set
    schema_version = excluded.schema_version,
    appearance = excluded.appearance,
    canonical_miic = excluded.canonical_miic,
    revision = excluded.revision,
    avatar_path = excluded.avatar_path,
    client_operation_id = excluded.client_operation_id,
    updated_at = excluded.updated_at
  returning * into v_row;

  if v_row.is_active then
    update public.profiles as profile
    set
      avatar_path = v_row.avatar_path,
      updated_at = v_updated_at
    where profile.user_id = p_actor_id;
  end if;

  return v_row;
end;
$$;

revoke all on function private.write_profile_mii(
  uuid,
  integer,
  uuid,
  bigint,
  integer,
  jsonb,
  text,
  text
) from public, anon, authenticated;

create or replace function public.save_profile_mii_slot(
  p_client_operation_id uuid,
  p_revision bigint,
  p_schema_version integer,
  p_appearance jsonb,
  p_avatar_path text,
  p_canonical_miic_base64 text,
  p_slot integer
)
returns table (
  user_id uuid,
  slot integer,
  schema_version integer,
  revision bigint,
  avatar_path text,
  is_active boolean,
  updated_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_row public.profile_miis;
  v_is_replay boolean;
  v_replay_response jsonb;
  v_request_payload jsonb;
  v_response_payload jsonb;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  v_request_payload := jsonb_build_object(
    'slot', p_slot,
    'revision', p_revision,
    'schema_version', p_schema_version,
    'appearance_sha256', encode(
      extensions.digest(convert_to(p_appearance::text, 'UTF8'), 'sha256'),
      'hex'
    ),
    'avatar_path', p_avatar_path,
    'canonical_miic_sha256', case
      when p_canonical_miic_base64 is null then null
      else encode(
        extensions.digest(decode(p_canonical_miic_base64, 'base64'), 'sha256'),
        'hex'
      )
    end
  );

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_replay_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'save_profile_mii_slot',
    v_request_payload
  ) as operation;

  if v_is_replay then
    return query
    select
      (v_replay_response ->> 'user_id')::uuid,
      (v_replay_response ->> 'slot')::integer,
      (v_replay_response ->> 'schema_version')::integer,
      (v_replay_response ->> 'revision')::bigint,
      v_replay_response ->> 'avatar_path',
      (v_replay_response ->> 'is_active')::boolean,
      (v_replay_response ->> 'updated_at')::timestamptz;
    return;
  end if;

  v_row := private.write_profile_mii(
    v_actor_id,
    p_slot,
    p_client_operation_id,
    p_revision,
    p_schema_version,
    p_appearance,
    p_avatar_path,
    p_canonical_miic_base64
  );

  v_response_payload := jsonb_build_object(
    'user_id', v_row.user_id,
    'slot', v_row.slot,
    'schema_version', v_row.schema_version,
    'revision', v_row.revision,
    'avatar_path', v_row.avatar_path,
    'is_active', v_row.is_active,
    'updated_at', v_row.updated_at
  );
  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'save_profile_mii_slot',
    v_request_payload,
    v_response_payload
  );

  return query
  select
    v_row.user_id,
    v_row.slot::integer,
    v_row.schema_version::integer,
    v_row.revision,
    v_row.avatar_path,
    v_row.is_active,
    v_row.updated_at;
end;
$$;

revoke all on function public.save_profile_mii_slot(
  uuid,
  bigint,
  integer,
  jsonb,
  text,
  text,
  integer
) from public, anon;
grant execute on function public.save_profile_mii_slot(
  uuid,
  bigint,
  integer,
  jsonb,
  text,
  text,
  integer
) to authenticated;

create or replace function public.set_active_mii_slot(
  p_client_operation_id uuid,
  p_slot integer
)
returns table (
  user_id uuid,
  slot integer,
  avatar_path text,
  updated_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_row public.profile_miis;
  v_is_replay boolean;
  v_replay_response jsonb;
  v_request_payload jsonb;
  v_response_payload jsonb;
  v_updated_at timestamptz := clock_timestamp();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_slot is null or p_slot not between 1 and 3 then
    raise exception 'Mii slot must be between 1 and 3' using errcode = '22023';
  end if;

  v_request_payload := jsonb_build_object('slot', p_slot);

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_replay_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'set_active_mii_slot',
    v_request_payload
  ) as operation;

  if v_is_replay then
    return query
    select
      (v_replay_response ->> 'user_id')::uuid,
      (v_replay_response ->> 'slot')::integer,
      v_replay_response ->> 'avatar_path',
      (v_replay_response ->> 'updated_at')::timestamptz;
    return;
  end if;

  perform 1
  from public.profiles as profile
  where profile.user_id = v_actor_id
  for update;
  if not found then
    raise exception 'Profile is unavailable' using errcode = 'P0002';
  end if;

  select mii.* into v_row
  from public.profile_miis as mii
  where mii.user_id = v_actor_id
    and mii.slot = p_slot
  for update;
  if not found then
    raise exception 'No Mii is saved in that slot' using errcode = 'P0002';
  end if;

  update public.profile_miis as mii
  set
    is_active = false,
    updated_at = v_updated_at
  where mii.user_id = v_actor_id
    and mii.is_active
    and mii.slot <> p_slot;

  update public.profile_miis as mii
  set
    is_active = true,
    updated_at = v_updated_at
  where mii.user_id = v_actor_id
    and mii.slot = p_slot
  returning mii.* into v_row;

  update public.profiles as profile
  set
    avatar_path = v_row.avatar_path,
    updated_at = v_updated_at
  where profile.user_id = v_actor_id;

  v_response_payload := jsonb_build_object(
    'user_id', v_row.user_id,
    'slot', v_row.slot,
    'avatar_path', v_row.avatar_path,
    'updated_at', v_row.updated_at
  );
  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'set_active_mii_slot',
    v_request_payload,
    v_response_payload
  );

  return query
  select
    v_row.user_id,
    v_row.slot::integer,
    v_row.avatar_path,
    v_row.updated_at;
end;
$$;

revoke all on function public.set_active_mii_slot(uuid, integer) from public, anon;
grant execute on function public.set_active_mii_slot(uuid, integer) to authenticated;

create or replace function public.save_profile_mii(
  p_client_operation_id uuid,
  p_revision bigint,
  p_schema_version integer,
  p_appearance jsonb,
  p_avatar_path text,
  p_canonical_miic_base64 text default null
)
returns table (
  user_id uuid,
  schema_version integer,
  revision bigint,
  avatar_path text,
  updated_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_slot integer;
  v_row public.profile_miis;
  v_is_replay boolean;
  v_replay_response jsonb;
  v_request_payload jsonb;
  v_response_payload jsonb;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  v_request_payload := jsonb_build_object(
    'revision', p_revision,
    'schema_version', p_schema_version,
    'appearance_sha256', encode(
      extensions.digest(convert_to(p_appearance::text, 'UTF8'), 'sha256'),
      'hex'
    ),
    'avatar_path', p_avatar_path,
    'canonical_miic_sha256', case
      when p_canonical_miic_base64 is null then null
      else encode(
        extensions.digest(decode(p_canonical_miic_base64, 'base64'), 'sha256'),
        'hex'
      )
    end
  );

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_replay_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'save_profile_mii',
    v_request_payload
  ) as operation;

  if v_is_replay then
    return query
    select
      (v_replay_response ->> 'user_id')::uuid,
      (v_replay_response ->> 'schema_version')::integer,
      (v_replay_response ->> 'revision')::bigint,
      v_replay_response ->> 'avatar_path',
      (v_replay_response ->> 'updated_at')::timestamptz;
    return;
  end if;

  select coalesce(
    (
      select mii.slot
      from public.profile_miis as mii
      where mii.user_id = v_actor_id
        and mii.is_active
    ),
    1
  ) into v_slot;

  v_row := private.write_profile_mii(
    v_actor_id,
    v_slot,
    p_client_operation_id,
    p_revision,
    p_schema_version,
    p_appearance,
    p_avatar_path,
    p_canonical_miic_base64
  );

  v_response_payload := jsonb_build_object(
    'user_id', v_row.user_id,
    'schema_version', v_row.schema_version,
    'revision', v_row.revision,
    'avatar_path', v_row.avatar_path,
    'updated_at', v_row.updated_at
  );
  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'save_profile_mii',
    v_request_payload,
    v_response_payload
  );

  return query
  select
    v_row.user_id,
    v_row.schema_version::integer,
    v_row.revision,
    v_row.avatar_path,
    v_row.updated_at;
end;
$$;

comment on column public.profile_miis.slot is
  'Mii save slot, 1 through 3. Each account keeps at most three Miis.';
comment on column public.profile_miis.is_active is
  'Exactly one slot per account is active; its portrait is published as profiles.avatar_path.';
comment on function public.save_profile_mii(uuid, bigint, integer, jsonb, text, text) is
  'Retained slot-less entry point for clients released before Mii slots; writes the account''s active slot.';

commit;
