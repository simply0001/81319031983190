begin;

-- Explicit OAuth opt-in. Existing apps and grants receive no additional scopes.
create or replace function private.api_scope_keys() returns text[]
language sql immutable set search_path='' as $$
  select array['profile:read','friends:read','friends:write','messages:read','messages:write','groups:write','notifications:read','presence:read','presence:write','tokens:read','encounters:read','puzzles:read','boards:read','boards:write','boards:membership','boards:invite','boards:manage','boards:moderate','boards:drafts','boards:purchase','blocks:read','blocks:write','privacy:read','privacy:write' ]::text[];
$$;
create or replace function private.api_scope_descriptions() returns jsonb
language sql immutable set search_path='' as $$
  select '{"profile:read":"See your profile (name, bio, avatar, age, country)","friends:read":"See your friends list and friend requests","friends:write":"Add and remove friends and answer friend requests as you","messages:read":"Read your conversations and messages","messages:write":"Send, edit and delete messages as you","groups:write":"Create group chats and manage their members as you","notifications:read":"See and clear your notifications","presence:read":"See which of your friends are online and who is active in your chats","presence:write":"Show you as online and typing to your friends","tokens:read":"See your token balance and supporter status","encounters:read":"See the people you have met nearby","puzzles:read":"See your Puzzle Swap progress","boards:read":"Read public boards and your private boards, notes, drawings, members, invitations and activity","boards:write":"Publish, edit and remove your notes and replies, react, report content and submit appeals as you","boards:membership":"Join and leave boards, accept invitations and ownership offers, request boards and change board notifications","boards:invite":"Invite people to your boards and create or revoke invitation codes as you","boards:manage":"Change your boards, artwork, moderators and ownership, and archive or reopen them","boards:moderate":"Review your boards'' reports and retained content, manage membership requests, and mute or ban members as a board moderator","boards:drafts":"Read, save and discard your private cloud note drafts","boards:purchase":"Spend your PocketPass tokens on permanent stationery purchases","blocks:read":"See the accounts you have blocked","blocks:write":"Block and unblock accounts as you","privacy:read":"See whether you have Block Messages enabled","privacy:write":"Enable or disable Block Messages on your account"}'::jsonb;
$$;

-- A connected app never inherits the account owner's central dashboard powers.
create or replace function private.has_permission(p_permission text) returns boolean
language sql stable security definer set search_path='' as $$
  select coalesce(auth.jwt()->>'client_id','')='' and exists(
    select 1 from private.admin_users a where a.user_id=auth.uid()
      and (a.is_owner or p_permission=any(a.permissions)));
$$;

-- The API wrappers below are the only api_client grants into Boards. Native
-- RPC grants remain unchanged; sharing rules must not require impersonating a
-- first-party JWT or granting connected apps access to private tables.
create or replace function private.board_actor(p_require_enabled boolean default true) returns uuid
language plpgsql stable security definer set search_path='' as $$
begin
  if auth.uid() is null or coalesce((auth.jwt()->>'is_anonymous')::boolean,false)
    or (coalesce(auth.jwt()->>'client_id','')<>'' and not private.api_has_scope(null)) then
    raise exception 'A PocketPass account is required' using errcode='42501';
  end if;
  if p_require_enabled and not(select enabled from private.board_settings where singleton=true) then
    raise exception 'Boards are temporarily unavailable. Your notes are safe.' using errcode='42501',hint='BOARDS_DISABLED';
  end if;
  return auth.uid();
end $$;

