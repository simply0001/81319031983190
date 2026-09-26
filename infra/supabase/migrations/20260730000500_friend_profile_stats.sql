begin;

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
    );
end;
$$;

revoke all on function public.get_friend_profile_stats(uuid) from public, anon;
grant execute on function public.get_friend_profile_stats(uuid) to authenticated;

comment on function public.get_friend_profile_stats(uuid) is
  'Profile-card counters: encounter_count is nearby encounters between the caller and the subject; trophy_count is the distinct people the subject has met. Visibility follows private.can_view_profile, so blocked accounts are rejected.';

commit;
