begin;

alter table public.profiles add column block_messages boolean not null default false;

create function public.set_message_privacy(p_blocked boolean, p_account_id uuid default auth.uid())
returns public.profiles
language plpgsql security definer set search_path = ''
as $$
declare v_profile public.profiles;
begin
  if auth.uid() is null or p_account_id is distinct from auth.uid()
    or coalesce(auth.jwt()->>'client_id', '') <> '' then
    raise exception 'A PocketPass account is required' using errcode = '42501';
  end if;
  if p_blocked is null then raise exception 'A blocking preference is required' using errcode = '22004'; end if;
  update public.profiles set block_messages = p_blocked
    where user_id = auth.uid() returning * into v_profile;
  if not found then raise exception 'Profile not found' using errcode = 'P0002'; end if;
  return v_profile;
end;
$$;
revoke all on function public.set_message_privacy(boolean, uuid) from public, anon;
grant execute on function public.set_message_privacy(boolean, uuid) to authenticated;

-- Enforce at the write boundary for app RPCs, connected apps and attachments alike.
create function private.enforce_message_privacy()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare v_blocked boolean;
begin
  if tg_op = 'UPDATE' then
    if new.body is not distinct from old.body and new.metadata is not distinct from old.metadata then return new; end if;
    if new.deleted_at is not null then return new; end if;
  end if;
  if not exists (select 1 from public.conversations where id = new.conversation_id and kind = 'direct') then
    return new;
  end if;
  -- Share locks serialize delivery with the recipient changing this preference.
  for v_blocked in
    select profile.block_messages from public.profiles profile
    join public.conversation_members member on member.user_id = profile.user_id
    where member.conversation_id = new.conversation_id and member.left_at is null
      and member.user_id <> new.sender_id
    order by profile.user_id for share of profile
  loop
    if v_blocked then
      raise exception 'This person has Block all messages turned on.'
        using errcode = '42501', hint = 'DIRECT_MESSAGES_BLOCKED';
    end if;
  end loop;
  return new;
end;
$$;
revoke all on function private.enforce_message_privacy() from public, anon, authenticated;
create trigger messages_enforce_privacy before insert or update on public.messages
for each row execute function private.enforce_message_privacy();

create function private.enforce_group_message_privacy()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare v_blocked boolean;
begin
  if new.left_at is not null then return new; end if;
  if tg_op = 'UPDATE' then
    if old.left_at is null and new.user_id = old.user_id and new.conversation_id = old.conversation_id then
      return new;
    end if;
  end if;
  if not exists (select 1 from public.conversations where id = new.conversation_id and kind = 'group') then
    return new;
  end if;
  -- Choosing to create/join a group yourself is allowed; others cannot add you.
  if new.user_id = auth.uid() then return new; end if;
  select block_messages into v_blocked from public.profiles where user_id = new.user_id for share;
  if v_blocked then
    raise exception 'This person has Block all messages turned on and can''t be added to groups.'
      using errcode = '42501', hint = 'GROUP_MESSAGES_BLOCKED';
  end if;
  return new;
end;
$$;
revoke all on function private.enforce_group_message_privacy() from public, anon, authenticated;
create trigger conversation_members_enforce_privacy before insert or update on public.conversation_members
for each row execute function private.enforce_group_message_privacy();

-- Other devices already listen to the owner's friends topic to refresh the profile.
create function private.broadcast_message_privacy()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  perform realtime.send(jsonb_build_object('user_id', new.user_id), 'UPDATE', 'friends:' || new.user_id::text, true);
  return new;
end;
$$;
revoke all on function private.broadcast_message_privacy() from public, anon, authenticated;
create trigger profiles_message_privacy_changed after update of block_messages on public.profiles
for each row when (old.block_messages is distinct from new.block_messages)
execute function private.broadcast_message_privacy();

notify pgrst, 'reload schema';
commit;
