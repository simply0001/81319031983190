begin;

-- Existing Android clients keep their three-argument registration RPC.
alter table private.message_push_devices
  add column platform text not null default 'android' check (platform in ('android', 'ios'));

create function public.register_ios_message_push_device(p_installation_id uuid, p_token text)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare v_binding uuid;
begin
  -- Reuse the session, ownership, token, rate-limit and binding checks from Android.
  v_binding := public.register_message_push_device(p_installation_id, p_token, true);
  update private.message_push_devices set platform = 'ios'
    where installation_id = p_installation_id and user_id = auth.uid() and binding_id = v_binding;
  return v_binding;
end;
$$;
revoke all on function public.register_ios_message_push_device(uuid, text) from public, anon;
grant execute on function public.register_ios_message_push_device(uuid, text) to authenticated;

create or replace function public.claim_message_push_batch()
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

notify pgrst, 'reload schema';
commit;
