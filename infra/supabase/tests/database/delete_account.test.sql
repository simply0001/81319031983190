begin;

set local search_path = public, extensions;

select extensions.plan(5);

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
  '98200000-0000-4000-8000-000000000001',
  'authenticated',
  'authenticated',
  'delete-account@pocketpass.test',
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  '{"display_name":"Deleting User"}',
  now(),
  now(),
  '',
  '',
  '',
  ''
);

insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
values (
  'avatars',
  '98200000-0000-4000-8000-000000000001/mii-r1-98200000-0000-4000-8000-000000000011.png',
  '98200000-0000-4000-8000-000000000001',
  '98200000-0000-4000-8000-000000000001',
  '{"mimetype":"image/png","size":1024}'
);

select extensions.ok(
  exists (
    select 1
    from public.profiles as profile
    where profile.user_id = '98200000-0000-4000-8000-000000000001'
  ),
  'the account has a profile before deletion'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98200000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.lives_ok(
  $$ select public.delete_my_account(); $$,
  'deleting the calling account succeeds'
);

reset role;

select extensions.ok(
  not exists (
    select 1
    from auth.users as account
    where account.id = '98200000-0000-4000-8000-000000000001'
  ),
  'the auth user is removed'
);

select extensions.ok(
  not exists (
    select 1
    from public.profiles as profile
    where profile.user_id = '98200000-0000-4000-8000-000000000001'
  ),
  'the public profile is removed'
);

select extensions.ok(
  exists (
    select 1
    from storage.objects as object
    where object.bucket_id = 'avatars'
      and object.name like '98200000-0000-4000-8000-000000000001/%'
  ),
  'deletion succeeds alongside stored avatars, which the Storage API removes'
);

select * from extensions.finish();

rollback;
