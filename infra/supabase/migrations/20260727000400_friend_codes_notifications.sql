begin;

create table public.friend_codes (
  user_id uuid primary key references public.profiles (user_id) on delete cascade,
  code text not null unique,
  created_at timestamptz not null default now(),
  constraint friend_codes_eight_digits check (code ~ '^[0-9]{8}$')
);

create table private.friend_code_lookup_attempts (
  id bigint generated always as identity primary key,
  actor_id uuid not null references public.profiles (user_id) on delete cascade,
  attempted_at timestamptz not null default now()
);

create index friend_code_lookup_attempts_actor_time_idx
  on private.friend_code_lookup_attempts (actor_id, attempted_at desc);

create or replace function private.ensure_friend_code(p_user_id uuid)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_code text;
  v_attempt integer;
begin
  select friend_code.code
  into v_code
  from public.friend_codes as friend_code
  where friend_code.user_id = p_user_id;

  if found then
    return v_code;
  end if;

  for v_attempt in 1..64 loop
    v_code := lpad(
      (
        (
          hashtextextended(gen_random_uuid()::text, v_attempt::bigint)
          & 9223372036854775807
        ) % 100000000
      )::text,
      8,
      '0'
    );
    begin
      insert into public.friend_codes (user_id, code)
      values (p_user_id, v_code);
      return v_code;
    exception
      when unique_violation then
        if exists (
          select 1
          from public.friend_codes
          where user_id = p_user_id
        ) then
          select code into v_code
          from public.friend_codes
          where user_id = p_user_id;
          return v_code;
        end if;
    end;
  end loop;

  raise exception 'Friend code could not be generated' using errcode = '40001';
end;
$$;

revoke all on function private.ensure_friend_code(uuid) from public;

create or replace function private.create_friend_code_for_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.ensure_friend_code(new.user_id);
  return new;
end;
$$;

revoke all on function private.create_friend_code_for_profile() from public;

create trigger profiles_create_friend_code
after insert on public.profiles
for each row execute function private.create_friend_code_for_profile();

select private.ensure_friend_code(profile.user_id)
from public.profiles as profile;

alter table public.friend_codes enable row level security;

create policy friend_codes_select_owner
on public.friend_codes
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.friend_codes from anon, authenticated;
grant select on table public.friend_codes to authenticated;

