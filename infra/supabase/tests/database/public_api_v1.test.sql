begin;

set local search_path = public, extensions;

select extensions.plan(223);

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
    ('99800000-0000-4000-8000-000000000001'::uuid, 'api-alice@pocketpass.test', 'Api Alice pgtap'),
    ('99800000-0000-4000-8000-000000000002'::uuid, 'api-bob@pocketpass.test', 'Api Bob pgtap'),
    ('99800000-0000-4000-8000-000000000003'::uuid, 'api-carol@pocketpass.test', 'Api Carol pgtap'),
    ('99800000-0000-4000-8000-000000000004'::uuid, 'api-dave@pocketpass.test', 'Api Dave pgtap'),
    ('99800000-0000-4000-8000-000000000005'::uuid, 'api-erin@pocketpass.test', 'Api Erin pgtap'),
    ('99800000-0000-4000-8000-000000000009'::uuid, 'api-dev@pocketpass.test', 'Api Dev pgtap')
) as seed(id, email, name);

update public.profiles
set username = names.username, bio = names.bio, age = 27, country_code = 'GB'
from (
  values
    ('99800000-0000-4000-8000-000000000001'::uuid, 'pgtap.alice.998', 'Alice bio'),
    ('99800000-0000-4000-8000-000000000002'::uuid, 'pgtap.bob.998', 'Bob bio'),
    ('99800000-0000-4000-8000-000000000003'::uuid, 'pgtap.carol.998', 'Carol bio'),
    ('99800000-0000-4000-8000-000000000004'::uuid, 'pgtap.dave.998', 'Dave bio'),
    ('99800000-0000-4000-8000-000000000005'::uuid, 'pgtap.erin.998', 'Erin bio'),
    ('99800000-0000-4000-8000-000000000009'::uuid, 'pgtap.dev.998', 'Dev bio')
) as names(user_id, username, bio)
where profiles.user_id = names.user_id;

update public.profiles
set avatar_path = '99800000-0000-4000-8000-000000000001/avatar.png'
where user_id = '99800000-0000-4000-8000-000000000001';

insert into public.friendships (user_low, user_high, created_by, created_at)
values (
  '99800000-0000-4000-8000-000000000001',
  '99800000-0000-4000-8000-000000000002',
  '99800000-0000-4000-8000-000000000001',
  now() - interval '1 day'
);

insert into public.user_blocks (blocker_id, blocked_id)
values ('99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000005');

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
    '99800000-0000-4000-8000-000000000101',
    'direct',
    '99800000-0000-4000-8000-000000000001',
    null,
    '99800000-0000-4000-8000-000000000001',
    '99800000-0000-4000-8000-000000000002',
    now() - interval '2 hours',
    now() - interval '1 hour'
  ),
  (
    '99800000-0000-4000-8000-000000000102',
    'group',
    '99800000-0000-4000-8000-000000000001',
    'Api Group pgtap',
    null,
    null,
    now() - interval '3 hours',
    now() - interval '2 hours'
  ),
  (
    '99800000-0000-4000-8000-000000000103',
    'direct',
    '99800000-0000-4000-8000-000000000003',
    null,
    '99800000-0000-4000-8000-000000000003',
    '99800000-0000-4000-8000-000000000004',
    now() - interval '3 hours',
    now() - interval '3 hours'
  );

insert into public.conversation_members (conversation_id, user_id, role, joined_at)
values
  ('99800000-0000-4000-8000-000000000101', '99800000-0000-4000-8000-000000000001', 'owner', now() - interval '2 hours'),
  ('99800000-0000-4000-8000-000000000101', '99800000-0000-4000-8000-000000000002', 'member', now() - interval '2 hours'),
  ('99800000-0000-4000-8000-000000000102', '99800000-0000-4000-8000-000000000001', 'owner', now() - interval '3 hours'),
  ('99800000-0000-4000-8000-000000000102', '99800000-0000-4000-8000-000000000004', 'member', now() - interval '3 hours'),
  ('99800000-0000-4000-8000-000000000102', '99800000-0000-4000-8000-000000000005', 'member', now() - interval '3 hours'),
  ('99800000-0000-4000-8000-000000000103', '99800000-0000-4000-8000-000000000003', 'owner', now() - interval '3 hours'),
  ('99800000-0000-4000-8000-000000000103', '99800000-0000-4000-8000-000000000004', 'member', now() - interval '3 hours');

insert into public.messages (id, conversation_id, sender_id, client_operation_id, body, reply_to_id, metadata, created_at)
values
  (
    '99800000-0000-4000-8000-000000000206',
    '99800000-0000-4000-8000-000000000103',
    '99800000-0000-4000-8000-000000000003',
    '99800000-0000-4000-8000-000000000306',
    'Private',
    null,
    '{}'::jsonb,
    now() - interval '10 minutes'
  ),
  (
    '99800000-0000-4000-8000-000000000201',
    '99800000-0000-4000-8000-000000000101',
    '99800000-0000-4000-8000-000000000002',
    '99800000-0000-4000-8000-000000000301',
    'One',
    null,
    '{}'::jsonb,
    now() - interval '5 minutes'
  ),
  (
    '99800000-0000-4000-8000-000000000202',
    '99800000-0000-4000-8000-000000000101',
    '99800000-0000-4000-8000-000000000001',
    '99800000-0000-4000-8000-000000000302',
    'Two',
    null,
    '{"attachment":{"path":"99800000-0000-4000-8000-000000000001/99800000-0000-4000-8000-000000000101/photo.png","mime_type":"image/png"}}'::jsonb,
    now() - interval '4 minutes'
  ),
  (
    '99800000-0000-4000-8000-000000000203',
    '99800000-0000-4000-8000-000000000101',
    '99800000-0000-4000-8000-000000000002',
    '99800000-0000-4000-8000-000000000303',
    'Three',
    null,
    '{}'::jsonb,
    now() - interval '3 minutes'
  ),
  (
    '99800000-0000-4000-8000-000000000204',
    '99800000-0000-4000-8000-000000000101',
    '99800000-0000-4000-8000-000000000001',
    '99800000-0000-4000-8000-000000000304',
    'Four',
    '99800000-0000-4000-8000-000000000202',
    '{}'::jsonb,
    now() - interval '2 minutes'
  ),
  (
    '99800000-0000-4000-8000-000000000205',
    '99800000-0000-4000-8000-000000000101',
    '99800000-0000-4000-8000-000000000002',
    '99800000-0000-4000-8000-000000000305',
    'Five',
    null,
    '{}'::jsonb,
    now() - interval '1 minute'
  );

update public.messages
set body = 'Three edited', edited_at = now() - interval '30 seconds'
where id = '99800000-0000-4000-8000-000000000203';

update public.messages
set body = 'Message deleted', metadata = '{}'::jsonb, deleted_at = now() - interval '20 seconds'
where id = '99800000-0000-4000-8000-000000000201';

insert into public.friend_requests (id, requester_id, addressee_id, client_operation_id)
values (
  '99800000-0000-4000-8000-000000000401',
  '99800000-0000-4000-8000-000000000003',
  '99800000-0000-4000-8000-000000000001',
  '99800000-0000-4000-8000-000000000402'
);

insert into public.notifications (id, recipient_id, kind, title, body, created_at, updated_at, deleted_at)
values
  (
    '99800000-0000-4000-8000-000000000500',
    '99800000-0000-4000-8000-000000000001',
    'system',
    'Old news',
    'already removed',
    now() - interval '4 hours',
    now() - interval '4 hours',
    now() - interval '1 hour'
  ),
  (
    '99800000-0000-4000-8000-000000000501',
    '99800000-0000-4000-8000-000000000001',
    'system',
    'System one',
    'first',
    now() - interval '3 hours',
    now() - interval '3 hours',
    null
  ),
  (
    '99800000-0000-4000-8000-000000000502',
    '99800000-0000-4000-8000-000000000001',
    'system',
    'System two',
    'second',
    now() - interval '2 hours',
    now() - interval '2 hours',
    null
  ),
  (
    '99800000-0000-4000-8000-000000000503',
    '99800000-0000-4000-8000-000000000001',
    'system',
    'System three',
    'third',
    now() - interval '1 hour',
    now() - interval '1 hour',
    null
  );

select pg_catalog.set_config(
  'api_test.expected_message_id',
  substr(
    encode(
      extensions.digest(
        '99800000-0000-4000-8000-000000000001:99800000-0000-4000-8000-000000000701',
        'sha256'
      ),
      'hex'
    ),
    1,
    32
  )::uuid::text,
  true
);