create function private.api_board_contract() returns jsonb
language sql immutable set search_path='' as $contract$
  select '{"settings":{"operation":"settings","mutation":false,"scope":"boards:read","fields":[],"required":[],"page":""},"list":{"operation":"directory","mutation":false,"scope":"boards:read","fields":["scope","search","limit","cursor"],"required":[],"page":"cursor"},"get":{"operation":"board","mutation":false,"scope":"boards:read","fields":["board_id","review"],"required":["board_id"],"page":""},"feed":{"operation":"feed","mutation":false,"scope":"boards:read","fields":["board_id","sort","period","limit","cursor","review"],"required":["board_id"],"page":"cursor"},"replies":{"operation":"replies","mutation":false,"scope":"boards:read","fields":["post_id","limit","cursor","review"],"required":["post_id"],"page":"cursor"},"post":{"operation":"post","mutation":false,"scope":"boards:read","fields":["post_id","reveal","review"],"required":["post_id"],"page":""},"preview":{"operation":"preview","mutation":false,"scope":"boards:read","fields":["post_id","reveal","review"],"required":["post_id"],"page":""},"asset":{"operation":"asset","mutation":false,"scope":"boards:read","fields":["asset_id"],"required":["asset_id"],"page":""},"branding_preview":{"operation":"branding_preview","mutation":false,"scope":"boards:read","fields":["board_id","kind"],"required":["board_id","kind"],"page":""},"drafts":{"operation":"drafts","mutation":false,"scope":"boards:drafts","fields":["board_id","limit","cursor"],"required":[],"page":"cursor"},"inbox":{"operation":"inbox","mutation":false,"scope":"boards:read","fields":["limit","cursor"],"required":[],"page":"cursor"},"invitations":{"operation":"invitations","mutation":false,"scope":"boards:read","fields":["limit","cursor"],"required":[],"page":"array"},"proposals":{"operation":"proposals","mutation":false,"scope":"boards:read","fields":["limit","cursor"],"required":[],"page":"array"},"notices":{"operation":"notices","mutation":false,"scope":"boards:read","fields":["limit","cursor"],"required":[],"page":"offset"},"members":{"operation":"members","mutation":false,"scope":"boards:read","fields":["board_id","limit","cursor","review"],"required":["board_id"],"page":"cursor"},"management":{"operation":"management","mutation":false,"scope":"boards:moderate","fields":["board_id","limit","cursor"],"required":["board_id"],"page":"offset"},"outgoing_invitations":{"operation":"management","mutation":false,"scope":"boards:invite","fields":["board_id","limit","cursor"],"required":["board_id"],"page":"offset"},"history":{"operation":"history","mutation":false,"scope":"boards:moderate","fields":["board_id","post_id","limit","cursor"],"required":["board_id"],"page":"array"},"stationery":{"operation":"stationery","mutation":false,"scope":"boards:read","fields":["limit","cursor"],"required":[],"page":"array"},"propose":{"operation":"propose","mutation":true,"scope":"boards:membership","fields":["id","name","description","rules","visibility","operation_id"],"required":["name","visibility","operation_id"],"page":""},"join":{"operation":"join","mutation":true,"scope":"boards:membership","fields":["board_id","operation_id"],"required":["board_id","operation_id"],"page":""},"accept_invitation":{"operation":"accept_invitation","mutation":true,"scope":"boards:membership","fields":["id","operation_id"],"required":["id","operation_id"],"page":""},"join_code":{"operation":"join_code","mutation":true,"scope":"boards:membership","fields":["code","operation_id"],"required":["code","operation_id"],"page":""},"decline_invitation":{"operation":"revoke_invitation","mutation":true,"scope":"boards:membership","fields":["board_id","id","operation_id"],"required":["board_id","id","operation_id"],"page":""},"decide_join":{"operation":"decide_join","mutation":true,"scope":"boards:moderate","fields":["board_id","user_id","approve","operation_id"],"required":["board_id","user_id","approve","operation_id"],"page":""},"invite":{"operation":"invite","mutation":true,"scope":"boards:invite","fields":["board_id","user_id","id","operation_id"],"required":["board_id","user_id","operation_id"],"page":""},"create_code":{"operation":"create_code","mutation":true,"scope":"boards:invite","fields":["board_id","id","operation_id"],"required":["board_id","operation_id"],"page":""},"revoke_invitation":{"operation":"revoke_invitation","mutation":true,"scope":"boards:invite","fields":["board_id","id","operation_id"],"required":["board_id","id","operation_id"],"page":""},"leave":{"operation":"leave","mutation":true,"scope":"boards:membership","fields":["board_id","operation_id"],"required":["board_id","operation_id"],"page":""},"preferences":{"operation":"preferences","mutation":true,"scope":"boards:membership","fields":["board_id","muted","push_enabled","operation_id"],"required":["board_id","operation_id"],"page":""},"push_preference":{"operation":"push_preference","mutation":true,"scope":"boards:membership","fields":["enabled","operation_id"],"required":["enabled","operation_id"],"page":""},"update_board":{"operation":"update_board","mutation":true,"scope":"boards:manage","fields":["board_id","name","description","rules","visibility","accent","join_policy","code_policy","members_can_invite","operation_id"],"required":["board_id","operation_id"],"page":""},"archive":{"operation":"archive","mutation":true,"scope":"boards:manage","fields":["board_id","archived","operation_id"],"required":["board_id","archived","operation_id"],"page":""},"transfer":{"operation":"transfer","mutation":true,"scope":"boards:manage","fields":["board_id","user_id","operation_id"],"required":["board_id","user_id","operation_id"],"page":""},"set_moderator":{"operation":"set_moderator","mutation":true,"scope":"boards:manage","fields":["board_id","user_id","moderator","operation_id"],"required":["board_id","user_id","moderator","operation_id"],"page":""},"accept_transfer":{"operation":"accept_transfer","mutation":true,"scope":"boards:membership","fields":["board_id","operation_id"],"required":["board_id","operation_id"],"page":""},"publish":{"operation":"publish","mutation":true,"scope":"boards:write","fields":["board_id","id","thread_id","reply_to","body","drawing","stationery_id","spoiler","draft_id","draft_revision_id","draft_base_id","operation_id"],"required":["board_id","operation_id"],"page":""},"edit":{"operation":"edit","mutation":true,"scope":"boards:write","fields":["board_id","post_id","body","operation_id"],"required":["board_id","post_id","body","operation_id"],"page":""},"delete_post":{"operation":"delete_post","mutation":true,"scope":"boards:write","fields":["board_id","post_id","reason","review","operation_id"],"required":["board_id","post_id","operation_id"],"page":""},"spoiler":{"operation":"spoiler","mutation":true,"scope":"boards:write","fields":["board_id","post_id","spoiler","reason","review","operation_id"],"required":["board_id","post_id","spoiler","operation_id"],"page":""},"react":{"operation":"react","mutation":true,"scope":"boards:write","fields":["board_id","post_id","yeah","operation_id"],"required":["board_id","post_id","yeah","operation_id"],"page":""},"draw_branding":{"operation":"draw_branding","mutation":true,"scope":"boards:manage","fields":["board_id","kind","drawing","draft_id","draft_revision_id","draft_base_id","operation_id"],"required":["board_id","kind","drawing","operation_id"],"page":""},"save_draft":{"operation":"save_draft","mutation":true,"scope":"boards:drafts","fields":["board_id","draft_id","revision_id","base_id","payload","operation_id"],"required":["board_id","draft_id","revision_id","payload","operation_id"],"page":""},"discard_draft":{"operation":"discard_draft","mutation":true,"scope":"boards:drafts","fields":["revision_id","operation_id"],"required":["revision_id","operation_id"],"page":""},"report":{"operation":"report","mutation":true,"scope":"boards:write","fields":["board_id","post_id","reason","id","operation_id"],"required":["board_id","reason","operation_id"],"page":""},"resolve_report":{"operation":"resolve_report","mutation":true,"scope":"boards:moderate","fields":["board_id","id","reason","status","operation_id"],"required":["board_id","id","reason","operation_id"],"page":""},"restrict":{"operation":"restrict","mutation":true,"scope":"boards:moderate","fields":["board_id","user_id","kind","reason","expires_at","id","operation_id"],"required":["board_id","user_id","kind","reason","operation_id"],"page":""},"revoke_restriction":{"operation":"revoke_restriction","mutation":true,"scope":"boards:moderate","fields":["board_id","id","operation_id"],"required":["board_id","id","operation_id"],"page":""},"appeal":{"operation":"appeal","mutation":true,"scope":"boards:write","fields":["board_id","case_id","reason","id","operation_id"],"required":["board_id","case_id","reason","operation_id"],"page":""},"resolve_appeal":{"operation":"resolve_appeal","mutation":true,"scope":"boards:moderate","fields":["board_id","id","reason","status","operation_id"],"required":["board_id","id","reason","operation_id"],"page":""},"read_event":{"operation":"read_event","mutation":true,"scope":"boards:membership","fields":["id","operation_id"],"required":["id","operation_id"],"page":""},"read_thread":{"operation":"read_thread","mutation":true,"scope":"boards:membership","fields":["board_id","thread_id","operation_id"],"required":["board_id","operation_id"],"page":""},"buy_stationery":{"operation":"buy_stationery","mutation":true,"scope":"boards:purchase","fields":["stationery_id","operation_id"],"required":["stationery_id","operation_id"],"page":""}}'::jsonb;
