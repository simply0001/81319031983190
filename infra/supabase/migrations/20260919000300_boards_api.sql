begin;

create function private.board_invalidate(p_board uuid) returns void
language plpgsql security definer set search_path='' as $$
declare viewer uuid;
begin
  for viewer in select user_id from private.board_members where board_id=p_board
    union select user_id from private.board_viewers where board_id=p_board and seen_at>now()-interval '1 hour'
    union select auth.uid() where auth.uid() is not null loop
    perform realtime.send(jsonb_build_object('board_id',p_board),'BOARDS','notifications:'||viewer,true);
  end loop;
end $$;
revoke all on function private.board_invalidate(uuid) from public,anon,authenticated;

create function private.board_emit(p_board uuid,p_thread uuid,p_actor uuid,p_kind text) returns void
language plpgsql security definer set search_path='' as $$
declare m record; event_id uuid;
begin
  perform private.board_invalidate(p_board);
  for m in select * from private.board_members where board_id=p_board loop
    -- This event is an invalidation only; never broadcast private content.
    if p_kind='access' then continue; end if;
    if m.user_id=p_actor or m.muted or private.board_blocked(m.user_id,p_actor) or private.board_restricted(p_board,m.user_id,true) then continue; end if;
    if p_kind='report' and m.role not in ('owner','moderator') then continue; end if;
    select id into event_id from private.board_events where recipient_id=m.user_id and board_id=p_board
      and thread_id is not distinct from p_thread and kind=p_kind and read_at is null and updated_at>now()-interval '5 minutes'
      order by updated_at desc limit 1 for update;
    if found then
      update private.board_events set event_count=event_count+1,updated_at=now(),push_pending=m.push_enabled and p_kind<>'report',actor_id=p_actor where id=event_id;
    else
      insert into private.board_events(recipient_id,board_id,thread_id,kind,push_pending,actor_id)
        values(m.user_id,p_board,p_thread,p_kind,m.push_enabled and p_kind<>'report',p_actor);
    end if;
  end loop;
end $$;

create function public.boards_query(p_operation text,p_args jsonb default '{}') returns jsonb
language plpgsql security definer set search_path='' as $$
declare
  actor uuid:=private.board_actor(p_operation<>'settings' and left(p_operation,6)<>'staff_' and not
    (coalesce((p_args->>'review')::boolean,false) and p_operation in ('board','feed','replies','post','preview','asset','branding_preview','members','management','history')));
  bid uuid:=nullif(p_args->>'board_id','')::uuid;
  pid uuid:=nullif(p_args->>'post_id','')::uuid;
  review boolean:=coalesce((p_args->>'review')::boolean,false);
  lim integer:=least(greatest(coalesce((p_args->>'limit')::integer,30),1),100);
  result jsonb; rows jsonb; cursor jsonb:=p_args->'cursor'; sort text:=coalesce(p_args->>'sort','newest');
  since timestamptz; central boolean;
