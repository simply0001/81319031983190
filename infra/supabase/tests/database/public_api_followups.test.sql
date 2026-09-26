begin;

set local search_path = public, extensions;

select extensions.plan(124);

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
    ('99600000-0000-4000-8000-000000000001'::uuid, 'fu-fay@pocketpass.test', 'Fu Fay pgtap'),
    ('99600000-0000-4000-8000-000000000002'::uuid, 'fu-gus@pocketpass.test', 'Fu Gus pgtap'),
    ('99600000-0000-4000-8000-000000000003'::uuid, 'fu-hal@pocketpass.test', 'Fu Hal pgtap'),
    ('99600000-0000-4000-8000-000000000004'::uuid, 'fu-ivy@pocketpass.test', 'Fu Ivy pgtap'),
    ('99600000-0000-4000-8000-000000000005'::uuid, 'fu-jo@pocketpass.test', 'Fu Jo pgtap'),
    ('99600000-0000-4000-8000-000000000006'::uuid, 'fu-kim@pocketpass.test', 'Fu Kim pgtap'),
    ('99600000-0000-4000-8000-000000000007'::uuid, 'fu-lee@pocketpass.test', 'Fu Lee pgtap'),
    ('99600000-0000-4000-8000-000000000008'::uuid, 'fu-max@pocketpass.test', 'Fu Max pgtap'),
    ('99600000-0000-4000-8000-000000000009'::uuid, 'fu-dev@pocketpass.test', 'Fu Dev pgtap')
) as seed(id, email, name);

update public.profiles
set username = names.username, bio = 'bio', age = 30, country_code = 'GB'
from (
  values
    ('99600000-0000-4000-8000-000000000001'::uuid, 'pgtap.fay.996'),
    ('99600000-0000-4000-8000-000000000002'::uuid, 'pgtap.gus.996'),
    ('99600000-0000-4000-8000-000000000003'::uuid, 'pgtap.hal.996'),
    ('99600000-0000-4000-8000-000000000004'::uuid, 'pgtap.ivy.996'),
    ('99600000-0000-4000-8000-000000000005'::uuid, 'pgtap.jo.996'),
    ('99600000-0000-4000-8000-000000000006'::uuid, 'pgtap.kim.996'),
    ('99600000-0000-4000-8000-000000000007'::uuid, 'pgtap.lee.996'),
    ('99600000-0000-4000-8000-000000000008'::uuid, 'pgtap.max.996'),
    ('99600000-0000-4000-8000-000000000009'::uuid, 'pgtap.dev.996')
) as names(user_id, username)
where profiles.user_id = names.user_id;

insert into public.friendships (user_low, user_high, created_by, created_at)
values (
  '99600000-0000-4000-8000-000000000001',
  '99600000-0000-4000-8000-000000000002',
  '99600000-0000-4000-8000-000000000001',
  now() - interval '1 day'
);

insert into public.user_blocks (blocker_id, blocked_id)
values ('99600000-0000-4000-8000-000000000001', '99600000-0000-4000-8000-000000000005');

insert into public.friend_requests (id, requester_id, addressee_id, status, client_operation_id, created_at)
values
  (
    '99600000-0000-4000-8000-000000000401',
    '99600000-0000-4000-8000-000000000003',
    '99600000-0000-4000-8000-000000000001',
    'pending',
    '99600000-0000-4000-8000-000000000501',
    now() - interval '3 hours'
  ),
  (
    '99600000-0000-4000-8000-000000000402',
    '99600000-0000-4000-8000-000000000001',
    '99600000-0000-4000-8000-000000000006',
    'pending',
    '99600000-0000-4000-8000-000000000502',
    now() - interval '2 hours'
  ),
  (
    '99600000-0000-4000-8000-000000000403',
    '99600000-0000-4000-8000-000000000007',
    '99600000-0000-4000-8000-000000000001',
    'pending',
    '99600000-0000-4000-8000-000000000503',
    now() - interval '1 hour'
  ),
  (
    '99600000-0000-4000-8000-000000000404',
    '99600000-0000-4000-8000-000000000008',
    '99600000-0000-4000-8000-000000000001',
    'pending',
    '99600000-0000-4000-8000-000000000504',
    now() - interval '30 minutes'
  );

insert into public.friend_codes (user_id, code)
values
  ('99600000-0000-4000-8000-000000000004', '19960004'),
  ('99600000-0000-4000-8000-000000000005', '19960005')
on conflict (user_id) do update set code = excluded.code;

insert into public.conversations (
  id,
  kind,
  created_by,
  title,
  direct_user_low,
  direct_user_high,
  created_at,
  updated_at
)
values
  (
    '99600000-0000-4000-8000-000000000101',
    'direct',
    '99600000-0000-4000-8000-000000000001',
    null,
    '99600000-0000-4000-8000-000000000001',
    '99600000-0000-4000-8000-000000000002',
    now() - interval '2 hours',
    now() - interval '1 hour'
  ),
  (
    '99600000-0000-4000-8000-000000000102',
    'direct',
    '99600000-0000-4000-8000-000000000002',
    null,
    '99600000-0000-4000-8000-000000000002',
    '99600000-0000-4000-8000-000000000003',
    now() - interval '2 hours',
    now() - interval '1 hour'
  );

