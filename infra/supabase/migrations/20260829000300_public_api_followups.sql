begin;

create or replace function private.api_scope_keys()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array[
    'profile:read',
    'friends:read',
    'friends:write',
    'messages:read',
    'messages:write',
    'notifications:read'
  ]::text[];
$$;

create or replace function private.api_scope_descriptions()
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'profile:read', 'See your profile (name, bio, avatar, age, country)',
    'friends:read', 'See your friends list and friend requests',
    'friends:write', 'Add and remove friends and answer friend requests as you',
    'messages:read', 'Read your conversations and messages',
    'messages:write', 'Send, edit and delete messages as you',
    'notifications:read', 'See and clear your notifications'
  );
$$;

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
    elsif v_message in ('Messaging is not allowed', 'Conversation is not allowed', 'Friend request is not allowed') then
      return jsonb_build_object('code', 'PT403', 'message', v_message, 'hint', 'BLOCKED');
    elsif v_message = 'A friendship is required' then
      return jsonb_build_object('code', 'PT403', 'message', v_message, 'hint', 'NOT_FRIENDS');
    elsif v_message like 'Only the sender can %' then
      return jsonb_build_object('code', 'PT403', 'message', v_message, 'hint', 'NOT_SENDER');
    elsif v_message = 'Only the addressee may respond' then
      return jsonb_build_object('code', 'PT403', 'message', v_message, 'hint', 'NOT_ADDRESSEE');
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
    elsif v_message = 'Reply target is not in this conversation' then
      return jsonb_build_object('code', 'PT400', 'message', v_message, 'hint', 'REPLY_TARGET');
    elsif v_message in ('A user cannot friend themselves', 'A user cannot block themselves', 'A different user id is required') then
      return jsonb_build_object('code', 'PT400', 'message', v_message, 'hint', 'SELF_TARGET');
    elsif v_message = 'Friend code is invalid' then
      return jsonb_build_object('code', 'PT400', 'message', 'code must be 8 digits', 'hint', 'INVALID_CODE');
    end if;
    return jsonb_build_object('code', 'PT400', 'message', v_message, 'hint', 'INVALID_FIELD');
  elsif p_state = 'P0001' then
    if v_message = 'Users are already friends' then
      return jsonb_build_object('code', 'PT409', 'message', v_message, 'hint', 'ALREADY_FRIENDS');
    elsif v_message = 'Friend request already has a different terminal state' then
      return jsonb_build_object('code', 'PT409', 'message', 'Friend request was already answered or cancelled', 'hint', 'REQUEST_CLOSED');
    end if;
  elsif p_state = 'P0002' then
    if v_message = 'Profile not found' then
      return jsonb_build_object('code', 'PT404', 'message', v_message, 'hint', 'PROFILE_NOT_FOUND');
    elsif v_message in ('Friend request not found', 'Stored friend request result is missing') then
      return jsonb_build_object('code', 'PT404', 'message', 'Friend request not found', 'hint', 'REQUEST_NOT_FOUND');
    end if;
  elsif p_state = 'PT409' and v_message = 'respond_to_friend_request_before_delete' then
    return jsonb_build_object(
      'code', 'PT409',
      'message', 'Respond to the friend request before deleting its notification',
      'hint', 'FRIEND_REQUEST_PENDING'
    );
  elsif p_state = 'PT429' and v_message = 'friend_code_rate_limited' then
    return jsonb_build_object(
      'code', 'PT429',
      'message', 'Too many friend code lookups, retry in an hour',
      'hint', 'FRIEND_CODE_RATE_LIMITED'
    );
  end if;
  return jsonb_build_object('code', 'PT500', 'message', 'Internal error', 'hint', 'INTERNAL');
end;
$$;

create or replace function private.api_arg_boolean(p_payload jsonb, p_key text, p_required boolean)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_value jsonb := p_payload -> p_key;
begin
  if v_value is null or jsonb_typeof(v_value) = 'null' then
    if p_required then
      raise sqlstate 'PT400' using
        message = format('%s is required', p_key),
        hint = 'MISSING_FIELD';
    end if;
    return null;
  end if;
  if jsonb_typeof(v_value) <> 'boolean' then
    raise sqlstate 'PT400' using
      message = format('%s must be true or false', p_key),
      hint = 'INVALID_FIELD';
  end if;
  return (v_value #>> '{}')::boolean;
end;
$$;

revoke all on function private.api_arg_boolean(jsonb, text, boolean) from public;

create or replace function private.api_profile_lite_json(p_profile public.profiles)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'user_id', p_profile.user_id,
    'username', p_profile.username::text,
    'display_name', p_profile.display_name,
    'avatar_path', p_profile.avatar_path
  );
$$;

revoke all on function private.api_profile_lite_json(public.profiles) from public;

