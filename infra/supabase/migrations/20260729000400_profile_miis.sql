begin;

create or replace function private.mii_json_int_between(
  p_appearance jsonb,
  p_key text,
  p_minimum integer,
  p_maximum integer
)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_value_text text;
  v_value numeric;
begin
  if p_appearance is null
    or p_key is null
    or jsonb_typeof(p_appearance -> p_key) <> 'number'
  then
    return false;
  end if;

  v_value_text := p_appearance ->> p_key;
  if v_value_text !~ '^-?[0-9]+$' then
    return false;
  end if;
  v_value := v_value_text::numeric;
  return v_value between p_minimum and p_maximum;
exception
  when others then
    return false;
end;
$$;

create or replace function private.is_sanitized_mii_appearance(
  p_appearance jsonb,
  p_schema_version integer
)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_allowed_keys constant text[] := array[
    'schemaVersion',
    'gender',
    'favoriteColor',
    'build',
    'height',
    'faceType',
    'skinColor',
    'wrinklesType',
    'makeupType',
    'hairType',
    'hairColor',
    'flipHair',
    'eyeType',
    'eyeColor',
    'eyeScale',
    'eyeVerticalStretch',
    'eyeRotation',
    'eyeSpacing',
    'eyeYPosition',
    'eyebrowType',
    'eyebrowColor',
    'eyebrowScale',
    'eyebrowVerticalStretch',
    'eyebrowRotation',
    'eyebrowSpacing',
    'eyebrowYPosition',
    'noseType',
    'noseScale',
    'noseYPosition',
    'mouthType',
    'mouthColor',
    'mouthScale',
    'mouthHorizontalStretch',
    'mouthYPosition',
    'mustacheType',
    'mustacheScale',
    'mustacheYPosition',
    'beardType',
    'facialHairColor',
    'glassesType',
    'glassesColor',
    'glassesScale',
    'glassesYPosition',
    'moleEnabled',
    'moleScale',
    'moleXPosition',
    'moleYPosition',
    'extHatType',
    'extHatColor',
    'extFacePaintColor'
  ];
