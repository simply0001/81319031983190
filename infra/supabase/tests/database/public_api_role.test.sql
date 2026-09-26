begin;

set local search_path = public, extensions;

select extensions.plan(64);

select extensions.ok(
  exists (
    select 1
    from pg_catalog.pg_roles
    where rolname = 'api_client'
  ),
  'the api_client role exists'
);

select extensions.ok(
  (
    select not rolcanlogin
    from pg_catalog.pg_roles
    where rolname = 'api_client'
  ),
  'api_client cannot log in directly'
);

select extensions.ok(
  pg_catalog.pg_has_role('authenticator', 'api_client', 'member'),
  'api_client is granted to authenticator'
);

select extensions.ok(
  pg_catalog.pg_has_role('supabase_storage_admin', 'api_client', 'set'),
  'storage-api can assume api_client through authenticator'
);

select extensions.ok(
  pg_catalog.pg_has_role('postgres', 'api_client', 'set'),
  'the test runner can assume api_client through authenticator'
);

select extensions.ok(
  pg_catalog.has_schema_privilege('api_client', 'public', 'usage'),
  'api_client can use the public schema'
);

select extensions.ok(
  pg_catalog.has_schema_privilege('api_client', 'private', 'usage'),
  'api_client can use the private schema'
);

select extensions.ok(
  pg_catalog.has_schema_privilege('api_client', 'storage', 'usage'),
  'api_client can use the storage schema'
);

select extensions.ok(
  pg_catalog.has_schema_privilege('api_client', 'extensions', 'usage'),
  'api_client can use the extensions schema'
);

select extensions.ok(
  not pg_catalog.has_schema_privilege('api_client', 'auth', 'usage'),
  'api_client cannot use the auth schema'
);

select extensions.ok(
  pg_catalog.has_schema_privilege('api_client', 'realtime', 'usage'),
  'api_client can use the realtime schema'
);

select extensions.ok(
  case
    when exists (
      select 1 from pg_catalog.pg_namespace where nspname = 'graphql_public'
    ) then not pg_catalog.has_schema_privilege('api_client', 'graphql_public', 'usage')
    else true
  end,
  'api_client cannot use the graphql_public schema'
);

select extensions.is(
  (
    select coalesce(array_agg(rel.name order by rel.name collate "C"), '{}'::text[])
    from (
      select rel.oid, ns.nspname || '.' || rel.relname as name
      from pg_catalog.pg_class as rel
      join pg_catalog.pg_namespace as ns on ns.oid = rel.relnamespace
      where ns.nspname in ('public', 'private', 'auth', 'realtime', 'graphql_public')
        and rel.relkind in ('r', 'v', 'm', 'p', 'f')
      offset 0
    ) as rel
    where pg_catalog.has_table_privilege(
        'api_client',
        rel.oid,
        'select, insert, update, delete, truncate, references, trigger'
      )
  ),
  array['realtime.messages']::text[],
  'api_client holds a privilege on realtime.messages only in public, private, auth, realtime or graphql_public'
);

select extensions.ok(
  pg_catalog.has_table_privilege('api_client', 'realtime.messages', 'select')
    and pg_catalog.has_table_privilege('api_client', 'realtime.messages', 'insert')
    and not pg_catalog.has_table_privilege('api_client', 'realtime.messages', 'update, delete, truncate, references, trigger'),
  'api_client can only read realtime.messages and insert presence rows'
);

select extensions.is(
  (
    select count(*)
    from (
      select rel.oid
      from pg_catalog.pg_class as rel
      join pg_catalog.pg_namespace as ns on ns.oid = rel.relnamespace
      where ns.nspname in ('public', 'private', 'auth', 'realtime', 'graphql_public')
        and rel.relkind = 'S'
      offset 0
    ) as rel
    where pg_catalog.has_sequence_privilege('api_client', rel.oid, 'usage, select, update')
  ),
  0::bigint,
  'api_client holds no privilege on any sequence in those schemas'
);