select pg_catalog.set_config(
  'api_test.bob_notification',
  (
    select notification.id::text
    from public.notifications as notification
    where notification.recipient_id = '99800000-0000-4000-8000-000000000002'
      and notification.kind = 'message'
      and notification.conversation_id = '99800000-0000-4000-8000-000000000101'
  ),
  true
);

insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
values
  (
    'avatars',
    '99800000-0000-4000-8000-000000000001/avatar.png',
    '99800000-0000-4000-8000-000000000001',
    '99800000-0000-4000-8000-000000000001',
    '{"mimetype":"image/png","size":1024}'
  ),
  (
    'avatars',
    '99800000-0000-4000-8000-000000000003/avatar.png',
    '99800000-0000-4000-8000-000000000003',
    '99800000-0000-4000-8000-000000000003',
    '{"mimetype":"image/png","size":1024}'
  ),
  (
    'avatars',
    '99800000-0000-4000-8000-000000000005/avatar.png',
    '99800000-0000-4000-8000-000000000005',
    '99800000-0000-4000-8000-000000000005',
    '{"mimetype":"image/png","size":1024}'
  ),
  (
    'message-media',
    '99800000-0000-4000-8000-000000000001/99800000-0000-4000-8000-000000000101/photo.png',
    '99800000-0000-4000-8000-000000000001',
    '99800000-0000-4000-8000-000000000001',
    '{"mimetype":"image/png","size":2048}'
  ),
  (
    'message-media',
    '99800000-0000-4000-8000-000000000003/99800000-0000-4000-8000-000000000103/photo.png',
    '99800000-0000-4000-8000-000000000003',
    '99800000-0000-4000-8000-000000000003',
    '{"mimetype":"image/png","size":2048}'
  );

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99800000-0000-4000-8000-000000000009', true);

