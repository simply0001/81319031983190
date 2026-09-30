begin;

set local search_path = public, extensions;

select extensions.plan(36);

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
    ('99660000-0000-4000-8000-000000000001'::uuid, 'aph-ann@pocketpass.test', 'Aph Ann pgtap'),
    ('99660000-0000-4000-8000-000000000002'::uuid, 'aph-ben@pocketpass.test', 'Aph Ben pgtap'),
    ('99660000-0000-4000-8000-000000000009'::uuid, 'aph-dev@pocketpass.test', 'Aph Dev pgtap')
) as seed(id, email, name);

update public.profiles
set username = names.username, bio = 'bio', age = 30, country_code = 'GB'
from (
  values
    ('99660000-0000-4000-8000-000000000001'::uuid, 'pgtap.ann.9966'),
    ('99660000-0000-4000-8000-000000000002'::uuid, 'pgtap.ben.9966'),
    ('99660000-0000-4000-8000-000000000009'::uuid, 'pgtap.dev.9966')
) as names(user_id, username)
where profiles.user_id = names.user_id;

insert into public.friendships (user_low, user_high, created_by)
values (
  '99660000-0000-4000-8000-000000000001',
  '99660000-0000-4000-8000-000000000002',
  '99660000-0000-4000-8000-000000000001'
);

insert into public.conversations (id, kind, created_by, direct_user_low, direct_user_high)
values (
  '99660000-0000-4000-8000-000000000101',
  'direct',
  '99660000-0000-4000-8000-000000000001',
  '99660000-0000-4000-8000-000000000001',
  '99660000-0000-4000-8000-000000000002'
);

insert into public.conversation_members (conversation_id, user_id, role)
values
  ('99660000-0000-4000-8000-000000000101', '99660000-0000-4000-8000-000000000001', 'owner'),
  ('99660000-0000-4000-8000-000000000101', '99660000-0000-4000-8000-000000000002', 'member');