create or replace function private.api_friend_request_json(p_request public.friend_requests)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', p_request.id,
    'status', case
      when p_request.status = 'rejected' then 'declined'
      else p_request.status::text
    end,
    'requester', (
      select private.api_profile_lite_json(profile)
      from public.profiles as profile
      where profile.user_id = p_request.requester_id
    ),
    'addressee', (
      select private.api_profile_lite_json(profile)
      from public.profiles as profile
      where profile.user_id = p_request.addressee_id
    ),
    'created_at', p_request.created_at,
    'responded_at', p_request.responded_at
  );
$$;

revoke all on function private.api_friend_request_json(public.friend_requests) from public;

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
      or exists (
        select 1
        from public.friend_requests as request
        where request.status = 'pending'
          and (
            (request.requester_id = p_viewer_id and request.addressee_id = p_profile_id)
            or (request.requester_id = p_profile_id and request.addressee_id = p_viewer_id)
          )
      )
    );
$$;

create or replace function public.api_v1_friends_requests_list(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:read');
  v_user_id uuid;
  v_incoming jsonb;
  v_outgoing jsonb;
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
        private.api_friend_request_json(request)
        order by request.created_at desc, request.id
      ),
      '[]'::jsonb
    )
    into v_incoming
    from public.friend_requests as request
    where request.addressee_id = v_user_id
      and request.status = 'pending';

    select coalesce(
      jsonb_agg(
        private.api_friend_request_json(request)
        order by request.created_at desc, request.id
      ),
      '[]'::jsonb
    )
    into v_outgoing
    from public.friend_requests as request
    where request.requester_id = v_user_id
      and request.status = 'pending';

    return jsonb_build_object('incoming', v_incoming, 'outgoing', v_outgoing);
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

create or replace function public.api_v1_friends_request_send(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:write');
  v_user_id uuid;
  v_target_id uuid;
  v_operation_id uuid;
  v_request public.friend_requests;
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
    v_target_id := private.api_arg_uuid($1, 'user_id', true);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);

    v_request := public.send_friend_request(v_target_id, v_operation_id);

    return jsonb_build_object('request', private.api_friend_request_json(v_request));
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

create or replace function public.api_v1_friends_request_respond(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:write');
  v_user_id uuid;
  v_request_id uuid;
  v_accept boolean;
  v_operation_id uuid;
  v_request public.friend_requests;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['request_id', 'accept', 'client_operation_id']);
    v_request_id := private.api_arg_uuid($1, 'request_id', true);
    v_accept := private.api_arg_boolean($1, 'accept', true);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);

    v_request := public.respond_to_friend_request(v_request_id, v_accept, v_operation_id);

    return jsonb_build_object('request', private.api_friend_request_json(v_request));
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

create or replace function public.api_v1_friends_request_cancel(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:write');
  v_user_id uuid;
  v_request_id uuid;
  v_request public.friend_requests;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['request_id']);
    v_request_id := private.api_arg_uuid($1, 'request_id', true);

    select request.*
    into v_request
    from public.friend_requests as request
    where request.id = v_request_id
    for update;

    if not found or v_user_id not in (v_request.requester_id, v_request.addressee_id) then
      raise sqlstate 'PT404' using
        message = 'Friend request not found',
        hint = 'REQUEST_NOT_FOUND';
    end if;
    if v_request.requester_id <> v_user_id then
      raise sqlstate 'PT403' using
        message = 'Only the requester can cancel a friend request',
        hint = 'NOT_REQUESTER';
    end if;
    if v_request.status = 'cancelled' then
      return jsonb_build_object('request', private.api_friend_request_json(v_request));
    end if;
    if v_request.status <> 'pending' then
      raise sqlstate 'PT409' using
        message = 'Friend request was already answered or cancelled',
        hint = 'REQUEST_CLOSED';
    end if;

    update public.friend_requests as request
    set status = 'cancelled', responded_at = now()
    where request.id = v_request_id
    returning request.* into v_request;

    update public.notifications as notification
    set deleted_at = coalesce(notification.deleted_at, now()), updated_at = now()
    where notification.recipient_id = v_request.addressee_id
      and notification.friend_request_id = v_request.id
      and notification.kind = 'friend_request';

    return jsonb_build_object('request', private.api_friend_request_json(v_request));
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

create or replace function public.api_v1_friends_remove(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:write');
  v_user_id uuid;
  v_target_id uuid;
  v_operation_id uuid;
  v_removed boolean;
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
    v_target_id := private.api_arg_uuid($1, 'user_id', true);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);

    v_removed := public.remove_friend(v_target_id, v_operation_id);

    return jsonb_build_object('removed', coalesce(v_removed, false));
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

