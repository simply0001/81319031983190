begin;

set local search_path = public, extensions;

select extensions.plan(202);

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
    ('99700000-0000-4000-8000-000000000001'::uuid, 'dev-one@pocketpass.test', 'Dev One pgtap'),
    ('99700000-0000-4000-8000-000000000002'::uuid, 'dev-two@pocketpass.test', 'Dev Two pgtap'),
    ('99700000-0000-4000-8000-000000000003'::uuid, 'dev-consenter@pocketpass.test', 'Consenter pgtap'),
    ('99700000-0000-4000-8000-000000000004'::uuid, 'dev-second@pocketpass.test', 'Second Consenter pgtap'),
    ('99700000-0000-4000-8000-000000000005'::uuid, 'dev-apps-admin@pocketpass.test', 'Apps Admin pgtap'),
    ('99700000-0000-4000-8000-000000000006'::uuid, 'dev-plain-admin@pocketpass.test', 'Plain Admin pgtap')
) as seed(id, email, name);

insert into private.admin_users (user_id, note, permissions)
values
  ('99700000-0000-4000-8000-000000000005', 'pgtap apps admin', array['apps']),
  ('99700000-0000-4000-8000-000000000006', 'pgtap plain admin', '{}');

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_proc as proc
    where proc.pronamespace = 'public'::regnamespace
      and proc.proname in (
        'developer_whoami',
        'developer_list_apps',
        'developer_create_app',
        'developer_update_app',
        'developer_rotate_secret',
        'developer_delete_app',
        'developer_app_usage',
        'api_app_info',
        'api_connected_apps',
        'api_revoke_app',
        'admin_list_developer_apps',
        'admin_set_developer_app_status'
      )
      and pg_catalog.has_function_privilege('authenticated', proc.oid, 'execute')
  ),
  12::bigint,
  'authenticated can execute the twelve portal, consent and admin RPCs'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_proc as proc
    where proc.pronamespace = 'public'::regnamespace
      and (proc.proname like 'developer\_%' or proc.proname in ('api_app_info', 'api_connected_apps', 'api_revoke_app', 'admin_list_developer_apps', 'admin_set_developer_app_status'))
      and pg_catalog.has_function_privilege('anon', proc.oid, 'execute')
  ),
  0::bigint,
  'anon cannot execute any of them'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_proc as proc
    where proc.pronamespace = 'public'::regnamespace
      and (proc.proname like 'developer\_%' or proc.proname in ('api_app_info', 'api_connected_apps', 'api_revoke_app', 'admin_list_developer_apps', 'admin_set_developer_app_status'))
      and pg_catalog.has_function_privilege('api_client', proc.oid, 'execute')
  ),
  0::bigint,
  'api_client cannot execute any of them'
);

select extensions.throws_ok(
  $$select public.developer_whoami()$$,
  '42501',
  'Authentication required',
  'developer_whoami rejects an unauthenticated caller'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https://cocoon.example/callback'], array['profile:read'], 'public')$$,
  '42501',
  'Authentication required',
  'developer_create_app rejects an unauthenticated caller'
);

select extensions.throws_ok(
  $$select public.api_connected_apps()$$,
  '42501',
  'Authentication required',
  'api_connected_apps rejects an unauthenticated caller'
);

select extensions.throws_ok(
  $$select public.api_revoke_app('99700000-0000-4000-8000-000000000999')$$,
  '42501',
  'Authentication required',
  'api_revoke_app rejects an unauthenticated caller'
);

select extensions.throws_ok(
  $$select public.admin_list_developer_apps()$$,
  '42501',
  'Authentication required',
  'admin_list_developer_apps rejects an unauthenticated caller'
);

reset role;
set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select extensions.is(
  public.developer_whoami() ->> 'user_id',
  '99700000-0000-4000-8000-000000000001',
  'whoami reports the caller'
);

select extensions.is(
  public.developer_whoami() ->> 'display_name',
  'Dev One pgtap',
  'whoami reports the display name'
);

select extensions.is(
  (public.developer_whoami() ->> 'app_count')::integer,
  0,
  'a new developer has no apps'
);

select extensions.is(
  (public.developer_whoami() ->> 'max_apps')::integer,
  5,
  'the app limit is five'
);

select extensions.is(
  public.developer_whoami() -> 'scopes',
  '[
    {"key":"profile:read","description":"See your profile (name, bio, avatar, age, country)"},
    {"key":"friends:read","description":"See your friends list and friend requests"},
    {"key":"friends:write","description":"Add and remove friends and answer friend requests as you"},
    {"key":"messages:read","description":"Read your conversations and messages"},
    {"key":"messages:write","description":"Send, edit and delete messages as you"},
    {"key":"groups:write","description":"Create group chats and manage their members as you"},
    {"key":"notifications:read","description":"See and clear your notifications"},
    {"key":"presence:read","description":"See which of your friends are online and who is active in your chats"},
    {"key":"presence:write","description":"Show you as online and typing to your friends"},
    {"key":"tokens:read","description":"See your token balance and supporter status"},
    {"key":"encounters:read","description":"See the people you have met nearby"},
    {"key":"puzzles:read","description":"See your Puzzle Swap progress"}
  ]'::jsonb,
  'whoami lists every scope with its description in canonical order'
);

