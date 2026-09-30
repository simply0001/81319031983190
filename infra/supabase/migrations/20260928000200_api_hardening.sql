begin;

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
    and not private.account_banned(auth.uid())
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
  if private.account_banned(private.api_try_uuid(coalesce(event ->> 'user_id', event -> 'claims' ->> 'sub'))) then
    return jsonb_build_object(
      'error',
      jsonb_build_object('http_code', 403, 'message', 'This account is banned.')
    );
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

create or replace function public.pocketpass_request_guard()
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_path text := coalesce(current_setting('request.path', true), '');
begin
  if private.account_banned(auth.uid()) and v_path not like '%/rpc/get_my_account_ban' then
    raise sqlstate 'PT403' using message = 'This account is banned.', hint = 'ACCOUNT_BANNED';
  end if;
  if v_path like '/rpc/api\_v1\_%' and coalesce(auth.role(), '') <> 'api_client' then
    raise sqlstate 'PT401' using
      message = 'A connected app access token is required',
      hint = 'API_TOKEN_REQUIRED';
  end if;
end;
$$;

create or replace function private.api_error(p_state text, p_message text, p_hint text)
returns jsonb
language plpgsql
set search_path = ''
as $$
declare
  v_status integer := private.api_http_status(p_state);
begin
  perform set_config('response.status', v_status::text, true);
  perform set_config(
    'response.headers',
    (
      coalesce(nullif(current_setting('response.headers', true), '')::jsonb, '[]'::jsonb)
      || jsonb_build_array(jsonb_build_object('X-PocketPass-Error', 'api'))
    )::text,
    true
  );
  return jsonb_build_object(
    'code', 'PT' || v_status::text,
    'message', p_message,
    'hint', p_hint
  );
end;
$$;

create or replace function private.api_failure(p_state text,p_message text,p_hint text) returns jsonb
language plpgsql set search_path='' as $$
declare translated jsonb; retry_after integer;
begin
  if p_state='42501' and p_hint in (
    'DIRECT_MESSAGES_BLOCKED','FRIEND_REQUESTS_BLOCKED',
    'GROUP_MESSAGES_BLOCKED','BOARD_INVITATIONS_BLOCKED'
  ) then
    return private.api_error('PT403',p_message,p_hint);
  end if;
  if p_state like 'PT%' and coalesce(p_hint,'')<>'' then
    return private.api_error(p_state,p_message,p_hint);
  end if;
  translated:=private.api_translate(p_state,p_message);
  if translated->>'hint'='FRIEND_CODE_RATE_LIMITED' then
    select greatest(1,ceil(extract(epoch from (min(attempt.attempted_at)+interval '1 hour'-now())))::integer)
      into retry_after
      from private.friend_code_lookup_attempts as attempt
      where attempt.actor_id=auth.uid() and attempt.attempted_at>=now()-interval '1 hour';
    perform set_config('response.headers',(coalesce(nullif(current_setting('response.headers',true),'')::jsonb,'[]')||
      jsonb_build_array(jsonb_build_object('Retry-After',coalesce(retry_after,3600)::text)))::text,true);
  end if;
  return private.api_error(translated->>'code',translated->>'message',translated->>'hint');
end $$;

alter table private.api_rate_buckets_app add column shard smallint not null default 0;
alter table private.api_rate_buckets_app drop constraint api_rate_buckets_app_pkey;
alter table private.api_rate_buckets_app add constraint api_rate_buckets_app_pkey primary key (client_id, bucket, shard);
alter table private.api_rate_buckets_app_second add column shard smallint not null default 0;
alter table private.api_rate_buckets_app_second drop constraint api_rate_buckets_app_second_pkey;
alter table private.api_rate_buckets_app_second add constraint api_rate_buckets_app_second_pkey primary key (client_id, bucket, shard);

