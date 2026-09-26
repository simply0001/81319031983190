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
  '{}',
  now(),
  now(),
  '',
  '',
  '',
  ''
from (
  values
    ('99200000-0000-4000-8000-000000000001'::uuid, 'abandoned-old@pocketpass.test'),
    ('99200000-0000-4000-8000-000000000002'::uuid, 'abandoned-recent@pocketpass.test'),
    ('99200000-0000-4000-8000-000000000003'::uuid, 'finished@pocketpass.test'),
    ('99200000-0000-4000-8000-000000000004'::uuid, 'abandoned-busy@pocketpass.test')
) as seed(id, email);

update public.profiles as profile
set username = 'realname'
where profile.user_id = '99200000-0000-4000-8000-000000000003';

update public.profiles as profile
set created_at = now() - interval '40 days'
where profile.user_id in (
  '99200000-0000-4000-8000-000000000001',
  '99200000-0000-4000-8000-000000000003',
  '99200000-0000-4000-8000-000000000004'
);

update public.profiles as profile
set created_at = now() - interval '10 days'
where profile.user_id = '99200000-0000-4000-8000-000000000002';

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
  '99210000-0000-4000-8000-000000000001',
  '99200000-0000-4000-8000-000000000003',
  '99200000-0000-4000-8000-000000000004',
  '99200000-0000-4000-8000-000000000004',
  extensions.gen_random_uuid(),
  now() - interval '35 days',
  now() - interval '35 days'
);

select extensions.ok(
  not pg_catalog.has_function_privilege(
    'authenticated',
    'private.prune_unfinished_signups()',
    'execute'
  ),
  'clients cannot run the signup pruner'
);

select extensions.is(
  private.prune_unfinished_signups(),
  1,
  'only the abandoned signup past thirty days is removed'
);

select extensions.is(
  (
    select count(*)
    from public.profiles as profile
    where profile.user_id = '99200000-0000-4000-8000-000000000001'
  ),
  0::bigint,
  'the abandoned signup profile is gone'
);

select extensions.is(
  (
    select count(*)
    from auth.users as account
    where account.id = '99200000-0000-4000-8000-000000000001'
  ),
  0::bigint,
  'the abandoned signup login is gone'
);

select extensions.is(
  (
    select count(*)
    from public.profiles as profile
    where profile.user_id = '99200000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'a signup still inside the thirty day window is kept'
);

select extensions.is(
  (
    select count(*)
    from public.profiles as profile
    where profile.user_id = '99200000-0000-4000-8000-000000000003'
  ),
  1::bigint,
  'an account that finished setup is kept however old it is'
);

select extensions.is(
  (
    select count(*)
    from public.profiles as profile
    where profile.user_id = '99200000-0000-4000-8000-000000000004'
  ),
  1::bigint,
  'an unfinished signup that has an encounter is left alone'
);

select extensions.is(
  (
    select count(*)
    from public.nearby_encounters as encounter
    where encounter.id = '99210000-0000-4000-8000-000000000001'
  ),
  1::bigint,
  'the other player keeps the encounter they shared with it'
);

select extensions.is(
  private.prune_unfinished_signups(),
  0,
  'a second run has nothing left to remove'
);

select * from extensions.finish();

rollback;