select pg_catalog.set_config(
  'api_test.full',
  public.developer_create_app(
    'Api Full pgtap',
    '',
    '',
    '',
    array['https://full.example/callback'],
    array['notifications:read', 'messages:write', 'messages:read', 'friends:read', 'profile:read'],
    'confidential'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'api_test.limited',
  public.developer_create_app(
    'Api Limited pgtap',
    '',
    '',
    '',
    array['https://limited.example/callback'],
    array['profile:read'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'api_test.rev',
  public.developer_create_app(
    'Api Revocable pgtap',
    '',
    '',
    '',
    array['https://revocable.example/callback'],
    array['profile:read', 'messages:read'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'api_test.susp',
  public.developer_create_app(
    'Api Suspendable pgtap',
    '',
    '',
    '',
    array['https://suspendable.example/callback'],
    array['profile:read'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

reset role;

update private.developer_apps
set rate_limit_per_second = 100000
where owner_user_id = '99800000-0000-4000-8000-000000000009';

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', current_setting('api_test.full')::uuid, 'openid', now()),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', current_setting('api_test.limited')::uuid, 'openid', now()),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', current_setting('api_test.rev')::uuid, 'openid', now()),
  (gen_random_uuid(), '99800000-0000-4000-8000-000000000001', current_setting('api_test.susp')::uuid, 'openid', now());

insert into auth.sessions (id, user_id, created_at, updated_at, aal, oauth_client_id, scopes)
values
  (
    '99800000-0000-4000-8000-000000000601',
    '99800000-0000-4000-8000-000000000001',
    now(),
    now(),
    'aal1'::auth.aal_level,
    null,
    null
  ),
  (
    '99800000-0000-4000-8000-000000000602',
    '99800000-0000-4000-8000-000000000001',
    now(),
    now(),
    'aal1'::auth.aal_level,
    current_setting('api_test.full')::uuid,
    'openid'
  );

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'code',
  'PT401',
  'a client token without client_id is refused'
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'hint',
  'API_TOKEN_REQUIRED',
  'the missing client id is reported as API_TOKEN_REQUIRED'
);

select extensions.is(
  current_setting('response.status', true),
  '401',
  'the HTTP status is set to 401'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'authenticated',
    'client_id', current_setting('api_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'hint',
  'API_TOKEN_REQUIRED',
  'a token whose role is not api_client is refused'
);

reset role;

select extensions.is(
  (
    select count(*)
    from private.api_rate_buckets as bucket
    where bucket.user_id = '99800000-0000-4000-8000-000000000001'
  ),
  0::bigint,
  'refused tokens are not metered'
);

set local role api_client;
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', '99800000-0000-4000-8000-000000000999',
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'code',
  'PT404',
  'an unregistered client id is refused'
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'hint',
  'APP_NOT_FOUND',
  'the unregistered client is reported as APP_NOT_FOUND'
);

select extensions.is(
  current_setting('response.status', true),
  '404',
  'the HTTP status is set to 404'
);

reset role;

select extensions.ok(
  (
    select stat.requests = 2 and stat.denied = 2
    from private.api_usage as stat
    where stat.client_id = '99800000-0000-4000-8000-000000000999'
      and stat.user_id = '99800000-0000-4000-8000-000000000001'
      and stat.day = (now() at time zone 'utc')::date
  ),
  'calls for an unregistered client are metered and denied'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99800000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  $$select public.api_v1_me_get('{}'::jsonb)$$,
  '42501',
  null,
  'a first-party session cannot call the public API'
);

reset role;
set local role anon;
select pg_catalog.set_config('request.jwt.claim.role', 'anon', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select extensions.throws_ok(
  $$select public.api_v1_me_get('{}'::jsonb)$$,
  '42501',
  null,
  'an anonymous request cannot call the public API'
);

reset role;
set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select pg_catalog.set_config('api_test.session', public.api_v1_session_get('{}'::jsonb)::text, true);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('api_test.session')::jsonb) as keys(key)
  ),
  array['app', 'client_id', 'rate_limit', 'scopes', 'unread_total', 'user_id']::text[],
  'session.get has the documented keys'
);

select extensions.is(
  current_setting('api_test.session')::jsonb ->> 'user_id',
  '99800000-0000-4000-8000-000000000001',
  'session.get reports the user'
);

select extensions.is(
  current_setting('api_test.session')::jsonb ->> 'client_id',
  current_setting('api_test.full'),
  'session.get reports the client'
);

select extensions.is(
  current_setting('api_test.session')::jsonb -> 'app' ->> 'name',
  'Api Full pgtap',
  'session.get names the app'
);

select extensions.is(
  current_setting('api_test.session')::jsonb -> 'scopes',
  '["profile:read","friends:read","messages:read","messages:write","notifications:read"]'::jsonb,
  'session.get lists the app scopes in canonical order'
);

select extensions.is(
  (current_setting('api_test.session')::jsonb -> 'rate_limit' ->> 'per_minute')::integer,
  120,
  'session.get reports the per-minute limit'
);

select extensions.is(
  (current_setting('api_test.session')::jsonb -> 'rate_limit' ->> 'remaining')::integer,
  119,
  'session.get counts itself against the limit'
);

select extensions.is(
  (current_setting('api_test.session')::jsonb -> 'rate_limit' ->> 'reset_at')::timestamptz,
  date_trunc('minute', now()) + interval '1 minute',
  'session.get reports the end of the current minute'
);

select extensions.is(
  (current_setting('api_test.session')::jsonb ->> 'unread_total')::integer,
  5,
  'session.get counts unread, undeleted notifications'
);

select extensions.is(
  public.api_v1_session_get('{"x":1}'::jsonb) ->> 'hint',
  'UNKNOWN_FIELD',
  'unknown body fields are rejected'
);

select extensions.is(
  public.api_v1_session_get('{"x":1}'::jsonb) ->> 'message',
  'Unknown field: x',
  'the unknown field is named'
);

select extensions.is(
  public.api_v1_me_get('[]'::jsonb) ->> 'hint',
  'INVALID_FIELD',
  'a non-object body is rejected'
);

select extensions.is(
  public.api_v1_me_get('[]'::jsonb) ->> 'code',
  'PT400',
  'a non-object body is a 400'
);

select pg_catalog.set_config('api_test.me', public.api_v1_me_get('{}'::jsonb)::text, true);

select extensions.is(
  current_setting('api_test.me')::jsonb -> 'profile' ->> 'user_id',
  '99800000-0000-4000-8000-000000000001',
  'me.get returns the caller profile'
);

select extensions.ok(
  not (current_setting('api_test.me')::jsonb -> 'profile' ? 'email'),
  'the developer API profile never contains the account email'
);

select extensions.is(
  current_setting('api_test.me')::jsonb -> 'profile' ->> 'username',
  'pgtap.alice.998',
  'me.get returns the username'
);

select extensions.is(
  current_setting('api_test.me')::jsonb -> 'profile' ->> 'display_name',
  'Api Alice pgtap',
  'me.get returns the display name'
);

select extensions.is(
  current_setting('api_test.me')::jsonb -> 'profile' ->> 'avatar_path',
  '99800000-0000-4000-8000-000000000001/avatar.png',
  'me.get returns the avatar path'
);

select extensions.is(
  (current_setting('api_test.me')::jsonb -> 'profile' ->> 'setup_complete')::boolean,
  true,
  'me.get reports a completed setup'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('api_test.me')::jsonb -> 'profile') as keys(key)
  ),
  array[
    'age',
    'avatar_path',
    'bio',
    'country_code',
    'created_at',
    'display_name',
    'setup_complete',
    'updated_at',
    'user_id',
    'username'
  ]::text[],
  'the profile projection has the documented keys'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99800000-0000-4000-8000-000000000002"}'::jsonb) -> 'profile' ->> 'user_id',
  '99800000-0000-4000-8000-000000000002',
  'profiles.get returns a friend'
);

select extensions.ok(
  not (public.api_v1_profiles_get('{"user_id":"99800000-0000-4000-8000-000000000002"}'::jsonb) -> 'profile' ? 'email'),
  'the developer API does not expose a friend email'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99800000-0000-4000-8000-000000000004"}'::jsonb) -> 'profile' ->> 'user_id',
  '99800000-0000-4000-8000-000000000004',
  'profiles.get returns a conversation co-member'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99800000-0000-4000-8000-000000000003"}'::jsonb) -> 'profile' ->> 'user_id',
  '99800000-0000-4000-8000-000000000003',
  'profiles.get returns someone with a pending friend request'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99800000-0000-4000-8000-000000000009"}'::jsonb) ->> 'code',
  'PT404',
  'profiles.get hides a stranger'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99800000-0000-4000-8000-000000000009"}'::jsonb) ->> 'hint',
  'PROFILE_NOT_FOUND',
  'a hidden profile is reported as PROFILE_NOT_FOUND'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99800000-0000-4000-8000-000000000005"}'::jsonb) ->> 'code',
  'PT404',
  'profiles.get hides a blocked co-member'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"99800000-0000-4000-8000-000000000001"}'::jsonb) -> 'profile' ->> 'user_id',
  '99800000-0000-4000-8000-000000000001',
  'profiles.get returns the caller'
);

select extensions.is(
  public.api_v1_profiles_get('{}'::jsonb) ->> 'hint',
  'MISSING_FIELD',
  'profiles.get requires user_id'
);

select extensions.is(
  public.api_v1_profiles_get('{"user_id":"nope"}'::jsonb) ->> 'hint',
  'INVALID_FIELD',
  'profiles.get rejects a malformed user_id'
);

select extensions.is(
  (
    select array_agg((item ->> 'user_id')::uuid order by ordinality)
    from jsonb_array_elements(
      public.api_v1_profiles_get_many(
        '{"user_ids":["99800000-0000-4000-8000-000000000009","99800000-0000-4000-8000-000000000003","99800000-0000-4000-8000-000000000004","99800000-0000-4000-8000-000000000002","99800000-0000-4000-8000-000000000005","99800000-0000-4000-8000-000000000001"]}'::jsonb
      ) -> 'items'
    ) with ordinality as page(item, ordinality)
  ),
  array[
    '99800000-0000-4000-8000-000000000003',
    '99800000-0000-4000-8000-000000000004',
    '99800000-0000-4000-8000-000000000002',
    '99800000-0000-4000-8000-000000000001'
  ]::uuid[],
  'profiles.get_many filters by relationship and keeps the input order'
);

select extensions.is(
  public.api_v1_profiles_get_many('{"user_ids":[]}'::jsonb) -> 'items',
  '[]'::jsonb,
  'profiles.get_many accepts an empty list'
);

select extensions.is(
  public.api_v1_profiles_get_many(
    jsonb_build_object('user_ids', (select jsonb_agg(gen_random_uuid()) from generate_series(1, 101)))
  ) ->> 'hint',
  'INVALID_FIELD',
  'profiles.get_many caps the list at 100'
);

select extensions.is(
  public.api_v1_profiles_get_many('{}'::jsonb) ->> 'hint',
  'MISSING_FIELD',
  'profiles.get_many requires user_ids'
);

select extensions.is(
  public.api_v1_profiles_get_many('{"user_ids":["99800000-0000-4000-8000-000000000002", 7]}'::jsonb) ->> 'hint',
  'INVALID_FIELD',
  'profiles.get_many rejects non-UUID entries'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.limited'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) -> 'profile' ->> 'user_id',
  '99800000-0000-4000-8000-000000000001',
  'a profile:read app can read the profile'
);

select extensions.is(
  public.api_v1_friends_list('{}'::jsonb) ->> 'code',
  'PT403',
  'a profile:read app cannot list friends'
);

select extensions.is(
  public.api_v1_friends_list('{}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'the missing scope is reported as SCOPE_REQUIRED'
);

select extensions.is(
  current_setting('response.status', true),
  '403',
  'the HTTP status is set to 403'
);

select extensions.is(
  public.api_v1_messages_list('{"conversation_id":"99800000-0000-4000-8000-000000000101"}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'a profile:read app cannot list messages'
);

reset role;

select extensions.ok(
  (
    select stat.requests = 4 and stat.denied = 3
    from private.api_usage as stat
    where stat.client_id = current_setting('api_test.limited')::uuid
      and stat.user_id = '99800000-0000-4000-8000-000000000001'
      and stat.day = (now() at time zone 'utc')::date
  ),
  'scope denials are metered and counted as denied'
);

set local role api_client;
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select pg_catalog.set_config('api_test.friends', public.api_v1_friends_list('{}'::jsonb)::text, true);

select extensions.is(
  jsonb_array_length(current_setting('api_test.friends')::jsonb -> 'items'),
  1,
  'friends.list returns the one friend'
);

select extensions.is(
  current_setting('api_test.friends')::jsonb -> 'items' -> 0 ->> 'user_id',
  '99800000-0000-4000-8000-000000000002',
  'friends.list returns the friend profile'
);

select extensions.is(
  (current_setting('api_test.friends')::jsonb -> 'items' -> 0 ->> 'friends_since')::timestamptz,
  now() - interval '1 day',
  'friends.list carries friends_since'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('api_test.friends')::jsonb -> 'items' -> 0) as keys(key)
  ),
  array[
    'age',
    'avatar_path',
    'bio',
    'country_code',
    'created_at',
    'display_name',
    'friends_since',
    'setup_complete',
    'updated_at',
    'user_id',
    'username'
  ]::text[],
  'friend items are the profile projection plus friends_since'
);

select pg_catalog.set_config('api_test.conversations', public.api_v1_conversations_list('{}'::jsonb)::text, true);

select extensions.is(
  jsonb_array_length(current_setting('api_test.conversations')::jsonb -> 'items'),
  2,
  'conversations.list returns the active memberships'
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(current_setting('api_test.conversations')::jsonb -> 'items') with ordinality as page(item, ordinality)
  ),
  array['99800000-0000-4000-8000-000000000101', '99800000-0000-4000-8000-000000000102']::uuid[],
  'conversations.list orders by updated_at descending'
);

select extensions.is(
  current_setting('api_test.conversations')::jsonb -> 'next_cursor',
  'null'::jsonb,
  'conversations.list has no next cursor when everything fits'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('api_test.conversations')::jsonb -> 'items' -> 0) as keys(key)
  ),
  array[
    'created_at',
    'id',
    'kind',
    'last_message',
    'last_read_at',
    'members',
    'title',
    'unread_count',
    'updated_at'
  ]::text[],
  'the conversation projection has the documented keys'
);

select extensions.is(
  (
    select array_agg((member ->> 'user_id')::uuid order by ordinality)
    from jsonb_array_elements(current_setting('api_test.conversations')::jsonb -> 'items' -> 0 -> 'members') with ordinality as page(member, ordinality)
  ),
  array['99800000-0000-4000-8000-000000000001', '99800000-0000-4000-8000-000000000002']::uuid[],
  'members list the active participants'
);

select extensions.is(
  current_setting('api_test.conversations')::jsonb -> 'items' -> 0 -> 'last_message' ->> 'id',
  '99800000-0000-4000-8000-000000000205',
  'last_message is the newest undeleted message'
);

select extensions.is(
  (current_setting('api_test.conversations')::jsonb -> 'items' -> 0 ->> 'unread_count')::integer,
  2,
  'unread_count ignores own and deleted messages'
);

