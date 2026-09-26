begin;

set local search_path = public, extensions;

select extensions.plan(21);

select extensions.has_table(
  'private',
  'encounter_token_rewards',
  'encounter token rewards table exists'
);
select extensions.ok(
  not pg_catalog.has_table_privilege(
    'authenticated',
    'private.encounter_token_rewards',
    'select'
  ),
  'clients cannot read the reward ledger'
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
values
  (
    '00000000-0000-0000-0000-000000000000',
    '98000000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'reward-a@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Reward A"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98000000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'reward-b@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Reward B"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

create temporary table reward_test_credentials (
  owner_id uuid not null,
  token uuid not null,
  signing_public_key text not null
);
grant all on reward_test_credentials to authenticated;

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

insert into reward_test_credentials
select
  '98000000-0000-4000-8000-000000000001'::uuid,
  issued.token,
  issued.signing_public_key
from public.issue_nearby_credentials(array[repeat('A', 90)]) as issued;

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98000000-0000-4000-8000-000000000002',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

insert into reward_test_credentials
select
  '98000000-0000-4000-8000-000000000002'::uuid,
  issued.token,
  issued.signing_public_key
from public.issue_nearby_credentials(array[repeat('B', 90)]) as issued;

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.submit_nearby_encounter(
      '98100000-0000-4000-8000-000000000001',
      '98200000-0000-4000-8000-000000000001',
      (
        select token from reward_test_credentials
        where owner_id = '98000000-0000-4000-8000-000000000001'
      ),
      (
        select token from reward_test_credentials
        where owner_id = '98000000-0000-4000-8000-000000000002'
      ),
      repeat('A', 90),
      repeat('B', 90),
      repeat('H', 43),
      repeat('S', 88),
      repeat('T', 88),
      clock_timestamp()
    )
  ),
  1::bigint,
  'the first receipt resolves the peer'
);

reset role;

select extensions.is(
  (
    select count(*) from private.encounter_token_rewards
    where user_low = '98000000-0000-4000-8000-000000000001'
  ),
  0::bigint,
  'a one-sided receipt pays nothing'
);
select extensions.is(
  coalesce(
    (
      select balance from public.token_balances
      where user_id = '98000000-0000-4000-8000-000000000001'
    ),
    0
  ),
  0,
  'the reporter balance is untouched before confirmation'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98000000-0000-4000-8000-000000000002',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.submit_nearby_encounter(
      '98100000-0000-4000-8000-000000000002',
      '98200000-0000-4000-8000-000000000002',
      (
        select token from reward_test_credentials
        where owner_id = '98000000-0000-4000-8000-000000000002'
      ),
      (
        select token from reward_test_credentials
        where owner_id = '98000000-0000-4000-8000-000000000001'
      ),
      repeat('B', 90),
      repeat('A', 90),
      repeat('H', 43),
      repeat('T', 88),
      repeat('S', 88),
      clock_timestamp()
    )
  ),
  1::bigint,
  'the reciprocal receipt resolves too'
);

reset role;

select extensions.is(
  (
    select amount from private.encounter_token_rewards
    where encounter_id = '98100000-0000-4000-8000-000000000001'
  ),
  30,
  'confirming a first meeting pays 30 tokens'
);
select extensions.is(
  (
    select balance from public.token_balances
    where user_id = '98000000-0000-4000-8000-000000000001'
  ),
  30,
  'the reporter receives 30 tokens'
);
select extensions.is(
  (
    select balance from public.token_balances
    where user_id = '98000000-0000-4000-8000-000000000002'
  ),
  30,
  'the peer receives 30 tokens'
);
select extensions.is(
  (
    select body from public.notifications
    where recipient_id = '98000000-0000-4000-8000-000000000001'
      and kind = 'system'
      and title = 'Tokens earned'
      and body like '+30%'
  ),
  '+30 tokens for meeting Reward B',
  'the reporter is told about the reward'
);
select extensions.is(
  (
    select body from public.notifications
    where recipient_id = '98000000-0000-4000-8000-000000000002'
      and kind = 'system'
      and title = 'Tokens earned'
      and body like '+30%'
  ),
  '+30 tokens for meeting Reward A',
  'the peer is told about the reward'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98000000-0000-4000-8000-000000000002',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.submit_nearby_encounter(
      '98100000-0000-4000-8000-000000000002',
      '98200000-0000-4000-8000-000000000002',
      (
        select token from reward_test_credentials
        where owner_id = '98000000-0000-4000-8000-000000000002'
      ),
      (
        select token from reward_test_credentials
        where owner_id = '98000000-0000-4000-8000-000000000001'
      ),
      repeat('B', 90),
      repeat('A', 90),
      repeat('H', 43),
      repeat('T', 88),
      repeat('S', 88),
      clock_timestamp()
    )
  ),
  1::bigint,
  'a retried receipt still resolves'
);