select extensions.is(
  (
    select coalesce(
      array_agg((ns.nspname || '.' || proc.proname) order by (ns.nspname || '.' || proc.proname) collate "C"),
      '{}'::text[]
    )
    from pg_catalog.pg_proc as proc
    join pg_catalog.pg_namespace as ns on ns.oid = proc.pronamespace
    where ns.nspname in ('public', 'private')
      and pg_catalog.has_function_privilege('api_client', proc.oid, 'execute')
  ),
  array[
    'private.api_can_access_realtime_topic',
    'private.api_can_read_object',
    'private.api_can_read_presence_topic',
    'private.api_can_track_presence_topic',
    'private.api_can_write_object',
    'public.api_v1_conversations_add_members',
    'public.api_v1_conversations_create',
    'public.api_v1_conversations_get',
    'public.api_v1_conversations_leave',
    'public.api_v1_conversations_list',
    'public.api_v1_conversations_mark_read',
    'public.api_v1_conversations_open',
    'public.api_v1_conversations_remove_member',
    'public.api_v1_conversations_rename',
    'public.api_v1_encounters_list',
    'public.api_v1_friends_code_get',
    'public.api_v1_friends_code_resolve',
    'public.api_v1_friends_list',
    'public.api_v1_friends_remove',
    'public.api_v1_friends_request_cancel',
    'public.api_v1_friends_request_respond',
    'public.api_v1_friends_request_send',
    'public.api_v1_friends_requests_list',
    'public.api_v1_me_get',
    'public.api_v1_messages_delete',
    'public.api_v1_messages_edit',
    'public.api_v1_messages_list',
    'public.api_v1_messages_send',
    'public.api_v1_notifications_delete',
    'public.api_v1_notifications_list',
    'public.api_v1_notifications_mark_read',
    'public.api_v1_profiles_get',
    'public.api_v1_profiles_get_many',
    'public.api_v1_puzzles_get',
    'public.api_v1_session_get',
    'public.api_v1_session_revoke',
    'public.api_v1_tokens_get'
  ]::text[],
  'api_client can execute only the api_v1 functions and the storage and realtime predicates'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_proc as proc
    where proc.pronamespace = 'public'::regnamespace
      and proc.proname like 'api\_v1\_%'
  ),
  32::bigint,
  'thirty-two api_v1 functions exist'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_proc as proc
    where proc.pronamespace = 'public'::regnamespace
      and proc.proname like 'api\_v1\_%'
      and pg_catalog.has_function_privilege('anon', proc.oid, 'execute')
  ),
  0::bigint,
  'anon cannot execute any api_v1 function'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_proc as proc
    where proc.pronamespace = 'public'::regnamespace
      and proc.proname like 'api\_v1\_%'
      and pg_catalog.has_function_privilege('authenticated', proc.oid, 'execute')
  ),
  0::bigint,
  'authenticated cannot execute any api_v1 function'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_proc as proc
    where proc.pronamespace = 'public'::regnamespace
      and proc.proname like 'api\_v1\_%'
      and pg_catalog.has_function_privilege('service_role', proc.oid, 'execute')
  ),
  0::bigint,
  'service_role cannot execute any api_v1 function'
);

select extensions.ok(
  pg_catalog.has_function_privilege(
    'supabase_auth_admin',
    'public.pocketpass_access_token_hook(jsonb)',
    'execute'
  ),
  'supabase_auth_admin can execute the access token hook'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.pocketpass_access_token_hook(jsonb)', 'execute'),
  'anon cannot execute the access token hook'
);

select extensions.ok(
  not pg_catalog.has_function_privilege(
    'authenticated',
    'public.pocketpass_access_token_hook(jsonb)',
    'execute'
  ),
  'authenticated cannot execute the access token hook'
);

select extensions.ok(
  not pg_catalog.has_function_privilege(
    'service_role',
    'public.pocketpass_access_token_hook(jsonb)',
    'execute'
  ),
  'service_role cannot execute the access token hook'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('api_client', 'public.pocketpass_access_token_hook(jsonb)', 'execute'),
  'api_client cannot execute the access token hook'
);

select extensions.ok(
  pg_catalog.has_schema_privilege('supabase_auth_admin', 'public', 'usage'),
  'supabase_auth_admin can reach the public schema for the hook'
);

select extensions.is(
  (
    select array_agg(
      (att.attname || ':' || pg_catalog.format_type(att.atttypid, att.atttypmod))
      order by att.attname::text collate "C"
    )
    from pg_catalog.pg_attribute as att
    where att.attrelid = 'auth.oauth_clients'::regclass
      and att.attnum > 0
      and not att.attisdropped
      and att.attname in (
        'id',
        'client_secret_hash',
        'registration_type',
        'redirect_uris',
        'grant_types',
        'client_name',
        'client_uri',
        'logo_uri',
        'created_at',
        'updated_at',
        'deleted_at',
        'client_type',
        'token_endpoint_auth_method'
      )
  ),
  array[
    'client_name:text',
    'client_secret_hash:text',
    'client_type:auth.oauth_client_type',
    'client_uri:text',
    'created_at:timestamp with time zone',
    'deleted_at:timestamp with time zone',
    'grant_types:text',
    'id:uuid',
    'logo_uri:text',
    'redirect_uris:text',
    'registration_type:auth.oauth_registration_type',
    'token_endpoint_auth_method:text',
    'updated_at:timestamp with time zone'
  ]::text[],
  'auth.oauth_clients has the columns and types the registry writes'
);

