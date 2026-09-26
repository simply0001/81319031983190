begin;

set local search_path = public, extensions;

select extensions.plan(29);

select extensions.ok(
  'groups:write' = any (private.api_scope_keys()),
  'the groups:write scope is registered'
);
select extensions.is(
  private.api_scope_descriptions() ->> 'groups:write',
  'Create group chats and manage their members as you',
  'the groups:write scope has a consent description'
);
select extensions.ok(
  pg_catalog.has_function_privilege('api_client', 'public.api_v1_conversations_create(jsonb)', 'execute')
    and pg_catalog.has_function_privilege('api_client', 'public.api_v1_conversations_add_members(jsonb)', 'execute')
    and pg_catalog.has_function_privilege('api_client', 'public.api_v1_conversations_remove_member(jsonb)', 'execute')
    and pg_catalog.has_function_privilege('api_client', 'public.api_v1_conversations_leave(jsonb)', 'execute')
    and pg_catalog.has_function_privilege('api_client', 'public.api_v1_conversations_rename(jsonb)', 'execute'),
  'api_client can execute the group endpoints'
);
select extensions.ok(
  not pg_catalog.has_function_privilege('authenticated', 'public.api_v1_conversations_create(jsonb)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'public.api_v1_conversations_create(jsonb)', 'execute'),
  'app users cannot call the group endpoints directly'
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
    ('98950000-0000-4000-8000-000000000001'::uuid, 'api-group-alice@pocketpass.test', 'Api Group Alice'),
    ('98950000-0000-4000-8000-000000000002'::uuid, 'api-group-bob@pocketpass.test', 'Api Group Bob'),
    ('98950000-0000-4000-8000-000000000003'::uuid, 'api-group-carol@pocketpass.test', 'Api Group Carol'),
    ('98950000-0000-4000-8000-000000000004'::uuid, 'api-group-dave@pocketpass.test', 'Api Group Dave'),
    ('98950000-0000-4000-8000-000000000009'::uuid, 'api-group-dev@pocketpass.test', 'Api Group Dev')
) as seed(id, email, name);

insert into public.friendships (user_low, user_high, created_by)
values
  ('98950000-0000-4000-8000-000000000001', '98950000-0000-4000-8000-000000000002', '98950000-0000-4000-8000-000000000001'),
  ('98950000-0000-4000-8000-000000000001', '98950000-0000-4000-8000-000000000003', '98950000-0000-4000-8000-000000000001');

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '98950000-0000-4000-8000-000000000009', true);