insert into public.conversation_members (conversation_id, user_id, role, joined_at)
values
  ('99600000-0000-4000-8000-000000000101', '99600000-0000-4000-8000-000000000001', 'owner', now() - interval '2 hours'),
  ('99600000-0000-4000-8000-000000000101', '99600000-0000-4000-8000-000000000002', 'member', now() - interval '2 hours'),
  ('99600000-0000-4000-8000-000000000102', '99600000-0000-4000-8000-000000000002', 'owner', now() - interval '2 hours'),
  ('99600000-0000-4000-8000-000000000102', '99600000-0000-4000-8000-000000000003', 'member', now() - interval '2 hours');

insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
values (
  'message-media',
  '99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/photo.png',
  '99600000-0000-4000-8000-000000000001',
  '99600000-0000-4000-8000-000000000001',
  '{"mimetype":"image/png","size":2048}'
);

insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
values ('message-media',
  '99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/animation.gif',
  '99600000-0000-4000-8000-000000000001', '99600000-0000-4000-8000-000000000001',
  '{"mimetype":"image/gif","size":2048}');

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99600000-0000-4000-8000-000000000009', true);

select pg_catalog.set_config(
  'fu_test.full',
  public.developer_create_app(
    'Fu Full pgtap',
    '',
    '',
    '',
    array['https://full.example/callback'],
    array['profile:read', 'friends:read', 'friends:write', 'messages:read', 'messages:write', 'notifications:read'],
    'confidential'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'fu_test.reader',
  public.developer_create_app(
    'Fu Reader pgtap',
    '',
    '',
    '',
    array['https://reader.example/callback'],
    array['profile:read', 'friends:read'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'fu_test.chat',
  public.developer_create_app(
    'Fu Chat pgtap',
    '',
    '',
    '',
    array['https://chat.example/callback'],
    array['messages:read', 'messages:write'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

select extensions.is(
  jsonb_array_length(public.developer_whoami() -> 'scopes'),
  12,
  'developer_whoami lists twelve scopes'
);

select extensions.is(
  public.developer_whoami() -> 'scopes' -> 2,
  '{"key":"friends:write","description":"Add and remove friends and answer friend requests as you"}'::jsonb,
  'friends:write follows friends:read in the catalog'
);

reset role;

update private.developer_apps
set rate_limit_per_second = 100000
where owner_user_id = '99600000-0000-4000-8000-000000000009';

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values
  (gen_random_uuid(), '99600000-0000-4000-8000-000000000001', current_setting('fu_test.full')::uuid, 'openid', now()),
  (gen_random_uuid(), '99600000-0000-4000-8000-000000000001', current_setting('fu_test.reader')::uuid, 'openid', now()),
  (gen_random_uuid(), '99600000-0000-4000-8000-000000000001', current_setting('fu_test.chat')::uuid, 'openid', now());

select extensions.is(
  private.api_scope_keys(),
  array['profile:read', 'friends:read', 'friends:write', 'messages:read', 'messages:write', 'groups:write', 'notifications:read', 'presence:read', 'presence:write', 'tokens:read', 'encounters:read', 'puzzles:read']::text[],
  'the scope catalog carries friends:write and groups:write in canonical order'
);

select extensions.is(
  private.api_normalize_scopes(array['friends:write', 'profile:read', 'friends:read']),
  array['profile:read', 'friends:read', 'friends:write']::text[],
  'normalisation keeps friends:write between friends:read and messages:read'
);

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99600000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('fu_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select pg_catalog.set_config(
  'fu_test.requests',
  public.api_v1_friends_requests_list('{}'::jsonb)::text,
  true
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('fu_test.requests')::jsonb) as keys(key)
  ),
  array['incoming', 'outgoing']::text[],
  'friends.requests_list returns incoming and outgoing'
);

select extensions.is(
  jsonb_array_length(current_setting('fu_test.requests')::jsonb -> 'incoming'),
  3,
  'three pending requests are incoming'
);

select extensions.is(
  jsonb_array_length(current_setting('fu_test.requests')::jsonb -> 'outgoing'),
  1,
  'one pending request is outgoing'
);

select extensions.is(
  current_setting('fu_test.requests')::jsonb -> 'incoming' -> 0 -> 'requester' ->> 'user_id',
  '99600000-0000-4000-8000-000000000008',
  'incoming requests are newest first'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('fu_test.requests')::jsonb -> 'incoming' -> 0) as keys(key)
  ),
  array['addressee', 'created_at', 'id', 'requester', 'responded_at', 'status']::text[],
  'a friend request object has the documented keys'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('fu_test.requests')::jsonb -> 'incoming' -> 0 -> 'requester') as keys(key)
  ),
  array['avatar_path', 'display_name', 'user_id', 'username']::text[],
  'request participants are lite profiles'
);

