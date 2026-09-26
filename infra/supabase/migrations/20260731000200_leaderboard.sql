begin;

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
  order by tally.trophy_total desc, tally.encounter_total desc, profile.display_name asc;
end;
$$;

revoke all on function public.get_leaderboard() from public, anon;
grant execute on function public.get_leaderboard() to authenticated;

comment on function public.get_leaderboard() is
  'Leaderboard rows for the caller and their accepted friends, highest trophies first. trophy_count is the distinct people that subject has met and encounter_count is that subject''s total nearby encounters, so every row measures the same thing. Blocked accounts drop out through private.can_view_profile.';

commit;
