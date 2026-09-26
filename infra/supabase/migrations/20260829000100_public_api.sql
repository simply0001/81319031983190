begin;

do $$
begin
  if not exists (
    select 1 from pg_catalog.pg_roles where rolname = 'api_client'
  ) then
    create role api_client nologin;
  end if;
end $$;

grant api_client to authenticator;
grant usage on schema public to api_client;
grant usage on schema private to api_client;
grant usage on schema storage to api_client;
grant usage on schema extensions to api_client;
grant select on table storage.objects to api_client;

revoke all on function private.is_sanitized_mii_appearance(jsonb, integer) from public;
revoke all on function private.mii_json_int_between(jsonb, text, integer, integer) from public;
revoke all on function private.mii_optional_common_color(jsonb, text) from public;
grant execute on function private.is_sanitized_mii_appearance(jsonb, integer) to authenticated;
grant execute on function private.mii_json_int_between(jsonb, text, integer, integer) to authenticated;
grant execute on function private.mii_optional_common_color(jsonb, text) to authenticated;

create or replace function private.api_scope_keys()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array[
    'profile:read',
    'friends:read',
    'messages:read',
    'messages:write',
    'notifications:read'
  ]::text[];
$$;

revoke all on function private.api_scope_keys() from public;

create or replace function private.api_scope_descriptions()
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'profile:read', 'See your profile (name, bio, avatar, age, country)',
    'friends:read', 'See your friends list',
    'messages:read', 'Read your conversations and messages',
    'messages:write', 'Send, edit and delete messages as you',
    'notifications:read', 'See and clear your notifications'
  );
$$;

revoke all on function private.api_scope_descriptions() from public;

create or replace function private.api_oidc_scope_descriptions()
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'email', 'your email address',
    'phone', 'your phone number',
    'profile', 'your account name and picture'
  );
$$;

revoke all on function private.api_oidc_scope_descriptions() from public;

create or replace function private.api_normalize_scopes(p_scopes text[])
returns text[]
language sql
immutable
set search_path = ''
as $$
  select coalesce(
    (
      select array_agg(keyed.key order by keyed.ordinality)
      from unnest(private.api_scope_keys()) with ordinality as keyed(key, ordinality)
      where keyed.key = any (coalesce(p_scopes, '{}'::text[]))
    ),
    '{}'::text[]
  );
$$;

revoke all on function private.api_normalize_scopes(text[]) from public;

create or replace function private.api_scope_objects(p_scopes text[])
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select coalesce(
    (
      select jsonb_agg(
        jsonb_build_object(
          'key', keyed.key,
          'description', private.api_scope_descriptions() ->> keyed.key
        )
        order by keyed.ordinality
      )
      from unnest(private.api_scope_keys()) with ordinality as keyed(key, ordinality)
      where keyed.key = any (coalesce(p_scopes, '{}'::text[]))
    ),
    '[]'::jsonb
  );
$$;

revoke all on function private.api_scope_objects(text[]) from public;

create or replace function private.api_rate_limit_per_minute()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 120;
$$;

revoke all on function private.api_rate_limit_per_minute() from public;

create or replace function private.developer_max_apps()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 5;
$$;

revoke all on function private.developer_max_apps() from public;

create or replace function private.api_try_uuid(p_value text)
returns uuid
language plpgsql
immutable
set search_path = ''
as $$
begin
  return p_value::uuid;
exception
  when others then
    return null;
end;
$$;

revoke all on function private.api_try_uuid(text) from public;

create table private.developer_apps (
  client_id uuid primary key,
  owner_user_id uuid not null references public.profiles (user_id) on delete cascade,
  name text not null,
  description text not null default '',
  website text not null default '',
  logo_url text not null default '',
  client_type text not null,
  scopes text[] not null,
  scopes_changed_at timestamptz not null default now(),
  status text not null default 'active',
  rate_limit_per_minute integer not null default 600,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint developer_apps_name_length check (
    char_length(btrim(name)) between 1 and 64
  ),
  constraint developer_apps_description_length check (char_length(description) <= 280),
  constraint developer_apps_website_shape check (
    (website = '' or website ~ '^https://') and char_length(website) <= 2048
  ),
  constraint developer_apps_logo_url_shape check (
    (logo_url = '' or logo_url ~ '^https://') and char_length(logo_url) <= 2048
  ),
  constraint developer_apps_client_type_known check (
    client_type in ('public', 'confidential')
  ),
  constraint developer_apps_scopes_known check (
    scopes <@ private.api_scope_keys() and cardinality(scopes) > 0
  ),
  constraint developer_apps_status_known check (
    status in ('active', 'suspended')
  ),
  constraint developer_apps_rate_limit_positive check (rate_limit_per_minute > 0)
);

create index developer_apps_owner_idx
  on private.developer_apps (owner_user_id, created_at);

revoke all on table private.developer_apps from public, anon, authenticated;

create trigger developer_apps_set_updated_at
before update on private.developer_apps
for each row execute function private.set_updated_at();

create table private.developer_audit (
  id bigint generated always as identity primary key,
  actor_id uuid,
  action text not null,
  client_id uuid,
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint developer_audit_payload_object check (jsonb_typeof(payload) = 'object')
);

create index developer_audit_client_idx
  on private.developer_audit (client_id, id desc);

revoke all on table private.developer_audit from public, anon, authenticated;

create table private.api_usage (
  client_id uuid not null,
  day date not null,
  user_id uuid not null,
  requests bigint not null default 0,
  denied bigint not null default 0,
  primary key (client_id, day, user_id)
);

revoke all on table private.api_usage from public, anon, authenticated;

create table private.api_rate_buckets (
  client_id uuid not null,
  user_id uuid not null,
  bucket timestamptz not null,
  requests integer not null default 0,
  primary key (client_id, user_id, bucket)
);

revoke all on table private.api_rate_buckets from public, anon, authenticated;

create table private.api_rate_buckets_app (
  client_id uuid not null,
  bucket timestamptz not null,
  requests integer not null default 0,
  primary key (client_id, bucket)
);

revoke all on table private.api_rate_buckets_app from public, anon, authenticated;

create index conversations_updated_id_idx
  on public.conversations (updated_at desc, id desc);

create index messages_conversation_edited_idx
  on public.messages (conversation_id, edited_at desc)
  where edited_at is not null;

create index messages_conversation_deleted_idx
  on public.messages (conversation_id, deleted_at desc)
  where deleted_at is not null;

create or replace function private.prune_api_rate_buckets()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from private.api_rate_buckets
  where bucket < now() - interval '1 day';
  delete from private.api_rate_buckets_app
  where bucket < now() - interval '1 day';
$$;

revoke all on function private.prune_api_rate_buckets() from public;

create or replace function private.prune_oauth_authorizations()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from auth.oauth_authorizations
  where created_at < now() - interval '1 day';
$$;

revoke all on function private.prune_oauth_authorizations() from public;

do $schedule$
begin
  if exists (
    select 1 from pg_available_extensions where name = 'pg_cron'
  ) then
    create extension if not exists pg_cron;
    if not exists (
      select 1 from cron.job where jobname = 'pocketpass-prune-api-buckets'
    ) then
      perform cron.schedule(
        'pocketpass-prune-api-buckets',
        '23 * * * *',
        'select private.prune_api_rate_buckets();'
      );
    end if;
    if not exists (
      select 1 from cron.job where jobname = 'pocketpass-prune-oauth-authorizations'
    ) then
      perform cron.schedule(
        'pocketpass-prune-oauth-authorizations',
        '41 3 * * *',
        'select private.prune_oauth_authorizations();'
      );
    end if;
  end if;
end;
$schedule$;

create or replace function public.pocketpass_access_token_hook(event jsonb)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if coalesce(event -> 'claims' ->> 'client_id', '') = '' then
    return event;
  end if;
  return jsonb_build_object(
    'claims',
    (event -> 'claims') || jsonb_build_object(
      'role', 'api_client',
      'email', '',
      'phone', '',
      'user_metadata', '{}'::jsonb,
      'app_metadata', '{}'::jsonb
    )
  );
end;
$$;

revoke all on function public.pocketpass_access_token_hook(jsonb) from public, anon, authenticated, service_role;
grant execute on function public.pocketpass_access_token_hook(jsonb) to supabase_auth_admin;
grant usage on schema public to supabase_auth_admin;

create or replace function private.api_http_status(p_state text)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case p_state
    when 'PT400' then 400
    when 'PT401' then 401
    when 'PT403' then 403
    when 'PT404' then 404
    when 'PT409' then 409
    when 'PT429' then 429
    when '22023' then 400
    when '22004' then 400
    when '42501' then 403
    else 500
  end;
$$;

revoke all on function private.api_http_status(text) from public;

create or replace function private.api_error(p_state text, p_message text, p_hint text)
returns jsonb
language plpgsql
set search_path = ''
as $$
declare
  v_status integer := private.api_http_status(p_state);
begin
  perform set_config('response.status', v_status::text, true);
  return jsonb_build_object(
    'code', 'PT' || v_status::text,
    'message', p_message,
    'hint', p_hint
  );
end;
$$;

revoke all on function private.api_error(text, text, text) from public;