select pg_catalog.set_config(
  'api_groups.full',
  public.developer_create_app(
    'Api Groups Full pgtap',
    '',
    '',
    '',
    array['https://groups-full.example/callback'],
    array['groups:write', 'messages:read', 'messages:write'],
    'confidential'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'api_groups.messages',
  public.developer_create_app(
    'Api Groups Messages pgtap',
    '',
    '',
    '',
    array['https://groups-messages.example/callback'],
    array['messages:write'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

reset role;

update private.developer_apps
set rate_limit_per_second = 100000
where owner_user_id = '98950000-0000-4000-8000-000000000009';

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values
  (gen_random_uuid(), '98950000-0000-4000-8000-000000000001', current_setting('api_groups.full')::uuid, 'openid', now()),
  (gen_random_uuid(), '98950000-0000-4000-8000-000000000001', current_setting('api_groups.messages')::uuid, 'openid', now()),
  (gen_random_uuid(), '98950000-0000-4000-8000-000000000002', current_setting('api_groups.full')::uuid, 'openid', now());

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '98950000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_groups.messages'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_conversations_create('{"title":"Api Trip","member_ids":["98950000-0000-4000-8000-000000000002"],"client_operation_id":"98960000-0000-4000-8000-000000000001"}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'conversations.create needs the groups:write scope'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '98950000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_groups.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select pg_catalog.set_config(
  'api_groups.created',
  public.api_v1_conversations_create('{"title":" Api Trip ","member_ids":["98950000-0000-4000-8000-000000000002","98950000-0000-4000-8000-000000000003"],"client_operation_id":"98960000-0000-4000-8000-000000000002"}'::jsonb)::text,
  true
);
select pg_catalog.set_config(
  'api_groups.trip',
  current_setting('api_groups.created')::jsonb -> 'conversation' ->> 'id',
  true
);

select extensions.is(
  current_setting('api_groups.created')::jsonb -> 'conversation' ->> 'kind',
  'group',
  'conversations.create returns a group conversation'
);
select extensions.is(
  current_setting('api_groups.created')::jsonb -> 'conversation' ->> 'title',
  'Api Trip',
  'conversations.create trims the title'
);
select extensions.is(
  jsonb_array_length(current_setting('api_groups.created')::jsonb -> 'conversation' -> 'members'),
  3,
  'the creator and both members are listed'
);
select extensions.is(
  public.api_v1_conversations_create('{"title":"Api Trip","member_ids":["98950000-0000-4000-8000-000000000004"],"client_operation_id":"98960000-0000-4000-8000-000000000003"}'::jsonb) ->> 'hint',
  'NOT_FRIENDS',
  'strangers cannot be added through the API'
);
select extensions.is(
  public.api_v1_conversations_create('{"title":"Api Trip","member_ids":[],"client_operation_id":"98960000-0000-4000-8000-000000000004"}'::jsonb) ->> 'hint',
  'MEMBERS_REQUIRED',
  'an empty member list is reported as MEMBERS_REQUIRED'
);
select extensions.is(
  public.api_v1_conversations_create('{"title":"   ","member_ids":["98950000-0000-4000-8000-000000000002"],"client_operation_id":"98960000-0000-4000-8000-000000000005"}'::jsonb) ->> 'hint',
  'TITLE_LENGTH',
  'a blank title is reported as TITLE_LENGTH'
);
select extensions.is(
  public.api_v1_conversations_create(
    jsonb_build_object(
      'title', 'Api Trip',
      'member_ids', (select jsonb_agg(gen_random_uuid()) from generate_series(1, 20)),
      'client_operation_id', '98960000-0000-4000-8000-000000000006'
    )
  ) ->> 'hint',
  'INVALID_FIELD',
  'more than nineteen member ids are rejected'
);
select extensions.is(
  public.api_v1_conversations_add_members(jsonb_build_object(
    'conversation_id', current_setting('api_groups.trip'),
    'member_ids', jsonb_build_array('98950000-0000-4000-8000-000000000003'),
    'client_operation_id', '98960000-0000-4000-8000-000000000007'
  )) ->> 'added',
  '0',
  'adding an existing member adds nobody'
);

select pg_catalog.set_config(
  'api_groups.removed',
  public.api_v1_conversations_remove_member(jsonb_build_object(
    'conversation_id', current_setting('api_groups.trip'),
    'user_id', '98950000-0000-4000-8000-000000000002',
    'client_operation_id', '98960000-0000-4000-8000-000000000008'
  ))::text,
  true
);

select extensions.is(
  current_setting('api_groups.removed')::jsonb ->> 'removed',
  'true',
  'the owner removes a member'
);
select extensions.is(
  jsonb_array_length(current_setting('api_groups.removed')::jsonb -> 'conversation' -> 'members'),
  2,
  'the returned roster no longer lists the removed member'
);
select extensions.is(
  public.api_v1_conversations_remove_member(jsonb_build_object(
    'conversation_id', current_setting('api_groups.trip'),
    'user_id', '98950000-0000-4000-8000-000000000001',
    'client_operation_id', '98960000-0000-4000-8000-000000000009'
  )) ->> 'hint',
  'SELF_TARGET',
  'removing yourself is reported as SELF_TARGET'
);
select extensions.is(
  public.api_v1_conversations_rename(jsonb_build_object(
    'conversation_id', current_setting('api_groups.trip'),
    'title', 'Api Trip 2',
    'client_operation_id', '98960000-0000-4000-8000-000000000010'
  )) -> 'conversation' ->> 'title',
  'Api Trip 2',
  'the owner renames the group'
);
select extensions.is(
  public.api_v1_conversations_add_members(jsonb_build_object(
    'conversation_id', current_setting('api_groups.trip'),
    'member_ids', jsonb_build_array('98950000-0000-4000-8000-000000000002'),
    'client_operation_id', '98960000-0000-4000-8000-000000000011'
  )) ->> 'added',
  '1',
  'a removed member can be added back'
);

select pg_catalog.set_config(
  'api_groups.direct',
  public.api_v1_conversations_open('{"user_id":"98950000-0000-4000-8000-000000000002","client_operation_id":"98960000-0000-4000-8000-000000000012"}'::jsonb) -> 'conversation' ->> 'id',
  true
);

select extensions.is(
  public.api_v1_conversations_add_members(jsonb_build_object(
    'conversation_id', current_setting('api_groups.direct'),
    'member_ids', jsonb_build_array('98950000-0000-4000-8000-000000000003'),
    'client_operation_id', '98960000-0000-4000-8000-000000000013'
  )) ->> 'hint',
  'NOT_A_GROUP',
  'direct conversations cannot gain members'
);
select extensions.is(
  public.api_v1_conversations_add_members('{"conversation_id":"98950000-0000-4000-8000-0000000000ff","member_ids":["98950000-0000-4000-8000-000000000003"],"client_operation_id":"98960000-0000-4000-8000-000000000014"}'::jsonb) ->> 'hint',
  'NOT_A_MEMBER',
  'unknown conversations are reported as NOT_A_MEMBER'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '98950000-0000-4000-8000-000000000002',
    'role', 'api_client',
    'client_id', current_setting('api_groups.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_conversations_rename(jsonb_build_object(
    'conversation_id', current_setting('api_groups.trip'),
    'title', 'Nope',
    'client_operation_id', '98960000-0000-4000-8000-000000000020'
  )) ->> 'hint',
  'NOT_OWNER',
  'members cannot rename the group'
);
select extensions.is(
  public.api_v1_conversations_remove_member(jsonb_build_object(
    'conversation_id', current_setting('api_groups.trip'),
    'user_id', '98950000-0000-4000-8000-000000000003',
    'client_operation_id', '98960000-0000-4000-8000-000000000021'
  )) ->> 'hint',
  'NOT_OWNER',
  'members cannot remove each other'
);
select extensions.is(
  public.api_v1_conversations_leave(jsonb_build_object(
    'conversation_id', current_setting('api_groups.trip'),
    'client_operation_id', '98960000-0000-4000-8000-000000000022'
  )) ->> 'left',
  'true',
  'a member leaves the group'
);
select extensions.is(
  public.api_v1_conversations_get(jsonb_build_object('conversation_id', current_setting('api_groups.trip'))) ->> 'hint',
  'CONVERSATION_NOT_FOUND',
  'a member who left can no longer read the group'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '98950000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('api_groups.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  jsonb_array_length(
    public.api_v1_conversations_get(jsonb_build_object('conversation_id', current_setting('api_groups.trip'))) -> 'conversation' -> 'members'
  ),
  2,
  'the roster shrinks after a member leaves'
);
select extensions.is(
  public.api_v1_conversations_leave(jsonb_build_object(
    'conversation_id', current_setting('api_groups.trip'),
    'client_operation_id', '98960000-0000-4000-8000-000000000023'
  )) ->> 'left',
  'true',
  'the owner leaves the group'
);

reset role;

select extensions.is(
  (
    select member.user_id::text
    from public.conversation_members as member
    where member.conversation_id = current_setting('api_groups.trip')::uuid
      and member.role = 'owner'
      and member.left_at is null
  ),
  '98950000-0000-4000-8000-000000000003',
  'ownership passes to the remaining member'
);
select extensions.is(
  private.api_translate('P0001', 'Group conversations are limited to 20 members') ->> 'hint',
  'GROUP_FULL',
  'the member cap is reported as GROUP_FULL'
);
select extensions.is(
  private.api_translate('P0002', 'Stored conversation result is missing') ->> 'hint',
  'CONVERSATION_NOT_FOUND',
  'a missing replay target is reported as CONVERSATION_NOT_FOUND'
);

select * from extensions.finish();

rollback;