create or replace function public.api_v1_friends_code_get(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:write');
  v_user_id uuid;
  v_code text;
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

    select friend_code.code
    into v_code
    from public.get_my_friend_code() as friend_code;

    return jsonb_build_object('code', v_code);
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
      raise sqlstate 'PT404' using
        message = 'No account uses this friend code',
        hint = 'CODE_NOT_FOUND';
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
  v_attachment jsonb;
  v_path text;
  v_mime_type text;
  v_metadata jsonb := '{}'::jsonb;
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
      array['conversation_id', 'body', 'client_operation_id', 'reply_to_id', 'attachment']
    );
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);
    v_body := private.api_arg_text($1, 'body', false, null);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);
    v_reply_to_id := private.api_arg_uuid($1, 'reply_to_id', false);

    v_attachment := $1 -> 'attachment';
    if v_attachment is not null and jsonb_typeof(v_attachment) <> 'null' then
      if jsonb_typeof(v_attachment) <> 'object' then
        raise sqlstate 'PT400' using
          message = 'attachment must be an object with path and mime_type',
          hint = 'INVALID_FIELD';
      end if;
      perform private.api_reject_unknown(v_attachment, array['path', 'mime_type']);
      v_path := private.api_arg_text(v_attachment, 'path', true, 512);
      v_mime_type := private.api_arg_text(v_attachment, 'mime_type', true, 64);
      if v_mime_type not in ('image/jpeg', 'image/png', 'image/webp') then
        raise sqlstate 'PT400' using
          message = 'attachment.mime_type must be image/jpeg, image/png or image/webp',
          hint = 'UNSUPPORTED_MEDIA';
      end if;
      if v_path !~ (
        '^' || v_user_id::text || '/' || v_conversation_id::text
        || '/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
      ) then
        raise sqlstate 'PT400' using
          message = 'attachment.path must be <your user id>/<conversation id>/<file name> in the message-media bucket',
          hint = 'INVALID_ATTACHMENT_PATH';
      end if;
      if not exists (
        select 1
        from storage.objects as object
        where object.bucket_id = 'message-media'
          and object.name = v_path
      ) then
        raise sqlstate 'PT404' using
          message = 'attachment.path has not been uploaded to the message-media bucket',
          hint = 'ATTACHMENT_NOT_FOUND';
      end if;
      v_metadata := jsonb_build_object(
        'attachment', jsonb_build_object('path', v_path, 'mime_type', v_mime_type)
      );
      if v_body is null or btrim(v_body) = '' then
        v_body := '📷';
      end if;
    end if;

    if v_body is null then
      raise sqlstate 'PT400' using
        message = 'body is required',
        hint = 'MISSING_FIELD';
    end if;

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
      v_metadata
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

revoke all on function public.api_v1_friends_requests_list(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_friends_request_send(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_friends_request_respond(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_friends_request_cancel(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_friends_remove(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_friends_code_get(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_friends_code_resolve(jsonb) from public, anon, authenticated, service_role;

grant execute on function public.api_v1_friends_requests_list(jsonb) to api_client;
grant execute on function public.api_v1_friends_request_send(jsonb) to api_client;
grant execute on function public.api_v1_friends_request_respond(jsonb) to api_client;
grant execute on function public.api_v1_friends_request_cancel(jsonb) to api_client;
grant execute on function public.api_v1_friends_remove(jsonb) to api_client;
grant execute on function public.api_v1_friends_code_get(jsonb) to api_client;
grant execute on function public.api_v1_friends_code_resolve(jsonb) to api_client;

create or replace function private.api_can_write_object(p_bucket_id text, p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    p_bucket_id = 'message-media'
    and auth.uid() is not null
    and private.api_has_scope('messages:write')
    and (storage.foldername(p_name))[1] = auth.uid()::text
    and private.is_active_conversation_member(
      private.message_media_conversation_id(p_name),
      auth.uid()
    )
    and p_name ~ (
      '^'
      || auth.uid()::text
      || '/[0-9a-fA-F-]{36}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
    );
$$;

revoke all on function private.api_can_write_object(text, text) from public;
grant execute on function private.api_can_write_object(text, text) to api_client;

grant insert on table storage.objects to api_client;

create policy pocketpass_api_message_media_insert
on storage.objects
for insert
to api_client
with check (
  bucket_id = 'message-media'
  and private.api_can_write_object(bucket_id, name)
);

create or replace function private.api_can_access_realtime_topic(p_topic text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    auth.uid() is not null
    and (
      (
        private.conversation_id_from_topic(p_topic) is not null
        and private.api_has_scope('messages:read')
        and private.is_active_conversation_member(
          private.conversation_id_from_topic(p_topic),
          auth.uid()
        )
      )
      or (
        private.notification_user_id_from_topic(p_topic) = auth.uid()
        and private.api_has_scope('notifications:read')
      )
      or (
        private.friend_user_id_from_topic(p_topic) = auth.uid()
        and private.api_has_scope('friends:read')
      )
    ),
    false
  );
$$;

revoke all on function private.api_can_access_realtime_topic(text) from public;
grant execute on function private.api_can_access_realtime_topic(text) to api_client;

grant select on table realtime.messages to api_client;

create policy pocketpass_api_realtime_read
on realtime.messages
for select
to api_client
using (
  extension = 'broadcast'
  and private.api_can_access_realtime_topic(realtime.topic())
);

commit;
