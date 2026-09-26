begin;

create or replace function private.group_member_limit()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 20;
$$;

revoke all on function private.group_member_limit() from public;

create or replace function private.canonical_member_ids(
  p_member_ids uuid[],
  p_actor_id uuid
)
returns uuid[]
language sql
immutable
set search_path = ''
as $$
  select coalesce(
    (
      select array_agg(distinct member_id order by member_id)
      from unnest(coalesce(p_member_ids, '{}'::uuid[])) as member_id
      where member_id is not null
        and member_id <> p_actor_id
    ),
    '{}'::uuid[]
  );
$$;

revoke all on function private.canonical_member_ids(uuid[], uuid) from public;

create or replace function private.assert_group_addable(
  p_adder_id uuid,
  p_member_id uuid
)
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not exists (
    select 1
    from public.profiles as profile
    where profile.user_id = p_member_id
  ) then
    raise exception 'Profile not found'
      using errcode = 'P0002', detail = p_member_id::text;
  end if;

  if private.has_block_between(p_adder_id, p_member_id) then
    raise exception 'Conversation is not allowed'
      using errcode = '42501', detail = p_member_id::text;
  end if;

  if not private.are_friends(p_adder_id, p_member_id) then
    raise exception 'A friendship is required'
      using errcode = '42501', detail = p_member_id::text;
  end if;
end;
$$;

revoke all on function private.assert_group_addable(uuid, uuid) from public;

create or replace function private.lock_group_conversation(
  p_conversation_id uuid,
  p_actor_id uuid
)
returns public.conversations
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_conversation public.conversations;
begin
  if not private.is_active_conversation_member(p_conversation_id, p_actor_id) then
    raise exception 'Active conversation membership is required'
      using errcode = '42501';
  end if;

  select conversation.*
  into v_conversation
  from public.conversations as conversation
  where conversation.id = p_conversation_id
  for update;

  if not found then
    raise exception 'Active conversation membership is required'
      using errcode = '42501';
  end if;

  if v_conversation.kind <> 'group' then
    raise exception 'A group conversation is required' using errcode = '22023';
  end if;

  return v_conversation;
end;
$$;

revoke all on function private.lock_group_conversation(uuid, uuid) from public;

create or replace function private.transfer_group_ownership(
  p_conversation_id uuid,
  p_from_user_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_new_owner_id uuid;
begin
  select member.user_id
  into v_new_owner_id
  from public.conversation_members as member
  where member.conversation_id = p_conversation_id
    and member.left_at is null
    and member.user_id <> p_from_user_id
  order by member.joined_at, member.user_id
  limit 1;

  if v_new_owner_id is null then
    return null;
  end if;

  update public.conversation_members as member
  set role = 'owner'
  where member.conversation_id = p_conversation_id
    and member.user_id = v_new_owner_id;

  return v_new_owner_id;
end;
$$;

revoke all on function private.transfer_group_ownership(uuid, uuid) from public;

create or replace function public.create_group_conversation(
  p_title text,
  p_member_ids uuid[],
  p_client_operation_id uuid
)
returns public.conversations
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_title text;
  v_member_ids uuid[];
  v_member_id uuid;
  v_conversation public.conversations;
  v_rpc_request jsonb;
  v_rpc_response jsonb;
  v_is_replay boolean;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_title is null or p_member_ids is null or p_client_operation_id is null then
    raise exception 'title, member ids, and client operation id are required'
      using errcode = '22004';
  end if;

  v_title := btrim(p_title);

  if char_length(v_title) not between 1 and 80 then
    raise exception 'Group title must contain 1 to 80 characters'
      using errcode = '22023';
  end if;

  v_member_ids := private.canonical_member_ids(p_member_ids, v_actor_id);

  if cardinality(v_member_ids) = 0 then
    raise exception 'At least one other member is required' using errcode = '22023';
  end if;

  if cardinality(v_member_ids) + 1 > private.group_member_limit() then
    raise exception 'Group conversations are limited to % members',
      private.group_member_limit()
      using errcode = 'P0001';
  end if;

  foreach v_member_id in array v_member_ids loop
    perform private.assert_group_addable(v_actor_id, v_member_id);
  end loop;

  v_rpc_request := jsonb_build_object(
    'title', v_title,
    'member_ids', to_jsonb(v_member_ids)
  );

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_rpc_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'create_group_conversation',
    v_rpc_request
  ) as operation;

  if v_is_replay then
    select conversation.*
    into v_conversation
    from public.conversations as conversation
    where conversation.id = (v_rpc_response ->> 'conversation_id')::uuid;

    if not found then
      raise exception 'Stored conversation result is missing' using errcode = 'P0002';
    end if;

    return v_conversation;
  end if;

  insert into public.conversations (kind, created_by, title)
  values ('group', v_actor_id, v_title)
  returning * into v_conversation;

  insert into public.conversation_members (conversation_id, user_id, role, last_read_at)
  select
    v_conversation.id,
    v_actor_id,
    'owner'::public.conversation_member_role,
    now()
  union all
  select
    v_conversation.id,
    member_id,
    'member'::public.conversation_member_role,
    now()
  from unnest(v_member_ids) as member_id;

  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'create_group_conversation',
    v_rpc_request,
    jsonb_build_object('conversation_id', v_conversation.id)
  );

  return v_conversation;
