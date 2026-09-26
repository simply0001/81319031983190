begin;

alter table public.profiles add column block_invites boolean not null default false;
update public.profiles set block_invites=block_messages where block_messages;

create function public.set_invite_privacy(p_blocked boolean, p_account_id uuid default auth.uid())
returns public.profiles language plpgsql security definer set search_path='' as $$
declare v_profile public.profiles;
begin
  if auth.uid() is null or p_account_id is distinct from auth.uid()
    or coalesce(auth.jwt()->>'client_id','')<>'' then
    raise exception 'A PocketPass account is required' using errcode='42501';
  end if;
  if p_blocked is null then raise exception 'A blocking preference is required' using errcode='22004'; end if;
  update public.profiles set block_invites=p_blocked where user_id=auth.uid() returning * into v_profile;
  if not found then raise exception 'Profile not found' using errcode='P0002'; end if;
  return v_profile;
end $$;
revoke all on function public.set_invite_privacy(boolean,uuid) from public,anon;
grant execute on function public.set_invite_privacy(boolean,uuid) to authenticated;

create or replace function private.enforce_group_message_privacy()
returns trigger language plpgsql security definer set search_path='' as $$
declare v_blocked boolean;
begin
  if new.left_at is not null then return new; end if;
  if tg_op='UPDATE' and old.left_at is null and new.user_id=old.user_id and new.conversation_id=old.conversation_id then return new; end if;
  if not exists (select 1 from public.conversations where id=new.conversation_id and kind='group') then return new; end if;
  if new.user_id=auth.uid() then return new; end if;
  select block_invites into v_blocked from public.profiles where user_id=new.user_id for share;
  if v_blocked then
    raise exception 'This person is not accepting group invitations.' using errcode='42501',hint='GROUP_MESSAGES_BLOCKED';
  end if;
  return new;
end $$;

do $$
declare definition text;
begin
  definition:=pg_get_functiondef('private.enforce_message_privacy()'::regprocedure);
  if position('This person has Block all messages turned on.' in definition)=0 then
    raise exception 'Direct-message privacy guard changed';
  end if;
  execute replace(definition,'This person has Block all messages turned on.','This person is not accepting direct messages.');
end $$;

create or replace function private.enforce_friend_request_message_privacy()
returns trigger language plpgsql security definer set search_path='' as $$
declare v_blocked boolean;
begin
  if new.status<>'pending' then return new; end if;
  if tg_op='UPDATE' and old.status='pending' and old.addressee_id=new.addressee_id then return new; end if;
  select profile.block_invites into v_blocked from public.profiles profile where profile.user_id=new.addressee_id for share;
  if v_blocked then
    raise exception 'This person is not accepting friend requests.' using errcode='42501',hint='FRIEND_REQUESTS_BLOCKED';
  end if;
  return new;
end $$;

create function private.enforce_board_invite_privacy() returns trigger
language plpgsql security definer set search_path='' as $$
begin
  if new.recipient_id is null or new.revoked or new.accepted_at is not null then return new; end if;
  if tg_op='UPDATE' and old.recipient_id is not distinct from new.recipient_id and old.revoked is not distinct from new.revoked then return new; end if;
  if (select block_invites from public.profiles where user_id=new.recipient_id for share) then
    raise exception 'This person is not accepting Board invitations.' using errcode='42501',hint='BOARD_INVITATIONS_BLOCKED';
  end if;
  return new;
end $$;
revoke all on function private.enforce_board_invite_privacy() from public,anon,authenticated;
create trigger board_invitations_enforce_privacy before insert or update of recipient_id,revoked on private.board_invitations
for each row execute function private.enforce_board_invite_privacy();

do $$
declare definition text;
  previous text := 'if (select block_messages from public.profiles where user_id=target for share) then raise exception ''This person has Block Messages turned on and cannot receive board invitations.''';
  replacement text := 'if (select block_invites from public.profiles where user_id=target for share) then raise exception ''This person is not accepting Board invitations.''';
begin
  definition:=pg_get_functiondef('public.boards_mutate(text,jsonb,uuid)'::regprocedure);
  if position(previous in definition)=0 then raise exception 'Board invitation privacy guard changed'; end if;
  execute replace(definition,previous,replacement);
end $$;

create trigger profiles_invite_privacy_changed after update of block_invites on public.profiles
for each row when (old.block_invites is distinct from new.block_invites)
execute function private.broadcast_message_privacy();

notify pgrst,'reload schema';
commit;