$contract$;

create function private.api_boards_failure(p_state text,p_message text,p_hint text) returns jsonb
language plpgsql set search_path='' as $$
begin
  if p_hint='BOARD_RATE_LIMIT' then
    perform set_config('response.headers',(coalesce(nullif(current_setting('response.headers',true),'')::jsonb,'[]')||
      jsonb_build_array(jsonb_build_object('Retry-After','60')))::text,true);
    return private.api_error('PT429',p_message,p_hint);
  end if;
  if p_state='42501' then return private.api_error('PT403',p_message,coalesce(nullif(p_hint,''),'BOARD_ACCESS_DENIED')); end if;
  if p_state in ('22023','22004') and (p_message ilike '%operation ID%already used%' or p_message ilike '%Upload ID%already used%') then
    return private.api_error('PT409',p_message,'DUPLICATE_OPERATION_ID');
  end if;
  if p_state like '22%' or p_state in ('23502','23503','23514') then
    return private.api_error('PT400',case when p_state like '22%' then p_message else 'Invalid board data' end,
      coalesce(nullif(p_hint,''),'INVALID_FIELD'));
  end if;
  if p_state='23505' then return private.api_error('PT409','This item already exists','ALREADY_EXISTS'); end if;
  return private.api_failure(p_state,p_message,p_hint);