select extensions.is(
  current_setting('fu_test.requests')::jsonb -> 'outgoing' -> 0 ->> 'id',
  '99600000-0000-4000-8000-000000000402',
  'the outgoing request is the one Fay sent'
);

select extensions.is(
  public.api_v1_friends_requests_list('{"limit":1}'::jsonb) ->> 'hint',
  'UNKNOWN_FIELD',
  'friends.requests_list takes no arguments'
);

select pg_catalog.set_config(
  'fu_test.sent',
  public.api_v1_friends_request_send(
    '{"user_id":"99600000-0000-4000-8000-000000000004","client_operation_id":"99600000-0000-4000-8000-000000000701"}'::jsonb
  )::text,
  true
);

select extensions.is(
  current_setting('fu_test.sent')::jsonb -> 'request' ->> 'status',
  'pending',
  'friends.request_send creates a pending request'
);

select extensions.is(
  current_setting('fu_test.sent')::jsonb -> 'request' -> 'requester' ->> 'user_id',
  '99600000-0000-4000-8000-000000000001',
  'the connected user is the requester'
);

select extensions.is(
  current_setting('fu_test.sent')::jsonb -> 'request' -> 'addressee' ->> 'display_name',
  'Fu Ivy pgtap',
  'the addressee is the requested user'
);

select extensions.is(
  public.api_v1_friends_request_send(
    '{"user_id":"99600000-0000-4000-8000-000000000004","client_operation_id":"99600000-0000-4000-8000-000000000701"}'::jsonb
  ) -> 'request' ->> 'id',
  current_setting('fu_test.sent')::jsonb -> 'request' ->> 'id',
  'replaying the same operation returns the same request'
);

select extensions.is(
  public.api_v1_friends_request_send(
    '{"user_id":"99600000-0000-4000-8000-000000000007","client_operation_id":"99600000-0000-4000-8000-000000000701"}'::jsonb
  ) ->> 'hint',
  'DUPLICATE_OPERATION_ID',
  'reusing the operation id for another user is refused'
);

select extensions.is(
  public.api_v1_friends_request_send(
    '{"user_id":"99600000-0000-4000-8000-000000000001","client_operation_id":"99600000-0000-4000-8000-000000000702"}'::jsonb
  ) ->> 'hint',
  'SELF_TARGET',
  'a user cannot friend themselves'
);

select extensions.is(
  public.api_v1_friends_request_send(
    '{"user_id":"99600000-0000-4000-8000-000000000005","client_operation_id":"99600000-0000-4000-8000-000000000703"}'::jsonb
  ) ->> 'hint',
  'BLOCKED',
  'a blocked user cannot be requested'
);

select pg_catalog.set_config(
  'fu_test.already',
  public.api_v1_friends_request_send(
    '{"user_id":"99600000-0000-4000-8000-000000000002","client_operation_id":"99600000-0000-4000-8000-000000000704"}'::jsonb
  )::text,
  true
);

select extensions.is(
  current_setting('fu_test.already')::jsonb ->> 'hint',
  'ALREADY_FRIENDS',
  'an existing friend cannot be requested'
);

select extensions.is(
  current_setting('fu_test.already')::jsonb ->> 'code',
  'PT409',
  'already friends is a conflict'
);

select extensions.is(
  current_setting('response.status', true),
  '409',
  'the HTTP status is set to 409'
);

select extensions.is(
  public.api_v1_friends_request_send(
    '{"user_id":"99600000-0000-4000-8000-0000000000ff","client_operation_id":"99600000-0000-4000-8000-000000000705"}'::jsonb
  ) ->> 'hint',
  'PROFILE_NOT_FOUND',
  'an unknown user is not found'
);

select extensions.is(
  public.api_v1_friends_request_send(
    '{"client_operation_id":"99600000-0000-4000-8000-000000000706"}'::jsonb
  ) ->> 'hint',
  'MISSING_FIELD',
  'user_id is required'
);

select extensions.is(
  public.api_v1_friends_request_send(
    '{"user_id":"99600000-0000-4000-8000-000000000003","client_operation_id":"99600000-0000-4000-8000-000000000707"}'::jsonb
  ) -> 'request' ->> 'id',
  '99600000-0000-4000-8000-000000000401',
  'requesting someone who already asked returns their pending request'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99600000-0000-4000-8000-000000000004"}'::jsonb) -> 'profile' ->> 'username',
  'pgtap.ivy.996',
  'a pending outgoing request makes the profile visible'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99600000-0000-4000-8000-000000000008"}'::jsonb) -> 'profile' ->> 'username',
  'pgtap.max.996',
  'a pending incoming request makes the profile visible'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99600000-0000-4000-8000-000000000009"}'::jsonb) ->> 'hint',
  'PROFILE_NOT_FOUND',
  'a stranger stays hidden'
);