create or replace function private.api_translate(p_state text, p_message text)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_message text := coalesce(p_message, '');
begin
  if p_state = '42501' then
    if v_message = 'Active conversation membership is required' then
      return jsonb_build_object('code', 'PT403', 'message', v_message, 'hint', 'NOT_A_MEMBER');
    elsif v_message in ('Messaging is not allowed', 'Conversation is not allowed') then
      return jsonb_build_object('code', 'PT403', 'message', v_message, 'hint', 'BLOCKED');
    elsif v_message = 'A friendship is required' then
      return jsonb_build_object('code', 'PT403', 'message', v_message, 'hint', 'NOT_FRIENDS');
    elsif v_message like 'Only the sender can %' then
      return jsonb_build_object('code', 'PT403', 'message', v_message, 'hint', 'NOT_SENDER');
    elsif v_message = 'Authentication required' then
      return jsonb_build_object('code', 'PT401', 'message', v_message, 'hint', 'API_TOKEN_REQUIRED');
    end if;
  elsif p_state in ('22023', '22004') then
    if v_message like 'Client operation id was already used%' then
      return jsonb_build_object('code', 'PT409', 'message', v_message, 'hint', 'DUPLICATE_OPERATION_ID');
    elsif v_message = 'Message has been deleted' then
      return jsonb_build_object('code', 'PT409', 'message', v_message, 'hint', 'MESSAGE_DELETED');
    elsif v_message like 'Message body must contain%' then
      return jsonb_build_object('code', 'PT400', 'message', v_message, 'hint', 'BODY_LENGTH');
    end if;
    return jsonb_build_object('code', 'PT400', 'message', v_message, 'hint', 'INVALID_FIELD');
  elsif p_state = 'P0002' then
    if v_message = 'Profile not found' then
      return jsonb_build_object('code', 'PT404', 'message', v_message, 'hint', 'PROFILE_NOT_FOUND');
    end if;
  elsif p_state = 'PT409' and v_message = 'respond_to_friend_request_before_delete' then
    return jsonb_build_object(
      'code', 'PT409',
      'message', 'Respond to the friend request before deleting its notification',
      'hint', 'FRIEND_REQUEST_PENDING'
    );
  end if;
  return jsonb_build_object('code', 'PT500', 'message', 'Internal error', 'hint', 'INTERNAL');
end;
$$;

revoke all on function private.api_translate(text, text) from public;

create or replace function private.api_failure(p_state text, p_message text, p_hint text)
returns jsonb
language plpgsql
set search_path = ''
as $$
declare
  v_translated jsonb;
begin
  if p_state like 'PT%' and coalesce(p_hint, '') <> '' then
    return private.api_error(p_state, p_message, p_hint);
  end if;
  v_translated := private.api_translate(p_state, p_message);
  return private.api_error(
    v_translated ->> 'code',
    v_translated ->> 'message',
    v_translated ->> 'hint'
  );
end;
$$;

revoke all on function private.api_failure(text, text, text) from public;

