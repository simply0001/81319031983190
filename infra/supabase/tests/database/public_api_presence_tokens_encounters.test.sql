begin;

set local search_path = public, extensions;

select extensions.plan(67);

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
    ('99650000-0000-4000-8000-000000000001'::uuid, 'pte-ann@pocketpass.test', 'Pte Ann pgtap'),
    ('99650000-0000-4000-8000-000000000002'::uuid, 'pte-ben@pocketpass.test', 'Pte Ben pgtap'),
    ('99650000-0000-4000-8000-000000000003'::uuid, 'pte-cal@pocketpass.test', 'Pte Cal pgtap'),
    ('99650000-0000-4000-8000-000000000004'::uuid, 'pte-dee@pocketpass.test', 'Pte Dee pgtap'),
    ('99650000-0000-4000-8000-000000000009'::uuid, 'pte-dev@pocketpass.test', 'Pte Dev pgtap')
) as seed(id, email, name);

update public.profiles
set username = names.username, bio = 'bio', age = 30, country_code = 'GB'
from (
  values
    ('99650000-0000-4000-8000-000000000001'::uuid, 'pgtap.ann.9965'),
    ('99650000-0000-4000-8000-000000000002'::uuid, 'pgtap.ben.9965'),
    ('99650000-0000-4000-8000-000000000003'::uuid, 'pgtap.cal.9965'),
    ('99650000-0000-4000-8000-000000000004'::uuid, 'pgtap.dee.9965'),
    ('99650000-0000-4000-8000-000000000009'::uuid, 'pgtap.dev.9965')
) as names(user_id, username)
where profiles.user_id = names.user_id;

insert into public.friendships (user_low, user_high, created_by, created_at)
values (
  '99650000-0000-4000-8000-000000000001',
  '99650000-0000-4000-8000-000000000002',
  '99650000-0000-4000-8000-000000000001',
  now() - interval '1 day'
);

insert into public.user_blocks (blocker_id, blocked_id)
values ('99650000-0000-4000-8000-000000000001', '99650000-0000-4000-8000-000000000004');

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
    '99650000-0000-4000-8000-000000000101',
    'direct',
    '99650000-0000-4000-8000-000000000001',
    null,
    '99650000-0000-4000-8000-000000000001',
    '99650000-0000-4000-8000-000000000002',
    now() - interval '2 hours',
    now() - interval '1 hour'
  ),
  (
    '99650000-0000-4000-8000-000000000102',
    'direct',
    '99650000-0000-4000-8000-000000000002',
    null,
    '99650000-0000-4000-8000-000000000002',
    '99650000-0000-4000-8000-000000000003',
    now() - interval '2 hours',
    now() - interval '1 hour'
  );

insert into public.conversation_members (conversation_id, user_id, role, joined_at)
values
  ('99650000-0000-4000-8000-000000000101', '99650000-0000-4000-8000-000000000001', 'owner', now() - interval '2 hours'),
  ('99650000-0000-4000-8000-000000000101', '99650000-0000-4000-8000-000000000002', 'member', now() - interval '2 hours'),
  ('99650000-0000-4000-8000-000000000102', '99650000-0000-4000-8000-000000000002', 'owner', now() - interval '2 hours'),
  ('99650000-0000-4000-8000-000000000102', '99650000-0000-4000-8000-000000000003', 'member', now() - interval '2 hours');

insert into public.nearby_encounters (
  id, user_low, user_high, reported_by, reporter_operation_id, occurred_at, created_at, confirmed_at
)
values
  (
    '99650000-0000-4000-8000-000000000201',
    '99650000-0000-4000-8000-000000000001',
    '99650000-0000-4000-8000-000000000002',
    '99650000-0000-4000-8000-000000000001',
    '99650000-0000-4000-8000-000000000301',
    now() - interval '2 days',
    now() - interval '2 days',
    now() - interval '2 days' + interval '1 minute'
  ),
  (
    '99650000-0000-4000-8000-000000000202',
    '99650000-0000-4000-8000-000000000001',
    '99650000-0000-4000-8000-000000000003',
    '99650000-0000-4000-8000-000000000001',
    '99650000-0000-4000-8000-000000000302',
    now() - interval '1 hour',
    now() - interval '1 hour',
    null
  ),
  (
    '99650000-0000-4000-8000-000000000203',
    '99650000-0000-4000-8000-000000000001',
    '99650000-0000-4000-8000-000000000004',
    '99650000-0000-4000-8000-000000000001',
    '99650000-0000-4000-8000-000000000303',
    now() - interval '3 days',
    now() - interval '3 days',
    now() - interval '3 days' + interval '1 minute'
  );

