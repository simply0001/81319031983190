begin;

create or replace function private.has_block_between(p_user_a uuid, p_user_b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    p_user_a is not null
    and p_user_b is not null
    and exists (
      select 1
      from public.user_blocks as block
      where
        (block.blocker_id = p_user_a and block.blocked_id = p_user_b)
        or (block.blocker_id = p_user_b and block.blocked_id = p_user_a)
    );
$$;

create or replace function private.are_friends(p_user_a uuid, p_user_b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    p_user_a is not null
    and p_user_b is not null
    and exists (
      select 1
      from public.friendships as friendship
      where friendship.user_low = least(p_user_a, p_user_b)
        and friendship.user_high = greatest(p_user_a, p_user_b)
    );
$$;

create or replace function private.is_active_conversation_member(
  p_conversation_id uuid,
  p_user_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    p_conversation_id is not null
    and p_user_id is not null
    and exists (
      select 1
      from public.conversation_members as member
      where member.conversation_id = p_conversation_id
        and member.user_id = p_user_id
        and member.left_at is null
    );
$$;

create or replace function private.can_view_profile(p_viewer_id uuid, p_profile_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    p_viewer_id is not null
    and p_profile_id is not null
    and (
      p_viewer_id = p_profile_id
      or not private.has_block_between(p_viewer_id, p_profile_id)
    );
$$;

create or replace function private.avatar_owner_id(p_object_name text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when split_part(p_object_name, '/', 1)
      ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    then split_part(p_object_name, '/', 1)::uuid
    else null
  end;
$$;

create or replace function private.conversation_id_from_topic(p_topic text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when p_topic
      ~ '^conversation:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    then split_part(p_topic, ':', 2)::uuid
    else null
  end;
$$;

create or replace function private.can_access_realtime_topic(
  p_topic text,
  p_user_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select private.is_active_conversation_member(
    private.conversation_id_from_topic(p_topic),
    p_user_id
  );
$$;

create or replace function private.begin_rpc_operation(
  p_actor_id uuid,
  p_client_operation_id uuid,
  p_operation_name text,
  p_request_payload jsonb
)
returns table (
  is_replay boolean,
  response_payload jsonb
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_existing private.rpc_operations;
begin
  if p_actor_id is null
    or p_client_operation_id is null
    or p_operation_name is null
    or p_request_payload is null
  then
    raise exception 'Incomplete RPC operation identity' using errcode = '22004';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(
      p_actor_id::text || ':' || p_client_operation_id::text,
      0
    )
  );

  select operation.*
  into v_existing
  from private.rpc_operations as operation
  where operation.actor_id = p_actor_id
    and operation.client_operation_id = p_client_operation_id;

  if not found then
    return query select false, null::jsonb;
    return;
  end if;

  if v_existing.operation_name <> p_operation_name
    or v_existing.request_payload <> p_request_payload
  then
    raise exception 'Client operation id was already used for another operation'
      using errcode = '22023';
  end if;

  return query select true, v_existing.response_payload;
end;
$$;

create or replace function private.finish_rpc_operation(
  p_actor_id uuid,
  p_client_operation_id uuid,
  p_operation_name text,
  p_request_payload jsonb,
  p_response_payload jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into private.rpc_operations (
    actor_id,
    client_operation_id,
    operation_name,
    request_payload,
    response_payload
  )
  values (
    p_actor_id,
    p_client_operation_id,
    p_operation_name,
    p_request_payload,
    p_response_payload
  );
end;
$$;

revoke all on all functions in schema private from public;
grant usage on schema private to authenticated;
grant execute on function private.has_block_between(uuid, uuid) to authenticated;
grant execute on function private.are_friends(uuid, uuid) to authenticated;
grant execute on function private.is_active_conversation_member(uuid, uuid) to authenticated;
grant execute on function private.can_view_profile(uuid, uuid) to authenticated;
grant execute on function private.avatar_owner_id(text) to authenticated;
grant execute on function private.conversation_id_from_topic(text) to authenticated;
grant execute on function private.can_access_realtime_topic(text, uuid) to authenticated;

create or replace function public.send_friend_request(
  p_addressee_id uuid,
  p_client_operation_id uuid
)
returns public.friend_requests
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_request public.friend_requests;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_addressee_id is null or p_client_operation_id is null then
    raise exception 'addressee and client operation id are required'
      using errcode = '22004';
  end if;

  if v_actor_id = p_addressee_id then
    raise exception 'A user cannot friend themselves' using errcode = '22023';
  end if;

  if not exists (
    select 1 from public.profiles where user_id = p_addressee_id
  ) then
    raise exception 'Profile not found' using errcode = 'P0002';
  end if;

  select request.*
  into v_request
  from public.friend_requests as request
  where request.requester_id = v_actor_id
    and request.client_operation_id = p_client_operation_id;

  if found then
    if v_request.addressee_id <> p_addressee_id then
      raise exception 'Client operation id was already used for another request'
        using errcode = '22023';
    end if;
    return v_request;
  end if;

  if private.has_block_between(v_actor_id, p_addressee_id) then
    raise exception 'Friend request is not allowed' using errcode = '42501';
  end if;

  if private.are_friends(v_actor_id, p_addressee_id) then
    raise exception 'Users are already friends' using errcode = 'P0001';
  end if;

  select request.*
  into v_request
  from public.friend_requests as request
  where request.status = 'pending'
    and least(request.requester_id, request.addressee_id)
      = least(v_actor_id, p_addressee_id)
    and greatest(request.requester_id, request.addressee_id)
      = greatest(v_actor_id, p_addressee_id)
  for update;

  if found then
    return v_request;
  end if;

  insert into public.friend_requests (
    requester_id,
    addressee_id,
    client_operation_id
  )
  values (
    v_actor_id,
    p_addressee_id,
    p_client_operation_id
  )
  on conflict do nothing
  returning * into v_request;

  if found then
    return v_request;
  end if;

  select request.*
  into v_request
  from public.friend_requests as request
  where request.requester_id = v_actor_id
    and request.client_operation_id = p_client_operation_id;

  if found then
    if v_request.addressee_id <> p_addressee_id then
      raise exception 'Client operation id was already used for another request'
        using errcode = '22023';
    end if;
    return v_request;
  end if;

  select request.*
  into v_request
  from public.friend_requests as request
  where request.status = 'pending'
    and least(request.requester_id, request.addressee_id)
      = least(v_actor_id, p_addressee_id)
    and greatest(request.requester_id, request.addressee_id)
      = greatest(v_actor_id, p_addressee_id);

  if found then
    return v_request;
  end if;

  raise exception 'Friend request could not be created' using errcode = '40001';
end;
$$;

create or replace function public.respond_to_friend_request(
  p_request_id uuid,
  p_accept boolean,
  p_client_operation_id uuid
)
returns public.friend_requests
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_request public.friend_requests;
  v_desired_status public.friend_request_status;
  v_rpc_request jsonb;
  v_rpc_response jsonb;
  v_is_replay boolean;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_request_id is null
    or p_accept is null
    or p_client_operation_id is null
  then
    raise exception 'request id, decision, and client operation id are required'
      using errcode = '22004';
  end if;

  v_rpc_request := jsonb_build_object(
    'request_id', p_request_id,
    'accept', p_accept
  );

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_rpc_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'respond_to_friend_request',
    v_rpc_request
  ) as operation;

  if v_is_replay then
    select request.*
    into v_request
    from public.friend_requests as request
    where request.id = (v_rpc_response ->> 'request_id')::uuid;

    if not found then
      raise exception 'Stored friend request result is missing'
        using errcode = 'P0002';
    end if;

    return v_request;
  end if;

  select request.*
  into v_request
  from public.friend_requests as request
  where request.id = p_request_id
  for update;

  if not found then
    raise exception 'Friend request not found' using errcode = 'P0002';
  end if;

  if v_request.addressee_id <> v_actor_id then
    raise exception 'Only the addressee may respond' using errcode = '42501';
  end if;

  v_desired_status := case
    when p_accept then 'accepted'::public.friend_request_status
    else 'rejected'::public.friend_request_status
  end;

  if v_request.status = v_desired_status then
    perform private.finish_rpc_operation(
      v_actor_id,
      p_client_operation_id,
      'respond_to_friend_request',
      v_rpc_request,
      jsonb_build_object('request_id', v_request.id)
    );
    return v_request;
  end if;

  if v_request.status <> 'pending' then
    raise exception 'Friend request already has a different terminal state'
      using errcode = 'P0001';
  end if;

  if p_accept then
    if private.has_block_between(v_actor_id, v_request.requester_id) then
      raise exception 'Friend request is not allowed' using errcode = '42501';
    end if;

    insert into public.friendships (
      user_low,
      user_high,
      created_by
    )
    values (
      least(v_actor_id, v_request.requester_id),
      greatest(v_actor_id, v_request.requester_id),
      v_actor_id
    )
    on conflict (user_low, user_high) do nothing;
  end if;

  update public.friend_requests
  set
    status = v_desired_status,
    responded_at = now()
  where id = v_request.id
  returning * into v_request;

  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'respond_to_friend_request',
    v_rpc_request,
    jsonb_build_object('request_id', v_request.id)
  );

  return v_request;
end;
$$;

create or replace function public.remove_friend(
  p_friend_id uuid,
  p_client_operation_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_deleted_count integer;
  v_rpc_request jsonb;
  v_rpc_response jsonb;
  v_is_replay boolean;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_friend_id is null or p_client_operation_id is null then
    raise exception 'friend id and client operation id are required'
      using errcode = '22004';
  end if;

  if p_friend_id = v_actor_id then
    raise exception 'A different user id is required' using errcode = '22023';
  end if;

  v_rpc_request := jsonb_build_object('friend_id', p_friend_id);

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_rpc_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'remove_friend',
    v_rpc_request
  ) as operation;

  if v_is_replay then
    return (v_rpc_response ->> 'removed')::boolean;
  end if;

  delete from public.friendships
  where user_low = least(v_actor_id, p_friend_id)
    and user_high = greatest(v_actor_id, p_friend_id);

  get diagnostics v_deleted_count = row_count;

  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'remove_friend',
    v_rpc_request,
    jsonb_build_object('removed', v_deleted_count > 0)
  );

  return v_deleted_count > 0;
end;
$$;

create or replace function public.set_user_block(
  p_user_id uuid,
  p_blocked boolean,
  p_client_operation_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_rpc_request jsonb;
  v_rpc_response jsonb;
  v_is_replay boolean;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_user_id is null
    or p_blocked is null
    or p_client_operation_id is null
  then
    raise exception 'target, blocked state, and client operation id are required'
      using errcode = '22004';
  end if;

  if p_user_id = v_actor_id then
    raise exception 'A user cannot block themselves' using errcode = '22023';
  end if;

  if not exists (
    select 1 from public.profiles where user_id = p_user_id
  ) then
    raise exception 'Profile not found' using errcode = 'P0002';
  end if;

  v_rpc_request := jsonb_build_object(
    'user_id', p_user_id,
    'blocked', p_blocked
  );

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_rpc_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'set_user_block',
    v_rpc_request
  ) as operation;

  if v_is_replay then
    return (v_rpc_response ->> 'blocked')::boolean;
  end if;

  if p_blocked then
    insert into public.user_blocks (blocker_id, blocked_id)
    values (v_actor_id, p_user_id)
    on conflict (blocker_id, blocked_id) do nothing;

    update public.friend_requests
    set
      status = 'cancelled',
      responded_at = now()
    where status = 'pending'
      and least(requester_id, addressee_id)
        = least(v_actor_id, p_user_id)
      and greatest(requester_id, addressee_id)
        = greatest(v_actor_id, p_user_id);

    delete from public.friendships
    where user_low = least(v_actor_id, p_user_id)
      and user_high = greatest(v_actor_id, p_user_id);

    perform private.finish_rpc_operation(
      v_actor_id,
      p_client_operation_id,
      'set_user_block',
      v_rpc_request,
      '{"blocked":true}'::jsonb
    );

    return true;
  end if;

  delete from public.user_blocks
  where blocker_id = v_actor_id
    and blocked_id = p_user_id;

  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'set_user_block',
    v_rpc_request,
    '{"blocked":false}'::jsonb
  );

  return false;
end;
$$;

create or replace function public.get_or_create_direct_conversation(
  p_other_user_id uuid,
  p_client_operation_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_user_low uuid;
  v_user_high uuid;
  v_conversation_id uuid;
  v_created boolean := false;
  v_rpc_request jsonb;
  v_rpc_response jsonb;
  v_is_replay boolean;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_other_user_id is null or p_client_operation_id is null then
    raise exception 'other user id and client operation id are required'
      using errcode = '22004';
  end if;

  if p_other_user_id = v_actor_id then
    raise exception 'A different user id is required' using errcode = '22023';
  end if;

  if not exists (
    select 1 from public.profiles where user_id = p_other_user_id
  ) then
    raise exception 'Profile not found' using errcode = 'P0002';
  end if;

  if private.has_block_between(v_actor_id, p_other_user_id) then
    raise exception 'Conversation is not allowed' using errcode = '42501';
  end if;

  if not private.are_friends(v_actor_id, p_other_user_id) then
    raise exception 'A friendship is required' using errcode = '42501';
  end if;

  v_rpc_request := jsonb_build_object('other_user_id', p_other_user_id);

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_rpc_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'get_or_create_direct_conversation',
    v_rpc_request
  ) as operation;

  if v_is_replay then
    return (v_rpc_response ->> 'conversation_id')::uuid;
  end if;

  v_user_low := least(v_actor_id, p_other_user_id);
  v_user_high := greatest(v_actor_id, p_other_user_id);

  insert into public.conversations (
    kind,
    created_by,
    direct_user_low,
    direct_user_high
  )
  values (
    'direct',
    v_actor_id,
    v_user_low,
    v_user_high
  )
  on conflict (direct_user_low, direct_user_high)
    where kind = 'direct'
  do nothing
  returning id into v_conversation_id;

  v_created := found;

  if not v_created then
    select conversation.id
    into v_conversation_id
    from public.conversations as conversation
    where conversation.kind = 'direct'
      and conversation.direct_user_low = v_user_low
      and conversation.direct_user_high = v_user_high;
  end if;

  if v_conversation_id is null then
    raise exception 'Conversation could not be created' using errcode = '40001';
  end if;

  insert into public.conversation_members (
    conversation_id,
    user_id,
    role
  )
  values
    (
      v_conversation_id,
      v_actor_id,
      case
        when v_created then 'owner'::public.conversation_member_role
        else 'member'::public.conversation_member_role
      end
    ),
    (
      v_conversation_id,
      p_other_user_id,
      'member'
    )
  on conflict (conversation_id, user_id)
  do update set left_at = null;

  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'get_or_create_direct_conversation',
    v_rpc_request,
    jsonb_build_object('conversation_id', v_conversation_id)
  );

  return v_conversation_id;
end;
$$;

create or replace function public.send_message(
  p_message_id uuid,
  p_conversation_id uuid,
  p_client_operation_id uuid,
  p_body text,
  p_reply_to_id uuid default null,
  p_metadata jsonb default '{}'::jsonb
)
returns public.messages
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_message public.messages;
  v_metadata jsonb := coalesce(p_metadata, '{}'::jsonb);
  v_inserted boolean := false;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_message_id is null
    or p_conversation_id is null
    or p_client_operation_id is null
    or p_body is null
  then
    raise exception 'message, conversation, client operation id, and body are required'
      using errcode = '22004';
  end if;

  if char_length(btrim(p_body)) not between 1 and 4000 then
    raise exception 'Message body must contain 1 to 4000 characters'
      using errcode = '22023';
  end if;

  if jsonb_typeof(v_metadata) <> 'object'
    or octet_length(v_metadata::text) > 8192
  then
    raise exception 'Message metadata must be an object no larger than 8192 bytes'
      using errcode = '22023';
  end if;

  if not private.is_active_conversation_member(
    p_conversation_id,
    v_actor_id
  ) then
    raise exception 'Active conversation membership is required'
      using errcode = '42501';
  end if;

  if exists (
    select 1
    from public.conversation_members as member
    where member.conversation_id = p_conversation_id
      and member.left_at is null
      and member.user_id <> v_actor_id
      and private.has_block_between(v_actor_id, member.user_id)
  ) then
    raise exception 'Messaging is not allowed' using errcode = '42501';
  end if;

  select message.*
  into v_message
  from public.messages as message
  where message.sender_id = v_actor_id
    and message.client_operation_id = p_client_operation_id;

  if found then
    if v_message.id <> p_message_id
      or v_message.conversation_id <> p_conversation_id
      or v_message.body <> p_body
      or v_message.reply_to_id is distinct from p_reply_to_id
      or v_message.metadata <> v_metadata
    then
      raise exception 'Client operation id was already used for another message'
        using errcode = '22023';
    end if;
    return v_message;
  end if;

  if p_reply_to_id is not null and not exists (
    select 1
    from public.messages as parent
    where parent.id = p_reply_to_id
      and parent.conversation_id = p_conversation_id
  ) then
    raise exception 'Reply target is not in this conversation'
      using errcode = '22023';
  end if;

  insert into public.messages (
    id,
    conversation_id,
    sender_id,
    client_operation_id,
    body,
    reply_to_id,
    metadata
  )
  values (
    p_message_id,
    p_conversation_id,
    v_actor_id,
    p_client_operation_id,
    p_body,
    p_reply_to_id,
    v_metadata
  )
  on conflict (sender_id, client_operation_id) do nothing
  returning * into v_message;

  v_inserted := found;

  if not v_inserted then
    select message.*
    into v_message
    from public.messages as message
    where message.sender_id = v_actor_id
      and message.client_operation_id = p_client_operation_id;

    if not found then
      raise exception 'Message could not be created' using errcode = '40001';
    end if;

    if v_message.id <> p_message_id
      or v_message.conversation_id <> p_conversation_id
      or v_message.body <> p_body
      or v_message.reply_to_id is distinct from p_reply_to_id
      or v_message.metadata <> v_metadata
    then
      raise exception 'Client operation id was already used for another message'
        using errcode = '22023';
    end if;
  end if;

  if v_inserted then
    update public.conversations
    set updated_at = v_message.created_at
    where id = p_conversation_id;
  end if;

  return v_message;
end;
$$;

create or replace function public.mark_conversation_read(
  p_conversation_id uuid,
  p_read_at timestamptz default now()
)
returns timestamptz
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_last_read_at timestamptz;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_conversation_id is null then
    raise exception 'conversation id is required' using errcode = '22004';
  end if;

  update public.conversation_members
  set last_read_at = greatest(
    coalesce(last_read_at, '-infinity'::timestamptz),
    least(coalesce(p_read_at, now()), now())
  )
  where conversation_id = p_conversation_id
    and user_id = v_actor_id
    and left_at is null
  returning last_read_at into v_last_read_at;

  if not found then
    raise exception 'Active conversation membership is required'
      using errcode = '42501';
  end if;

  return v_last_read_at;
end;
$$;

create or replace function public.record_interaction_event(
  p_event_id uuid,
  p_subject_user_id uuid,
  p_event_type public.interaction_event_type,
  p_client_operation_id uuid,
  p_payload jsonb default '{}'::jsonb,
  p_occurred_at timestamptz default now()
)
returns public.interaction_events
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_event public.interaction_events;
  v_payload jsonb := coalesce(p_payload, '{}'::jsonb);
  v_occurred_at timestamptz := coalesce(p_occurred_at, now());
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_event_id is null
    or p_event_type is null
    or p_client_operation_id is null
  then
    raise exception 'event id, event type, and client operation id are required'
      using errcode = '22004';
  end if;

  if jsonb_typeof(v_payload) <> 'object'
    or octet_length(v_payload::text) > 8192
  then
    raise exception 'Event payload must be an object no larger than 8192 bytes'
      using errcode = '22023';
  end if;

  select event.*
  into v_event
  from public.interaction_events as event
  where event.actor_id = v_actor_id
    and event.client_operation_id = p_client_operation_id;

  if found then
    if v_event.id <> p_event_id
      or v_event.subject_user_id is distinct from p_subject_user_id
      or v_event.event_type <> p_event_type
      or v_event.payload <> v_payload
    then
      raise exception 'Client operation id was already used for another event'
        using errcode = '22023';
    end if;
    return v_event;
  end if;

  if p_subject_user_id is not null and not exists (
    select 1 from public.profiles where user_id = p_subject_user_id
  ) then
    raise exception 'Subject profile not found' using errcode = 'P0002';
  end if;

  if p_subject_user_id is not null
    and private.has_block_between(v_actor_id, p_subject_user_id)
  then
    raise exception 'Interaction is not allowed' using errcode = '42501';
  end if;

  if v_occurred_at < now() - interval '30 days'
    or v_occurred_at > now() + interval '5 minutes'
  then
    raise exception 'Event time is outside the accepted window'
      using errcode = '22023';
  end if;

  insert into public.interaction_events (
    id,
    actor_id,
    subject_user_id,
    event_type,
    client_operation_id,
    payload,
    occurred_at
  )
  values (
    p_event_id,
    v_actor_id,
    p_subject_user_id,
    p_event_type,
    p_client_operation_id,
    v_payload,
    v_occurred_at
  )
  on conflict (actor_id, client_operation_id) do nothing
  returning * into v_event;

  if found then
    return v_event;
  end if;

  select event.*
  into v_event
  from public.interaction_events as event
  where event.actor_id = v_actor_id
    and event.client_operation_id = p_client_operation_id;

  if not found then
    raise exception 'Interaction event could not be created' using errcode = '40001';
  end if;

  if v_event.id <> p_event_id
    or v_event.subject_user_id is distinct from p_subject_user_id
    or v_event.event_type <> p_event_type
    or v_event.payload <> v_payload
  then
    raise exception 'Client operation id was already used for another event'
      using errcode = '22023';
  end if;

  return v_event;
end;
$$;

revoke all on function public.send_friend_request(uuid, uuid) from public;
revoke all on function public.respond_to_friend_request(uuid, boolean, uuid) from public;
revoke all on function public.remove_friend(uuid, uuid) from public;
revoke all on function public.set_user_block(uuid, boolean, uuid) from public;
revoke all on function public.get_or_create_direct_conversation(uuid, uuid) from public;
revoke all on function public.send_message(uuid, uuid, uuid, text, uuid, jsonb) from public;
revoke all on function public.mark_conversation_read(uuid, timestamptz) from public;
revoke all on function public.record_interaction_event(
  uuid,
  uuid,
  public.interaction_event_type,
  uuid,
  jsonb,
  timestamptz
) from public;

grant execute on function public.send_friend_request(uuid, uuid) to authenticated;
grant execute on function public.respond_to_friend_request(uuid, boolean, uuid) to authenticated;
grant execute on function public.remove_friend(uuid, uuid) to authenticated;
grant execute on function public.set_user_block(uuid, boolean, uuid) to authenticated;
grant execute on function public.get_or_create_direct_conversation(uuid, uuid) to authenticated;
grant execute on function public.send_message(uuid, uuid, uuid, text, uuid, jsonb) to authenticated;
grant execute on function public.mark_conversation_read(uuid, timestamptz) to authenticated;
grant execute on function public.record_interaction_event(
  uuid,
  uuid,
  public.interaction_event_type,
  uuid,
  jsonb,
  timestamptz
) to authenticated;

alter table public.profiles enable row level security;
alter table public.friend_requests enable row level security;
alter table public.friendships enable row level security;
alter table public.user_blocks enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;
alter table public.interaction_events enable row level security;

create policy profiles_select_visible
on public.profiles
for select
to authenticated
using (private.can_view_profile(auth.uid(), user_id));

create policy profiles_update_self
on public.profiles
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create policy friend_requests_select_participant
on public.friend_requests
for select
to authenticated
using (auth.uid() = requester_id or auth.uid() = addressee_id);

create policy friendships_select_participant
on public.friendships
for select
to authenticated
using (auth.uid() = user_low or auth.uid() = user_high);

create policy user_blocks_select_own
on public.user_blocks
for select
to authenticated
using (auth.uid() = blocker_id);

create policy conversations_select_active_member
on public.conversations
for select
to authenticated
using (private.is_active_conversation_member(id, auth.uid()));

create policy conversation_members_select_active_member
on public.conversation_members
for select
to authenticated
using (
  private.is_active_conversation_member(conversation_id, auth.uid())
);

create policy messages_select_active_member
on public.messages
for select
to authenticated
using (
  private.is_active_conversation_member(conversation_id, auth.uid())
);

create policy interaction_events_select_participant
on public.interaction_events
for select
to authenticated
using (
  (auth.uid() = actor_id or auth.uid() = subject_user_id)
  and (
    subject_user_id is null
    or not private.has_block_between(actor_id, subject_user_id)
  )
);

revoke all on table public.profiles from anon, authenticated;
revoke all on table public.friend_requests from anon, authenticated;
revoke all on table public.friendships from anon, authenticated;
revoke all on table public.user_blocks from anon, authenticated;
revoke all on table public.conversations from anon, authenticated;
revoke all on table public.conversation_members from anon, authenticated;
revoke all on table public.messages from anon, authenticated;
revoke all on table public.interaction_events from anon, authenticated;

grant select on table public.profiles to authenticated;
grant update (
  username,
  display_name,
  bio,
  avatar_path,
  age,
  country_code
) on table public.profiles to authenticated;
grant select on table public.friend_requests to authenticated;
grant select on table public.friendships to authenticated;
grant select on table public.user_blocks to authenticated;
grant select on table public.conversations to authenticated;
grant select on table public.conversation_members to authenticated;
grant select on table public.messages to authenticated;
grant select on table public.interaction_events to authenticated;

create or replace function private.broadcast_message_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_conversation_id uuid;
begin
  if tg_op = 'DELETE' then
    v_conversation_id := old.conversation_id;
  else
    v_conversation_id := new.conversation_id;
  end if;

  perform realtime.broadcast_changes(
    'conversation:' || v_conversation_id::text,
    tg_op,
    tg_op,
    tg_table_name,
    tg_table_schema,
    new,
    old
  );

  if tg_op = 'DELETE' then
    return old;
  end if;

  return new;
end;
$$;

revoke all on function private.broadcast_message_change() from public;

create trigger messages_broadcast_change
after insert or update or delete on public.messages
for each row execute function private.broadcast_message_change();

create policy pocketpass_conversation_realtime_read
on realtime.messages
for select
to authenticated
using (
  extension in ('broadcast', 'presence')
  and private.can_access_realtime_topic(realtime.topic(), auth.uid())
);

create policy pocketpass_conversation_presence_track
on realtime.messages
for insert
to authenticated
with check (
  extension = 'presence'
  and private.can_access_realtime_topic(realtime.topic(), auth.uid())
);

grant select, insert on table realtime.messages to authenticated;

commit;