begin
  if jsonb_typeof(p_args)<>'object' then raise exception 'Arguments must be an object' using errcode='22023'; end if;
  case p_operation
  when 'settings' then
    return (select jsonb_build_object('enabled',enabled,'requests_open',requests_open,'push_enabled',coalesce((select push_enabled from private.board_preferences where user_id=actor),true)) from private.board_settings);
  when 'directory' then
    select coalesce(jsonb_agg(private.board_json(q.id) order by q.created_at desc,q.id desc),'[]') into rows
    from (select b.id,b.created_at from private.boards b where private.board_can_read(b.id,actor)
      and case when coalesce(p_args->>'scope','joined')='explore' then b.visibility='public' and not b.archived else private.board_role(b.id,actor) is not null end
      and (coalesce(p_args->>'search','')='' or position(lower(p_args->>'search') in lower(b.name))>0)
      and (cursor is null or (b.created_at,b.id)<((cursor->>'created_at')::timestamptz,(cursor->>'id')::uuid))
      order by b.created_at desc,b.id desc limit lim) q;
    return jsonb_build_object('items',rows,'cursor',case when jsonb_array_length(rows)=lim then jsonb_build_object('created_at',rows->-1->'created_at','id',rows->-1->'id') end);
  when 'board' then
    perform private.board_require_read(bid,review);
    return private.board_json(bid);
  when 'feed' then
    perform private.board_require_read(bid,review);
    if sort not in ('newest','activity','popular') then raise exception 'Unknown feed order' using errcode='22023'; end if;
    since:=case coalesce(p_args->>'period','week') when 'today' then date_trunc('day',now() at time zone 'UTC') at time zone 'UTC'
      when 'all' then '-infinity'::timestamptz else now()-interval '7 days' end;
    with ranked as (
      select p.id,p.created_at,case sort when 'popular' then (select count(*)::numeric from private.board_reactions r where r.post_id=p.id and (review or not private.board_blocked(actor,r.user_id)))
        when 'activity' then extract(epoch from p.activity_at) else extract(epoch from p.created_at) end score
      from private.board_posts p where p.board_id=bid and p.thread_id is null
        and (review or not private.board_blocked(actor,p.author_id)) and (sort<>'popular' or p.created_at>=since)
    ), page as (select * from ranked where cursor is null or (score,created_at,id)<((cursor->>'score')::numeric,(cursor->>'created_at')::timestamptz,(cursor->>'id')::uuid)
      order by score desc,created_at desc,id desc limit lim)
    select coalesce(jsonb_agg(private.board_post_json(id)||jsonb_build_object('score',score) order by score desc,created_at desc,id desc),'[]') into rows from page;
    return jsonb_build_object('items',rows,'cursor',case when jsonb_array_length(rows)=lim then jsonb_build_object('score',rows->-1->'score','created_at',rows->-1->'created_at','id',rows->-1->'id') end);
  when 'replies' then
    select board_id into bid from private.board_posts where id=pid and thread_id is null;
    perform private.board_require_read(bid,review);
    if not review and private.board_blocked(actor,(select author_id from private.board_posts where id=pid)) then raise exception 'Post unavailable' using errcode='42501'; end if;
    select coalesce(jsonb_agg(private.board_post_json(q.id) order by q.created_at,q.id),'[]') into rows
    from (select id,created_at from private.board_posts p where thread_id=pid and (review or not private.board_blocked(actor,p.author_id))
      and (cursor is null or (created_at,id)>((cursor->>'created_at')::timestamptz,(cursor->>'id')::uuid)) order by created_at,id limit lim) q;
    return jsonb_build_object('post',private.board_post_json(pid),'items',rows,'cursor',case when jsonb_array_length(rows)=lim then jsonb_build_object('created_at',rows->-1->'created_at','id',rows->-1->'id') end);
  when 'post' then
    select board_id into bid from private.board_posts where id=pid;
    perform private.board_require_read(bid,review);
    if not review and private.board_blocked(actor,(select author_id from private.board_posts where id=pid)) then raise exception 'Post unavailable' using errcode='42501'; end if;
    return private.board_post_json(pid,coalesce((p_args->>'reveal')::boolean,false));
  when 'preview' then
    select board_id into bid from private.board_posts where id=pid;
    perform private.board_require_read(bid,review);
    if not review and private.board_blocked(actor,(select author_id from private.board_posts where id=pid)) then raise exception 'Post unavailable' using errcode='42501'; end if;
    return (select jsonb_build_object('svg',case when spoiler and not coalesce((p_args->>'reveal')::boolean,false) then null else drawing_preview end) from private.board_posts where id=pid);
  when 'asset' then
    select board_id into bid from private.board_assets where id=(p_args->>'asset_id')::uuid;
    if review and private.has_permission('boards') then
      perform private.board_require_branding(bid);
      insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'branding_review',true);
    else perform private.board_require_read(bid,review); end if;
    if not exists(select 1 from private.boards where id=bid and (icon_asset_id=(p_args->>'asset_id')::uuid or cover_asset_id=(p_args->>'asset_id')::uuid)) then
      perform private.board_require_moderator(bid);
      insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'retained_artwork_review',private.has_permission('board_content'));
    end if;
    return (select jsonb_build_object('mime',mime,'data',encode(bytes,'base64')) from private.board_assets where id=(p_args->>'asset_id')::uuid);
  when 'branding_preview' then
    if review and private.has_permission('boards') then
      perform private.board_require_branding(bid);
      insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'branding_review',true);
    else perform private.board_require_read(bid,review); end if;
    return (select jsonb_build_object('svg',private.board_drawing_preview(case when p_args->>'kind'='icon' then icon_drawing else cover_drawing end)) from private.boards where id=bid);
  when 'drafts' then
    -- No staff override exists for this branch.
    select coalesce(jsonb_agg(to_jsonb(d) order by d.created_at desc),'[]') into rows
      from (select * from private.board_draft_versions where user_id=actor and is_head
        and (bid is null or board_id=bid) and private.board_can_read(board_id,actor)
        and (cursor is null or (created_at,id)<((cursor->>'created_at')::timestamptz,(cursor->>'id')::uuid))
        order by created_at desc,id desc limit lim) d;
    return jsonb_build_object('items',rows,'cursor',case when jsonb_array_length(rows)=lim then jsonb_build_object('created_at',rows->-1->'created_at','id',rows->-1->'id') end);
  when 'inbox' then
    select coalesce(jsonb_agg(jsonb_build_object('id',e.id,'board_id',e.board_id,'board_name',b.name,'thread_id',e.thread_id,
      'kind',e.kind,'event_count',e.event_count,'read_at',e.read_at,'updated_at',e.updated_at) order by e.updated_at desc,e.id desc),'[]') into rows
      from (select * from private.board_events e where recipient_id=actor and private.board_can_read(board_id,actor)
        and not private.board_blocked(actor,e.actor_id)
        and (thread_id is null or not private.board_blocked(actor,(select author_id from private.board_posts where id=e.thread_id)))
        and (cursor is null or (updated_at,id)<((cursor->>'updated_at')::timestamptz,(cursor->>'id')::uuid)) order by updated_at desc,id desc limit lim) e
      join private.boards b on b.id=e.board_id;
    return jsonb_build_object('items',rows,'cursor',case when jsonb_array_length(rows)=lim then jsonb_build_object('updated_at',rows->-1->'updated_at','id',rows->-1->'id') end);
  when 'invitations' then
    return (select coalesce(jsonb_agg(jsonb_build_object('id',i.id,'board_id',i.board_id,'board_name',b.name,'inviter_id',i.inviter_id,'created_at',i.created_at) order by i.created_at desc),'[]')
      from private.board_invitations i join private.boards b on b.id=i.board_id where i.recipient_id=actor and not i.revoked and i.accepted_at is null and not b.archived
      and not private.board_blocked(actor,i.inviter_id) and not private.board_restricted(i.board_id,actor,true));
  when 'proposals' then
    return (select coalesce(jsonb_agg(to_jsonb(p) order by p.created_at desc),'[]') from private.board_proposals p where author_id=actor);
  when 'notices' then
    return jsonb_build_object('restrictions',(select coalesce(jsonb_agg(to_jsonb(r) order by created_at desc),'[]') from private.board_restrictions r where user_id=actor),
      'actions',(select coalesce(jsonb_agg(to_jsonb(a) order by created_at desc),'[]') from private.board_audit a where subject_id=actor and action in ('delete_post','spoiler')),
      'appeals',(select coalesce(jsonb_agg(to_jsonb(a) order by created_at desc),'[]') from private.board_appeals a where user_id=actor));
  when 'members' then
    if review then
      central:=private.board_require_moderator(bid,'board_members');
      insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'member_review',central);
    else perform private.board_require_read(bid); end if;
    select coalesce(jsonb_agg(to_jsonb(q) order by q.joined_at,q.user_id),'[]') into rows from
      (select m.user_id,m.role,m.joined_at,p.display_name,p.avatar_path from private.board_members m join public.profiles p on p.user_id=m.user_id
        where m.board_id=bid and (review or not private.board_blocked(actor,m.user_id))
        and (cursor is null or (m.joined_at,m.user_id)>((cursor->>'joined_at')::timestamptz,(cursor->>'user_id')::uuid))
        order by m.joined_at,m.user_id limit lim) q;
    return jsonb_build_object('items',rows,'cursor',case when jsonb_array_length(rows)=lim then jsonb_build_object('joined_at',rows->-1->'joined_at','user_id',rows->-1->'user_id') end);
  when 'management' then
    if not review and private.board_role(bid,actor)='member' then
      perform private.board_require_read(bid);
      select coalesce(jsonb_agg(to_jsonb(q)-'code_hash'),'[]') into rows from
        (select * from private.board_invitations where board_id=bid and inviter_id=actor and not revoked and accepted_at is null
          order by created_at,id limit lim offset greatest(coalesce((p_args->>'offset')::int,0),0)) q;
      return jsonb_build_object('board',private.board_json(bid),'invitations',rows,'next_offset',
        case when jsonb_array_length(rows)=lim then greatest(coalesce((p_args->>'offset')::int,0),0)+lim end);
    end if;
    central:=private.board_require_moderator(bid,'board_members');
    insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'management_review',central);
    result:=jsonb_build_object('board',private.board_json(bid),
      'requests',(select coalesce(jsonb_agg(to_jsonb(q)),'[]') from (select r.*,p.display_name from private.board_join_requests r join public.profiles p on p.user_id=r.user_id where board_id=bid order by r.created_at,r.user_id limit lim offset greatest(coalesce((p_args->>'offset')::int,0),0)) q),
      'invitations',(select coalesce(jsonb_agg(to_jsonb(q)-'code_hash'),'[]') from (select * from private.board_invitations where board_id=bid and not revoked and accepted_at is null order by created_at,id limit lim offset greatest(coalesce((p_args->>'offset')::int,0),0)) q),
      'restrictions',(select coalesce(jsonb_agg(to_jsonb(q)),'[]') from (select * from private.board_restrictions where board_id=bid and revoked_at is null order by created_at,id limit lim offset greatest(coalesce((p_args->>'offset')::int,0),0)) q),
      'appeals',(select coalesce(jsonb_agg(to_jsonb(q)),'[]') from (select * from private.board_appeals where board_id=bid and status='open' order by created_at,id limit lim offset greatest(coalesce((p_args->>'offset')::int,0),0)) q),
      'reports',(select coalesce(jsonb_agg(to_jsonb(q)-'reporter_id'),'[]') from (select * from private.board_reports where board_id=bid and status='open' and
        ((central and private.has_permission('board_content')) or (not central and not central_only)) order by created_at,id limit lim offset greatest(coalesce((p_args->>'offset')::int,0),0)) q));
    return result||jsonb_build_object('next_offset',case when exists(select 1 from jsonb_each(result) where jsonb_array_length(case when jsonb_typeof(value)='array' then value else '[]'::jsonb end)=lim)
      then greatest(coalesce((p_args->>'offset')::int,0),0)+lim end);
  when 'history' then
    central:=private.board_require_moderator(bid);
    insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'history_review',central);
    if pid is not null and not exists(select 1 from private.board_posts where id=pid and board_id=bid) then raise exception 'Post unavailable' using errcode='42501'; end if;
    return (select coalesce(jsonb_agg(to_jsonb(h)),'[]') from private.board_history h where board_id=bid and (pid is null or post_id=pid));
  when 'stationery' then
    return (select coalesce(jsonb_agg(to_jsonb(s)||jsonb_build_object('available',private.board_stationery_allowed(actor,s.id),
      'owned',exists(select 1 from private.board_stationery_owned where user_id=actor and stationery_id=s.id)) order by s.id),'[]') from private.board_stationery s
      where active or exists(select 1 from private.board_stationery_owned where user_id=actor and stationery_id=s.id));
  when 'staff_requests' then
    perform private.require_permission('board_requests');
    return (select coalesce(jsonb_agg(to_jsonb(q)),'[]') from (select * from private.board_proposals where status=coalesce(p_args->>'status','pending') order by created_at limit lim offset greatest(coalesce((p_args->>'offset')::integer,0),0)) q);
  when 'staff_boards' then
    if not (private.has_permission('boards') or private.has_permission('board_members') or private.has_permission('board_content') or private.has_permission('board_delete')) then raise exception 'Board staff permission required' using errcode='42501'; end if;
    return (select coalesce(jsonb_agg(to_jsonb(q)),'[]') from (select b.id,b.name,b.owner_id,b.visibility,b.archived,b.created_at from private.boards b
      where visibility='public' or private.has_permission('board_private_review') order by created_at desc limit lim offset greatest(coalesce((p_args->>'offset')::integer,0),0)) q);
  when 'staff_board' then
    if not (private.has_permission('boards') or private.has_permission('board_members') or private.has_permission('board_content') or private.has_permission('board_delete')) then raise exception 'Board staff permission required' using errcode='42501'; end if;
    if (select visibility='private' from private.boards where id=bid) then perform private.require_permission('board_private_review'); end if;
    insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'review_access',true);
    return private.board_json(bid);
  when 'staff_reports' then
    perform private.require_permission('board_content');
    result:=jsonb_build_object('reports',(select coalesce(jsonb_agg(to_jsonb(q)-'reporter_id'),'[]') from
      (select r.* from private.board_reports r join private.boards b on b.id=r.board_id where r.status='open'
        and (b.visibility='public' or private.has_permission('board_private_review')) order by r.created_at limit lim offset greatest(coalesce((p_args->>'offset')::integer,0),0)) q),
      'filters',(select coalesce(jsonb_agg(to_jsonb(q)),'[]') from (select r.* from private.board_filter_reviews r left join private.boards b on b.id=r.board_id
        where r.status='open' and (b.visibility='public' or private.has_permission('board_private_review')
          or (r.board_id is null and (r.proposal_id is null or exists(select 1 from private.board_proposals p where p.id=r.proposal_id and p.visibility='public'))))
        order by r.created_at limit lim offset greatest(coalesce((p_args->>'offset')::integer,0),0)) q));
    insert into private.board_audit(board_id,actor_id,action,central)
      select distinct (r->>'board_id')::uuid,actor,'report_review',true
      from jsonb_array_elements((result->'reports')||(result->'filters')) r where r->>'board_id' is not null;
    return result;
  when 'staff_filters' then
    perform private.require_permission('board_filters');
    return (select coalesce(jsonb_agg(to_jsonb(r) order by created_at),'[]') from private.board_word_rules r);
  when 'staff_settings' then
    perform private.require_permission('board_settings'); return (select to_jsonb(s) from private.board_settings s);
  when 'staff_stationery' then
    perform private.require_permission('board_stationery'); return (select coalesce(jsonb_agg(to_jsonb(s) order by id),'[]') from private.board_stationery s);
  when 'staff_audit' then
    perform private.require_permission('board_content');
    return (select coalesce(jsonb_agg(to_jsonb(q)),'[]') from (select a.* from private.board_audit a left join private.boards b on b.id=a.board_id
      where (bid is null or a.board_id=bid) and (b.id is null or b.visibility='public' or private.has_permission('board_private_review'))
      order by a.created_at desc limit lim offset greatest(coalesce((p_args->>'offset')::integer,0),0)) q);
  else raise exception 'Unknown Boards query' using errcode='22023';
  end case;
