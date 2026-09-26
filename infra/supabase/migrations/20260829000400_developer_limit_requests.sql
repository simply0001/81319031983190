begin;

alter table private.developer_apps
  add column user_rate_limit_per_minute integer not null default 120,
  add column rate_limit_per_second integer not null default 100,
  add column realtime_connection_limit integer,
  add constraint developer_apps_user_rate_limit_range check (
    user_rate_limit_per_minute between 1 and 1000000
  ),
  add constraint developer_apps_rate_limit_per_second_range check (
    rate_limit_per_second between 1 and 1000000
  ),
  add constraint developer_apps_realtime_limit_range check (
    realtime_connection_limit is null or realtime_connection_limit between 1 and 1000000
  );

create table private.api_rate_buckets_app_second (
  client_id uuid not null,
  bucket timestamptz not null,
  requests integer not null default 0,
  primary key (client_id, bucket)
);

revoke all on table private.api_rate_buckets_app_second from public, anon, authenticated;

create table private.developer_limit_requests (
  id uuid primary key default gen_random_uuid(),
  client_id uuid not null references private.developer_apps (client_id) on delete cascade,
  requester_user_id uuid references public.profiles (user_id) on delete set null,
  status text not null default 'pending',
  current_limits jsonb not null,
  requested_limits jsonb not null,
  granted_limits jsonb,
  reason text not null,
  resolution_note text not null default '',
  resolved_by uuid references public.profiles (user_id) on delete set null,
  created_at timestamptz not null default now(),
  resolved_at timestamptz,
  constraint developer_limit_requests_status_known check (
    status in ('pending', 'approved', 'denied')
  ),
  constraint developer_limit_requests_reason_length check (
    char_length(btrim(reason)) between 20 and 1000
  ),
  constraint developer_limit_requests_note_length check (char_length(resolution_note) <= 1000),
  constraint developer_limit_requests_limits_objects check (
    jsonb_typeof(current_limits) = 'object'
    and jsonb_typeof(requested_limits) = 'object'
    and (granted_limits is null or jsonb_typeof(granted_limits) = 'object')
  ),
  constraint developer_limit_requests_resolution check (
    (status = 'pending' and resolved_at is null and granted_limits is null)
    or (status = 'approved' and resolved_at is not null and granted_limits is not null)
    or (status = 'denied' and resolved_at is not null and granted_limits is null)
  )
);

create unique index developer_limit_requests_one_pending_idx
  on private.developer_limit_requests (client_id)
  where status = 'pending';

create index developer_limit_requests_status_idx
  on private.developer_limit_requests (status, created_at desc);

revoke all on table private.developer_limit_requests from public, anon, authenticated;

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
  delete from private.api_rate_buckets_app_second
  where bucket < now() - interval '1 hour';
$$;