select pg_catalog.set_config(
  'fu_test.accepted',
  public.api_v1_friends_request_respond(
    '{"request_id":"99600000-0000-4000-8000-000000000401","accept":true,"client_operation_id":"99600000-0000-4000-8000-000000000711"}'::jsonb
  )::text,
  true
);

select extensions.is(
  current_setting('fu_test.accepted')::jsonb -> 'request' ->> 'status',
  'accepted',
  'friends.request_respond accepts a request'
);

select extensions.ok(
  (current_setting('fu_test.accepted')::jsonb -> 'request' ->> 'responded_at') is not null,
  'an accepted request carries responded_at'
);

select extensions.is(
  jsonb_array_length(public.api_v1_friends_requests_list('{}'::jsonb) -> 'incoming'),
  2,
  'an accepted request leaves the incoming list'
);

select extensions.ok(
  exists (
    select 1
    from jsonb_array_elements(public.api_v1_friends_list('{}'::jsonb) -> 'items') as item
    where item ->> 'user_id' = '99600000-0000-4000-8000-000000000003'
  ),
  'the new friend appears in friends.list'
);

select extensions.is(
  public.api_v1_friends_request_respond(
    '{"request_id":"99600000-0000-4000-8000-000000000401","accept":false,"client_operation_id":"99600000-0000-4000-8000-000000000712"}'::jsonb
  ) ->> 'hint',
  'REQUEST_CLOSED',
  'an answered request cannot be answered again'
);

select extensions.is(
  public.api_v1_friends_request_respond(
    '{"request_id":"99600000-0000-4000-8000-000000000402","accept":true,"client_operation_id":"99600000-0000-4000-8000-000000000713"}'::jsonb
  ) ->> 'hint',
  'NOT_ADDRESSEE',
  'only the addressee can answer'
);

select extensions.is(
  public.api_v1_friends_request_respond(
    '{"request_id":"99600000-0000-4000-8000-0000000004ff","accept":true,"client_operation_id":"99600000-0000-4000-8000-000000000714"}'::jsonb
  ) ->> 'hint',
  'REQUEST_NOT_FOUND',
  'an unknown request is not found'
);

select extensions.is(
  public.api_v1_friends_request_respond(
    '{"request_id":"99600000-0000-4000-8000-000000000403","client_operation_id":"99600000-0000-4000-8000-000000000715"}'::jsonb
  ) ->> 'hint',
  'MISSING_FIELD',
  'accept is required'
);

select extensions.is(
  public.api_v1_friends_request_respond(
    '{"request_id":"99600000-0000-4000-8000-000000000403","accept":"yes","client_operation_id":"99600000-0000-4000-8000-000000000715"}'::jsonb
  ) ->> 'hint',
  'INVALID_FIELD',
  'accept must be a boolean'
);

select pg_catalog.set_config(
  'fu_test.declined',
  public.api_v1_friends_request_respond(
    '{"request_id":"99600000-0000-4000-8000-000000000403","accept":false,"client_operation_id":"99600000-0000-4000-8000-000000000716"}'::jsonb
  )::text,
  true
);

select extensions.is(
  current_setting('fu_test.declined')::jsonb -> 'request' ->> 'status',
  'declined',
  'a declined request reports declined'
);

select extensions.ok(
  not exists (
    select 1
    from jsonb_array_elements(public.api_v1_friends_list('{}'::jsonb) -> 'items') as item
    where item ->> 'user_id' = '99600000-0000-4000-8000-000000000007'
  ),
  'declining creates no friendship'
);

select pg_catalog.set_config(
  'fu_test.cancelled',
  public.api_v1_friends_request_cancel(
    '{"request_id":"99600000-0000-4000-8000-000000000402"}'::jsonb
  )::text,
  true
);

select extensions.is(
  current_setting('fu_test.cancelled')::jsonb -> 'request' ->> 'status',
  'cancelled',
  'friends.request_cancel cancels an outgoing request'
);

select extensions.ok(
  (current_setting('fu_test.cancelled')::jsonb -> 'request' ->> 'responded_at') is not null,
  'a cancelled request carries responded_at'
);

select extensions.is(
  public.api_v1_friends_request_cancel(
    '{"request_id":"99600000-0000-4000-8000-000000000402"}'::jsonb
  ) -> 'request' ->> 'status',
  'cancelled',
  'cancelling twice is idempotent'
);

select extensions.is(
  public.api_v1_friends_request_cancel(
    '{"request_id":"99600000-0000-4000-8000-000000000404"}'::jsonb
  ) ->> 'hint',
  'NOT_REQUESTER',
  'the addressee cannot cancel'
);

select extensions.is(
  public.api_v1_friends_request_cancel(
    '{"request_id":"99600000-0000-4000-8000-000000000401"}'::jsonb
  ) ->> 'hint',
  'NOT_REQUESTER',
  'an accepted incoming request cannot be cancelled by the addressee'
);

select extensions.is(
  public.api_v1_friends_request_cancel(
    '{"request_id":"99600000-0000-4000-8000-0000000004ff"}'::jsonb
  ) ->> 'hint',
  'REQUEST_NOT_FOUND',
  'cancelling an unknown request is not found'
);

