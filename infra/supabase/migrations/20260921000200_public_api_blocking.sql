begin;

-- Existing message/group endpoints surface the privacy trigger's reason too.
create or replace function private.api_failure(p_state text,p_message text,p_hint text) returns jsonb
language plpgsql set search_path='' as $$
declare translated jsonb;
begin
  if p_state='42501' and p_hint in ('DIRECT_MESSAGES_BLOCKED','GROUP_MESSAGES_BLOCKED','BOARD_INVITATIONS_BLOCKED') then
    return private.api_error('PT403',p_message,p_hint);
  end if;
  if p_state like 'PT%' and coalesce(p_hint,'')<>'' then return private.api_error(p_state,p_message,p_hint); end if;
  translated:=private.api_translate(p_state,p_message);
  return private.api_error(translated->>'code',translated->>'message',translated->>'hint');
end $$;

create function private.api_privacy_request(p_endpoint text,p_payload jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare
  guard jsonb:=private.api_guard(case p_endpoint when 'blocks.list' then 'blocks:read'
    when 'blocks.set' then 'blocks:write' when 'privacy.get' then 'privacy:read' when 'privacy.set' then 'privacy:write' end);
  args jsonb:=coalesce(p_payload,'{}'::jsonb); actor uuid; target uuid; operation uuid; blocked boolean;
  lim integer; cursor_ts timestamptz; cursor_id uuid; items jsonb; response jsonb; replay boolean;
  state text; message text; hint text;
begin
  if guard ? 'code' then return guard; end if;
  actor:=(guard->>'user_id')::uuid;
  begin
    case p_endpoint
    when 'blocks.list' then
      perform private.api_reject_unknown(args,array['limit','cursor']);
      lim:=private.api_arg_int(args,'limit',30,1,100);
      if args->>'cursor' is not null then
        if length(args->>'cursor')>4096 then raise exception 'Invalid cursor' using errcode='22023'; end if;
        select ts,id into cursor_ts,cursor_id from private.api_decode_cursor(args->>'cursor');
      end if;
      select coalesce(jsonb_agg(to_jsonb(q) order by q.blocked_at desc,q.user_id desc),'[]') into items from
        (select b.blocked_id user_id,p.display_name,p.avatar_path,b.created_at blocked_at
          from public.user_blocks b join public.profiles p on p.user_id=b.blocked_id
          where b.blocker_id=actor and (cursor_ts is null or (b.created_at,b.blocked_id)<(cursor_ts,cursor_id))
          order by b.created_at desc,b.blocked_id desc limit lim+1) q;
      if jsonb_array_length(items)>lim then
        response:=jsonb_build_object('items',items-lim,'next_cursor',private.api_encode_cursor(
          (items->(lim-1)->>'blocked_at')::timestamptz,(items->(lim-1)->>'user_id')::uuid));
      else response:=jsonb_build_object('items',items,'next_cursor',null); end if;
      return response;
    when 'blocks.set' then
      perform private.api_reject_unknown(args,array['user_id','blocked','operation_id']);
      target:=private.api_arg_uuid(args,'user_id',true);
      operation:=private.api_arg_uuid(args,'operation_id',true);
      if jsonb_typeof(args->'blocked') is distinct from 'boolean' then raise exception 'blocked must be a boolean' using errcode='22023'; end if;
      blocked:=public.set_user_block(target,(args->>'blocked')::boolean,operation);
      return jsonb_build_object('user_id',target,'blocked',blocked);
    when 'privacy.get' then
      perform private.api_reject_unknown(args,'{}');
      return (select jsonb_build_object('block_messages',block_messages) from public.profiles where user_id=actor);
    when 'privacy.set' then
      perform private.api_reject_unknown(args,array['block_messages','operation_id']);
      operation:=private.api_arg_uuid(args,'operation_id',true);
      if jsonb_typeof(args->'block_messages') is distinct from 'boolean' then raise exception 'block_messages must be a boolean' using errcode='22023'; end if;
      select is_replay,response_payload into replay,response from private.begin_rpc_operation(actor,operation,'api_message_privacy',args-'operation_id');
      if replay then return response; end if;
      update public.profiles set block_messages=(args->>'block_messages')::boolean where user_id=actor;
      if not found then raise exception 'Profile not found' using errcode='P0002'; end if;
      response:=jsonb_build_object('block_messages',(args->>'block_messages')::boolean);
      perform private.finish_rpc_operation(actor,operation,'api_message_privacy',args-'operation_id',response);
      return response;
    else return private.api_error('PT404','Unknown endpoint','UNKNOWN_ENDPOINT');
    end case;
  exception when sqlstate '40001' or sqlstate '40P01' then raise;
    when others then get stacked diagnostics state=returned_sqlstate,message=message_text,hint=pg_exception_hint;
      return private.api_failure(state,message,hint);
  end;
end $$;
revoke all on function private.api_privacy_request(text,jsonb) from public,anon,authenticated,api_client,service_role;

create function public.api_v1_blocks_list(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_privacy_request('blocks.list',$1); $$;
create function public.api_v1_blocks_set(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_privacy_request('blocks.set',$1); $$;
create function public.api_v1_privacy_get(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_privacy_request('privacy.get',$1); $$;
create function public.api_v1_privacy_set(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_privacy_request('privacy.set',$1); $$;
revoke all on function public.api_v1_blocks_list(jsonb),public.api_v1_blocks_set(jsonb),public.api_v1_privacy_get(jsonb),public.api_v1_privacy_set(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_blocks_list(jsonb),public.api_v1_blocks_set(jsonb),public.api_v1_privacy_get(jsonb),public.api_v1_privacy_set(jsonb) to api_client;

-- Private Broadcast invalidations contain no content or member identities.
-- Existing topics continue to use the same policy and scope checks.
alter function private.api_can_access_realtime_topic(text) rename to api_can_access_realtime_topic_before_boards;
create function private.api_can_access_realtime_topic(p_topic text) returns boolean
language sql stable security definer set search_path='' as $$
  select coalesce(private.api_can_access_realtime_topic_before_boards(p_topic)
    or (p_topic='boards:'||auth.uid()::text and private.api_has_scope('boards:read'))
    or (p_topic='board-drafts:'||auth.uid()::text and private.api_has_scope('boards:drafts'))
    or (p_topic='blocks:'||auth.uid()::text and private.api_has_scope('blocks:read'))
    or (p_topic='privacy:'||auth.uid()::text and private.api_has_scope('privacy:read')),false);
$$;
revoke all on function private.api_can_access_realtime_topic(text),private.api_can_access_realtime_topic_before_boards(text) from public,anon,authenticated;
grant execute on function private.api_can_access_realtime_topic(text) to api_client;
-- Rebind the RLS policy: ALTER FUNCTION retains the old function OID.
alter policy pocketpass_api_realtime_read on realtime.messages
  using ((extension='broadcast' and private.api_can_access_realtime_topic(realtime.topic()))
    or (extension='presence' and private.api_can_read_presence_topic(realtime.topic())));

create or replace function private.board_invalidate(p_board uuid) returns void
language plpgsql security definer set search_path='' as $$
declare viewer uuid;
begin
  for viewer in select user_id from private.board_members where board_id=p_board
    union select user_id from private.board_viewers where board_id=p_board and seen_at>now()-interval '1 hour'
    union select auth.uid() where auth.uid() is not null loop
    -- The legacy notifications scope must not disclose private board IDs.
    perform realtime.send('{}'::jsonb,'BOARDS','notifications:'||viewer,true);
    perform realtime.send('{}'::jsonb,'BOARDS','boards:'||viewer,true);
  end loop;
end $$;

create or replace function private.board_block_changed() returns trigger
language plpgsql security definer set search_path='' as $$
declare a uuid; b uuid;
begin
  if tg_op='DELETE' then a:=old.blocker_id;b:=old.blocked_id;else a:=new.blocker_id;b:=new.blocked_id;end if;
  perform realtime.send('{}'::jsonb,'BOARDS','notifications:'||a,true);
  perform realtime.send('{}'::jsonb,'BOARDS','notifications:'||b,true);
  perform realtime.send('{}'::jsonb,'BOARDS','boards:'||a,true);
  perform realtime.send('{}'::jsonb,'BOARDS','boards:'||b,true);
  perform realtime.send('{}'::jsonb,'BLOCKS','blocks:'||a,true);
  return null;
end $$;

-- Invitations/restrictions also notify the legacy first-party listener. Keep
-- its payload generic, since notifications:read is independent of boards:read.
do $migration$
declare definition text:=pg_get_functiondef('public.boards_mutate(text,jsonb,uuid)'::regprocedure);
begin
  definition:=replace(definition,'realtime.send(jsonb_build_object(''board_id'',bid),''BOARDS''',
    'realtime.send(''{}''::jsonb,''BOARDS''');
  execute definition;
end $migration$;

create function private.api_board_account_change() returns trigger
language plpgsql security definer set search_path='' as $$
declare recipient uuid;
begin
  if tg_table_name='board_invitations' then recipient:=coalesce(new.recipient_id,old.recipient_id);
  elsif tg_table_name='board_restrictions' then recipient:=coalesce(new.user_id,old.user_id);
  elsif tg_table_name='board_proposals' then recipient:=coalesce(new.author_id,old.author_id);
  elsif tg_table_name='board_draft_versions' then recipient:=coalesce(new.user_id,old.user_id);
  elsif tg_table_name in ('board_preferences','board_stationery_owned') then recipient:=coalesce(new.user_id,old.user_id);
  else recipient:=coalesce(new.recipient_id,old.recipient_id); end if;
  if recipient is not null then
    if tg_table_name='board_draft_versions' then perform realtime.send('{}'::jsonb,'DRAFTS','board-drafts:'||recipient,true);
    else perform realtime.send('{}'::jsonb,'BOARDS','boards:'||recipient,true); end if;
  end if;
  return null;
end $$;
create trigger board_invitations_api_change after insert or update or delete on private.board_invitations for each row execute function private.api_board_account_change();
create trigger board_restrictions_api_change after insert or update or delete on private.board_restrictions for each row execute function private.api_board_account_change();
create trigger board_proposals_api_change after insert or update or delete on private.board_proposals for each row execute function private.api_board_account_change();
create trigger board_drafts_api_change after insert or update or delete on private.board_draft_versions for each row execute function private.api_board_account_change();
create trigger board_events_api_change after insert or update or delete on private.board_events for each row execute function private.api_board_account_change();
create trigger board_preferences_api_change after insert or update or delete on private.board_preferences for each row execute function private.api_board_account_change();
create trigger board_owned_stationery_api_change after insert or update or delete on private.board_stationery_owned for each row execute function private.api_board_account_change();
revoke all on function private.api_board_account_change() from public,anon,authenticated,api_client,service_role;

-- Global feature/catalogue changes also reach readers who have not joined a
-- board. This is an invalidation, not a push notification or data broadcast.
create function private.api_board_catalogue_change() returns trigger
language plpgsql security definer set search_path='' as $$
declare recipient uuid;
begin
  for recipient in select distinct c.user_id from auth.oauth_consents c join private.developer_apps a on a.client_id=c.client_id
    where c.revoked_at is null and c.granted_at>=a.scopes_changed_at and a.status='active' and 'boards:read'=any(a.scopes) loop
    perform realtime.send('{}'::jsonb,'BOARDS','boards:'||recipient,true);
  end loop;
  return null;
end $$;
create trigger board_settings_api_change after update on private.board_settings for each statement execute function private.api_board_catalogue_change();
create trigger board_stationery_api_change after insert or update or delete on private.board_stationery for each statement execute function private.api_board_catalogue_change();
revoke all on function private.api_board_catalogue_change() from public,anon,authenticated,api_client,service_role;

create function private.api_privacy_changed() returns trigger
language plpgsql security definer set search_path='' as $$
begin perform realtime.send('{}'::jsonb,'PRIVACY','privacy:'||new.user_id,true);return null;end $$;
create trigger profiles_api_privacy_change after update of block_messages on public.profiles
  for each row when (old.block_messages is distinct from new.block_messages) execute function private.api_privacy_changed();
revoke all on function private.api_privacy_changed() from public,anon,authenticated,api_client,service_role;

notify pgrst,'reload schema';
commit;
