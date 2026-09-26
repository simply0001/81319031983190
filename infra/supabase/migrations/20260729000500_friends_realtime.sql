begin;

create or replace function private.friend_user_id_from_topic(p_topic text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when p_topic
      ~ '^friends:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    then split_part(p_topic, ':', 2)::uuid
    else null
  end;
$$;

create or replace function private.friend_presence_low_from_topic(p_topic text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when p_topic
      ~ '^friend-presence:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    then split_part(p_topic, ':', 2)::uuid
    else null
  end;
$$;

create or replace function private.friend_presence_high_from_topic(p_topic text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when p_topic
      ~ '^friend-presence:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    then split_part(p_topic, ':', 3)::uuid
    else null
  end;
$$;

create or replace function private.can_access_friend_presence_topic(
  p_topic text,
  p_user_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    p_user_id is not null
    and private.friend_presence_low_from_topic(p_topic) is not null
    and private.friend_presence_high_from_topic(p_topic) is not null
    and private.friend_presence_low_from_topic(p_topic)
      < private.friend_presence_high_from_topic(p_topic)
    and p_user_id in (
      private.friend_presence_low_from_topic(p_topic),
      private.friend_presence_high_from_topic(p_topic)
    )
    and private.are_friends(
      private.friend_presence_low_from_topic(p_topic),
      private.friend_presence_high_from_topic(p_topic)
    );
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
  select
    private.is_active_conversation_member(
      private.conversation_id_from_topic(p_topic),
      p_user_id
    )
    or private.notification_user_id_from_topic(p_topic) = p_user_id
    or private.friend_user_id_from_topic(p_topic) = p_user_id
    or private.can_access_friend_presence_topic(p_topic, p_user_id);
$$;

revoke all on function private.friend_user_id_from_topic(text) from public;
revoke all on function private.friend_presence_low_from_topic(text) from public;
revoke all on function private.friend_presence_high_from_topic(text) from public;
revoke all on function private.can_access_friend_presence_topic(text, uuid) from public;
grant execute on function private.friend_user_id_from_topic(text) to authenticated;
grant execute on function private.friend_presence_low_from_topic(text) to authenticated;
grant execute on function private.friend_presence_high_from_topic(text) to authenticated;
grant execute on function private.can_access_friend_presence_topic(text, uuid) to authenticated;

create or replace function private.broadcast_friendship_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_low uuid;
  v_user_high uuid;
begin
  v_user_low := case when tg_op = 'DELETE' then old.user_low else new.user_low end;
  v_user_high := case when tg_op = 'DELETE' then old.user_high else new.user_high end;

  perform realtime.broadcast_changes(
    'friends:' || v_user_low::text,
    tg_op,
    tg_op,
    tg_table_name,
    tg_table_schema,
    new,
    old
  );
  perform realtime.broadcast_changes(
    'friends:' || v_user_high::text,
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

create or replace function private.broadcast_friend_request_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_requester_id uuid;
  v_addressee_id uuid;
begin
  v_requester_id := case
    when tg_op = 'DELETE' then old.requester_id
    else new.requester_id
  end;
  v_addressee_id := case
    when tg_op = 'DELETE' then old.addressee_id
    else new.addressee_id
  end;

  perform realtime.broadcast_changes(
    'friends:' || v_requester_id::text,
    tg_op,
    tg_op,
    tg_table_name,
    tg_table_schema,
    new,
    old
  );
  perform realtime.broadcast_changes(
    'friends:' || v_addressee_id::text,
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

revoke all on function private.broadcast_friendship_change() from public;
revoke all on function private.broadcast_friend_request_change() from public;

create trigger friendships_broadcast_change
after insert or update or delete on public.friendships
for each row execute function private.broadcast_friendship_change();

create trigger friend_requests_broadcast_change
after insert or update or delete on public.friend_requests
for each row execute function private.broadcast_friend_request_change();

commit;
