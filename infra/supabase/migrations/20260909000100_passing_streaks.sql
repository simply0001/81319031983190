begin;

-- A passing streak is the run of consecutive days on which the player met
-- someone. Days are the player's own, so the app sends its UTC offset and the
-- server keeps the latest one to know when each player's Sunday evening is.
create table private.user_clock_offsets (
  user_id uuid primary key references public.profiles (user_id) on delete cascade,
  utc_offset_minutes integer not null,
  updated_at timestamptz not null default now(),
  constraint user_clock_offsets_range check (utc_offset_minutes between -840 and 840)
);

comment on table private.user_clock_offsets is 'The UTC offset each player''s device last reported; places the day boundary for passing streaks and times the weekly recap.';

create table private.weekly_recaps (
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  week_start date not null,
  passes integer not null,
  sent_at timestamptz not null default now(),
  primary key (user_id, week_start)
);

comment on table private.weekly_recaps is 'One row per player per local week once the recap has been considered; the primary key keeps the hourly job from handling a week twice.';

create or replace function private.passing_stats(
  p_user_id uuid,
  p_utc_offset_minutes integer,
  p_now timestamptz default now()
)
returns table (
  current_streak integer,
  best_streak integer,
  week_passes integer,
  week_people integer,
  week_regions integer,
  week_start date
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_offset interval := make_interval(mins => p_utc_offset_minutes);
  v_today date := ((p_now at time zone 'UTC') + v_offset)::date;
  v_week_start date := v_today - (extract(isodow from v_today)::integer - 1);
begin
  return query
  with pass_days as (
    select
      ((encounter.occurred_at at time zone 'UTC') + v_offset)::date as local_day,
      case
        when encounter.user_low = p_user_id then encounter.user_high
        else encounter.user_low
      end as peer_id
    from public.nearby_encounters as encounter
    where p_user_id in (encounter.user_low, encounter.user_high)
      and encounter.confirmed_at is not null
      and encounter.occurred_at <= p_now
  ),
  distinct_days as (
    select distinct pd.local_day from pass_days as pd
  ),
  day_runs as (
    select
      dd.local_day,
      dd.local_day - (row_number() over (order by dd.local_day))::integer as run_key
    from distinct_days as dd
  ),
  streak_runs as (
    select max(dr.local_day) as last_day, count(*)::integer as length
    from day_runs as dr
    group by dr.run_key
  ),
  week_entries as (
    select pd.peer_id, peer.country_code
    from pass_days as pd
    join public.profiles as peer on peer.user_id = pd.peer_id
    where pd.local_day between v_week_start and v_today
  )
  select
    coalesce(
      (
        select sr.length
        from streak_runs as sr
        where sr.last_day >= v_today - 1
        order by sr.last_day desc
        limit 1
      ),
      0
    ),
    coalesce((select max(sr.length) from streak_runs as sr), 0),
    (select count(*)::integer from week_entries),
    (select count(distinct we.peer_id)::integer from week_entries as we),
    (
      select count(distinct we.country_code)::integer
      from week_entries as we
      where we.country_code is not null
    ),
    v_week_start;
end;
$$;

revoke all on function private.passing_stats(uuid, integer, timestamptz) from public;

create or replace function public.get_passing_stats(p_utc_offset_minutes integer)
returns table (
  current_streak integer,
  best_streak integer,
  week_passes integer,
  week_people integer,
  week_regions integer,
  week_start date
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_utc_offset_minutes is null or p_utc_offset_minutes not between -840 and 840 then
    raise exception 'UTC offset is out of range' using errcode = '22023';
  end if;

  insert into private.user_clock_offsets (user_id, utc_offset_minutes)
  values (v_user_id, p_utc_offset_minutes)
  on conflict (user_id) do update
    set utc_offset_minutes = excluded.utc_offset_minutes,
        updated_at = now();

  return query
  select *
  from private.passing_stats(v_user_id, p_utc_offset_minutes, now());
end;
$$;

revoke all on function public.get_passing_stats(integer) from public, anon;
grant execute on function public.get_passing_stats(integer) to authenticated;

comment on function public.get_passing_stats(integer) is 'The caller''s passing streak (consecutive local days with a confirmed encounter; yesterday still counts until today ends), the best streak, and this week''s passes, people and regions, with day boundaries taken from the supplied UTC offset. Remembers the offset for the weekly recap.';

create or replace function private.weekly_recap_body(
  p_people integer,
  p_regions integer,
  p_streak integer
)
returns text
language sql
immutable
set search_path = ''
as $$
  select
    'You passed '
    || case when p_people = 1 then '1 person' else p_people || ' people' end
    || case
         when p_regions = 1 then ' in 1 region'
         when p_regions > 1 then ' in ' || p_regions || ' regions'
         else ''
       end
    || ' this week.'
    || case
         when p_streak >= 2 then ' You''re on a ' || p_streak || '-day streak.'
         else ''
       end;
$$;

revoke all on function private.weekly_recap_body(integer, integer, integer) from public;

create or replace function private.send_weekly_recaps(p_now timestamptz default now())
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_device record;
  v_local timestamp;
  v_week_start date;
  v_stats record;
  v_sent integer := 0;
begin
  for v_device in
    select device.user_id, device.utc_offset_minutes
    from private.user_clock_offsets as device
  loop
    v_local := (p_now at time zone 'UTC') + make_interval(mins => v_device.utc_offset_minutes);
    if extract(isodow from v_local) <> 7 or v_local::time < time '18:00' then
      continue;
    end if;

    v_week_start := v_local::date - 6;
    if exists (
      select 1
      from private.weekly_recaps as recap
      where recap.user_id = v_device.user_id
        and recap.week_start = v_week_start
    ) then
      continue;
    end if;

    select *
    into v_stats
    from private.passing_stats(v_device.user_id, v_device.utc_offset_minutes, p_now);

    insert into private.weekly_recaps (user_id, week_start, passes)
    values (v_device.user_id, v_week_start, v_stats.week_passes);

    if v_stats.week_passes = 0 then
      continue;
    end if;

    insert into public.notifications (recipient_id, kind, title, body)
    values (
      v_device.user_id,
      'system',
      'Your week in passes',
      private.weekly_recap_body(v_stats.week_people, v_stats.week_regions, v_stats.current_streak)
    );
    v_sent := v_sent + 1;
  end loop;

  return v_sent;
end;
$$;

revoke all on function private.send_weekly_recaps(timestamptz) from public;

comment on function private.send_weekly_recaps(timestamptz) is 'Hourly job: once a player''s local clock passes 18:00 on Sunday, records the week and sends one system notification with the week''s passes, people and regions. Weeks without a pass are recorded silently.';

do $schedule$
begin
  if exists (
    select 1 from pg_available_extensions where name = 'pg_cron'
  ) then
    create extension if not exists pg_cron;
    if not exists (
      select 1 from cron.job where jobname = 'pocketpass-weekly-recap'
    ) then
      perform cron.schedule(
        'pocketpass-weekly-recap',
        '5 * * * *',
        'select private.send_weekly_recaps();'
      );
    end if;
  end if;
end;
$schedule$;

commit;
