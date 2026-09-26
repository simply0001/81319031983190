begin;

set local search_path = public, extensions;

select extensions.plan(24);

select extensions.has_table('private', 'user_clock_offsets', 'clock offsets table exists');
select extensions.has_table('private', 'weekly_recaps', 'weekly recap ledger exists');

select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.get_passing_stats(integer)', 'execute'),
  'anonymous callers cannot read passing stats'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('api_client', 'public.get_passing_stats(integer)', 'execute'),
  'connected apps cannot read passing stats'
);

select extensions.ok(
  pg_catalog.has_function_privilege('authenticated', 'public.get_passing_stats(integer)', 'execute'),
  'signed-in players can read passing stats'
);

insert into auth.users (
  instance_id,
  id,
  aud,
  role,
  email,
  encrypted_password,
  email_confirmed_at,
  raw_app_meta_data,
  raw_user_meta_data,
  created_at,
  updated_at,
  confirmation_token,
  email_change,
  email_change_token_new,
  recovery_token
)
select
  '00000000-0000-0000-0000-000000000000',
  seed.id,
  'authenticated',
  'authenticated',
  seed.email,
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}'::jsonb,
  jsonb_build_object('display_name', seed.name),
  now(),
  now(),
  '',
  '',
  '',
  ''
from (
  values
    ('99800000-0000-4000-8000-000000000001'::uuid, 'streak-one@pocketpass.test', 'Streak One'),
    ('99800000-0000-4000-8000-000000000002'::uuid, 'streak-two@pocketpass.test', 'Streak Two'),
    ('99800000-0000-4000-8000-000000000003'::uuid, 'streak-three@pocketpass.test', 'Streak Three')
) as seed(id, email, name);

update public.profiles set country_code = 'FR'
where user_id = '99800000-0000-4000-8000-000000000002';
update public.profiles set country_code = 'JP'
where user_id = '99800000-0000-4000-8000-000000000003';

-- Player one meets two (FR) and three (JP). 2026-08-30 is a Sunday. Seen from
-- UTC+2 the 28 Aug 23:30 pass lands on the 29th, so that offset gives the days
-- 18-20, 27, 29, 30; UTC gives 18-20, 27-30. The 26 Aug pass never confirmed.
insert into public.nearby_encounters (
  id, user_low, user_high, reported_by, reporter_operation_id, occurred_at, confirmed_at
)
values
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000002', '99800000-0000-4000-8000-000000000001', gen_random_uuid(), '2026-08-30 15:00:00+00', '2026-08-30 15:01:00+00'),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000003', '99800000-0000-4000-8000-000000000001', gen_random_uuid(), '2026-08-29 10:00:00+00', '2026-08-29 10:01:00+00'),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000002', '99800000-0000-4000-8000-000000000001', gen_random_uuid(), '2026-08-28 23:30:00+00', '2026-08-28 23:31:00+00'),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000003', '99800000-0000-4000-8000-000000000001', gen_random_uuid(), '2026-08-27 09:00:00+00', '2026-08-27 09:01:00+00'),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000002', '99800000-0000-4000-8000-000000000001', gen_random_uuid(), '2026-08-26 12:00:00+00', null),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000002', '99800000-0000-4000-8000-000000000001', gen_random_uuid(), '2026-08-20 12:00:00+00', '2026-08-20 12:01:00+00'),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000002', '99800000-0000-4000-8000-000000000001', gen_random_uuid(), '2026-08-19 12:00:00+00', '2026-08-19 12:01:00+00'),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000002', '99800000-0000-4000-8000-000000000001', gen_random_uuid(), '2026-08-18 12:00:00+00', '2026-08-18 12:01:00+00');

select extensions.results_eq(
  $$
    select current_streak, best_streak, week_passes, week_people, week_regions, week_start::text
    from private.passing_stats('99800000-0000-4000-8000-000000000001', 120, '2026-08-30 20:00:00+00')
  $$,
  $$ values (2, 3, 4, 2, 2, '2026-08-24') $$,
  'UTC+2 on Sunday evening: two-day streak, best of three, four passes with two people in two regions'
);

