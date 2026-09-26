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

  delete from storage.objects as object
  where object.bucket_id = 'avatars'
    and private.avatar_owner_id(object.name) = v_actor_id;

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

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

comment on function public.delete_my_account() is
  'Irreversibly removes the calling account: avatars, authored messages, conversations it belongs to, friendships, and the auth user. Dependent rows cascade from public.profiles.';

commit;
