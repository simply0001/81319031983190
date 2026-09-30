begin;

set local search_path = public, extensions;

select extensions.plan(55);

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
    ('99500000-0000-4000-8000-000000000001'::uuid, 'ban-owner@pocketpass.test', 'Owner'),
    ('99500000-0000-4000-8000-000000000002'::uuid, 'ban-moderator@pocketpass.test', 'Moderator'),
    ('99500000-0000-4000-8000-000000000003'::uuid, 'ban-viewer@pocketpass.test', 'Viewer'),
    ('99500000-0000-4000-8000-000000000004'::uuid, 'Target.Person+alt@gmail.com', 'Target'),
    ('99500000-0000-4000-8000-000000000005'::uuid, 'ban-friend@pocketpass.test', 'Friend'),
    ('99500000-0000-4000-8000-000000000006'::uuid, 'ban-other@pocketpass.test', 'Other')
) as seed(id, email, name);

insert into private.admin_users (user_id, note, permissions, is_owner)
values
  ('99500000-0000-4000-8000-000000000001', 'test owner', '{}', true),
  ('99500000-0000-4000-8000-000000000002', 'test moderator', array['bans', 'users'], false),
  ('99500000-0000-4000-8000-000000000003', 'test viewer', array['users'], false);

insert into auth.identities (provider_id, user_id, identity_data, provider, created_at, updated_at)
values (
  '123456789012345678',
  '99500000-0000-4000-8000-000000000004',
  '{"sub":"123456789012345678","email":"target.person@GMAIL.com"}',
  'discord',
  now(),
  now()
);

insert into auth.sessions (id, user_id, created_at, updated_at, aal, ip)
values (
  '99500000-0000-4000-8000-000000000104',
  '99500000-0000-4000-8000-000000000004',
  now(),
  now(),
  'aal1'::auth.aal_level,
  '203.0.113.7'
);

insert into public.friendships (user_low, user_high, created_by)
values (
  '99500000-0000-4000-8000-000000000004',
  '99500000-0000-4000-8000-000000000005',
  '99500000-0000-4000-8000-000000000004'
);

select pg_catalog.set_config(
  'pocketpass.test_target_code',
  (select code from public.friend_codes where user_id = '99500000-0000-4000-8000-000000000004'),
  true
);

select extensions.ok(
  'bans' = any (private.admin_permission_keys()),
  'bans is a known admin permission'
);

select extensions.is(
  private.normalize_ban_email(' Target.Person+alt@GoogleMail.com '),
  'targetperson@gmail.com',
  'gmail dots, plus tags and googlemail fold into one address'
);

select extensions.is(
  private.ban_network_value('10.1.2.3'),
  null,
  'private networks are never stored'
);

