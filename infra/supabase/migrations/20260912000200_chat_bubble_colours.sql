begin;

alter table public.profiles add column chat_bubble_colour text not null default 'default'
  check (chat_bubble_colour in ('default', 'blue', 'purple', 'pink', 'red', 'orange', 'yellow', 'green', 'teal'));

create function public.set_chat_bubble_colour(p_colour text, p_client_operation_id uuid, p_account_id uuid default auth.uid())
returns public.profiles
language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_profile public.profiles;
  v_replay boolean;
  v_request jsonb := jsonb_build_object('colour', p_colour);
begin
  if v_user is null or p_account_id is distinct from v_user or coalesce(auth.jwt()->>'client_id', '') <> '' then
    raise exception 'A PocketPass account is required' using errcode = '42501';
  end if;
  if p_colour is null or p_colour not in ('default', 'blue', 'purple', 'pink', 'red', 'orange', 'yellow', 'green', 'teal') then
    raise exception 'Unknown chat colour' using errcode = '22023';
  end if;
  select * into v_profile from public.profiles where user_id = v_user for update;
  if not found then raise exception 'Profile not found' using errcode = 'P0002'; end if;
  select operation.is_replay into v_replay from private.begin_rpc_operation(
    v_user, p_client_operation_id, 'set_chat_bubble_colour', v_request
  ) as operation;
  if v_replay then return v_profile; end if;
  update public.profiles set chat_bubble_colour = p_colour where user_id = v_user returning * into v_profile;
  perform private.finish_rpc_operation(v_user, p_client_operation_id, 'set_chat_bubble_colour', v_request, '{}'::jsonb);
  return v_profile;
end;
$$;
revoke all on function public.set_chat_bubble_colour(text, uuid, uuid) from public, anon;
grant execute on function public.set_chat_bubble_colour(text, uuid, uuid) to authenticated;

create function private.broadcast_chat_bubble_colour()
returns trigger
language plpgsql security definer set search_path = ''
as $$
declare v_conversation uuid;
begin
  perform realtime.send(jsonb_build_object('user_id', new.user_id), 'UPDATE', 'friends:' || new.user_id::text, true);
  for v_conversation in
    select conversation_id from public.conversation_members where user_id = new.user_id and left_at is null
    union
    select conversation_id from public.messages where sender_id = new.user_id
  loop
    perform realtime.send(jsonb_build_object(
      'operation', 'UPDATE', 'schema', 'public', 'table', 'profile_chat_colours',
      'record', jsonb_build_object('user_id', new.user_id, 'conversation_id', v_conversation)
    ), 'chat_colour', 'conversation:' || v_conversation::text, true);
  end loop;
  return new;
end;
$$;
revoke all on function private.broadcast_chat_bubble_colour() from public, anon, authenticated;
create trigger profiles_chat_bubble_colour_changed after update of chat_bubble_colour on public.profiles
for each row when (old.chat_bubble_colour is distinct from new.chat_bubble_colour)
execute function private.broadcast_chat_bubble_colour();

notify pgrst, 'reload schema';
commit;