end;
$$;

create or replace function public.add_group_members(
  p_conversation_id uuid,
  p_member_ids uuid[],
  p_client_operation_id uuid
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_member_ids uuid[];
  v_member_id uuid;
  v_conversation public.conversations;
  v_active_count integer;
  v_new_count integer;
  v_added integer;
  v_rpc_request jsonb;
  v_rpc_response jsonb;
  v_is_replay boolean;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_conversation_id is null or p_member_ids is null or p_client_operation_id is null then
    raise exception 'conversation, member ids, and client operation id are required'
      using errcode = '22004';
  end if;

  v_member_ids := private.canonical_member_ids(p_member_ids, v_actor_id);

  if cardinality(v_member_ids) = 0 then
    raise exception 'At least one member id is required' using errcode = '22023';
  end if;

  v_rpc_request := jsonb_build_object(
    'conversation_id', p_conversation_id,
    'member_ids', to_jsonb(v_member_ids)
  );

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_rpc_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'add_group_members',
    v_rpc_request
  ) as operation;

  if v_is_replay then
    return (v_rpc_response ->> 'added')::integer;
  end if;

  v_conversation := private.lock_group_conversation(p_conversation_id, v_actor_id);

  foreach v_member_id in array v_member_ids loop
    perform private.assert_group_addable(v_actor_id, v_member_id);
  end loop;

  select count(*)
  into v_active_count
  from public.conversation_members as member
  where member.conversation_id = p_conversation_id
    and member.left_at is null;

  select count(*)
  into v_new_count
  from unnest(v_member_ids) as member_id
  where not exists (
    select 1
    from public.conversation_members as member
    where member.conversation_id = p_conversation_id
      and member.user_id = member_id
      and member.left_at is null
  );

  if v_active_count + v_new_count > private.group_member_limit() then
    raise exception 'Group conversations are limited to % members',
      private.group_member_limit()
      using errcode = 'P0001';
  end if;

  insert into public.conversation_members (conversation_id, user_id, role, last_read_at)
  select
    p_conversation_id,
    member_id,
    'member'::public.conversation_member_role,
    now()
  from unnest(v_member_ids) as member_id
  on conflict (conversation_id, user_id) do update
    set
      left_at = null,
      joined_at = now(),
      last_read_at = now(),
      role = 'member'
    where public.conversation_members.left_at is not null;

  get diagnostics v_added = row_count;

  if v_added > 0 then
    update public.conversations as conversation
    set updated_at = now()
    where conversation.id = p_conversation_id;
  end if;

  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'add_group_members',
    v_rpc_request,
    jsonb_build_object('added', v_added)
  );

  return v_added;
end;
$$;

