begin;

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
  select coalesce(
    p_topic = 'app_updates'
    or private.is_active_conversation_member(
      private.conversation_id_from_topic(p_topic),
      p_user_id
    )
    or private.notification_user_id_from_topic(p_topic) = p_user_id
    or private.friend_user_id_from_topic(p_topic) = p_user_id
    or private.can_access_friend_presence_topic(p_topic, p_user_id)
    or private.token_user_id_from_topic(p_topic) = p_user_id
    or private.encounter_user_id_from_topic(p_topic) = p_user_id,
    false
  );
$$;

comment on function private.can_access_realtime_topic(text, uuid) is
  'Realtime topic authorization. The public app_updates topic carries release-manifest change broadcasts sent by the VM release poller so running apps re-check for updates immediately.';

commit;
