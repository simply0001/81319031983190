begin;

set local search_path = public, extensions;

select extensions.plan(27);

select pg_catalog.set_config(
  'hook_test.client_event',
  jsonb_build_object(
    'user_id', '99900000-0000-4000-8000-000000000101',
    'authentication_method', 'oauth',
    'claims', jsonb_build_object(
      'iss', 'https://api.pocketpass.xyz/auth/v1',
      'aud', 'authenticated',
      'exp', 1900000000,
      'iat', 1899996400,
      'sub', '99900000-0000-4000-8000-000000000101',
      'email', 'hook-user@pocketpass.test',
      'phone', '+15550100101',
      'app_metadata', jsonb_build_object('provider', 'email', 'providers', jsonb_build_array('email')),
      'user_metadata', jsonb_build_object('display_name', 'Hook User'),
      'role', 'authenticated',
      'aal', 'aal1',
      'amr', jsonb_build_array(jsonb_build_object('method', 'oauth', 'timestamp', 1899996400)),
      'session_id', '99900000-0000-4000-8000-000000000102',
      'is_anonymous', false,
      'client_id', '99900000-0000-4000-8000-000000000103',
      'scope', 'openid email profile'
    )
  )::text,
  true
);

select pg_catalog.set_config(
  'hook_test.first_party_event',
  jsonb_build_object(
    'user_id', '99900000-0000-4000-8000-000000000101',
    'authentication_method', 'otp',
    'claims', jsonb_build_object(
      'iss', 'https://api.pocketpass.xyz/auth/v1',
      'aud', 'authenticated',
      'exp', 1900000000,
      'iat', 1899996400,
      'sub', '99900000-0000-4000-8000-000000000101',
      'email', 'hook-user@pocketpass.test',
      'phone', '',
      'app_metadata', jsonb_build_object('provider', 'email', 'providers', jsonb_build_array('email')),
      'user_metadata', jsonb_build_object('display_name', 'Hook User'),
      'role', 'authenticated',
      'aal', 'aal1',
      'amr', jsonb_build_array(jsonb_build_object('method', 'otp', 'timestamp', 1899996400)),
      'session_id', '99900000-0000-4000-8000-000000000104',
      'is_anonymous', false
    )
  )::text,
  true
);

select pg_catalog.set_config(
  'hook_test.client_result',
  public.pocketpass_access_token_hook(current_setting('hook_test.client_event')::jsonb)::text,
  true
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'role',
  'api_client',
  'a client token is re-rolled to api_client'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'email',
  '',
  'the email claim is blanked'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'phone',
  '',
  'the phone claim is blanked'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' -> 'user_metadata',
  '{}'::jsonb,
  'user_metadata is emptied'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' -> 'app_metadata',
  '{}'::jsonb,
  'app_metadata is emptied'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'sub',
  '99900000-0000-4000-8000-000000000101',
  'sub is preserved'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'aud',
  'authenticated',
  'aud is preserved'
);

select extensions.is(
  (current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'exp')::bigint,
  1900000000::bigint,
  'exp is preserved'
);

select extensions.is(
  (current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'iat')::bigint,
  1899996400::bigint,
  'iat is preserved'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'iss',
  'https://api.pocketpass.xyz/auth/v1',
  'iss is preserved'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'aal',
  'aal1',
  'aal is preserved'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' -> 'amr',
  current_setting('hook_test.client_event')::jsonb -> 'claims' -> 'amr',
  'amr is preserved'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'session_id',
  '99900000-0000-4000-8000-000000000102',
  'session_id is preserved'
);

select extensions.is(
  (current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'is_anonymous')::boolean,
  false,
  'is_anonymous is preserved'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'client_id',
  '99900000-0000-4000-8000-000000000103',
  'client_id is preserved'
);

select extensions.is(
  current_setting('hook_test.client_result')::jsonb -> 'claims' ->> 'scope',
  'openid email profile',
  'scope is preserved'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('hook_test.client_result')::jsonb) as keys(key)
  ),
  array['claims']::text[],
  'the client branch returns only the claims object'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('hook_test.client_result')::jsonb -> 'claims') as keys(key)
  ),
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('hook_test.client_event')::jsonb -> 'claims') as keys(key)
  ),
  'the client branch neither adds nor drops claim keys'
);

select extensions.is(
  public.pocketpass_access_token_hook(current_setting('hook_test.first_party_event')::jsonb),
  current_setting('hook_test.first_party_event')::jsonb,
  'a first-party event is returned unchanged'
);

select extensions.is(
  public.pocketpass_access_token_hook(
    jsonb_set(current_setting('hook_test.first_party_event')::jsonb, '{claims,client_id}', '""'::jsonb)
  ),
  jsonb_set(current_setting('hook_test.first_party_event')::jsonb, '{claims,client_id}', '""'::jsonb),
  'an empty client_id is treated as first party'
);

select extensions.is(
  public.pocketpass_access_token_hook(
    jsonb_set(current_setting('hook_test.first_party_event')::jsonb, '{claims,client_id}', 'null'::jsonb)
  ),
  jsonb_set(current_setting('hook_test.first_party_event')::jsonb, '{claims,client_id}', 'null'::jsonb),
  'a null client_id is treated as first party'
);

select extensions.is(
  public.pocketpass_access_token_hook('{}'::jsonb),
  '{}'::jsonb,
  'an event without claims is returned unchanged'
);

select extensions.is(
  public.pocketpass_access_token_hook(
    current_setting('hook_test.client_event')::jsonb
  ) -> 'claims' ->> 'role',
  'api_client',
  'the hook is deterministic for the same client event'
);

select extensions.ok(
  (
    select proc.prosecdef and proc.provolatile = 's'
    from pg_catalog.pg_proc as proc
    where proc.oid = 'public.pocketpass_access_token_hook(jsonb)'::regprocedure
  ),
  'the hook is a stable security definer function'
);

select extensions.ok(
  exists (
    select 1
    from pg_catalog.pg_proc as proc
    cross join unnest(proc.proconfig) as config(setting)
    where proc.oid = 'public.pocketpass_access_token_hook(jsonb)'::regprocedure
      and config.setting like 'search\_path=%'
  ),
  'the hook pins its search_path'
);

select extensions.ok(
  pg_catalog.has_function_privilege(
    'supabase_auth_admin',
    'public.pocketpass_access_token_hook(jsonb)',
    'execute'
  ),
  'supabase_auth_admin can execute the hook'
);

select extensions.ok(
  not pg_catalog.has_function_privilege(
    'authenticated',
    'public.pocketpass_access_token_hook(jsonb)',
    'execute'
  ),
  'authenticated cannot execute the hook'
);

select * from extensions.finish();

rollback;