end $$;
revoke all on function public.boards_query(text,jsonb) from public,anon;
grant execute on function public.boards_query(text,jsonb) to authenticated;

create function public.boards_mutate(p_operation text,p_args jsonb,p_operation_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
<<op>>
declare
  actor uuid:=private.board_actor(left(p_operation,6)<>'staff_' and not (coalesce((p_args->>'review')::boolean,false)
    and p_operation in ('update_board','archive','transfer','set_moderator','delete_post','spoiler','restrict','revoke_restriction','resolve_report','resolve_appeal','decide_join')
    and (private.has_permission('boards') or private.has_permission('board_content') or private.has_permission('board_members') or private.has_permission('board_suspensions'))));
  bid uuid:=nullif(p_args->>'board_id','')::uuid;
  pid uuid:=nullif(p_args->>'post_id','')::uuid;
  target uuid:=nullif(p_args->>'user_id','')::uuid;
  rid uuid:=coalesce(nullif(p_args->>'id','')::uuid,gen_random_uuid());
  b private.boards; post private.board_posts; inv private.board_invitations; proposal private.board_proposals;
  paper private.board_stationery; restriction private.board_restrictions; report private.board_reports;
  replay private.board_operations; result jsonb:='{}'; payload_hash text:=md5(p_operation||':'||p_args::text);
  body text; name text; description text; rules text; reason text:=btrim(coalesce(p_args->>'reason',''));
  role text; central boolean:=false; code text; parent uuid; drawing jsonb:=nullif(p_args->'drawing','null'::jsonb);
  sid text:=coalesce(p_args->>'stationery_id','plain'); balance integer; revision uuid; base uuid;
begin
  if p_operation_id is null or p_args is null or jsonb_typeof(p_args)<>'object' or octet_length(p_args::text)>600000 then raise exception 'Invalid board operation' using errcode='22023'; end if;
  perform set_config('pocketpass.board_operation_id',p_operation_id::text,true);
  -- A per-account lock serializes retries and limits without serializing a board.
  perform pg_advisory_xact_lock(hashtextextended(actor::text||':boards:operation',0));
  select * into replay from private.board_operations where user_id=actor and operation_id=p_operation_id;
  if found then
    if replay.request_hash<>payload_hash or replay.operation<>p_operation then raise exception 'This operation ID was already used for a different request' using errcode='22023'; end if;
    return replay.response;
  end if;
  if bid is not null then
    select * into b from private.boards where id=bid for update;
    if not found then raise exception 'Board unavailable' using errcode='42501'; end if;
    role:=private.board_role(bid,actor);
  end if;
  case p_operation
  when 'propose','staff_create','staff_proposal' then
    if p_operation='propose' then
      if not(select requests_open from private.board_settings) then raise exception 'New board requests are closed for now' using errcode='42501'; end if;
      perform private.board_rate('invite_report');
    elsif p_operation='staff_create' then perform private.require_permission('boards');
    else
      perform private.require_permission('board_requests');
      select * into proposal from private.board_proposals where id=rid and status='pending' for update;
      if not found then raise exception 'Pending request not found' using errcode='22023'; end if;
      if not coalesce((p_args->>'approve')::boolean,false) then
        if reason='' then raise exception 'Give a reason for rejecting this request' using errcode='22023'; end if;
        update private.board_proposals set status='rejected',reason=op.reason where id=rid;
        result:=jsonb_build_object('id',rid,'status','rejected');
      end if;
    end if;
    if result='{}'::jsonb then
      name:=private.board_filter(btrim(coalesce(proposal.name,p_args->>'name')),'board name');
      description:=private.board_filter(coalesce(proposal.description,p_args->>'description',''),'board description');
      rules:=private.board_filter(coalesce(proposal.rules,p_args->>'rules',''),'board rules');
      if p_operation='propose' then
        insert into private.board_proposals(id,author_id,name,description,rules,visibility) values(rid,actor,name,description,rules,p_args->>'visibility');
        result:=jsonb_build_object('id',rid,'status','pending');
      else
        target:=coalesce(proposal.author_id,target,actor);
        bid:=gen_random_uuid();
        insert into private.boards(id,owner_id,name,description,rules,visibility) values(bid,target,name,description,rules,coalesce(proposal.visibility,p_args->>'visibility','public'));
        insert into private.board_members(board_id,user_id,role) values(bid,target,'owner');
        if p_operation='staff_proposal' then
          update private.board_proposals set status='approved',board_id=bid where id=rid;
          update private.board_filter_reviews set board_id=bid where proposal_id=rid;
        end if;
        insert into private.board_audit(board_id,actor_id,subject_id,action,central) values(bid,actor,target,'create',true);
        result:=jsonb_build_object('board_id',bid,'status','approved');
      end if;
    end if;
  when 'join','accept_invitation','join_code' then
    if p_operation='accept_invitation' then
      select * into inv from private.board_invitations where id=rid and recipient_id=actor and not revoked and accepted_at is null for update;
      if not found then raise exception 'Invitation unavailable' using errcode='42501'; end if;
    elsif p_operation='join_code' then
      select * into inv from private.board_invitations where code_hash=encode(extensions.digest(upper(btrim(p_args->>'code')),'sha256'),'hex') and not revoked for update;
      if not found then raise exception 'Invitation code unavailable' using errcode='42501'; end if;
    end if;
    if p_operation<>'join' then
      bid:=inv.board_id; select * into b from private.boards where id=bid for update;
      if private.board_blocked(actor,inv.inviter_id) then raise exception 'Invitation unavailable' using errcode='42501'; end if;
      if private.board_role(bid,inv.inviter_id) is null or (not b.members_can_invite and private.board_role(bid,inv.inviter_id)='member') then raise exception 'Invitation unavailable' using errcode='42501'; end if;
    elsif b.visibility<>'public' then raise exception 'An invitation is required' using errcode='42501'; end if;
    if b.id is null or b.archived or private.board_restricted(bid,actor) then raise exception 'You cannot join this board right now' using errcode='42501'; end if;
    if p_operation='accept_invitation' or (p_operation='join' and b.join_policy='open') or (p_operation='join_code' and b.code_policy='open') then
      insert into private.board_members(board_id,user_id) values(bid,actor) on conflict do nothing;
      delete from private.board_join_requests where board_id=bid and user_id=actor;
      if p_operation='accept_invitation' then update private.board_invitations set accepted_at=now() where id=rid; end if;
      result:=jsonb_build_object('board_id',bid,'status','joined');
    else
      insert into private.board_join_requests(board_id,user_id) values(bid,actor) on conflict do nothing;
      result:=jsonb_build_object('board_id',bid,'status','requested');
    end if;
  when 'decide_join' then
    central:=private.board_require_moderator(bid,'board_members');
    if b.archived or private.board_restricted(bid,target) then raise exception 'This person cannot join right now' using errcode='42501'; end if;
    if not exists(select 1 from private.board_join_requests where board_id=bid and user_id=target) then raise exception 'Join request unavailable' using errcode='22023'; end if;
    if coalesce((p_args->>'approve')::boolean,false) then insert into private.board_members(board_id,user_id) values(bid,target) on conflict do nothing; end if;
    delete from private.board_join_requests where board_id=bid and user_id=target;
  when 'invite','create_code' then
    perform private.board_require_participant(bid);
    if role not in ('owner','moderator') and not b.members_can_invite then raise exception 'Only board staff can invite people' using errcode='42501'; end if;
    perform private.board_rate('invite_report');
    if p_operation='invite' then
      if target is null or target=actor or private.board_blocked(actor,target) or private.board_restricted(bid,target) then raise exception 'This person cannot be invited' using errcode='42501'; end if;
      if (select block_messages from public.profiles where user_id=target for share) then raise exception 'This person has Block Messages turned on and cannot receive board invitations.' using errcode='42501',hint='BOARD_INVITATIONS_BLOCKED'; end if;
      insert into private.board_invitations(id,board_id,inviter_id,recipient_id) values(rid,bid,actor,target);
      perform realtime.send(jsonb_build_object('board_id',bid),'BOARDS','notifications:'||target,true);
      result:=jsonb_build_object('id',rid);
    else
      code:=upper(encode(extensions.gen_random_bytes(12),'hex'));
      insert into private.board_invitations(id,board_id,inviter_id,code_hash) values(rid,bid,actor,encode(extensions.digest(code,'sha256'),'hex'));
      result:=jsonb_build_object('id',rid,'code',code);
    end if;
  when 'revoke_invitation' then
    select * into inv from private.board_invitations where id=rid and board_id=bid;
    if not found then raise exception 'Invitation unavailable' using errcode='42501'; end if;
    if inv.recipient_id<>actor or inv.recipient_id is null then
      if inv.inviter_id<>actor then central:=private.board_require_moderator(bid,'board_members'); else perform private.board_require_participant(bid); end if;
    end if;
    update private.board_invitations set revoked=true where id=rid;
  when 'leave' then
    if role='owner' and not b.archived then raise exception 'Transfer ownership or archive the board before leaving' using errcode='42501'; end if;
    delete from private.board_members where board_id=bid and user_id=actor;
    delete from private.board_join_requests where board_id=bid and user_id=actor;
    update private.board_invitations set revoked=true where board_id=bid and inviter_id=actor;
    delete from private.board_events where board_id=bid and recipient_id=actor;
  when 'preferences' then
    if role is null then raise exception 'Board membership required' using errcode='42501'; end if;
    update private.board_members set muted=coalesce((p_args->>'muted')::boolean,muted),push_enabled=coalesce((p_args->>'push_enabled')::boolean,push_enabled) where board_id=bid and user_id=actor;
    if coalesce((p_args->>'muted')::boolean,false) or p_args->>'push_enabled'='false' then update private.board_events set push_pending=false where board_id=bid and recipient_id=actor; end if;
  when 'push_preference' then
    insert into private.board_preferences(user_id,push_enabled) values(actor,(p_args->>'enabled')::boolean)
      on conflict(user_id) do update set push_enabled=excluded.push_enabled;
  when 'update_board','archive','transfer','set_moderator' then
    central:=(coalesce((p_args->>'review')::boolean,false) or role is distinct from 'owner' or private.board_restricted(bid,actor)) and private.has_permission(case when p_operation in ('transfer','set_moderator') then 'board_members' else 'boards' end);
    if central and b.visibility='private' then perform private.require_permission('board_private_review'); end if;
    if not central and (role is distinct from 'owner' or private.board_restricted(bid,actor)) then raise exception 'Board owner required' using errcode='42501'; end if;
    if p_operation='update_board' then
      if b.visibility='private' and p_args->>'visibility'='public' then raise exception 'Private boards cannot become public' using errcode='22023'; end if;
      insert into private.board_history(board_id,subject_id,content) values(bid,b.owner_id,to_jsonb(b));
      update private.boards set name=case when p_args ? 'name' and btrim(p_args->>'name') is distinct from b.name then private.board_filter(btrim(p_args->>'name'),'board name',bid) else b.name end,
        description=case when p_args ? 'description' and p_args->>'description' is distinct from b.description then private.board_filter(p_args->>'description','board description',bid) else b.description end,
        rules=case when p_args ? 'rules' and p_args->>'rules' is distinct from b.rules then private.board_filter(p_args->>'rules','board rules',bid) else b.rules end,visibility=coalesce(p_args->>'visibility',b.visibility),
        accent=coalesce(p_args->>'accent',b.accent),join_policy=coalesce(p_args->>'join_policy',b.join_policy),code_policy=coalesce(p_args->>'code_policy',b.code_policy),
        members_can_invite=coalesce((p_args->>'members_can_invite')::boolean,b.members_can_invite),updated_at=now(),access_revision=access_revision+1 where id=bid;
    elsif p_operation='archive' then update private.boards set archived=coalesce((p_args->>'archived')::boolean,true),updated_at=now(),access_revision=access_revision+1 where id=bid;
    elsif p_operation='transfer' then
      if private.board_role(bid,target) is null or private.board_restricted(bid,target) then raise exception 'Choose a current unrestricted member' using errcode='22023'; end if;
      if central then
        update private.board_members set role='member' where board_id=bid and board_members.role='owner';
        update private.board_members set role='owner' where board_id=bid and user_id=target;
        update private.boards set owner_id=target,transfer_to=null where id=bid;
      else update private.boards set transfer_to=target where id=bid; end if;
    else
      if target=b.owner_id or private.board_role(bid,target) is null then raise exception 'Choose an ordinary member or moderator' using errcode='22023'; end if;
      update private.board_members set role=case when coalesce((p_args->>'moderator')::boolean,false) then 'moderator' else 'member' end where board_id=bid and user_id=target;
    end if;
  when 'accept_transfer' then
    perform private.board_require_participant(bid);
    if b.transfer_to is distinct from actor then raise exception 'No ownership offer for this account' using errcode='42501'; end if;
    update private.board_members set role='member' where board_id=bid and board_members.role='owner';
    update private.board_members set role='owner' where board_id=bid and user_id=actor;
    update private.boards set owner_id=actor,transfer_to=null where id=bid;
  when 'publish' then
    perform private.board_require_participant(bid); perform private.board_rate('submit');
    parent:=nullif(p_args->>'thread_id','')::uuid;
    if parent is not null then
      select * into post from private.board_posts where id=parent and board_id=bid and thread_id is null;
      if not found or private.board_blocked(actor,post.author_id) then raise exception 'Thread unavailable' using errcode='42501'; end if;
    end if;
    if p_args->>'reply_to' is not null then
      select * into post from private.board_posts where id=(p_args->>'reply_to')::uuid and board_id=bid and (id=parent or thread_id=parent);
      if not found or private.board_blocked(actor,post.author_id) then raise exception 'Reply target unavailable' using errcode='42501'; end if;
    end if;
    body:=coalesce(p_args->>'body','');
    if length(body)>(case when parent is null then 1000 else 500 end) or (btrim(body)='' and drawing is null) then raise exception 'Add a note or a drawing within the character limit' using errcode='22023'; end if;
    perform private.board_validate_drawing(drawing);
    body:=private.board_filter(body,'post',bid);
    if drawing is not null then
      if not private.board_stationery_allowed(actor,sid) then raise exception 'This stationery is not unlocked' using errcode='42501'; end if;
      select * into paper from private.board_stationery where id=sid;
    end if;
    insert into private.board_posts(id,board_id,author_id,thread_id,reply_to,body,drawing,drawing_preview,stationery,spoiler)
      values(rid,bid,actor,parent,nullif(p_args->>'reply_to','')::uuid,body,drawing,private.board_note_preview(drawing,paper.artwork),
        case when drawing is not null then jsonb_build_object('id',paper.id,'version',paper.version,'name',paper.name,'artwork',paper.artwork) end,coalesce((p_args->>'spoiler')::boolean,false));
    if parent is not null then update private.board_posts set activity_at=now() where id=parent; end if;
    -- Delete only the explicit published revision; independent recovered copies survive.
    delete from private.board_draft_versions where id in (nullif(p_args->>'draft_revision_id','')::uuid,nullif(p_args->>'draft_base_id','')::uuid)
      and user_id=actor and board_id=bid and (p_args->>'draft_id' is null or draft_id=(p_args->>'draft_id')::uuid);
    perform private.board_emit(bid,coalesce(parent,rid),actor,'activity');
    result:=jsonb_build_object('id',rid,'board_id',bid,'thread_id',coalesce(parent,rid));
  when 'edit','delete_post','spoiler','react' then
    select * into post from private.board_posts where id=pid and board_id=bid for update;
    if not found then raise exception 'Post unavailable' using errcode='42501'; end if;
    if private.board_blocked(actor,post.author_id) then
      if p_operation in ('delete_post','spoiler') and coalesce((p_args->>'review')::boolean,false) then central:=private.board_require_moderator(bid);
      else raise exception 'Post unavailable' using errcode='42501'; end if;
    end if;
    if p_operation='react' then
      perform private.board_require_participant(bid); perform private.board_rate('react');
      if post.removed then raise exception 'This post was removed' using errcode='22023'; end if;
      if coalesce((p_args->>'yeah')::boolean,false) then
        insert into private.board_reactions(post_id,user_id) values(pid,actor) on conflict do nothing;
        if found then perform private.board_emit(bid,coalesce(post.thread_id,pid),actor,'activity'); end if;
      else delete from private.board_reactions where post_id=pid and user_id=actor; end if;
    else
      if post.author_id=actor then
        if coalesce((p_args->>'review')::boolean,false) and p_operation<>'edit' then central:=private.board_require_moderator(bid);
        else perform private.board_require_participant(bid); end if;
      elsif p_operation='edit' then raise exception 'Only the author can edit text' using errcode='42501';
      else central:=private.board_require_moderator(bid); end if;
      if p_operation='edit' then
        perform private.board_require_participant(bid); perform private.board_rate('submit');
        if post.removed then raise exception 'This post was removed' using errcode='22023'; end if;
        body:=coalesce(p_args->>'body','');
        if length(body)>(case when post.thread_id is null then 1000 else 500 end) or (btrim(body)='' and post.drawing is null) then raise exception 'Invalid note length' using errcode='22023'; end if;
        if p_args ? 'drawing' then raise exception 'Published drawings cannot be edited' using errcode='22023'; end if;
        insert into private.board_history(board_id,post_id,subject_id,content) values(bid,pid,post.author_id,to_jsonb(post));
        update private.board_posts set body=private.board_filter(op.body,'edit',bid),edited_at=now() where id=pid;
      elsif p_operation='delete_post' then
        if post.author_id is distinct from actor and reason='' then raise exception 'Give a reason for removing this note' using errcode='22023'; end if;
        if not post.removed then insert into private.board_history(board_id,post_id,subject_id,content) values(bid,pid,post.author_id,to_jsonb(post)); end if;
        update private.board_posts set body='',drawing=null,drawing_preview=null,stationery=null,removed=true where id=pid;
        delete from private.board_reactions where post_id=pid;
      else
        if post.author_id is distinct from actor and reason='' then raise exception 'Give a reason for changing this spoiler label' using errcode='22023'; end if;
        update private.board_posts set spoiler=coalesce((p_args->>'spoiler')::boolean,true) where id=pid;
      end if;
      if post.author_id is distinct from actor then target:=post.author_id; end if;
    end if;
  when 'draw_branding' then
    central:=private.board_require_branding(bid);
    if p_args->>'kind' not in ('icon','cover') or p_args->>'kind' is null or drawing is null then raise exception 'Choose icon or cover and draw something first' using errcode='22023'; end if;
    perform private.board_validate_drawing(drawing); perform private.board_rate('submit');
    insert into private.board_history(board_id,subject_id,content) values(bid,b.owner_id,to_jsonb(b));
    if p_args->>'kind'='icon' then update private.boards set icon_drawing=drawing,icon_asset_id=null,updated_at=now(),access_revision=access_revision+1 where id=bid;
    else update private.boards set cover_drawing=drawing,cover_asset_id=null,updated_at=now(),access_revision=access_revision+1 where id=bid; end if;
    insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'draw_branding',central);
    delete from private.board_draft_versions where id in (nullif(p_args->>'draft_revision_id','')::uuid,nullif(p_args->>'draft_base_id','')::uuid)
      and user_id=actor and board_id=bid and (p_args->>'draft_id' is null or draft_id=(p_args->>'draft_id')::uuid);
    result:=jsonb_build_object('board_id',bid);
  when 'save_draft' then
    perform private.board_require_read(bid);
    revision:=(p_args->>'revision_id')::uuid; base:=nullif(p_args->>'base_id','')::uuid;
    if revision is null or p_args->>'draft_id' is null then raise exception 'Draft and revision IDs are required' using errcode='22023'; end if;
    if base is not null and not exists(select 1 from private.board_draft_versions where id=base and user_id=actor and draft_id=(p_args->>'draft_id')::uuid and board_id=bid) then raise exception 'Draft base unavailable' using errcode='22023',hint='BOARD_DRAFT_BASE_EXPIRED'; end if;
    perform private.board_validate_drawing(nullif(p_args->'payload'->'drawing','null'::jsonb));
    if length(coalesce(p_args->'payload'->>'body',''))>1000 then raise exception 'Draft text is too long' using errcode='22023'; end if;
    insert into private.board_draft_versions(id,draft_id,user_id,board_id,base_id,payload) values(revision,(p_args->>'draft_id')::uuid,actor,bid,base,p_args->'payload');
    update private.board_draft_versions set is_head=false where id=base and user_id=actor;
    result:=jsonb_build_object('revision_id',revision,'conflict',exists(select 1 from private.board_draft_versions where draft_id=(p_args->>'draft_id')::uuid and user_id=actor and is_head and id<>revision));
  when 'discard_draft' then
    delete from private.board_draft_versions where id=(p_args->>'revision_id')::uuid and user_id=actor;
  when 'report' then
    perform private.board_require_read(bid); perform private.board_rate('invite_report');
    if reason='' then raise exception 'Describe what happened' using errcode='22023'; end if;
    target:=b.owner_id;
    if pid is not null then
      select author_id into target from private.board_posts where id=pid and board_id=bid;
      if not found or private.board_blocked(actor,target) then raise exception 'Post unavailable' using errcode='42501'; end if;
    end if;
    central:=coalesce(private.board_role(bid,target),'') in ('owner','moderator');
    insert into private.board_reports(id,board_id,post_id,reporter_id,reason,central_only) values(rid,bid,pid,actor,reason,central);
    if not central then perform private.board_emit(bid,null,actor,'report'); end if;
    result:=jsonb_build_object('case_id',rid);
  when 'resolve_report' then
    central:=private.board_require_moderator(bid);
    select * into report from private.board_reports where id=rid and board_id=bid for update;
    if not found or (report.central_only and not central) then raise exception 'Report unavailable' using errcode='42501'; end if;
    if reason='' then raise exception 'Give a resolution' using errcode='22023'; end if;
    update private.board_reports set status=case when p_args->>'status'='dismissed' then 'dismissed' else 'resolved' end,resolution=op.reason,resolved_at=now() where id=rid;
  when 'restrict','revoke_restriction' then
    if bid is null then perform private.require_permission('board_suspensions'); central:=true;
    else central:=private.board_require_moderator(bid,'board_members'); end if;
    if p_operation='restrict' then
      if target is null or target=actor or (not central and (target=b.owner_id or (role='moderator' and private.board_role(bid,target)='moderator'))) then raise exception 'You cannot restrict this account' using errcode='42501'; end if;
      if reason='' then raise exception 'Give a reason for this restriction' using errcode='22023'; end if;
      insert into private.board_restrictions(id,board_id,user_id,kind,reason,actor_id,central,expires_at) values(rid,bid,target,case when bid is null then 'suspension' else coalesce(p_args->>'kind','mute') end,reason,actor,central,nullif(p_args->>'expires_at','')::timestamptz);
      if bid is not null and p_args->>'kind'='ban' then
        delete from private.board_members where board_id=bid and user_id=target;
        delete from private.board_events where board_id=bid and recipient_id=target;
        update private.board_invitations set revoked=true where board_id=bid and (recipient_id=target or inviter_id=target);
      end if;
      result:=jsonb_build_object('case_id',rid);
    else
      select * into restriction from private.board_restrictions where id=rid and board_id is not distinct from bid;
      if not found then raise exception 'Restriction unavailable' using errcode='42501'; end if;
      if not central and (restriction.user_id=b.owner_id or (role='moderator' and private.board_role(bid,restriction.user_id)='moderator')) then raise exception 'You cannot change this restriction' using errcode='42501'; end if;
      update private.board_restrictions set revoked_at=now() where id=rid;
      target:=restriction.user_id;
    end if;
    perform realtime.send(jsonb_build_object('board_id',bid),'BOARDS','notifications:'||target,true);
  when 'appeal' then
    perform private.board_rate('invite_report');
    if reason='' then raise exception 'Write your appeal' using errcode='22023'; end if;
    if not exists(select 1 from private.board_restrictions r where id=(p_args->>'case_id')::uuid and board_id=bid and user_id=actor and not r.central)
      and not exists(select 1 from private.board_audit a where id=(p_args->>'case_id')::uuid and board_id=bid and subject_id=actor and not a.central) then
      raise exception 'For PocketPass staff actions, appeal through Discord with your case reference.' using errcode='42501';
    end if;
    insert into private.board_appeals(id,board_id,user_id,case_id,body) values(rid,bid,actor,(p_args->>'case_id')::uuid,reason);
    perform private.board_emit(bid,null,actor,'report');
    result:=jsonb_build_object('case_id',rid);
  when 'resolve_appeal' then
    central:=private.board_require_moderator(bid,'board_members');
    if reason='' then raise exception 'Give a resolution' using errcode='22023'; end if;
    update private.board_appeals set status=case when p_args->>'status'='dismissed' then 'dismissed' else 'resolved' end,resolution=reason where id=rid and board_id=bid;
  when 'read_event' then update private.board_events set read_at=now(),push_pending=false where recipient_id=actor and id=rid;
  when 'read_thread' then
    perform private.board_require_read(bid);
    update private.board_events set read_at=now(),push_pending=false where recipient_id=actor and board_id=bid
      and (nullif(p_args->>'thread_id','') is null or thread_id=(p_args->>'thread_id')::uuid);
  when 'buy_stationery' then
    select * into paper from private.board_stationery where id=sid for share;
    if not found or paper.access<>'tokens' or not paper.active then raise exception 'This stationery is not for sale' using errcode='22023'; end if;
    if not exists(select 1 from private.board_stationery_owned where user_id=actor and stationery_id=sid) then
      perform private.ensure_token_balance(actor);
      select t.balance into balance from public.token_balances t where user_id=actor for update;
      if balance<paper.price then raise exception 'Not enough tokens' using errcode='22023'; end if;
      update public.token_balances set balance=token_balances.balance-paper.price,updated_at=now() where user_id=actor;
      insert into private.board_stationery_owned(user_id,stationery_id,price_paid) values(actor,sid,paper.price);
    end if;
    result:=jsonb_build_object('stationery_id',sid,'owned',true);
  when 'staff_filter' then
    perform private.require_permission('board_filters');
    insert into private.board_word_rules(id,phrase,action,reason,enabled) values(rid,btrim(p_args->>'phrase'),p_args->>'action',reason,coalesce((p_args->>'enabled')::boolean,true))
      on conflict(id) do update set phrase=excluded.phrase,action=excluded.action,reason=excluded.reason,enabled=excluded.enabled;
    result:=jsonb_build_object('id',rid);
  when 'staff_filter_resolve' then
    perform private.require_permission('board_content');
    select board_id into bid from private.board_filter_reviews where id=rid;
    if bid is not null then perform private.board_require_read(bid,true); end if;
    update private.board_filter_reviews set status='resolved' where id=rid;
  when 'staff_settings' then
    perform private.require_permission('board_settings');
    update private.board_settings set enabled=coalesce((p_args->>'enabled')::boolean,enabled),requests_open=coalesce((p_args->>'requests_open')::boolean,requests_open),
      submissions_per_minute=coalesce((p_args->>'submissions_per_minute')::integer,submissions_per_minute),reactions_per_minute=coalesce((p_args->>'reactions_per_minute')::integer,reactions_per_minute),
      invitations_reports_per_minute=coalesce((p_args->>'invitations_reports_per_minute')::integer,invitations_reports_per_minute);
  when 'staff_stationery' then
    perform private.require_permission('board_stationery');
    perform private.board_validate_artwork(nullif(p_args->'artwork','null'::jsonb));
    if sid='plain' then raise exception 'Plain paper stays available to everyone' using errcode='22023'; end if;
    if p_args->>'access'='achievement' and not(p_args->>'achievement_key'=any(private.achievement_keys())) then raise exception 'Unknown achievement' using errcode='22023'; end if;
    insert into private.board_stationery(id,name,access,price,achievement_key,active,artwork) values(sid,p_args->>'name',p_args->>'access',coalesce((p_args->>'price')::integer,0),p_args->>'achievement_key',coalesce((p_args->>'active')::boolean,true),coalesce(nullif(p_args->'artwork','null'::jsonb),'{"version":1,"background":"#FFFFFF"}'::jsonb))
      on conflict(id) do update set name=excluded.name,access=excluded.access,price=excluded.price,achievement_key=excluded.achievement_key,
        active=excluded.active,artwork=excluded.artwork,version=board_stationery.version+1;
  when 'staff_delete' then
    perform private.require_permission('board_delete');
    if b.visibility='private' then perform private.require_permission('board_private_review'); end if;
    if reason='' then raise exception 'Give a reason for permanently deleting this board' using errcode='22023'; end if;
    perform private.board_emit(bid,null,actor,'access');
    -- Cascades include drawings, branding bytes, drafts, reports, original text,
    -- and idempotency results. Remove identifying content from the audit too.
    delete from private.board_audit where board_id=bid;
    delete from private.board_proposals where board_id=bid;
    delete from private.boards where id=bid;
    insert into private.board_audit(board_id,actor_id,action,central) values(bid,actor,'permanent_delete',true);
    bid:=null;
  else raise exception 'Unknown Boards operation' using errcode='22023';
  end case;
  update private.board_filter_reviews set board_id=bid,proposal_id=case when p_operation='propose' then rid else proposal_id end
    where actor_id=actor and operation_id=p_operation_id;
  if p_operation in ('decide_join','update_board','archive','transfer','accept_transfer','set_moderator','restrict','revoke_restriction','resolve_report','resolve_appeal','staff_settings','staff_filter','staff_filter_resolve','staff_stationery')
    or (p_operation in ('delete_post','spoiler') and target is not null) then
    insert into private.board_audit(board_id,actor_id,subject_id,post_id,action,reason,central) values(bid,actor,target,pid,p_operation,reason,central or left(p_operation,6)='staff_');
  end if;
  if bid is not null then
    -- Invalidate even muted members' caches without generating activity alerts.
    perform private.board_invalidate(bid);
  end if;
  result:=result||jsonb_build_object('ok',true);
  insert into private.board_operations(user_id,operation_id,operation,request_hash,response,board_id)
    values(actor,p_operation_id,p_operation,payload_hash,result,bid);
  return result;
