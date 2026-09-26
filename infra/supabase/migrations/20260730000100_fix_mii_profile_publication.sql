begin;

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
  on conflict on constraint profile_miis_pkey do update
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

create or replace function private.broadcast_profile_friend_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_friendship public.friendships;
begin
  for v_friendship in
    select friendship.*
    from public.friendships as friendship
    where friendship.user_low = new.user_id
      or friendship.user_high = new.user_id
  loop
    perform realtime.broadcast_changes(
      'friends:' || v_friendship.user_low::text,
      tg_op,
      tg_op,
      tg_table_name,
      tg_table_schema,
      new,
      old
    );
    perform realtime.broadcast_changes(
      'friends:' || v_friendship.user_high::text,
      tg_op,
      tg_op,
      tg_table_name,
      tg_table_schema,
      new,
      old
    );
  end loop;
  return new;
end;
$$;

revoke all on function private.broadcast_profile_friend_change() from public;

drop trigger if exists profiles_broadcast_friend_change on public.profiles;
create trigger profiles_broadcast_friend_change
after update of display_name, bio, avatar_path, age, country_code
on public.profiles
for each row
when (
  old.display_name is distinct from new.display_name
  or old.bio is distinct from new.bio
  or old.avatar_path is distinct from new.avatar_path
  or old.age is distinct from new.age
  or old.country_code is distinct from new.country_code
)
execute function private.broadcast_profile_friend_change();

commit;
