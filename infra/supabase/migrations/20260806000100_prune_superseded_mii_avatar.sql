begin;

-- Saving a Mii slot uploads a new, uniquely-named portrait
-- (<account>/mii-r<revision>-<operation>.png) and repoints the slot at it.
-- The previous portrait was left orphaned in storage forever. This teaches the
-- save RPC to report the now-orphaned path so the client can delete it through
-- the storage API. The path is only reported when it is no longer referenced by
-- ANY of the caller's slots or by their profile avatar, so a portrait that is
-- still in use by another slot can never be selected for deletion.

drop function if exists public.save_profile_mii_slot(
  uuid,
  bigint,
  integer,
  jsonb,
  text,
  text,
  integer
);

create function public.save_profile_mii_slot(
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
  updated_at timestamptz,
  superseded_avatar_path text
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
  v_old_avatar_path text;
  v_superseded_avatar_path text;
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
      (v_replay_response ->> 'updated_at')::timestamptz,
      v_replay_response ->> 'superseded_avatar_path';
    return;
  end if;

  select mii.avatar_path
  into v_old_avatar_path
  from public.profile_miis as mii
  where mii.user_id = v_actor_id
    and mii.slot = p_slot;

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

  v_superseded_avatar_path := null;
  if v_old_avatar_path is not null
     and v_old_avatar_path is distinct from v_row.avatar_path
     and not exists (
       select 1
       from public.profile_miis as mii
       where mii.user_id = v_actor_id
         and mii.avatar_path = v_old_avatar_path
     )
     and not exists (
       select 1
       from public.profiles as profile
       where profile.user_id = v_actor_id
         and profile.avatar_path = v_old_avatar_path
     ) then
    v_superseded_avatar_path := v_old_avatar_path;
  end if;

  v_response_payload := jsonb_build_object(
    'user_id', v_row.user_id,
    'slot', v_row.slot,
    'schema_version', v_row.schema_version,
    'revision', v_row.revision,
    'avatar_path', v_row.avatar_path,
    'is_active', v_row.is_active,
    'updated_at', v_row.updated_at,
    'superseded_avatar_path', v_superseded_avatar_path
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
    v_row.updated_at,
    v_superseded_avatar_path;
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

commit;