select extensions.is(
  current_setting('api_test.conversations')::jsonb -> 'items' -> 0 -> 'last_read_at',
  'null'::jsonb,
  'last_read_at is null before reading'
);

select extensions.is(
  current_setting('api_test.conversations')::jsonb -> 'items' -> 0 ->> 'kind',
  'direct',
  'the direct conversation reports its kind'
);

select extensions.is(
  current_setting('api_test.conversations')::jsonb -> 'items' -> 1 ->> 'title',
  'Api Group pgtap',
  'the group conversation carries its title'
);

select pg_catalog.set_config('api_test.conv_page1', public.api_v1_conversations_list('{"limit":1}'::jsonb)::text, true);

select extensions.is(
  current_setting('api_test.conv_page1')::jsonb -> 'items' -> 0 ->> 'id',
  '99800000-0000-4000-8000-000000000101',
  'the first conversation page holds the newest conversation'
);

select extensions.ok(
  current_setting('api_test.conv_page1')::jsonb ->> 'next_cursor' is not null,
  'a full conversation page returns a cursor'
);

reset role;

select extensions.is(
  (
    select decoded.id
    from private.api_decode_cursor(current_setting('api_test.conv_page1')::jsonb ->> 'next_cursor') as decoded
  ),
  '99800000-0000-4000-8000-000000000101'::uuid,
  'the conversation cursor encodes the last id'
);

select extensions.is(
  (
    select decoded.ts
    from private.api_decode_cursor(current_setting('api_test.conv_page1')::jsonb ->> 'next_cursor') as decoded
  ),
  now() - interval '1 hour',
  'the conversation cursor encodes the last updated_at'
);

set local role api_client;

select pg_catalog.set_config(
  'api_test.conv_page2',
  public.api_v1_conversations_list(
    jsonb_build_object('limit', 1, 'cursor', current_setting('api_test.conv_page1')::jsonb ->> 'next_cursor')
  )::text,
  true
);

select extensions.is(
  current_setting('api_test.conv_page2')::jsonb -> 'items' -> 0 ->> 'id',
  '99800000-0000-4000-8000-000000000102',
  'the second conversation page continues after the cursor'
);

select extensions.is(
  current_setting('api_test.conv_page2')::jsonb -> 'next_cursor',
  'null'::jsonb,
  'the last conversation page has no cursor'
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(
      public.api_v1_conversations_list(
        jsonb_build_object('updated_after', to_json(now() - interval '90 minutes') #>> '{}')
      ) -> 'items'
    ) with ordinality as page(item, ordinality)
  ),
  array['99800000-0000-4000-8000-000000000101']::uuid[],
  'updated_after filters older conversations out'
);

select extensions.is(
  public.api_v1_conversations_list('{"cursor":"not a cursor"}'::jsonb) ->> 'hint',
  'INVALID_CURSOR',
  'a malformed conversation cursor is rejected'
);

select extensions.is(
  public.api_v1_conversations_list('{"limit":"x"}'::jsonb) ->> 'hint',
  'INVALID_FIELD',
  'a non-numeric limit is rejected'
);

select extensions.is(
  public.api_v1_conversations_list('{"limit":1.5}'::jsonb) ->> 'hint',
  'INVALID_FIELD',
  'a fractional limit is rejected'
);

select extensions.is(
  public.api_v1_conversations_list('{"updated_after":"last tuesday-ish"}'::jsonb) ->> 'hint',
  'INVALID_FIELD',
  'a malformed updated_after is rejected'
);

select extensions.is(
  public.api_v1_conversations_get('{"conversation_id":"99800000-0000-4000-8000-000000000101"}'::jsonb) -> 'conversation' ->> 'id',
  '99800000-0000-4000-8000-000000000101',
  'conversations.get returns a member conversation'
);

select extensions.is(
  public.api_v1_conversations_get('{"conversation_id":"99800000-0000-4000-8000-000000000103"}'::jsonb) ->> 'code',
  'PT404',
  'conversations.get hides a conversation the caller is not in'
);

select extensions.is(
  public.api_v1_conversations_get('{"conversation_id":"99800000-0000-4000-8000-000000000103"}'::jsonb) ->> 'hint',
  'CONVERSATION_NOT_FOUND',
  'a hidden conversation is reported as CONVERSATION_NOT_FOUND'
);

select extensions.is(
  public.api_v1_conversations_get('{}'::jsonb) ->> 'hint',
  'MISSING_FIELD',
  'conversations.get requires conversation_id'
);

select extensions.is(
  public.api_v1_conversations_open('{"user_id":"99800000-0000-4000-8000-000000000002","client_operation_id":"99800000-0000-4000-8000-000000000702"}'::jsonb) -> 'conversation' ->> 'id',
  '99800000-0000-4000-8000-000000000101',
  'conversations.open returns the existing direct conversation with a friend'
);

select extensions.is(
  public.api_v1_conversations_open('{"user_id":"99800000-0000-4000-8000-000000000002","client_operation_id":"99800000-0000-4000-8000-000000000702"}'::jsonb) -> 'conversation' ->> 'id',
  '99800000-0000-4000-8000-000000000101',
  'conversations.open replays the same operation id'
);

select extensions.is(
  public.api_v1_conversations_open('{"user_id":"99800000-0000-4000-8000-000000000003","client_operation_id":"99800000-0000-4000-8000-000000000703"}'::jsonb) ->> 'code',
  'PT403',
  'conversations.open refuses a non-friend'
);

select extensions.is(
  public.api_v1_conversations_open('{"user_id":"99800000-0000-4000-8000-000000000003","client_operation_id":"99800000-0000-4000-8000-000000000703"}'::jsonb) ->> 'hint',
  'NOT_FRIENDS',
  'the non-friend refusal is reported as NOT_FRIENDS'
);

select extensions.is(
  public.api_v1_conversations_open('{"user_id":"99800000-0000-4000-8000-000000000005","client_operation_id":"99800000-0000-4000-8000-000000000704"}'::jsonb) ->> 'hint',
  'BLOCKED',
  'conversations.open with a blocked user is reported as BLOCKED'
);

select extensions.is(
  public.api_v1_conversations_open('{"user_id":"99800000-0000-4000-8000-000000000001","client_operation_id":"99800000-0000-4000-8000-000000000705"}'::jsonb) ->> 'code',
  'PT400',
  'conversations.open with oneself is a 400'
);

select extensions.is(
  public.api_v1_conversations_open('{"user_id":"99800000-0000-4000-8000-000000000001","client_operation_id":"99800000-0000-4000-8000-000000000705"}'::jsonb) ->> 'message',
  'A different user id is required',
  'the delegated validation message is kept'
);

select extensions.is(
  public.api_v1_conversations_open('{"user_id":"99800000-0000-4000-8000-000000000998","client_operation_id":"99800000-0000-4000-8000-000000000706"}'::jsonb) ->> 'hint',
  'PROFILE_NOT_FOUND',
  'conversations.open with an unknown user is reported as PROFILE_NOT_FOUND'
);

select extensions.is(
  (public.api_v1_conversations_mark_read('{"conversation_id":"99800000-0000-4000-8000-000000000101"}'::jsonb) ->> 'last_read_at')::timestamptz,
  now(),
  'conversations.mark_read stamps now'
);

select extensions.is(
  (public.api_v1_conversations_get('{"conversation_id":"99800000-0000-4000-8000-000000000101"}'::jsonb) -> 'conversation' ->> 'unread_count')::integer,
  0,
  'reading clears the unread count'
);

select extensions.is(
  public.api_v1_conversations_mark_read('{"conversation_id":"99800000-0000-4000-8000-000000000103"}'::jsonb) ->> 'code',
  'PT403',
  'conversations.mark_read refuses a non-member'
);

select extensions.is(
  public.api_v1_conversations_mark_read('{"conversation_id":"99800000-0000-4000-8000-000000000103"}'::jsonb) ->> 'hint',
  'NOT_A_MEMBER',
  'the membership refusal is reported as NOT_A_MEMBER'
);

select pg_catalog.set_config(
  'api_test.messages',
  public.api_v1_messages_list('{"conversation_id":"99800000-0000-4000-8000-000000000101"}'::jsonb)::text,
  true
);

select extensions.is(
  jsonb_array_length(current_setting('api_test.messages')::jsonb -> 'items'),
  5,
  'messages.list returns every message including deleted ones'
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(current_setting('api_test.messages')::jsonb -> 'items') with ordinality as page(item, ordinality)
  ),
  array[
    '99800000-0000-4000-8000-000000000205',
    '99800000-0000-4000-8000-000000000204',
    '99800000-0000-4000-8000-000000000203',
    '99800000-0000-4000-8000-000000000202',
    '99800000-0000-4000-8000-000000000201'
  ]::uuid[],
  'messages.list is newest first'
);

