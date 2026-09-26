begin;

alter table public.profiles
  add column legacy_account boolean not null default false;

comment on column public.profiles.legacy_account is
  'True for accounts carried over from the old PocketPass app. Set by hand; drives the day_one achievement.';

create table private.country_continents (
  country_code text primary key,
  continent text not null,
  constraint country_continents_code_shape check (country_code ~ '^[A-Z]{2}$')
);

insert into private.country_continents (country_code, continent)
select code, continent
from (
  select unnest(array[
    'DZ','AO','BJ','BW','BF','BI','CM','CV','CF','TD','KM','CG','CD','CI','DJ','EG',
    'GQ','ER','SZ','ET','GA','GM','GH','GN','GW','KE','LS','LR','LY','MG','MW','ML',
    'MR','MU','MA','MZ','NA','NE','NG','RW','ST','SN','SC','SL','SO','ZA','SS','SD',
    'TZ','TG','TN','UG','ZM','ZW','EH','YT','RE','SH'
  ]) as code, 'Africa' as continent
  union all
  select unnest(array[
    'AF','AM','AZ','BH','BD','BT','BN','KH','CN','CY','GE','HK','IN','ID','IR','IQ',
    'IL','JP','JO','KZ','KW','KG','LA','LB','MO','MY','MV','MN','MM','NP','KP','OM',
    'PK','PS','PH','QA','SA','SG','KR','LK','SY','TW','TJ','TH','TL','TR','TM','AE',
    'UZ','VN','YE','IO'
  ]), 'Asia'
  union all
  select unnest(array[
    'AX','AL','AD','AT','BY','BE','BA','BG','HR','CZ','DK','EE','FO','FI','FR','DE',
    'GI','GR','GG','HU','IS','IE','IM','IT','JE','XK','LV','LI','LT','LU','MT','MD',
    'MC','ME','NL','MK','NO','PL','PT','RO','RU','SM','RS','SK','SI','ES','SJ','SE',
    'CH','UA','GB','VA'
  ]), 'Europe'
  union all
  select unnest(array[
    'AI','AG','AW','BS','BB','BZ','BM','BQ','CA','KY','CR','CU','CW','DM','DO','SV',
    'GL','GD','GP','GT','HT','HN','JM','MQ','MX','MS','NI','PA','PR','BL','KN','LC',
    'MF','PM','VC','SX','TT','TC','US','VG','VI'
  ]), 'North America'
  union all
  select unnest(array[
    'AR','BO','BR','CL','CO','EC','FK','GF','GY','PY','PE','SR','UY','VE'
  ]), 'South America'
  union all
  select unnest(array[
    'AS','AU','CK','FJ','PF','GU','KI','MH','FM','NR','NC','NZ','NU','NF','MP','PW',
    'PG','PN','WS','SB','TK','TO','TV','UM','VU','WF'
  ]), 'Oceania'
  union all
  select unnest(array['AQ','BV','GS','HM','TF']), 'Antarctica'
) as mapping(code, continent);

create table public.achievement_unlocks (
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  achievement_key text not null,
  unlocked_at timestamptz not null default now(),
  primary key (user_id, achievement_key),
  constraint achievement_unlocks_key_format check (achievement_key ~ '^[a-z0-9_]{1,64}$')
);

alter table public.achievement_unlocks enable row level security;

