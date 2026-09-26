begin;

alter table private.boards add column icon_drawing jsonb, add column cover_drawing jsonb;
create table private.board_upload_tickets (
  id uuid primary key default gen_random_uuid(),
  board_id uuid not null references private.boards(id) on delete cascade,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  operation_id uuid not null,
  kind text not null check(kind in ('icon','cover')),
  source_hash text not null check(source_hash ~ '^[a-f0-9]{64}$'),
  created_at timestamptz not null default now(),
  unique(user_id,operation_id)
);
alter table private.board_upload_tickets enable row level security;
revoke all on private.board_upload_tickets from public,anon,authenticated;

create function private.board_require_branding(p_board uuid) returns boolean
language plpgsql security definer set search_path='' as $$
begin
  if not exists(select 1 from private.boards where id=p_board) then raise exception 'Board unavailable' using errcode='42501'; end if;
  if private.board_role(p_board,auth.uid())='owner' and not private.board_restricted(p_board,auth.uid()) then
    perform private.board_require_read(p_board); return false;
  end if;
  perform private.require_permission('boards');
  if (select visibility='private' from private.boards where id=p_board) then perform private.require_permission('board_private_review'); end if;
  return true;
end $$;

create function public.prepare_board_branding(p_board_id uuid,p_kind text,p_operation_id uuid,p_source_hash text) returns uuid
language plpgsql security definer set search_path='' as $$
declare actor uuid:=private.board_actor(); ticket private.board_upload_tickets;
begin
  perform private.board_require_branding(p_board_id);
  if p_kind not in ('icon','cover') or p_kind is null or p_operation_id is null or p_source_hash !~ '^[a-f0-9]{64}$' or p_source_hash is null then
    raise exception 'Invalid image upload' using errcode='22023'; end if;
  perform pg_advisory_xact_lock(hashtextextended(actor::text||':boards:operation',0));
  select * into ticket from private.board_upload_tickets where user_id=actor and operation_id=p_operation_id;
  if found then
    if ticket.board_id<>p_board_id or ticket.kind<>p_kind or ticket.source_hash<>p_source_hash then raise exception 'Upload ID already used' using errcode='22023'; end if;
    return ticket.id;
  end if;
  if exists(select 1 from private.board_operations where user_id=actor and operation_id=p_operation_id) then raise exception 'Operation ID already used' using errcode='22023'; end if;
  perform private.board_rate('submit');
  insert into private.board_upload_tickets(board_id,user_id,operation_id,kind,source_hash)
    values(p_board_id,actor,p_operation_id,p_kind,p_source_hash) returning * into ticket;
  return ticket.id;
end $$;

-- Only the internal image processor may commit freshly decoded and re-encoded bytes.
create function public.commit_board_branding(p_ticket uuid,p_data text,p_width integer,p_height integer) returns jsonb
language plpgsql security definer set search_path='' as $$
declare ticket private.board_upload_tickets; actor uuid; central boolean; asset uuid:=gen_random_uuid();
  content bytea; response jsonb; b private.boards; previous_claims text;
begin
  select * into ticket from private.board_upload_tickets where id=p_ticket;
  if not found then raise exception 'Upload unavailable' using errcode='42501'; end if;
  actor:=ticket.user_id;
  perform pg_advisory_xact_lock(hashtextextended(actor::text||':boards:operation',0));
  previous_claims:=current_setting('request.jwt.claims',true);
  perform set_config('request.jwt.claims',jsonb_build_object('sub',actor,'role','authenticated')::text,true);
  perform private.board_actor(); central:=private.board_require_branding(ticket.board_id);
  select * into b from private.boards where id=ticket.board_id for update;
  select o.response into response from private.board_operations o where user_id=actor and operation_id=ticket.operation_id;
  if response is null then
    if ticket.created_at<now()-interval '1 hour' then raise exception 'Upload expired. Choose the image again.' using errcode='22023'; end if;
    content:=decode(p_data,'base64');
    if octet_length(content)>2097152 or octet_length(content)<12 or substring(content from 1 for 4)<>decode('52494646','hex') or substring(content from 9 for 4)<>decode('57454250','hex') then raise exception 'Invalid processed image' using errcode='22023'; end if;
    insert into private.board_history(board_id,subject_id,content) values(b.id,b.owner_id,to_jsonb(b));
    insert into private.board_assets(id,board_id,owner_id,mime,bytes,width,height) values(asset,b.id,actor,'image/webp',content,p_width,p_height);
    if ticket.kind='icon' then update private.boards set icon_asset_id=asset,icon_drawing=null,updated_at=now(),access_revision=access_revision+1 where id=b.id;
    else update private.boards set cover_asset_id=asset,cover_drawing=null,updated_at=now(),access_revision=access_revision+1 where id=b.id; end if;
    insert into private.board_audit(board_id,actor_id,action,central) values(b.id,actor,'branding_import',central);
    response:=jsonb_build_object('ok',true,'board_id',b.id,'asset_id',asset);
    insert into private.board_operations(user_id,operation_id,operation,request_hash,response,board_id) values(actor,ticket.operation_id,'branding_import',ticket.source_hash,response,b.id);
    perform private.board_emit(b.id,null,actor,'access');
  end if;
  perform set_config('request.jwt.claims',coalesce(previous_claims,''),true);
  return response;
end $$;
revoke all on function private.board_require_branding(uuid),public.prepare_board_branding(uuid,text,uuid,text),public.commit_board_branding(uuid,text,integer,integer) from public,anon,authenticated;
grant execute on function public.prepare_board_branding(uuid,text,uuid,text) to authenticated;
grant execute on function public.commit_board_branding(uuid,text,integer,integer) to service_role;
create function private.board_block_changed() returns trigger
language plpgsql security definer set search_path='' as $$
declare a uuid; b uuid;
begin
  if tg_op='DELETE' then a:=old.blocker_id; b:=old.blocked_id; else a:=new.blocker_id; b:=new.blocked_id; end if;
  perform realtime.send('{}'::jsonb,'BOARDS','notifications:'||a,true);
  perform realtime.send('{}'::jsonb,'BOARDS','notifications:'||b,true);
  return null;
end $$;
revoke all on function private.board_block_changed() from public,anon,authenticated;
create trigger user_blocks_boards after insert or delete on public.user_blocks for each row execute function private.board_block_changed();
notify pgrst,'reload schema';
commit;
