begin;

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  delete from public.messages as message
  where message.sender_id = v_actor_id;

  delete from public.conversations as conversation
  where conversation.created_by = v_actor_id
    or conversation.direct_user_low = v_actor_id
    or conversation.direct_user_high = v_actor_id;

  delete from public.conversations as conversation
  where exists (
    select 1
    from public.conversation_members as member
    where member.conversation_id = conversation.id
      and member.user_id = v_actor_id
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
  'Irreversibly removes the calling account and its rows. Callers must delete stored avatars through the Storage API first: storage.protect_delete blocks direct deletes, and bypassing it would orphan the backing files.';

commit;