insert into public.token_balances (user_id, balance)
values ('99650000-0000-4000-8000-000000000001', 42)
on conflict (user_id) do update set balance = excluded.balance;

insert into public.supporter_status (user_id, active_until)
values ('99650000-0000-4000-8000-000000000001', now() + interval '10 days');

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99650000-0000-4000-8000-000000000009', true);

select pg_catalog.set_config(
  'pte_test.full',
  public.developer_create_app(
    'Pte Full pgtap',
    '',
    '',
    '',
    array['https://full.example/callback'],
    array['profile:read', 'friends:read', 'messages:read', 'presence:read', 'presence:write', 'tokens:read', 'encounters:read'],
    'confidential'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'pte_test.reader',
  public.developer_create_app(
    'Pte Reader pgtap',
    '',
    '',
    '',
    array['https://reader.example/callback'],
    array['profile:read', 'presence:read'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'pte_test.tracker',
  public.developer_create_app(
    'Pte Tracker pgtap',
    '',
    '',
    '',
    array['https://tracker.example/callback'],
    array['friends:read', 'presence:write'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'pte_test.bare',
  public.developer_create_app(
    'Pte Bare pgtap',
    '',
    '',
    '',
    array['https://bare.example/callback'],
    array['profile:read'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

reset role;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

update private.developer_apps
set rate_limit_per_second = 100000
where owner_user_id = '99650000-0000-4000-8000-000000000009';

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values
  (gen_random_uuid(), '99650000-0000-4000-8000-000000000001', current_setting('pte_test.full')::uuid, 'openid', now()),
  (gen_random_uuid(), '99650000-0000-4000-8000-000000000001', current_setting('pte_test.reader')::uuid, 'openid', now()),
  (gen_random_uuid(), '99650000-0000-4000-8000-000000000001', current_setting('pte_test.tracker')::uuid, 'openid', now()),
  (gen_random_uuid(), '99650000-0000-4000-8000-000000000001', current_setting('pte_test.bare')::uuid, 'openid', now()),
  (gen_random_uuid(), '99650000-0000-4000-8000-000000000002', current_setting('pte_test.full')::uuid, 'openid', now());

select extensions.is(
  private.api_scope_keys(),
  array[
    'profile:read', 'friends:read', 'friends:write', 'messages:read', 'messages:write',
    'groups:write', 'notifications:read', 'presence:read', 'presence:write', 'tokens:read', 'encounters:read',
    'puzzles:read'
  ]::text[],
  'the scope catalog appends the presence, tokens and encounters scopes'
);

select extensions.is(
  private.api_normalize_scopes(array['tokens:read', 'profile:read', 'presence:write']),
  array['profile:read', 'presence:write', 'tokens:read']::text[],
  'normalisation orders the new scopes after the existing ones'
);

select extensions.is(
  private.api_scope_descriptions() ->> 'presence:read',
  'See when your friends were last online, who is online now and who is active in your chats',
  'presence:read has its consent line'
);

select extensions.is(
  private.api_scope_descriptions() ->> 'presence:write',
  'Show you as online and typing to your friends',
  'presence:write has its consent line'
);

select extensions.is(
  private.api_scope_descriptions() ->> 'tokens:read',
  'See your token balance and supporter status',
  'tokens:read has its consent line'
);

select extensions.is(
  private.api_scope_descriptions() ->> 'encounters:read',
  'See the people you have met nearby',
  'encounters:read has its consent line'
);

select extensions.ok(
  pg_catalog.has_function_privilege('api_client', 'public.api_v1_tokens_get(jsonb)', 'execute'),
  'api_client can execute tokens.get'
);

select extensions.ok(
  pg_catalog.has_function_privilege('api_client', 'public.api_v1_encounters_list(jsonb)', 'execute'),
  'api_client can execute encounters.list'
);

select extensions.ok(
  pg_catalog.has_function_privilege('api_client', 'private.api_can_read_presence_topic(text)', 'execute')
    and pg_catalog.has_function_privilege('api_client', 'private.api_can_track_presence_topic(text)', 'execute'),
  'api_client can execute both presence predicates'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('authenticated', 'public.api_v1_tokens_get(jsonb)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'public.api_v1_tokens_get(jsonb)', 'execute')
    and not pg_catalog.has_function_privilege('authenticated', 'public.api_v1_encounters_list(jsonb)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'public.api_v1_encounters_list(jsonb)', 'execute'),
  'first-party roles cannot execute the new endpoints'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('authenticated', 'private.api_can_read_presence_topic(text)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'private.api_can_read_presence_topic(text)', 'execute')
    and not pg_catalog.has_function_privilege('authenticated', 'private.api_can_track_presence_topic(text)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'private.api_can_track_presence_topic(text)', 'execute'),
  'first-party roles do not execute the presence predicates'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('api_client', 'private.api_presence_topic_member(text)', 'execute'),
  'the presence membership helper is internal'
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
  'api_client has a presence-track and a read policy on realtime.messages'
);

select extensions.ok(
  (
    select qual like '%extension = ''broadcast''%' and qual like '%extension = ''presence''%'
    from pg_catalog.pg_policies
    where schemaname = 'realtime'
      and tablename = 'messages'
      and policyname = 'pocketpass_api_realtime_read'
  ),
  'the read policy covers broadcast and presence'
);

select extensions.ok(
  (
    select with_check like '%extension = ''presence''%' and with_check not like '%broadcast%'
    from pg_catalog.pg_policies
    where schemaname = 'realtime'
      and tablename = 'messages'
      and policyname = 'pocketpass_api_presence_track'
  ),
  'the track policy is presence only'
);

select extensions.has_trigger(
  'public',
  'nearby_encounters',
  'nearby_encounters_broadcast_change',
  'encounter changes still broadcast'
);

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99650000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('pte_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key)
    from jsonb_object_keys(public.api_v1_tokens_get('{}'::jsonb)) as keys(key)
  ),
  array['supporter', 'tokens']::text[],
  'tokens.get returns the tokens and supporter objects'
);

select extensions.is(
  public.api_v1_tokens_get('{}'::jsonb) -> 'tokens' ->> 'balance',
  '42',
  'tokens.get reports the balance'
);

select extensions.is(
  (public.api_v1_tokens_get('{}'::jsonb) -> 'supporter' ->> 'active')::boolean,
  true,
  'tokens.get reports an active supporter period'
);

select extensions.ok(
  (public.api_v1_tokens_get('{}'::jsonb) -> 'supporter' ->> 'active_until')::timestamptz > now(),
  'tokens.get reports when the supporter period ends'
);

select extensions.is(
  public.api_v1_tokens_get('{"x": 1}'::jsonb) ->> 'hint',
  'UNKNOWN_FIELD',
  'tokens.get rejects unknown fields'
);

select extensions.is(
  jsonb_array_length(public.api_v1_encounters_list('{}'::jsonb) -> 'items'),
  2,
  'encounters.list lists the encounters and leaves out the blocked person'
);

select extensions.is(
  public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 0 ->> 'id',
  '99650000-0000-4000-8000-000000000202',
  'the newest encounter comes first'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key)
    from jsonb_object_keys(public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 0) as keys(key)
  ),
  array['confirmed_at', 'created_at', 'id', 'occurred_at', 'peer', 'updated_at']::text[],
  'an encounter carries exactly the documented keys'
);

select extensions.is(
  public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 0 -> 'peer' ->> 'user_id',
  '99650000-0000-4000-8000-000000000003',
  'the peer is the other participant as a profile object'
);

select extensions.ok(
  jsonb_typeof(public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 0 -> 'confirmed_at') = 'null',
  'an unconfirmed encounter has a null confirmed_at'
);

select extensions.ok(
  public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 1 ->> 'id' = '99650000-0000-4000-8000-000000000201'
    and jsonb_typeof(public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 1 -> 'confirmed_at') = 'string',
  'the confirmed encounter follows with its confirmation time'
);

select extensions.ok(
  jsonb_typeof(public.api_v1_encounters_list('{}'::jsonb) -> 'next_cursor') = 'null',
  'a complete page has no next cursor'
);

select extensions.ok(
  jsonb_array_length(public.api_v1_encounters_list('{"limit": 1}'::jsonb) -> 'items') = 1
    and jsonb_typeof(public.api_v1_encounters_list('{"limit": 1}'::jsonb) -> 'next_cursor') = 'string',
  'limit pages the list and hands back a cursor'
);

select extensions.ok(
  (
    select
      page.result -> 'items' -> 0 ->> 'id' = '99650000-0000-4000-8000-000000000201'
      and jsonb_typeof(page.result -> 'next_cursor') = 'null'
    from (
      select public.api_v1_encounters_list(
        jsonb_build_object(
          'limit', 1,
          'cursor', public.api_v1_encounters_list('{"limit": 1}'::jsonb) ->> 'next_cursor'
        )
      ) as result
    ) as page
  ),
  'the cursor continues to the next encounter and then ends'
);

select extensions.is(
  public.api_v1_encounters_list('{"cursor": "nope"}'::jsonb) ->> 'hint',
  'INVALID_CURSOR',
  'a malformed cursor is rejected'
);

select extensions.is(
  public.api_v1_encounters_list('{"limit": "x"}'::jsonb) ->> 'hint',
  'INVALID_FIELD',
  'a non-integer limit is rejected'
);

select extensions.ok(
  (
    select
      jsonb_array_length(page.result -> 'items') = 1
      and page.result -> 'items' -> 0 ->> 'id' = '99650000-0000-4000-8000-000000000202'
    from (
      select public.api_v1_encounters_list(
        jsonb_build_object(
          'updated_after', public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 1 ->> 'updated_at'
        )
      ) as result
    ) as page
  ),
  'updated_after returns only encounters changed after the watermark'
);

select extensions.ok(
  private.api_can_access_realtime_topic('tokens:99650000-0000-4000-8000-000000000001'),
  'a tokens:read app may follow the user tokens topic'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('tokens:99650000-0000-4000-8000-000000000002'),
  'another user tokens topic stays closed'
);

select extensions.ok(
  private.api_can_access_realtime_topic('encounters:99650000-0000-4000-8000-000000000001'),
  'an encounters:read app may follow the user encounters topic'
);

select extensions.ok(
  not private.api_can_access_realtime_topic('encounters:99650000-0000-4000-8000-000000000002'),
  'another user encounters topic stays closed'
);

select extensions.ok(
  not private.api_can_access_realtime_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
  ),
  'friend presence is not a broadcast topic'
);

select extensions.ok(
  private.api_can_read_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
  ),
  'a presence:read app may see presence on its friend pair'
);

select extensions.ok(
  not private.api_can_read_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000002:99650000-0000-4000-8000-000000000001'
  ),
  'a reversed pair topic stays closed'
);

select extensions.ok(
  not private.api_can_read_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000003'
  ),
  'a pair with a stranger stays closed'
);

select extensions.ok(
  private.api_can_read_presence_topic('conversation:99650000-0000-4000-8000-000000000101'),
  'a presence:read app may see presence in its conversation'
);

select extensions.ok(
  not private.api_can_read_presence_topic('conversation:99650000-0000-4000-8000-000000000102'),
  'presence in a conversation the user is not in stays closed'
);

select extensions.ok(
  not private.api_can_read_presence_topic('tokens:99650000-0000-4000-8000-000000000001'),
  'broadcast-only topics carry no presence'
);

select extensions.ok(
  private.api_can_track_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
  ),
  'a presence:write app may track presence on its friend pair'
);

select extensions.ok(
  private.api_can_track_presence_topic('conversation:99650000-0000-4000-8000-000000000101'),
  'a presence:write app may track presence in its conversation'
);

select extensions.ok(
  not private.api_can_track_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000003'
  ),
  'tracking on a pair with a stranger stays closed'
);

reset role;
select pg_catalog.set_config('request.jwt.claims', '', true);

update public.nearby_encounters
set confirmed_at = now()
where id = '99650000-0000-4000-8000-000000000202';

select extensions.ok(
  exists (
    select 1
    from realtime.messages as message
    where message.topic = 'encounters:99650000-0000-4000-8000-000000000001'
      and message.event = 'UPDATE'
      and message.payload ->> 'encounter_id' = '99650000-0000-4000-8000-000000000202'
      and message.payload ? 'occurred_at'
      and message.payload ? 'confirmed_at'
      and not message.payload ? 'record'
      and not message.payload ? 'user_low'
      and not message.payload ? 'reported_by'
      and not message.payload ? 'reporter_operation_id'
  ),
  'the encounters topic carries a curated payload without the raw row'
);

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99650000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('pte_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.ok(
  public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 0 ->> 'id' = '99650000-0000-4000-8000-000000000202'
    and jsonb_typeof(public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 0 -> 'confirmed_at') = 'string'
    and (public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 0 ->> 'updated_at')::timestamptz
      = (public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 0 ->> 'confirmed_at')::timestamptz,
  'a confirmation moves the encounter to the top with a matching updated_at'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99650000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('pte_test.reader'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.ok(
  not private.api_can_read_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
  ),
  'presence on a friend pair needs friends:read as well'
);

select extensions.ok(
  not private.api_can_read_presence_topic('conversation:99650000-0000-4000-8000-000000000101'),
  'presence in a conversation needs messages:read as well'
);

select extensions.is(
  public.api_v1_tokens_get('{}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'tokens.get needs tokens:read'
);

select extensions.is(
  public.api_v1_encounters_list('{}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'encounters.list needs encounters:read'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99650000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('pte_test.tracker'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.ok(
  private.api_can_read_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
  ),
  'presence:write lets the app see presence on its friend pair'
);

select extensions.ok(
  private.api_can_track_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
  ),
  'presence:write lets the app track presence on its friend pair'
);

select extensions.ok(
  not private.api_can_track_presence_topic('conversation:99650000-0000-4000-8000-000000000101'),
  'tracking in a conversation still needs messages:read'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99650000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('pte_test.bare'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.ok(
  not private.api_can_access_realtime_topic('tokens:99650000-0000-4000-8000-000000000001')
    and not private.api_can_access_realtime_topic('encounters:99650000-0000-4000-8000-000000000001')
    and not private.api_can_read_presence_topic(
      'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
    )
    and not private.api_can_track_presence_topic(
      'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
    ),
  'an app without the scopes gets none of the new topics'
);

select extensions.is(
  public.api_v1_tokens_get('{}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'an app without tokens:read cannot read the balance'
);

reset role;
select pg_catalog.set_config('request.jwt.claims', '', true);

update auth.oauth_consents
set revoked_at = now()
where user_id = '99650000-0000-4000-8000-000000000001'
  and client_id = current_setting('pte_test.full')::uuid;

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99650000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('pte_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.ok(
  not private.api_can_access_realtime_topic('tokens:99650000-0000-4000-8000-000000000001'),
  'a revoked consent closes the tokens topic'
);

select extensions.ok(
  not private.api_can_read_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
  ),
  'a revoked consent closes presence reads'
);

select extensions.ok(
  not private.api_can_track_presence_topic(
    'friend-presence:99650000-0000-4000-8000-000000000001:99650000-0000-4000-8000-000000000002'
  ),
  'a revoked consent closes presence tracking'
);

select extensions.is(
  public.api_v1_tokens_get('{}'::jsonb) ->> 'hint',
  'CONSENT_REVOKED',
  'a revoked consent closes tokens.get'
);

reset role;
select pg_catalog.set_config('request.jwt.claims', '', true);

delete from public.token_balances
where user_id = '99650000-0000-4000-8000-000000000002';

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99650000-0000-4000-8000-000000000002',
    'role', 'api_client',
    'client_id', current_setting('pte_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_tokens_get('{}'::jsonb) -> 'tokens' ->> 'balance',
  '0',
  'a missing balance row reads as zero'
);

select extensions.ok(
  jsonb_typeof(public.api_v1_tokens_get('{}'::jsonb) -> 'tokens' -> 'updated_at') = 'null'
    and (public.api_v1_tokens_get('{}'::jsonb) -> 'supporter' ->> 'active')::boolean = false
    and jsonb_typeof(public.api_v1_tokens_get('{}'::jsonb) -> 'supporter' -> 'active_until') = 'null',
  'a user who never was a supporter reads as inactive with no end date'
);

select extensions.ok(
  jsonb_array_length(public.api_v1_encounters_list('{}'::jsonb) -> 'items') = 1
    and public.api_v1_encounters_list('{}'::jsonb) -> 'items' -> 0 -> 'peer' ->> 'user_id'
      = '99650000-0000-4000-8000-000000000001',
  'the other participant sees the same encounter with the peer swapped'
);

reset role;

select extensions.ok(
  not exists (
    select 1
    from public.token_balances
    where user_id = '99650000-0000-4000-8000-000000000002'
  ),
  'tokens.get does not create a balance row'
);

reset role;

select * from extensions.finish();

rollback;