end $$;

-- Opaque cursors are tied to an endpoint and its filters, never to a table name
-- or an executable expression. A cursor carries no authority.
create function private.api_boards_cursor(p_endpoint text,p_args jsonb,p_position jsonb) returns text
language sql immutable set search_path='' as $$
  select case when p_position is null or p_position='null'::jsonb then null else
    replace(encode(convert_to(jsonb_build_object('v',1,'endpoint',p_endpoint,
      'filter',md5((coalesce(p_args,'{}'::jsonb)-'cursor'-'limit'-'offset')::text),'position',p_position)::text,'UTF8'),'base64'),E'\n','') end;
$$;

-- These first-party queries return whole collections. Bound them before
-- materializing JSON for connected apps, with deterministic ordering.
create function private.api_boards_collection(p_operation text,p_args jsonb,p_limit integer,p_offset integer) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); bid uuid:=(p_args->>'board_id')::uuid; pid uuid:=(p_args->>'post_id')::uuid; result jsonb;
begin
  case p_operation
  when 'invitations' then
    select coalesce(jsonb_agg(to_jsonb(q) order by q.created_at desc,q.id desc),'[]') into result from
      (select i.id,i.board_id,b.name board_name,i.inviter_id,i.created_at from private.board_invitations i
        join private.boards b on b.id=i.board_id where i.recipient_id=actor and not i.revoked
          and i.accepted_at is null and not b.archived and not private.board_blocked(actor,i.inviter_id)
          and not private.board_restricted(i.board_id,actor,true)
        order by i.created_at desc,i.id desc limit p_limit offset p_offset) q;
  when 'proposals' then
    select coalesce(jsonb_agg(to_jsonb(q) order by q.created_at desc,q.id desc),'[]') into result from
      (select * from private.board_proposals where author_id=actor order by created_at desc,id desc limit p_limit offset p_offset) q;
  when 'history' then
    perform private.board_require_moderator(bid);
    if pid is not null and not exists(select 1 from private.board_posts where id=pid and board_id=bid) then
      raise exception 'Post unavailable' using errcode='42501';
    end if;
    insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'history_review',false);
    select coalesce(jsonb_agg(to_jsonb(q) order by q.expires_at desc,q.id desc),'[]') into result from
      (select * from private.board_history where board_id=bid and (pid is null or post_id=pid)
        order by expires_at desc,id desc limit p_limit offset p_offset) q;
  when 'stationery' then
    select coalesce(jsonb_agg(to_jsonb(q) order by q.id),'[]') into result from
      (select s.*,private.board_stationery_allowed(actor,s.id) available,
        exists(select 1 from private.board_stationery_owned where user_id=actor and stationery_id=s.id) owned
        from private.board_stationery s where active or exists(select 1 from private.board_stationery_owned where user_id=actor and stationery_id=s.id)
        order by s.id limit p_limit offset p_offset) q;
  when 'notices' then
    result:=jsonb_build_object(
      'restrictions',(select coalesce(jsonb_agg(to_jsonb(q) order by q.created_at desc,q.id desc),'[]') from
        (select * from private.board_restrictions where user_id=actor order by created_at desc,id desc limit p_limit offset p_offset) q),
      'actions',(select coalesce(jsonb_agg(to_jsonb(q) order by q.created_at desc,q.id desc),'[]') from
        (select * from private.board_audit where subject_id=actor and action in ('delete_post','spoiler') order by created_at desc,id desc limit p_limit offset p_offset) q),
      'appeals',(select coalesce(jsonb_agg(to_jsonb(q) order by q.created_at desc,q.id desc),'[]') from
        (select * from private.board_appeals where user_id=actor order by created_at desc,id desc limit p_limit offset p_offset) q));
    result:=result||jsonb_build_object('next_offset',case when exists(select 1 from jsonb_each(result) where jsonb_array_length(value)=p_limit) then p_offset+p_limit end);
  else raise exception 'Unknown collection' using errcode='22023';
  end case;
  return result;