create or replace function private.api_meter(p_client_id uuid, p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_bucket timestamptz := date_trunc('minute', now());
  v_second timestamptz := date_trunc('second', clock_timestamp());
  v_user_requests integer;
  v_app_requests integer;
  v_second_requests integer;
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

  insert into private.api_rate_buckets_app_second (client_id, bucket, requests)
  values (p_client_id, v_second, 1)
  on conflict (client_id, bucket)
  do update set requests = private.api_rate_buckets_app_second.requests + 1
  returning requests into v_second_requests;

  insert into private.api_usage (client_id, day, user_id, requests)
  values (p_client_id, (now() at time zone 'utc')::date, p_user_id, 1)
  on conflict (client_id, day, user_id)
  do update set requests = private.api_usage.requests + 1;

  return jsonb_build_object(
    'bucket', v_bucket,
    'user_requests', v_user_requests,
    'app_requests', v_app_requests,
    'second_requests', v_second_requests
  );
end;
$$;

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
  v_second_requests integer;
  v_user_limit integer;
  v_app_limit integer;
  v_second_limit integer;
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
  v_user_limit := coalesce(v_app.user_rate_limit_per_minute, private.api_rate_limit_per_minute());
  v_app_limit := coalesce(v_app.rate_limit_per_minute, 600);
  v_second_limit := coalesce(v_app.rate_limit_per_second, 100);

  v_meter := private.api_meter(v_client_id, v_user_id);
  v_bucket := (v_meter ->> 'bucket')::timestamptz;
  v_user_requests := (v_meter ->> 'user_requests')::integer;
  v_app_requests := (v_meter ->> 'app_requests')::integer;
  v_second_requests := (v_meter ->> 'second_requests')::integer;
  v_reset_seconds := greatest(
    1,
    ceil(extract(epoch from (v_bucket + interval '1 minute' - now())))::integer
  );
  v_remaining := greatest(
    0,
    least(v_user_limit - v_user_requests, v_app_limit - v_app_requests)
  );

  if v_second_requests > v_second_limit then
    perform private.api_set_rate_headers(v_remaining, v_reset_seconds, 1);
    return private.api_deny(
      v_client_id,
      v_user_id,
      'PT429',
      'Rate limit exceeded, retry after 1s',
      'API_RATE_LIMITED'
    );
  end if;

  if v_user_requests > v_user_limit or v_app_requests > v_app_limit then
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
  v_per_minute integer;
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
    v_per_minute := coalesce(v_app.user_rate_limit_per_minute, private.api_rate_limit_per_minute());

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
        'app_per_minute', v_app.rate_limit_per_minute,
        'per_second', v_app.rate_limit_per_second,
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

create or replace function private.developer_limits_json(p_app private.developer_apps)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'user_per_minute', p_app.user_rate_limit_per_minute,
    'app_per_minute', p_app.rate_limit_per_minute,
    'app_per_second', p_app.rate_limit_per_second,
    'realtime_connections', p_app.realtime_connection_limit
  );
$$;

revoke all on function private.developer_limits_json(private.developer_apps) from public;

create or replace function private.developer_validate_limits(p_limits jsonb)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_key text;
  v_value jsonb;
  v_number numeric;
  v_result jsonb := '{}'::jsonb;
begin
  if p_limits is null or jsonb_typeof(p_limits) <> 'object' then
    raise exception 'Limits must be an object' using errcode = '22023';
  end if;
  select keys.key
  into v_key
  from jsonb_object_keys(p_limits) as keys(key)
  where keys.key not in ('user_per_minute', 'app_per_minute', 'app_per_second', 'realtime_connections')
  order by keys.key
  limit 1;
  if found then
    raise exception 'Unknown limit: %', v_key using errcode = '22023';
  end if;
  foreach v_key in array array['user_per_minute', 'app_per_minute', 'app_per_second', 'realtime_connections'] loop
    v_value := p_limits -> v_key;
    if v_value is null or jsonb_typeof(v_value) = 'null' then
      if v_key = 'realtime_connections' then
        v_result := v_result || jsonb_build_object(v_key, null::integer);
        continue;
      end if;
      raise exception '% is required', v_key using errcode = '22023';
    end if;
    if jsonb_typeof(v_value) <> 'number' then
      raise exception '% must be a whole number between 1 and 1000000', v_key using errcode = '22023';
    end if;
    v_number := (v_value #>> '{}')::numeric;
    if v_number <> trunc(v_number) or v_number < 1 or v_number > 1000000 then
      raise exception '% must be a whole number between 1 and 1000000', v_key using errcode = '22023';
    end if;
    v_result := v_result || jsonb_build_object(v_key, v_number::integer);
  end loop;
  return v_result;
end;
$$;

revoke all on function private.developer_validate_limits(jsonb) from public;

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
    ),
    'limits', private.developer_limits_json(p_app),
    'pending_limit_request', exists (
      select 1
      from private.developer_limit_requests as request
      where request.client_id = p_app.client_id
        and request.status = 'pending'
    )
  );
