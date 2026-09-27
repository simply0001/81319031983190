begin;

create or replace function public.claim_message_push_batch()
returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare v_result jsonb;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'Service role required' using errcode = '42501';
  end if;
  delete from private.message_push_devices where refreshed_at < now() - interval '30 days';
  delete from private.message_push_queue as queue
  where queue.created_at < now() - interval '15 minutes' or queue.attempts >= 8
    or not exists (
      select 1 from private.message_push_devices as device
      join public.notifications as notification on notification.id = queue.notification_id
      join public.conversation_members as member on member.user_id = device.user_id and member.conversation_id = notification.conversation_id
      where device.installation_id = queue.installation_id and device.binding_id = queue.binding_id and device.enabled
        and device.user_id = notification.recipient_id and member.left_at is null
        and notification.read_at is null and notification.deleted_at is null
        and notification.event_count = queue.event_count
        and coalesce(member.last_read_at, '-infinity') < notification.updated_at
        and not private.has_block_between(notification.recipient_id, notification.actor_id)
    );
  with candidates as (
    select id from private.message_push_queue
    where available_at <= now() and (lease_until is null or lease_until < now())
    order by available_at for update skip locked limit 10
  ), leased as (
    update private.message_push_queue as queue set lease_id = gen_random_uuid(), lease_until = now() + interval '5 minutes', attempts = attempts + 1
    from candidates where queue.id = candidates.id returning queue.*
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', leased.id, 'lease_id', leased.lease_id, 'token', device.token, 'platform', device.platform,
    'data', jsonb_build_object(
      'version', '1', 'type', 'message', 'recipient_id', device.user_id::text,
      'binding_id', device.binding_id::text, 'conversation_id', notification.conversation_id::text,
      'notification_id', notification.id::text, 'event_count', leased.event_count::text,
      'title', case when device.platform = 'ios' then 'PocketPass' else left(notification.title, 120) end,
      'body', case when device.platform = 'ios' then 'You have a new message.' else left(notification.body, 300) end
    )
  )), '[]'::jsonb) into v_result
  from leased join private.message_push_devices as device using (installation_id)
  join public.notifications as notification on notification.id = leased.notification_id;
  return v_result;
end;
$$;

create or replace function public.finish_message_push(p_id uuid, p_lease_id uuid, p_outcome text)
returns void
language plpgsql security definer set search_path = ''
as $$
declare v_job private.message_push_queue;
begin
  if auth.role() is distinct from 'service_role' then
    raise exception 'Service role required' using errcode = '42501';
  end if;
  if p_outcome not in ('sent', 'retry', 'unregistered') then
    raise exception 'Invalid push outcome' using errcode = '22023';
  end if;
  select * into v_job from private.message_push_queue where id = p_id and lease_id = p_lease_id for update;
  if not found then return; end if;
  if p_outcome = 'unregistered' then
    delete from private.message_push_devices where installation_id = v_job.installation_id and binding_id = v_job.binding_id;
  elsif p_outcome = 'sent' then
    delete from private.message_push_queue where id = p_id;
  else
    update private.message_push_queue set lease_id = null, lease_until = null,
      available_at = now() + make_interval(secs => least(300, power(2, attempts)::integer))
    where id = p_id;
  end if;
end;
$$;

revoke all on function public.claim_message_push_batch() from public, anon, authenticated;
revoke all on function public.finish_message_push(uuid, uuid, text) from public, anon, authenticated;
grant execute on function public.claim_message_push_batch() to service_role;
grant execute on function public.finish_message_push(uuid, uuid, text) to service_role;

create or replace function public.claim_board_push_batch() returns jsonb
language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
  if auth.role() is distinct from 'service_role' then raise exception 'Service role required' using errcode='42501'; end if;
  delete from private.board_push_queue q where q.created_at<now()-interval '15 minutes' or q.attempts>=8
    or not private.board_push_eligible(q.event_id,q.installation_id,q.binding_id)
    or q.event_count<>(select event_count from private.board_events where id=q.event_id);
  with candidates as (
    select id from private.board_push_queue where available_at<=now() and (lease_until is null or lease_until<now())
      order by available_at for update skip locked limit 10
  ), leased as (
    update private.board_push_queue q set lease_id=gen_random_uuid(),lease_until=now()+interval '5 minutes',attempts=attempts+1
      from candidates c where q.id=c.id returning q.*
  ) select coalesce(jsonb_agg(jsonb_build_object('id',l.id,'lease_id',l.lease_id,'token',d.token,'platform',d.platform,
    'data',jsonb_build_object('version','1','type','board','recipient_id',d.user_id::text,'binding_id',d.binding_id::text,
      'board_id',e.board_id::text,'thread_id',coalesce(e.thread_id::text,''),'notification_id',e.id::text,'event_count',l.event_count::text,
      'title','PocketPass Boards','body','There is new activity in your boards.'))),'[]') into result
    from leased l join private.message_push_devices d on d.installation_id=l.installation_id join private.board_events e on e.id=l.event_id;
  return result;
end $$;

create or replace function public.finish_board_push(p_id uuid,p_lease_id uuid,p_outcome text) returns void
language plpgsql security definer set search_path='' as $$
declare job private.board_push_queue;
begin
  if auth.role() is distinct from 'service_role' then raise exception 'Service role required' using errcode='42501'; end if;
  select * into job from private.board_push_queue where id=p_id and lease_id=p_lease_id for update;
  if not found then return; end if;
  if p_outcome='sent' then delete from private.board_push_queue where id=p_id;
  elsif p_outcome='unregistered' then delete from private.message_push_devices where installation_id=job.installation_id and binding_id=job.binding_id;
  elsif p_outcome='retry' then update private.board_push_queue set lease_id=null,lease_until=null,available_at=now()+make_interval(secs=>least(300,5*(2^least(attempts,6))::integer)) where id=p_id;
  else raise exception 'Unknown push outcome' using errcode='22023'; end if;
end $$;
revoke all on function public.claim_board_push_batch(),public.finish_board_push(uuid,uuid,text) from public,anon,authenticated;
grant execute on function public.claim_board_push_batch(),public.finish_board_push(uuid,uuid,text) to service_role;

create or replace function public.commit_board_branding(p_ticket uuid,p_data text,p_width integer,p_height integer) returns jsonb
language plpgsql security definer set search_path='' as $$
declare ticket private.board_upload_tickets; actor uuid; central boolean; asset uuid:=gen_random_uuid();
  content bytea; response jsonb; b private.boards; previous_claims text; replay private.board_operations;
begin
  if auth.role() is distinct from 'service_role' then raise exception 'Service role required' using errcode='42501'; end if;
  select * into ticket from private.board_upload_tickets where id=p_ticket;
  if not found then raise exception 'Upload unavailable' using errcode='42501'; end if;
  actor:=ticket.user_id;
  perform pg_advisory_xact_lock(hashtextextended(actor::text||':boards:operation',0));
  previous_claims:=current_setting('request.jwt.claims',true);
  perform set_config('request.jwt.claims',jsonb_build_object('sub',actor,
    'role',case when ticket.client_id is null then 'authenticated' else 'api_client' end,
    'client_id',ticket.client_id)::text,true);
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
revoke all on function public.commit_board_branding(uuid,text,integer,integer) from public,anon,authenticated;
grant execute on function public.commit_board_branding(uuid,text,integer,integer) to service_role;

notify pgrst,'reload schema';
commit;