reset role;

select extensions.is(
  (
    select balance from public.token_balances
    where user_id = '98000000-0000-4000-8000-000000000001'
  ),
  30,
  'a retried receipt does not pay again'
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
values
  (
    '98100000-0000-4000-8000-000000000003',
    '98000000-0000-4000-8000-000000000001',
    '98000000-0000-4000-8000-000000000002',
    '98000000-0000-4000-8000-000000000001',
    gen_random_uuid(),
    (date_trunc('day', now() at time zone 'utc') + interval '1 day 12 hours') at time zone 'utc',
    (date_trunc('day', now() at time zone 'utc') + interval '1 day 12 hours') at time zone 'utc'
  ),
  (
    '98100000-0000-4000-8000-000000000004',
    '98000000-0000-4000-8000-000000000001',
    '98000000-0000-4000-8000-000000000002',
    '98000000-0000-4000-8000-000000000001',
    gen_random_uuid(),
    (date_trunc('day', now() at time zone 'utc') + interval '1 day 13 hours') at time zone 'utc',
    (date_trunc('day', now() at time zone 'utc') + interval '1 day 13 hours') at time zone 'utc'
  ),
  (
    '98100000-0000-4000-8000-000000000005',
    '98000000-0000-4000-8000-000000000001',
    '98000000-0000-4000-8000-000000000002',
    '98000000-0000-4000-8000-000000000001',
    gen_random_uuid(),
    (date_trunc('day', now() at time zone 'utc') + interval '2 days 12 hours') at time zone 'utc',
    (date_trunc('day', now() at time zone 'utc') + interval '2 days 12 hours') at time zone 'utc'
  ),
  (
    '98100000-0000-4000-8000-000000000006',
    '98000000-0000-4000-8000-000000000001',
    '98000000-0000-4000-8000-000000000002',
    '98000000-0000-4000-8000-000000000001',
    gen_random_uuid(),
    (date_trunc('day', now() at time zone 'utc') + interval '2 days 13 hours') at time zone 'utc',
    null
  );

select extensions.is(
  private.reward_confirmed_encounter('98100000-0000-4000-8000-000000000003'),
  5,
  'meeting the same person on a later day pays 5 tokens'
);
select extensions.is(
  private.reward_confirmed_encounter('98100000-0000-4000-8000-000000000004'),
  0,
  'a second meeting on the same day pays nothing more'
);
select extensions.is(
  private.reward_confirmed_encounter('98100000-0000-4000-8000-000000000005'),
  5,
  'the day after pays 5 again'
);
select extensions.is(
  private.reward_confirmed_encounter('98100000-0000-4000-8000-000000000006'),
  0,
  'an unconfirmed encounter pays nothing'
);
select extensions.is(
  private.reward_confirmed_encounter('98100000-0000-4000-8000-000000000001'),
  0,
  'an already rewarded encounter pays nothing'
);
select extensions.is(
  (
    select balance from public.token_balances
    where user_id = '98000000-0000-4000-8000-000000000001'
  ),
  40,
  'the reporter balance adds up'
);
select extensions.is(
  (
    select balance from public.token_balances
    where user_id = '98000000-0000-4000-8000-000000000002'
  ),
  40,
  'the peer balance adds up'
);
select extensions.is(
  (
    select count(*) from public.notifications
    where recipient_id = '98000000-0000-4000-8000-000000000001'
      and kind = 'system'
      and title = 'Tokens earned'
      and body = '+5 tokens for meeting Reward B again'
  ),
  2::bigint,
  'repeat rewards say so'
);

select * from extensions.finish();

rollback;