select extensions.throws_ok(
  $$select public.developer_create_app('', '', '', '', array['https://cocoon.example/callback'], array['profile:read'], 'public')$$,
  '22023',
  'Name must be 1 to 64 characters',
  'an empty name is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app(repeat('x', 65), '', '', '', array['https://cocoon.example/callback'], array['profile:read'], 'public')$$,
  '22023',
  'Name must be 1 to 64 characters',
  'an overlong name is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app(E'Bad\tName', '', '', '', array['https://cocoon.example/callback'], array['profile:read'], 'public')$$,
  '22023',
  'Name must not contain control characters',
  'a name with control characters is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('My PocketPass Bot', '', '', '', array['https://cocoon.example/callback'], array['profile:read'], 'public')$$,
  'PT403',
  'App names containing PocketPass are reserved',
  'names containing PocketPass are reserved for admins'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', repeat('d', 281), '', '', array['https://cocoon.example/callback'], array['profile:read'], 'public')$$,
  '22023',
  'Description must be at most 280 characters',
  'an overlong description is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', 'http://cocoon.example', '', array['https://cocoon.example/callback'], array['profile:read'], 'public')$$,
  '22023',
  'Website must be an https URL',
  'a non-https website is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', 'ftp://cocoon.example/logo.png', array['https://cocoon.example/callback'], array['profile:read'], 'public')$$,
  '22023',
  'Logo URL must be an https URL',
  'a non-https logo URL is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', '{}'::text[], array['profile:read'], 'public')$$,
  '22023',
  'At least one redirect URI is required',
  'an empty redirect URI list is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['', '   '], array['profile:read'], 'public')$$,
  '22023',
  'At least one redirect URI is required',
  'a redirect URI list of blanks is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['javascript://cocoon.example/cb'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI scheme is not allowed: javascript://cocoon.example/cb',
  'a javascript redirect URI is rejected even with a host'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['data:text/html;base64;x'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI scheme is not allowed: data:text/html;base64;x',
  'a data redirect URI is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['FILE:///etc/passwd'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI scheme is not allowed: FILE:///etc/passwd',
  'a file redirect URI is rejected regardless of case'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['vbscript:msgbox'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI scheme is not allowed: vbscript:msgbox',
  'a vbscript redirect URI is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https://cocoon.example/cb,https://other.example/cb'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI must not contain whitespace, control characters, # or ,: https://cocoon.example/cb,https://other.example/cb',
  'a redirect URI containing a comma is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https://cocoon.example/cb#fragment'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI must not contain whitespace, control characters, # or ,: https://cocoon.example/cb#fragment',
  'a redirect URI with a fragment is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https://cocoon example/cb'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI must not contain whitespace, control characters, # or ,: https://cocoon example/cb',
  'a redirect URI with whitespace is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array[E'https://cocoon.example/c\tb'], array['profile:read'], 'public')$$,
  '22023',
  E'Redirect URI must not contain whitespace, control characters, # or ,: https://cocoon.example/c\tb',
  'a redirect URI with a control character is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['cocoon.example/cb'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI must be absolute with a scheme: cocoon.example/cb',
  'a relative redirect URI is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https:///cb'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI must include a host: https:///cb',
  'an https redirect URI without a host is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https://user@cocoon.example/cb'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI must include a host: https://user@cocoon.example/cb',
  'an https redirect URI with userinfo is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['http://cocoon.example/cb'], array['profile:read'], 'public')$$,
  '22023',
  'http redirect URIs are allowed only for localhost, 127.0.0.1 and [::1]: http://cocoon.example/cb',
  'a plain http redirect URI is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['http://localhost.evil.example/cb'], array['profile:read'], 'public')$$,
  '22023',
  'http redirect URIs are allowed only for localhost, 127.0.0.1 and [::1]: http://localhost.evil.example/cb',
  'a lookalike localhost host does not unlock http'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['myapp:'], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI must include a path or host: myapp:',
  'a bare custom scheme is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https://cocoon.example/' || repeat('x', 2040)], array['profile:read'], 'public')$$,
  '22023',
  'Redirect URI is too long: ' || left('https://cocoon.example/' || repeat('x', 2040), 80),
  'an overlong redirect URI is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', (select array_agg('https://cocoon.example/cb/' || n::text) from generate_series(1, 11) as n), array['profile:read'], 'public')$$,
  '22023',
  'At most 10 redirect URIs are allowed',
  'more than ten redirect URIs are rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https://cocoon.example/callback'], '{}'::text[], 'public')$$,
  '22023',
  'At least one scope is required',
  'an empty scope list is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https://cocoon.example/callback'], array['profile:read', 'admin:all'], 'public')$$,
  '22023',
  'Unknown scope',
  'an unknown scope is rejected'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Cocoon pgtap', '', '', '', array['https://cocoon.example/callback'], array['profile:read'], 'hybrid')$$,
  '22023',
  'Client type must be public or confidential',
  'an unknown client type is rejected'
);

select pg_catalog.set_config(
  'dev_test.created',
  public.developer_create_app(
    'Cocoon pgtap',
    '  Cocoon front-end  ',
    'https://cocoon.example',
    'https://cocoon.example/logo.png',
    array[
      'https://cocoon.example/callback',
      ' https://cocoon.example/callback ',
      '',
      'http://localhost:3000/callback',
      'http://127.0.0.1:3000/callback',
      'http://[::1]:8080/callback',
      'com.example.cocoon:/callback'
    ],
    array['messages:write', 'profile:read', 'profile:read', 'messages:read'],
    'Confidential'
  )::text,
  true
);

select pg_catalog.set_config(
  'dev_test.cocoon',
  current_setting('dev_test.created')::jsonb -> 'app' ->> 'client_id',
  true
);

select extensions.matches(
  current_setting('dev_test.created')::jsonb ->> 'client_secret',
  '^pp_secret_[0-9a-f]{64}$',
  'a confidential app receives a pp_secret_ secret once'
);

select extensions.is(
  current_setting('dev_test.created')::jsonb -> 'app' ->> 'name',
  'Cocoon pgtap',
  'the app name is stored'
);

select extensions.is(
  current_setting('dev_test.created')::jsonb -> 'app' ->> 'description',
  'Cocoon front-end',
  'the description is trimmed'
);

select extensions.is(
  current_setting('dev_test.created')::jsonb -> 'app' ->> 'website',
  'https://cocoon.example',
  'the website is stored'
);

select extensions.is(
  current_setting('dev_test.created')::jsonb -> 'app' ->> 'logo_url',
  'https://cocoon.example/logo.png',
  'the logo URL is stored'
);

select extensions.is(
  current_setting('dev_test.created')::jsonb -> 'app' ->> 'client_type',
  'confidential',
  'the client type is normalised to lower case'
);

select extensions.is(
  current_setting('dev_test.created')::jsonb -> 'app' ->> 'status',
  'active',
  'a new app is active'
);

select extensions.is(
  current_setting('dev_test.created')::jsonb -> 'app' -> 'scopes',
  '["profile:read","messages:read","messages:write"]'::jsonb,
  'scopes are deduped and stored in canonical order'
);

select extensions.is(
  current_setting('dev_test.created')::jsonb -> 'app' -> 'redirect_uris',
  '["https://cocoon.example/callback","http://localhost:3000/callback","http://127.0.0.1:3000/callback","http://[::1]:8080/callback","com.example.cocoon:/callback"]'::jsonb,
  'redirect URIs are trimmed, deduped and keep loopback and custom schemes'
);

select extensions.is(
  (current_setting('dev_test.created')::jsonb -> 'app' ->> 'connected_users')::integer,
  0,
  'a new app has no connected users'
);

select extensions.is(
  (current_setting('dev_test.created')::jsonb -> 'app' ->> 'requests_30d')::integer,
  0,
  'a new app has no requests'
);