select extensions.results_eq(
  $$
    select current_streak, best_streak, week_passes, week_people, week_regions, week_start::text
    from private.passing_stats('99800000-0000-4000-8000-000000000001', 0, '2026-08-30 20:00:00+00')
  $$,
  $$ values (4, 4, 4, 2, 2, '2026-08-24') $$,
  'UTC keeps the Friday pass on Friday and joins the days into a four-day streak'
);

select extensions.results_eq(
  $$
    select current_streak, best_streak, week_passes, week_people, week_regions, week_start::text
    from private.passing_stats('99800000-0000-4000-8000-000000000001', 120, '2026-08-31 10:00:00+00')
  $$,
  $$ values (2, 3, 0, 0, 0, '2026-08-31') $$,
  'a streak that ended yesterday still counts and a new week starts empty'
);

select extensions.is(
  (
    select current_streak
    from private.passing_stats('99800000-0000-4000-8000-000000000001', 120, '2026-09-01 10:00:00+00')
  ),
  0,
  'a streak is over once a full day passes without a pass'
);

select extensions.is(
  (
    select current_streak
    from private.passing_stats('99800000-0000-4000-8000-000000000002', 120, '2026-08-30 20:00:00+00')
  ),
  2,
  'the other side of a pass earns the same streak'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select extensions.throws_ok(
  'select * from public.get_passing_stats(120)',
  '42501',
  'Authentication required',
  'a session without a subject is refused'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99800000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  'select * from public.get_passing_stats(2000)',
  '22023',
  'UTC offset is out of range',
  'an impossible offset is refused'
);

select extensions.is(
  (select count(*) from public.get_passing_stats(120)),
  1::bigint,
  'the RPC returns one row'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99800000-0000-4000-8000-000000000003', true);
select public.get_passing_stats(120);

reset role;

select extensions.is(
  (
    select utc_offset_minutes
    from private.user_clock_offsets
    where user_id = '99800000-0000-4000-8000-000000000001'
  ),
  120,
  'the RPC remembers the device offset'
);

select extensions.is(
  private.weekly_recap_body(1, 0, 5),
  'You passed 1 person this week. You''re on a 5-day streak.',
  'the recap copy handles one person and a streak'
);

select extensions.is(
  private.weekly_recap_body(12, 3, 1),
  'You passed 12 people in 3 regions this week.',
  'the recap copy handles many people and regions without a streak'
);

select extensions.is(
  private.send_weekly_recaps('2026-08-30 15:00:00+00'),
  0,
  'nothing is sent before 18:00 local time'
);

select extensions.is(
  private.send_weekly_recaps('2026-08-23 20:00:00+00'),
  1,
  'the earlier week is sent to the one player who passed someone'
);

select extensions.is(
  (
    select body
    from public.notifications
    where recipient_id = '99800000-0000-4000-8000-000000000001'
      and title = 'Your week in passes'
  ),
  'You passed 1 person in 1 region this week.',
  'the earlier week counts one person in one region and no live streak'
);

select extensions.is(
  (
    select passes
    from private.weekly_recaps
    where user_id = '99800000-0000-4000-8000-000000000003'
      and week_start = '2026-08-17'
  ),
  0,
  'a quiet week is recorded without a notification'
);

select extensions.is(
  private.send_weekly_recaps('2026-08-30 20:00:00+00'),
  2,
  'the later week reaches both players who passed someone'
);

select extensions.is(
  (
    select count(*)
    from public.notifications
    where recipient_id = '99800000-0000-4000-8000-000000000001'
      and title = 'Your week in passes'
      and body = 'You passed 2 people in 2 regions this week. You''re on a 2-day streak.'
  ),
  1::bigint,
  'the later week counts two people in two regions and the live streak'
);

select extensions.is(
  private.send_weekly_recaps('2026-08-30 21:00:00+00'),
  0,
  'a week is never sent twice'
);

select extensions.is(
  (
    select count(*)
    from public.notifications
    where recipient_id = '99800000-0000-4000-8000-000000000003'
      and title = 'Your week in passes'
  ),
  1::bigint,
  'the other side of the passes gets its own recap once'
);

select * from extensions.finish();

rollback;
