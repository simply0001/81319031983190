begin;

set local search_path = public, extensions;

select extensions.plan(23);

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
    ('99600000-0000-4000-8000-000000000001'::uuid, 'studio-owner@pocketpass.test', 'Owner'),
    ('99600000-0000-4000-8000-000000000002'::uuid, 'studio-manager@pocketpass.test', 'Manager'),
    ('99600000-0000-4000-8000-000000000003'::uuid, 'studio-plain@pocketpass.test', 'Plain')
) as seed(id, email, name);

insert into private.admin_users (user_id, note, permissions, is_owner)
values
  ('99600000-0000-4000-8000-000000000001', 'test owner', '{}', true),
  ('99600000-0000-4000-8000-000000000002', 'test manager', array['admins', 'users'], false);

select extensions.ok(
  pg_catalog.has_function_privilege('anon', 'public.studio_gate()', 'execute'),
  'anon can execute the Studio gate'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('authenticated', 'public.studio_gate()', 'execute'),
  'authenticated cannot execute the Studio gate directly'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.admin_studio_session_create()', 'execute'),
  'anon cannot mint Studio sessions'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.admin_studio_sessions_revoke()', 'execute'),
  'anon cannot revoke Studio sessions'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99600000-0000-4000-8000-000000000003', true);

select extensions.throws_ok(
  $$select public.admin_studio_session_create()$$,
  '42501',
  'Admin access required',
  'non-admins cannot mint Studio sessions'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99600000-0000-4000-8000-000000000002', true);

select extensions.throws_ok(
  $$select public.admin_studio_session_create()$$,
  'PT403',
  'Only owners can open Studio',
  'non-owner admins cannot mint Studio sessions'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99600000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config('studio_test.first_token', public.admin_studio_session_create() ->> 'token', true);

select extensions.matches(
  current_setting('studio_test.first_token'),
  '^[0-9a-f]{64}$',
  'owner receives a 64-character hex token'
);

select pg_catalog.set_config('studio_test.second', public.admin_studio_session_create()::text, true);

select extensions.ok(
  (current_setting('studio_test.second')::jsonb ->> 'expires_at')::timestamptz
    between now() + interval '11 hours 59 minutes' and now() + interval '12 hours 1 minute',
  'sessions expire twelve hours after creation'
);

reset role;

select extensions.is(
  (select count(*)::integer from private.admin_studio_sessions where user_id = '99600000-0000-4000-8000-000000000001'),
  1,
  'minting again replaces the previous session'
);

select extensions.ok(
  not exists (
    select 1
    from private.admin_studio_sessions
    where token_hash = extensions.digest(current_setting('studio_test.first_token')::text, 'sha256')
  ),
  'the replaced token is gone'
);

select extensions.is(
  (select count(*)::integer from private.admin_audit where action = 'studio_session_create' and admin_id = '99600000-0000-4000-8000-000000000001'),
  2,
  'each mint is audited'
);

set local role anon;
select pg_catalog.set_config('request.jwt.claim.role', 'anon', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select pg_catalog.set_config('request.cookies', '{}', true);
select extensions.throws_ok(
  $$select public.studio_gate()$$,
  'PT401',
  'Open Studio from the admin console',
  'a request without the cookie is rejected'
);

select pg_catalog.set_config('request.cookies', '', true);
select extensions.throws_ok(
  $$select public.studio_gate()$$,
  'PT401',
  'Open Studio from the admin console',
  'an empty cookie setting is rejected'
);

select pg_catalog.set_config(
  'request.cookies',
  json_build_object('pocketpass_studio', repeat('0', 64))::text,
  true
);
select extensions.throws_ok(
  $$select public.studio_gate()$$,
  'PT401',
  'Open Studio from the admin console',
  'an unknown token is rejected'
);

select pg_catalog.set_config(
  'request.cookies',
  json_build_object('pocketpass_studio', 'not-a-token; drop table x')::text,
  true
);
select extensions.throws_ok(
  $$select public.studio_gate()$$,
  'PT401',
  'Open Studio from the admin console',
  'a malformed token is rejected'
);

select pg_catalog.set_config(
  'request.cookies',
  json_build_object('other', 'x', 'pocketpass_studio', (current_setting('studio_test.second')::jsonb ->> 'token'), 'sb', 'y')::text,
  true
);
select extensions.is(
  public.studio_gate(),
  true,
  'a live owner session passes the gate'
);

reset role;

update private.admin_users set is_owner = false where user_id = '99600000-0000-4000-8000-000000000001';

set local role anon;
select extensions.throws_ok(
  $$select public.studio_gate()$$,
  'PT401',
  'Open Studio from the admin console',
  'a demoted owner no longer passes the gate'
);

reset role;

update private.admin_users set is_owner = true where user_id = '99600000-0000-4000-8000-000000000001';
update private.admin_studio_sessions set expires_at = now() - interval '1 second' where user_id = '99600000-0000-4000-8000-000000000001';

set local role anon;
select extensions.throws_ok(
  $$select public.studio_gate()$$,
  'PT401',
  'Open Studio from the admin console',
  'an expired session is rejected'
);

reset role;

update private.admin_studio_sessions set expires_at = now() + interval '12 hours' where user_id = '99600000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99600000-0000-4000-8000-000000000001', true);

select extensions.is(
  public.admin_studio_sessions_revoke(),
  1,
  'revoking reports the removed session'
);

select extensions.is(
  public.admin_studio_sessions_revoke(),
  0,
  'revoking again finds nothing'
);

reset role;

set local role anon;
select pg_catalog.set_config('request.jwt.claim.role', 'anon', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select extensions.throws_ok(
  $$select public.studio_gate()$$,
  'PT401',
  'Open Studio from the admin console',
  'a revoked session is rejected'
);

reset role;

select extensions.is(
  (select count(*)::integer from private.admin_audit where action = 'studio_sessions_revoke' and admin_id = '99600000-0000-4000-8000-000000000001'),
  1,
  'revocation is audited once per actual removal'
);

delete from private.admin_users where user_id = '99600000-0000-4000-8000-000000000001';

select extensions.is(
  (select count(*)::integer from private.admin_studio_sessions where user_id = '99600000-0000-4000-8000-000000000001'),
  0,
  'removing an admin cascades to their Studio sessions'
);

select * from extensions.finish();

rollback;