select pg_catalog.set_config(
  'aph_test.missing_code',
  (
    select lpad(candidate::text, 8, '0')
    from generate_series(0, 5000) as candidate
    where not exists (
      select 1 from public.friend_codes as code where code.code = lpad(candidate::text, 8, '0')
    )
    limit 1
  ),
  true
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99660000-0000-4000-8000-000000000009', true);

select pg_catalog.set_config(
  'aph_test.app',
  public.developer_create_app(
    'Aph Full pgtap',
    '',
    '',
    '',
    array['https://aph.example/callback'],
    array['profile:read', 'friends:read', 'friends:write', 'notifications:read', 'privacy:read', 'privacy:write', 'messages:read', 'messages:write', 'blocks:read'],
    'confidential'
  ) -> 'app' ->> 'client_id',
  true
);

reset role;
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

update private.developer_apps
set rate_limit_per_second = 100000
where owner_user_id = '99660000-0000-4000-8000-000000000009';

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values (gen_random_uuid(), '99660000-0000-4000-8000-000000000001', current_setting('aph_test.app')::uuid, 'openid', now());

insert into public.notifications (recipient_id, kind, title, body)
values
  ('99660000-0000-4000-8000-000000000001', 'system', 'Old', 'old'),
  ('99660000-0000-4000-8000-000000000001', 'system', 'New', 'new');

update public.notifications
set updated_at = now() - interval '2 hours'
where recipient_id = '99660000-0000-4000-8000-000000000001'
  and title = 'Old';

select extensions.is(
  private.api_scope_descriptions() ->> 'privacy:write',
  'Enable or disable Block Messages and Block Invites on your account',
  'the privacy:write scope describes Block Invites too'
);

select extensions.ok(
  position('order by d.created_at desc,d.id desc' in pg_get_functiondef('public.boards_query(text,jsonb)'::regprocedure)) > 0,
  'Boards drafts sort with an id tie-break'
);

insert into private.api_rate_buckets_app (client_id, bucket, shard, requests)
values
  (current_setting('aph_test.app')::uuid, date_trunc('minute', now()), 3, 5),
  (current_setting('aph_test.app')::uuid, date_trunc('minute', now()), 7, 5);

select extensions.is(
  (private.api_meter(current_setting('aph_test.app')::uuid, '99660000-0000-4000-8000-000000000001') ->> 'app_requests')::integer,
  11,
  'the per-app counter adds up every shard'
);

delete from private.api_rate_buckets_app where client_id = current_setting('aph_test.app')::uuid;
delete from private.api_rate_buckets_app_second where client_id = current_setting('aph_test.app')::uuid;
delete from private.api_rate_buckets where client_id = current_setting('aph_test.app')::uuid;

update public.profiles set display_name = 'Aph Ben renamed' where user_id = '99660000-0000-4000-8000-000000000002';

select extensions.is(
  (
    select count(*)::integer
    from realtime.messages as message
    where message.topic = 'friends:99660000-0000-4000-8000-000000000001'
      and message.payload ->> 'user_id' = '99660000-0000-4000-8000-000000000002'
      and not message.payload ? 'record'
  ),
  1,
  'a friend gets one profile change event with only the user id'
);

select extensions.is(
  (
    select count(*)::integer
    from realtime.messages as message
    where message.topic in (
        'friends:99660000-0000-4000-8000-000000000001',
        'friends:99660000-0000-4000-8000-000000000002'
      )
      and message.payload ->> 'table' = 'profiles'
  ),
  0,
  'profile rows are no longer broadcast to friends'
);

set local role api_client;
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99660000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('aph_test.app'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.ok(
  public.api_v1_privacy_get('{}'::jsonb) ?& array['block_messages', 'block_invites'],
  'privacy.get returns both privacy settings'
);

select extensions.is(
  public.api_v1_privacy_set(jsonb_build_object('block_invites', true, 'operation_id', gen_random_uuid())),
  '{"block_messages": false, "block_invites": true}'::jsonb,
  'privacy.set turns on Block Invites and leaves Block Messages alone'
);

select extensions.is(
  public.api_v1_privacy_set(jsonb_build_object('operation_id', gen_random_uuid())) ->> 'hint',
  'MISSING_FIELD',
  'privacy.set needs at least one setting'
);

select extensions.ok(
  position('X-PocketPass-Error' in current_setting('response.headers', true)) > 0,
  'API errors carry the error marker header'
);

select extensions.is(
  public.api_v1_friends_code_resolve(jsonb_build_object('code', current_setting('aph_test.missing_code'))) ->> 'hint',
  'CODE_NOT_FOUND',
  'an unknown friend code is not found'
);

reset role;

select extensions.is(
  (select block_invites from public.profiles where user_id = '99660000-0000-4000-8000-000000000001'),
  true,
  'Block Invites is saved'
);

select extensions.is(
  (
    select count(*)::integer
    from realtime.messages as message
    where message.topic = 'privacy:99660000-0000-4000-8000-000000000001'
      and message.event = 'PRIVACY'
  ),
  1,
  'changing Block Invites notifies the privacy topic'
);

select extensions.is(
  (
    select count(*)::integer
    from private.friend_code_lookup_attempts as attempt
    where attempt.actor_id = '99660000-0000-4000-8000-000000000001'
  ),
  1,
  'a friend code miss counts toward the lookup limit'
);

insert into private.friend_code_lookup_attempts (actor_id, attempted_at)
select '99660000-0000-4000-8000-000000000001', now() - interval '30 minutes'
from generate_series(1, 49);

set local role api_client;

select extensions.is(
  public.api_v1_friends_code_resolve(jsonb_build_object('code', current_setting('aph_test.missing_code'))) ->> 'hint',
  'FRIEND_CODE_RATE_LIMITED',
  'the 51st lookup in an hour is refused'
);

select extensions.ok(
  (
    select (header.value ->> 'Retry-After')::integer between 1700 and 1801
    from jsonb_array_elements(current_setting('response.headers', true)::jsonb) as header(value)
    where header.value ? 'Retry-After'
  ),
  'the friend code limit says when to retry'
);

select extensions.is(
  jsonb_array_length(public.api_v1_notifications_list(jsonb_build_object('updated_after', now() - interval '1 hour')) -> 'items'),
  1,
  'notifications.list filters by updated_after'
);

select extensions.is(
  jsonb_array_length(public.api_v1_notifications_list('{}'::jsonb) -> 'items'),
  2,
  'notifications.list without updated_after returns everything'
);

select extensions.ok(
  jsonb_typeof(public.api_v1_session_get('{}'::jsonb) -> 'rate_limit' -> 'remaining') = 'number',
  'session.get still reports the remaining requests'
);

select extensions.is(
  public.api_v1_messages_send(jsonb_build_object('conversation_id', '99660000-0000-4000-8000-000000000101', 'client_operation_id', '99660000-0000-4000-8000-00000000a001', 'body', 'hello')) -> 'message' ->> 'body',
  'hello',
  'messages.send sends a message'
);

select extensions.is(
  public.api_v1_messages_send(jsonb_build_object('conversation_id', '99660000-0000-4000-8000-000000000101', 'client_operation_id', '99660000-0000-4000-8000-00000000a004', 'body', 'second')) -> 'message' ->> 'body',
  'second',
  'messages.send sends a second message'
);

select extensions.is(
  public.api_v1_messages_send(jsonb_build_object('conversation_id', '99660000-0000-4000-8000-000000000101', 'client_operation_id', '99660000-0000-4000-8000-00000000a004', 'body', 'changed')) ->> 'hint',
  'DUPLICATE_OPERATION_ID',
  'reusing an operation id for a different message is refused'
);

select extensions.is(
  public.api_v1_blocks_list(jsonb_build_object('cursor', repeat('a', 5000))) ->> 'hint',
  'INVALID_CURSOR',
  'an over-long blocks.list cursor is INVALID_CURSOR'
);

reset role;

update public.messages
set body = 'edited', edited_at = now()
where client_operation_id = '99660000-0000-4000-8000-00000000a001';

insert into public.user_blocks (blocker_id, blocked_id)
values ('99660000-0000-4000-8000-000000000001', '99660000-0000-4000-8000-000000000002');

set local role api_client;

select extensions.is(
  public.api_v1_messages_send(jsonb_build_object('conversation_id', '99660000-0000-4000-8000-000000000101', 'client_operation_id', '99660000-0000-4000-8000-00000000a001', 'body', 'hello')) -> 'message' ->> 'body',
  'edited',
  'a retry after an edit returns the message as it is now'
);

select extensions.is(
  public.api_v1_messages_send(jsonb_build_object('conversation_id', '99660000-0000-4000-8000-000000000101', 'client_operation_id', '99660000-0000-4000-8000-00000000a002', 'body', 'blocked')) ->> 'code',
  'PT403',
  'a new message is refused after a block'
);

reset role;

delete from public.user_blocks where blocker_id = '99660000-0000-4000-8000-000000000001';
update public.conversation_members
set left_at = now()
where conversation_id = '99660000-0000-4000-8000-000000000101'
  and user_id = '99660000-0000-4000-8000-000000000001';

set local role api_client;

select extensions.is(
  public.api_v1_messages_send(jsonb_build_object('conversation_id', '99660000-0000-4000-8000-000000000101', 'client_operation_id', '99660000-0000-4000-8000-00000000a004', 'body', 'second')) -> 'message' ->> 'body',
  'second',
  'a retry after leaving the chat still returns the message'
);

select extensions.is(
  public.api_v1_messages_send(jsonb_build_object('conversation_id', '99660000-0000-4000-8000-000000000101', 'client_operation_id', '99660000-0000-4000-8000-00000000a003', 'body', 'after leaving')) ->> 'code',
  'PT403',
  'a new message after leaving the chat is refused'
);

reset role;

select extensions.ok(
  private.api_has_scope('friends:read'),
  'the connected app has its scope before a ban'
);

insert into private.account_bans (user_id, reason, banned_by)
values ('99660000-0000-4000-8000-000000000001', 'pgtap', '99660000-0000-4000-8000-000000000009');

select extensions.is(
  public.pocketpass_access_token_hook(jsonb_build_object(
    'user_id', '99660000-0000-4000-8000-000000000001',
    'claims', jsonb_build_object('sub', '99660000-0000-4000-8000-000000000001', 'client_id', current_setting('aph_test.app'))
  )) -> 'error' ->> 'http_code',
  '403',
  'a banned account cannot get a connected app token'
);

select extensions.ok(
  public.pocketpass_access_token_hook(jsonb_build_object(
    'user_id', '99660000-0000-4000-8000-000000000001',
    'claims', jsonb_build_object('sub', '99660000-0000-4000-8000-000000000001')
  )) ? 'claims',
  'a banned account can still sign in to the app itself'
);

select extensions.ok(
  not private.api_has_scope('friends:read'),
  'a ban removes every connected app scope'
);

set local role api_client;

select extensions.ok(
  not private.api_can_access_realtime_topic('friends:99660000-0000-4000-8000-000000000001'),
  'a banned account connected app cannot join its Realtime topics'
);

select pg_catalog.set_config('request.path', '/rpc/api_v1_me_get', true);

select extensions.throws_ok(
  $$select public.pocketpass_request_guard()$$,
  'PT403',
  'This account is banned.',
  'a banned account connected app gets PT403 ACCOUNT_BANNED'
);

reset role;
delete from private.account_bans where user_id = '99660000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99660000-0000-4000-8000-000000000002', true);

select extensions.throws_ok(
  $$select public.pocketpass_request_guard()$$,
  'PT401',
  'A connected app access token is required',
  'an app session token on a /v1 endpoint gets PT401 API_TOKEN_REQUIRED'
);

select pg_catalog.set_config('request.path', '/rpc/send_message', true);

select extensions.lives_ok(
  $$select public.pocketpass_request_guard()$$,
  'app session tokens still reach the app endpoints'
);

set local role anon;
select pg_catalog.set_config('request.jwt.claim.role', 'anon', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.path', '/rpc/api_v1_friends_list', true);

select extensions.throws_ok(
  $$select public.pocketpass_request_guard()$$,
  'PT401',
  'A connected app access token is required',
  'a request without a token on a /v1 endpoint gets PT401 API_TOKEN_REQUIRED'
);

reset role;

select extensions.ok(
  not pg_catalog.has_function_privilege('api_client', 'private.account_banned(uuid)', 'execute'),
  'connected apps still cannot call the ban helper directly'
);

select * from extensions.finish();

rollback;
