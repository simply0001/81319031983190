begin;

set local search_path = public, extensions;

select extensions.plan(33);

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
    ('99400000-0000-4000-8000-000000000001'::uuid, 'owner@pocketpass.test', 'Owner'),
    ('99400000-0000-4000-8000-000000000002'::uuid, 'viewer@pocketpass.test', 'Viewer'),
    ('99400000-0000-4000-8000-000000000003'::uuid, 'manager@pocketpass.test', 'Manager'),
    ('99400000-0000-4000-8000-000000000004'::uuid, 'plain@pocketpass.test', 'Plain')
) as seed(id, email, name);

insert into private.admin_users (user_id, note, permissions, is_owner)
values
  ('99400000-0000-4000-8000-000000000001', 'test owner', '{}', true),
  ('99400000-0000-4000-8000-000000000002', 'test viewer', array['users'], false),
  ('99400000-0000-4000-8000-000000000003', 'test manager', array['admins', 'users'], false);

select extensions.throws_ok(
  $$insert into private.admin_users (user_id, permissions) values ('99400000-0000-4000-8000-000000000004', array['fly'])$$,
  '23514',
  null,
  'unknown permission keys cannot be stored'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000001', true);

select extensions.is(
  (public.admin_whoami() ->> 'is_owner')::boolean,
  true,
  'the owner is reported as owner'
);

select extensions.is(
  jsonb_array_length(public.admin_whoami() -> 'permissions'),
  8,
  'the owner holds every permission implicitly'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000002', true);

select extensions.lives_ok(
  $$select public.admin_stats()$$,
  'any admin can read the overview stats'
);

select extensions.is(
  (
    select listed.email
    from public.admin_list_users('plain@pocketpass.test') as listed
  ),
  'plain@pocketpass.test',
  'the users permission opens the user list'
);

select extensions.throws_ok(
  $$select public.admin_adjust_tokens('99400000-0000-4000-8000-000000000004', 5, 'nope')$$,
  '42501',
  'Permission required: tokens',
  'a viewer cannot adjust tokens'
);

select extensions.throws_ok(
  $$select public.admin_set_legacy_account('99400000-0000-4000-8000-000000000004', true)$$,
  '42501',
  'Permission required: legacy',
  'a viewer cannot flip legacy accounts'
);

select extensions.throws_ok(
  $$select public.admin_set_achievement('99400000-0000-4000-8000-000000000004', 'day_one', true)$$,
  '42501',
  'Permission required: achievements',
  'a viewer cannot change achievements'
);

select extensions.throws_ok(
  $$select * from public.admin_list_audit()$$,
  '42501',
  'Permission required: audit',
  'a viewer cannot read the audit log'
);

select extensions.throws_ok(
  $$select * from public.admin_list_admins()$$,
  '42501',
  'Permission required: admins',
  'a viewer cannot list admins'
);

select extensions.throws_ok(
  $$select public.admin_add_admin('plain@pocketpass.test', array['tokens'])$$,
  '42501',
  'Permission required: admins',
  'a viewer cannot add admins'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000003', true);

select extensions.is(
  (select count(*) from public.admin_list_admins() as listed where listed.email like '%@pocketpass.test'),
  3::bigint,
  'a manager sees the admin list'
);

select extensions.is(
  (select listed.is_owner from public.admin_list_admins() as listed order by listed.is_owner desc, listed.granted_at limit 1),
  true,
  'owners sort first'
);

select extensions.throws_ok(
  $$select public.admin_add_admin('nobody-here@pocketpass.test', array['tokens'])$$,
  'P0002',
  'No PocketPass account with that email',
  'adding an unknown email is rejected'
);

select extensions.throws_ok(
  $$select public.admin_add_admin('plain@pocketpass.test', array['tokens', 'fly'])$$,
  '22023',
  'Unknown permission key',
  'adding with an unknown permission is rejected'
);

select extensions.is(
  (public.admin_add_admin('Plain@PocketPass.test', array['tokens', 'tokens', 'users'], 'contest helper') -> 'permissions'),
  '["tokens", "users"]'::jsonb,
  'a manager can add an admin; permissions are deduplicated and sorted'
);

select extensions.throws_ok(
  $$select public.admin_add_admin('plain@pocketpass.test', array['tokens'])$$,
  '23505',
  'Already an admin',
  'an existing admin cannot be added twice'
);

select extensions.throws_ok(
  $$select public.admin_set_admin_permissions('99400000-0000-4000-8000-000000000001', array['users'])$$,
  '42501',
  'Owners are managed with psql',
  'a manager cannot edit an owner'
);

select extensions.throws_ok(
  $$select public.admin_remove_admin('99400000-0000-4000-8000-000000000001')$$,
  '42501',
  'Owners are managed with psql',
  'a manager cannot remove an owner'
);

select extensions.throws_ok(
  $$select public.admin_remove_admin('99400000-0000-4000-8000-000000000003')$$,
  '22023',
  'You cannot remove yourself',
  'a manager cannot remove themselves'
);

select extensions.throws_ok(
  $$select public.admin_set_admin_permissions('99400000-0000-4000-8000-000000000003', array['users'])$$,
  '22023',
  'You cannot remove your own admin-management permission',
  'a manager cannot drop their own admins permission'
);

select extensions.lives_ok(
  $$select public.admin_set_admin_permissions('99400000-0000-4000-8000-000000000003', array['admins', 'users', 'audit'])$$,
  'a manager can widen their own permissions while keeping admins'
);

select extensions.is(
  (public.admin_set_admin_permissions('99400000-0000-4000-8000-000000000004', array['tokens'], 'tokens only') -> 'permissions'),
  '["tokens"]'::jsonb,
  'a manager can change another admin''s permissions'
);

select extensions.throws_ok(
  $$select public.admin_set_admin_permissions('99400000-0000-4000-8000-0000000000ff', array['tokens'])$$,
  'P0002',
  'Admin not found',
  'editing an unknown admin is rejected'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000004', true);

select extensions.is(
  (public.admin_adjust_tokens('99400000-0000-4000-8000-000000000002', 5, 'perm test') ->> 'balance_after')::integer,
  5,
  'the newly added admin can use the permission they were given'
);

select extensions.throws_ok(
  $$select * from public.admin_list_users()$$,
  '42501',
  'Permission required: users',
  'and nothing beyond it'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000003', true);

select extensions.is(
  (public.admin_remove_admin('99400000-0000-4000-8000-000000000004') ->> 'removed')::boolean,
  true,
  'a manager can remove a non-owner admin'
);

select extensions.throws_ok(
  $$select public.admin_remove_admin('99400000-0000-4000-8000-000000000004')$$,
  'P0002',
  'Admin not found',
  'removing twice is rejected'
);

reset role;

select extensions.is(
  (
    select count(*)
    from private.admin_audit as entry
    where entry.target_user_id = '99400000-0000-4000-8000-000000000004'
      and entry.action in ('add_admin', 'set_admin_permissions', 'remove_admin')
  ),
  3::bigint,
  'admin management is audited'
);

select extensions.is(
  (select count(*) from private.admin_audit as entry where entry.payload ? 'email'),
  0::bigint,
  'admin management audit JSON does not duplicate account emails'
);

select extensions.is(
  (
    select entry.payload -> 'after'
    from private.admin_audit as entry
    where entry.target_user_id = '99400000-0000-4000-8000-000000000003'
      and entry.action = 'set_admin_permissions'
    order by entry.id desc
    limit 1
  ),
  '["admins", "audit", "users"]'::jsonb,
  'permission changes record the resulting set'
);

select extensions.is(
  (select admin.granted_by from private.admin_users as admin where admin.user_id = '99400000-0000-4000-8000-000000000003'),
  null::uuid,
  'seeded rows have no granter'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.admin_add_admin(text, text[], text)', 'execute'),
  'anon cannot execute admin management RPCs'
);

select * from extensions.finish();

rollback;
