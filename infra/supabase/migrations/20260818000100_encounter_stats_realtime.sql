begin;

create or replace function private.encounter_user_id_from_topic(p_topic text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when p_topic
      ~ '^encounters:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    then split_part(p_topic, ':', 2)::uuid
    else null
  end;
$$;

revoke all on function private.encounter_user_id_from_topic(text) from public;
grant execute on function private.encounter_user_id_from_topic(text) to authenticated;

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
    private.is_active_conversation_member(
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

create or replace function private.broadcast_encounter_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform realtime.broadcast_changes(
    'encounters:' || new.user_low::text,
    tg_op,
    tg_op,
    tg_table_name,
    tg_table_schema,
    new,
    old
  );
  perform realtime.broadcast_changes(
    'encounters:' || new.user_high::text,
    tg_op,
    tg_op,
    tg_table_name,
    tg_table_schema,
    new,
    old
  );
  return new;
end;
$$;

revoke all on function private.broadcast_encounter_change() from public;

create trigger nearby_encounters_broadcast_change
after insert or update on public.nearby_encounters
for each row execute function private.broadcast_encounter_change();

comment on trigger nearby_encounters_broadcast_change on public.nearby_encounters is
  'Pings both participants'' private encounters:<user_id> topics on every encounter insert/confirmation so the app refreshes its encounter and World Tour tallies without polling.';

commit;