select extensions.is(
  current_setting('api_test.messages')::jsonb -> 'next_cursor',
  'null'::jsonb,
  'messages.list has no cursor when everything fits'
);

select extensions.is(
  current_setting('api_test.messages')::jsonb -> 'items' -> 1 ->> 'reply_to_id',
  '99800000-0000-4000-8000-000000000202',
  'replies carry reply_to_id'
);

select extensions.ok(
  (current_setting('api_test.messages')::jsonb -> 'items' -> 4 ->> 'deleted_at') is not null
    and current_setting('api_test.messages')::jsonb -> 'items' -> 4 ->> 'body' = 'Message deleted',
  'deleted messages carry deleted_at and the placeholder body'
);

select extensions.ok(
  (current_setting('api_test.messages')::jsonb -> 'items' -> 2 ->> 'edited_at') is not null,
  'edited messages carry edited_at'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('api_test.messages')::jsonb -> 'items' -> 0) as keys(key)
  ),
  array[
    'attachment',
    'body',
    'conversation_id',
    'created_at',
    'deleted_at',
    'edited_at',
    'id',
    'reply_to_id',
    'sender_id'
  ]::text[],
  'the message projection has the documented keys'
);

select extensions.is(
  current_setting('api_test.messages')::jsonb -> 'items' -> 0 -> 'attachment',
  'null'::jsonb,
  'messages without media have a null attachment'
);

select extensions.is(
  current_setting('api_test.messages')::jsonb -> 'items' -> 3 -> 'attachment',
  jsonb_build_object(
    'path', '99800000-0000-4000-8000-000000000001/99800000-0000-4000-8000-000000000101/photo.png',
    'mime_type', 'image/png'
  ),
  'messages with media expose path and mime type'
);

select pg_catalog.set_config(
  'api_test.msg_page1',
  public.api_v1_messages_list('{"conversation_id":"99800000-0000-4000-8000-000000000101","limit":2}'::jsonb)::text,
  true
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(current_setting('api_test.msg_page1')::jsonb -> 'items') with ordinality as page(item, ordinality)
  ),
  array['99800000-0000-4000-8000-000000000205', '99800000-0000-4000-8000-000000000204']::uuid[],
  'the first message page holds the two newest messages'
);

select extensions.ok(
  current_setting('api_test.msg_page1')::jsonb ->> 'next_cursor' is not null,
  'a full message page returns a cursor'
);

select pg_catalog.set_config(
  'api_test.msg_page2',
  public.api_v1_messages_list(
    jsonb_build_object(
      'conversation_id', '99800000-0000-4000-8000-000000000101',
      'limit', 2,
      'cursor', current_setting('api_test.msg_page1')::jsonb ->> 'next_cursor'
    )
  )::text,
  true
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(current_setting('api_test.msg_page2')::jsonb -> 'items') with ordinality as page(item, ordinality)
  ),
  array['99800000-0000-4000-8000-000000000203', '99800000-0000-4000-8000-000000000202']::uuid[],
  'the second message page continues after the cursor'
);

select pg_catalog.set_config(
  'api_test.msg_page3',
  public.api_v1_messages_list(
    jsonb_build_object(
      'conversation_id', '99800000-0000-4000-8000-000000000101',
      'limit', 2,
      'cursor', current_setting('api_test.msg_page2')::jsonb ->> 'next_cursor'
    )
  )::text,
  true
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(current_setting('api_test.msg_page3')::jsonb -> 'items') with ordinality as page(item, ordinality)
  ),
  array['99800000-0000-4000-8000-000000000201']::uuid[],
  'the last message page holds the remainder'
);

select extensions.is(
  current_setting('api_test.msg_page3')::jsonb -> 'next_cursor',
  'null'::jsonb,
  'the last message page has no cursor'
);

select extensions.is(
  jsonb_array_length(public.api_v1_messages_list('{"conversation_id":"99800000-0000-4000-8000-000000000101","limit":0}'::jsonb) -> 'items'),
  1,
  'limit clamps up to one'
);

select extensions.is(
  jsonb_array_length(public.api_v1_messages_list('{"conversation_id":"99800000-0000-4000-8000-000000000101","limit":1000}'::jsonb) -> 'items'),
  5,
  'limit clamps down to one hundred'
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(
      public.api_v1_messages_list(
        jsonb_build_object(
          'conversation_id', '99800000-0000-4000-8000-000000000101',
          'changed_since', to_json(now() - interval '90 seconds') #>> '{}'
        )
      ) -> 'items'
    ) with ordinality as page(item, ordinality)
  ),
  array[
    '99800000-0000-4000-8000-000000000205',
    '99800000-0000-4000-8000-000000000203',
    '99800000-0000-4000-8000-000000000201'
  ]::uuid[],
  'changed_since returns created, edited and deleted messages'
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(
      public.api_v1_messages_list(
        jsonb_build_object(
          'conversation_id', '99800000-0000-4000-8000-000000000101',
          'changed_since', to_json(now() - interval '25 seconds') #>> '{}'
        )
      ) -> 'items'
    ) with ordinality as page(item, ordinality)
  ),
  array['99800000-0000-4000-8000-000000000201']::uuid[],
  'changed_since honours the deletion time'
);

select extensions.is(
  public.api_v1_messages_list('{"conversation_id":"99800000-0000-4000-8000-000000000103"}'::jsonb) ->> 'hint',
  'CONVERSATION_NOT_FOUND',
  'messages.list hides a conversation the caller is not in'
);

select extensions.is(
  public.api_v1_messages_list('{"conversation_id":"99800000-0000-4000-8000-000000000101","cursor":"nope"}'::jsonb) ->> 'hint',
  'INVALID_CURSOR',
  'a malformed message cursor is rejected'
);

select extensions.is(
  public.api_v1_messages_list('{"conversation_id":"99800000-0000-4000-8000-000000000101","bogus":1}'::jsonb) ->> 'hint',
  'UNKNOWN_FIELD',
  'messages.list rejects unknown fields'
);

select pg_catalog.set_config(
  'api_test.sent',
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"Hello from the app","client_operation_id":"99800000-0000-4000-8000-000000000701"}'::jsonb)::text,
  true
);

select extensions.is(
  current_setting('api_test.sent')::jsonb -> 'message' ->> 'id',
  current_setting('api_test.expected_message_id'),
  'messages.send derives the message id from the user and operation id'
);

select extensions.is(
  current_setting('api_test.sent')::jsonb -> 'message' ->> 'sender_id',
  '99800000-0000-4000-8000-000000000001',
  'messages.send sends as the caller'
);

select extensions.is(
  current_setting('api_test.sent')::jsonb -> 'message' ->> 'body',
  'Hello from the app',
  'messages.send stores the body'
);

select extensions.is(
  current_setting('api_test.sent')::jsonb -> 'message' ->> 'conversation_id',
  '99800000-0000-4000-8000-000000000101',
  'messages.send targets the conversation'
);

reset role;

select extensions.is(
  (
    select count(*)
    from public.messages as message
    where message.conversation_id = '99800000-0000-4000-8000-000000000101'
  ),
  6::bigint,
  'messages.send inserts one row'
);

set local role api_client;

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"Hello from the app","client_operation_id":"99800000-0000-4000-8000-000000000701"}'::jsonb) -> 'message' ->> 'id',
  current_setting('api_test.sent')::jsonb -> 'message' ->> 'id',
  'retrying with the same operation id returns the same message'
);

reset role;

select extensions.is(
  (
    select count(*)
    from public.messages as message
    where message.conversation_id = '99800000-0000-4000-8000-000000000101'
  ),
  6::bigint,
  'a retry does not insert a second row'
);

select extensions.is(
  (
    select conversation.updated_at
    from public.conversations as conversation
    where conversation.id = '99800000-0000-4000-8000-000000000101'
  ),
  now(),
  'sending bumps the conversation'
);

set local role api_client;

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"Different body","client_operation_id":"99800000-0000-4000-8000-000000000701"}'::jsonb) ->> 'code',
  'PT409',
  'reusing an operation id with a different body is a conflict'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"Different body","client_operation_id":"99800000-0000-4000-8000-000000000701"}'::jsonb) ->> 'hint',
  'DUPLICATE_OPERATION_ID',
  'the conflict is reported as DUPLICATE_OPERATION_ID'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"   ","client_operation_id":"99800000-0000-4000-8000-000000000707"}'::jsonb) ->> 'code',
  'PT400',
  'a blank body is a 400'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"   ","client_operation_id":"99800000-0000-4000-8000-000000000707"}'::jsonb) ->> 'hint',
  'BODY_LENGTH',
  'a blank body is reported as BODY_LENGTH'
);