create or replace function private.api_meter(p_client_id uuid, p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_bucket timestamptz := date_trunc('minute', now());
  v_second timestamptz := date_trunc('second', clock_timestamp());
  v_shard smallint := (pg_backend_pid() % 16)::smallint;
  v_user_requests integer;
  v_app_requests integer;
  v_second_requests integer;
begin
  insert into private.api_rate_buckets (client_id, user_id, bucket, requests)
  values (p_client_id, p_user_id, v_bucket, 1)
  on conflict (client_id, user_id, bucket)
  do update set requests = private.api_rate_buckets.requests + 1
  returning requests into v_user_requests;

  insert into private.api_rate_buckets_app (client_id, bucket, shard, requests)
  values (p_client_id, v_bucket, v_shard, 1)
  on conflict (client_id, bucket, shard)
  do update set requests = private.api_rate_buckets_app.requests + 1;

  select coalesce(sum(bucket.requests), 0)::integer
  into v_app_requests
  from private.api_rate_buckets_app as bucket
  where bucket.client_id = p_client_id
    and bucket.bucket = v_bucket;

  insert into private.api_rate_buckets_app_second (client_id, bucket, shard, requests)
  values (p_client_id, v_second, v_shard, 1)
  on conflict (client_id, bucket, shard)
  do update set requests = private.api_rate_buckets_app_second.requests + 1;

  select coalesce(sum(bucket.requests), 0)::integer
  into v_second_requests
  from private.api_rate_buckets_app_second as bucket
  where bucket.client_id = p_client_id
    and bucket.bucket = v_second;

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

do $migration$
declare
  definition text := pg_get_functiondef('public.api_v1_session_get(jsonb)'::regprocedure);
  target text := E'select bucket.requests\n    into v_app_requests';
begin
  if (length(definition) - length(replace(definition, target, ''))) / length(target) <> 1 then
    raise exception 'api_v1_session_get no longer matches the expected app bucket query';
  end if;
  execute replace(definition, target, E'select sum(bucket.requests)::integer\n    into v_app_requests');
end
$migration$;

create or replace function private.broadcast_profile_friend_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_recipient uuid;
begin
  for v_recipient in
    select new.user_id
    union
    select case when friendship.user_low = new.user_id then friendship.user_high else friendship.user_low end
    from public.friendships as friendship
    where new.user_id in (friendship.user_low, friendship.user_high)
  loop
    perform realtime.send(
      jsonb_build_object('user_id', new.user_id),
      'UPDATE',
      'friends:' || v_recipient::text,
      true
    );
  end loop;
  return new;
end;
$$;

create or replace function public.api_v1_friends_code_resolve(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:write');
  v_user_id uuid;
  v_code text;
  v_target_id uuid;
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
    perform private.api_reject_unknown($1, array['code']);
    v_code := btrim(private.api_arg_text($1, 'code', true, 32));

    select resolved.user_id
    into v_target_id
    from public.resolve_friend_code(v_code) as resolved;

    if v_target_id is null then
      return private.api_error('PT404', 'No account uses this friend code', 'CODE_NOT_FOUND');
    end if;

    select profile.*
    into v_profile
    from public.profiles as profile
    where profile.user_id = v_target_id;

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

create or replace function private.api_privacy_request(p_endpoint text,p_payload jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare
  guard jsonb:=private.api_guard(case p_endpoint when 'blocks.list' then 'blocks:read'
    when 'blocks.set' then 'blocks:write' when 'privacy.get' then 'privacy:read' when 'privacy.set' then 'privacy:write' end);
  args jsonb:=coalesce(p_payload,'{}'::jsonb); actor uuid; target uuid; operation uuid; blocked boolean;
  lim integer; cursor_ts timestamptz; cursor_id uuid; items jsonb; response jsonb; replay boolean;
  state text; message text; hint text;
begin
  if guard ? 'code' then return guard; end if;
  actor:=(guard->>'user_id')::uuid;
  begin
    case p_endpoint
    when 'blocks.list' then
      perform private.api_reject_unknown(args,array['limit','cursor']);
      lim:=private.api_arg_int(args,'limit',30,1,100);
      if args->>'cursor' is not null then
        if length(args->>'cursor')>4096 then raise exception 'Invalid cursor' using errcode='22023'; end if;
        select ts,id into cursor_ts,cursor_id from private.api_decode_cursor(args->>'cursor');
      end if;
      select coalesce(jsonb_agg(to_jsonb(q) order by q.blocked_at desc,q.user_id desc),'[]') into items from
        (select b.blocked_id user_id,p.display_name,p.avatar_path,b.created_at blocked_at
          from public.user_blocks b join public.profiles p on p.user_id=b.blocked_id
          where b.blocker_id=actor and (cursor_ts is null or (b.created_at,b.blocked_id)<(cursor_ts,cursor_id))
          order by b.created_at desc,b.blocked_id desc limit lim+1) q;
      if jsonb_array_length(items)>lim then
        response:=jsonb_build_object('items',items-lim,'next_cursor',private.api_encode_cursor(
          (items->(lim-1)->>'blocked_at')::timestamptz,(items->(lim-1)->>'user_id')::uuid));
      else response:=jsonb_build_object('items',items,'next_cursor',null); end if;
      return response;
    when 'blocks.set' then
      perform private.api_reject_unknown(args,array['user_id','blocked','operation_id']);
      target:=private.api_arg_uuid(args,'user_id',true);
      operation:=private.api_arg_uuid(args,'operation_id',true);
      if jsonb_typeof(args->'blocked') is distinct from 'boolean' then raise exception 'blocked must be a boolean' using errcode='22023'; end if;
      blocked:=public.set_user_block(target,(args->>'blocked')::boolean,operation);
      return jsonb_build_object('user_id',target,'blocked',blocked);
    when 'privacy.get' then
      perform private.api_reject_unknown(args,'{}');
      return (select jsonb_build_object('block_messages',block_messages,'block_invites',block_invites) from public.profiles where user_id=actor);
    when 'privacy.set' then
      perform private.api_reject_unknown(args,array['block_messages','block_invites','operation_id']);
      operation:=private.api_arg_uuid(args,'operation_id',true);
      if not (args ? 'block_messages' or args ? 'block_invites') then
        raise sqlstate 'PT400' using message='Send block_messages, block_invites or both',hint='MISSING_FIELD';
      end if;
      if args ? 'block_messages' and jsonb_typeof(args->'block_messages') is distinct from 'boolean' then raise exception 'block_messages must be a boolean' using errcode='22023'; end if;
      if args ? 'block_invites' and jsonb_typeof(args->'block_invites') is distinct from 'boolean' then raise exception 'block_invites must be a boolean' using errcode='22023'; end if;
      select is_replay,response_payload into replay,response from private.begin_rpc_operation(actor,operation,'api_message_privacy',args-'operation_id');
      if replay then return response; end if;
      update public.profiles
        set block_messages=coalesce((args->>'block_messages')::boolean,block_messages),
            block_invites=coalesce((args->>'block_invites')::boolean,block_invites)
        where user_id=actor
        returning jsonb_build_object('block_messages',block_messages,'block_invites',block_invites) into response;
      if not found then raise exception 'Profile not found' using errcode='P0002'; end if;
      perform private.finish_rpc_operation(actor,operation,'api_message_privacy',args-'operation_id',response);
      return response;
    else return private.api_error('PT404','Unknown endpoint','UNKNOWN_ENDPOINT');
    end case;
  exception when sqlstate '40001' or sqlstate '40P01' then raise;
    when others then get stacked diagnostics state=returned_sqlstate,message=message_text,hint=pg_exception_hint;
      return private.api_failure(state,message,hint);
  end;
end $$;

drop trigger profiles_api_privacy_change on public.profiles;
create trigger profiles_api_privacy_change after update of block_messages, block_invites on public.profiles
  for each row when (
    old.block_messages is distinct from new.block_messages
    or old.block_invites is distinct from new.block_invites
  ) execute function private.api_privacy_changed();

do $migration$
declare
  definition text := pg_get_functiondef('private.api_scope_descriptions()'::regprocedure);
  old_read text := '"privacy:read":"See whether you have Block Messages enabled"';
  old_write text := '"privacy:write":"Enable or disable Block Messages on your account"';
begin
  if position(old_read in definition) = 0 or position(old_write in definition) = 0 then
    raise exception 'api_scope_descriptions no longer matches the expected privacy text';
  end if;
  definition := replace(definition, old_read, '"privacy:read":"See whether you have Block Messages and Block Invites enabled"');
  definition := replace(definition, old_write, '"privacy:write":"Enable or disable Block Messages and Block Invites on your account"');
  execute definition;
end
$migration$;

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
        and (v_updated_after is null or notification.updated_at > v_updated_after)
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

do $migration$
declare
  definition text := pg_get_functiondef('public.boards_query(text,jsonb)'::regprocedure);
  target text := 'jsonb_agg(to_jsonb(d) order by d.created_at desc)';
begin
  if (length(definition) - length(replace(definition, target, ''))) / length(target) <> 1 then
    raise exception 'boards_query no longer matches the expected drafts ordering';
  end if;
  execute replace(definition, target, 'jsonb_agg(to_jsonb(d) order by d.created_at desc,d.id desc)');
end
$migration$;

notify pgrst, 'reload schema';

commit;