select extensions.is(
  (
    select array_agg(
      (att.attname || ':' || pg_catalog.format_type(att.atttypid, att.atttypmod))
      order by att.attname::text collate "C"
    )
    from pg_catalog.pg_attribute as att
    where att.attrelid = 'auth.oauth_consents'::regclass
      and att.attnum > 0
      and not att.attisdropped
      and att.attname in ('id', 'user_id', 'client_id', 'scopes', 'granted_at', 'revoked_at')
  ),
  array[
    'client_id:uuid',
    'granted_at:timestamp with time zone',
    'id:uuid',
    'revoked_at:timestamp with time zone',
    'scopes:text',
    'user_id:uuid'
  ]::text[],
  'auth.oauth_consents has the columns the guard reads'
);

select extensions.is(
  (
    select array_agg(
      (att.attname || ':' || pg_catalog.format_type(att.atttypid, att.atttypmod))
      order by att.attname::text collate "C"
    )
    from pg_catalog.pg_attribute as att
    where att.attrelid = 'auth.sessions'::regclass
      and att.attnum > 0
      and not att.attisdropped
      and att.attname in ('oauth_client_id', 'scopes')
  ),
  array['oauth_client_id:uuid', 'scopes:text']::text[],
  'auth.sessions carries the OAuth client columns'
);

select extensions.ok(
  exists (
    select 1
    from pg_catalog.pg_attribute as att
    where att.attrelid = 'auth.oauth_authorizations'::regclass
      and att.attname = 'created_at'
      and not att.attisdropped
  ),
  'auth.oauth_authorizations has created_at for the prune job'
);

select extensions.is(
  (
    select array_agg(enumlabel::text order by enumlabel::text collate "C")
    from pg_catalog.pg_enum
    where enumtypid = 'auth.oauth_client_type'::regtype
  ),
  array['confidential', 'public']::text[],
  'auth.oauth_client_type has the expected labels'
);

select extensions.is(
  (
    select array_agg(enumlabel::text order by enumlabel::text collate "C")
    from pg_catalog.pg_enum
    where enumtypid = 'auth.oauth_registration_type'::regtype
  ),
  array['dynamic', 'manual']::text[],
  'auth.oauth_registration_type has the expected labels'
);

select extensions.ok(
  pg_catalog.has_table_privilege('postgres', 'auth.oauth_clients', 'insert')
    and pg_catalog.has_table_privilege('postgres', 'auth.oauth_clients', 'update')
    and pg_catalog.has_table_privilege('postgres', 'auth.oauth_clients', 'delete'),
  'postgres holds DML on auth.oauth_clients'
);

select extensions.ok(
  pg_catalog.has_table_privilege('postgres', 'auth.oauth_consents', 'insert')
    and pg_catalog.has_table_privilege('postgres', 'auth.oauth_consents', 'update')
    and pg_catalog.has_table_privilege('postgres', 'auth.oauth_consents', 'delete'),
  'postgres holds DML on auth.oauth_consents'
);

select extensions.ok(
  pg_catalog.has_table_privilege('postgres', 'auth.sessions', 'insert')
    and pg_catalog.has_table_privilege('postgres', 'auth.sessions', 'update')
    and pg_catalog.has_table_privilege('postgres', 'auth.sessions', 'delete'),
  'postgres holds DML on auth.sessions'
);

select extensions.ok(
  pg_catalog.has_table_privilege('postgres', 'auth.oauth_authorizations', 'select')
    and pg_catalog.has_table_privilege('postgres', 'auth.oauth_authorizations', 'delete'),
  'postgres can prune auth.oauth_authorizations'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_policies
    where schemaname = 'realtime'
      and tablename = 'messages'
      and 'api_client' = any (roles)
  ),
  2::bigint,
  'realtime.messages has a read and a presence-track policy for api_client'
);

select extensions.ok(
  pg_catalog.has_table_privilege('api_client', 'storage.objects', 'select'),
  'api_client can select storage objects'
);

select extensions.ok(
  pg_catalog.has_table_privilege('api_client', 'storage.objects', 'insert')
    and not pg_catalog.has_table_privilege('api_client', 'storage.objects', 'update')
    and not pg_catalog.has_table_privilege('api_client', 'storage.objects', 'delete'),
  'api_client can insert but not update or delete storage objects'
);

select extensions.ok(
  not pg_catalog.has_table_privilege(
    'api_client',
    'storage.buckets',
    'select, insert, update, delete, truncate, references, trigger'
  ),
  'api_client holds no privilege on storage.buckets'
);

