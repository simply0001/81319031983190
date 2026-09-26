begin;

set local search_path = public, extensions;

select extensions.plan(12);

select extensions.ok(
  pg_catalog.has_column_privilege('authenticated', 'public.profiles', 'username', 'update'),
  'authenticated can update its own username'
);

select extensions.ok(
  pg_catalog.has_column_privilege('authenticated', 'public.profiles', 'display_name', 'update'),
  'authenticated can update its own display name'
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
    '98800000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'rename-first@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Rename First"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98800000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'rename-second@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Rename Second"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

update public.profiles
set bio = 'keeps the bio'
where user_id = '98800000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98800000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

update public.profiles
set username = 'rename.one', display_name = 'rename.one'
where user_id = '98800000-0000-4000-8000-000000000001';

reset role;

select extensions.is(
  (
    select username::text
    from public.profiles
    where user_id = '98800000-0000-4000-8000-000000000001'
  ),
  'rename.one',
  'the first account claims rename.one during setup'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98800000-0000-4000-8000-000000000002',
  true
);

select extensions.throws_ok(
  $$
    update public.profiles
    set username = 'rename.one', display_name = 'rename.one'
    where user_id = '98800000-0000-4000-8000-000000000002'
  $$,
  '23505',
  null,
  'a name that is in use cannot be claimed by another account'
);

reset role;

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98800000-0000-4000-8000-000000000001',
  true
);

update public.profiles
set username = 'rename.two', display_name = 'rename.two'
where user_id = '98800000-0000-4000-8000-000000000001';

reset role;

select extensions.is(
  (
    select username::text
    from public.profiles
    where user_id = '98800000-0000-4000-8000-000000000001'
  ),
  'rename.two',
  'renaming replaces the username'
);

select extensions.is(
  (
    select display_name
    from public.profiles
    where user_id = '98800000-0000-4000-8000-000000000001'
  ),
  'rename.two',
  'renaming replaces the display name'
);

select extensions.is(
  (
    select bio
    from public.profiles
    where user_id = '98800000-0000-4000-8000-000000000001'
  ),
  'keeps the bio',
  'renaming leaves the rest of the profile untouched'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98800000-0000-4000-8000-000000000002',
  true
);

update public.profiles
set username = 'rename.one', display_name = 'rename.one'
where user_id = '98800000-0000-4000-8000-000000000002';

reset role;

select extensions.is(
  (
    select username::text
    from public.profiles
    where user_id = '98800000-0000-4000-8000-000000000002'
  ),
  'rename.one',
  'the released name is free for another account to claim'
);

select extensions.is(
  (
    select display_name
    from public.profiles
    where user_id = '98800000-0000-4000-8000-000000000002'
  ),
  'rename.one',
  'the claiming account also takes the released display name'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98800000-0000-4000-8000-000000000002',
  true
);

select extensions.throws_ok(
  $$
    update public.profiles
    set username = 'rename.two', display_name = 'rename.two'
    where user_id = '98800000-0000-4000-8000-000000000002'
  $$,
  '23505',
  null,
  'the new name is held by the account that renamed'
);

select extensions.throws_ok(
  $$
    update public.profiles
    set username = 'Rename.Two', display_name = 'Rename.Two'
    where user_id = '98800000-0000-4000-8000-000000000002'
  $$,
  '23514',
  null,
  'a differently cased spelling cannot sidestep the name key'
);

reset role;

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98800000-0000-4000-8000-000000000001',
  true
);

select extensions.throws_ok(
  $$
    update public.profiles
    set username = 'rename.one', display_name = 'rename.one'
    where user_id = '98800000-0000-4000-8000-000000000001'
  $$,
  '23505',
  null,
  'a released name cannot be taken back once someone else holds it'
);

reset role;

select * from extensions.finish();

rollback;