select extensions.is(
  (current_setting('dev_test.created')::jsonb -> 'app' ->> 'denied_30d')::integer,
  0,
  'a new app has no denials'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('dev_test.created')::jsonb -> 'app') as keys(key)
  ),
  array[
    'client_id',
    'client_type',
    'connected_users',
    'created_at',
    'denied_30d',
    'description',
    'limits',
    'logo_url',
    'name',
    'pending_limit_request',
    'redirect_uris',
    'requests_30d',
    'scopes',
    'status',
    'updated_at',
    'website'
  ]::text[],
  'the app projection has the documented keys'
);

reset role;

select extensions.is(
  (
    select client.client_name
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'Cocoon pgtap',
  'the GoTrue client carries the app name'
);

select extensions.is(
  (
    select client.client_uri
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'https://cocoon.example',
  'the GoTrue client carries the website'
);

select extensions.is(
  (
    select client.logo_uri
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'https://cocoon.example/logo.png',
  'the GoTrue client carries the logo'
);

select extensions.is(
  (
    select client.registration_type::text
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'manual',
  'the GoTrue client is registered manually'
);

select extensions.is(
  (
    select client.client_type::text
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'confidential',
  'the GoTrue client type matches'
);

select extensions.is(
  (
    select client.token_endpoint_auth_method
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'client_secret_basic',
  'a confidential client authenticates with client_secret_basic'
);

select extensions.is(
  (
    select client.grant_types
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'authorization_code,refresh_token',
  'the GoTrue client allows the code and refresh grants'
);

select extensions.is(
  (
    select client.redirect_uris
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'https://cocoon.example/callback,http://localhost:3000/callback,http://127.0.0.1:3000/callback,http://[::1]:8080/callback,com.example.cocoon:/callback',
  'the GoTrue client stores the redirect URIs comma-joined'
);

select extensions.ok(
  (
    select client.deleted_at is null
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'a new GoTrue client is live'
);

select extensions.is(
  (
    select client.client_secret_hash
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  translate(
    rtrim(
      encode(
        extensions.digest(current_setting('dev_test.created')::jsonb ->> 'client_secret', 'sha256'),
        'base64'
      ),
      '='
    ),
    '+/',
    '-_'
  ),
  'the secret hash is base64url(sha256(secret)) without padding'
);

select extensions.matches(
  (
    select client.client_secret_hash
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  '^[A-Za-z0-9_-]{43}$',
  'the secret hash is 43 base64url characters'
);

select extensions.is(
  (
    select app.owner_user_id
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  '99700000-0000-4000-8000-000000000001'::uuid,
  'the registry row belongs to the caller'
);

select extensions.is(
  (
    select app.rate_limit_per_minute
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  600,
  'a new app gets the default per-app ceiling'
);

select extensions.is(
  (
    select count(*)
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_create'
      and audit.actor_id = '99700000-0000-4000-8000-000000000001'
  ),
  1::bigint,
  'creation is audited once'
);

select extensions.ok(
  (
    select audit.payload ->> 'name' = 'Cocoon pgtap' and not (audit.payload ? 'client_secret')
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_create'
  ),
  'the creation audit row names the app and carries no secret'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config(
  'dev_test.native_created',
  public.developer_create_app(
    'Cocoon Native pgtap',
    '',
    '',
    '',
    array['com.example.cocoon:/callback', 'http://127.0.0.1/callback'],
    array['profile:read'],
    'public'
  )::text,
  true
);

select pg_catalog.set_config(
  'dev_test.native',
  current_setting('dev_test.native_created')::jsonb -> 'app' ->> 'client_id',
  true
);

select extensions.is(
  current_setting('dev_test.native_created')::jsonb -> 'client_secret',
  'null'::jsonb,
  'a public client receives no secret'
);

reset role;

select extensions.ok(
  (
    select client.client_secret_hash = ''
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.native')::uuid
  ),
  'a public GoTrue client has no secret hash'
);

select extensions.is(
  (
    select client.token_endpoint_auth_method
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.native')::uuid
  ),
  'none',
  'a public client uses token_endpoint_auth_method none'
);

select extensions.is(
  (
    select client.client_type::text
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.native')::uuid
  ),
  'public',
  'a public client is typed public in GoTrue'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select extensions.is(
  (public.developer_whoami() ->> 'app_count')::integer,
  2,
  'whoami counts both apps'
);

select extensions.is(
  jsonb_array_length(public.developer_list_apps() -> 'items'),
  2,
  'developer_list_apps returns both apps'
);

select extensions.is(
  (
    select array_agg(item ->> 'name' order by (item ->> 'name') collate "C")
    from jsonb_array_elements(public.developer_list_apps() -> 'items') as item
  ),
  array['Cocoon Native pgtap', 'Cocoon pgtap']::text[],
  'developer_list_apps lists the owner apps only'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000004', true);

select extensions.is(
  public.developer_list_apps() -> 'items',
  '[]'::jsonb,
  'a user without apps gets an empty list'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000005', true);

select extensions.lives_ok(
  $$select public.developer_create_app('PocketPass Companion pgtap', '', '', '', array['https://companion.example/callback'], array['profile:read'], 'public')$$,
  'admins may use the reserved name'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000002', true);

select public.developer_create_app(
  'Limit ' || n::text || ' pgtap',
  '',
  '',
  '',
  array['https://limit.example/callback'],
  array['profile:read'],
  'public'
)
from generate_series(1, 5) as n;

select extensions.is(
  (public.developer_whoami() ->> 'app_count')::integer,
  5,
  'the second developer reaches five apps'
);

select extensions.throws_ok(
  $$select public.developer_create_app('Limit 6 pgtap', '', '', '', array['https://limit.example/callback'], array['profile:read'], 'public')$$,
  'PT403',
  'You can register at most 5 apps',
  'a sixth app is refused'
);

select extensions.is(
  (
    public.developer_delete_app(
      (
        select (item ->> 'client_id')::uuid
        from jsonb_array_elements(public.developer_list_apps() -> 'items') as item
        where item ->> 'name' = 'Limit 1 pgtap'
      )
    ) ->> 'deleted'
  )::boolean,
  true,
  'deleting an app reports deleted'
);

select extensions.lives_ok(
  $$select public.developer_create_app('Limit 6 pgtap', '', '', '', array['https://limit.example/callback'], array['profile:read'], 'public')$$,
  'deleting an app frees a slot'
);

select pg_catalog.set_config(
  'dev_test.limit2',
  (
    select item ->> 'client_id'
    from jsonb_array_elements(public.developer_list_apps() -> 'items') as item
    where item ->> 'name' = 'Limit 2 pgtap'
  ),
  true
);

select extensions.throws_ok(
  format(
    $$select public.developer_update_app(%L, 'Hijack', '', '', '', array['https://hijack.example/cb'], array['profile:read'])$$,
    current_setting('dev_test.cocoon')
  ),
  'PT404',
  'App not found',
  'a non-owner cannot update the app'
);

select extensions.throws_ok(
  format($$select public.developer_rotate_secret(%L)$$, current_setting('dev_test.cocoon')),
  'PT404',
  'App not found',
  'a non-owner cannot rotate the secret'
);

select extensions.throws_ok(
  format($$select public.developer_delete_app(%L)$$, current_setting('dev_test.cocoon')),
  'PT404',
  'App not found',
  'a non-owner cannot delete the app'
);

select extensions.throws_ok(
  format($$select public.developer_app_usage(%L, 7)$$, current_setting('dev_test.cocoon')),
  'PT404',
  'App not found',
  'a non-owner cannot read usage'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  $$select public.developer_update_app('99700000-0000-4000-8000-000000000999', 'Ghost', '', '', '', array['https://ghost.example/cb'], array['profile:read'])$$,
  'PT404',
  'App not found',
  'an unknown app cannot be updated'
);

select extensions.throws_ok(
  format(
    $$select public.developer_update_app(%L, 'Cocoon pgtap', '', '', '', array['http://cocoon.example/cb'], array['profile:read'])$$,
    current_setting('dev_test.cocoon')
  ),
  '22023',
  'http redirect URIs are allowed only for localhost, 127.0.0.1 and [::1]: http://cocoon.example/cb',
  'updates run the same redirect URI validation'
);

reset role;

update private.developer_apps
set scopes_changed_at = now() - interval '2 hours'
where client_id = current_setting('dev_test.cocoon')::uuid;

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values
  (
    gen_random_uuid(),
    '99700000-0000-4000-8000-000000000003',
    current_setting('dev_test.cocoon')::uuid,
    'openid',
    now() - interval '1 hour'
  ),
  (
    gen_random_uuid(),
    '99700000-0000-4000-8000-000000000004',
    current_setting('dev_test.cocoon')::uuid,
    'openid email',
    now() - interval '1 hour'
  );

insert into auth.sessions (id, user_id, created_at, updated_at, aal, oauth_client_id, scopes)
values
  (
    '99700000-0000-4000-8000-000000000301',
    '99700000-0000-4000-8000-000000000003',
    now(),
    now(),
    'aal1'::auth.aal_level,
    null,
    null
  ),
  (
    '99700000-0000-4000-8000-000000000302',
    '99700000-0000-4000-8000-000000000003',
    now(),
    now(),
    'aal1'::auth.aal_level,
    current_setting('dev_test.cocoon')::uuid,
    'openid'
  ),
  (
    '99700000-0000-4000-8000-000000000303',
    '99700000-0000-4000-8000-000000000004',
    now(),
    now(),
    'aal1'::auth.aal_level,
    current_setting('dev_test.cocoon')::uuid,
    'openid email'
  );

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select extensions.is(
  (
    select (item ->> 'connected_users')::integer
    from jsonb_array_elements(public.developer_list_apps() -> 'items') as item
    where item ->> 'client_id' = current_setting('dev_test.cocoon')
  ),
  2,
  'live consents count as connected users'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000003', true);

select extensions.is(
  public.api_app_info(current_setting('dev_test.cocoon')::uuid) ->> 'name',
  'Cocoon pgtap',
  'api_app_info names the app'
);

select extensions.is(
  public.api_app_info(current_setting('dev_test.cocoon')::uuid) ->> 'owner_display_name',
  'Dev One pgtap',
  'api_app_info names the owner'
);

select extensions.is(
  public.api_app_info(current_setting('dev_test.cocoon')::uuid) -> 'scopes',
  '[
    {"key":"profile:read","description":"See your profile (name, bio, avatar, age, country)"},
    {"key":"messages:read","description":"Read your conversations and messages"},
    {"key":"messages:write","description":"Send, edit and delete messages as you"}
  ]'::jsonb,
  'api_app_info describes the declared scopes in canonical order'
);

select extensions.is(
  public.api_app_info(current_setting('dev_test.cocoon')::uuid) -> 'oidc_scope_descriptions',
  jsonb_build_object(
    'email', 'your email address',
    'phone', 'your phone number',
    'profile', 'your PocketPass username'
  ),
  'api_app_info carries the OIDC scope descriptions'
);

select extensions.is(
  public.api_app_info(current_setting('dev_test.cocoon')::uuid) ->> 'status',
  'active',
  'api_app_info reports an active app'
);

select extensions.throws_ok(
  $$select public.api_app_info('99700000-0000-4000-8000-000000000999')$$,
  'PT404',
  'App not found',
  'api_app_info rejects an unknown app'
);

select extensions.is(
  jsonb_array_length(public.api_connected_apps() -> 'items'),
  1,
  'the consenting user sees one connected app'
);

select extensions.is(
  public.api_connected_apps() -> 'items' -> 0 ->> 'client_id',
  current_setting('dev_test.cocoon'),
  'the connected app is the consented client'
);

select extensions.is(
  public.api_connected_apps() -> 'items' -> 0 ->> 'name',
  'Cocoon pgtap',
  'the connected app carries the registry name'
);

select extensions.is(
  public.api_connected_apps() -> 'items' -> 0 -> 'scopes',
  '["profile:read","messages:read","messages:write"]'::jsonb,
  'the connected app carries the app scopes'
);

select extensions.is(
  (public.api_connected_apps() -> 'items' -> 0 ->> 'granted_at')::timestamptz,
  now() - interval '1 hour',
  'the connected app carries the grant time'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config(
  'dev_test.rotated',
  public.developer_rotate_secret(current_setting('dev_test.cocoon')::uuid) ->> 'client_secret',
  true
);

select extensions.matches(
  current_setting('dev_test.rotated'),
  '^pp_secret_[0-9a-f]{64}$',
  'rotation returns a fresh pp_secret_ secret'
);

select extensions.isnt(
  current_setting('dev_test.rotated'),
  current_setting('dev_test.created')::jsonb ->> 'client_secret',
  'the rotated secret differs from the original'
);

select extensions.throws_ok(
  format($$select public.developer_rotate_secret(%L)$$, current_setting('dev_test.native')),
  '22023',
  'Public clients have no secret',
  'public clients cannot rotate a secret'
);

reset role;

select extensions.is(
  (
    select client.client_secret_hash
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  private.developer_secret_hash(current_setting('dev_test.rotated')),
  'rotation stores the hash of the new secret'
);

select extensions.is(
  (
    select count(*)
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_rotate_secret'
      and audit.payload = '{}'::jsonb
  ),
  1::bigint,
  'rotation is audited without payload'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config(
  'dev_test.shrunk',
  public.developer_update_app(
    current_setting('dev_test.cocoon')::uuid,
    'Cocoon 2 pgtap',
    'Updated description',
    'https://cocoon2.example',
    '',
    array['https://cocoon2.example/callback'],
    array['messages:read', 'profile:read']
  )::text,
  true
);

select extensions.is(
  (current_setting('dev_test.shrunk')::jsonb ->> 'consents_revoked')::integer,
  0,
  'shrinking and reordering scopes revokes nothing'
);

select extensions.is(
  current_setting('dev_test.shrunk')::jsonb -> 'app' -> 'scopes',
  '["profile:read","messages:read"]'::jsonb,
  'the shrunk scopes are stored in canonical order'
);

select extensions.is(
  current_setting('dev_test.shrunk')::jsonb -> 'app' ->> 'name',
  'Cocoon 2 pgtap',
  'the name is updated'
);

select extensions.is(
  current_setting('dev_test.shrunk')::jsonb -> 'app' -> 'redirect_uris',
  '["https://cocoon2.example/callback"]'::jsonb,
  'the redirect URIs are replaced'
);

reset role;

select extensions.is(
  (
    select count(*)
    from auth.oauth_consents as consent
    where consent.client_id = current_setting('dev_test.cocoon')::uuid
      and consent.revoked_at is null
  ),
  2::bigint,
  'consents survive a scope shrink'
);

select extensions.is(
  (
    select count(*)
    from auth.sessions as session
    where session.oauth_client_id = current_setting('dev_test.cocoon')::uuid
  ),
  2::bigint,
  'sessions survive a scope shrink'
);

select extensions.is(
  (
    select app.scopes_changed_at
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  now() - interval '2 hours',
  'a scope shrink does not move scopes_changed_at'
);

select extensions.is(
  (
    select client.client_name
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'Cocoon 2 pgtap',
  'the GoTrue client name follows the update'
);

select extensions.is(
  (
    select client.redirect_uris
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'https://cocoon2.example/callback',
  'the GoTrue redirect URIs follow the update'
);

select extensions.is(
  (
    select audit.payload -> 'after' ->> 'name'
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_update'
  ),
  'Cocoon 2 pgtap',
  'the update is audited with the new values'
);

select extensions.is(
  (
    select count(*)
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_scopes_expand'
  ),
  0::bigint,
  'a shrink writes no expansion audit row'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config(
  'dev_test.expanded',
  public.developer_update_app(
    current_setting('dev_test.cocoon')::uuid,
    'Cocoon 2 pgtap',
    'Updated description',
    'https://cocoon2.example',
    '',
    array['https://cocoon2.example/callback'],
    array['friends:read', 'profile:read', 'messages:read', 'messages:write']
  )::text,
  true
);

select extensions.is(
  (current_setting('dev_test.expanded')::jsonb ->> 'consents_revoked')::integer,
  2,
  'expanding scopes reports the revoked consents'
);

select extensions.is(
  current_setting('dev_test.expanded')::jsonb -> 'app' -> 'scopes',
  '["profile:read","friends:read","messages:read","messages:write"]'::jsonb,
  'the expanded scopes are stored in canonical order'
);

reset role;

select extensions.is(
  (
    select count(*)
    from auth.oauth_consents as consent
    where consent.client_id = current_setting('dev_test.cocoon')::uuid
      and consent.revoked_at is not null
  ),
  2::bigint,
  'expanding scopes revokes every live consent'
);

select extensions.is(
  (
    select count(*)
    from auth.sessions as session
    where session.oauth_client_id = current_setting('dev_test.cocoon')::uuid
  ),
  0::bigint,
  'expanding scopes deletes the client sessions'
);

select extensions.is(
  (
    select count(*)
    from auth.sessions as session
    where session.id = '99700000-0000-4000-8000-000000000301'
  ),
  1::bigint,
  'the first-party session of a consenting user survives'
);

select extensions.is(
  (
    select app.scopes_changed_at
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  now(),
  'an expansion moves scopes_changed_at'
);

select extensions.is(
  (
    select audit.payload
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_scopes_expand'
  ),
  jsonb_build_object(
    'old', '["profile:read","messages:read"]'::jsonb,
    'new', '["profile:read","friends:read","messages:read","messages:write"]'::jsonb,
    'consents_revoked', 2,
    'sessions_deleted', 2
  ),
  'the expansion is audited with the counts'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select extensions.is(
  (
    select (item ->> 'connected_users')::integer
    from jsonb_array_elements(public.developer_list_apps() -> 'items') as item
    where item ->> 'client_id' = current_setting('dev_test.cocoon')
  ),
  0,
  'connected users drop to zero after an expansion'
);

reset role;

update private.developer_apps
set scopes_changed_at = now() - interval '3 hours'
where client_id = current_setting('dev_test.cocoon')::uuid;

update auth.oauth_consents
set revoked_at = null, granted_at = now() - interval '1 hour'
where client_id = current_setting('dev_test.cocoon')::uuid;

select extensions.is(
  (
    select (private.developer_app_json(app) ->> 'connected_users')::integer
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  2,
  'consents granted after scopes_changed_at are live again'
);

update private.developer_apps
set scopes = array['profile:read', 'friends:read', 'messages:read', 'messages:write', 'notifications:read']
where client_id = current_setting('dev_test.cocoon')::uuid;

select extensions.is(
  (
    select app.scopes_changed_at
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  now(),
  'a direct scope expansion moves scopes_changed_at'
);

select extensions.is(
  (
    select count(*)
    from auth.oauth_consents as consent
    where consent.client_id = current_setting('dev_test.cocoon')::uuid
      and consent.revoked_at is not null
  ),
  2::bigint,
  'a direct scope expansion revokes consents'
);

select extensions.is(
  (
    select count(*)
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_scopes_expand'
  ),
  2::bigint,
  'a direct scope expansion is audited'
);

update auth.oauth_consents
set revoked_at = null, granted_at = now() - interval '1 hour'
where client_id = current_setting('dev_test.cocoon')::uuid
  and user_id = '99700000-0000-4000-8000-000000000003';

select extensions.is(
  (
    select (private.developer_app_json(app) ->> 'connected_users')::integer
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  0,
  'a consent granted before scopes_changed_at is not live'
);

update auth.oauth_consents
set granted_at = now()
where client_id = current_setting('dev_test.cocoon')::uuid
  and user_id = '99700000-0000-4000-8000-000000000003';

select extensions.is(
  (
    select (private.developer_app_json(app) ->> 'connected_users')::integer
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  1,
  'a consent granted at scopes_changed_at is live'
);

update private.developer_apps
set scopes_changed_at = now() - interval '4 hours'
where client_id = current_setting('dev_test.cocoon')::uuid;

update private.developer_apps
set scopes = array['notifications:read', 'profile:read', 'messages:read', 'messages:write', 'friends:read']
where client_id = current_setting('dev_test.cocoon')::uuid;

select extensions.is(
  (
    select app.scopes
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  array['profile:read', 'friends:read', 'messages:read', 'messages:write', 'notifications:read']::text[],
  'a direct reorder is normalised to canonical order'
);

select extensions.is(
  (
    select app.scopes_changed_at
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  now() - interval '4 hours',
  'a direct reorder does not move scopes_changed_at'
);

select extensions.is(
  (
    select count(*)
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_scopes_expand'
  ),
  2::bigint,
  'a direct reorder is not audited as an expansion'
);

select extensions.throws_ok(
  format(
    $$update private.developer_apps set scopes = array['bogus'] where client_id = %L$$,
    current_setting('dev_test.cocoon')
  ),
  '23514',
  null,
  'unknown scopes cannot be stored even directly'
);

insert into private.api_usage (client_id, day, user_id, requests, denied)
values
  (
    current_setting('dev_test.cocoon')::uuid,
    (now() at time zone 'utc')::date,
    '99700000-0000-4000-8000-000000000003',
    10,
    2
  ),
  (
    current_setting('dev_test.cocoon')::uuid,
    (now() at time zone 'utc')::date,
    '99700000-0000-4000-8000-000000000004',
    5,
    0
  ),
  (
    current_setting('dev_test.cocoon')::uuid,
    (now() at time zone 'utc')::date - 1,
    '99700000-0000-4000-8000-000000000003',
    3,
    1
  ),
  (
    current_setting('dev_test.cocoon')::uuid,
    (now() at time zone 'utc')::date - 40,
    '99700000-0000-4000-8000-000000000003',
    100,
    0
  );

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config(
  'dev_test.usage',
  public.developer_app_usage(current_setting('dev_test.cocoon')::uuid, 7)::text,
  true
);

select extensions.is(
  jsonb_array_length(current_setting('dev_test.usage')::jsonb -> 'items'),
  7,
  'usage returns one row per requested day'
);

select extensions.is(
  current_setting('dev_test.usage')::jsonb -> 'items' -> 6 ->> 'day',
  (now() at time zone 'utc')::date::text,
  'the last usage row is today in UTC'
);

select extensions.is(
  (current_setting('dev_test.usage')::jsonb -> 'items' -> 6 ->> 'requests')::integer,
  15,
  'today sums requests across users'
);

select extensions.is(
  (current_setting('dev_test.usage')::jsonb -> 'items' -> 6 ->> 'denied')::integer,
  2,
  'today sums denials across users'
);

select extensions.is(
  (current_setting('dev_test.usage')::jsonb -> 'items' -> 6 ->> 'users')::integer,
  2,
  'today counts distinct users'
);

select extensions.is(
  (current_setting('dev_test.usage')::jsonb -> 'items' -> 5 ->> 'requests')::integer,
  3,
  'yesterday carries its own requests'
);

select extensions.is(
  (current_setting('dev_test.usage')::jsonb -> 'items' -> 5 ->> 'users')::integer,
  1,
  'yesterday counts one user'
);

select extensions.is(
  current_setting('dev_test.usage')::jsonb -> 'items' -> 0,
  jsonb_build_object(
    'day', ((now() at time zone 'utc')::date - 6)::text,
    'requests', 0,
    'denied', 0,
    'users', 0
  ),
  'days without traffic are zero-filled'
);

select extensions.is(
  jsonb_array_length(public.developer_app_usage(current_setting('dev_test.cocoon')::uuid, 0) -> 'items'),
  1,
  'p_days clamps up to one'
);

select extensions.is(
  jsonb_array_length(public.developer_app_usage(current_setting('dev_test.cocoon')::uuid, 500) -> 'items'),
  90,
  'p_days clamps down to ninety'
);

select extensions.is(
  jsonb_array_length(public.developer_app_usage(current_setting('dev_test.cocoon')::uuid, null) -> 'items'),
  30,
  'p_days defaults to thirty'
);

select extensions.is(
  (
    select (item ->> 'requests_30d')::integer
    from jsonb_array_elements(public.developer_list_apps() -> 'items') as item
    where item ->> 'client_id' = current_setting('dev_test.cocoon')
  ),
  18,
  'requests_30d ignores usage older than thirty days'
);

select extensions.is(
  (
    select (item ->> 'denied_30d')::integer
    from jsonb_array_elements(public.developer_list_apps() -> 'items') as item
    where item ->> 'client_id' = current_setting('dev_test.cocoon')
  ),
  3,
  'denied_30d sums the recent denials'
);

reset role;

insert into auth.sessions (id, user_id, created_at, updated_at, aal, oauth_client_id, scopes)
values (
  '99700000-0000-4000-8000-000000000304',
  '99700000-0000-4000-8000-000000000003',
  now(),
  now(),
  'aal1'::auth.aal_level,
  current_setting('dev_test.cocoon')::uuid,
  'openid'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000006', true);

select extensions.throws_ok(
  format($$select public.admin_set_developer_app_status(%L, 'suspended')$$, current_setting('dev_test.cocoon')),
  '42501',
  'Permission required: apps',
  'an admin without the apps permission cannot suspend'
);

select extensions.throws_ok(
  $$select public.admin_list_developer_apps()$$,
  '42501',
  'Permission required: apps',
  'an admin without the apps permission cannot list apps'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  format($$select public.admin_set_developer_app_status(%L, 'suspended')$$, current_setting('dev_test.cocoon')),
  '42501',
  'Admin access required',
  'a developer cannot suspend their own app through the admin RPC'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000005', true);

select extensions.throws_ok(
  format($$select public.admin_set_developer_app_status(%L, 'paused')$$, current_setting('dev_test.cocoon')),
  '22023',
  'p_status must be active or suspended',
  'an unknown status is rejected'
);

select extensions.throws_ok(
  $$select public.admin_set_developer_app_status('99700000-0000-4000-8000-000000000999', 'suspended')$$,
  'PT404',
  'App not found',
  'an unknown app cannot be suspended'
);

select extensions.is(
  public.admin_set_developer_app_status(current_setting('dev_test.cocoon')::uuid, 'suspended'),
  jsonb_build_object('client_id', current_setting('dev_test.cocoon')::uuid, 'status', 'suspended'),
  'an apps admin can suspend an app'
);

reset role;

select extensions.ok(
  (
    select client.deleted_at is not null
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'suspension soft-deletes the GoTrue client'
);

select extensions.is(
  (
    select count(*)
    from auth.sessions as session
    where session.oauth_client_id = current_setting('dev_test.cocoon')::uuid
  ),
  0::bigint,
  'suspension deletes the client sessions'
);

select extensions.is(
  (
    select count(*)
    from auth.oauth_consents as consent
    where consent.client_id = current_setting('dev_test.cocoon')::uuid
      and consent.revoked_at is null
  ),
  1::bigint,
  'suspension keeps consents'
);

select extensions.is(
  (
    select app.status
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  'suspended',
  'the registry row is suspended'
);

select extensions.is(
  (
    select count(*)
    from private.admin_audit as audit
    where audit.admin_id = '99700000-0000-4000-8000-000000000005'
      and audit.action = 'set_developer_app_status'
      and audit.target_user_id = '99700000-0000-4000-8000-000000000001'
      and audit.payload ->> 'status' = 'suspended'
      and audit.payload ->> 'client_id' = current_setting('dev_test.cocoon')
  ),
  1::bigint,
  'suspension is recorded in the admin audit'
);

select extensions.is(
  (
    select count(*)
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_status'
      and audit.payload ->> 'status' = 'suspended'
  ),
  1::bigint,
  'suspension is recorded in the developer audit'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  format(
    $$select public.developer_update_app(%L, 'Cocoon 3 pgtap', '', '', '', array['https://cocoon2.example/callback'], array['profile:read'])$$,
    current_setting('dev_test.cocoon')
  ),
  'PT403',
  'This app has been suspended',
  'a suspended app cannot be updated'
);

select extensions.throws_ok(
  format($$select public.developer_rotate_secret(%L)$$, current_setting('dev_test.cocoon')),
  'PT403',
  'This app has been suspended',
  'a suspended app cannot rotate its secret'
);

select extensions.is(
  (
    select item ->> 'status'
    from jsonb_array_elements(public.developer_list_apps() -> 'items') as item
    where item ->> 'client_id' = current_setting('dev_test.cocoon')
  ),
  'suspended',
  'a suspended app stays visible to its owner'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000003', true);

select extensions.throws_ok(
  format($$select public.api_app_info(%L)$$, current_setting('dev_test.cocoon')),
  'PT403',
  'This app has been suspended',
  'the consent page refuses a suspended app'
);

select extensions.is(
  (
    select count(*)
    from jsonb_array_elements(public.api_connected_apps() -> 'items') as item
    where item ->> 'client_id' = current_setting('dev_test.cocoon')
  ),
  1::bigint,
  'a suspended app still shows in connected apps'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000005', true);

select pg_catalog.set_config(
  'dev_test.admin_list',
  public.admin_list_developer_apps('Cocoon 2 pgtap')::text,
  true
);

select extensions.is(
  (current_setting('dev_test.admin_list')::jsonb ->> 'total_count')::integer,
  1,
  'admins can search apps by name'
);

select extensions.is(
  current_setting('dev_test.admin_list')::jsonb -> 'items' -> 0 ->> 'status',
  'suspended',
  'the admin list shows the status'
);

select extensions.is(
  current_setting('dev_test.admin_list')::jsonb -> 'items' -> 0 ->> 'owner_display_name',
  'Dev One pgtap',
  'the admin list shows the owner'
);

select extensions.is(
  (current_setting('dev_test.admin_list')::jsonb -> 'items' -> 0 ->> 'connected_users')::integer,
  1,
  'the admin list counts live consents'
);

select extensions.is(
  current_setting('dev_test.admin_list')::jsonb -> 'items' -> 0 ->> 'client_type',
  'confidential',
  'the admin list shows the client type'
);

select extensions.is(
  (public.admin_list_developer_apps(current_setting('dev_test.cocoon')) ->> 'total_count')::integer,
  1,
  'admins can search apps by client id'
);

select extensions.is(
  (public.admin_list_developer_apps('Dev One pgtap') ->> 'total_count')::integer,
  2,
  'admins can search apps by owner display name'
);

select extensions.is(
  (public.admin_list_developer_apps('dev-one@pocketpass.test') ->> 'total_count')::integer,
  2,
  'admins can search apps by owner email'
);

select extensions.is(
  jsonb_array_length(public.admin_list_developer_apps('Dev One pgtap', 1, 0) -> 'items'),
  1,
  'the admin list honours the page size'
);

select extensions.ok(
  (public.admin_list_developer_apps('') ->> 'total_count')::integer >= 8,
  'an empty query lists every app'
);

select extensions.is(
  public.admin_set_developer_app_status(current_setting('dev_test.cocoon')::uuid, 'active') ->> 'status',
  'active',
  'an apps admin can reactivate an app'
);

reset role;

select extensions.ok(
  (
    select client.deleted_at is null
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'reactivation clears the GoTrue soft delete'
);

select extensions.is(
  (
    select app.status
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  'active',
  'the registry row is active again'
);

insert into auth.sessions (id, user_id, created_at, updated_at, aal, oauth_client_id, scopes)
values (
  '99700000-0000-4000-8000-000000000305',
  '99700000-0000-4000-8000-000000000003',
  now(),
  now(),
  'aal1'::auth.aal_level,
  current_setting('dev_test.cocoon')::uuid,
  'openid'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select extensions.is(
  (public.developer_delete_app(current_setting('dev_test.cocoon')::uuid) ->> 'deleted')::boolean,
  true,
  'the owner can delete the app'
);

reset role;

select extensions.is(
  (
    select count(*)
    from private.developer_apps as app
    where app.client_id = current_setting('dev_test.cocoon')::uuid
  ),
  0::bigint,
  'the registry row is gone'
);

select extensions.ok(
  (
    select client.deleted_at is not null
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.cocoon')::uuid
  ),
  'deletion soft-deletes the GoTrue client'
);

select extensions.is(
  (
    select count(*)
    from auth.oauth_consents as consent
    where consent.client_id = current_setting('dev_test.cocoon')::uuid
      and consent.revoked_at is null
  ),
  0::bigint,
  'deletion revokes the remaining consents'
);

select extensions.is(
  (
    select count(*)
    from auth.sessions as session
    where session.oauth_client_id = current_setting('dev_test.cocoon')::uuid
  ),
  0::bigint,
  'deletion deletes the client sessions'
);

select extensions.is(
  (
    select audit.payload
    from private.developer_audit as audit
    where audit.client_id = current_setting('dev_test.cocoon')::uuid
      and audit.action = 'app_delete'
  ),
  jsonb_build_object(
    'name', 'Cocoon 2 pgtap',
    'owner_user_id', '99700000-0000-4000-8000-000000000001',
    'consents_revoked', 1,
    'sessions_deleted', 1
  ),
  'deletion is audited with the cleanup counts'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  format($$select public.developer_delete_app(%L)$$, current_setting('dev_test.cocoon')),
  'PT404',
  'App not found',
  'deleting twice reports not found'
);

select extensions.is(
  jsonb_array_length(public.developer_list_apps() -> 'items'),
  1,
  'the deleted app leaves the owner list'
);

reset role;

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values (
  gen_random_uuid(),
  '99700000-0000-4000-8000-000000000003',
  current_setting('dev_test.limit2')::uuid,
  'openid',
  now()
);

insert into auth.sessions (id, user_id, created_at, updated_at, aal, oauth_client_id, scopes)
values (
  '99700000-0000-4000-8000-000000000306',
  '99700000-0000-4000-8000-000000000003',
  now(),
  now(),
  'aal1'::auth.aal_level,
  current_setting('dev_test.limit2')::uuid,
  'openid'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000002', true);

select extensions.lives_ok(
  $$select public.delete_my_account()$$,
  'a developer can delete their account'
);

reset role;

select extensions.is(
  (
    select count(*)
    from private.developer_apps as app
    where app.owner_user_id = '99700000-0000-4000-8000-000000000002'
  ),
  0::bigint,
  'deleting the account removes the registry rows'
);

select extensions.ok(
  (
    select client.deleted_at is not null
    from auth.oauth_clients as client
    where client.id = current_setting('dev_test.limit2')::uuid
  ),
  'deleting the account soft-deletes the GoTrue clients'
);

select extensions.is(
  (
    select count(*)
    from auth.oauth_consents as consent
    where consent.client_id = current_setting('dev_test.limit2')::uuid
      and consent.revoked_at is null
  ),
  0::bigint,
  'deleting the account revokes consents to its apps'
);

select extensions.is(
  (
    select count(*)
    from auth.sessions as session
    where session.oauth_client_id = current_setting('dev_test.limit2')::uuid
  ),
  0::bigint,
  'deleting the account deletes sessions of its apps'
);

select extensions.is(
  (
    select count(*)
    from private.developer_audit as audit
    where audit.action = 'app_delete'
      and audit.payload ->> 'owner_user_id' = '99700000-0000-4000-8000-000000000002'
  ),
  6::bigint,
  'every app of the deleted developer is audited'
);

select extensions.is(
  (
    select count(*)
    from private.developer_audit as audit
    where audit.payload::text like '%pp\_secret\_%'
  ),
  0::bigint,
  'audit rows never contain a secret'
);

select extensions.is(
  (
    select count(*)
    from private.developer_audit as audit
    where strpos(audit.payload::text, private.developer_secret_hash(current_setting('dev_test.rotated'))) > 0
  ),
  0::bigint,
  'audit rows never contain a secret hash'
);

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values (
  gen_random_uuid(),
  '99700000-0000-4000-8000-000000000003',
  current_setting('dev_test.native')::uuid,
  'openid',
  now()
);

insert into auth.sessions (id, user_id, created_at, updated_at, aal, oauth_client_id, scopes)
values (
  '99700000-0000-4000-8000-000000000307',
  '99700000-0000-4000-8000-000000000003',
  now(),
  now(),
  'aal1'::auth.aal_level,
  current_setting('dev_test.native')::uuid,
  'openid'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000003', true);

select extensions.is(
  (
    select count(*)
    from jsonb_array_elements(public.api_connected_apps() -> 'items') as item
    where item ->> 'client_id' = current_setting('dev_test.native')
  ),
  1::bigint,
  'a fresh consent shows in connected apps'
);

select extensions.is(
  (public.api_revoke_app(current_setting('dev_test.native')::uuid) ->> 'revoked')::boolean,
  true,
  'a user can revoke an app'
);

reset role;

select extensions.is(
  (
    select count(*)
    from auth.sessions as session
    where session.id = '99700000-0000-4000-8000-000000000307'
  ),
  0::bigint,
  'revoking deletes the sessions for that client'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000003', true);

select extensions.is(
  (public.api_revoke_app(current_setting('dev_test.native')::uuid) ->> 'revoked')::boolean,
  false,
  'revoking again reports nothing revoked'
);

select extensions.throws_ok(
  $$select public.api_revoke_app(null)$$,
  '22004',
  'p_client_id is required',
  'revoking requires a client id'
);

select extensions.is(
  (
    select count(*)
    from jsonb_array_elements(public.api_connected_apps() -> 'items') as item
    where item ->> 'client_id' = current_setting('dev_test.native')
  ),
  0::bigint,
  'a revoked app leaves connected apps'
);

reset role;

insert into auth.oauth_clients (
  id,
  client_secret_hash,
  registration_type,
  redirect_uris,
  grant_types,
  client_name,
  client_uri,
  logo_uri,
  client_type,
  token_endpoint_auth_method,
  created_at,
  updated_at
)
values (
  '99700000-0000-4000-8000-000000000201',
  '',
  'dynamic'::auth.oauth_registration_type,
  'https://legacy.example/cb',
  'authorization_code,refresh_token',
  'Legacy Client pgtap',
  null,
  null,
  'public'::auth.oauth_client_type,
  'none',
  now(),
  now()
);

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values (
  gen_random_uuid(),
  '99700000-0000-4000-8000-000000000003',
  '99700000-0000-4000-8000-000000000201',
  'openid',
  now()
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99700000-0000-4000-8000-000000000003', true);

select extensions.is(
  (
    select item ->> 'name'
    from jsonb_array_elements(public.api_connected_apps() -> 'items') as item
    where item ->> 'client_id' = '99700000-0000-4000-8000-000000000201'
  ),
  'Legacy Client pgtap',
  'a consent to an unregistered client falls back to the GoTrue name'
);

select extensions.is(
  (
    select item -> 'scopes'
    from jsonb_array_elements(public.api_connected_apps() -> 'items') as item
    where item ->> 'client_id' = '99700000-0000-4000-8000-000000000201'
  ),
  '[]'::jsonb,
  'an unregistered client lists no PocketPass scopes'
);

select extensions.is(
  (
    select item ->> 'website'
    from jsonb_array_elements(public.api_connected_apps() -> 'items') as item
    where item ->> 'client_id' = '99700000-0000-4000-8000-000000000201'
  ),
  '',
  'an unregistered client without a URI shows an empty website'
);

reset role;

select * from extensions.finish();

rollback;