create or replace function public.remove_group_member(
  p_conversation_id uuid,
  p_user_id uuid,
  p_client_operation_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_conversation public.conversations;
  v_actor_role public.conversation_member_role;
  v_removed boolean;
  v_rpc_request jsonb;
  v_rpc_response jsonb;
  v_is_replay boolean;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_conversation_id is null or p_user_id is null or p_client_operation_id is null then
    raise exception 'conversation, user id, and client operation id are required'
      using errcode = '22004';
  end if;

  if p_user_id = v_actor_id then
    raise exception 'Use leave_group_conversation to remove yourself'
      using errcode = '22023';
  end if;

  v_rpc_request := jsonb_build_object(
    'conversation_id', p_conversation_id,
    'user_id', p_user_id
  );

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_rpc_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'remove_group_member',
    v_rpc_request
  ) as operation;

  if v_is_replay then
    return (v_rpc_response ->> 'removed')::boolean;
  end if;

  v_conversation := private.lock_group_conversation(p_conversation_id, v_actor_id);

  select member.role
  into v_actor_role
  from public.conversation_members as member
  where member.conversation_id = p_conversation_id
    and member.user_id = v_actor_id
    and member.left_at is null;

  if v_actor_role is distinct from 'owner'::public.conversation_member_role then
    raise exception 'Only the group owner may remove members' using errcode = '42501';
  end if;

  update public.conversation_members as member
  set left_at = now()
  where member.conversation_id = p_conversation_id
    and member.user_id = p_user_id
    and member.left_at is null;

  v_removed := found;

  if v_removed then
    update public.conversations as conversation
    set updated_at = now()
    where conversation.id = p_conversation_id;
  end if;

  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'remove_group_member',
    v_rpc_request,
    jsonb_build_object('removed', v_removed)
  );

  return v_removed;
end;
$$;

create or replace function public.leave_group_conversation(
  p_conversation_id uuid,
  p_client_operation_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_conversation public.conversations;
  v_actor_role public.conversation_member_role;
  v_remaining integer;
  v_deleted boolean := false;
  v_rpc_request jsonb;
  v_rpc_response jsonb;
  v_is_replay boolean;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_conversation_id is null or p_client_operation_id is null then
    raise exception 'conversation and client operation id are required'
      using errcode = '22004';
  end if;

  v_rpc_request := jsonb_build_object('conversation_id', p_conversation_id);

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_rpc_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'leave_group_conversation',
    v_rpc_request
  ) as operation;

  if v_is_replay then
    return true;
  end if;

  v_conversation := private.lock_group_conversation(p_conversation_id, v_actor_id);

  select member.role
  into v_actor_role
  from public.conversation_members as member
  where member.conversation_id = p_conversation_id
    and member.user_id = v_actor_id
    and member.left_at is null;

  update public.conversation_members as member
  set left_at = now(), role = 'member'
  where member.conversation_id = p_conversation_id
    and member.user_id = v_actor_id
    and member.left_at is null;

  if v_actor_role = 'owner'::public.conversation_member_role then
    perform private.transfer_group_ownership(p_conversation_id, v_actor_id);
  end if;

  select count(*)
  into v_remaining
  from public.conversation_members as member
  where member.conversation_id = p_conversation_id
    and member.left_at is null;

  if v_remaining = 0 then
    delete from public.conversations as conversation
    where conversation.id = p_conversation_id;
    v_deleted := true;
  else
    update public.conversations as conversation
    set updated_at = now()
    where conversation.id = p_conversation_id;
  end if;

  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'leave_group_conversation',
    v_rpc_request,
    jsonb_build_object('left', true, 'deleted', v_deleted)
  );

  return true;
end;
$$;

create or replace function public.rename_group_conversation(
  p_conversation_id uuid,
  p_title text,
  p_client_operation_id uuid
)
returns public.conversations
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_title text;
  v_conversation public.conversations;
  v_actor_role public.conversation_member_role;
  v_rpc_request jsonb;
  v_rpc_response jsonb;
  v_is_replay boolean;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_conversation_id is null or p_title is null or p_client_operation_id is null then
    raise exception 'conversation, title, and client operation id are required'
      using errcode = '22004';
  end if;

  v_title := btrim(p_title);

  if char_length(v_title) not between 1 and 80 then
    raise exception 'Group title must contain 1 to 80 characters'
      using errcode = '22023';
  end if;

  v_rpc_request := jsonb_build_object(
    'conversation_id', p_conversation_id,
    'title', v_title
  );

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_rpc_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'rename_group_conversation',
    v_rpc_request
  ) as operation;

  if v_is_replay then
    select conversation.*
    into v_conversation
    from public.conversations as conversation
    where conversation.id = p_conversation_id;

    if not found then
      raise exception 'Stored conversation result is missing' using errcode = 'P0002';
    end if;

    return v_conversation;
  end if;

  v_conversation := private.lock_group_conversation(p_conversation_id, v_actor_id);

  select member.role
  into v_actor_role
  from public.conversation_members as member
  where member.conversation_id = p_conversation_id
    and member.user_id = v_actor_id
    and member.left_at is null;

  if v_actor_role is distinct from 'owner'::public.conversation_member_role then
    raise exception 'Only the group owner may rename the group' using errcode = '42501';
  end if;

  if v_conversation.title <> v_title then
    update public.conversations as conversation
    set title = v_title
    where conversation.id = p_conversation_id
    returning conversation.* into v_conversation;
  end if;

  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'rename_group_conversation',
    v_rpc_request,
    jsonb_build_object('conversation_id', v_conversation.id)
  );

  return v_conversation;
