begin;

set local search_path = public, extensions;

select extensions.plan(9);

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
  '{"provider":"email","providers":["email"]}',
  jsonb_build_object('display_name', seed.name),
  now(),
  now(),
  '',
  '',
  '',
  ''
from (
  values
    ('98970000-0000-4000-8000-000000000001'::uuid, 'daily-pass-a@pocketpass.test', 'Daily A'),
    ('98970000-0000-4000-8000-000000000002'::uuid, 'daily-pass-b@pocketpass.test', 'Daily B')
) as seed(id, email, name);

insert into private.nearby_credentials (token, owner_id, signing_public_key, created_at, expires_at)
values
  (
    '98980000-0000-4000-8000-000000000001',
    '98970000-0000-4000-8000-000000000001',
    repeat('A', 90),
    now() - interval '2 days',
    now() + interval '5 days'
  ),
  (
    '98980000-0000-4000-8000-000000000002',
    '98970000-0000-4000-8000-000000000002',
    repeat('B', 90),
    now() - interval '2 days',
    now() + interval '5 days'
  );

insert into public.nearby_encounters (
  id,
  user_low,
  user_high,
  reported_by,
  reporter_operation_id,
  occurred_at,
  confirmed_at
)
values (
  '98990000-0000-4000-8000-000000000001',
  '98970000-0000-4000-8000-000000000001',
  '98970000-0000-4000-8000-000000000002',
  '98970000-0000-4000-8000-000000000001',
  gen_random_uuid(),
  (date_trunc('day', now() at time zone 'utc') - interval '30 minutes') at time zone 'utc',
  (date_trunc('day', now() at time zone 'utc') - interval '30 minutes') at time zone 'utc'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98970000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select encounter_id
    from public.submit_nearby_encounter(
      '98990000-0000-4000-8000-000000000002',
      '98960000-0000-4000-8000-000000000101',
      '98980000-0000-4000-8000-000000000001',
      '98980000-0000-4000-8000-000000000002',
      repeat('A', 90),
      repeat('B', 90),
      repeat('H', 43),
      repeat('S', 88),
      repeat('T', 88),
      (date_trunc('day', now() at time zone 'utc') + interval '30 seconds') at time zone 'utc'
    )
  ),
  '98990000-0000-4000-8000-000000000002'::uuid,
  'a pass after UTC midnight starts a new encounter even within 24 hours of the last one'
);
select extensions.is(
  (
    select encounter_id
    from public.submit_nearby_encounter(
      '98990000-0000-4000-8000-000000000003',
      '98960000-0000-4000-8000-000000000102',
      '98980000-0000-4000-8000-000000000001',
      '98980000-0000-4000-8000-000000000002',
      repeat('A', 90),
      repeat('B', 90),
      repeat('H', 43),
      repeat('S', 88),
      repeat('T', 88),
      (date_trunc('day', now() at time zone 'utc') + interval '5 minutes') at time zone 'utc'
    )
  ),
  '98990000-0000-4000-8000-000000000002'::uuid,
  'a second pass on the same UTC day folds into that day''s encounter'
);

reset role;

select extensions.is(
  (
    select count(*)
    from public.nearby_encounters
    where user_low = '98970000-0000-4000-8000-000000000001'
      and user_high = '98970000-0000-4000-8000-000000000002'
  ),
  2::bigint,
  'the pair has one encounter per UTC day'
);
select extensions.is(
  (
    select count(*)
    from public.notifications
    where kind = 'nearby_encounter'
      and recipient_id = '98970000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'the folded pass sends no second nearby notification'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98970000-0000-4000-8000-000000000002', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select encounter_id
    from public.submit_nearby_encounter(
      '98990000-0000-4000-8000-000000000004',
      '98960000-0000-4000-8000-000000000103',
      '98980000-0000-4000-8000-000000000002',
      '98980000-0000-4000-8000-000000000001',
      repeat('B', 90),
      repeat('A', 90),
      repeat('H', 43),
      repeat('T', 88),
      repeat('S', 88),
      (date_trunc('day', now() at time zone 'utc') + interval '6 minutes') at time zone 'utc'
    )
  ),
  '98990000-0000-4000-8000-000000000002'::uuid,
  'the peer''s receipt lands on the same day''s encounter'
);

reset role;

select extensions.ok(
  (
    select confirmed_at is not null
    from public.nearby_encounters
    where id = '98990000-0000-4000-8000-000000000002'
  ),
  'matching receipts confirm the day''s encounter'
);
select extensions.is(
  (
    select rewarded_on::text || '|' || amount
    from private.encounter_token_rewards
    where encounter_id = '98990000-0000-4000-8000-000000000002'
  ),
  (date_trunc('day', now() at time zone 'utc'))::date::text || '|30',
  'the reward is dated by the day the pass happened'
);

insert into public.nearby_encounters (
  id,
  user_low,
  user_high,
  reported_by,
  reporter_operation_id,
  occurred_at,
  confirmed_at
)
values (
  '98990000-0000-4000-8000-000000000005',
  '98970000-0000-4000-8000-000000000001',
  '98970000-0000-4000-8000-000000000002',
  '98970000-0000-4000-8000-000000000001',
  gen_random_uuid(),
  (date_trunc('day', now() at time zone 'utc') - interval '1 day 1 minute') at time zone 'utc',
  (date_trunc('day', now() at time zone 'utc') + interval '1 minute') at time zone 'utc'
);

select extensions.is(
  private.reward_confirmed_encounter('98990000-0000-4000-8000-000000000005'),
  5,
  'a pass confirmed after midnight still rewards the day it happened'
);
select extensions.is(
  (
    select rewarded_on
    from private.encounter_token_rewards
    where encounter_id = '98990000-0000-4000-8000-000000000005'
  ),
  (date_trunc('day', now() at time zone 'utc') - interval '2 days')::date,
  'the late confirmation is dated by its pass day, not its confirmation day'
);

select * from extensions.finish();

rollback;