begin
  if p_schema_version <> 1
    or jsonb_typeof(p_appearance) <> 'object'
    or octet_length(p_appearance::text) > 16384
    or not (p_appearance ?& v_allowed_keys)
    or exists (
      select 1
      from jsonb_object_keys(p_appearance) as appearance_key(key)
      where not (appearance_key.key = any (v_allowed_keys))
    )
    or jsonb_typeof(p_appearance -> 'flipHair') <> 'boolean'
    or jsonb_typeof(p_appearance -> 'moleEnabled') <> 'boolean'
  then
    return false;
  end if;

  return
    private.mii_json_int_between(p_appearance, 'schemaVersion', 1, 1)
    and private.mii_json_int_between(p_appearance, 'gender', 0, 1)
    and private.mii_json_int_between(p_appearance, 'favoriteColor', 0, 11)
    and private.mii_json_int_between(p_appearance, 'build', 0, 127)
    and private.mii_json_int_between(p_appearance, 'height', 0, 127)
    and private.mii_json_int_between(p_appearance, 'faceType', 0, 11)
    and private.mii_json_int_between(p_appearance, 'skinColor', 0, 5)
    and private.mii_json_int_between(p_appearance, 'wrinklesType', 0, 11)
    and private.mii_json_int_between(p_appearance, 'makeupType', 0, 11)
    and private.mii_json_int_between(p_appearance, 'hairType', 0, 131)
    and private.mii_json_int_between(p_appearance, 'hairColor', 0, 7)
    and private.mii_json_int_between(p_appearance, 'eyeType', 0, 59)
    and private.mii_json_int_between(p_appearance, 'eyeColor', 0, 5)
    and private.mii_json_int_between(p_appearance, 'eyeScale', 0, 7)
    and private.mii_json_int_between(p_appearance, 'eyeVerticalStretch', 0, 6)
    and private.mii_json_int_between(p_appearance, 'eyeRotation', 0, 7)
    and private.mii_json_int_between(p_appearance, 'eyeSpacing', 0, 12)
    and private.mii_json_int_between(p_appearance, 'eyeYPosition', 0, 18)
    and private.mii_json_int_between(p_appearance, 'eyebrowType', 0, 23)
    and private.mii_json_int_between(p_appearance, 'eyebrowColor', 0, 7)
    and private.mii_json_int_between(p_appearance, 'eyebrowScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'eyebrowVerticalStretch', 0, 6)
    and private.mii_json_int_between(p_appearance, 'eyebrowRotation', 0, 11)
    and private.mii_json_int_between(p_appearance, 'eyebrowSpacing', 0, 12)
    and private.mii_json_int_between(p_appearance, 'eyebrowYPosition', 3, 18)
    and private.mii_json_int_between(p_appearance, 'noseType', 0, 17)
    and private.mii_json_int_between(p_appearance, 'noseScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'noseYPosition', 0, 18)
    and private.mii_json_int_between(p_appearance, 'mouthType', 0, 35)
    and private.mii_json_int_between(p_appearance, 'mouthColor', 0, 4)
    and private.mii_json_int_between(p_appearance, 'mouthScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'mouthHorizontalStretch', 0, 6)
    and private.mii_json_int_between(p_appearance, 'mouthYPosition', 0, 18)
    and private.mii_json_int_between(p_appearance, 'mustacheType', 0, 5)
    and private.mii_json_int_between(p_appearance, 'mustacheScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'mustacheYPosition', 0, 16)
    and private.mii_json_int_between(p_appearance, 'beardType', 0, 5)
    and private.mii_json_int_between(p_appearance, 'facialHairColor', 0, 7)
    and private.mii_json_int_between(p_appearance, 'glassesType', 0, 19)
    and private.mii_json_int_between(p_appearance, 'glassesColor', 0, 5)
    and private.mii_json_int_between(p_appearance, 'glassesScale', 0, 7)
    and private.mii_json_int_between(p_appearance, 'glassesYPosition', 0, 20)
    and private.mii_json_int_between(p_appearance, 'moleScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'moleXPosition', 0, 16)
    and private.mii_json_int_between(p_appearance, 'moleYPosition', 0, 30)
    and private.mii_json_int_between(p_appearance, 'extHatType', -1, 9)
    and private.mii_json_int_between(p_appearance, 'extHatColor', -1, 11)
    and private.mii_json_int_between(p_appearance, 'extFacePaintColor', -1, 11);
end;
$$;

create table public.profile_miis (
  user_id uuid primary key references public.profiles (user_id) on delete cascade,
  schema_version smallint not null,
  appearance jsonb not null,
  canonical_miic bytea,
  revision bigint not null,
  avatar_path text not null,
  client_operation_id uuid not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint profile_miis_schema_supported check (schema_version = 1),
  constraint profile_miis_appearance_sanitized check (
    private.is_sanitized_mii_appearance(appearance, schema_version)
  ),
  constraint profile_miis_canonical_size check (
    canonical_miic is null
    or octet_length(canonical_miic) between 1 and 4096
  ),
  constraint profile_miis_revision_positive check (revision > 0),
  constraint profile_miis_avatar_owned check (
    avatar_path = (
      user_id::text
      || '/mii-r'
      || revision::text
      || '-'
      || client_operation_id::text
      || '.png'
    )
  )
);

alter table public.profile_miis enable row level security;

create policy profile_miis_select_own
on public.profile_miis
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.profile_miis from public, anon, authenticated;
grant select on table public.profile_miis to authenticated;

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
  v_expected_avatar_path text;
  v_canonical_miic bytea;
  v_existing public.profile_miis;
  v_is_replay boolean;
  v_replay_response jsonb;
  v_request_payload jsonb;
  v_response_payload jsonb;
  v_updated_at timestamptz;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_client_operation_id is null
    or p_revision is null
    or p_schema_version is null
    or p_appearance is null
    or p_avatar_path is null
  then
    raise exception 'Incomplete Mii publication' using errcode = '22004';
  end if;
  if p_revision <= 0 then
    raise exception 'Mii revision must be positive' using errcode = '22023';
  end if;
  if not private.is_sanitized_mii_appearance(
    p_appearance,
    p_schema_version
  ) then
    raise exception 'Mii appearance is invalid or contains unsupported fields'
      using errcode = '22023';
  end if;

  v_expected_avatar_path := (
    v_actor_id::text
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

  v_request_payload := jsonb_build_object(
    'revision', p_revision,
    'schema_version', p_schema_version,
    'appearance_sha256', encode(
      extensions.digest(convert_to(p_appearance::text, 'UTF8'), 'sha256'),
      'hex'
    ),
    'avatar_path', p_avatar_path,
    'canonical_miic_sha256', case
      when v_canonical_miic is null then null
      else encode(extensions.digest(v_canonical_miic, 'sha256'), 'hex')
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

  perform 1
  from public.profiles as profile
  where profile.user_id = v_actor_id
  for update;
  if not found then
    raise exception 'Profile is unavailable' using errcode = 'P0002';
  end if;

  select mii.*
  into v_existing
  from public.profile_miis as mii
  where mii.user_id = v_actor_id
  for update;
  if found and p_revision <= v_existing.revision then
    raise exception 'Mii revision must increase' using errcode = '22023';
  end if;

  v_updated_at := clock_timestamp();
  insert into public.profile_miis (
    user_id,
    schema_version,
    appearance,
    canonical_miic,
    revision,
    avatar_path,
    client_operation_id,
    updated_at
  )
  values (
    v_actor_id,
    p_schema_version,
    p_appearance,
    v_canonical_miic,
    p_revision,
    p_avatar_path,
    p_client_operation_id,
    v_updated_at
  )
  on conflict (user_id) do update
  set
    schema_version = excluded.schema_version,
    appearance = excluded.appearance,
    canonical_miic = excluded.canonical_miic,
    revision = excluded.revision,
    avatar_path = excluded.avatar_path,
    client_operation_id = excluded.client_operation_id,
    updated_at = excluded.updated_at;

  update public.profiles as profile
  set
    avatar_path = p_avatar_path,
    updated_at = v_updated_at
  where profile.user_id = v_actor_id;

  v_response_payload := jsonb_build_object(
    'user_id', v_actor_id,
    'schema_version', p_schema_version,
    'revision', p_revision,
    'avatar_path', p_avatar_path,
    'updated_at', v_updated_at
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
    v_actor_id,
    p_schema_version,
    p_revision,
    p_avatar_path,
    v_updated_at;
end;
$$;

revoke all on function public.save_profile_mii(
  uuid,
  bigint,
  integer,
  jsonb,
  text,
  text
) from public, anon;
grant execute on function public.save_profile_mii(
  uuid,
  bigint,
  integer,
  jsonb,
  text,
  text
) to authenticated;

comment on table public.profile_miis is
  'Owner-private PocketPass Mii appearance. JSON and optional MIIC payloads must contain appearance only.';
comment on column public.profile_miis.canonical_miic is
  'Optional sanitized appearance-only MIIC bytes; never store names, creator ids, birthdays, or account metadata.';

commit;