end;
$$;

revoke all on function public.create_group_conversation(text, uuid[], uuid) from public, anon;
revoke all on function public.add_group_members(uuid, uuid[], uuid) from public, anon;
revoke all on function public.remove_group_member(uuid, uuid, uuid) from public, anon;
revoke all on function public.leave_group_conversation(uuid, uuid) from public, anon;
revoke all on function public.rename_group_conversation(uuid, text, uuid) from public, anon;
grant execute on function public.create_group_conversation(text, uuid[], uuid) to authenticated;
grant execute on function public.add_group_members(uuid, uuid[], uuid) to authenticated;
grant execute on function public.remove_group_member(uuid, uuid, uuid) to authenticated;
grant execute on function public.leave_group_conversation(uuid, uuid) to authenticated;
grant execute on function public.rename_group_conversation(uuid, text, uuid) to authenticated;

comment on function public.create_group_conversation(text, uuid[], uuid) is
  'Creates a group conversation owned by the caller. The trimmed title must be 1-80 characters, every member must be a friend of the caller with no block in either direction, and the group holds at most 20 active members. Idempotent per client operation id.';

comment on function public.add_group_members(uuid, uuid[], uuid) is
  'Adds the caller''s friends to a group the caller is an active member of, reactivating members who left. Blocks between the caller and a member refuse the call; the 20-member cap is enforced under the conversation row lock. Returns the number of members added. Idempotent per client operation id.';

comment on function public.remove_group_member(uuid, uuid, uuid) is
  'Marks another member as having left a group. Only the group owner may call it; use leave_group_conversation to leave. Returns false when the user was not an active member. Idempotent per client operation id.';

comment on function public.leave_group_conversation(uuid, uuid) is
  'Marks the caller as having left a group. Ownership passes to the earliest-joined remaining member and a group with no active members left is deleted together with its messages. Idempotent per client operation id.';

comment on function public.rename_group_conversation(uuid, text, uuid) is
  'Replaces the title of a group the caller owns (trimmed, 1-80 characters). Members are notified through the conversation Realtime topic. Idempotent per client operation id.';

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
    from public.conversations as conversation
    join public.conversation_members as member
      on member.conversation_id = conversation.id
    where conversation.id = p_conversation_id
      and conversation.kind = 'direct'
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

create or replace function public.edit_message(
  p_message_id uuid,
  p_body text
)
returns public.messages
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_message public.messages;
  v_kind public.conversation_kind;
  v_sender_name text;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_message_id is null or p_body is null then
    raise exception 'message and body are required' using errcode = '22004';
  end if;

  if char_length(btrim(p_body)) not between 1 and 4000 then
    raise exception 'Message body must contain 1 to 4000 characters'
      using errcode = '22023';
  end if;

  select message.*
  into v_message
  from public.messages as message
  where message.id = p_message_id
    and message.sender_id = v_actor_id
  for update;

  if not found then
    raise exception 'Only the sender can edit this message' using errcode = '42501';
  end if;

  if v_message.deleted_at is not null then
    raise exception 'Message has been deleted' using errcode = '22023';
  end if;

  if not private.is_active_conversation_member(
    v_message.conversation_id,
    v_actor_id
  ) then
    raise exception 'Active conversation membership is required'
      using errcode = '42501';
  end if;

  select conversation.kind
  into v_kind
  from public.conversations as conversation
  where conversation.id = v_message.conversation_id;

  if v_kind = 'direct' and exists (
    select 1
    from public.conversation_members as member
    where member.conversation_id = v_message.conversation_id
      and member.left_at is null
      and member.user_id <> v_actor_id
      and private.has_block_between(v_actor_id, member.user_id)
  ) then
    raise exception 'Messaging is not allowed' using errcode = '42501';
  end if;

  if v_message.body = p_body then
    return v_message;
  end if;

  update public.messages as message
  set body = p_body, edited_at = now()
  where message.id = v_message.id
  returning message.* into v_message;

  select profile.display_name
  into v_sender_name
  from public.profiles as profile
  where profile.user_id = v_actor_id;

  update public.notifications as notification
  set body = case
    when v_kind = 'group'
      then coalesce(v_sender_name, 'Someone') || ': ' || left(p_body, 200)
    else left(p_body, 240)
  end
  where notification.kind = 'message'
    and notification.conversation_id = v_message.conversation_id
    and notification.actor_id = v_actor_id
    and notification.updated_at = v_message.created_at
    and notification.recipient_id in (
      select member.user_id
      from public.conversation_members as member
      where member.conversation_id = v_message.conversation_id
    );

  return v_message;