select extensions.is(
  public.api_v1_messages_send(
    jsonb_build_object(
      'conversation_id', '99800000-0000-4000-8000-000000000101',
      'body', repeat('x', 4001),
      'client_operation_id', '99800000-0000-4000-8000-000000000708'
    )
  ) ->> 'hint',
  'BODY_LENGTH',
  'an oversized body is reported as BODY_LENGTH'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"No op id"}'::jsonb) ->> 'hint',
  'MISSING_FIELD',
  'messages.send requires client_operation_id'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","client_operation_id":"99800000-0000-4000-8000-000000000709"}'::jsonb) ->> 'hint',
  'MISSING_FIELD',
  'messages.send requires body'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"Reply","client_operation_id":"99800000-0000-4000-8000-000000000710","reply_to_id":"99800000-0000-4000-8000-000000000201"}'::jsonb) -> 'message' ->> 'reply_to_id',
  '99800000-0000-4000-8000-000000000201',
  'messages.send accepts a reply target in the conversation'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"Bad reply","client_operation_id":"99800000-0000-4000-8000-000000000711","reply_to_id":"99800000-0000-4000-8000-000000000206"}'::jsonb) ->> 'hint',
  'REPLY_TARGET',
  'a reply target outside the conversation is rejected'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"Bad reply","client_operation_id":"99800000-0000-4000-8000-000000000711","reply_to_id":"99800000-0000-4000-8000-000000000206"}'::jsonb) ->> 'message',
  'Reply target is not in this conversation',
  'the reply validation message is kept'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000103","body":"Intruder","client_operation_id":"99800000-0000-4000-8000-000000000712"}'::jsonb) ->> 'code',
  'PT403',
  'messages.send refuses a conversation the caller is not in'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000103","body":"Intruder","client_operation_id":"99800000-0000-4000-8000-000000000712"}'::jsonb) ->> 'hint',
  'NOT_A_MEMBER',
  'the membership refusal is reported as NOT_A_MEMBER'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000102","body":"Group hello","client_operation_id":"99800000-0000-4000-8000-000000000713"}'::jsonb) -> 'message' ->> 'body',
  'Group hello',
  'messages.send accepts a group that contains a blocked member'
);

select extensions.is(
  public.api_v1_conversations_get('{"conversation_id":"99800000-0000-4000-8000-000000000102"}'::jsonb) -> 'conversation' -> 'last_message' ->> 'body',
  'Group hello',
  'the group message becomes the conversation''s last message'
);

select extensions.is(
  public.api_v1_messages_send('{"conversation_id":"99800000-0000-4000-8000-000000000101","body":"x","client_operation_id":"99800000-0000-4000-8000-000000000714","metadata":{}}'::jsonb) ->> 'hint',
  'UNKNOWN_FIELD',
  'messages.send rejects unknown fields such as metadata'
);

select extensions.is(
  public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000202","body":"Two edited"}'::jsonb) -> 'message' ->> 'body',
  'Two edited',
  'messages.edit replaces the body of an own message'
);

select extensions.ok(
  (public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000202","body":"Two edited"}'::jsonb) -> 'message' ->> 'edited_at') is not null,
  'messages.edit stamps edited_at'
);

select extensions.is(
  public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000201","body":"Hijack"}'::jsonb) ->> 'code',
  'PT403',
  'messages.edit refuses another member message'
);

select extensions.is(
  public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000201","body":"Hijack"}'::jsonb) ->> 'hint',
  'NOT_SENDER',
  'the sender refusal is reported as NOT_SENDER'
);

select extensions.is(
  public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000206","body":"Hijack"}'::jsonb) ->> 'code',
  'PT404',
  'messages.edit hides a message outside the caller conversations'
);

select extensions.is(
  public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000206","body":"Hijack"}'::jsonb) ->> 'hint',
  'MESSAGE_NOT_FOUND',
  'a hidden message is reported as MESSAGE_NOT_FOUND'
);

select extensions.is(
  public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000997","body":"Ghost"}'::jsonb) ->> 'hint',
  'MESSAGE_NOT_FOUND',
  'messages.edit reports an unknown message as MESSAGE_NOT_FOUND'
);

select extensions.is(
  public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000202","body":""}'::jsonb) ->> 'hint',
  'BODY_LENGTH',
  'messages.edit rejects an empty body'
);

select pg_catalog.set_config(
  'api_test.deleted',
  public.api_v1_messages_delete('{"message_id":"99800000-0000-4000-8000-000000000204"}'::jsonb)::text,
  true
);

select extensions.ok(
  (current_setting('api_test.deleted')::jsonb -> 'message' ->> 'deleted_at') is not null,
  'messages.delete stamps deleted_at'
);

select extensions.is(
  current_setting('api_test.deleted')::jsonb -> 'message' ->> 'body',
  'Message deleted',
  'messages.delete scrubs the body'
);

select extensions.is(
  public.api_v1_messages_delete('{"message_id":"99800000-0000-4000-8000-000000000204"}'::jsonb) -> 'message' ->> 'deleted_at',
  current_setting('api_test.deleted')::jsonb -> 'message' ->> 'deleted_at',
  'messages.delete is idempotent'
);

select extensions.is(
  public.api_v1_messages_delete('{"message_id":"99800000-0000-4000-8000-000000000205"}'::jsonb) ->> 'hint',
  'NOT_SENDER',
  'messages.delete refuses another member message'
);

select extensions.is(
  public.api_v1_messages_delete('{"message_id":"99800000-0000-4000-8000-000000000206"}'::jsonb) ->> 'hint',
  'MESSAGE_NOT_FOUND',
  'messages.delete hides a message outside the caller conversations'
);

select extensions.is(
  public.api_v1_messages_delete('{}'::jsonb) ->> 'hint',
  'MISSING_FIELD',
  'messages.delete requires message_id'
);

select extensions.is(
  public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000204","body":"Too late"}'::jsonb) ->> 'code',
  'PT409',
  'editing a deleted message is a conflict'
);

select extensions.is(
  public.api_v1_messages_edit('{"message_id":"99800000-0000-4000-8000-000000000204","body":"Too late"}'::jsonb) ->> 'hint',
  'MESSAGE_DELETED',
  'the deleted conflict is reported as MESSAGE_DELETED'
);

select pg_catalog.set_config('api_test.notifications', public.api_v1_notifications_list('{}'::jsonb)::text, true);

select extensions.is(
  jsonb_array_length(current_setting('api_test.notifications')::jsonb -> 'items'),
  5,
  'notifications.list returns the undeleted notifications'
);

select extensions.is(
  (
    select array_agg(item ->> 'kind' order by ordinality)
    from jsonb_array_elements(current_setting('api_test.notifications')::jsonb -> 'items') with ordinality as page(item, ordinality)
  ),
  array['friend_request', 'message', 'system', 'system', 'system']::text[],
  'notifications.list orders by updated_at descending'
);

select extensions.is(
  current_setting('api_test.notifications')::jsonb -> 'next_cursor',
  'null'::jsonb,
  'notifications.list has no cursor when everything fits'
);

select extensions.is(
  current_setting('api_test.notifications')::jsonb -> 'items' -> 0 -> 'actor' ->> 'user_id',
  '99800000-0000-4000-8000-000000000003',
  'the friend request notification carries its actor'
);

select extensions.is(
  current_setting('api_test.notifications')::jsonb -> 'items' -> 0 -> 'actor' ->> 'display_name',
  'Api Carol pgtap',
  'the actor carries a display name'
);

select extensions.is(
  current_setting('api_test.notifications')::jsonb -> 'items' -> 0 ->> 'friend_request_status',
  'pending',
  'the friend request notification is pending'
);

select extensions.is(
  current_setting('api_test.notifications')::jsonb -> 'items' -> 1 ->> 'conversation_id',
  '99800000-0000-4000-8000-000000000101',
  'the message notification points at the conversation'
);

select extensions.is(
  (current_setting('api_test.notifications')::jsonb -> 'items' -> 1 ->> 'event_count')::integer,
  3,
  'the message notification groups the peer messages'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('api_test.notifications')::jsonb -> 'items' -> 0) as keys(key)
  ),
  array[
    'actor',
    'body',
    'conversation_id',
    'created_at',
    'event_count',
    'friend_request_id',
    'friend_request_status',
    'id',
    'kind',
    'read_at',
    'title',
    'updated_at'
  ]::text[],
  'the notification projection has the documented keys'
);

