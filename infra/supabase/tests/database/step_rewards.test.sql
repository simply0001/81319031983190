begin;

set local search_path = public, extensions;

select extensions.plan(20);

select extensions.has_table(
  'private',
  'step_reward_days',
  'step reward days table exists'
);
select extensions.ok(
  not pg_catalog.has_table_privilege(
    'authenticated',
    'private.step_reward_days',
    'select'
  ),
  'clients cannot read the step ledger'
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
    '98e50000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'walker-a@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Walker A"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98e50000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'walker-b@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Walker B"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

select extensions.throws_ok(
  $$ select * from public.report_daily_steps((now() at time zone 'utc')::date, 100, 0) $$,
  '42501',
  'Authentication required',
  'reporting rejects an unauthenticated caller'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98e50000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.throws_ok(
  $$ select * from public.report_daily_steps((now() at time zone 'utc')::date, -1, 0) $$,
  '22023',
  null,
  'a negative step count is rejected'
);
select extensions.throws_ok(
  $$ select * from public.report_daily_steps((now() at time zone 'utc')::date, 100, 900) $$,
  '22023',
  null,
  'an impossible UTC offset is rejected'
);
select extensions.throws_ok(
  $$ select * from public.report_daily_steps((now() at time zone 'utc')::date - 2, 100, 0) $$,
  'PT422',
  'Step day is outside the accepted window',
  'a day older than yesterday is rejected'
);
select extensions.throws_ok(
  $$ select * from public.report_daily_steps((now() at time zone 'utc')::date + 1, 100, 0) $$,
  'PT422',
  'Step day is outside the accepted window',
  'a day in the future is rejected'
);

select extensions.is(
  (
    select report.tokens_awarded || '/' || report.tokens_credited
    from public.report_daily_steps((now() at time zone 'utc')::date, 3999, 0) as report
  ),
  '9/9',
  '3999 steps pay 9 tokens'
);

reset role;
select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '98e50000-0000-4000-8000-000000000001'
  ),
  9,
  'the balance received the 9 tokens'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98e50000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select report.tokens_awarded || '/' || report.tokens_credited
    from public.report_daily_steps((now() at time zone 'utc')::date, 3999, 0) as report
  ),
  '9/0',
  'repeating the same report pays nothing more'
);
select extensions.is(
  (
    select report.steps || '/' || report.tokens_credited
    from public.report_daily_steps((now() at time zone 'utc')::date, 1000, 0) as report
  ),
  '3999/0',
  'a lower count keeps the highest count seen'
);
select extensions.is(
  (
    select report.tokens_awarded || '/' || report.tokens_credited
    from public.report_daily_steps((now() at time zone 'utc')::date, 10000, 0) as report
  ),
  '25/16',
  '10,000 steps reach the cap and pay the remaining 16'
);

reset role;
select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.recipient_id = '98e50000-0000-4000-8000-000000000001'
      and notification.kind = 'system'
      and notification.body like 'Step goal%'
  ),
  1::bigint,
  'reaching the cap sends one notification'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98e50000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select report.tokens_awarded || '/' || report.tokens_credited
    from public.report_daily_steps((now() at time zone 'utc')::date, 12000, 0) as report
  ),
  '25/0',
  'steps beyond the cap pay nothing more'
);

reset role;
select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.recipient_id = '98e50000-0000-4000-8000-000000000001'
      and notification.body like 'Step goal%'
  ),
  1::bigint,
  'the cap notification is not repeated'
);
select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '98e50000-0000-4000-8000-000000000001'
  ),
  25,
  'the balance holds exactly the daily cap'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98e50000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select report.tokens_awarded || '/' || report.tokens_credited
    from public.report_daily_steps((now() at time zone 'utc')::date - 1, 800, 0) as report
  ),
  '2/2',
  'yesterday is still accepted as its own day'
);

reset role;
select extensions.is(
  (
    select count(*)
    from private.step_reward_days as reward_day
    where reward_day.user_id = '98e50000-0000-4000-8000-000000000001'
  ),
  2::bigint,
  'each day keeps its own ledger row'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98e50000-0000-4000-8000-000000000002',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select report.tokens_awarded || '/' || report.tokens_credited
    from public.report_daily_steps((now() at time zone 'utc')::date, 400, 0) as report
  ),
  '1/1',
  'another account has its own independent cap'
);

reset role;
select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '98e50000-0000-4000-8000-000000000002'
  ),
  1,
  'the second account received its token'
);

select * from extensions.finish();

rollback;
