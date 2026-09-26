begin;

drop function public.get_leaderboard();

create function public.get_leaderboard(p_scope text default 'friends')
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

  if p_scope not in ('friends', 'global') then
    raise exception 'Unknown leaderboard scope: %', p_scope
      using errcode = '22023';
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
    where p_scope = 'friends'
      and v_actor_id in (friendship.user_low, friendship.user_high)
    union
    select profile.user_id
    from public.profiles as profile
    where p_scope = 'global'
  ),
  tallies as (
    select
      subject.subject_id,
      (
        select count(*)
        from public.nearby_encounters as encounter
        where subject.subject_id in (encounter.user_low, encounter.user_high)
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
  order by tally.trophy_total desc, tally.encounter_total desc, profile.display_name asc
  limit case when p_scope = 'global' then 100 end;
end;
$$;

revoke all on function public.get_leaderboard(text) from public, anon;
grant execute on function public.get_leaderboard(text) to authenticated;

comment on function public.get_leaderboard(text) is
  'Leaderboard rows, highest trophies first. friends scope returns the caller and their accepted friends; global returns the top hundred visible players. trophy_count is the distinct people that subject has met and encounter_count is that subject''s total nearby encounters. Blocked accounts drop out through private.can_view_profile. Calls without an argument keep the historical friends behavior.';

commit;