select pg_catalog.set_config('api_test.notif_page1', public.api_v1_notifications_list('{"limit":2}'::jsonb)::text, true);

select extensions.is(
  (
    select array_agg(item ->> 'kind' order by ordinality)
    from jsonb_array_elements(current_setting('api_test.notif_page1')::jsonb -> 'items') with ordinality as page(item, ordinality)
  ),
  array['friend_request', 'message']::text[],
  'the first notification page holds the newest two'
);

select extensions.ok(
  current_setting('api_test.notif_page1')::jsonb ->> 'next_cursor' is not null,
  'a full notification page returns a cursor'
);

select pg_catalog.set_config(
  'api_test.notif_page2',
  public.api_v1_notifications_list(
    jsonb_build_object('limit', 2, 'cursor', current_setting('api_test.notif_page1')::jsonb ->> 'next_cursor')
  )::text,
  true
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(current_setting('api_test.notif_page2')::jsonb -> 'items') with ordinality as page(item, ordinality)
  ),
  array['99800000-0000-4000-8000-000000000503', '99800000-0000-4000-8000-000000000502']::uuid[],
  'the second notification page continues after the cursor'
);

select pg_catalog.set_config(
  'api_test.notif_page3',
  public.api_v1_notifications_list(
    jsonb_build_object('limit', 2, 'cursor', current_setting('api_test.notif_page2')::jsonb ->> 'next_cursor')
  )::text,
  true
);

select extensions.is(
  (
    select array_agg((item ->> 'id')::uuid order by ordinality)
    from jsonb_array_elements(current_setting('api_test.notif_page3')::jsonb -> 'items') with ordinality as page(item, ordinality)
  ),
  array['99800000-0000-4000-8000-000000000501']::uuid[],
  'the last notification page holds the remainder'
);

select extensions.is(
  current_setting('api_test.notif_page3')::jsonb -> 'next_cursor',
  'null'::jsonb,
  'the last notification page has no cursor'
);

select extensions.is(
  public.api_v1_notifications_list('{"cursor":"x"}'::jsonb) ->> 'hint',
  'INVALID_CURSOR',
  'a malformed notification cursor is rejected'
);

select extensions.is(
  public.api_v1_notifications_list('{"limit":"a"}'::jsonb) ->> 'hint',
  'INVALID_FIELD',
  'a non-numeric notification limit is rejected'
);

select extensions.is(
  public.api_v1_notifications_mark_read('{"notification_id":"99800000-0000-4000-8000-000000000503"}'::jsonb),
  '{"ok":true}'::jsonb,
  'notifications.mark_read acknowledges'
);

reset role;

select extensions.ok(
  (
    select notification.read_at is not null
    from public.notifications as notification
    where notification.id = '99800000-0000-4000-8000-000000000503'
  ),
  'notifications.mark_read stamps read_at'
);

set local role api_client;

select extensions.is(
  (public.api_v1_session_get('{}'::jsonb) ->> 'unread_total')::integer,
  4,
  'reading a notification lowers unread_total'
);

select extensions.is(
  public.api_v1_notifications_mark_read('{"notification_id":"99800000-0000-4000-8000-000000000500"}'::jsonb) ->> 'code',
  'PT404',
  'a deleted notification cannot be marked read'
);

select extensions.is(
  public.api_v1_notifications_mark_read('{"notification_id":"99800000-0000-4000-8000-000000000500"}'::jsonb) ->> 'hint',
  'NOTIFICATION_NOT_FOUND',
  'the missing notification is reported as NOTIFICATION_NOT_FOUND'
);

select extensions.is(
  public.api_v1_notifications_mark_read('{"notification_id":"99800000-0000-4000-8000-000000000996"}'::jsonb) ->> 'hint',
  'NOTIFICATION_NOT_FOUND',
  'an unknown notification cannot be marked read'
);

select extensions.is(
  public.api_v1_notifications_mark_read(jsonb_build_object('notification_id', current_setting('api_test.bob_notification'))) ->> 'hint',
  'NOTIFICATION_NOT_FOUND',
  'another user notification cannot be marked read'
);

select extensions.is(
  public.api_v1_notifications_delete('{"notification_id":"99800000-0000-4000-8000-000000000502"}'::jsonb),
  '{"ok":true}'::jsonb,
  'notifications.delete acknowledges'
);

reset role;

select extensions.ok(
  (
    select notification.deleted_at is not null
    from public.notifications as notification
    where notification.id = '99800000-0000-4000-8000-000000000502'
  ),
  'notifications.delete soft-deletes'
);

set local role api_client;

select extensions.is(
  public.api_v1_notifications_delete('{"notification_id":"99800000-0000-4000-8000-000000000502"}'::jsonb),
  '{"ok":true}'::jsonb,
  'notifications.delete is idempotent'
);

select extensions.is(
  public.api_v1_notifications_delete(
    jsonb_build_object('notification_id', current_setting('api_test.notifications')::jsonb -> 'items' -> 0 ->> 'id')
  ) ->> 'code',
  'PT409',
  'a pending friend request notification cannot be deleted'
);

select extensions.is(
  public.api_v1_notifications_delete(
    jsonb_build_object('notification_id', current_setting('api_test.notifications')::jsonb -> 'items' -> 0 ->> 'id')
  ) ->> 'hint',
  'FRIEND_REQUEST_PENDING',
  'the pending request is reported as FRIEND_REQUEST_PENDING'
);

select extensions.is(
  public.api_v1_notifications_delete(jsonb_build_object('notification_id', current_setting('api_test.bob_notification'))) ->> 'hint',
  'NOTIFICATION_NOT_FOUND',
  'another user notification cannot be deleted'
);

select extensions.is(
  jsonb_array_length(public.api_v1_notifications_list('{}'::jsonb) -> 'items'),
  4,
  'a deleted notification leaves the list'
);

select extensions.is(
  (
    select count(*)
    from storage.objects as object
    where object.bucket_id = 'avatars'
      and object.name like '99800000-0000-4000-8000-%'
  ),
  2::bigint,
  'a profile:read app sees unblocked avatars'
);

select extensions.is(
  (
    select array_agg(object.name order by object.name collate "C")
    from storage.objects as object
    where object.bucket_id = 'avatars'
      and object.name like '99800000-0000-4000-8000-%'
  ),
  array[
    '99800000-0000-4000-8000-000000000001/avatar.png',
    '99800000-0000-4000-8000-000000000003/avatar.png'
  ]::text[],
  'the blocked user avatar stays hidden'
);

select extensions.is(
  (
    select count(*)
    from storage.objects as object
    where object.bucket_id = 'message-media'
      and object.name like '99800000-0000-4000-8000-%'
  ),
  1::bigint,
  'a messages:read app sees media of its conversations only'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.limited'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  (
    select count(*)
    from storage.objects as object
    where object.bucket_id = 'avatars'
      and object.name like '99800000-0000-4000-8000-%'
  ),
  2::bigint,
  'a profile:read only app still sees avatars'
);

select extensions.is(
  (
    select count(*)
    from storage.objects as object
    where object.bucket_id = 'message-media'
      and object.name like '99800000-0000-4000-8000-%'
  ),
  0::bigint,
  'an app without messages:read sees no media'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.rev'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  (
    select count(*)
    from storage.objects as object
    where object.bucket_id = 'avatars'
      and object.name like '99800000-0000-4000-8000-%'
  ),
  2::bigint,
  'the revocable app sees avatars while consented'
);

reset role;

select pg_catalog.set_config(
  'api_test.full_bucket',
  coalesce(
    (
      select bucket.requests
      from private.api_rate_buckets as bucket
      where bucket.client_id = current_setting('api_test.full')::uuid
        and bucket.user_id = '99800000-0000-4000-8000-000000000001'
        and bucket.bucket = date_trunc('minute', now())
    ),
    0
  )::text,
  true
);

select pg_catalog.set_config(
  'api_test.full_requests',
  coalesce(
    (
      select stat.requests
      from private.api_usage as stat
      where stat.client_id = current_setting('api_test.full')::uuid
        and stat.user_id = '99800000-0000-4000-8000-000000000001'
        and stat.day = (now() at time zone 'utc')::date
    ),
    0
  )::text,
  true
);

set local role api_client;
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_conversations_get('{"conversation_id":"99800000-0000-4000-8000-000000000995"}'::jsonb) ->> 'hint',
  'CONVERSATION_NOT_FOUND',
  'a bogus conversation id fails cleanly'
);

reset role;