end;
$$;

create or replace function private.notify_message_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_sender_name text;
  v_kind public.conversation_kind;
  v_title text;
begin
  select display_name into v_sender_name
  from public.profiles where user_id = new.sender_id;

  select conversation.kind, conversation.title
  into v_kind, v_title
  from public.conversations as conversation
  where conversation.id = new.conversation_id;

  insert into public.notifications (
    recipient_id,
    kind,
    actor_id,
    conversation_id,
    title,
    body,
    event_count,
    created_at,
    updated_at
  )
  select
    member.user_id,
    'message',
    new.sender_id,
    new.conversation_id,
    case
      when v_kind = 'group' then v_title
      else coalesce(v_sender_name, 'New message')
    end,
    case
      when v_kind = 'group'
        then coalesce(v_sender_name, 'Someone') || ': ' || left(new.body, 200)
      else left(new.body, 240)
    end,
    1,
    new.created_at,
    new.created_at
  from public.conversation_members as member
  where member.conversation_id = new.conversation_id
    and member.user_id <> new.sender_id
    and member.left_at is null
  on conflict (recipient_id, conversation_id)
    where kind = 'message'
  do update set
    actor_id = excluded.actor_id,
    title = excluded.title,
    body = excluded.body,
    event_count = public.notifications.event_count + 1,
    read_at = null,
    deleted_at = null,
    updated_at = excluded.updated_at;

  return new;
end;
$$;

create or replace function private.broadcast_conversation_member_change()
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
    'membership',
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

revoke all on function private.broadcast_conversation_member_change() from public;

create trigger conversation_members_broadcast_change
after insert or update of left_at, role or delete on public.conversation_members
for each row execute function private.broadcast_conversation_member_change();

create or replace function private.broadcast_conversation_title_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if old.title is distinct from new.title then
    perform realtime.broadcast_changes(
      'conversation:' || new.id::text,
      'conversation',
      tg_op,
      tg_table_name,
      tg_table_schema,
      new,
      old
    );
  end if;

  return new;
end;
$$;

revoke all on function private.broadcast_conversation_title_change() from public;

create trigger conversations_broadcast_title_change
after update of title on public.conversations
for each row execute function private.broadcast_conversation_title_change();

create or replace function private.notify_group_membership_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_kind public.conversation_kind;
  v_title text;
  v_actor_name text;
  v_notification_title text;
  v_notification_body text;
  v_notification_actor uuid := v_actor_id;
begin
  if v_actor_id is null or new.user_id = v_actor_id then
    return new;
  end if;

  select conversation.kind, conversation.title
  into v_kind, v_title
  from public.conversations as conversation
  where conversation.id = new.conversation_id;

  if v_kind is distinct from 'group'::public.conversation_kind then
    return new;
  end if;

  select profile.display_name
  into v_actor_name
  from public.profiles as profile
  where profile.user_id = v_actor_id;

  if tg_op = 'INSERT' then
    if new.left_at is not null then
      return new;
    end if;
    v_notification_title := 'Added to ' || v_title;
    v_notification_body := coalesce(v_actor_name, 'Someone') || ' added you';
  elsif old.left_at is not null and new.left_at is null then
    v_notification_title := 'Added to ' || v_title;
    v_notification_body := coalesce(v_actor_name, 'Someone') || ' added you';
  elsif old.left_at is null and new.left_at is not null then
    v_notification_title := 'Removed from ' || v_title;
    v_notification_body := coalesce(v_actor_name, 'The owner') || ' removed you';
  elsif old.role = 'member'::public.conversation_member_role
    and new.role = 'owner'::public.conversation_member_role
    and new.left_at is null
  then
    v_notification_title := 'You now own ' || v_title;
    v_notification_body := 'The previous owner left the group';
    v_notification_actor := null;
  else
    return new;
  end if;

  insert into public.notifications (
    recipient_id,
    kind,
    actor_id,
    conversation_id,
    title,
    body
  )
  values (
    new.user_id,
    'system',
    v_notification_actor,
    new.conversation_id,
    v_notification_title,
    v_notification_body
  );

  return new;
