begin;

set local search_path = public, extensions;

select extensions.plan(16);

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
values (
  '00000000-0000-0000-0000-000000000000',
  '98c00000-0000-4000-8000-000000000001',
  'authenticated',
  'authenticated',
  'simply.one@users.pocketpass.xyz',
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  '{"username":"simply.one"}',
  now(),
  now(),
  '',
  '',
  '',
  ''
);

select extensions.is(
  (select username::text from public.profiles where user_id = '98c00000-0000-4000-8000-000000000001'),
  'simply.one',
  'a login-domain sign-up claims its username at creation'
);

select extensions.is(
  (select display_name from public.profiles where user_id = '98c00000-0000-4000-8000-000000000001'),
  'simply.one',
  'the display name starts as the username'
);

select extensions.throws_ok(
  $$
    insert into auth.users (
      instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, email_change, email_change_token_new, recovery_token
    )
    values (
      '00000000-0000-0000-0000-000000000000',
      '98c00000-0000-4000-8000-000000000002',
      'authenticated', 'authenticated',
      'Simply.One@users.pocketpass.xyz',
      extensions.crypt('test-only', extensions.gen_salt('bf')),
      now(),
      '{"provider":"email","providers":["email"]}',
      '{"username":"simply.one"}',
      now(), now(), '', '', '', ''
    )
  $$,
  '23505',
  null,
  'a second sign-up with the same username is rejected'
);

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, email_change, email_change_token_new, recovery_token
)
values (
  '00000000-0000-0000-0000-000000000000',
  '98c00000-0000-4000-8000-000000000003',
  'authenticated', 'authenticated',
  'plain@pocketpass.test',
  '',
  now(),
  '{"provider":"email","providers":["email"]}',
  '{"display_name":"Plain User"}',
  now(), now(), '', '', '', ''
);

select extensions.is(
  (select username::text from public.profiles where user_id = '98c00000-0000-4000-8000-000000000003'),
  '98c00000000040008000000000000003',
  'email sign-ups keep the placeholder username until setup'
);

select extensions.ok(
  public.username_available('fresh.name'),
  'unused names are available'
);

select extensions.ok(
  not public.username_available('Simply.One'),
  'taken names are reported case-insensitively'
);

select extensions.ok(
  not public.username_available('ab'),
  'names shorter than three characters are not available'
);

select extensions.ok(
  not public.username_available('bad..dots'),
  'names with doubled dots are not available'
);

select pg_catalog.set_config('pocketpass.enforce_signup_guard', 'on', true);

select extensions.lives_ok(
  $$
    insert into auth.users (
      instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, email_change, email_change_token_new, recovery_token
    )
    values (
      '00000000-0000-0000-0000-000000000000',
      '98c00000-0000-4000-8000-000000000004',
      'authenticated', 'authenticated',
      'someone@example.com',
      '$2a$10$0123456789012345678901u0123456789012345678901234567890',
      now(),
      '{"provider":"email","providers":["email"]}',
      '{}',
      now(), now(), '', '', '', ''
    )
  $$,
  'GoTrue-generated passwords do not block email OTP sign-ups'
);

select extensions.is(
  (select encrypted_password from auth.users where id = '98c00000-0000-4000-8000-000000000004'),
  '',
  'email OTP accounts do not retain the generated password hash'
);

select extensions.throws_ok(
  $$
    insert into auth.users (
      instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, email_change, email_change_token_new, recovery_token
    )
    values (
      '00000000-0000-0000-0000-000000000000',
      '98c00000-0000-4000-8000-000000000005',
      'authenticated', 'authenticated',
      'x@users.pocketpass.xyz',
      '$2a$10$0123456789012345678901u0123456789012345678901234567890',
      now(),
      '{"provider":"email","providers":["email"]}',
      '{}',
      now(), now(), '', '', '', ''
    )
  $$,
  '22023',
  null,
  'login-domain sign-ups must carry a valid username'
);

select extensions.throws_ok(
  $$
    insert into auth.users (
      instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, email_change, email_change_token_new, recovery_token
    )
    values (
      '00000000-0000-0000-0000-000000000000',
      '98c00000-0000-4000-8000-000000000006',
      'authenticated', 'authenticated',
      'nopass@users.pocketpass.xyz',
      '',
      now(),
      '{"provider":"email","providers":["email"]}',
      '{}',
      now(), now(), '', '', '', ''
    )
  $$,
  '22023',
  null,
  'login-domain addresses cannot be created without a password'
);

select extensions.lives_ok(
  $$
    insert into auth.users (
      instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, email_change, email_change_token_new, recovery_token
    )
    values (
      '00000000-0000-0000-0000-000000000000',
      '98c00000-0000-4000-8000-000000000007',
      'authenticated', 'authenticated',
      'second.user@users.pocketpass.xyz',
      '$2a$10$0123456789012345678901u0123456789012345678901234567890',
      now(),
      '{"provider":"email","providers":["email"]}',
      '{"username":"second.user"}',
      now(), now(), '', '', '', ''
    )
  $$,
  'valid username sign-ups pass the guard'
);

select extensions.ok(
  (select encrypted_password <> '' from auth.users where id = '98c00000-0000-4000-8000-000000000007'),
  'PocketPass username accounts retain their password hash'
);

select extensions.lives_ok(
  $$
    insert into auth.users (
      instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, email_change, email_change_token_new, recovery_token
    )
    values (
      '00000000-0000-0000-0000-000000000000',
      '98c00000-0000-4000-8000-000000000008',
      'authenticated', 'authenticated',
      'otp@pocketpass.test',
      '',
      null,
      '{"provider":"email","providers":["email"]}',
      '{}',
      now(), now(), '', '', '', ''
    )
  $$,
  'email code sign-ups without a password still work'
);

select pg_catalog.set_config('pocketpass.enforce_signup_guard', 'off', true);

select extensions.is(
  (select username::text from public.profiles where user_id = '98c00000-0000-4000-8000-000000000007'),
  'second.user',
  'a guarded sign-up also claims its username'
);

select * from extensions.finish();

rollback;
