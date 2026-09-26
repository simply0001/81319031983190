begin;

-- Owner-only edit and soft delete for public.messages. Clients hold SELECT only on
-- messages (see tests/database/security.test.sql), so both writes go through
-- security definer RPCs. The existing messages_broadcast_change trigger (insert,
-- update, delete) fans the changed row out over realtime; the insert-only
-- notification and achievement triggers are untouched, so admin counters and
-- achievement metrics stay stable. Storage objects are not removed here
-- (storage.protect_delete); a client may remove its own message-media object
-- through the Storage API after delete_message succeeds.

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

  if exists (
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

  -- Refresh the grouped inbox preview only when it was produced by this message.
  update public.notifications as notification
  set body = left(p_body, 240)
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

create or replace function public.delete_message(p_message_id uuid)
returns public.messages
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_message public.messages;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_message_id is null then
    raise exception 'message is required' using errcode = '22004';
  end if;

  select message.*
  into v_message
  from public.messages as message
  where message.id = p_message_id
    and message.sender_id = v_actor_id
  for update;

  if not found then
    raise exception 'Only the sender can delete this message' using errcode = '42501';
  end if;

  if v_message.deleted_at is not null then
    return v_message;
  end if;

  update public.messages as message
  set
    body = 'Message deleted',
    metadata = '{}'::jsonb,
    deleted_at = now()
  where message.id = v_message.id
  returning message.* into v_message;

  -- Scrub the grouped inbox preview only when it was produced by this message.
  update public.notifications as notification
  set body = 'Message deleted'
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

revoke all on function public.edit_message(uuid, text) from public, anon;
revoke all on function public.delete_message(uuid) from public, anon;
grant execute on function public.edit_message(uuid, text) to authenticated;
grant execute on function public.delete_message(uuid) to authenticated;

comment on function public.edit_message(uuid, text) is
  'Replaces the body of a message the caller sent. Validates like send_message (trimmed length 1-4000, active membership, no block), rejects deleted messages, and stamps edited_at only when the body actually changes. Never bumps conversations.updated_at.';

comment on function public.delete_message(uuid) is
  'Soft-deletes a message the caller sent: sets deleted_at, replaces body with ''Message deleted'' and clears metadata so clients drop the attachment. Idempotent. The message-media object is not removed here.';

commit;
