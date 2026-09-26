begin;

set local search_path = public, extensions;

select extensions.plan(9);

select extensions.has_table(
  'private',
  'nearby_device_tag_secrets',
  'device tag secrets table exists'
);

select extensions.ok(
  not pg_catalog.has_table_privilege('authenticated', 'private.nearby_device_tag_secrets', 'select'),
  'clients cannot read device tag secrets directly'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.get_nearby_device_tag_secret()', 'execute'),
  'anonymous callers cannot fetch a device tag secret'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('api_client', 'public.get_nearby_device_tag_secret()', 'execute'),
  'connected apps cannot fetch a device tag secret'
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
    ('99700000-0000-4000-8000-000000000001'::uuid, 'tag-one@pocketpass.test', 'Tag One'),
    ('99700000-0000-4000-8000-000000000002'::uuid, 'tag-two@pocketpass.test', 'Tag Two')
) as seed(id, email, name);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select extensions.throws_ok(
  'select public.get_nearby_device_tag_secret()',
  '42501',
  'Authentication required',
  'a session without a subject is refused'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config('tag_test.first', public.get_nearby_device_tag_secret(), true);

select extensions.is(
  octet_length(decode(current_setting('tag_test.first'), 'base64')),
  32,
  'the secret is 32 random bytes'
);

select extensions.is(
  public.get_nearby_device_tag_secret(),
  current_setting('tag_test.first'),
  'the same account gets the same secret every time'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000002', true);

select extensions.isnt(
  public.get_nearby_device_tag_secret(),
  current_setting('tag_test.first'),
  'another account gets its own secret'
);

reset role;

select extensions.is(
  (select count(*) from private.nearby_device_tag_secrets),
  2::bigint,
  'one secret row per account'
);

select * from extensions.finish();

rollback;