end $$;
revoke all on function private.api_boards_collection(text,jsonb,integer,integer) from public,anon,authenticated,api_client,service_role;

create function private.api_boards_request(p_endpoint text,p_payload jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare
  spec jsonb:=private.api_board_contract()->p_endpoint;
  guard jsonb; args jsonb:=coalesce(p_payload,'{}'::jsonb); response jsonb; position jsonb;
  operation text; allowed text[]; field text; value jsonb; kind text; required boolean;
  operation_id uuid; lim integer:=30; skip integer:=0; page text; bid uuid; pid uuid;
  state text; message text; hint text;
begin
  if spec is null then return private.api_error('PT404','Unknown endpoint','UNKNOWN_ENDPOINT'); end if;
  guard:=private.api_guard(spec->>'scope');
  if guard ? 'code' then return guard; end if;
  begin
    operation:=spec->>'operation'; page:=spec->>'page';
    select array_agg(x) into allowed from jsonb_array_elements_text(spec->'fields') x;
    perform private.api_reject_unknown(args,coalesce(allowed,'{}'));
    if octet_length(args::text)>600000 then raise sqlstate 'PT400' using message='Board request is too large',hint='INVALID_FIELD'; end if;
    for field in select jsonb_array_elements_text(spec->'required') loop
      if not(args ? field) or args->field='null'::jsonb then
        raise sqlstate 'PT400' using message=field||' is required',hint='MISSING_FIELD';
      end if;
    end loop;
    for field,value in select * from jsonb_each(args) loop
      kind:=jsonb_typeof(value);
      if field=any(array['board_id','post_id','thread_id','reply_to','user_id','id','operation_id','asset_id','draft_id','revision_id','base_id','draft_revision_id','draft_base_id','case_id']) then
        perform private.api_arg_uuid(args,field,false);
      elsif field=any(array['review','reveal','approve','muted','push_enabled','enabled','members_can_invite','archived','moderator','spoiler','yeah']) then
        if kind<>'boolean' then raise sqlstate 'PT400' using message=field||' must be a boolean',hint='INVALID_FIELD'; end if;
      elsif field='limit' then
        lim:=private.api_arg_int(args,'limit',30,1,100);
      elsif field in ('drawing','payload') then
        if kind<>'object' and not(field='drawing' and kind='null') then
          raise sqlstate 'PT400' using message=field||' must be an object',hint='INVALID_FIELD';
        end if;
      elsif kind<>'string' and not(field in ('cursor','expires_at') and kind='null') then
        raise sqlstate 'PT400' using message=field||' must be a string',hint='INVALID_FIELD';
      end if;
    end loop;
    if length(coalesce(args->>'cursor',''))>4096 then raise sqlstate 'PT400' using message='Invalid cursor',hint='INVALID_CURSOR'; end if;
    if args ? 'cursor' and args->'cursor'<>'null'::jsonb then
      begin
        position:=convert_from(decode(args->>'cursor','base64'),'UTF8')::jsonb;
        if position->>'v' is distinct from '1' or position->>'endpoint' is distinct from p_endpoint
          or position->>'filter' is distinct from md5((args-'cursor'-'limit'-'offset')::text)
          or jsonb_typeof(position->'position') is distinct from 'object' then
          raise exception 'Invalid cursor';
        end if;
        position:=position->'position';
      exception when others then
        raise sqlstate 'PT400' using message='Invalid cursor for these filters',hint='INVALID_CURSOR';
      end;
    else position:=null; end if;
    args:=args-'cursor';
    if page='cursor' then
      args:=args||jsonb_build_object('limit',lim);
      if position is not null then args:=args||jsonb_build_object('cursor',position); end if;
    elsif page in ('offset','array') then
      skip:=coalesce((position->>'offset')::integer,0);
      if skip<0 or skip>100000 then raise sqlstate 'PT400' using message='Invalid cursor offset',hint='INVALID_CURSOR'; end if;
      args:=args||jsonb_build_object('limit',lim,'offset',skip);
    end if;
    bid:=private.api_arg_uuid(args,'board_id',false);
    pid:=private.api_arg_uuid(args,'post_id',false);
    if args ? 'period' and args->>'period' not in ('today','week','all') then raise exception 'Unknown popularity period' using errcode='22023'; end if;
    if args ? 'scope' and args->>'scope' not in ('joined','explore') then raise exception 'Unknown directory scope' using errcode='22023'; end if;
    if args ? 'kind' and operation in ('branding_preview','draw_branding') and args->>'kind' not in ('icon','cover') then raise exception 'Choose icon or cover' using errcode='22023'; end if;
    if operation='save_draft' then
      perform private.api_reject_unknown(args->'payload',array['body','drawing','spoiler','stationery_id','thread_id','reply_to','branding_kind']);
      for field,value in select * from jsonb_each(args->'payload') loop
        kind:=jsonb_typeof(value);
        if field in ('thread_id','reply_to') then perform private.api_arg_uuid(args->'payload',field,false);
        elsif field='drawing' then perform private.board_validate_drawing(nullif(value,'null'::jsonb));
        elsif field='spoiler' and kind is distinct from 'boolean' then raise exception 'spoiler must be a boolean' using errcode='22023';
        elsif field in ('body','stationery_id','branding_kind') and kind<>'string' and not(field='branding_kind' and kind='null') then
          raise exception 'Invalid draft text field' using errcode='22023';
        end if;
      end loop;
      if args->'payload'->>'branding_kind' is not null and args->'payload'->>'branding_kind' not in ('icon','cover') then
        raise exception 'Choose icon or cover' using errcode='22023';
      end if;
    end if;
    if operation='asset' and exists(select 1 from private.board_assets a join private.boards b on b.id=a.board_id
      where a.id=(args->>'asset_id')::uuid and a.id is distinct from b.icon_asset_id and a.id is distinct from b.cover_asset_id)
      and not private.api_has_scope('boards:moderate') then
      raise sqlstate 'PT403' using message='The boards:moderate scope is required for retained artwork',hint='SCOPE_REQUIRED';
    end if;
    if coalesce((args->>'review')::boolean,false) and not private.api_has_scope('boards:moderate') then
      raise sqlstate 'PT403' using message='The boards:moderate scope is required',hint='SCOPE_REQUIRED';
    end if;
    if (args ?| array['draft_id','draft_revision_id','draft_base_id']) and operation in ('publish','draw_branding')
      and not private.api_has_scope('boards:drafts') then
      raise sqlstate 'PT403' using message='The boards:drafts scope is required to publish a saved draft',hint='SCOPE_REQUIRED';
    end if;
    if operation in ('delete_post','spoiler') and exists(select 1 from private.board_posts where id=pid and author_id is distinct from auth.uid())
      and not private.api_has_scope('boards:moderate') then
      raise sqlstate 'PT403' using message='The boards:moderate scope is required for another author''s note',hint='SCOPE_REQUIRED';
    end if;
    if p_endpoint='decline_invitation' and not exists(select 1 from private.board_invitations where id=(args->>'id')::uuid and recipient_id=auth.uid()) then
      raise exception 'Invitation unavailable' using errcode='42501';
    end if;
    -- Review never bypasses the feature kill switch for connected applications.
    perform private.board_actor(operation<>'settings');
    if (spec->>'mutation')::boolean then
      operation_id:=private.api_arg_uuid(args,'operation_id',true);
      response:=public.boards_mutate(operation,args-'operation_id',operation_id);
    else
      if operation='notices' then response:=private.api_boards_collection(operation,args,lim,skip);
      elsif page='array' then response:=private.api_boards_collection(operation,args,lim+1,skip);
      else response:=public.boards_query(operation,args); end if;
      if p_endpoint='outgoing_invitations' then
        response:=jsonb_build_object('items',response->'invitations','next_offset',response->'next_offset');
      end if;
      if page='cursor' then
        response:=(response-'cursor')||jsonb_build_object('next_cursor',private.api_boards_cursor(p_endpoint,p_payload,response->'cursor'));
      elsif page='offset' then
        response:=(response-'next_offset')||jsonb_build_object('next_cursor',
          private.api_boards_cursor(p_endpoint,p_payload,case when response->>'next_offset' is not null
            then jsonb_build_object('offset',response->'next_offset') end));
      elsif page='array' then
        select coalesce(jsonb_agg(item order by ordinal),'[]'::jsonb) into value
          from jsonb_array_elements(response) with ordinality as items(item,ordinal) where ordinal<=lim;
        response:=jsonb_build_object('items',value,'next_cursor',private.api_boards_cursor(p_endpoint,p_payload,
          case when jsonb_array_length(response)>lim then jsonb_build_object('offset',skip+lim) end));
      end if;
    end if;
    return response;
  exception when sqlstate '40001' or sqlstate '40P01' then raise;
    when others then get stacked diagnostics state=returned_sqlstate,message=message_text,hint=pg_exception_hint;
      return private.api_boards_failure(state,message,hint);
  end;
end $$;
revoke all on function private.api_board_contract(),private.api_boards_failure(text,text,text),
  private.api_boards_cursor(text,jsonb,jsonb),private.api_boards_request(text,jsonb) from public,anon,authenticated,api_client,service_role;

create function public.api_v1_boards_settings(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('settings',$1); $$;
revoke all on function public.api_v1_boards_settings(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_settings(jsonb) to api_client;

create function public.api_v1_boards_list(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('list',$1); $$;
revoke all on function public.api_v1_boards_list(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_list(jsonb) to api_client;

create function public.api_v1_boards_get(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('get',$1); $$;
revoke all on function public.api_v1_boards_get(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_get(jsonb) to api_client;

create function public.api_v1_boards_feed(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('feed',$1); $$;
revoke all on function public.api_v1_boards_feed(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_feed(jsonb) to api_client;

create function public.api_v1_boards_replies(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('replies',$1); $$;
revoke all on function public.api_v1_boards_replies(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_replies(jsonb) to api_client;

create function public.api_v1_boards_post(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('post',$1); $$;
revoke all on function public.api_v1_boards_post(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_post(jsonb) to api_client;

create function public.api_v1_boards_preview(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('preview',$1); $$;
revoke all on function public.api_v1_boards_preview(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_preview(jsonb) to api_client;

create function public.api_v1_boards_asset(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('asset',$1); $$;
revoke all on function public.api_v1_boards_asset(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_asset(jsonb) to api_client;

create function public.api_v1_boards_branding_preview(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('branding_preview',$1); $$;
revoke all on function public.api_v1_boards_branding_preview(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_branding_preview(jsonb) to api_client;

create function public.api_v1_boards_drafts(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('drafts',$1); $$;
revoke all on function public.api_v1_boards_drafts(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_drafts(jsonb) to api_client;

create function public.api_v1_boards_inbox(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('inbox',$1); $$;
revoke all on function public.api_v1_boards_inbox(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_inbox(jsonb) to api_client;

create function public.api_v1_boards_invitations(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('invitations',$1); $$;
revoke all on function public.api_v1_boards_invitations(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_invitations(jsonb) to api_client;

create function public.api_v1_boards_proposals(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('proposals',$1); $$;
revoke all on function public.api_v1_boards_proposals(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_proposals(jsonb) to api_client;

create function public.api_v1_boards_notices(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('notices',$1); $$;
revoke all on function public.api_v1_boards_notices(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_notices(jsonb) to api_client;

create function public.api_v1_boards_members(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('members',$1); $$;
revoke all on function public.api_v1_boards_members(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_members(jsonb) to api_client;

create function public.api_v1_boards_management(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('management',$1); $$;
revoke all on function public.api_v1_boards_management(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_management(jsonb) to api_client;

create function public.api_v1_boards_outgoing_invitations(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('outgoing_invitations',$1); $$;
revoke all on function public.api_v1_boards_outgoing_invitations(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_outgoing_invitations(jsonb) to api_client;

create function public.api_v1_boards_history(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('history',$1); $$;
revoke all on function public.api_v1_boards_history(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_history(jsonb) to api_client;

create function public.api_v1_boards_stationery(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('stationery',$1); $$;
revoke all on function public.api_v1_boards_stationery(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_stationery(jsonb) to api_client;

create function public.api_v1_boards_propose(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('propose',$1); $$;
revoke all on function public.api_v1_boards_propose(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_propose(jsonb) to api_client;

create function public.api_v1_boards_join(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('join',$1); $$;
revoke all on function public.api_v1_boards_join(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_join(jsonb) to api_client;

create function public.api_v1_boards_accept_invitation(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('accept_invitation',$1); $$;
revoke all on function public.api_v1_boards_accept_invitation(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_accept_invitation(jsonb) to api_client;

create function public.api_v1_boards_join_code(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('join_code',$1); $$;
revoke all on function public.api_v1_boards_join_code(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_join_code(jsonb) to api_client;

create function public.api_v1_boards_decline_invitation(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('decline_invitation',$1); $$;
revoke all on function public.api_v1_boards_decline_invitation(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_decline_invitation(jsonb) to api_client;

create function public.api_v1_boards_decide_join(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('decide_join',$1); $$;
revoke all on function public.api_v1_boards_decide_join(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_decide_join(jsonb) to api_client;

create function public.api_v1_boards_invite(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('invite',$1); $$;
revoke all on function public.api_v1_boards_invite(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_invite(jsonb) to api_client;

create function public.api_v1_boards_create_code(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('create_code',$1); $$;
revoke all on function public.api_v1_boards_create_code(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_create_code(jsonb) to api_client;

create function public.api_v1_boards_revoke_invitation(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('revoke_invitation',$1); $$;
revoke all on function public.api_v1_boards_revoke_invitation(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_revoke_invitation(jsonb) to api_client;

create function public.api_v1_boards_leave(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('leave',$1); $$;
revoke all on function public.api_v1_boards_leave(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_leave(jsonb) to api_client;

create function public.api_v1_boards_preferences(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('preferences',$1); $$;
revoke all on function public.api_v1_boards_preferences(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_preferences(jsonb) to api_client;

create function public.api_v1_boards_push_preference(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('push_preference',$1); $$;
revoke all on function public.api_v1_boards_push_preference(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_push_preference(jsonb) to api_client;

create function public.api_v1_boards_update_board(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('update_board',$1); $$;
revoke all on function public.api_v1_boards_update_board(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_update_board(jsonb) to api_client;

create function public.api_v1_boards_archive(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('archive',$1); $$;
revoke all on function public.api_v1_boards_archive(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_archive(jsonb) to api_client;

create function public.api_v1_boards_transfer(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('transfer',$1); $$;
revoke all on function public.api_v1_boards_transfer(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_transfer(jsonb) to api_client;

create function public.api_v1_boards_set_moderator(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('set_moderator',$1); $$;
revoke all on function public.api_v1_boards_set_moderator(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_set_moderator(jsonb) to api_client;

create function public.api_v1_boards_accept_transfer(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('accept_transfer',$1); $$;
revoke all on function public.api_v1_boards_accept_transfer(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_accept_transfer(jsonb) to api_client;

create function public.api_v1_boards_publish(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('publish',$1); $$;
revoke all on function public.api_v1_boards_publish(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_publish(jsonb) to api_client;

create function public.api_v1_boards_edit(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('edit',$1); $$;
revoke all on function public.api_v1_boards_edit(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_edit(jsonb) to api_client;

create function public.api_v1_boards_delete_post(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('delete_post',$1); $$;
revoke all on function public.api_v1_boards_delete_post(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_delete_post(jsonb) to api_client;

create function public.api_v1_boards_spoiler(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('spoiler',$1); $$;
revoke all on function public.api_v1_boards_spoiler(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_spoiler(jsonb) to api_client;

create function public.api_v1_boards_react(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('react',$1); $$;
revoke all on function public.api_v1_boards_react(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_react(jsonb) to api_client;

create function public.api_v1_boards_draw_branding(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('draw_branding',$1); $$;
revoke all on function public.api_v1_boards_draw_branding(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_draw_branding(jsonb) to api_client;

create function public.api_v1_boards_save_draft(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('save_draft',$1); $$;
revoke all on function public.api_v1_boards_save_draft(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_save_draft(jsonb) to api_client;

create function public.api_v1_boards_discard_draft(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('discard_draft',$1); $$;
revoke all on function public.api_v1_boards_discard_draft(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_discard_draft(jsonb) to api_client;

create function public.api_v1_boards_report(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('report',$1); $$;
revoke all on function public.api_v1_boards_report(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_report(jsonb) to api_client;

create function public.api_v1_boards_resolve_report(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('resolve_report',$1); $$;
revoke all on function public.api_v1_boards_resolve_report(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_resolve_report(jsonb) to api_client;

create function public.api_v1_boards_restrict(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('restrict',$1); $$;
revoke all on function public.api_v1_boards_restrict(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_restrict(jsonb) to api_client;

create function public.api_v1_boards_revoke_restriction(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('revoke_restriction',$1); $$;
revoke all on function public.api_v1_boards_revoke_restriction(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_revoke_restriction(jsonb) to api_client;

create function public.api_v1_boards_appeal(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('appeal',$1); $$;
revoke all on function public.api_v1_boards_appeal(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_appeal(jsonb) to api_client;

create function public.api_v1_boards_resolve_appeal(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('resolve_appeal',$1); $$;
revoke all on function public.api_v1_boards_resolve_appeal(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_resolve_appeal(jsonb) to api_client;

create function public.api_v1_boards_read_event(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('read_event',$1); $$;
revoke all on function public.api_v1_boards_read_event(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_read_event(jsonb) to api_client;

create function public.api_v1_boards_read_thread(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('read_thread',$1); $$;
revoke all on function public.api_v1_boards_read_thread(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_read_thread(jsonb) to api_client;

create function public.api_v1_boards_buy_stationery(jsonb default '{}') returns jsonb
language sql security definer set search_path='' as $$ select private.api_boards_request('buy_stationery',$1); $$;
revoke all on function public.api_v1_boards_buy_stationery(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_buy_stationery(jsonb) to api_client;

notify pgrst,'reload schema';
commit;