end;
$$;

revoke all on function private.notify_group_membership_change() from public;

create trigger conversation_members_notify_group_change
after insert or update of left_at, role on public.conversation_members
for each row execute function private.notify_group_membership_change();

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_membership record;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  delete from public.messages as message
  where message.sender_id = v_actor_id;

  delete from public.conversations as conversation
  where conversation.kind = 'direct'
    and (
      conversation.direct_user_low = v_actor_id
      or conversation.direct_user_high = v_actor_id
    );

  for v_membership in
    select member.conversation_id, member.role
    from public.conversation_members as member
    join public.conversations as conversation
      on conversation.id = member.conversation_id
    where member.user_id = v_actor_id
      and member.left_at is null
      and conversation.kind = 'group'
    order by member.conversation_id
  loop
    perform 1
    from public.conversations as conversation
    where conversation.id = v_membership.conversation_id
    for update;

    update public.conversation_members as member
    set left_at = now(), role = 'member'
    where member.conversation_id = v_membership.conversation_id
      and member.user_id = v_actor_id;

    if v_membership.role = 'owner'::public.conversation_member_role then
      perform private.transfer_group_ownership(v_membership.conversation_id, v_actor_id);
    end if;
  end loop;

  delete from public.conversations as conversation
  where conversation.kind = 'group'
    and exists (
      select 1
      from public.conversation_members as member
      where member.conversation_id = conversation.id
        and member.user_id = v_actor_id
    )
    and not exists (
      select 1
      from public.conversation_members as member
      where member.conversation_id = conversation.id
        and member.left_at is null
    );

  update public.conversations as conversation
  set created_by = (
    select member.user_id
    from public.conversation_members as member
    where member.conversation_id = conversation.id
      and member.left_at is null
    order by (member.role = 'owner'::public.conversation_member_role) desc,
      member.joined_at,
      member.user_id
    limit 1
  )
  where conversation.kind = 'group'
    and conversation.created_by = v_actor_id
    and exists (
      select 1
      from public.conversation_members as member
      where member.conversation_id = conversation.id
        and member.left_at is null
    );

  delete from public.friendships as friendship
  where friendship.created_by = v_actor_id
    or friendship.user_low = v_actor_id
    or friendship.user_high = v_actor_id;

  delete from auth.users as account
  where account.id = v_actor_id;
end;
$$;

comment on function public.delete_my_account() is
  'Irreversibly removes the calling account and its rows. Direct conversations are deleted; group memberships are marked as left, ownership passes to the earliest-joined remaining member, created_by moves to the surviving owner, and only groups left with no active members are deleted. Callers must delete stored avatars through the Storage API first: storage.protect_delete blocks direct deletes, and bypassing it would orphan the backing files.';

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
    elsif v_message like 'Only the group owner may %' then
      return jsonb_build_object('code', 'PT403', 'message', v_message, 'hint', 'NOT_OWNER');
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
    elsif v_message like 'Group title must contain%' then
      return jsonb_build_object('code', 'PT400', 'message', v_message, 'hint', 'TITLE_LENGTH');
    elsif v_message = 'A group conversation is required' then
      return jsonb_build_object('code', 'PT400', 'message', v_message, 'hint', 'NOT_A_GROUP');
    elsif v_message in ('At least one other member is required', 'At least one member id is required') then
      return jsonb_build_object('code', 'PT400', 'message', v_message, 'hint', 'MEMBERS_REQUIRED');
    elsif v_message = 'Reply target is not in this conversation' then
      return jsonb_build_object('code', 'PT400', 'message', v_message, 'hint', 'REPLY_TARGET');
    elsif v_message in ('A user cannot friend themselves', 'A user cannot block themselves', 'A different user id is required', 'Use leave_group_conversation to remove yourself') then
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
    elsif v_message like 'Group conversations are limited to %' then
      return jsonb_build_object('code', 'PT409', 'message', v_message, 'hint', 'GROUP_FULL');
    end if;
  elsif p_state = 'P0002' then
    if v_message = 'Profile not found' then
      return jsonb_build_object('code', 'PT404', 'message', v_message, 'hint', 'PROFILE_NOT_FOUND');
    elsif v_message in ('Friend request not found', 'Stored friend request result is missing') then
      return jsonb_build_object('code', 'PT404', 'message', 'Friend request not found', 'hint', 'REQUEST_NOT_FOUND');
    elsif v_message = 'Stored conversation result is missing' then
      return jsonb_build_object('code', 'PT404', 'message', 'Conversation not found', 'hint', 'CONVERSATION_NOT_FOUND');
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
    'groups:write',
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
    'groups:write', 'Create group chats and manage their members as you',
    'notifications:read', 'See and clear your notifications'
  );