$$;

create or replace function private.limit_request_json(p_request private.developer_limit_requests)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', p_request.id,
    'client_id', p_request.client_id,
    'app_name', app.name,
    'owner_user_id', app.owner_user_id,
    'owner_display_name', profile.display_name,
    'status', p_request.status,
    'current_limits', p_request.current_limits,
    'requested_limits', p_request.requested_limits,
    'granted_limits', p_request.granted_limits,
    'reason', p_request.reason,
    'resolution_note', p_request.resolution_note,
    'created_at', p_request.created_at,
    'resolved_at', p_request.resolved_at
  )
  from private.developer_apps as app
  left join public.profiles as profile on profile.user_id = app.owner_user_id
  where app.client_id = p_request.client_id;
$$;

revoke all on function private.limit_request_json(private.developer_limit_requests) from public;

create or replace function private.limit_summary(p_limits jsonb)
returns text
language sql
immutable
set search_path = ''
as $$
  select format(
    'user/min %s · app/min %s · app/sec %s · Realtime %s',
    coalesce(p_limits ->> 'user_per_minute', '?'),
    coalesce(p_limits ->> 'app_per_minute', '?'),
    coalesce(p_limits ->> 'app_per_second', '?'),
    coalesce(p_limits ->> 'realtime_connections', 'shared')
  );
$$;

revoke all on function private.limit_summary(jsonb) from public;

create or replace function private.notify_limit_request(
  p_request private.developer_limit_requests,
  p_event text
)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_url text;
  v_app private.developer_apps;
  v_owner text;
  v_admin text;
  v_content text;
  v_request_id bigint;
begin
  select secret.decrypted_secret
  into v_url
  from vault.decrypted_secrets as secret
  where secret.name = 'discord_limit_requests_webhook'
  order by secret.created_at desc
  limit 1;
  if v_url is null
    or v_url !~ '^https://(discord\.com|discordapp\.com|ptb\.discord\.com|canary\.discord\.com)/api/webhooks/'
  then
    return null;
  end if;

  select app.*
  into v_app
  from private.developer_apps as app
  where app.client_id = p_request.client_id;

  select profile.display_name
  into v_owner
  from public.profiles as profile
  where profile.user_id = v_app.owner_user_id;

  select profile.display_name
  into v_admin
  from public.profiles as profile
  where profile.user_id = p_request.resolved_by;

  v_content := case p_event
    when 'created' then format(
      E'**Limit request** for **%s** by %s\nCurrent: %s\nRequested: %s\nReason: %s\nReview: https://admin.pocketpass.xyz/#apps',
      v_app.name,
      coalesce(v_owner, 'unknown'),
      private.limit_summary(p_request.current_limits),
      private.limit_summary(p_request.requested_limits),
      left(p_request.reason, 600)
    )
    when 'approved' then format(
      E'**Approved** limits for **%s** by %s\nGranted: %s%s',
      v_app.name,
      coalesce(v_admin, 'an admin'),
      private.limit_summary(p_request.granted_limits),
      case when p_request.resolution_note = '' then '' else E'\nNote: ' || left(p_request.resolution_note, 400) end
    )
    else format(
      E'**Denied** limit request for **%s** by %s%s',
      v_app.name,
      coalesce(v_admin, 'an admin'),
      case when p_request.resolution_note = '' then '' else E'\nNote: ' || left(p_request.resolution_note, 400) end
    )
  end;

  select net.http_post(
    url => v_url,
    body => jsonb_build_object(
      'content', left(v_content, 1900),
      'allowed_mentions', jsonb_build_object('parse', '[]'::jsonb)
    ),
    headers => '{"Content-Type": "application/json"}'::jsonb,
    timeout_milliseconds => 5000
  )
  into v_request_id;
  return v_request_id;
exception
  when others then
    return null;
end;
$$;