select extensions.is(
  public.api_v1_friends_request_cancel(
    '{"request_id":"99600000-0000-4000-8000-000000000102"}'::jsonb
  ) ->> 'hint',
  'REQUEST_NOT_FOUND',
  'a request between other users is not found'
);

select pg_catalog.set_config(
  'fu_test.removed',
  public.api_v1_friends_remove(
    '{"user_id":"99600000-0000-4000-8000-000000000002","client_operation_id":"99600000-0000-4000-8000-000000000721"}'::jsonb
  )::text,
  true
);

select extensions.is(
  current_setting('fu_test.removed')::jsonb,
  '{"removed":true}'::jsonb,
  'friends.remove removes a friend'
);

select extensions.ok(
  not exists (
    select 1
    from jsonb_array_elements(public.api_v1_friends_list('{}'::jsonb) -> 'items') as item
    where item ->> 'user_id' = '99600000-0000-4000-8000-000000000002'
  ),
  'the friendship is gone'
);

select extensions.is(
  public.api_v1_friends_remove(
    '{"user_id":"99600000-0000-4000-8000-000000000002","client_operation_id":"99600000-0000-4000-8000-000000000722"}'::jsonb
  ),
  '{"removed":false}'::jsonb,
  'removing again reports nothing removed'
);

select extensions.is(
  public.api_v1_friends_remove(
    '{"user_id":"99600000-0000-4000-8000-000000000001","client_operation_id":"99600000-0000-4000-8000-000000000723"}'::jsonb
  ) ->> 'hint',
  'SELF_TARGET',
  'a user cannot remove themselves'
);

select pg_catalog.set_config(
  'fu_test.code',
  public.api_v1_friends_code_get('{}'::jsonb)::text,
  true
);

select extensions.ok(
  (current_setting('fu_test.code')::jsonb ->> 'code') ~ '^[0-9]{8}$',
  'friends.code_get returns an eight digit code'
);

select pg_catalog.set_config(
  'fu_test.resolved',
  public.api_v1_friends_code_resolve('{"code":"19960004"}'::jsonb)::text,
  true
);

select extensions.is(
  current_setting('fu_test.resolved')::jsonb -> 'profile' ->> 'user_id',
  '99600000-0000-4000-8000-000000000004',
  'friends.code_resolve finds the profile behind a code'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('fu_test.resolved')::jsonb -> 'profile') as keys(key)
  ),
  array['age', 'avatar_path', 'bio', 'country_code', 'created_at', 'display_name', 'setup_complete', 'updated_at', 'user_id', 'username']::text[],
  'a resolved code returns a full profile'
);

select extensions.is(
  public.api_v1_friends_code_resolve('{"code":" 19960004 "}'::jsonb) -> 'profile' ->> 'user_id',
  '99600000-0000-4000-8000-000000000004',
  'surrounding whitespace is trimmed from the code'
);

select extensions.is(
  public.api_v1_friends_code_resolve('{"code":"abc"}'::jsonb) ->> 'hint',
  'INVALID_CODE',
  'a malformed code is rejected'
);

select extensions.is(
  public.api_v1_friends_code_resolve('{"code":"00000000"}'::jsonb) ->> 'hint',
  'CODE_NOT_FOUND',
  'an unused code is not found'
);

select extensions.is(
  public.api_v1_friends_code_resolve('{"code":"19960005"}'::jsonb) ->> 'hint',
  'CODE_NOT_FOUND',
  'a blocked user code is not found'
);

select extensions.is(
  public.api_v1_friends_code_resolve(
    jsonb_build_object('code', current_setting('fu_test.code')::jsonb ->> 'code')
  ) ->> 'hint',
  'CODE_NOT_FOUND',
  'a user cannot resolve their own code'
);

select extensions.is(
  public.api_v1_friends_code_resolve('{}'::jsonb) ->> 'hint',
  'MISSING_FIELD',
  'code is required'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000799","attachment":{"path":"99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/animation.gif","mime_type":"image/gif"}}'::jsonb
  ) -> 'message' -> 'attachment' ->> 'mime_type',
  'image/gif',
  'messages.send preserves an uploaded GIF attachment'
);

select pg_catalog.set_config(
  'fu_test.photo',
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000731","attachment":{"path":"99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/photo.png","mime_type":"image/png"}}'::jsonb
  )::text,
  true
);

select extensions.is(
  current_setting('fu_test.photo')::jsonb -> 'message' -> 'attachment',
  '{"path":"99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/photo.png","mime_type":"image/png"}'::jsonb,
  'messages.send stores an uploaded attachment'
);

select extensions.is(
  current_setting('fu_test.photo')::jsonb -> 'message' ->> 'body',
  '📷',
  'an image without text gets the placeholder body'
);