select extensions.is(
  private.ban_network_value('2001:db8:1:2:3:4:5:6'),
  '2001:db8:1:2::/64',
  'ipv6 networks are kept at /64'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000003', true);

select extensions.throws_ok(
  $$select public.admin_ban_account('99500000-0000-4000-8000-000000000004', 'Spam', null, 'permanent')$$,
  '42501',
  'Permission required: bans',
  'banning needs the bans permission'
);

select extensions.throws_ok(
  $$select * from public.admin_list_bans()$$,
  '42501',
  'Permission required: bans',
  'listing bans needs the bans permission'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000002', true);

select extensions.throws_ok(
  $$select public.admin_ban_account('99500000-0000-4000-8000-000000000002', 'Spam', null, 'permanent')$$,
  '22023',
  'You cannot ban yourself',
  'staff cannot ban themselves'
);

select extensions.throws_ok(
  $$select public.admin_ban_account('99500000-0000-4000-8000-000000000001', 'Spam', null, 'permanent')$$,
  '42501',
  'Admins and owners cannot be banned',
  'owners cannot be banned'
);

select extensions.throws_ok(
  $$select public.admin_ban_account('99500000-0000-4000-8000-000000000004', '  ', null, 'permanent')$$,
  '22023',
  'p_reason must be 1 to 300 characters',
  'a reason is required'
);

select extensions.throws_ok(
  $$select public.admin_ban_account('99500000-0000-4000-8000-000000000004', 'Spam', null, 'forever')$$,
  '22023',
  'Unknown duration',
  'only the listed durations are accepted'
);

select extensions.lives_ok(
  $$select public.admin_ban_account('99500000-0000-4000-8000-000000000004', 'Spam in Boards', 'Three reports', 'permanent')$$,
  'a moderator can ban an account'
);

select extensions.throws_ok(
  $$select public.admin_ban_account('99500000-0000-4000-8000-000000000004', 'Spam', null, '7d')$$,
  '23505',
  'This account is already banned',
  'an account cannot be banned twice'
);

select extensions.ok(
  private.account_banned('99500000-0000-4000-8000-000000000004'),
  'the account is banned'
);

select extensions.is(
  (select count(*)::integer from public.admin_list_bans('active') as listed where listed.user_id = '99500000-0000-4000-8000-000000000004'),
  1,
  'the ban is listed as active'
);

select extensions.is(
  (select listed.staff_note from public.admin_get_user_bans('99500000-0000-4000-8000-000000000004') as listed),
  'Three reports',
  'the staff note is kept'
);

reset role;

select extensions.is(
  (
    select count(*)::integer
    from private.ban_signals as signal
    join private.account_bans as ban on ban.id = signal.ban_id
    where ban.user_id = '99500000-0000-4000-8000-000000000004' and signal.kind = 'email'
  ),
  1,
  'account and Discord emails collapse into one email signal'
);

select extensions.is(
  (
    select count(*)::integer
    from private.ban_signals as signal
    join private.account_bans as ban on ban.id = signal.ban_id
    where ban.user_id = '99500000-0000-4000-8000-000000000004' and signal.kind = 'discord'
  ),
  1,
  'the Discord account is recorded'
);

select extensions.ok(
  (
    select signal.expires_at <= now() + interval '30 days'
    from private.ban_signals as signal
    join private.account_bans as ban on ban.id = signal.ban_id
    where ban.user_id = '99500000-0000-4000-8000-000000000004' and signal.kind = 'network'
  ),
  'the session network is recorded for at most 30 days'
);

select extensions.is(
  (
    select count(*)::integer
    from private.ban_signals as signal
    join private.account_bans as ban on ban.id = signal.ban_id
    where ban.user_id = '99500000-0000-4000-8000-000000000004'
      and octet_length(signal.value_hash) <> 32
  ),
  0,
  'signals are stored as keyed hashes only'
);

select extensions.ok(
  exists (
    select 1
    from private.admin_audit as audit
    where audit.action = 'ban_account'
      and audit.target_user_id = '99500000-0000-4000-8000-000000000004'
      and not audit.payload ? 'email'
  ),
  'the ban is audited without an email'
);

select extensions.ok(
  private.board_blocked('99500000-0000-4000-8000-000000000005', '99500000-0000-4000-8000-000000000004'),
  'Boards treat the banned account as blocked'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000005', true);

select extensions.is(
  (select count(*)::integer from public.profiles where user_id = '99500000-0000-4000-8000-000000000004'),
  0,
  'friends no longer see the banned profile'
);

select extensions.is(
  (select count(*)::integer from public.friendships),
  0,
  'the friendship is hidden while banned'
);

select extensions.is(
  (select count(*)::integer from public.resolve_friend_code(current_setting('pocketpass.test_target_code'))),
  0,
  'the friend code no longer resolves'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000006', true);

select extensions.throws_ok(
  $$select public.send_friend_request('99500000-0000-4000-8000-000000000004', gen_random_uuid())$$,
  '42501',
  null,
  'friend requests to a banned account are refused'
);

select pg_catalog.set_config('request.path', '/rpc/send_message', true);

select extensions.lives_ok(
  $$select public.pocketpass_request_guard()$$,
  'the request guard lets other accounts through'
);

select extensions.is(
  public.get_my_account_ban(),
  null,
  'an account without a ban gets null'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000004', true);

select extensions.throws_ok(
  $$select public.pocketpass_request_guard()$$,
  'PT403',
  'This account is banned.',
  'the request guard refuses the banned account'
);

select pg_catalog.set_config('request.path', '/rpc/get_my_account_ban', true);

select extensions.lives_ok(
  $$select public.pocketpass_request_guard()$$,
  'the ban notice stays reachable'
);

select pg_catalog.set_config('request.headers', '{"x-pocketpass-client-ip":"198.51.100.9"}', true);

select extensions.is(
  public.get_my_account_ban() ->> 'reason',
  'Spam in Boards',
  'the banned account reads its reason'
);

select extensions.ok(
  public.get_my_account_ban() -> 'ends_at' = 'null'::jsonb,
  'a permanent ban has no end date'
);

reset role;

select extensions.is(
  (
    select count(*)::integer
    from private.ban_signals as signal
    join private.account_bans as ban on ban.id = signal.ban_id
    where ban.user_id = '99500000-0000-4000-8000-000000000004' and signal.kind = 'network'
  ),
  2,
  'opening the ban notice records the new network'
);

select extensions.is(
  (public.pocketpass_before_user_created(jsonb_build_object(
    'metadata', jsonb_build_object('ip_address', '192.0.2.44'),
    'user', jsonb_build_object('email', 'targetperson@googlemail.com', 'app_metadata', jsonb_build_object('provider', 'email'))
  )) -> 'error' ->> 'http_code')::integer,
  403,
  'an email variant of a banned email is refused'
);

select extensions.is(
  public.pocketpass_before_user_created(jsonb_build_object(
    'metadata', jsonb_build_object('ip_address', '203.0.113.7'),
    'user', jsonb_build_object('email', 'someone.new@example.com', 'app_metadata', jsonb_build_object('provider', 'email'))
  )),
  '{}'::jsonb,
  'email sign-ups are not checked by network'
);

select extensions.is(
  public.pocketpass_before_user_created(jsonb_build_object(
    'metadata', jsonb_build_object('ip_address', '192.0.2.44'),
    'user', jsonb_build_object(
      'email', 'fresh@example.com',
      'app_metadata', jsonb_build_object('provider', 'discord'),
      'user_metadata', jsonb_build_object('provider_id', '123456789012345678', 'sub', '123456789012345678')
    )
  )) -> 'error' ->> 'message',
  'This sign-up is blocked because of a ban.',
  'the banned Discord account cannot sign up again'
);

select extensions.is(
  (public.pocketpass_before_user_created(jsonb_build_object(
    'metadata', jsonb_build_object('ip_address', '203.0.113.7'),
    'user', jsonb_build_object('email', 'newname@users.pocketpass.xyz', 'app_metadata', jsonb_build_object('provider', 'email'))
  )) -> 'error' ->> 'http_code')::integer,
  403,
  'a username account from the banned network is refused'
);

select extensions.is(
  (public.pocketpass_before_user_created(jsonb_build_object(
    'metadata', jsonb_build_object('ip_address', '198.51.100.9'),
    'user', jsonb_build_object('email', 'another@users.pocketpass.xyz', 'app_metadata', jsonb_build_object('provider', 'email'))
  )) -> 'error' ->> 'http_code')::integer,
  403,
  'a username account from a network seen on the ban notice is refused'
);

select extensions.is(
  public.pocketpass_before_user_created(jsonb_build_object(
    'metadata', jsonb_build_object('ip_address', '192.0.2.44'),
    'user', jsonb_build_object('email', 'clean@users.pocketpass.xyz', 'app_metadata', jsonb_build_object('provider', 'email'))
  )),
  '{}'::jsonb,
  'a username account from a clean network is allowed'
);

select extensions.is(
  public.pocketpass_before_user_created('{"user": null}'::jsonb),
  '{}'::jsonb,
  'an unexpected payload lets the sign-up through'
);

select extensions.is(
  (
    select count(*)::integer
    from private.ban_signup_blocks as block
    join private.account_bans as ban on ban.id = block.ban_id
    where ban.user_id = '99500000-0000-4000-8000-000000000004'
  ),
  4,
  'each refused sign-up is logged'
);

select extensions.throws_ok(
  $$insert into auth.identities (provider_id, user_id, identity_data, provider, created_at, updated_at)
    values ('123456789012345678', '99500000-0000-4000-8000-000000000006', '{"sub":"123456789012345678"}', 'discord', now(), now())$$,
  '42501',
  'This sign-up is blocked because of a ban.',
  'the banned Discord account cannot be linked to another account'
);

select extensions.throws_ok(
  $$update auth.users set email_change = 'target.person@gmail.com' where id = '99500000-0000-4000-8000-000000000006'$$,
  '42501',
  'This email is blocked because of a ban.',
  'a banned email cannot be linked to another account'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('authenticated', 'public.pocketpass_before_user_created(jsonb)', 'execute')
    and pg_catalog.has_function_privilege('supabase_auth_admin', 'public.pocketpass_before_user_created(jsonb)', 'execute'),
  'only Auth can run the sign-up check'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.admin_ban_account(uuid, text, text, text)', 'execute'),
  'anon cannot ban'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000002', true);

select extensions.is(
  (select count(*)::integer from public.admin_list_blocked_signups() as listed where listed.user_id = '99500000-0000-4000-8000-000000000004'),
  4,
  'refused sign-ups point at the banned account'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000003', true);

select extensions.throws_ok(
  $$select public.admin_lift_ban((select max(id) from public.admin_list_bans('all')), null)$$,
  '42501',
  null,
  'lifting needs the bans permission'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000002', true);
select pg_catalog.set_config(
  'pocketpass.test_ban_id',
  (select max(id)::text from public.admin_list_bans('all')),
  true
);

select extensions.lives_ok(
  $$select public.admin_lift_ban(current_setting('pocketpass.test_ban_id')::bigint, 'Appeal accepted')$$,
  'a moderator can lift a ban'
);

select extensions.throws_ok(
  $$select public.admin_lift_ban(current_setting('pocketpass.test_ban_id')::bigint, null)$$,
  '22023',
  'This ban has already ended',
  'a lifted ban cannot be lifted again'
);

select extensions.ok(
  not private.account_banned('99500000-0000-4000-8000-000000000004'),
  'the account is no longer banned'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000005', true);

select extensions.is(
  (select count(*)::integer from public.profiles where user_id = '99500000-0000-4000-8000-000000000004'),
  1,
  'lifting the ban shows the profile again'
);

select extensions.is(
  (select count(*)::integer from public.friendships),
  1,
  'lifting the ban restores the friendship'
);

reset role;

select extensions.is(
  (
    select count(*)::integer
    from private.ban_signals as signal
    where signal.ban_id = current_setting('pocketpass.test_ban_id')::bigint
  ),
  0,
  'lifting a ban removes its signals'
);

select extensions.is(
  public.pocketpass_before_user_created(jsonb_build_object(
    'metadata', jsonb_build_object('ip_address', '203.0.113.7'),
    'user', jsonb_build_object('email', 'target.person@gmail.com', 'app_metadata', jsonb_build_object('provider', 'email'))
  )),
  '{}'::jsonb,
  'sign-ups are allowed again after the ban is lifted'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000002', true);

select extensions.lives_ok(
  $$select public.admin_ban_account('99500000-0000-4000-8000-000000000004', 'Cooling off', null, '1d')$$,
  'a timed ban can be issued'
);

reset role;

update private.account_bans
set created_at = now() - interval '2 days',
    ends_at = now() - interval '1 day'
where user_id = '99500000-0000-4000-8000-000000000004'
  and lifted_at is null;

select extensions.ok(
  not private.account_banned('99500000-0000-4000-8000-000000000004'),
  'a timed ban ends on its own'
);

select * from extensions.finish();

rollback;