revoke all on function private.notify_limit_request(private.developer_limit_requests, text) from public;

create or replace function public.developer_request_limits(
  p_client_id uuid,
  p_limits jsonb,
  p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_app private.developer_apps := private.developer_require_owned_app(p_client_id);
  v_current jsonb;
  v_requested jsonb;
  v_reason text := btrim(coalesce(p_reason, ''));
  v_key text;
  v_request private.developer_limit_requests;
begin
  if v_app.status <> 'active' then
    raise sqlstate 'PT403' using
      message = 'This app is suspended',
      hint = 'APP_SUSPENDED';
  end if;

  v_current := private.developer_limits_json(v_app);
  v_requested := private.developer_validate_limits(p_limits);
  if jsonb_typeof(v_requested -> 'realtime_connections') = 'null' then
    v_requested := v_requested || jsonb_build_object('realtime_connections', v_current -> 'realtime_connections');
  end if;

  foreach v_key in array array['user_per_minute', 'app_per_minute', 'app_per_second', 'realtime_connections'] loop
    if (v_requested ->> v_key) is not null
      and (v_current ->> v_key) is not null
      and (v_requested ->> v_key)::integer < (v_current ->> v_key)::integer
    then
      raise exception 'Requested limits cannot be lower than the current ones' using errcode = '22023';
    end if;
  end loop;

  if v_requested = v_current then
    raise sqlstate 'PT400' using
      message = 'Ask for at least one limit above the current one',
      hint = 'LIMITS_UNCHANGED';
  end if;

  if char_length(v_reason) not between 20 and 1000 then
    raise exception 'Reason must be 20 to 1000 characters' using errcode = '22023';
  end if;

  if exists (
    select 1
    from private.developer_limit_requests as request
    where request.client_id = p_client_id
      and request.status = 'pending'
  ) then
    raise sqlstate 'PT409' using
      message = 'A request for this app is already waiting for review',
      hint = 'LIMIT_REQUEST_PENDING';
  end if;

  insert into private.developer_limit_requests (
    client_id,
    requester_user_id,
    current_limits,
    requested_limits,
    reason
  )
  values (p_client_id, auth.uid(), v_current, v_requested, v_reason)
  returning * into v_request;

  insert into private.developer_audit (actor_id, action, client_id, payload)
  values (
    auth.uid(),
    'limit_request',
    p_client_id,
    jsonb_build_object('request_id', v_request.id, 'requested', v_requested)
  );

  perform private.notify_limit_request(v_request, 'created');

  return jsonb_build_object('request', private.limit_request_json(v_request));
end;
$$;

revoke all on function public.developer_request_limits(uuid, jsonb, text) from public, anon;
grant execute on function public.developer_request_limits(uuid, jsonb, text) to authenticated;

create or replace function public.developer_list_limit_requests(p_client_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_app private.developer_apps := private.developer_require_owned_app(p_client_id);
begin
  return jsonb_build_object(
    'items', coalesce(
      (
        select jsonb_agg(private.limit_request_json(request) order by request.created_at desc, request.id)
        from (
          select *
          from private.developer_limit_requests as request
          where request.client_id = v_app.client_id
          order by request.created_at desc, request.id
          limit 20
        ) as request
      ),
      '[]'::jsonb
    )
  );
end;
$$;

revoke all on function public.developer_list_limit_requests(uuid) from public, anon;
grant execute on function public.developer_list_limit_requests(uuid) to authenticated;

create or replace function public.admin_list_limit_requests(
  p_status text default 'pending',
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
  v_status text := coalesce(nullif(btrim(p_status), ''), 'pending');
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
begin
  perform private.require_permission('apps');
  if v_status not in ('pending', 'approved', 'denied', 'all') then
    raise exception 'p_status must be pending, approved, denied or all' using errcode = '22023';
  end if;
  return jsonb_build_object(
    'items', coalesce(
      (
        select jsonb_agg(private.limit_request_json(request) order by request.created_at desc, request.id)
        from (
          select *
          from private.developer_limit_requests as request
          where v_status = 'all' or request.status = v_status
          order by request.created_at desc, request.id
          limit v_limit
          offset v_offset
        ) as request
      ),
      '[]'::jsonb
    ),
    'total_count', (
      select count(*)
      from private.developer_limit_requests as request
      where v_status = 'all' or request.status = v_status
    )
  );
end;
$$;

revoke all on function public.admin_list_limit_requests(text, integer, integer) from public, anon;
grant execute on function public.admin_list_limit_requests(text, integer, integer) to authenticated;

create or replace function public.admin_resolve_limit_request(
  p_request_id uuid,
  p_approve boolean,
  p_note text default '',
  p_limits jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('apps');
  v_request private.developer_limit_requests;
  v_app private.developer_apps;
  v_note text := btrim(coalesce(p_note, ''));
  v_granted jsonb;
begin
  if p_request_id is null or p_approve is null then
    raise exception 'request id and decision are required' using errcode = '22004';
  end if;
  if char_length(v_note) > 1000 then
    raise exception 'Note must be at most 1000 characters' using errcode = '22023';
  end if;

  select request.*
  into v_request
  from private.developer_limit_requests as request
  where request.id = p_request_id
  for update;
  if not found then
    raise sqlstate 'PT404' using
      message = 'Limit request not found',
      hint = 'LIMIT_REQUEST_NOT_FOUND';
  end if;
  if v_request.status <> 'pending' then
    raise sqlstate 'PT409' using
      message = 'This request was already resolved',
      hint = 'LIMIT_REQUEST_CLOSED';
  end if;

  select app.*
  into v_app
  from private.developer_apps as app
  where app.client_id = v_request.client_id
  for update;

  if p_approve then
    v_granted := case
      when p_limits is null then v_request.requested_limits
      else private.developer_validate_limits(p_limits)
    end;

    update private.developer_apps as app
    set
      user_rate_limit_per_minute = (v_granted ->> 'user_per_minute')::integer,
      rate_limit_per_minute = (v_granted ->> 'app_per_minute')::integer,
      rate_limit_per_second = (v_granted ->> 'app_per_second')::integer,
      realtime_connection_limit = nullif(v_granted ->> 'realtime_connections', '')::integer
    where app.client_id = v_request.client_id;

    update private.developer_limit_requests as request
    set
      status = 'approved',
      granted_limits = v_granted,
      resolved_at = now(),
      resolved_by = v_admin_id,
      resolution_note = v_note
    where request.id = p_request_id
    returning request.* into v_request;
  else
    update private.developer_limit_requests as request
    set
      status = 'denied',
      resolved_at = now(),
      resolved_by = v_admin_id,
      resolution_note = v_note
    where request.id = p_request_id
    returning request.* into v_request;
  end if;

  perform private.record_admin_action(
    v_admin_id,
    'resolve_limit_request',
    v_app.owner_user_id,
    jsonb_build_object(
      'client_id', v_app.client_id,
      'name', v_app.name,
      'request_id', p_request_id,
      'approved', p_approve,
      'granted', v_granted,
      'note', v_note
    )
  );

  insert into private.developer_audit (actor_id, action, client_id, payload)
  values (
    v_admin_id,
    case when p_approve then 'limit_request_approved' else 'limit_request_denied' end,
    v_app.client_id,
    jsonb_build_object('request_id', p_request_id, 'granted', v_granted, 'note', v_note)
  );

  perform private.notify_limit_request(v_request, v_request.status);

  return jsonb_build_object('request', private.limit_request_json(v_request));
end;
$$;

revoke all on function public.admin_resolve_limit_request(uuid, boolean, text, jsonb) from public, anon;
grant execute on function public.admin_resolve_limit_request(uuid, boolean, text, jsonb) to authenticated;

commit;
