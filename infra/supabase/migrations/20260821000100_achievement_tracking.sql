begin;

create index if not exists messages_sender_created_idx
  on public.messages (sender_id, created_at);

create or replace function private.achievement_metrics(p_user_id uuid)
returns table (
  is_legacy boolean,
  token_balance integer,
  sent_any boolean,
  best_streak integer,
  has_friend boolean,
  confirmed_encounters integer,
  met_foreigner boolean,
  continents_met integer
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_country text;
begin
  select profile.country_code, coalesce(profile.legacy_account, false)
  into v_country, is_legacy
  from public.profiles as profile
  where profile.user_id = p_user_id;
  is_legacy := coalesce(is_legacy, false);

  select coalesce(max(balance_row.balance), 0)
  into token_balance
  from public.token_balances as balance_row
  where balance_row.user_id = p_user_id;

  sent_any := exists (
    select 1
    from public.messages as message
    where message.sender_id = p_user_id
  );

  select coalesce(max(run.length), 0)
  into best_streak
  from (
    select count(*) as length
    from (
      select
        sent.day,
        sent.day - (row_number() over (order by sent.day))::integer as bucket
      from (
        select distinct (message.created_at at time zone 'UTC')::date as day
        from public.messages as message
        where message.sender_id = p_user_id
      ) as sent
    ) as grouped
    group by grouped.bucket
  ) as run;

  has_friend := exists (
    select 1
    from public.friendships as friendship
    where p_user_id in (friendship.user_low, friendship.user_high)
  ) or exists (
    select 1
    from public.friend_requests as request
    where request.status = 'accepted'
      and p_user_id in (request.requester_id, request.addressee_id)
  );

  select count(*)::integer
  into confirmed_encounters
  from public.nearby_encounters as encounter
  where p_user_id in (encounter.user_low, encounter.user_high)
    and encounter.confirmed_at is not null;

  met_foreigner := v_country is not null and exists (
    select 1
    from public.nearby_encounters as encounter
    join public.profiles as peer
      on peer.user_id = case
        when encounter.user_low = p_user_id then encounter.user_high
        else encounter.user_low
      end
    where p_user_id in (encounter.user_low, encounter.user_high)
      and encounter.confirmed_at is not null
      and peer.country_code is not null
      and peer.country_code <> v_country
  );

  select count(distinct continent_map.continent)::integer
  into continents_met
  from public.nearby_encounters as encounter
  join public.profiles as peer
    on peer.user_id = case
      when encounter.user_low = p_user_id then encounter.user_high
      else encounter.user_low
    end
  join private.country_continents as continent_map
    on continent_map.country_code = peer.country_code
  where p_user_id in (encounter.user_low, encounter.user_high)
    and encounter.confirmed_at is not null
    and continent_map.continent <> 'Antarctica';

  return next;
end;
$$;

revoke all on function private.achievement_metrics(uuid) from public;

comment on function private.achievement_metrics(uuid) is
  'Raw tallies behind every achievement for one user, read from the authoritative tables. Shared by the achievements screen, the unlock sync and progress percentages.';

create or replace function private.sync_achievements(p_user_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  metric record;
begin
  if p_user_id is null
    or not exists (
      select 1
      from public.profiles as profile
      where profile.user_id = p_user_id
    )
  then
    return;
  end if;

  select * into metric from private.achievement_metrics(p_user_id);

  insert into public.achievement_unlocks (user_id, achievement_key)
  select p_user_id, candidate.key
  from (values
    ('day_one', metric.is_legacy),
    ('saving_up', metric.token_balance >= 500),
    ('icebreaker', metric.sent_any),
    ('streak', metric.best_streak >= 10),
    ('plus_one', metric.has_friend),
    ('first_encounter', metric.confirmed_encounters >= 1),
    ('small_world', metric.confirmed_encounters >= 10),
    ('passport_stamped', metric.met_foreigner),
    ('continental', metric.continents_met >= 6)
  ) as candidate(key, satisfied)
  where candidate.satisfied
  on conflict on constraint achievement_unlocks_pkey do nothing;
end;
$$;

revoke all on function private.sync_achievements(uuid) from public;

comment on function private.sync_achievements(uuid) is
  'Records every achievement the user currently satisfies. One-way: unlocks survive later regressions such as unfriending or spending tokens. Called by table triggers as activity happens and by get_achievements for the caller.';

create or replace function public.get_achievements()
returns table (
  achievement_key text,
  unlocked boolean,
  unlocked_at timestamptz,
  progress_percent integer
)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  metric record;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  perform private.sync_achievements(v_actor_id);
  select * into metric from private.achievement_metrics(v_actor_id);

  return query
  select
    candidate.key,
    unlock.unlocked_at is not null,
    unlock.unlocked_at,
    case
      when unlock.unlocked_at is not null then 100
      else candidate.progress
    end
  from (values
    ('day_one', 0),
    ('saving_up', least(metric.token_balance * 100 / 500, 99)),
    ('icebreaker', 0),
    ('streak', least(metric.best_streak * 100 / 10, 99)),
    ('plus_one', 0),
    ('first_encounter', 0),
    ('small_world', least(metric.confirmed_encounters * 100 / 10, 99)),
    ('passport_stamped', 0),
    ('continental', least(metric.continents_met * 100 / 6, 99)),
    ('full_set', 0),
    ('missing_piece', 0)
  ) as candidate(key, progress)
  left join public.achievement_unlocks as unlock
    on unlock.user_id = v_actor_id
    and unlock.achievement_key = candidate.key;
end;
$$;

comment on function public.get_achievements() is
  'Returns every achievement key for the caller with unlocked state and personal progress percent. Unlocks are recorded by private.sync_achievements, which table triggers run as activity happens; calling this also runs it so the screen is never behind.';

create or replace function private.sync_achievements_for_encounter()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.sync_achievements(new.user_low);
  perform private.sync_achievements(new.user_high);
  return null;
end;
$$;

create or replace function private.sync_achievements_for_message()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.sync_achievements(new.sender_id);
  return null;
end;
$$;

create or replace function private.sync_achievements_for_friendship()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.sync_achievements(new.user_low);
  perform private.sync_achievements(new.user_high);
  return null;
end;
$$;

create or replace function private.sync_achievements_for_friend_request()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.status = 'accepted' then
    perform private.sync_achievements(new.requester_id);
    perform private.sync_achievements(new.addressee_id);
  end if;
  return null;
end;
$$;

create or replace function private.sync_achievements_for_token_balance()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.sync_achievements(new.user_id);
  return null;
end;
$$;

create or replace function private.sync_achievements_for_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.sync_achievements(new.user_id);
  return null;
end;
$$;

revoke all on function private.sync_achievements_for_encounter() from public;
revoke all on function private.sync_achievements_for_message() from public;
revoke all on function private.sync_achievements_for_friendship() from public;
revoke all on function private.sync_achievements_for_friend_request() from public;
revoke all on function private.sync_achievements_for_token_balance() from public;
revoke all on function private.sync_achievements_for_profile() from public;

drop trigger if exists nearby_encounters_sync_achievements on public.nearby_encounters;
create trigger nearby_encounters_sync_achievements
after insert or update of confirmed_at on public.nearby_encounters
for each row execute function private.sync_achievements_for_encounter();

drop trigger if exists messages_sync_achievements on public.messages;
create trigger messages_sync_achievements
after insert on public.messages
for each row execute function private.sync_achievements_for_message();

drop trigger if exists friendships_sync_achievements on public.friendships;
create trigger friendships_sync_achievements
after insert on public.friendships
for each row execute function private.sync_achievements_for_friendship();

drop trigger if exists friend_requests_sync_achievements on public.friend_requests;
create trigger friend_requests_sync_achievements
after insert or update of status on public.friend_requests
for each row execute function private.sync_achievements_for_friend_request();

drop trigger if exists token_balances_sync_achievements on public.token_balances;
create trigger token_balances_sync_achievements
after insert or update of balance on public.token_balances
for each row execute function private.sync_achievements_for_token_balance();

drop trigger if exists profiles_sync_achievements on public.profiles;
create trigger profiles_sync_achievements
after update of legacy_account, country_code on public.profiles
for each row execute function private.sync_achievements_for_profile();

create or replace function public.get_leaderboard(p_scope text default 'friends')
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
          and encounter.confirmed_at is not null
      ) as encounter_total,
      (
        select count(*)
        from public.achievement_unlocks as unlock
        where unlock.user_id = subject.subject_id
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

comment on function public.get_leaderboard(text) is
  'Leaderboard rows, most achievements first. friends scope returns the caller and their accepted friends; global returns the top hundred visible players. trophy_count is the achievements that subject has unlocked and encounter_count is their encounters confirmed by both parties. Blocked accounts drop out through private.can_view_profile. Calls without an argument keep the historical friends behavior.';

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
        and encounter.confirmed_at is not null
    ),
    (
      select count(*)
      from public.achievement_unlocks as unlock
      where unlock.user_id = p_friend_user_id
    );
end;
$$;

comment on function public.get_friend_profile_stats(uuid) is
  'Profile-card counters: encounter_count is confirmed nearby encounters between the caller and the subject; trophy_count is the achievements the subject has unlocked. Visibility follows private.can_view_profile, so blocked accounts are rejected.';

update public.bingo_goals
set goal_text = 'Meet 10 people!', short_label = '10 People'
where slug = 'reach_10_trophies';

update public.bingo_goals
set goal_text = 'Meet 25 people!', short_label = '25 People'
where slug = 'reach_25_trophies';

do $$
begin
  perform private.sync_achievements(profile.user_id)
  from public.profiles as profile;
end;
$$;

commit;