select extensions.is(
  (
    select array_agg(policyname::text order by policyname::text collate "C")
    from pg_catalog.pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and 'api_client' = any (roles)
  ),
  array[
    'pocketpass_api_avatars_read',
    'pocketpass_api_message_media_insert',
    'pocketpass_api_message_media_read'
  ]::text[],
  'api_client has exactly two read policies and one insert policy on storage.objects'
);

select extensions.is(
  (
    select array_agg(cmd::text order by cmd::text collate "C")
    from pg_catalog.pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and 'api_client' = any (roles)
  ),
  array['INSERT', 'SELECT', 'SELECT']::text[],
  'api_client storage policies never update or delete'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and policyname like 'pocketpass\_avatars\_%'
  ),
  4::bigint,
  'the counted avatars policy prefix is untouched'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and policyname like 'pocketpass\_message\_media\_%'
  ),
  4::bigint,
  'the counted message-media policy prefix is untouched'
);

select extensions.ok(
  pg_catalog.has_function_privilege(
    'authenticated',
    'private.is_sanitized_mii_appearance(jsonb, integer)',
    'execute'
  ),
  'authenticated keeps execute on is_sanitized_mii_appearance'
);

select extensions.ok(
  pg_catalog.has_function_privilege(
    'authenticated',
    'private.mii_json_int_between(jsonb, text, integer, integer)',
    'execute'
  ),
  'authenticated keeps execute on mii_json_int_between'
);

select extensions.ok(
  pg_catalog.has_function_privilege(
    'authenticated',
    'private.mii_optional_common_color(jsonb, text)',
    'execute'
  ),
  'authenticated keeps execute on mii_optional_common_color'
);

select extensions.is(
  private.api_scope_keys(),
  array['profile:read', 'friends:read', 'friends:write', 'messages:read', 'messages:write', 'groups:write', 'notifications:read', 'presence:read', 'presence:write', 'tokens:read', 'encounters:read', 'puzzles:read']::text[],
  'the scope catalog is in canonical order'
);

select extensions.is(
  private.api_normalize_scopes(array['messages:write', 'profile:read', 'bogus', 'profile:read']),
  array['profile:read', 'messages:write']::text[],
  'scope normalisation dedupes, drops unknown keys and restores canonical order'
);

select extensions.is(
  private.api_scope_descriptions() ->> 'messages:write',
  'Send, edit and delete messages as you',
  'scope descriptions are exposed'
);

select extensions.is(
  private.api_oidc_scope_descriptions(),
  jsonb_build_object(
    'email', 'your email address',
    'phone', 'your phone number',
    'profile', 'your account name and picture'
  ),
  'OIDC scope descriptions are exposed'
);

select extensions.is(
  private.api_rate_limit_per_minute(),
  120,
  'the per app and user rate limit is 120 per minute'
);

select extensions.is(
  private.admin_permission_keys(),
  array['users', 'audit', 'legacy', 'tokens', 'achievements', 'admins', 'apps', 'supporters']::text[],
  'the admin permission catalog gained supporters'
);

select extensions.has_table('private', 'developer_apps', 'developer_apps table exists');
select extensions.has_table('private', 'developer_audit', 'developer_audit table exists');
select extensions.has_table('private', 'api_usage', 'api_usage table exists');
select extensions.has_table('private', 'api_rate_buckets', 'api_rate_buckets table exists');
select extensions.has_table('private', 'api_rate_buckets_app', 'api_rate_buckets_app table exists');

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_class as rel
    where rel.relnamespace = 'private'::regnamespace
      and rel.relname in (
        'developer_apps',
        'developer_audit',
        'api_usage',
        'api_rate_buckets',
        'api_rate_buckets_app'
      )
      and pg_catalog.has_table_privilege(
        'authenticated',
        rel.oid,
        'select, insert, update, delete, truncate, references, trigger'
      )
  ),
  0::bigint,
  'authenticated holds no privilege on the registry tables'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_indexes
    where (schemaname, indexname) in (
      ('public', 'conversations_updated_id_idx'),
      ('public', 'messages_conversation_edited_idx'),
      ('public', 'messages_conversation_deleted_idx')
    )
  ),
  3::bigint,
  'the keyset indexes exist'
);

select extensions.has_trigger(
  'private',
  'developer_apps',
  'developer_apps_after_delete',
  'deleting an app cleans up its GoTrue client'
);

select extensions.has_trigger(
  'private',
  'developer_apps',
  'developer_apps_before_update_scopes',
  'scope changes are normalised and expansion revokes consents'
);

select extensions.lives_ok(
  $$select private.prune_api_rate_buckets()$$,
  'the rate bucket prune job runs'
);

select extensions.lives_ok(
  $$select private.prune_oauth_authorizations()$$,
  'the authorization prune job runs'
);

select * from extensions.finish();

rollback;
