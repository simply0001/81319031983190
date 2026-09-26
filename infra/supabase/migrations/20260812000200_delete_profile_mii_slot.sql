begin;

create or replace function public.delete_profile_mii_slot(
  p_slot integer
)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_avatar_path text;
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_slot is null or p_slot not between 1 and 3 then
    raise exception 'Mii slot must be between 1 and 3' using errcode = '22023';
  end if;

  delete from public.profile_miis as mii
  where mii.user_id = v_user_id
    and mii.slot = p_slot
    and not mii.is_active
  returning mii.avatar_path into v_avatar_path;

  if v_avatar_path is null and exists (
    select 1
    from public.profile_miis as mii
    where mii.user_id = v_user_id
      and mii.slot = p_slot
  ) then
    raise exception 'The active Mii cannot be deleted' using errcode = '22023';
  end if;

  return jsonb_build_object(
    'deleted', v_avatar_path is not null,
    'avatar_path', v_avatar_path
  );
end;
$$;

revoke all on function public.delete_profile_mii_slot(integer) from public;
grant execute on function public.delete_profile_mii_slot(integer) to authenticated;

commit;