end $$;
revoke all on function public.boards_mutate(text,jsonb,uuid) from public,anon;
grant execute on function public.boards_mutate(text,jsonb,uuid) to authenticated;

create function private.board_cleanup() returns void
language plpgsql security definer set search_path='' as $$
begin
  delete from private.board_history h where expires_at<now()
    and not exists(select 1 from private.board_reports r where r.board_id=h.board_id and (r.post_id=h.post_id or r.post_id is null) and r.status='open')
    and not exists(select 1 from private.board_appeals a where a.board_id=h.board_id and a.user_id=h.subject_id and a.status='open');
  delete from private.board_filter_reviews where status='resolved' and created_at<now()-interval '30 days';
  delete from private.board_rate_events where occurred_at<now()-interval '2 minutes';
  delete from private.board_events where updated_at<now()-interval '90 days';
  -- Superseded drafts are no longer current versions and need not persist forever.
  delete from private.board_draft_versions where not is_head and created_at<now()-interval '30 days';
  delete from private.board_assets a where created_at<now()-interval '30 days'
    and not exists(select 1 from private.boards b where b.icon_asset_id=a.id or b.cover_asset_id=a.id)
    and not exists(select 1 from private.board_reports r where r.board_id=a.board_id and r.status='open')
    and not exists(select 1 from private.board_appeals r where r.board_id=a.board_id and r.status='open');
  delete from private.board_viewers where seen_at<now()-interval '1 hour';
  delete from private.board_upload_tickets t where created_at<now()-interval '1 day'
    and not exists(select 1 from private.board_operations o where o.user_id=t.user_id and o.operation_id=t.operation_id);
end $$;
revoke all on function private.board_emit(uuid,uuid,uuid,text),private.board_cleanup() from public,anon,authenticated;
do $$ begin
  if exists(select 1 from pg_extension where extname='pg_cron') then
    perform cron.schedule('pocketpass-boards-retention','23 3 * * *','select private.board_cleanup();');
  end if;
end $$;
notify pgrst,'reload schema';
commit;