select extensions.is(
  (
    select item -> 'attachment' ->> 'path'
    from jsonb_array_elements(
      public.api_v1_messages_list('{"conversation_id":"99600000-0000-4000-8000-000000000101"}'::jsonb) -> 'items'
    ) as item
    where item ->> 'id' = current_setting('fu_test.photo')::jsonb -> 'message' ->> 'id'
  ),
  '99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/photo.png',
  'the attachment is persisted and listed'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000732","body":"Look","attachment":{"path":"99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/photo.png","mime_type":"image/png"}}'::jsonb
  ) -> 'message' ->> 'body',
  'Look',
  'a caption is kept alongside the attachment'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000733","attachment":{"path":"99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/photo.png","mime_type":"image/svg+xml"}}'::jsonb
  ) ->> 'hint',
  'UNSUPPORTED_MEDIA',
  'unsupported image formats are rejected'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000734","attachment":{"path":"99600000-0000-4000-8000-000000000002/99600000-0000-4000-8000-000000000101/photo.png","mime_type":"image/png"}}'::jsonb
  ) ->> 'hint',
  'INVALID_ATTACHMENT_PATH',
  'an attachment from another folder is refused'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000735","attachment":{"path":"99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000102/photo.png","mime_type":"image/png"}}'::jsonb
  ) ->> 'hint',
  'INVALID_ATTACHMENT_PATH',
  'an attachment for another conversation is refused'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000736","attachment":{"path":"99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/missing.png","mime_type":"image/png"}}'::jsonb
  ) ->> 'hint',
  'ATTACHMENT_NOT_FOUND',
  'an attachment that was never uploaded is refused'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000737","attachment":"photo.png"}'::jsonb
  ) ->> 'hint',
  'INVALID_FIELD',
  'attachment must be an object'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000738","attachment":{"path":"x","mime_type":"image/png","size":1}}'::jsonb
  ) ->> 'hint',
  'UNKNOWN_FIELD',
  'attachment rejects unknown keys'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000739"}'::jsonb
  ) ->> 'hint',
  'MISSING_FIELD',
  'body is required without an attachment'
);

select extensions.is(
  public.api_v1_messages_send(
    '{"conversation_id":"99600000-0000-4000-8000-000000000101","client_operation_id":"99600000-0000-4000-8000-000000000740","body":"Plain"}'::jsonb
  ) -> 'message' -> 'attachment',
  'null'::jsonb,
  'a text message still has no attachment'
);

select extensions.lives_ok(
  $$
    insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
    values (
      'message-media',
      '99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/api.png',
      '99600000-0000-4000-8000-000000000001',
      '99600000-0000-4000-8000-000000000001',
      '{"mimetype":"image/png","size":2048}'
    );
  $$,
  'a messages:write app uploads into the user folder of their conversation'
);

select extensions.throws_ok(
  $$
    insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
    values (
      'message-media',
      '99600000-0000-4000-8000-000000000002/99600000-0000-4000-8000-000000000101/api.png',
      '99600000-0000-4000-8000-000000000001',
      '99600000-0000-4000-8000-000000000001',
      '{"mimetype":"image/png","size":2048}'
    );
  $$,
  '42501',
  'new row violates row-level security policy for table "objects"',
  'an app cannot upload into another user folder'
);

select extensions.throws_ok(
  $$
    insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
    values (
      'message-media',
      '99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000102/api.png',
      '99600000-0000-4000-8000-000000000001',
      '99600000-0000-4000-8000-000000000001',
      '{"mimetype":"image/png","size":2048}'
    );
  $$,
  '42501',
  'new row violates row-level security policy for table "objects"',
  'an app cannot upload for a conversation the user is not in'
);

select extensions.throws_ok(
  $$
    insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
    values (
      'avatars',
      '99600000-0000-4000-8000-000000000001/avatar.png',
      '99600000-0000-4000-8000-000000000001',
      '99600000-0000-4000-8000-000000000001',
      '{"mimetype":"image/png","size":2048}'
    );
  $$,
  '42501',
  'new row violates row-level security policy for table "objects"',
  'an app cannot upload avatars'
);

select extensions.throws_ok(
  $$
    delete from storage.objects
    where bucket_id = 'message-media'
      and name = '99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/api.png';
  $$,
  '42501',
  null,
  'an app cannot delete uploads'
);

select extensions.ok(
  private.api_can_access_realtime_topic('conversation:99600000-0000-4000-8000-000000000101'),
  'a messages:read app may follow its conversation topic'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('conversation:99600000-0000-4000-8000-000000000102'),
  'a conversation the user is not in stays closed'
);

select extensions.ok(
  private.api_can_access_realtime_topic('notifications:99600000-0000-4000-8000-000000000001'),
  'a notifications:read app may follow the user notifications topic'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('notifications:99600000-0000-4000-8000-000000000002'),
  'another user notifications topic stays closed'
);

select extensions.ok(
  private.api_can_access_realtime_topic('friends:99600000-0000-4000-8000-000000000001'),
  'a friends:read app may follow the user friends topic'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('friends:99600000-0000-4000-8000-000000000002'),
  'another user friends topic stays closed'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('tokens:99600000-0000-4000-8000-000000000001'),
  'the tokens topic is closed without tokens:read'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('encounters:99600000-0000-4000-8000-000000000001'),
  'the encounters topic is closed without encounters:read'
);

