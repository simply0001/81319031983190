begin;

set local search_path = public, extensions;

select extensions.plan(29);

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
    '99300000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'ann@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Ann"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '99300000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'tom@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Tom"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

select extensions.throws_ok(
  $$select public.admin_stats()$$,
  '42501',
  'Authentication required',
  'admin stats reject an unauthenticated caller'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '99300000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.throws_ok(
  $$select public.admin_stats()$$,
  '42501',
  'Admin access required',
  'a signed-in non-admin cannot read stats'
);

select extensions.throws_ok(
  $$select public.admin_set_legacy_account('99300000-0000-4000-8000-000000000002', true)$$,
  '42501',
  'Admin access required',
  'a signed-in non-admin cannot flip legacy accounts'
);

select extensions.throws_ok(
  $$select public.admin_adjust_tokens('99300000-0000-4000-8000-000000000002', 10, 'nope')$$,
  '42501',
  'Admin access required',
  'a signed-in non-admin cannot grant tokens'
);

select extensions.is(
  (public.admin_whoami() ->> 'is_admin')::boolean,
  false,
  'whoami reports a non-admin without raising'
);

select extensions.throws_ok(
  $$select count(*) from private.admin_users$$,
  '42501',
  null,
  'the allowlist is not readable by authenticated users'
);

reset role;

insert into private.admin_users (user_id, note, permissions)
values ('99300000-0000-4000-8000-000000000001', 'test admin', private.admin_permission_keys());

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '99300000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (public.admin_whoami() ->> 'is_admin')::boolean,
  true,
  'whoami reports an allowlisted admin'
);

select extensions.is(
  jsonb_array_length(public.admin_whoami() -> 'permissions'),
  19,
  'whoami lists the effective permissions'
);

select extensions.lives_ok(
  $$select public.admin_stats()$$,
  'an admin can read stats'
);

select extensions.ok(
  (public.admin_stats() ->> 'admins')::integer >= 1,
  'stats count the allowlist'
);

select extensions.is(
  (
    select listed.email
    from public.admin_list_users('tom@pocketpass.test') as listed
  ),
  'tom@pocketpass.test',
  'user search finds an account by email'
);

select extensions.is(
  (
    select count(*)
    from public.admin_list_users('99300000-0000-4000-8000-000000000002') as listed
  ),
  1::bigint,
  'user search accepts an exact user id'
);

select extensions.is(
  jsonb_array_length(public.admin_get_user('99300000-0000-4000-8000-000000000002') -> 'achievements'),
  11,
  'user detail lists every achievement key'
);

select extensions.throws_ok(
  $$select public.admin_get_user('99300000-0000-4000-8000-0000000000ff')$$,
  'P0002',
  'User not found',
  'user detail rejects an unknown id'
);

select extensions.is(
  (public.admin_set_legacy_account('99300000-0000-4000-8000-000000000002', true) ->> 'legacy_account')::boolean,
  true,
  'legacy flag can be switched on'
);

reset role;

select extensions.is(
  (select profile.legacy_account from public.profiles as profile where profile.user_id = '99300000-0000-4000-8000-000000000002'),
  true,
  'legacy flag is persisted on the profile'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '99300000-0000-4000-8000-000000000002',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select entry.unlocked
    from public.get_achievements() as entry
    where entry.achievement_key = 'day_one'
  ),
  true,
  'day_one is unlocked for the legacy account'
);

select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '99300000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (public.admin_adjust_tokens('99300000-0000-4000-8000-000000000002', 40, 'welcome gift') ->> 'balance_after')::integer,
  40,
  'a token grant raises the balance'
);

select extensions.throws_ok(
  $$select public.admin_adjust_tokens('99300000-0000-4000-8000-000000000002', -100, 'too much')$$,
  '22023',
  'Insufficient balance: user has 40 tokens',
  'a token removal cannot overdraw the balance'
);

select extensions.is(
  (public.admin_adjust_tokens('99300000-0000-4000-8000-000000000002', -40, 'undo', 'Test undo') ->> 'balance_after')::integer,
  0,
  'a token removal lowers the balance'
);

select extensions.throws_ok(
  $$select public.admin_adjust_tokens('99300000-0000-4000-8000-000000000002', 5, '')$$,
  '22023',
  null,
  'a token change needs an audit reason'
);

select extensions.is(
  (public.admin_set_achievement('99300000-0000-4000-8000-000000000002', 'small_world', true) ->> 'changed')::boolean,
  true,
  'an achievement can be force-unlocked'
);

select extensions.throws_ok(
  $$select public.admin_set_achievement('99300000-0000-4000-8000-000000000002', 'nope', true)$$,
  '22023',
  'Unknown achievement key',
  'unknown achievement keys are rejected'
);

select extensions.is(
  (public.admin_set_achievement('99300000-0000-4000-8000-000000000002', 'small_world', false) ->> 'changed')::boolean,
  true,
  'an achievement can be revoked'
);

select extensions.is(
  (
    select count(*)
    from public.admin_list_audit('99300000-0000-4000-8000-000000000002') as entry
  ),
  5::bigint,
  'every admin action on the account is audited'
);

reset role;

select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.recipient_id = '99300000-0000-4000-8000-000000000002'
      and notification.kind = 'system'
      and notification.body = 'Test undo'
  ),
  1::bigint,
  'token changes notify the user with the admin-supplied message'
);

select extensions.is(
  (
    select count(*)
    from public.achievement_unlocks as unlock
    where unlock.user_id = '99300000-0000-4000-8000-000000000002'
      and unlock.achievement_key = 'small_world'
  ),
  0::bigint,
  'a revoked achievement is removed from unlocks'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.admin_stats()', 'execute'),
  'anon cannot execute admin RPCs'
);

select extensions.ok(
  pg_catalog.has_function_privilege('authenticated', 'public.admin_stats()', 'execute'),
  'authenticated can reach admin RPCs (gated inside)'
);

select * from extensions.finish();

rollback;
