begin;

create or replace function private.message_replay_matches(
  p_message public.messages,
  p_message_id uuid,
  p_conversation_id uuid,
  p_body text,
  p_reply_to_id uuid,
  p_metadata jsonb
)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select
    (p_message).id = p_message_id
    and (p_message).conversation_id = p_conversation_id
    and (p_message).reply_to_id is not distinct from p_reply_to_id
    and (
      (p_message).edited_at is not null
      or (p_message).deleted_at is not null
      or ((p_message).body = p_body and (p_message).metadata = p_metadata)
    );
$$;

revoke all on function private.message_replay_matches(public.messages, uuid, uuid, text, uuid, jsonb) from public;

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

  select message.*
  into v_message
  from public.messages as message
  where message.sender_id = v_actor_id
    and message.client_operation_id = p_client_operation_id;

  if found then
    if not private.message_replay_matches(
      v_message, p_message_id, p_conversation_id, p_body, p_reply_to_id, v_metadata
    ) then
      raise exception 'Client operation id was already used for another message'
        using errcode = '22023';
    end if;
    return v_message;
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

    if not private.message_replay_matches(
      v_message, p_message_id, p_conversation_id, p_body, p_reply_to_id, v_metadata
    ) then
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

do $migration$
declare
  definition text := pg_get_functiondef('public.api_v1_messages_send(jsonb)'::regprocedure);
  target text := E'      if not exists (\n        select 1\n        from storage.objects as object';
begin
  if (length(definition) - length(replace(definition, target, ''))) / length(target) <> 1 then
    raise exception 'api_v1_messages_send no longer matches the expected attachment check';
  end if;
  execute replace(
    definition,
    target,
    E'      if not exists (\n        select 1\n        from public.messages as sent\n        where sent.sender_id = v_user_id\n          and sent.client_operation_id = v_operation_id\n      ) and not exists (\n        select 1\n        from storage.objects as object'
  );
end
$migration$;

do $migration$
declare
  definition text := pg_get_functiondef('private.api_privacy_request(text,jsonb)'::regprocedure);
  target text := $$raise exception 'Invalid cursor' using errcode='22023';$$;
begin
  if (length(definition) - length(replace(definition, target, ''))) / length(target) <> 1 then
    raise exception 'api_privacy_request no longer matches the expected cursor check';
  end if;
  execute replace(definition, target, $$raise sqlstate 'PT400' using message='Invalid cursor',hint='INVALID_CURSOR';$$);
end
$migration$;

commit;
