begin;

set local search_path = public, extensions;

select extensions.plan(8);

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
  '99300000-0000-4000-8000-000000000001',
  'authenticated',
  'authenticated',
  'seen@pocketpass.test',
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  '{"display_name":"Seen pgtap"}',
  now(),
  now(),
  '',
  '',
  '',
  ''
);

select extensions.ok(
  has_function_privilege('authenticated', 'public.touch_last_seen()', 'execute'),
  'signed-in users can touch their last seen time'
);

select extensions.ok(
  not has_function_privilege('anon', 'public.touch_last_seen()', 'execute'),
  'anon cannot touch a last seen time'
);

select extensions.ok(
  not has_function_privilege('api_client', 'public.touch_last_seen()', 'execute'),
  'connected apps cannot touch a last seen time'
);

select extensions.is(
  (
    select profile.last_seen_at
    from public.profiles as profile
    where profile.user_id = '99300000-0000-4000-8000-000000000001'
  ),
  null::timestamptz,
  'a fresh profile has never been seen'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99300000-0000-4000-8000-000000000001', true);

select extensions.is(
  public.touch_last_seen(),
  now(),
  'touching returns the stamped time'
);

reset role;

select extensions.is(
  (
    select profile.last_seen_at
    from public.profiles as profile
    where profile.user_id = '99300000-0000-4000-8000-000000000001'
  ),
  now(),
  'the profile carries the stamped time'
);

update public.profiles
set last_seen_at = now() - interval '10 seconds'
where user_id = '99300000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99300000-0000-4000-8000-000000000001', true);

select public.touch_last_seen();

reset role;

select extensions.is(
  (
    select profile.last_seen_at
    from public.profiles as profile
    where profile.user_id = '99300000-0000-4000-8000-000000000001'
  ),
  now() - interval '10 seconds',
  'a touch within thirty seconds is coalesced'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select extensions.throws_ok(
  $$select public.touch_last_seen()$$,
  '42501',
  'Authentication required',
  'a token without a subject is refused'
);

reset role;

select * from extensions.finish();

rollback;
