begin;

create or replace function private.token_user_id_from_topic(p_topic text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when p_topic
      ~ '^tokens:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    then split_part(p_topic, ':', 2)::uuid
    else null
  end;
$$;

revoke all on function private.token_user_id_from_topic(text) from public;
grant execute on function private.token_user_id_from_topic(text) to authenticated;

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
    or private.token_user_id_from_topic(p_topic) = p_user_id,
    false
  );
$$;

create or replace function private.broadcast_token_balance_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform realtime.broadcast_changes(
    'tokens:' || new.user_id::text,
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

revoke all on function private.broadcast_token_balance_change() from public;

create trigger token_balances_broadcast_change
after insert or update on public.token_balances
for each row execute function private.broadcast_token_balance_change();

comment on trigger token_balances_broadcast_change on public.token_balances is
  'Pushes every balance change to the owner''s private tokens:<user_id> topic so the app refreshes the cached balance without polling.';

commit;
