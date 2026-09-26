begin;

create table private.message_push_devices (
  installation_id uuid primary key,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  session_id uuid not null references auth.sessions(id) on delete cascade,
  token text not null unique check (length(token) between 50 and 4096),
  binding_id uuid not null default gen_random_uuid(),
  enabled boolean not null default true,
  refreshed_at timestamptz not null default now()
);
create index message_push_devices_user_idx on private.message_push_devices(user_id);
create index message_push_devices_refresh_idx on private.message_push_devices(refreshed_at);
alter table private.message_push_devices enable row level security;
revoke all on private.message_push_devices from public, anon, authenticated;

create table private.message_push_queue (
  id uuid primary key default gen_random_uuid(),
  installation_id uuid not null references private.message_push_devices(installation_id) on delete cascade,
  binding_id uuid not null,
  notification_id uuid not null references public.notifications(id) on delete cascade,
  event_count integer not null,
  created_at timestamptz not null default now(),
  available_at timestamptz not null default now(),
  attempts integer not null default 0,
  lease_id uuid,
  lease_until timestamptz,
  unique (installation_id, notification_id)
);
create index message_push_queue_ready_idx on private.message_push_queue(available_at);
alter table private.message_push_queue enable row level security;
revoke all on private.message_push_queue from public, anon, authenticated;

create function public.register_message_push_device(p_installation_id uuid, p_token text, p_enabled boolean default true)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_session uuid := nullif(auth.jwt()->>'session_id', '')::uuid;
  v_binding uuid;
begin
  if v_user is null or coalesce(auth.jwt()->>'client_id', '') <> '' or not exists (
    select 1 from auth.sessions where id = v_session and user_id = v_user
  ) then
    raise exception 'A PocketPass session is required' using errcode = '42501';
  end if;
  if p_installation_id is null or p_token is null or length(p_token) not between 50 and 4096 or p_enabled is null then
    raise exception 'Invalid push registration' using errcode = '22023';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(v_user::text, 791));
  delete from private.message_push_devices where user_id = v_user and refreshed_at < now() - interval '30 days';
  if exists (select 1 from private.message_push_devices where installation_id = p_installation_id and user_id <> v_user and token <> p_token) then
    raise exception 'Installation belongs to another account' using errcode = '42501';
  end if;
  if not exists (select 1 from private.message_push_devices where installation_id = p_installation_id and user_id = v_user)
     and (select count(*) from private.message_push_devices where user_id = v_user) >= 20 then
    raise exception 'Too many registered devices' using errcode = '54000';
  end if;
  delete from private.message_push_devices where token = p_token and installation_id <> p_installation_id;
  insert into private.message_push_devices as device(installation_id, user_id, session_id, token, enabled)
  values (p_installation_id, v_user, v_session, p_token, p_enabled)
  on conflict (installation_id) do update set
    user_id = excluded.user_id,
    session_id = excluded.session_id,
    token = excluded.token,
    enabled = excluded.enabled,
    refreshed_at = now(),
    binding_id = case when device.user_id <> excluded.user_id or device.session_id <> excluded.session_id
      or device.token <> excluded.token or (not device.enabled and excluded.enabled)
      then gen_random_uuid() else device.binding_id end
  returning binding_id into v_binding;
  delete from private.message_push_queue where installation_id = p_installation_id and (not p_enabled or binding_id <> v_binding);
  return v_binding;
end;
$$;

create function public.unregister_message_push_device(p_installation_id uuid)
returns void
language sql security definer set search_path = ''
as $$
  delete from private.message_push_devices where installation_id = p_installation_id and user_id = auth.uid() and coalesce(auth.jwt()->>'client_id', '') = '';
$$;

revoke all on function public.register_message_push_device(uuid, text, boolean) from public, anon;
revoke all on function public.unregister_message_push_device(uuid) from public, anon;
grant execute on function public.register_message_push_device(uuid, text, boolean) to authenticated;
grant execute on function public.unregister_message_push_device(uuid) to authenticated;

create function private.enqueue_message_push()
returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if new.kind <> 'message' or new.read_at is not null or new.deleted_at is not null or new.actor_id = new.recipient_id then
    return new;
  end if;
  if tg_op = 'UPDATE' and new.event_count <= old.event_count then return new; end if;
  insert into private.message_push_queue(installation_id, binding_id, notification_id, event_count, available_at)
  select device.installation_id, device.binding_id, new.id, new.event_count, now() + interval '1 second'
  from private.message_push_devices as device
  join public.conversation_members as member on member.user_id = device.user_id and member.conversation_id = new.conversation_id
  where device.user_id = new.recipient_id and device.enabled and device.refreshed_at > now() - interval '30 days'
    and member.left_at is null and coalesce(member.last_read_at, '-infinity') < new.updated_at
    and not private.has_block_between(new.recipient_id, new.actor_id)
  on conflict (installation_id, notification_id) do update set
    binding_id = excluded.binding_id, event_count = excluded.event_count, created_at = now(),
    available_at = least(message_push_queue.available_at, excluded.available_at), attempts = 0, lease_id = null, lease_until = null;
  return new;
end;
$$;
revoke all on function private.enqueue_message_push() from public, anon, authenticated;
create trigger notifications_enqueue_message_push after insert or update on public.notifications
for each row execute function private.enqueue_message_push();

create function public.claim_message_push_batch()
returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare v_result jsonb;
begin
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
    'id', leased.id, 'lease_id', leased.lease_id, 'token', device.token,
    'data', jsonb_build_object(
      'version', '1', 'type', 'message', 'recipient_id', device.user_id::text,
      'binding_id', device.binding_id::text, 'conversation_id', notification.conversation_id::text,
      'notification_id', notification.id::text, 'event_count', leased.event_count::text,
      'title', left(notification.title, 120), 'body', left(notification.body, 300)
    )
  )), '[]'::jsonb) into v_result
  from leased join private.message_push_devices as device using (installation_id)
  join public.notifications as notification on notification.id = leased.notification_id;
  return v_result;
end;
$$;

create function public.finish_message_push(p_id uuid, p_lease_id uuid, p_outcome text)
returns void
language plpgsql security definer set search_path = ''
as $$
declare v_job private.message_push_queue;
begin
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

notify pgrst, 'reload schema';
commit;