select extensions.is(
  (
    select bucket.requests
    from private.api_rate_buckets as bucket
    where bucket.client_id = current_setting('api_test.full')::uuid
      and bucket.user_id = '99800000-0000-4000-8000-000000000001'
      and bucket.bucket = date_trunc('minute', now())
  ),
  current_setting('api_test.full_bucket')::integer + 1,
  'a failing call still counts against the minute bucket'
);

select extensions.is(
  (
    select stat.requests
    from private.api_usage as stat
    where stat.client_id = current_setting('api_test.full')::uuid
      and stat.user_id = '99800000-0000-4000-8000-000000000001'
      and stat.day = (now() at time zone 'utc')::date
  ),
  current_setting('api_test.full_requests')::bigint + 1,
  'a failing call still counts as a request'
);

select extensions.is(
  (
    select stat.denied
    from private.api_usage as stat
    where stat.client_id = current_setting('api_test.full')::uuid
      and stat.user_id = '99800000-0000-4000-8000-000000000001'
      and stat.day = (now() at time zone 'utc')::date
  ),
  0::bigint,
  'a failure inside the endpoint is not a guard denial'
);

insert into private.api_rate_buckets (client_id, user_id, bucket, requests)
values (
  current_setting('api_test.limited')::uuid,
  '99800000-0000-4000-8000-000000000001',
  date_trunc('minute', now()),
  120
)
on conflict (client_id, user_id, bucket) do update set requests = 120;

set local role api_client;
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.limited'),
    'aud', 'authenticated'
  )::text,
  true
);

select pg_catalog.set_config('api_test.limited_429', public.api_v1_me_get('{}'::jsonb)::text, true);

select extensions.is(
  current_setting('api_test.limited_429')::jsonb ->> 'code',
  'PT429',
  'the 121st call in a minute is rate limited'
);

select extensions.is(
  current_setting('api_test.limited_429')::jsonb ->> 'hint',
  'API_RATE_LIMITED',
  'the limit is reported as API_RATE_LIMITED'
);

select extensions.matches(
  current_setting('api_test.limited_429')::jsonb ->> 'message',
  '^Rate limit exceeded, retry after [0-9]+s$',
  'the limit message says when to retry'
);

select extensions.is(
  current_setting('response.status', true),
  '429',
  'the HTTP status is set to 429'
);

select extensions.ok(
  current_setting('response.headers', true) like '%Retry-After%',
  'a Retry-After header is set'
);

select extensions.ok(
  current_setting('response.headers', true) like '%RateLimit-Remaining%',
  'a RateLimit-Remaining header is set'
);

reset role;

select extensions.is(
  (
    select bucket.requests
    from private.api_rate_buckets as bucket
    where bucket.client_id = current_setting('api_test.limited')::uuid
      and bucket.user_id = '99800000-0000-4000-8000-000000000001'
      and bucket.bucket = date_trunc('minute', now())
  ),
  121,
  'the limited call is still counted'
);

select extensions.is(
  (
    select stat.denied
    from private.api_usage as stat
    where stat.client_id = current_setting('api_test.limited')::uuid
      and stat.user_id = '99800000-0000-4000-8000-000000000001'
      and stat.day = (now() at time zone 'utc')::date
  ),
  4::bigint,
  'the limited call is counted as denied'
);

update private.api_rate_buckets
set requests = 0
where client_id = current_setting('api_test.limited')::uuid
  and user_id = '99800000-0000-4000-8000-000000000001'
  and bucket = date_trunc('minute', now());

insert into private.api_rate_buckets_app (client_id, bucket, requests)
values (current_setting('api_test.limited')::uuid, date_trunc('minute', now()), 600)
on conflict (client_id, bucket, shard) do update set requests = 600;

set local role api_client;

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'hint',
  'API_RATE_LIMITED',
  'the per-app ceiling limits every user'
);

reset role;

update private.api_rate_buckets_app
set requests = 0
where client_id = current_setting('api_test.limited')::uuid
  and bucket = date_trunc('minute', now());

set local role api_client;

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) -> 'profile' ->> 'user_id',
  '99800000-0000-4000-8000-000000000001',
  'calls succeed again once the buckets clear'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.rev'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) -> 'profile' ->> 'user_id',
  '99800000-0000-4000-8000-000000000001',
  'the revocable app works while consented'
);

reset role;
set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99800000-0000-4000-8000-000000000001', true);

select extensions.is(
  (public.api_revoke_app(current_setting('api_test.rev')::uuid) ->> 'revoked')::boolean,
  true,
  'the user revokes the app from the connected apps page'
);

reset role;
set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.rev'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'code',
  'PT401',
  'a revoked app is refused'
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'hint',
  'CONSENT_REVOKED',
  'the revocation is reported as CONSENT_REVOKED'
);

select extensions.is(
  (
    select count(*)
    from storage.objects as object
    where object.bucket_id = 'avatars'
      and object.name like '99800000-0000-4000-8000-%'
  ),
  0::bigint,
  'a revoked app sees no avatars'
);

reset role;

select extensions.is(
  (
    select stat.denied
    from private.api_usage as stat
    where stat.client_id = current_setting('api_test.rev')::uuid
      and stat.user_id = '99800000-0000-4000-8000-000000000001'
      and stat.day = (now() at time zone 'utc')::date
  ),
  2::bigint,
  'revoked calls are counted as denied'
);

set local role api_client;
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.susp'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) -> 'profile' ->> 'user_id',
  '99800000-0000-4000-8000-000000000001',
  'the suspendable app works while active'
);

reset role;

update private.developer_apps
set status = 'suspended'
where client_id = current_setting('api_test.susp')::uuid;

set local role api_client;

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'code',
  'PT403',
  'a suspended app is refused'
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'hint',
  'APP_SUSPENDED',
  'the suspension is reported as APP_SUSPENDED'
);

reset role;

select extensions.is(
  (
    select bucket.requests
    from private.api_rate_buckets as bucket
    where bucket.client_id = current_setting('api_test.susp')::uuid
      and bucket.user_id = '99800000-0000-4000-8000-000000000001'
      and bucket.bucket = date_trunc('minute', now())
  ),
  3,
  'suspended app calls still count against the bucket'
);

select extensions.ok(
  (
    select stat.requests = 3 and stat.denied = 2
    from private.api_usage as stat
    where stat.client_id = current_setting('api_test.susp')::uuid
      and stat.user_id = '99800000-0000-4000-8000-000000000001'
      and stat.day = (now() at time zone 'utc')::date
  ),
  'suspended app calls are metered and denied'
);

set local role api_client;
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99800000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_session_revoke('{}'::jsonb),
  '{"revoked":true}'::jsonb,
  'session.revoke revokes the consent'
);

reset role;

select extensions.ok(
  (
    select consent.revoked_at is not null
    from auth.oauth_consents as consent
    where consent.user_id = '99800000-0000-4000-8000-000000000001'
      and consent.client_id = current_setting('api_test.full')::uuid
  ),
  'session.revoke stamps revoked_at'
);

select extensions.is(
  (
    select count(*)
    from auth.sessions as session
    where session.user_id = '99800000-0000-4000-8000-000000000001'
      and session.oauth_client_id = current_setting('api_test.full')::uuid
  ),
  0::bigint,
  'session.revoke deletes the client sessions'
);

select extensions.is(
  (
    select count(*)
    from auth.sessions as session
    where session.id = '99800000-0000-4000-8000-000000000601'
  ),
  1::bigint,
  'the first-party session survives session.revoke'
);

set local role api_client;

select extensions.is(
  public.api_v1_session_revoke('{}'::jsonb),
  '{"revoked":false}'::jsonb,
  'session.revoke is idempotent'
);

select extensions.is(
  public.api_v1_me_get('{}'::jsonb) ->> 'hint',
  'CONSENT_REVOKED',
  'other endpoints refuse the app after session.revoke'
);

select extensions.is(
  (
    select count(*)
    from storage.objects as object
    where object.bucket_id = 'avatars'
      and object.name like '99800000-0000-4000-8000-%'
  ),
  0::bigint,
  'no avatars are visible after session.revoke'
);

select extensions.is(
  (
    select count(*)
    from storage.objects as object
    where object.bucket_id = 'message-media'
      and object.name like '99800000-0000-4000-8000-%'
  ),
  0::bigint,
  'no media is visible after session.revoke'
);

reset role;

select extensions.is(
  (
    select stat.denied
    from private.api_usage as stat
    where stat.client_id = current_setting('api_test.full')::uuid
      and stat.user_id = '99800000-0000-4000-8000-000000000001'
      and stat.day = (now() at time zone 'utc')::date
  ),
  1::bigint,
  'only the post-revocation call counts as denied for the full app'
);

select * from extensions.finish();

rollback;
