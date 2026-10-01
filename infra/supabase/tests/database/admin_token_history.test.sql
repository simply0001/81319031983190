begin;

set local search_path = public, extensions;

select extensions.plan(12);

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
  '',
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
    ('99510000-0000-4000-8000-000000000001'::uuid, 'history-owner@pocketpass.test', 'History Owner'),
    ('99510000-0000-4000-8000-000000000002'::uuid, 'history-viewer@pocketpass.test', 'History Viewer'),
    ('99510000-0000-4000-8000-000000000003'::uuid, 'history-target@pocketpass.test', 'History Target'),
    ('99510000-0000-4000-8000-000000000004'::uuid, 'history-friend@pocketpass.test', 'History Friend')
) as seed(id, email, name);

update public.profiles set username = 'historyfriend', display_name = 'History Friend'
where user_id = '99510000-0000-4000-8000-000000000004';

insert into private.admin_users (user_id, note, permissions, is_owner)
values
  ('99510000-0000-4000-8000-000000000001', 'test owner', '{}', true),
  ('99510000-0000-4000-8000-000000000002', 'test viewer', array['users'], false);

insert into private.step_reward_days (user_id, local_day, steps, tokens_awarded, updated_at)
values
  ('99510000-0000-4000-8000-000000000003', current_date - 2, 10400, 25, now() - interval '2 days'),
  ('99510000-0000-4000-8000-000000000003', current_date - 1, 300, 0, now() - interval '1 day');

insert into public.nearby_encounters (id, user_low, user_high, reported_by, reporter_operation_id, occurred_at)
values (
  '99520000-0000-4000-8000-000000000001',
  '99510000-0000-4000-8000-000000000003',
  '99510000-0000-4000-8000-000000000004',
  '99510000-0000-4000-8000-000000000003',
  gen_random_uuid(),
  now() - interval '3 days'
);

insert into private.encounter_token_rewards (encounter_id, user_low, user_high, rewarded_on, amount, created_at)
values (
  '99520000-0000-4000-8000-000000000001',
  '99510000-0000-4000-8000-000000000003',
  '99510000-0000-4000-8000-000000000004',
  current_date - 3,
  30,
  now() - interval '3 days'
)
on conflict do nothing;

insert into public.bingo_awards (user_id, week_key, lines_paid, blackout_paid, updated_at)
values
  ('99510000-0000-4000-8000-000000000003', '2026-38', 2, false, now() - interval '4 days'),
  ('99510000-0000-4000-8000-000000000003', '2026-39', 0, false, now() - interval '4 days');

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99510000-0000-4000-8000-000000000002', true);

select extensions.throws_ok(
  $$select public.admin_get_user_token_history('99510000-0000-4000-8000-000000000003')$$,
  '42501',
  'Permission required: tokens',
  'staff without the Tokens permission cannot read token history'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99510000-0000-4000-8000-000000000001', true);

select public.admin_adjust_tokens('99510000-0000-4000-8000-000000000003', 10, 'Contest prize', null);

select public.admin_get_user_token_history('99510000-0000-4000-8000-000000000003') as history \gset

select extensions.is((:'history'::jsonb -> 'totals' ->> 'steps')::integer, 25, 'steps earned are counted');
select extensions.is((:'history'::jsonb -> 'totals' ->> 'encounter')::integer, 30, 'encounter rewards are counted');
select extensions.is((:'history'::jsonb -> 'totals' ->> 'bingo')::integer, 50, 'bingo lines are counted');
select extensions.is((:'history'::jsonb -> 'totals' ->> 'staff')::integer, 10, 'staff changes are counted');
select extensions.is((:'history'::jsonb ->> 'entry_count')::integer, 4, 'days and weeks without tokens are left out');
select extensions.is(:'history'::jsonb -> 'entries' -> 0 ->> 'source', 'staff', 'the newest entry comes first');
select extensions.is(
  (select entry -> 'detail' ->> 'partner_username' from jsonb_array_elements(:'history'::jsonb -> 'entries') as entry where entry ->> 'source' = 'encounter'),
  'historyfriend',
  'encounter rewards name the other person'
);
select extensions.is(
  (select entry -> 'detail' ->> 'reason' from jsonb_array_elements(:'history'::jsonb -> 'entries') as entry where entry ->> 'source' = 'staff'),
  'Contest prize',
  'staff changes keep their reason'
);
select extensions.is(
  (select (entry -> 'detail' ->> 'steps')::integer from jsonb_array_elements(:'history'::jsonb -> 'entries') as entry where entry ->> 'source' = 'steps'),
  10400,
  'step entries keep the step count'
);
select extensions.is(
  jsonb_array_length(public.admin_get_user_token_history('99510000-0000-4000-8000-000000000003', 2) -> 'entries'),
  2,
  'the entry list honours the limit'
);
select extensions.throws_ok(
  $$select public.admin_get_user_token_history(null)$$,
  '22023',
  'Choose an account',
  'an account is required'
);

reset role;

select * from extensions.finish();
rollback;