create or replace function private.api_meter(p_client_id uuid, p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_bucket timestamptz := date_trunc('minute', now());
  v_user_requests integer;
  v_app_requests integer;
begin
  insert into private.api_rate_buckets (client_id, user_id, bucket, requests)
  values (p_client_id, p_user_id, v_bucket, 1)
  on conflict (client_id, user_id, bucket)
  do update set requests = private.api_rate_buckets.requests + 1
  returning requests into v_user_requests;

  insert into private.api_rate_buckets_app (client_id, bucket, requests)
  values (p_client_id, v_bucket, 1)
  on conflict (client_id, bucket)
  do update set requests = private.api_rate_buckets_app.requests + 1
  returning requests into v_app_requests;

  insert into private.api_usage (client_id, day, user_id, requests)
  values (p_client_id, (now() at time zone 'utc')::date, p_user_id, 1)
  on conflict (client_id, day, user_id)
  do update set requests = private.api_usage.requests + 1;

  return jsonb_build_object(
    'bucket', v_bucket,
    'user_requests', v_user_requests,
    'app_requests', v_app_requests
  );
end;
$$;

revoke all on function private.api_meter(uuid, uuid) from public;

create or replace function private.api_deny(
  p_client_id uuid,
  p_user_id uuid,
  p_state text,
  p_message text,
  p_hint text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into private.api_usage (client_id, day, user_id, requests, denied)
  values (p_client_id, (now() at time zone 'utc')::date, p_user_id, 0, 1)
  on conflict (client_id, day, user_id)
  do update set denied = private.api_usage.denied + 1;
  return private.api_error(p_state, p_message, p_hint);
end;
$$;

revoke all on function private.api_deny(uuid, uuid, text, text, text) from public;

create or replace function private.api_set_rate_headers(
  p_remaining integer,
  p_reset_seconds integer,
  p_retry_after integer
)
returns void
language plpgsql
set search_path = ''
as $$
begin
  perform set_config(
    'response.headers',
    (
      jsonb_build_array(
        jsonb_build_object('RateLimit-Remaining', p_remaining::text),
        jsonb_build_object('RateLimit-Reset', p_reset_seconds::text)
      )
      || case
        when p_retry_after is null then '[]'::jsonb
        else jsonb_build_array(jsonb_build_object('Retry-After', p_retry_after::text))
      end
    )::text,
    true
  );
end;
$$;

revoke all on function private.api_set_rate_headers(integer, integer, integer) from public;

create or replace function private.api_guard(p_scope text, p_allow_revoked boolean default false)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_client_id uuid := private.api_try_uuid(auth.jwt() ->> 'client_id');
  v_app private.developer_apps;
  v_meter jsonb;
  v_bucket timestamptz;
  v_user_requests integer;
  v_app_requests integer;
  v_per_minute integer := private.api_rate_limit_per_minute();
  v_app_limit integer;
  v_remaining integer;
  v_reset_seconds integer;
begin
  if coalesce(auth.role(), '') <> 'api_client'
    or v_user_id is null
    or v_client_id is null
  then
    return private.api_error(
      'PT401',
      'A connected app access token is required',
      'API_TOKEN_REQUIRED'
    );
  end if;

  select app.*
  into v_app
  from private.developer_apps as app
  where app.client_id = v_client_id;
  v_app_limit := coalesce(v_app.rate_limit_per_minute, v_per_minute);

  v_meter := private.api_meter(v_client_id, v_user_id);
  v_bucket := (v_meter ->> 'bucket')::timestamptz;
  v_user_requests := (v_meter ->> 'user_requests')::integer;
  v_app_requests := (v_meter ->> 'app_requests')::integer;
  v_reset_seconds := greatest(
    1,
    ceil(extract(epoch from (v_bucket + interval '1 minute' - now())))::integer
  );
  v_remaining := greatest(
    0,
    least(v_per_minute - v_user_requests, v_app_limit - v_app_requests)
  );

  if v_user_requests > v_per_minute or v_app_requests > v_app_limit then
    perform private.api_set_rate_headers(v_remaining, v_reset_seconds, v_reset_seconds);
    return private.api_deny(
      v_client_id,
      v_user_id,
      'PT429',
      format('Rate limit exceeded, retry after %ss', v_reset_seconds),
      'API_RATE_LIMITED'
    );
  end if;

  perform private.api_set_rate_headers(v_remaining, v_reset_seconds, null);

  if v_app.client_id is null then
    return private.api_deny(v_client_id, v_user_id, 'PT404', 'App not found', 'APP_NOT_FOUND');
  end if;

  if v_app.status <> 'active' then
    return private.api_deny(
      v_client_id,
      v_user_id,
      'PT403',
      'This app has been suspended',
      'APP_SUSPENDED'
    );
  end if;

  if p_scope is not null and not (p_scope = any (v_app.scopes)) then
    return private.api_deny(
      v_client_id,
      v_user_id,
      'PT403',
      format('The %s scope is required', p_scope),
      'SCOPE_REQUIRED'
    );
  end if;

  if not p_allow_revoked and not exists (
    select 1
    from auth.oauth_consents as consent
    where consent.user_id = v_user_id
      and consent.client_id = v_client_id
      and consent.revoked_at is null
      and consent.granted_at >= v_app.scopes_changed_at
  ) then
    return private.api_deny(
      v_client_id,
      v_user_id,
      'PT401',
      'The user has disconnected this app',
      'CONSENT_REVOKED'
    );
  end if;

  return jsonb_build_object('user_id', v_user_id, 'client_id', v_client_id);
end;
$$;

revoke all on function private.api_guard(text, boolean) from public;

create or replace function private.api_has_scope(p_scope text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    coalesce(auth.role(), '') = 'api_client'
    and auth.uid() is not null
    and private.api_try_uuid(auth.jwt() ->> 'client_id') is not null
    and exists (
      select 1
      from private.developer_apps as app
      join auth.oauth_consents as consent
        on consent.client_id = app.client_id
        and consent.user_id = auth.uid()
        and consent.revoked_at is null
        and consent.granted_at >= app.scopes_changed_at
      where app.client_id = private.api_try_uuid(auth.jwt() ->> 'client_id')
        and app.status = 'active'
        and (p_scope is null or p_scope = any (app.scopes))
    );
$$;

revoke all on function private.api_has_scope(text) from public;

create or replace function private.api_reject_unknown(p_payload jsonb, p_allowed text[])
returns void
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_key text;
begin
  if p_payload is null then
    return;
  end if;
  if jsonb_typeof(p_payload) <> 'object' then
    raise sqlstate 'PT400' using
      message = 'Request body must be a JSON object',
      hint = 'INVALID_FIELD';
  end if;
  select keys.key
  into v_key
  from jsonb_object_keys(p_payload) as keys(key)
  where not (keys.key = any (coalesce(p_allowed, '{}'::text[])))
  order by keys.key
  limit 1;
  if found then
    raise sqlstate 'PT400' using
      message = format('Unknown field: %s', v_key),
      hint = 'UNKNOWN_FIELD';
  end if;
end;
$$;

revoke all on function private.api_reject_unknown(jsonb, text[]) from public;

create or replace function private.api_arg_uuid(p_payload jsonb, p_key text, p_required boolean)
returns uuid
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_value jsonb := p_payload -> p_key;
  v_uuid uuid;
begin
  if v_value is null or jsonb_typeof(v_value) = 'null' then
    if p_required then
      raise sqlstate 'PT400' using
        message = format('%s is required', p_key),
        hint = 'MISSING_FIELD';
    end if;
    return null;
  end if;
  if jsonb_typeof(v_value) = 'string' then
    v_uuid := private.api_try_uuid(v_value #>> '{}');
  end if;
  if v_uuid is null then
    raise sqlstate 'PT400' using
      message = format('%s must be a UUID', p_key),
      hint = 'INVALID_FIELD';
  end if;
  return v_uuid;
end;
$$;

revoke all on function private.api_arg_uuid(jsonb, text, boolean) from public;

create or replace function private.api_arg_text(
  p_payload jsonb,
  p_key text,
  p_required boolean,
  p_max_length integer
)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_value jsonb := p_payload -> p_key;
  v_text text;
begin
  if v_value is null or jsonb_typeof(v_value) = 'null' then
    if p_required then
      raise sqlstate 'PT400' using
        message = format('%s is required', p_key),
        hint = 'MISSING_FIELD';
    end if;
    return null;
  end if;
  if jsonb_typeof(v_value) <> 'string' then
    raise sqlstate 'PT400' using
      message = format('%s must be a string', p_key),
      hint = 'INVALID_FIELD';
  end if;
  v_text := v_value #>> '{}';
  if p_max_length is not null and char_length(v_text) > p_max_length then
    raise sqlstate 'PT400' using
      message = format('%s must be at most %s characters', p_key, p_max_length),
      hint = 'INVALID_FIELD';
  end if;
  return v_text;
end;
$$;

revoke all on function private.api_arg_text(jsonb, text, boolean, integer) from public;

create or replace function private.api_arg_int(
  p_payload jsonb,
  p_key text,
  p_default integer,
  p_min integer,
  p_max integer
)
returns integer
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_value jsonb := p_payload -> p_key;
  v_number numeric;
begin
  if v_value is null or jsonb_typeof(v_value) = 'null' then
    return p_default;
  end if;
  if jsonb_typeof(v_value) <> 'number' then
    raise sqlstate 'PT400' using
      message = format('%s must be an integer', p_key),
      hint = 'INVALID_FIELD';
  end if;
  v_number := (v_value #>> '{}')::numeric;
  if v_number <> trunc(v_number) then
    raise sqlstate 'PT400' using
      message = format('%s must be an integer', p_key),
      hint = 'INVALID_FIELD';
  end if;
  return least(greatest(v_number, p_min), p_max)::integer;
end;
$$;

revoke all on function private.api_arg_int(jsonb, text, integer, integer, integer) from public;

create or replace function private.api_arg_timestamptz(p_payload jsonb, p_key text)
returns timestamptz
language plpgsql
stable
set search_path = ''
as $$
declare
  v_value jsonb := p_payload -> p_key;
  v_text text;
begin
  if v_value is null or jsonb_typeof(v_value) = 'null' then
    return null;
  end if;
  if jsonb_typeof(v_value) <> 'string' then
    raise sqlstate 'PT400' using
      message = format('%s must be an ISO 8601 timestamp', p_key),
      hint = 'INVALID_FIELD';
  end if;
  v_text := v_value #>> '{}';
  begin
    return v_text::timestamptz;
  exception
    when others then
      raise sqlstate 'PT400' using
        message = format('%s must be an ISO 8601 timestamp', p_key),
        hint = 'INVALID_FIELD';
  end;
end;
$$;

revoke all on function private.api_arg_timestamptz(jsonb, text) from public;

create or replace function private.api_arg_uuid_array(
  p_payload jsonb,
  p_key text,
  p_max_items integer
)
returns uuid[]
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_value jsonb := p_payload -> p_key;
  v_ids uuid[];
begin
  if v_value is null or jsonb_typeof(v_value) = 'null' then
    raise sqlstate 'PT400' using
      message = format('%s is required', p_key),
      hint = 'MISSING_FIELD';
  end if;
  if jsonb_typeof(v_value) <> 'array' then
    raise sqlstate 'PT400' using
      message = format('%s must be an array of UUIDs', p_key),
      hint = 'INVALID_FIELD';
  end if;
  if jsonb_array_length(v_value) > p_max_items then
    raise sqlstate 'PT400' using
      message = format('%s must contain at most %s items', p_key, p_max_items),
      hint = 'INVALID_FIELD';
  end if;
  select coalesce(
    array_agg(private.api_try_uuid(element.value #>> '{}') order by element.ordinality),
    '{}'::uuid[]
  )
  into v_ids
  from jsonb_array_elements(v_value) with ordinality as element(value, ordinality)
  where jsonb_typeof(element.value) = 'string';
  if cardinality(v_ids) <> jsonb_array_length(v_value)
    or array_position(v_ids, null::uuid) is not null
  then
    raise sqlstate 'PT400' using
      message = format('%s must be an array of UUIDs', p_key),
      hint = 'INVALID_FIELD';
  end if;
  return v_ids;
end;
$$;

revoke all on function private.api_arg_uuid_array(jsonb, text, integer) from public;

create or replace function private.api_encode_cursor(p_ts timestamptz, p_id uuid)
returns text
language sql
stable
set search_path = ''
as $$
  select replace(
    encode(
      convert_to((to_json(p_ts) #>> '{}') || '|' || p_id::text, 'UTF8'),
      'base64'
    ),
    E'\n',
    ''
  );
$$;

revoke all on function private.api_encode_cursor(timestamptz, uuid) from public;

create or replace function private.api_decode_cursor(p_cursor text)
returns table (ts timestamptz, id uuid)
language plpgsql
stable
set search_path = ''
as $$
declare
  v_parts text[];
begin
  begin
    v_parts := string_to_array(convert_from(decode(p_cursor, 'base64'), 'UTF8'), '|');
    if cardinality(v_parts) <> 2 then
      raise exception 'malformed cursor';
    end if;
    ts := v_parts[1]::timestamptz;
    id := v_parts[2]::uuid;
  exception
    when others then
      raise sqlstate 'PT400' using
        message = 'cursor is invalid',
        hint = 'INVALID_CURSOR';
  end;
  return next;
end;
$$;

revoke all on function private.api_decode_cursor(text) from public;

create or replace function private.api_profile_json(p_profile public.profiles)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'user_id', p_profile.user_id,
    'username', p_profile.username::text,
    'display_name', p_profile.display_name,
    'bio', p_profile.bio,
    'avatar_path', p_profile.avatar_path,
    'age', p_profile.age,
    'country_code', p_profile.country_code,
    'created_at', p_profile.created_at,
    'updated_at', p_profile.updated_at,
    'setup_complete', p_profile.username::text <> replace(p_profile.user_id::text, '-', '')
  );
$$;

revoke all on function private.api_profile_json(public.profiles) from public;

create or replace function private.api_message_json(p_message public.messages)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'id', p_message.id,
    'conversation_id', p_message.conversation_id,
    'sender_id', p_message.sender_id,
    'body', p_message.body,
    'reply_to_id', p_message.reply_to_id,
    'attachment', case
      when p_message.metadata -> 'attachment' ->> 'path' is not null then jsonb_build_object(
        'path', p_message.metadata -> 'attachment' ->> 'path',
        'mime_type', p_message.metadata -> 'attachment' ->> 'mime_type'
      )
      else null
    end,
    'created_at', p_message.created_at,
    'edited_at', p_message.edited_at,
    'deleted_at', p_message.deleted_at
  );
$$;

revoke all on function private.api_message_json(public.messages) from public;

create or replace function private.api_notification_json(p_notification public.notifications)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', p_notification.id,
    'kind', p_notification.kind,
    'title', p_notification.title,
    'body', p_notification.body,
    'event_count', p_notification.event_count,
    'actor', (
      select jsonb_build_object(
        'user_id', profile.user_id,
        'display_name', profile.display_name,
        'avatar_path', profile.avatar_path
      )
      from public.profiles as profile
      where profile.user_id = p_notification.actor_id
    ),
    'friend_request_id', p_notification.friend_request_id,
    'friend_request_status', p_notification.friend_request_status,
    'conversation_id', p_notification.conversation_id,
    'created_at', p_notification.created_at,
    'updated_at', p_notification.updated_at,
    'read_at', p_notification.read_at
  );
$$;

revoke all on function private.api_notification_json(public.notifications) from public;

create or replace function private.api_conversation_json(p_conversation_id uuid, p_viewer_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', conversation.id,
    'kind', conversation.kind::text,
    'title', conversation.title,
    'members', coalesce(
      (
        select jsonb_agg(
          jsonb_build_object(
            'user_id', member.user_id,
            'display_name', profile.display_name,
            'avatar_path', profile.avatar_path
          )
          order by member.joined_at, member.user_id
        )
        from public.conversation_members as member
        join public.profiles as profile on profile.user_id = member.user_id
        where member.conversation_id = conversation.id
          and member.left_at is null
      ),
      '[]'::jsonb
    ),
    'last_message', (
      select private.api_message_json(message)
      from public.messages as message
      where message.conversation_id = conversation.id
        and message.deleted_at is null
      order by message.created_at desc, message.id desc
      limit 1
    ),
    'unread_count', (
      select count(*)
      from public.messages as message
      where message.conversation_id = conversation.id
        and message.sender_id <> p_viewer_id
        and message.deleted_at is null
        and message.created_at > coalesce(viewer.last_read_at, '-infinity'::timestamptz)
    ),
    'last_read_at', viewer.last_read_at,
    'created_at', conversation.created_at,
    'updated_at', conversation.updated_at
  )
  from public.conversations as conversation
  left join public.conversation_members as viewer
    on viewer.conversation_id = conversation.id
    and viewer.user_id = p_viewer_id
  where conversation.id = p_conversation_id;
$$;

revoke all on function private.api_conversation_json(uuid, uuid) from public;

create or replace function private.api_can_see_profile(p_viewer_id uuid, p_profile_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    p_viewer_id is not null
    and p_profile_id is not null
    and private.can_view_profile(p_viewer_id, p_profile_id)
    and (
      p_viewer_id = p_profile_id
      or private.are_friends(p_viewer_id, p_profile_id)
      or exists (
        select 1
        from public.conversation_members as viewer
        join public.conversation_members as other
          on other.conversation_id = viewer.conversation_id
        where viewer.user_id = p_viewer_id
          and viewer.left_at is null
          and other.user_id = p_profile_id
          and other.left_at is null
      )
    );
$$;

revoke all on function private.api_can_see_profile(uuid, uuid) from public;

create or replace function public.api_v1_session_get(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard(null::text);
  v_user_id uuid;
  v_client_id uuid;
  v_app private.developer_apps;
  v_bucket timestamptz := date_trunc('minute', now());
  v_per_minute integer := private.api_rate_limit_per_minute();
  v_user_requests integer;
  v_app_requests integer;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  v_client_id := (v_guard ->> 'client_id')::uuid;
  begin
    perform private.api_reject_unknown($1, '{}'::text[]);

    select app.*
    into v_app
    from private.developer_apps as app
    where app.client_id = v_client_id;

    select bucket.requests
    into v_user_requests
    from private.api_rate_buckets as bucket
    where bucket.client_id = v_client_id
      and bucket.user_id = v_user_id
      and bucket.bucket = v_bucket;

    select bucket.requests
    into v_app_requests
    from private.api_rate_buckets_app as bucket
    where bucket.client_id = v_client_id
      and bucket.bucket = v_bucket;

    return jsonb_build_object(
      'user_id', v_user_id,
      'client_id', v_client_id,
      'app', jsonb_build_object('name', v_app.name),
      'scopes', to_jsonb(v_app.scopes),
      'rate_limit', jsonb_build_object(
        'per_minute', v_per_minute,
        'remaining', greatest(
          0,
          least(
            v_per_minute - coalesce(v_user_requests, 0),
            v_app.rate_limit_per_minute - coalesce(v_app_requests, 0)
          )
        ),
        'reset_at', v_bucket + interval '1 minute'
      ),
      'unread_total', (
        select count(*)
        from public.notifications as notification
        where notification.recipient_id = v_user_id
          and notification.read_at is null
          and notification.deleted_at is null
      )
    );
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_session_revoke(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard(null::text, true);
  v_user_id uuid;
  v_client_id uuid;
  v_revoked integer;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  v_client_id := (v_guard ->> 'client_id')::uuid;
  begin
    perform private.api_reject_unknown($1, '{}'::text[]);

    update auth.oauth_consents as consent
    set revoked_at = now()
    where consent.user_id = v_user_id
      and consent.client_id = v_client_id
      and consent.revoked_at is null;
    get diagnostics v_revoked = row_count;

    delete from auth.sessions as session
    where session.user_id = v_user_id
      and session.oauth_client_id = v_client_id;

    return jsonb_build_object('revoked', v_revoked > 0);
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_me_get(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('profile:read');
  v_user_id uuid;
  v_profile public.profiles;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, '{}'::text[]);

    select profile.*
    into v_profile
    from public.profiles as profile
    where profile.user_id = v_user_id;
    if not found then
      raise sqlstate 'PT404' using
        message = 'Profile not found',
        hint = 'PROFILE_NOT_FOUND';
    end if;

    return jsonb_build_object('profile', private.api_profile_json(v_profile));
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_profiles_get(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('profile:read');
  v_user_id uuid;
  v_profile_id uuid;
  v_profile public.profiles;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['user_id']);
    v_profile_id := private.api_arg_uuid($1, 'user_id', true);

    select profile.*
    into v_profile
    from public.profiles as profile
    where profile.user_id = v_profile_id
      and private.api_can_see_profile(v_user_id, v_profile_id);
    if not found then
      raise sqlstate 'PT404' using
        message = 'Profile not found',
        hint = 'PROFILE_NOT_FOUND';
    end if;

    return jsonb_build_object('profile', private.api_profile_json(v_profile));
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_profiles_get_many(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('profile:read');
  v_user_id uuid;
  v_ids uuid[];
  v_items jsonb;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['user_ids']);
    v_ids := private.api_arg_uuid_array($1, 'user_ids', 100);

    select coalesce(
      jsonb_agg(
        private.api_profile_json(profile)
        order by array_position(v_ids, profile.user_id), profile.user_id
      ),
      '[]'::jsonb
    )
    into v_items
    from public.profiles as profile
    where profile.user_id = any (v_ids)
      and private.api_can_see_profile(v_user_id, profile.user_id);

    return jsonb_build_object('items', v_items);
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_friends_list(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:read');
  v_user_id uuid;
  v_items jsonb;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, '{}'::text[]);

    select coalesce(
      jsonb_agg(
        private.api_profile_json(profile)
          || jsonb_build_object('friends_since', friendship.created_at)
        order by lower(profile.display_name), profile.user_id
      ),
      '[]'::jsonb
    )
    into v_items
    from public.friendships as friendship
    join public.profiles as profile
      on profile.user_id = case
        when friendship.user_low = v_user_id then friendship.user_high
        else friendship.user_low
      end
    where v_user_id in (friendship.user_low, friendship.user_high);

    return jsonb_build_object('items', v_items);
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_conversations_list(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('messages:read');
  v_user_id uuid;
  v_limit integer;
  v_cursor text;
  v_cursor_ts timestamptz;
  v_cursor_id uuid;
  v_updated_after timestamptz;
  v_items jsonb;
  v_has_more boolean;
  v_last_ts timestamptz;
  v_last_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['limit', 'cursor', 'updated_after']);
    v_limit := private.api_arg_int($1, 'limit', 50, 1, 100);
    v_cursor := private.api_arg_text($1, 'cursor', false, null);
    v_updated_after := private.api_arg_timestamptz($1, 'updated_after');
    if v_cursor is not null then
      select decoded.ts, decoded.id
      into v_cursor_ts, v_cursor_id
      from private.api_decode_cursor(v_cursor) as decoded;
    end if;

    select
      coalesce(
        jsonb_agg(private.api_conversation_json(page.id, v_user_id) order by page.rn)
          filter (where page.rn <= v_limit),
        '[]'::jsonb
      ),
      count(*) > v_limit,
      (array_agg(page.updated_at order by page.rn))[v_limit],
      (array_agg(page.id order by page.rn))[v_limit]
    into v_items, v_has_more, v_last_ts, v_last_id
    from (
      select
        conversation.id,
        conversation.updated_at,
        row_number() over (order by conversation.updated_at desc, conversation.id desc) as rn
      from public.conversations as conversation
      join public.conversation_members as member
        on member.conversation_id = conversation.id
        and member.user_id = v_user_id
        and member.left_at is null
      where (
          v_cursor_ts is null
          or (conversation.updated_at, conversation.id) < (v_cursor_ts, v_cursor_id)
        )
        and (v_updated_after is null or conversation.updated_at > v_updated_after)
      order by conversation.updated_at desc, conversation.id desc
      limit v_limit + 1
    ) as page;

    return jsonb_build_object(
      'items', v_items,
      'next_cursor', case
        when v_has_more then private.api_encode_cursor(v_last_ts, v_last_id)
        else null
      end
    );
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_conversations_get(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('messages:read');
  v_user_id uuid;
  v_conversation_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['conversation_id']);
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);

    if not private.is_active_conversation_member(v_conversation_id, v_user_id) then
      raise sqlstate 'PT404' using
        message = 'Conversation not found',
        hint = 'CONVERSATION_NOT_FOUND';
    end if;

    return jsonb_build_object(
      'conversation', private.api_conversation_json(v_conversation_id, v_user_id)
    );
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_conversations_open(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('messages:write');
  v_user_id uuid;
  v_other_id uuid;
  v_operation_id uuid;
  v_conversation_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['user_id', 'client_operation_id']);
    v_other_id := private.api_arg_uuid($1, 'user_id', true);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);

    v_conversation_id := public.get_or_create_direct_conversation(v_other_id, v_operation_id);

    return jsonb_build_object(
      'conversation', private.api_conversation_json(v_conversation_id, v_user_id)
    );
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_conversations_mark_read(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('messages:write');
  v_user_id uuid;
  v_conversation_id uuid;
  v_last_read_at timestamptz;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['conversation_id']);
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);

    v_last_read_at := public.mark_conversation_read(v_conversation_id, now());

    return jsonb_build_object('last_read_at', v_last_read_at);
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_messages_list(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('messages:read');
  v_user_id uuid;
  v_conversation_id uuid;
  v_limit integer;
  v_cursor text;
  v_cursor_ts timestamptz;
  v_cursor_id uuid;
  v_changed_since timestamptz;
  v_items jsonb;
  v_has_more boolean;
  v_last_ts timestamptz;
  v_last_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown(
      $1,
      array['conversation_id', 'limit', 'cursor', 'changed_since']
    );
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);
    v_limit := private.api_arg_int($1, 'limit', 50, 1, 100);
    v_cursor := private.api_arg_text($1, 'cursor', false, null);
    v_changed_since := private.api_arg_timestamptz($1, 'changed_since');
    if v_cursor is not null then
      select decoded.ts, decoded.id
      into v_cursor_ts, v_cursor_id
      from private.api_decode_cursor(v_cursor) as decoded;
    end if;

    if not private.is_active_conversation_member(v_conversation_id, v_user_id) then
      raise sqlstate 'PT404' using
        message = 'Conversation not found',
        hint = 'CONVERSATION_NOT_FOUND';
    end if;

    select
      coalesce(
        jsonb_agg(private.api_message_json(page.row_data) order by page.rn)
          filter (where page.rn <= v_limit),
        '[]'::jsonb
      ),
      count(*) > v_limit,
      (array_agg(page.created_at order by page.rn))[v_limit],
      (array_agg(page.id order by page.rn))[v_limit]
    into v_items, v_has_more, v_last_ts, v_last_id
    from (
      select
        message as row_data,
        message.id,
        message.created_at,
        row_number() over (order by message.created_at desc, message.id desc) as rn
      from public.messages as message
      where message.conversation_id = v_conversation_id
        and (
          v_cursor_ts is null
          or (message.created_at, message.id) < (v_cursor_ts, v_cursor_id)
        )
        and (
          v_changed_since is null
          or message.created_at > v_changed_since
          or message.edited_at > v_changed_since
          or message.deleted_at > v_changed_since
        )
      order by message.created_at desc, message.id desc
      limit v_limit + 1
    ) as page;

    return jsonb_build_object(
      'items', v_items,
      'next_cursor', case
        when v_has_more then private.api_encode_cursor(v_last_ts, v_last_id)
        else null
      end
    );
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_messages_send(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('messages:write');
  v_user_id uuid;
  v_conversation_id uuid;
  v_body text;
  v_operation_id uuid;
  v_reply_to_id uuid;
  v_message_id uuid;
  v_sent public.messages;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown(
      $1,
      array['conversation_id', 'body', 'client_operation_id', 'reply_to_id']
    );
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);
    v_body := private.api_arg_text($1, 'body', true, null);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);
    v_reply_to_id := private.api_arg_uuid($1, 'reply_to_id', false);

    if char_length(btrim(v_body)) not between 1 and 4000 then
      raise sqlstate 'PT400' using
        message = 'Message body must contain 1 to 4000 characters',
        hint = 'BODY_LENGTH';
    end if;

    v_message_id := substr(
      encode(
        extensions.digest(v_user_id::text || ':' || v_operation_id::text, 'sha256'),
        'hex'
      ),
      1,
      32
    )::uuid;

    v_sent := public.send_message(
      v_message_id,
      v_conversation_id,
      v_operation_id,
      v_body,
      v_reply_to_id,
      '{}'::jsonb
    );

    return jsonb_build_object('message', private.api_message_json(v_sent));
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_messages_edit(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('messages:write');
  v_user_id uuid;
  v_message_id uuid;
  v_body text;
  v_edited public.messages;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['message_id', 'body']);
    v_message_id := private.api_arg_uuid($1, 'message_id', true);
    v_body := private.api_arg_text($1, 'body', true, null);

    if char_length(btrim(v_body)) not between 1 and 4000 then
      raise sqlstate 'PT400' using
        message = 'Message body must contain 1 to 4000 characters',
        hint = 'BODY_LENGTH';
    end if;

    if not exists (
      select 1
      from public.messages as message
      where message.id = v_message_id
        and private.is_active_conversation_member(message.conversation_id, v_user_id)
    ) then
      raise sqlstate 'PT404' using
        message = 'Message not found',
        hint = 'MESSAGE_NOT_FOUND';
    end if;

    v_edited := public.edit_message(v_message_id, v_body);

    return jsonb_build_object('message', private.api_message_json(v_edited));
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_messages_delete(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('messages:write');
  v_user_id uuid;
  v_message_id uuid;
  v_deleted public.messages;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['message_id']);
    v_message_id := private.api_arg_uuid($1, 'message_id', true);

    if not exists (
      select 1
      from public.messages as message
      where message.id = v_message_id
        and private.is_active_conversation_member(message.conversation_id, v_user_id)
    ) then
      raise sqlstate 'PT404' using
        message = 'Message not found',
        hint = 'MESSAGE_NOT_FOUND';
    end if;

    v_deleted := public.delete_message(v_message_id);

    return jsonb_build_object('message', private.api_message_json(v_deleted));
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_notifications_list(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('notifications:read');
  v_user_id uuid;
  v_limit integer;
  v_cursor text;
  v_cursor_ts timestamptz;
  v_cursor_id uuid;
  v_items jsonb;
  v_has_more boolean;
  v_last_ts timestamptz;
  v_last_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['limit', 'cursor']);
    v_limit := private.api_arg_int($1, 'limit', 50, 1, 100);
    v_cursor := private.api_arg_text($1, 'cursor', false, null);
    if v_cursor is not null then
      select decoded.ts, decoded.id
      into v_cursor_ts, v_cursor_id
      from private.api_decode_cursor(v_cursor) as decoded;
    end if;

    select
      coalesce(
        jsonb_agg(private.api_notification_json(page.row_data) order by page.rn)
          filter (where page.rn <= v_limit),
        '[]'::jsonb
      ),
      count(*) > v_limit,
      (array_agg(page.updated_at order by page.rn))[v_limit],
      (array_agg(page.id order by page.rn))[v_limit]
    into v_items, v_has_more, v_last_ts, v_last_id
    from (
      select
        notification as row_data,
        notification.id,
        notification.updated_at,
        row_number() over (order by notification.updated_at desc, notification.id desc) as rn
      from public.notifications as notification
      where notification.recipient_id = v_user_id
        and notification.deleted_at is null
        and (
          v_cursor_ts is null
          or (notification.updated_at, notification.id) < (v_cursor_ts, v_cursor_id)
        )
      order by notification.updated_at desc, notification.id desc
      limit v_limit + 1
    ) as page;

    return jsonb_build_object(
      'items', v_items,
      'next_cursor', case
        when v_has_more then private.api_encode_cursor(v_last_ts, v_last_id)
        else null
      end
    );
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_notifications_mark_read(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('notifications:read');
  v_user_id uuid;
  v_notification_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['notification_id']);
    v_notification_id := private.api_arg_uuid($1, 'notification_id', true);

    if not exists (
      select 1
      from public.notifications as notification
      where notification.id = v_notification_id
        and notification.recipient_id = v_user_id
        and notification.deleted_at is null
    ) then
      raise sqlstate 'PT404' using
        message = 'Notification not found',
        hint = 'NOTIFICATION_NOT_FOUND';
    end if;

    perform public.mark_notification_read(v_notification_id, now());

    return jsonb_build_object('ok', true);
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_notifications_delete(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('notifications:read');
  v_user_id uuid;
  v_notification_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['notification_id']);
    v_notification_id := private.api_arg_uuid($1, 'notification_id', true);

    if not exists (
      select 1
      from public.notifications as notification
      where notification.id = v_notification_id
        and notification.recipient_id = v_user_id
    ) then
      raise sqlstate 'PT404' using
        message = 'Notification not found',
        hint = 'NOTIFICATION_NOT_FOUND';
    end if;

    perform public.delete_notification(v_notification_id, now());

    return jsonb_build_object('ok', true);
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

revoke all on function public.api_v1_session_get(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_session_revoke(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_me_get(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_profiles_get(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_profiles_get_many(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_friends_list(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_conversations_list(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_conversations_get(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_conversations_open(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_conversations_mark_read(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_messages_list(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_messages_send(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_messages_edit(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_messages_delete(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_notifications_list(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_notifications_mark_read(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_notifications_delete(jsonb) from public, anon, authenticated, service_role;

grant execute on function public.api_v1_session_get(jsonb) to api_client;
grant execute on function public.api_v1_session_revoke(jsonb) to api_client;
grant execute on function public.api_v1_me_get(jsonb) to api_client;
grant execute on function public.api_v1_profiles_get(jsonb) to api_client;
grant execute on function public.api_v1_profiles_get_many(jsonb) to api_client;
grant execute on function public.api_v1_friends_list(jsonb) to api_client;
grant execute on function public.api_v1_conversations_list(jsonb) to api_client;
grant execute on function public.api_v1_conversations_get(jsonb) to api_client;
grant execute on function public.api_v1_conversations_open(jsonb) to api_client;
grant execute on function public.api_v1_conversations_mark_read(jsonb) to api_client;
grant execute on function public.api_v1_messages_list(jsonb) to api_client;
grant execute on function public.api_v1_messages_send(jsonb) to api_client;
grant execute on function public.api_v1_messages_edit(jsonb) to api_client;
grant execute on function public.api_v1_messages_delete(jsonb) to api_client;
grant execute on function public.api_v1_notifications_list(jsonb) to api_client;
grant execute on function public.api_v1_notifications_mark_read(jsonb) to api_client;
grant execute on function public.api_v1_notifications_delete(jsonb) to api_client;

create or replace function private.api_can_read_object(p_bucket_id text, p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select case p_bucket_id
    when 'avatars' then
      private.api_has_scope('profile:read')
      and private.can_view_profile(auth.uid(), private.avatar_owner_id(p_name))
    when 'message-media' then
      private.api_has_scope('messages:read')
      and private.is_active_conversation_member(
        private.message_media_conversation_id(p_name),
        auth.uid()
      )
    else false
  end;
$$;

revoke all on function private.api_can_read_object(text, text) from public;
grant execute on function private.api_can_read_object(text, text) to api_client;

create policy pocketpass_api_avatars_read
on storage.objects
for select
to api_client
using (
  bucket_id = 'avatars'
  and private.api_can_read_object(bucket_id, name)
);

create policy pocketpass_api_message_media_read
on storage.objects
for select
to api_client
using (
  bucket_id = 'message-media'
  and private.api_can_read_object(bucket_id, name)
);

create or replace function private.developer_secret_hash(p_secret text)
returns text
language sql
immutable
set search_path = ''
as $$
  select translate(
    rtrim(encode(extensions.digest(p_secret, 'sha256'), 'base64'), '='),
    '+/',
    '-_'
  );
$$;

revoke all on function private.developer_secret_hash(text) from public;

create or replace function private.developer_validate_name(p_name text)
returns text
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_name text := btrim(coalesce(p_name, ''));
begin
  if char_length(v_name) not between 1 and 64 then
    raise exception 'Name must be 1 to 64 characters' using errcode = '22023';
  end if;
  if v_name ~ '[[:cntrl:]]' then
    raise exception 'Name must not contain control characters' using errcode = '22023';
  end if;
  if lower(v_name) like '%pocketpass%' and not private.is_admin() then
    raise sqlstate 'PT403' using
      message = 'App names containing PocketPass are reserved',
      hint = 'APP_NAME_RESERVED';
  end if;
  return v_name;
end;
$$;

revoke all on function private.developer_validate_name(text) from public;

create or replace function private.developer_validate_description(p_description text)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_description text := btrim(coalesce(p_description, ''));
begin
  if char_length(v_description) > 280 then
    raise exception 'Description must be at most 280 characters' using errcode = '22023';
  end if;
  return v_description;
end;
$$;

revoke all on function private.developer_validate_description(text) from public;

create or replace function private.developer_validate_url(p_value text, p_label text)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_value text := btrim(coalesce(p_value, ''));
begin
  if v_value = '' then
    return '';
  end if;
  if char_length(v_value) > 2048
    or v_value !~ '^https://[^[:space:][:cntrl:]]+$'
  then
    raise exception '% must be an https URL', p_label using errcode = '22023';
  end if;
  return v_value;
end;
$$;

revoke all on function private.developer_validate_url(text, text) from public;

create or replace function private.developer_validate_redirect_uris(p_uris text[])
returns text[]
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_uris text[] := '{}'::text[];
  v_uri text;
  v_scheme text;
  v_authority text;
  v_host text;
begin
  if p_uris is null or cardinality(p_uris) = 0 then
    raise exception 'At least one redirect URI is required' using errcode = '22023';
  end if;
  foreach v_uri in array p_uris loop
    v_uri := btrim(coalesce(v_uri, ''));
    if v_uri = '' or v_uri = any (v_uris) then
      continue;
    end if;
    if char_length(v_uri) > 2048 then
      raise exception 'Redirect URI is too long: %', left(v_uri, 80) using errcode = '22023';
    end if;
    if v_uri ~ '[[:space:][:cntrl:]#,]' then
      raise exception 'Redirect URI must not contain whitespace, control characters, # or ,: %', v_uri
        using errcode = '22023';
    end if;
    v_scheme := lower(substring(v_uri from '^([A-Za-z][A-Za-z0-9+.-]*):'));
    if v_scheme is null then
      raise exception 'Redirect URI must be absolute with a scheme: %', v_uri using errcode = '22023';
    end if;
    if v_scheme in ('javascript', 'data', 'file', 'vbscript', 'about', 'blob') then
      raise exception 'Redirect URI scheme is not allowed: %', v_uri using errcode = '22023';
    end if;
    if v_scheme in ('http', 'https') then
      v_authority := substring(v_uri from '^[A-Za-z][A-Za-z0-9+.-]*://([^/?]*)');
      if v_authority is null or v_authority = '' or v_authority like '%@%' then
        raise exception 'Redirect URI must include a host: %', v_uri using errcode = '22023';
      end if;
      v_host := lower(coalesce(
        substring(v_authority from '^(\[[^]]*\])'),
        substring(v_authority from '^([^:]+)')
      ));
      if v_host is null or v_host = '' then
        raise exception 'Redirect URI must include a host: %', v_uri using errcode = '22023';
      end if;
      if v_scheme = 'http' and v_host not in ('localhost', '127.0.0.1', '[::1]') then
        raise exception 'http redirect URIs are allowed only for localhost, 127.0.0.1 and [::1]: %', v_uri
          using errcode = '22023';
      end if;
    elsif char_length(v_uri) <= char_length(v_scheme) + 1 then
      raise exception 'Redirect URI must include a path or host: %', v_uri using errcode = '22023';
    end if;
    v_uris := v_uris || v_uri;
  end loop;
  if cardinality(v_uris) = 0 then
    raise exception 'At least one redirect URI is required' using errcode = '22023';
  end if;
  if cardinality(v_uris) > 10 then
    raise exception 'At most 10 redirect URIs are allowed' using errcode = '22023';
  end if;
  return v_uris;
end;
$$;

revoke all on function private.developer_validate_redirect_uris(text[]) from public;

create or replace function private.developer_validate_scopes(p_scopes text[])
returns text[]
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_scopes text[];
begin
  if p_scopes is null or cardinality(p_scopes) = 0 then
    raise exception 'At least one scope is required' using errcode = '22023';
  end if;
  if not (p_scopes <@ private.api_scope_keys()) then
    raise exception 'Unknown scope' using errcode = '22023';
  end if;
  v_scopes := private.api_normalize_scopes(p_scopes);
  if cardinality(v_scopes) = 0 then
    raise exception 'At least one scope is required' using errcode = '22023';
  end if;
  return v_scopes;
end;
$$;

revoke all on function private.developer_validate_scopes(text[]) from public;

create or replace function private.developer_require_owned_app(p_client_id uuid)
returns private.developer_apps
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_app private.developer_apps;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  select app.*
  into v_app
  from private.developer_apps as app
  where app.client_id = p_client_id
    and app.owner_user_id = v_actor_id;
  if not found then
    raise sqlstate 'PT404' using
      message = 'App not found',
      hint = 'APP_NOT_FOUND';
  end if;
  return v_app;
end;
$$;

revoke all on function private.developer_require_owned_app(uuid) from public;

create or replace function private.developer_app_json(p_app private.developer_apps)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'client_id', p_app.client_id,
    'name', p_app.name,
    'description', p_app.description,
    'website', p_app.website,
    'logo_url', p_app.logo_url,
    'client_type', p_app.client_type,
    'redirect_uris', coalesce(
      (
        select to_jsonb(string_to_array(client.redirect_uris, ','))
        from auth.oauth_clients as client
        where client.id = p_app.client_id
      ),
      '[]'::jsonb
    ),
    'scopes', to_jsonb(p_app.scopes),
    'status', p_app.status,
    'created_at', p_app.created_at,
    'updated_at', p_app.updated_at,
    'connected_users', (
      select count(*)
      from auth.oauth_consents as consent
      where consent.client_id = p_app.client_id
        and consent.revoked_at is null
        and consent.granted_at >= p_app.scopes_changed_at
    ),
    'requests_30d', (
      select coalesce(sum(stat.requests), 0)::bigint
      from private.api_usage as stat
      where stat.client_id = p_app.client_id
        and stat.day >= (now() at time zone 'utc')::date - 29
    ),
    'denied_30d', (
      select coalesce(sum(stat.denied), 0)::bigint
      from private.api_usage as stat
      where stat.client_id = p_app.client_id
        and stat.day >= (now() at time zone 'utc')::date - 29
    )
  );
$$;

revoke all on function private.developer_app_json(private.developer_apps) from public;

create or replace function private.developer_apps_after_delete()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_consents integer;
  v_sessions integer;
begin
  update auth.oauth_clients as client
  set
    deleted_at = coalesce(client.deleted_at, now()),
    updated_at = now()
  where client.id = old.client_id;

  update auth.oauth_consents as consent
  set revoked_at = now()
  where consent.client_id = old.client_id
    and consent.revoked_at is null;
  get diagnostics v_consents = row_count;

  delete from auth.sessions as session
  where session.oauth_client_id = old.client_id;
  get diagnostics v_sessions = row_count;

  insert into private.developer_audit (actor_id, action, client_id, payload)
  values (
    auth.uid(),
    'app_delete',
    old.client_id,
    jsonb_build_object(
      'name', old.name,
      'owner_user_id', old.owner_user_id,
      'consents_revoked', v_consents,
      'sessions_deleted', v_sessions
    )
  );

  return old;
end;
$$;

revoke all on function private.developer_apps_after_delete() from public;

create trigger developer_apps_after_delete
after delete on private.developer_apps
for each row execute function private.developer_apps_after_delete();

create or replace function private.developer_apps_before_update_scopes()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_consents integer;
  v_sessions integer;
begin
  new.scopes := private.api_normalize_scopes(new.scopes);
  if new.scopes <@ old.scopes then
    return new;
  end if;

  new.scopes_changed_at := now();

  update auth.oauth_consents as consent
  set revoked_at = now()
  where consent.client_id = new.client_id
    and consent.revoked_at is null;
  get diagnostics v_consents = row_count;

  delete from auth.sessions as session
  where session.oauth_client_id = new.client_id;
  get diagnostics v_sessions = row_count;

  insert into private.developer_audit (actor_id, action, client_id, payload)
  values (
    auth.uid(),
    'app_scopes_expand',
    new.client_id,
    jsonb_build_object(
      'old', to_jsonb(old.scopes),
      'new', to_jsonb(new.scopes),
      'consents_revoked', v_consents,
      'sessions_deleted', v_sessions
    )
  );

  return new;
end;
$$;

revoke all on function private.developer_apps_before_update_scopes() from public;

create trigger developer_apps_before_update_scopes
before update of scopes on private.developer_apps
for each row execute function private.developer_apps_before_update_scopes();

create or replace function public.developer_whoami()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  return jsonb_build_object(
    'user_id', v_actor_id,
    'display_name', (
      select profile.display_name
      from public.profiles as profile
      where profile.user_id = v_actor_id
    ),
    'app_count', (
      select count(*)
      from private.developer_apps as app
      where app.owner_user_id = v_actor_id
    ),
    'max_apps', private.developer_max_apps(),
    'scopes', private.api_scope_objects(private.api_scope_keys())
  );
end;
$$;

revoke all on function public.developer_whoami() from public, anon;
grant execute on function public.developer_whoami() to authenticated;

create or replace function public.developer_list_apps()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  return jsonb_build_object(
    'items', coalesce(
      (
        select jsonb_agg(private.developer_app_json(app) order by app.created_at, app.client_id)
        from private.developer_apps as app
        where app.owner_user_id = v_actor_id
      ),
      '[]'::jsonb
    )
  );
end;
$$;

revoke all on function public.developer_list_apps() from public, anon;
grant execute on function public.developer_list_apps() to authenticated;

create or replace function public.developer_create_app(
  p_name text,
  p_description text,
  p_website text,
  p_logo_url text,
  p_redirect_uris text[],
  p_scopes text[],
  p_client_type text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_name text;
  v_description text;
  v_website text;
  v_logo_url text;
  v_redirect_uris text[];
  v_scopes text[];
  v_client_type text := lower(btrim(coalesce(p_client_type, '')));
  v_client_id uuid := gen_random_uuid();
  v_secret text;
  v_secret_hash text;
  v_app private.developer_apps;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  perform 1
  from public.profiles as profile
  where profile.user_id = v_actor_id
  for update;
  if not found then
    raise exception 'Profile not found' using errcode = 'P0002';
  end if;

  v_name := private.developer_validate_name(p_name);
  v_description := private.developer_validate_description(p_description);
  v_website := private.developer_validate_url(p_website, 'Website');
  v_logo_url := private.developer_validate_url(p_logo_url, 'Logo URL');
  v_redirect_uris := private.developer_validate_redirect_uris(p_redirect_uris);
  v_scopes := private.developer_validate_scopes(p_scopes);
  if v_client_type not in ('public', 'confidential') then
    raise exception 'Client type must be public or confidential' using errcode = '22023';
  end if;

  if (
    select count(*)
    from private.developer_apps as app
    where app.owner_user_id = v_actor_id
  ) >= private.developer_max_apps() then
    raise sqlstate 'PT403' using
      message = format('You can register at most %s apps', private.developer_max_apps()),
      hint = 'APP_LIMIT';
  end if;

  if v_client_type = 'confidential' then
    v_secret := 'pp_secret_' || encode(extensions.gen_random_bytes(32), 'hex');
    v_secret_hash := private.developer_secret_hash(v_secret);
  end if;

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
    v_client_id,
    v_secret_hash,
    'manual'::auth.oauth_registration_type,
    array_to_string(v_redirect_uris, ','),
    'authorization_code,refresh_token',
    v_name,
    nullif(v_website, ''),
    nullif(v_logo_url, ''),
    v_client_type::auth.oauth_client_type,
    case when v_client_type = 'confidential' then 'client_secret_basic' else 'none' end,
    now(),
    now()
  );

  insert into private.developer_apps (
    client_id,
    owner_user_id,
    name,
    description,
    website,
    logo_url,
    client_type,
    scopes
  )
  values (
    v_client_id,
    v_actor_id,
    v_name,
    v_description,
    v_website,
    v_logo_url,
    v_client_type,
    v_scopes
  )
  returning * into v_app;

  insert into private.developer_audit (actor_id, action, client_id, payload)
  values (
    v_actor_id,
    'app_create',
    v_client_id,
    jsonb_build_object(
      'name', v_name,
      'client_type', v_client_type,
      'scopes', to_jsonb(v_scopes),
      'redirect_uris', to_jsonb(v_redirect_uris)
    )
  );

  return jsonb_build_object(
    'app', private.developer_app_json(v_app),
    'client_secret', v_secret
  );
end;
$$;

revoke all on function public.developer_create_app(text, text, text, text, text[], text[], text) from public, anon;
grant execute on function public.developer_create_app(text, text, text, text, text[], text[], text) to authenticated;

create or replace function public.developer_update_app(
  p_client_id uuid,
  p_name text,
  p_description text,
  p_website text,
  p_logo_url text,
  p_redirect_uris text[],
  p_scopes text[]
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_app private.developer_apps := private.developer_require_owned_app(p_client_id);
  v_updated private.developer_apps;
  v_name text;
  v_description text;
  v_website text;
  v_logo_url text;
  v_redirect_uris text[];
  v_scopes text[];
  v_before integer;
  v_after integer;
begin
  if v_app.status <> 'active' then
    raise sqlstate 'PT403' using
      message = 'This app has been suspended',
      hint = 'APP_SUSPENDED';
  end if;

  v_name := private.developer_validate_name(p_name);
  v_description := private.developer_validate_description(p_description);
  v_website := private.developer_validate_url(p_website, 'Website');
  v_logo_url := private.developer_validate_url(p_logo_url, 'Logo URL');
  v_redirect_uris := private.developer_validate_redirect_uris(p_redirect_uris);
  v_scopes := private.developer_validate_scopes(p_scopes);

  select count(*)
  into v_before
  from auth.oauth_consents as consent
  where consent.client_id = v_app.client_id
    and consent.revoked_at is null
    and consent.granted_at >= v_app.scopes_changed_at;

  update private.developer_apps as app
  set
    name = v_name,
    description = v_description,
    website = v_website,
    logo_url = v_logo_url,
    scopes = v_scopes
  where app.client_id = v_app.client_id
  returning app.* into v_updated;

  update auth.oauth_clients as client
  set
    redirect_uris = array_to_string(v_redirect_uris, ','),
    client_name = v_name,
    client_uri = nullif(v_website, ''),
    logo_uri = nullif(v_logo_url, ''),
    updated_at = now()
  where client.id = v_app.client_id;

  select count(*)
  into v_after
  from auth.oauth_consents as consent
  where consent.client_id = v_updated.client_id
    and consent.revoked_at is null
    and consent.granted_at >= v_updated.scopes_changed_at;

  insert into private.developer_audit (actor_id, action, client_id, payload)
  values (
    v_actor_id,
    'app_update',
    v_app.client_id,
    jsonb_build_object(
      'before', jsonb_build_object(
        'name', v_app.name,
        'description', v_app.description,
        'website', v_app.website,
        'logo_url', v_app.logo_url,
        'scopes', to_jsonb(v_app.scopes)
      ),
      'after', jsonb_build_object(
        'name', v_updated.name,
        'description', v_updated.description,
        'website', v_updated.website,
        'logo_url', v_updated.logo_url,
        'scopes', to_jsonb(v_updated.scopes),
        'redirect_uris', to_jsonb(v_redirect_uris)
      ),
      'consents_revoked', v_before - v_after
    )
  );

  return jsonb_build_object(
    'app', private.developer_app_json(v_updated),
    'consents_revoked', v_before - v_after
  );
end;
$$;

revoke all on function public.developer_update_app(uuid, text, text, text, text, text[], text[]) from public, anon;
grant execute on function public.developer_update_app(uuid, text, text, text, text, text[], text[]) to authenticated;

create or replace function public.developer_rotate_secret(p_client_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_app private.developer_apps := private.developer_require_owned_app(p_client_id);
  v_secret text;
begin
  if v_app.status <> 'active' then
    raise sqlstate 'PT403' using
      message = 'This app has been suspended',
      hint = 'APP_SUSPENDED';
  end if;
  if v_app.client_type <> 'confidential' then
    raise exception 'Public clients have no secret' using errcode = '22023';
  end if;

  v_secret := 'pp_secret_' || encode(extensions.gen_random_bytes(32), 'hex');

  update auth.oauth_clients as client
  set
    client_secret_hash = private.developer_secret_hash(v_secret),
    updated_at = now()
  where client.id = v_app.client_id;
  if not found then
    raise exception 'OAuth client not found' using errcode = 'P0002';
  end if;

  insert into private.developer_audit (actor_id, action, client_id, payload)
  values (v_actor_id, 'app_rotate_secret', v_app.client_id, '{}'::jsonb);

  return jsonb_build_object('client_secret', v_secret);
end;
$$;

revoke all on function public.developer_rotate_secret(uuid) from public, anon;
grant execute on function public.developer_rotate_secret(uuid) to authenticated;

create or replace function public.developer_delete_app(p_client_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_app private.developer_apps := private.developer_require_owned_app(p_client_id);
begin
  delete from private.developer_apps as app
  where app.client_id = v_app.client_id;
  return jsonb_build_object('deleted', true);
end;
$$;

revoke all on function public.developer_delete_app(uuid) from public, anon;
grant execute on function public.developer_delete_app(uuid) to authenticated;

create or replace function public.developer_app_usage(
  p_client_id uuid,
  p_days integer default 30
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_app private.developer_apps := private.developer_require_owned_app(p_client_id);
  v_days integer := least(greatest(coalesce(p_days, 30), 1), 90);
  v_today date := (now() at time zone 'utc')::date;
begin
  return jsonb_build_object(
    'items', coalesce(
      (
        select jsonb_agg(
          jsonb_build_object(
            'day', series.day,
            'requests', coalesce(stat.requests, 0),
            'denied', coalesce(stat.denied, 0),
            'users', coalesce(stat.users, 0)
          )
          order by series.day
        )
        from (
          select (v_today - offsets.days_ago)::date as day
          from generate_series(v_days - 1, 0, -1) as offsets(days_ago)
        ) as series
        left join (
          select
            usage_row.day,
            sum(usage_row.requests)::bigint as requests,
            sum(usage_row.denied)::bigint as denied,
            count(distinct usage_row.user_id) filter (
              where usage_row.requests > 0 or usage_row.denied > 0
            ) as users
          from private.api_usage as usage_row
          where usage_row.client_id = v_app.client_id
            and usage_row.day >= v_today - (v_days - 1)
          group by usage_row.day
        ) as stat on stat.day = series.day
      ),
      '[]'::jsonb
    )
  );
end;
$$;

revoke all on function public.developer_app_usage(uuid, integer) from public, anon;
grant execute on function public.developer_app_usage(uuid, integer) to authenticated;

create or replace function public.api_app_info(p_client_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_app private.developer_apps;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  select app.*
  into v_app
  from private.developer_apps as app
  where app.client_id = p_client_id;
  if not found then
    raise sqlstate 'PT404' using
      message = 'App not found',
      hint = 'APP_NOT_FOUND';
  end if;
  if v_app.status <> 'active' then
    raise sqlstate 'PT403' using
      message = 'This app has been suspended',
      hint = 'APP_SUSPENDED';
  end if;

  return jsonb_build_object(
    'client_id', v_app.client_id,
    'name', v_app.name,
    'description', v_app.description,
    'website', v_app.website,
    'logo_url', v_app.logo_url,
    'owner_display_name', (
      select profile.display_name
      from public.profiles as profile
      where profile.user_id = v_app.owner_user_id
    ),
    'scopes', private.api_scope_objects(v_app.scopes),
    'oidc_scope_descriptions', private.api_oidc_scope_descriptions(),
    'status', v_app.status
  );
end;
$$;

revoke all on function public.api_app_info(uuid) from public, anon;
grant execute on function public.api_app_info(uuid) to authenticated;

create or replace function public.api_connected_apps()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  return jsonb_build_object(
    'items', coalesce(
      (
        select jsonb_agg(
          jsonb_build_object(
            'client_id', consent.client_id,
            'name', coalesce(app.name, client.client_name, 'Unknown app'),
            'website', coalesce(app.website, client.client_uri, ''),
            'logo_url', coalesce(app.logo_url, client.logo_uri, ''),
            'scopes', coalesce(to_jsonb(app.scopes), '[]'::jsonb),
            'granted_at', consent.granted_at
          )
          order by consent.granted_at desc, consent.client_id
        )
        from auth.oauth_consents as consent
        left join private.developer_apps as app on app.client_id = consent.client_id
        left join auth.oauth_clients as client on client.id = consent.client_id
        where consent.user_id = v_actor_id
          and consent.revoked_at is null
      ),
      '[]'::jsonb
    )
  );
end;
$$;

revoke all on function public.api_connected_apps() from public, anon;
grant execute on function public.api_connected_apps() to authenticated;

create or replace function public.api_revoke_app(p_client_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_revoked integer;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_client_id is null then
    raise exception 'p_client_id is required' using errcode = '22004';
  end if;

  update auth.oauth_consents as consent
  set revoked_at = now()
  where consent.user_id = v_actor_id
    and consent.client_id = p_client_id
    and consent.revoked_at is null;
  get diagnostics v_revoked = row_count;

  delete from auth.sessions as session
  where session.user_id = v_actor_id
    and session.oauth_client_id = p_client_id;

  return jsonb_build_object('revoked', v_revoked > 0);
end;
$$;

revoke all on function public.api_revoke_app(uuid) from public, anon;
grant execute on function public.api_revoke_app(uuid) to authenticated;

alter table private.admin_users
  drop constraint admin_users_permissions_known,
  add constraint admin_users_permissions_known check (
    permissions <@ array['users', 'audit', 'legacy', 'tokens', 'achievements', 'admins', 'apps']::text[]
  );

create or replace function private.admin_permission_keys()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['users', 'audit', 'legacy', 'tokens', 'achievements', 'admins', 'apps']::text[];
$$;

revoke all on function private.admin_permission_keys() from public;

comment on function private.admin_permission_keys() is 'users: list accounts with emails and open their detail; audit: read the audit log; legacy/tokens/achievements: the matching mutations; admins: manage other admins; apps: review and suspend developer apps.';

create or replace function public.admin_list_developer_apps(
  p_query text default '',
  p_limit integer default 50,
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_query text := nullif(btrim(coalesce(p_query, '')), '');
  v_pattern text;
  v_uuid uuid;
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_result jsonb;
begin
  perform private.require_permission('apps');

  if v_query is not null then
    v_pattern := '%' || replace(replace(replace(v_query, '\', '\\'), '%', '\%'), '_', '\_') || '%';
    v_uuid := private.api_try_uuid(v_query);
  end if;

  with matched as (
    select
      app.client_id,
      app.name,
      app.owner_user_id,
      profile.display_name as owner_display_name,
      app.status,
      app.client_type,
      app.scopes,
      app.scopes_changed_at,
      app.created_at
    from private.developer_apps as app
    left join public.profiles as profile on profile.user_id = app.owner_user_id
    left join auth.users as account on account.id = app.owner_user_id
    where v_query is null
      or app.client_id = v_uuid
      or app.name ilike v_pattern
      or profile.display_name ilike v_pattern
      or profile.username::text ilike v_pattern
      or account.email::text ilike v_pattern
  ),
  page as (
    select *
    from matched
    order by matched.created_at desc, matched.client_id
    limit v_limit
    offset v_offset
  )
  select jsonb_build_object(
    'items', coalesce(
      (
        select jsonb_agg(
          jsonb_build_object(
            'client_id', page.client_id,
            'name', page.name,
            'owner_user_id', page.owner_user_id,
            'owner_display_name', page.owner_display_name,
            'status', page.status,
            'client_type', page.client_type,
            'scopes', to_jsonb(page.scopes),
            'connected_users', (
              select count(*)
              from auth.oauth_consents as consent
              where consent.client_id = page.client_id
                and consent.revoked_at is null
                and consent.granted_at >= page.scopes_changed_at
            ),
            'requests_30d', (
              select coalesce(sum(stat.requests), 0)::bigint
              from private.api_usage as stat
              where stat.client_id = page.client_id
                and stat.day >= (now() at time zone 'utc')::date - 29
            ),
            'created_at', page.created_at
          )
          order by page.created_at desc, page.client_id
        )
        from page
      ),
      '[]'::jsonb
    ),
    'total_count', (select count(*) from matched)
  )
  into v_result;

  return v_result;
end;
$$;

revoke all on function public.admin_list_developer_apps(text, integer, integer) from public, anon;
grant execute on function public.admin_list_developer_apps(text, integer, integer) to authenticated;

create or replace function public.admin_set_developer_app_status(
  p_client_id uuid,
  p_status text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('apps');
  v_app private.developer_apps;
begin
  if p_status is null or p_status not in ('active', 'suspended') then
    raise exception 'p_status must be active or suspended' using errcode = '22023';
  end if;

  select app.*
  into v_app
  from private.developer_apps as app
  where app.client_id = p_client_id
  for update;
  if not found then
    raise sqlstate 'PT404' using
      message = 'App not found',
      hint = 'APP_NOT_FOUND';
  end if;

  update private.developer_apps as app
  set status = p_status
  where app.client_id = p_client_id;

  if p_status = 'suspended' then
    update auth.oauth_clients as client
    set
      deleted_at = coalesce(client.deleted_at, now()),
      updated_at = now()
    where client.id = p_client_id;

    delete from auth.sessions as session
    where session.oauth_client_id = p_client_id;
  else
    update auth.oauth_clients as client
    set
      deleted_at = null,
      updated_at = now()
    where client.id = p_client_id;
  end if;

  perform private.record_admin_action(
    v_admin_id,
    'set_developer_app_status',
    v_app.owner_user_id,
    jsonb_build_object(
      'client_id', p_client_id,
      'name', v_app.name,
      'status', p_status
    )
  );

  insert into private.developer_audit (actor_id, action, client_id, payload)
  values (v_admin_id, 'app_status', p_client_id, jsonb_build_object('status', p_status));

  return jsonb_build_object('client_id', p_client_id, 'status', p_status);
end;
$$;

revoke all on function public.admin_set_developer_app_status(uuid, text) from public, anon;
grant execute on function public.admin_set_developer_app_status(uuid, text) to authenticated;

commit;
