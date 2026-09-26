begin;

-- Block Messages also refuses new friend requests, regardless of whether they
-- originate in Boards, another PocketPass screen, or the connected-app API.
create function private.enforce_friend_request_message_privacy()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare v_blocked boolean;
begin
  if new.status <> 'pending' then return new; end if;
  if tg_op = 'UPDATE' then
    if old.status = 'pending' and old.addressee_id = new.addressee_id then
      return new;
    end if;
  end if;

  -- Serialize new requests with the recipient changing their preference.
  select profile.block_messages into v_blocked
  from public.profiles as profile
  where profile.user_id = new.addressee_id
  for share;
  if v_blocked then
    raise exception 'This person has Block all messages turned on and cannot receive friend requests.'
      using errcode = '42501', hint = 'FRIEND_REQUESTS_BLOCKED';
  end if;
  return new;
end;
$$;
revoke all on function private.enforce_friend_request_message_privacy()
  from public, anon, authenticated;

create trigger friend_requests_enforce_message_privacy
before insert or update of addressee_id, status on public.friend_requests
for each row execute function private.enforce_friend_request_message_privacy();

-- Connected apps receive the same actionable 403 as the other privacy gates.
create or replace function private.api_failure(p_state text,p_message text,p_hint text) returns jsonb
language plpgsql set search_path='' as $$
declare translated jsonb;
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
  return private.api_error(translated->>'code',translated->>'message',translated->>'hint');
end $$;

commit;