create or replace function public.get_my_friend_code()
returns table (code text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  return query select private.ensure_friend_code(v_actor_id);
end;
$$;

create or replace function public.resolve_friend_code(p_code text)
returns table (
  user_id uuid,
  display_name text,
  bio text,
  avatar_path text,
  age smallint,
  country_code text,
  last_seen_at timestamptz,
  updated_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_attempt_count bigint;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_code is null or p_code !~ '^[0-9]{8}$' then
    raise exception 'Friend code is invalid' using errcode = '22023';
  end if;

  delete from private.friend_code_lookup_attempts
  where actor_id = v_actor_id
    and attempted_at < now() - interval '24 hours';

  insert into private.friend_code_lookup_attempts (actor_id)
  values (v_actor_id);

  select count(*)
  into v_attempt_count
  from private.friend_code_lookup_attempts
  where actor_id = v_actor_id
    and attempted_at >= now() - interval '1 hour';

  if v_attempt_count > 50 then
    raise sqlstate 'PT429' using message = 'friend_code_rate_limited';
  end if;

  return query
  select
    profile.user_id,
    profile.display_name,
    profile.bio,
    profile.avatar_path,
    profile.age,
    profile.country_code,
    profile.last_seen_at,
    profile.updated_at
  from public.friend_codes as friend_code
  join public.profiles as profile on profile.user_id = friend_code.user_id
  where friend_code.code = p_code
    and profile.user_id <> v_actor_id
    and not private.has_block_between(v_actor_id, profile.user_id)
  limit 1;
end;
$$;

revoke all on function public.get_my_friend_code() from public;
revoke all on function public.resolve_friend_code(text) from public;
grant execute on function public.get_my_friend_code() to authenticated;
grant execute on function public.resolve_friend_code(text) to authenticated;

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references public.profiles (user_id) on delete cascade,
  kind text not null,
  actor_id uuid references public.profiles (user_id) on delete set null,
  friend_request_id uuid references public.friend_requests (id) on delete set null,
  friend_request_status text,
  conversation_id uuid references public.conversations (id) on delete cascade,
  title text not null,
  body text not null default '',
  event_count integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  read_at timestamptz,
  deleted_at timestamptz,
  constraint notifications_kind_check check (
    kind in ('friend_request', 'friend_accepted', 'message', 'system')
  ),
  constraint notifications_friend_status_check check (
    friend_request_status is null
    or friend_request_status in ('pending', 'accepted', 'declined')
  ),
  constraint notifications_event_count_positive check (event_count > 0),
  constraint notifications_shape_check check (
    (kind = 'friend_request' and friend_request_id is not null)
    or (kind = 'friend_accepted' and friend_request_id is not null)
    or (kind = 'message' and conversation_id is not null)
    or kind = 'system'
  )
);

create unique index notifications_friend_request_recipient_idx
  on public.notifications (recipient_id, friend_request_id, kind)
  where friend_request_id is not null;

create unique index notifications_message_group_idx
  on public.notifications (recipient_id, conversation_id)
  where kind = 'message';

create index notifications_recipient_updated_idx
  on public.notifications (recipient_id, updated_at desc)
  where deleted_at is null;

create index notifications_recipient_unread_idx
  on public.notifications (recipient_id, updated_at desc)
  where read_at is null and deleted_at is null;

alter table public.notifications enable row level security;

create policy notifications_select_recipient
on public.notifications
for select
to authenticated
using (recipient_id = auth.uid());

revoke all on table public.notifications from anon, authenticated;
grant select on table public.notifications to authenticated;

create or replace function private.notify_friend_request_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_requester_name text;
  v_addressee_name text;
begin
  select display_name into v_requester_name
  from public.profiles where user_id = new.requester_id;

  select display_name into v_addressee_name
  from public.profiles where user_id = new.addressee_id;

  if tg_op = 'INSERT' then
    insert into public.notifications (
      recipient_id,
      kind,
      actor_id,
      friend_request_id,
      friend_request_status,
      title,
      body
    )
    values (
      new.addressee_id,
      'friend_request',
      new.requester_id,
      new.id,
      'pending',
      'Friend request',
      coalesce(v_requester_name, 'Someone') || ' wants to be friends'
    )
    on conflict (recipient_id, friend_request_id, kind)
      where friend_request_id is not null
    do nothing;
    return new;
  end if;

  if old.status = new.status then
    return new;
  end if;

  update public.notifications
  set
    friend_request_status = case
      when new.status = 'accepted' then 'accepted'
      else 'declined'
    end,
    title = case
      when new.status = 'accepted' then 'Friend request accepted'
      else 'Friend request declined'
    end,
    body = case
      when new.status = 'accepted'
        then 'You and ' || coalesce(v_requester_name, 'this person') || ' are now friends'
      else 'Friend request declined'
    end,
    updated_at = now()
  where recipient_id = new.addressee_id
    and friend_request_id = new.id
    and kind = 'friend_request';

  if new.status = 'accepted' then
    insert into public.notifications (
      recipient_id,
      kind,
      actor_id,
      friend_request_id,
      friend_request_status,
      title,
      body
    )
    values (
      new.requester_id,
      'friend_accepted',
      new.addressee_id,
      new.id,
      'accepted',
      'Friend request accepted',
      coalesce(v_addressee_name, 'Your friend') || ' accepted your request'
    )
    on conflict (recipient_id, friend_request_id, kind)
      where friend_request_id is not null
    do update set
      actor_id = excluded.actor_id,
      body = excluded.body,
      read_at = null,
      deleted_at = null,
      updated_at = now();
  end if;

  return new;
end;
$$;

revoke all on function private.notify_friend_request_change() from public;

create trigger friend_requests_create_notification
after insert or update of status on public.friend_requests
for each row execute function private.notify_friend_request_change();

create or replace function private.notify_message_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_sender_name text;
begin
  select display_name into v_sender_name
  from public.profiles where user_id = new.sender_id;

  insert into public.notifications (
    recipient_id,
    kind,
    actor_id,
    conversation_id,
    title,
    body,
    event_count,
    created_at,
    updated_at
  )
  select
    member.user_id,
    'message',
    new.sender_id,
    new.conversation_id,
    coalesce(v_sender_name, 'New message'),
    left(new.body, 240),
    1,
    new.created_at,
    new.created_at
  from public.conversation_members as member
  where member.conversation_id = new.conversation_id
    and member.user_id <> new.sender_id
    and member.left_at is null
  on conflict (recipient_id, conversation_id)
    where kind = 'message'
  do update set
    actor_id = excluded.actor_id,
    title = excluded.title,
    body = excluded.body,
    event_count = public.notifications.event_count + 1,
    read_at = null,
    deleted_at = null,
    updated_at = excluded.updated_at;

  return new;
end;
$$;

revoke all on function private.notify_message_insert() from public;

create trigger messages_create_notification
after insert on public.messages
for each row execute function private.notify_message_insert();

create or replace function public.mark_notification_read(
  p_notification_id uuid,
  p_read_at timestamptz default now()
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  update public.notifications
  set read_at = coalesce(read_at, p_read_at)
  where id = p_notification_id
    and recipient_id = auth.uid()
    and deleted_at is null;
end;
$$;

create or replace function public.mark_all_notifications_read(
  p_read_at timestamptz default now()
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  update public.notifications
  set read_at = coalesce(read_at, p_read_at)
  where recipient_id = auth.uid()
    and deleted_at is null;
end;
$$;

create or replace function public.delete_notification(
  p_notification_id uuid,
  p_deleted_at timestamptz default now()
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_notification public.notifications;
begin
  if auth.uid() is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  select notification.*
  into v_notification
  from public.notifications as notification
  where notification.id = p_notification_id
    and notification.recipient_id = auth.uid()
  for update;

  if not found then
    return;
  end if;

  if v_notification.kind = 'friend_request'
    and v_notification.friend_request_status = 'pending'
  then
    raise sqlstate 'PT409' using
      message = 'respond_to_friend_request_before_delete';
  end if;

  update public.notifications
  set deleted_at = coalesce(deleted_at, p_deleted_at)
  where id = p_notification_id;
end;
$$;

create or replace function public.publish_system_notification(
  p_title text,
  p_body text,
  p_recipient_id uuid default null
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  if auth.role() <> 'service_role' then
    raise exception 'Service role required' using errcode = '42501';
  end if;

  if nullif(btrim(p_title), '') is null then
    raise exception 'Title is required' using errcode = '22023';
  end if;

  insert into public.notifications (
    recipient_id,
    kind,
    title,
    body
  )
  select
    profile.user_id,
    'system',
    left(btrim(p_title), 120),
    left(coalesce(p_body, ''), 500)
  from public.profiles as profile
  where p_recipient_id is null or profile.user_id = p_recipient_id;

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

revoke all on function public.mark_notification_read(uuid, timestamptz) from public;
revoke all on function public.mark_all_notifications_read(timestamptz) from public;
revoke all on function public.delete_notification(uuid, timestamptz) from public;
revoke all on function public.publish_system_notification(text, text, uuid) from public;
grant execute on function public.mark_notification_read(uuid, timestamptz) to authenticated;
grant execute on function public.mark_all_notifications_read(timestamptz) to authenticated;
grant execute on function public.delete_notification(uuid, timestamptz) to authenticated;
grant execute on function public.publish_system_notification(text, text, uuid) to service_role;

create or replace function private.notification_user_id_from_topic(p_topic text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when p_topic
      ~ '^notifications:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    then split_part(p_topic, ':', 2)::uuid
    else null
  end;
$$;

create or replace function private.can_access_realtime_topic(
  p_topic text,
  p_user_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    private.is_active_conversation_member(
      private.conversation_id_from_topic(p_topic),
      p_user_id
    )
    or private.notification_user_id_from_topic(p_topic) = p_user_id;
$$;

revoke all on function private.notification_user_id_from_topic(text) from public;
grant execute on function private.notification_user_id_from_topic(text) to authenticated;

create or replace function private.broadcast_notification_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_recipient_id uuid;
begin
  v_recipient_id := case when tg_op = 'DELETE'
    then old.recipient_id
    else new.recipient_id
  end;

  perform realtime.broadcast_changes(
    'notifications:' || v_recipient_id::text,
    tg_op,
    tg_op,
    tg_table_name,
    tg_table_schema,
    new,
    old
  );

  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end;
$$;

revoke all on function private.broadcast_notification_change() from public;

create trigger notifications_broadcast_change
after insert or update or delete on public.notifications
for each row execute function private.broadcast_notification_change();

create or replace function private.prune_expired_notifications()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.notifications
  where updated_at < now() - interval '90 days'
    and not (
      kind = 'friend_request'
      and friend_request_status = 'pending'
    );
$$;

revoke all on function private.prune_expired_notifications() from public;

do $schedule$
begin
  if exists (
    select 1 from pg_available_extensions where name = 'pg_cron'
  ) then
    create extension if not exists pg_cron;
    if not exists (
      select 1 from cron.job where jobname = 'pocketpass-prune-notifications'
    ) then
      perform cron.schedule(
        'pocketpass-prune-notifications',
        '17 3 * * *',
        'select private.prune_expired_notifications();'
      );
    end if;
  end if;
end;
$schedule$;

commit;
