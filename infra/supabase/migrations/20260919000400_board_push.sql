begin;

alter table private.message_push_devices add column boards_enabled boolean not null default false;

create function public.register_board_push_device(p_installation_id uuid,p_token text,p_message_enabled boolean,p_board_enabled boolean)
returns uuid language plpgsql security definer set search_path='' as $$
declare binding uuid;
begin
  if p_board_enabled is null then raise exception 'A board push preference is required' using errcode='22023'; end if;
  binding:=public.register_message_push_device(p_installation_id,p_token,p_message_enabled);
  update private.message_push_devices set boards_enabled=p_board_enabled
    where installation_id=p_installation_id and user_id=auth.uid();
  return binding;
end $$;
revoke all on function public.register_board_push_device(uuid,text,boolean,boolean) from public,anon;
grant execute on function public.register_board_push_device(uuid,text,boolean,boolean) to authenticated;

create table private.board_push_queue (
  id uuid primary key default gen_random_uuid(),
  installation_id uuid not null references private.message_push_devices(installation_id) on delete cascade,
  binding_id uuid not null,
  event_id uuid not null references private.board_events(id) on delete cascade,
  event_count integer not null,
  created_at timestamptz not null default now(),
  available_at timestamptz not null default(now()+interval '3 seconds'),
  attempts integer not null default 0,
  lease_id uuid,
  lease_until timestamptz,
  unique(installation_id,event_id)
);
alter table private.board_push_queue enable row level security;
revoke all on private.board_push_queue from public,anon,authenticated;
create index board_push_ready_idx on private.board_push_queue(available_at);

create function private.board_push_eligible(p_event uuid,p_installation uuid,p_binding uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select 1 from private.board_events e join private.message_push_devices d on d.user_id=e.recipient_id
    join private.board_members m on m.board_id=e.board_id and m.user_id=e.recipient_id
    left join private.board_preferences pref on pref.user_id=e.recipient_id
    where e.id=p_event and d.installation_id=p_installation and d.binding_id=p_binding and d.boards_enabled
      and d.refreshed_at>now()-interval '30 days' and e.push_pending and e.read_at is null and e.kind<>'report'
      and coalesce(pref.push_enabled,true) and not m.muted and m.push_enabled
      and (select enabled from private.board_settings) and private.board_can_read(e.board_id,e.recipient_id)
      and not private.board_blocked(e.recipient_id,e.actor_id)
      and (e.thread_id is null or not private.board_blocked(e.recipient_id,(select author_id from private.board_posts where id=e.thread_id))));
$$;
create function private.board_enqueue_push() returns trigger
language plpgsql security definer set search_path='' as $$
begin
  if not new.push_pending or new.read_at is not null or new.kind='report' then return new; end if;
  if tg_op='UPDATE' and new.event_count<=old.event_count then return new; end if;
  insert into private.board_push_queue(installation_id,binding_id,event_id,event_count)
    select d.installation_id,d.binding_id,new.id,new.event_count from private.message_push_devices d
    where d.user_id=new.recipient_id and private.board_push_eligible(new.id,d.installation_id,d.binding_id)
    on conflict(installation_id,event_id) do update set binding_id=excluded.binding_id,event_count=excluded.event_count,
      created_at=now(),available_at=least(board_push_queue.available_at,excluded.available_at),attempts=0,lease_id=null,lease_until=null;
  return new;
end $$;
create trigger board_events_push after insert or update on private.board_events for each row execute function private.board_enqueue_push();

create function public.claim_board_push_batch() returns jsonb
language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
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

create function public.finish_board_push(p_id uuid,p_lease_id uuid,p_outcome text) returns void
language plpgsql security definer set search_path='' as $$
declare job private.board_push_queue;
begin
  select * into job from private.board_push_queue where id=p_id and lease_id=p_lease_id for update;
  if not found then return; end if;
  if p_outcome='sent' then delete from private.board_push_queue where id=p_id;
  elsif p_outcome='unregistered' then delete from private.message_push_devices where installation_id=job.installation_id and binding_id=job.binding_id;
  elsif p_outcome='retry' then update private.board_push_queue set lease_id=null,lease_until=null,available_at=now()+make_interval(secs=>least(300,5*(2^least(attempts,6))::integer)) where id=p_id;
  else raise exception 'Unknown push outcome' using errcode='22023'; end if;
end $$;
revoke all on function public.claim_board_push_batch(),public.finish_board_push(uuid,uuid,text),private.board_enqueue_push(),private.board_push_eligible(uuid,uuid,uuid) from public,anon,authenticated;
grant execute on function public.claim_board_push_batch(),public.finish_board_push(uuid,uuid,text) to service_role;
notify pgrst,'reload schema';
commit;
