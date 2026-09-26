begin;

alter table private.board_upload_tickets add column client_id uuid default private.api_try_uuid(auth.jwt()->>'client_id');
alter table private.board_audit add column client_id uuid default private.api_try_uuid(auth.jwt()->>'client_id');

-- Used by the media processor with the caller's original bearer token. The
-- untrusted client receives only a ticket; only service_role may commit pixels.
create function public.api_v1_boards_prepare_branding(jsonb default '{}') returns jsonb
language plpgsql security definer set search_path='' as $$
declare guard jsonb:=private.api_guard('boards:manage'); args jsonb:=coalesce($1,'{}'); ticket uuid;
  state text; message text; hint text;
begin
  if guard ? 'code' then return guard; end if;
  begin
    perform private.api_reject_unknown(args,array['board_id','kind','operation_id','source_hash']);
    ticket:=public.prepare_board_branding(private.api_arg_uuid(args,'board_id',true),
      private.api_arg_text(args,'kind',true,5),private.api_arg_uuid(args,'operation_id',true),
      private.api_arg_text(args,'source_hash',true,64));
    -- A ticket already issued to the native app or another client stays theirs.
    if exists(select 1 from private.board_upload_tickets where id=ticket
      and client_id is distinct from (guard->>'client_id')::uuid) then
      raise exception 'Upload ID already used' using errcode='22023';
    end if;
    return jsonb_build_object('ticket',ticket);
  exception when sqlstate '40001' or sqlstate '40P01' then raise;
    when others then get stacked diagnostics state=returned_sqlstate,message=message_text,hint=pg_exception_hint;
      return private.api_boards_failure(state,message,hint);
  end;
end $$;
revoke all on function public.api_v1_boards_prepare_branding(jsonb) from public,anon,authenticated,service_role;
grant execute on function public.api_v1_boards_prepare_branding(jsonb) to api_client;

create or replace function public.commit_board_branding(p_ticket uuid,p_data text,p_width integer,p_height integer) returns jsonb
language plpgsql security definer set search_path='' as $$
declare ticket private.board_upload_tickets; actor uuid; central boolean; asset uuid:=gen_random_uuid();
  content bytea; response jsonb; b private.boards; previous_claims text; replay private.board_operations;
begin
  select * into ticket from private.board_upload_tickets where id=p_ticket;
  if not found then raise exception 'Upload unavailable' using errcode='42501'; end if;
  actor:=ticket.user_id;
  perform pg_advisory_xact_lock(hashtextextended(actor::text||':boards:operation',0));
  previous_claims:=current_setting('request.jwt.claims',true);
  perform set_config('request.jwt.claims',jsonb_build_object('sub',actor,
    'role',case when ticket.client_id is null then 'authenticated' else 'api_client' end,
    'client_id',ticket.client_id)::text,true);
  -- Recheck consent, app suspension, scope and board ownership after decoding.
  -- Losing any of them while pixels are processed must prevent publication.
  if ticket.client_id is not null and not private.api_has_scope('boards:manage') then
    raise exception 'Board artwork permission is no longer available' using errcode='42501';
  end if;
  perform private.board_actor(); central:=private.board_require_branding(ticket.board_id);
  select * into b from private.boards where id=ticket.board_id for update;
  select * into replay from private.board_operations o where user_id=actor and operation_id=ticket.operation_id;
  if found then
    if replay.operation<>'branding_import' or replay.request_hash<>ticket.source_hash or replay.board_id is distinct from ticket.board_id then
      raise sqlstate 'PT409' using message='This operation ID was already used for a different request',hint='DUPLICATE_OPERATION_ID';
    end if;
    response:=replay.response;
  else
    if ticket.created_at<now()-interval '1 hour' then raise exception 'Upload expired. Choose the image again.' using errcode='22023'; end if;
    content:=decode(p_data,'base64');
    if p_width is null or p_height is null or p_width not between 1 and 2048 or p_height not between 1 and 2048
      or octet_length(content)>2097152 or octet_length(content)<12
      or substring(content from 1 for 4)<>decode('52494646','hex') or substring(content from 9 for 4)<>decode('57454250','hex') then
      raise exception 'Invalid processed image' using errcode='22023';
    end if;
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

notify pgrst,'reload schema';
commit;