$$;

create or replace function public.api_v1_conversations_create(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('groups:write');
  v_user_id uuid;
  v_title text;
  v_member_ids uuid[];
  v_operation_id uuid;
  v_conversation public.conversations;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['title', 'member_ids', 'client_operation_id']);
    v_title := private.api_arg_text($1, 'title', true, 80);
    v_member_ids := private.api_arg_uuid_array($1, 'member_ids', private.group_member_limit() - 1);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);

    v_conversation := public.create_group_conversation(v_title, v_member_ids, v_operation_id);

    return jsonb_build_object(
      'conversation', private.api_conversation_json(v_conversation.id, v_user_id)
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

create or replace function public.api_v1_conversations_add_members(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('groups:write');
  v_user_id uuid;
  v_conversation_id uuid;
  v_member_ids uuid[];
  v_operation_id uuid;
  v_added integer;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['conversation_id', 'member_ids', 'client_operation_id']);
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);
    v_member_ids := private.api_arg_uuid_array($1, 'member_ids', private.group_member_limit() - 1);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);

    v_added := public.add_group_members(v_conversation_id, v_member_ids, v_operation_id);

    return jsonb_build_object(
      'conversation', private.api_conversation_json(v_conversation_id, v_user_id),
      'added', v_added
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

create or replace function public.api_v1_conversations_remove_member(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('groups:write');
  v_user_id uuid;
  v_conversation_id uuid;
  v_member_id uuid;
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
    perform private.api_reject_unknown($1, array['conversation_id', 'user_id', 'client_operation_id']);
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);
    v_member_id := private.api_arg_uuid($1, 'user_id', true);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);

    v_removed := public.remove_group_member(v_conversation_id, v_member_id, v_operation_id);

    return jsonb_build_object(
      'conversation', private.api_conversation_json(v_conversation_id, v_user_id),
      'removed', v_removed
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

create or replace function public.api_v1_conversations_leave(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('groups:write');
  v_conversation_id uuid;
  v_operation_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  begin
    perform private.api_reject_unknown($1, array['conversation_id', 'client_operation_id']);
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);

    perform public.leave_group_conversation(v_conversation_id, v_operation_id);

    return jsonb_build_object('left', true);
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

create or replace function public.api_v1_conversations_rename(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('groups:write');
  v_user_id uuid;
  v_conversation_id uuid;
  v_title text;
  v_operation_id uuid;
  v_conversation public.conversations;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['conversation_id', 'title', 'client_operation_id']);
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);
    v_title := private.api_arg_text($1, 'title', true, 80);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);

    v_conversation := public.rename_group_conversation(v_conversation_id, v_title, v_operation_id);

    return jsonb_build_object(
      'conversation', private.api_conversation_json(v_conversation.id, v_user_id)
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

revoke all on function public.api_v1_conversations_create(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_conversations_add_members(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_conversations_remove_member(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_conversations_leave(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_conversations_rename(jsonb) from public, anon, authenticated, service_role;
grant execute on function public.api_v1_conversations_create(jsonb) to api_client;
grant execute on function public.api_v1_conversations_add_members(jsonb) to api_client;
grant execute on function public.api_v1_conversations_remove_member(jsonb) to api_client;
grant execute on function public.api_v1_conversations_leave(jsonb) to api_client;
grant execute on function public.api_v1_conversations_rename(jsonb) to api_client;

commit;