select extensions.ok(
  not private.api_can_access_realtime_topic(
    'friend-presence:99600000-0000-4000-8000-000000000001:99600000-0000-4000-8000-000000000003'
  ),
  'friend presence is not a broadcast topic'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('app_updates'),
  'the app_updates topic is first-party only'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('CONVERSATION:99600000-0000-4000-8000-000000000101'),
  'topic names are case sensitive'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99600000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('fu_test.reader'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  jsonb_array_length(public.api_v1_friends_requests_list('{}'::jsonb) -> 'incoming'),
  1,
  'a friends:read app lists requests'
);

select extensions.is(
  public.api_v1_friends_request_send(
    '{"user_id":"99600000-0000-4000-8000-000000000006","client_operation_id":"99600000-0000-4000-8000-000000000751"}'::jsonb
  ) ->> 'hint',
  'SCOPE_REQUIRED',
  'friends.request_send needs friends:write'
);

select extensions.is(
  public.api_v1_friends_request_respond(
    '{"request_id":"99600000-0000-4000-8000-000000000404","accept":true,"client_operation_id":"99600000-0000-4000-8000-000000000752"}'::jsonb
  ) ->> 'hint',
  'SCOPE_REQUIRED',
  'friends.request_respond needs friends:write'
);

select extensions.is(
  public.api_v1_friends_request_cancel('{"request_id":"99600000-0000-4000-8000-000000000404"}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'friends.request_cancel needs friends:write'
);

select extensions.is(
  public.api_v1_friends_remove(
    '{"user_id":"99600000-0000-4000-8000-000000000003","client_operation_id":"99600000-0000-4000-8000-000000000753"}'::jsonb
  ) ->> 'hint',
  'SCOPE_REQUIRED',
  'friends.remove needs friends:write'
);

select extensions.is(
  public.api_v1_friends_code_get('{}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'friends.code_get needs friends:write'
);

select extensions.is(
  public.api_v1_friends_code_resolve('{"code":"19960004"}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'friends.code_resolve needs friends:write'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('conversation:99600000-0000-4000-8000-000000000101'),
  'without messages:read the conversation topic stays closed'
);

select extensions.ok(
  private.api_can_access_realtime_topic('friends:99600000-0000-4000-8000-000000000001'),
  'friends:read alone opens the friends topic'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('notifications:99600000-0000-4000-8000-000000000001'),
  'without notifications:read the notifications topic stays closed'
);

select extensions.throws_ok(
  $$
    insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
    values (
      'message-media',
      '99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/reader.png',
      '99600000-0000-4000-8000-000000000001',
      '99600000-0000-4000-8000-000000000001',
      '{"mimetype":"image/png","size":2048}'
    );
  $$,
  '42501',
  'new row violates row-level security policy for table "objects"',
  'an app without messages:write cannot upload'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99600000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('fu_test.chat'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_friends_requests_list('{}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'friends.requests_list needs friends:read'
);

select extensions.ok(
  private.api_can_access_realtime_topic('conversation:99600000-0000-4000-8000-000000000101'),
  'a chat app may follow its conversation topic'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('friends:99600000-0000-4000-8000-000000000001'),
  'a chat app may not follow the friends topic'
);

reset role;

select extensions.is(
  (
    select request.status::text
    from public.friend_requests as request
    where request.id = '99600000-0000-4000-8000-000000000402'
  ),
  'cancelled',
  'the cancelled request is stored as cancelled'
);

select extensions.ok(
  (
    select notification.deleted_at is not null
    from public.notifications as notification
    where notification.recipient_id = '99600000-0000-4000-8000-000000000006'
      and notification.friend_request_id = '99600000-0000-4000-8000-000000000402'
      and notification.kind = 'friend_request'
  ),
  'cancelling hides the addressee notification'
);

select extensions.ok(
  exists (
    select 1
    from public.notifications as notification
    where notification.recipient_id = '99600000-0000-4000-8000-000000000004'
      and notification.kind = 'friend_request'
      and notification.actor_id = '99600000-0000-4000-8000-000000000001'
      and notification.deleted_at is null
  ),
  'sending a request notifies the addressee'
);

select extensions.is(
  (
    select request.status::text
    from public.friend_requests as request
    where request.id = '99600000-0000-4000-8000-000000000403'
  ),
  'rejected',
  'a declined request is stored with the first-party status'
);

select extensions.is(
  (
    select count(*)
    from storage.objects as object
    where object.bucket_id = 'message-media'
      and object.name like '99600000-0000-4000-8000-%'
  ),
  3::bigint,
  'exactly one upload was added to the two seeded image objects'
);

select extensions.ok(
  pg_catalog.has_schema_privilege('api_client', 'realtime', 'usage'),
  'api_client can use the realtime schema'
);

select extensions.ok(
  pg_catalog.has_table_privilege('api_client', 'realtime.messages', 'select'),
  'api_client can read realtime messages'
);

select extensions.ok(
  pg_catalog.has_table_privilege('api_client', 'realtime.messages', 'insert')
    and not pg_catalog.has_table_privilege('api_client', 'realtime.messages', 'update, delete'),
  'api_client may insert presence rows but not update or delete realtime messages'
);

select extensions.is(
  (
    select array_agg(policyname::text || ':' || cmd::text order by policyname::text collate "C")
    from pg_catalog.pg_policies
    where schemaname = 'realtime'
      and tablename = 'messages'
      and 'api_client' = any (roles)
  ),
  array['pocketpass_api_presence_track:INSERT', 'pocketpass_api_realtime_read:SELECT']::text[],
  'api_client has exactly a presence-track and a read policy on realtime.messages'
);

select extensions.ok(
  (
    select qual like '%extension = ''broadcast''%' and qual like '%extension = ''presence''%'
    from pg_catalog.pg_policies
    where schemaname = 'realtime'
      and tablename = 'messages'
      and policyname = 'pocketpass_api_realtime_read'
  ),
  'the realtime read policy covers broadcast and presence'
);

select extensions.ok(
  (
    select with_check like '%extension = ''presence''%' and with_check not like '%broadcast%'
    from pg_catalog.pg_policies
    where schemaname = 'realtime'
      and tablename = 'messages'
      and policyname = 'pocketpass_api_presence_track'
  ),
  'the presence-track policy is presence only'
);

select extensions.is(
  (
    select array_agg(policyname::text || ':' || cmd::text order by policyname::text collate "C")
    from pg_catalog.pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and 'api_client' = any (roles)
  ),
  array[
    'pocketpass_api_avatars_read:SELECT',
    'pocketpass_api_message_media_insert:INSERT',
    'pocketpass_api_message_media_read:SELECT'
  ]::text[],
  'api_client has two read policies and one insert policy on storage.objects'
);

select extensions.ok(
  pg_catalog.has_table_privilege('api_client', 'storage.objects', 'insert')
    and not pg_catalog.has_table_privilege('api_client', 'storage.objects', 'update')
    and not pg_catalog.has_table_privilege('api_client', 'storage.objects', 'delete'),
  'api_client may insert but not update or delete storage objects'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('authenticated', 'private.api_can_access_realtime_topic(text)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'private.api_can_access_realtime_topic(text)', 'execute')
    and not pg_catalog.has_function_privilege('authenticated', 'private.api_can_read_presence_topic(text)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'private.api_can_read_presence_topic(text)', 'execute')
    and not pg_catalog.has_function_privilege('authenticated', 'private.api_can_track_presence_topic(text)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'private.api_can_track_presence_topic(text)', 'execute'),
  'first-party roles do not execute the api realtime predicates'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('authenticated', 'private.api_can_write_object(text, text)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'private.api_can_write_object(text, text)', 'execute'),
  'first-party roles do not execute the api upload predicate'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99600000-0000-4000-8000-000000000001', true);

select extensions.ok(
  private.can_access_realtime_topic(
    'conversation:99600000-0000-4000-8000-000000000101',
    '99600000-0000-4000-8000-000000000001'
  ),
  'the first-party realtime predicate is unchanged'
);

select extensions.throws_ok(
  $$select public.api_v1_friends_requests_list('{}'::jsonb)$$,
  '42501',
  null,
  'authenticated cannot call friends.requests_list'
);

select extensions.throws_ok(
  $$select public.api_v1_friends_request_send('{}'::jsonb)$$,
  '42501',
  null,
  'authenticated cannot call friends.request_send'
);

select extensions.throws_ok(
  $$select public.api_v1_friends_code_resolve('{}'::jsonb)$$,
  '42501',
  null,
  'authenticated cannot call friends.code_resolve'
);

reset role;

update auth.oauth_consents
set revoked_at = now()
where user_id = '99600000-0000-4000-8000-000000000001'
  and client_id = current_setting('fu_test.full')::uuid;

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99600000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('fu_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.ok(
  not private.api_can_access_realtime_topic('conversation:99600000-0000-4000-8000-000000000101'),
  'a revoked consent closes realtime topics'
);

select extensions.is(
  public.api_v1_friends_requests_list('{}'::jsonb) ->> 'hint',
  'CONSENT_REVOKED',
  'a revoked consent closes the friend endpoints'
);

select extensions.throws_ok(
  $$
    insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
    values (
      'message-media',
      '99600000-0000-4000-8000-000000000001/99600000-0000-4000-8000-000000000101/revoked.png',
      '99600000-0000-4000-8000-000000000001',
      '99600000-0000-4000-8000-000000000001',
      '{"mimetype":"image/png","size":2048}'
    );
  $$,
  '42501',
  'new row violates row-level security policy for table "objects"',
  'a revoked consent closes uploads'
);

reset role;

select * from extensions.finish();

rollback;