create policy achievement_unlocks_select_owner
on public.achievement_unlocks
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.achievement_unlocks from anon, authenticated;
grant select on table public.achievement_unlocks to authenticated;

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
  v_country text;
  v_legacy boolean;
  v_balance integer;
  v_sent_any boolean;
  v_best_streak integer;
  v_has_friend boolean;
  v_encounters integer;
  v_foreign boolean;
  v_continents integer;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  select profile.country_code, profile.legacy_account
  into v_country, v_legacy
  from public.profiles as profile
  where profile.user_id = v_actor_id;

  select coalesce(max(token_balance.balance), 0)
  into v_balance
  from public.token_balances as token_balance
  where token_balance.user_id = v_actor_id;

  v_sent_any := exists (
    select 1
    from public.messages as message
    where message.sender_id = v_actor_id
  );

  select coalesce(max(run.length), 0)
  into v_best_streak
  from (
    select count(*) as length
    from (
      select
        sent.day,
        sent.day - (row_number() over (order by sent.day))::integer as bucket
      from (
        select distinct (message.created_at at time zone 'UTC')::date as day
        from public.messages as message
        where message.sender_id = v_actor_id
      ) as sent
    ) as grouped
    group by grouped.bucket
  ) as run;

  v_has_friend := exists (
    select 1
    from public.friendships as friendship
    where v_actor_id in (friendship.user_low, friendship.user_high)
  ) or exists (
    select 1
    from public.friend_requests as request
    where request.status = 'accepted'
      and v_actor_id in (request.requester_id, request.addressee_id)
  );

  select count(*)::integer
  into v_encounters
  from public.nearby_encounters as encounter
  where v_actor_id in (encounter.user_low, encounter.user_high)
    and encounter.confirmed_at is not null;

  v_foreign := v_country is not null and exists (
    select 1
    from public.nearby_encounters as encounter
    join public.profiles as peer
      on peer.user_id = case
        when encounter.user_low = v_actor_id then encounter.user_high
        else encounter.user_low
      end
    where v_actor_id in (encounter.user_low, encounter.user_high)
      and encounter.confirmed_at is not null
      and peer.country_code is not null
      and peer.country_code <> v_country
  );

  select count(distinct continent_map.continent)::integer
  into v_continents
  from public.nearby_encounters as encounter
  join public.profiles as peer
    on peer.user_id = case
      when encounter.user_low = v_actor_id then encounter.user_high
      else encounter.user_low
    end
  join private.country_continents as continent_map
    on continent_map.country_code = peer.country_code
  where v_actor_id in (encounter.user_low, encounter.user_high)
    and encounter.confirmed_at is not null
    and continent_map.continent <> 'Antarctica';

  insert into public.achievement_unlocks (user_id, achievement_key)
  select v_actor_id, candidate.key
  from (values
    ('day_one', coalesce(v_legacy, false)),
    ('saving_up', v_balance >= 500),
    ('icebreaker', v_sent_any),
    ('streak', v_best_streak >= 10),
    ('plus_one', v_has_friend),
    ('first_encounter', v_encounters >= 1),
    ('small_world', v_encounters >= 10),
    ('passport_stamped', v_foreign),
    ('continental', v_continents >= 6),
    ('full_set', false),
    ('missing_piece', false)
  ) as candidate(key, satisfied)
  where candidate.satisfied
  on conflict on constraint achievement_unlocks_pkey do nothing;

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
    ('saving_up', least(v_balance * 100 / 500, 99)),
    ('icebreaker', 0),
    ('streak', least(v_best_streak * 100 / 10, 99)),
    ('plus_one', 0),
    ('first_encounter', 0),
    ('small_world', least(v_encounters * 100 / 10, 99)),
    ('passport_stamped', 0),
    ('continental', least(v_continents * 100 / 6, 99)),
    ('full_set', 0),
    ('missing_piece', 0)
  ) as candidate(key, progress)
  left join public.achievement_unlocks as unlock
    on unlock.user_id = v_actor_id
    and unlock.achievement_key = candidate.key;
end;
$$;

revoke all on function public.get_achievements() from public, anon;
grant execute on function public.get_achievements() to authenticated;

comment on function public.get_achievements() is
  'Evaluates every achievement for the caller against authoritative tables, records newly earned keys in achievement_unlocks (one-way; unlocks survive later regressions such as unfriending or spending tokens), and returns all keys with unlocked state and personal progress percent.';

commit;
