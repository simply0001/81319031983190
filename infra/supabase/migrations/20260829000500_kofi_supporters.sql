begin;

create table if not exists private.kofi_events (
  message_id uuid primary key,
  kofi_transaction_id text,
  event_type text not null,
  email text,
  from_name text,
  tier_name text,
  is_first_subscription_payment boolean,
  amount numeric(12, 2),
  currency text,
  paid_at timestamptz not null,
  payload jsonb not null,
  received_at timestamptz not null default now(),
  user_id uuid references public.profiles (user_id) on delete set null,
  matched_by text,
  qualifies boolean not null default false,
  applied_at timestamptz,
  granted_until timestamptz,
  error text,
  constraint kofi_events_matched_by_known check (
    matched_by is null or matched_by in ('email', 'link', 'signup', 'admin')
  ),
  constraint kofi_events_payload_object check (jsonb_typeof(payload) = 'object')
);

create index if not exists kofi_events_email_idx on private.kofi_events (email);
create index if not exists kofi_events_user_idx on private.kofi_events (user_id);
create index if not exists kofi_events_received_idx on private.kofi_events (received_at desc);
create index if not exists kofi_events_transaction_idx on private.kofi_events (kofi_transaction_id);

revoke all on table private.kofi_events from public, anon, authenticated;

create table if not exists private.kofi_links (
  email text primary key,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  created_by uuid,
  created_at timestamptz not null default now(),
  constraint kofi_links_email_lower check (email = lower(btrim(email)) and email <> '')
);

create index if not exists kofi_links_user_idx on private.kofi_links (user_id);

revoke all on table private.kofi_links from public, anon, authenticated;

create table if not exists public.supporter_status (
  user_id uuid primary key references public.profiles (user_id) on delete cascade,
  active_until timestamptz not null,
  source text not null default 'kofi',
  last_event_id uuid,
  lapsed_notified_at timestamptz,
  updated_at timestamptz not null default now(),
  constraint supporter_status_source_known check (source in ('kofi', 'admin'))
);

alter table public.supporter_status enable row level security;

drop policy if exists supporter_status_select_owner on public.supporter_status;
create policy supporter_status_select_owner
on public.supporter_status
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.supporter_status from public, anon, authenticated;
grant select on table public.supporter_status to authenticated;

create or replace function private.broadcast_supporter_status_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform realtime.send(
    jsonb_build_object('user_id', new.user_id, 'active_until', new.active_until),
    'supporter_status',
    'tokens:' || new.user_id::text,
    true
  );
  return new;
end;
$$;

revoke all on function private.broadcast_supporter_status_change() from public;

drop trigger if exists supporter_status_broadcast_change on public.supporter_status;
create trigger supporter_status_broadcast_change
after insert or update on public.supporter_status
for each row execute function private.broadcast_supporter_status_change();

create or replace function private.kofi_grant_interval()
returns interval
language sql
immutable
set search_path = ''
as $$
  select interval '36 days';
$$;

revoke all on function private.kofi_grant_interval() from public;

create or replace function private.is_supporter(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.supporter_status as status
    where status.user_id = p_user_id
      and status.active_until > now()
  );
$$;

revoke all on function private.is_supporter(uuid) from public;

create or replace function private.owns_mii_hat(p_user_id uuid, p_hat_type integer)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_hat_type is null
    or p_hat_type < 0
    or exists (
      select 1
      from public.user_shop_items as owned
      join public.shop_items as item on item.id = owned.item_id
      where owned.user_id = p_user_id
        and item.mii_hat_type = p_hat_type
    )
    or private.is_supporter(p_user_id);
$$;

revoke all on function private.owns_mii_hat(uuid, integer) from public;

create or replace function private.kofi_try_uuid(p_value text)
returns uuid
language plpgsql
immutable
set search_path = ''
as $$
begin
  return p_value::uuid;
exception
  when others then
    return null;
end;
$$;

revoke all on function private.kofi_try_uuid(text) from public;

create or replace function private.kofi_try_timestamptz(p_value text)
returns timestamptz
language plpgsql
immutable
set search_path = ''
as $$
begin
  return p_value::timestamptz;
exception
  when others then
    return null;
end;
$$;

revoke all on function private.kofi_try_timestamptz(text) from public;

create or replace function private.kofi_try_numeric(p_value text)
returns numeric
language plpgsql
immutable
set search_path = ''
as $$
begin
  return round(p_value::numeric, 2);
exception
  when others then
    return null;
end;
$$;

revoke all on function private.kofi_try_numeric(text) from public;

create or replace function private.grant_supporter(
  p_user_id uuid,
  p_until timestamptz,
  p_source text,
  p_event_id uuid,
  p_notify boolean
)
returns timestamptz
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_before timestamptz;
  v_after timestamptz;
begin
  select status.active_until
  into v_before
  from public.supporter_status as status
  where status.user_id = p_user_id
  for update;

  insert into public.supporter_status (user_id, active_until, source, last_event_id, updated_at)
  values (p_user_id, p_until, p_source, p_event_id, now())
  on conflict (user_id) do update
    set active_until = excluded.active_until,
        source = excluded.source,
        last_event_id = coalesce(excluded.last_event_id, public.supporter_status.last_event_id),
        lapsed_notified_at = null,
        updated_at = now()
    where excluded.active_until > public.supporter_status.active_until;

  select status.active_until
  into v_after
  from public.supporter_status as status
  where status.user_id = p_user_id;

  if p_notify
    and v_after > now()
    and (v_before is null or v_after > v_before)
  then
    insert into public.notifications (recipient_id, kind, title, body)
    values (
      p_user_id,
      'system',
      'Thanks for supporting PocketPass!',
      'Every hat is unlocked until ' || to_char(v_after, 'DD Mon YYYY') || '.'
    );
  end if;

  return v_after;
end;
$$;

revoke all on function private.grant_supporter(uuid, timestamptz, text, uuid, boolean) from public;

create or replace function private.kofi_resolve_user(p_email text)
returns table (user_id uuid, matched_by text)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if p_email is null or p_email = '' then
    return;
  end if;

  return query
  select link.user_id, 'link'::text
  from private.kofi_links as link
  where link.email = p_email
  limit 1;
  if found then
    return;
  end if;

  return query
  select account.id, 'email'::text
  from auth.users as account
  join public.profiles as profile on profile.user_id = account.id
  where lower(account.email::text) = p_email
    and account.deleted_at is null
  order by account.created_at
  limit 1;
end;
$$;

revoke all on function private.kofi_resolve_user(text) from public;

create or replace function private.kofi_apply_event(p_message_id uuid)
returns boolean
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_event private.kofi_events;
  v_user_id uuid;
  v_matched_by text;
  v_until timestamptz;
begin
  select event.*
  into v_event
  from private.kofi_events as event
  where event.message_id = p_message_id
  for update;
  if not found then
    return false;
  end if;

  if v_event.user_id is null then
    select resolved.user_id, resolved.matched_by
    into v_user_id, v_matched_by
    from private.kofi_resolve_user(v_event.email) as resolved;
    if v_user_id is not null then
      update private.kofi_events
      set user_id = v_user_id,
          matched_by = v_matched_by
      where message_id = p_message_id;
      v_event.user_id := v_user_id;
    end if;
  end if;

  if v_event.user_id is null or not v_event.qualifies then
    return false;
  end if;

  v_until := v_event.paid_at + private.kofi_grant_interval();
  if v_event.applied_at is not null and v_until <= now() then
    return false;
  end if;

  perform private.grant_supporter(v_event.user_id, v_until, 'kofi', p_message_id, true);

  update private.kofi_events
  set applied_at = coalesce(applied_at, now()),
      granted_until = v_until,
      error = null
  where message_id = p_message_id;
  return true;
exception
  when others then
    update private.kofi_events
    set error = left(sqlerrm, 500)
    where message_id = p_message_id;
    return false;
end;
$$;

revoke all on function private.kofi_apply_event(uuid) from public;

create or replace function private.notify_kofi_event(p_message_id uuid)
returns bigint
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_url text;
  v_event private.kofi_events;
  v_username text;
  v_until timestamptz;
  v_content text;
  v_request_id bigint;
begin
  select secret.decrypted_secret
  into v_url
  from vault.decrypted_secrets as secret
  where secret.name = 'discord_supporters_webhook'
  order by secret.created_at desc
  limit 1;
  if v_url is null
    or v_url !~ '^https://(discord\.com|discordapp\.com|ptb\.discord\.com|canary\.discord\.com)/api/webhooks/'
  then
    return null;
  end if;

  select event.*
  into v_event
  from private.kofi_events as event
  where event.message_id = p_message_id;
  if not found then
    return null;
  end if;

  if v_event.user_id is not null then
    select profile.username::text
    into v_username
    from public.profiles as profile
    where profile.user_id = v_event.user_id;
    select status.active_until
    into v_until
    from public.supporter_status as status
    where status.user_id = v_event.user_id;
  end if;

  v_content := format(
    '☕ Ko-fi %s from %s: %s %s%s%s',
    v_event.event_type,
    coalesce(nullif(v_event.from_name, ''), 'anonymous'),
    coalesce(v_event.amount::text, '?'),
    coalesce(v_event.currency, ''),
    case when v_event.tier_name is null or v_event.tier_name = '' then '' else ' (tier ' || v_event.tier_name || ')' end,
    case
      when not v_event.qualifies then E'\nRecorded only; donations do not unlock hats.'
      when v_event.user_id is not null then format(
        E'\nMatched @%s, hats unlocked until %s.',
        coalesce(v_username, v_event.user_id::text),
        coalesce(to_char(v_until, 'DD Mon YYYY'), 'unknown')
      )
      else format(
        E'\nNOT matched (%s). Link it at https://admin.pocketpass.xyz/#supporters',
        coalesce(v_event.email, 'no email')
      )
    end
  );

  select net.http_post(
    url => v_url,
    body => jsonb_build_object(
      'content', left(v_content, 1900),
      'allowed_mentions', jsonb_build_object('parse', '[]'::jsonb)
    ),
    headers => '{"Content-Type": "application/json"}'::jsonb,
    timeout_milliseconds => 5000
  )
  into v_request_id;
  return v_request_id;
exception
  when others then
    return null;
end;
$$;

revoke all on function private.notify_kofi_event(uuid) from public;

create or replace function public.kofi_webhook(data text default null)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_secret text;
  v_payload jsonb;
  v_message_id uuid;
  v_paid_at timestamptz;
  v_email text;
  v_qualifies boolean;
  v_existing private.kofi_events;
  v_applied boolean;
  v_user_id uuid;
begin
  if auth.role() is distinct from 'anon' then
    raise sqlstate 'PT403' using
      message = 'Ko-fi webhook accepts anonymous calls only',
      hint = 'KOFI_ROLE_INVALID';
  end if;

  if data is null or btrim(data) = '' then
    raise sqlstate 'PT400' using
      message = 'Malformed Ko-fi payload',
      hint = 'KOFI_PAYLOAD_INVALID';
  end if;

  begin
    v_payload := data::jsonb;
  exception
    when others then
      raise sqlstate 'PT400' using
        message = 'Malformed Ko-fi payload',
        hint = 'KOFI_PAYLOAD_INVALID';
  end;
  if jsonb_typeof(v_payload) <> 'object' then
    raise sqlstate 'PT400' using
      message = 'Malformed Ko-fi payload',
      hint = 'KOFI_PAYLOAD_INVALID';
  end if;

  select secret.decrypted_secret
  into v_secret
  from vault.decrypted_secrets as secret
  where secret.name = 'kofi_verification_token'
  order by secret.created_at desc
  limit 1;
  if v_secret is null or v_secret = '' then
    raise sqlstate 'PT500' using
      message = 'Ko-fi webhook is not configured',
      hint = 'KOFI_NOT_CONFIGURED';
  end if;

  if extensions.digest(coalesce(v_payload ->> 'verification_token', ''), 'sha256')
    <> extensions.digest(v_secret, 'sha256')
  then
    raise sqlstate 'PT401' using
      message = 'Invalid verification token',
      hint = 'KOFI_TOKEN_INVALID';
  end if;

  v_message_id := private.kofi_try_uuid(v_payload ->> 'message_id');
  if v_message_id is null then
    raise sqlstate 'PT400' using
      message = 'Ko-fi payload has no message_id',
      hint = 'KOFI_PAYLOAD_INVALID';
  end if;

  v_payload := v_payload - 'verification_token';
  v_paid_at := least(
    greatest(
      coalesce(private.kofi_try_timestamptz(v_payload ->> 'timestamp'), now()),
      now() - interval '400 days'
    ),
    now() + interval '1 day'
  );
  v_email := nullif(lower(btrim(coalesce(v_payload ->> 'email', ''))), '');
  v_qualifies := coalesce(v_payload ->> 'type', '') = 'Subscription'
    or coalesce(lower(v_payload ->> 'is_subscription_payment') = 'true', false);

  insert into private.kofi_events (
    message_id,
    kofi_transaction_id,
    event_type,
    email,
    from_name,
    tier_name,
    is_first_subscription_payment,
    amount,
    currency,
    paid_at,
    payload,
    qualifies
  )
  values (
    v_message_id,
    nullif(left(coalesce(v_payload ->> 'kofi_transaction_id', ''), 128), ''),
    left(coalesce(nullif(v_payload ->> 'type', ''), 'Unknown'), 64),
    left(v_email, 320),
    left(v_payload ->> 'from_name', 200),
    left(v_payload ->> 'tier_name', 200),
    case lower(coalesce(v_payload ->> 'is_first_subscription_payment', ''))
      when 'true' then true
      when 'false' then false
      else null
    end,
    private.kofi_try_numeric(v_payload ->> 'amount'),
    left(v_payload ->> 'currency', 8),
    v_paid_at,
    v_payload,
    v_qualifies
  )
  on conflict (message_id) do nothing;

  if not found then
    select event.*
    into v_existing
    from private.kofi_events as event
    where event.message_id = v_message_id;
    v_applied := private.kofi_apply_event(v_message_id);
    return jsonb_build_object(
      'received', true,
      'duplicate', true,
      'matched', v_existing.user_id is not null,
      'applied', v_applied
    );
  end if;

  v_applied := private.kofi_apply_event(v_message_id);
  select event.user_id
  into v_user_id
  from private.kofi_events as event
  where event.message_id = v_message_id;
  perform private.notify_kofi_event(v_message_id);

  return jsonb_build_object(
    'received', true,
    'duplicate', false,
    'matched', v_user_id is not null,
    'applied', v_applied
  );
end;
$$;

revoke all on function public.kofi_webhook(text) from public, authenticated, service_role, api_client;
grant execute on function public.kofi_webhook(text) to anon;

create or replace function private.kofi_apply_events_for_new_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_email text;
  v_event record;
begin
  select lower(account.email::text)
  into v_email
  from auth.users as account
  where account.id = new.user_id;
  if v_email is null or v_email = '' then
    return new;
  end if;

  for v_event in
    select event.message_id
    from private.kofi_events as event
    where event.email = v_email
      and event.user_id is null
      and event.qualifies
      and event.paid_at + private.kofi_grant_interval() > now()
    order by event.paid_at
  loop
    update private.kofi_events
    set user_id = new.user_id,
        matched_by = 'signup'
    where message_id = v_event.message_id;
    perform private.kofi_apply_event(v_event.message_id);
  end loop;
  return new;
end;
$$;

revoke all on function private.kofi_apply_events_for_new_profile() from public;

drop trigger if exists profiles_apply_kofi_events on public.profiles;
create trigger profiles_apply_kofi_events
after insert on public.profiles
for each row execute function private.kofi_apply_events_for_new_profile();

create or replace function private.notify_lapsed_supporters()
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  with due as (
    select status.user_id
    from public.supporter_status as status
    where status.active_until < now()
      and status.lapsed_notified_at is null
    for update skip locked
  ),
  notified as (
    insert into public.notifications (recipient_id, kind, title, body)
    select
      due.user_id,
      'system',
      'Supporter perks ended',
      'Your supporter perks have ended. Hats you have not bought come off the next time you save your Mii.'
    from due
    returning recipient_id
  )
  update public.supporter_status as status
  set lapsed_notified_at = now()
  from notified
  where status.user_id = notified.recipient_id;
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

revoke all on function private.notify_lapsed_supporters() from public;

do $schedule$
begin
  if exists (
    select 1 from pg_available_extensions where name = 'pg_cron'
  ) then
    create extension if not exists pg_cron;
    if not exists (
      select 1 from cron.job where jobname = 'pocketpass-supporter-lapse'
    ) then
      perform cron.schedule(
        'pocketpass-supporter-lapse',
        '15 4 * * *',
        'select private.notify_lapsed_supporters();'
      );
    end if;
  end if;
end;
$schedule$;

alter table private.admin_users
  drop constraint admin_users_permissions_known,
  add constraint admin_users_permissions_known check (
    permissions <@ array['users', 'audit', 'legacy', 'tokens', 'achievements', 'admins', 'apps', 'supporters']::text[]
  );

create or replace function private.admin_permission_keys()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['users', 'audit', 'legacy', 'tokens', 'achievements', 'admins', 'apps', 'supporters']::text[];
$$;

revoke all on function private.admin_permission_keys() from public;

comment on function private.admin_permission_keys() is 'users: list accounts with emails and open their detail; audit: read the audit log; legacy/tokens/achievements: the matching mutations; admins: manage other admins; apps: review and suspend developer apps; supporters: Ko-fi payments and supporter status.';

create or replace function private.kofi_event_json(p_event private.kofi_events)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'message_id', p_event.message_id,
    'paid_at', p_event.paid_at,
    'received_at', p_event.received_at,
    'event_type', p_event.event_type,
    'from_name', p_event.from_name,
    'email', p_event.email,
    'tier_name', p_event.tier_name,
    'amount', p_event.amount,
    'currency', p_event.currency,
    'qualifies', p_event.qualifies,
    'user_id', p_event.user_id,
    'username', (
      select profile.username::text
      from public.profiles as profile
      where profile.user_id = p_event.user_id
    ),
    'display_name', (
      select profile.display_name
      from public.profiles as profile
      where profile.user_id = p_event.user_id
    ),
    'matched_by', p_event.matched_by,
    'applied_at', p_event.applied_at,
    'granted_until', p_event.granted_until,
    'error', p_event.error
  );
$$;

revoke all on function private.kofi_event_json(private.kofi_events) from public;

create or replace function public.admin_list_kofi_events(
  p_filter text default 'unmatched',
  p_limit integer default 50,
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_total bigint;
  v_items jsonb;
begin
  perform private.require_permission('supporters');
  if coalesce(p_filter, 'unmatched') not in ('unmatched', 'all') then
    raise exception 'Filter must be unmatched or all' using errcode = '22023';
  end if;

  select count(*)
  into v_total
  from private.kofi_events as event
  where coalesce(p_filter, 'unmatched') = 'all' or event.user_id is null;

  select coalesce(jsonb_agg(private.kofi_event_json(page.*) order by page.received_at desc), '[]'::jsonb)
  into v_items
  from (
    select event.*
    from private.kofi_events as event
    where coalesce(p_filter, 'unmatched') = 'all' or event.user_id is null
    order by event.received_at desc
    limit v_limit
    offset v_offset
  ) as page;

  return jsonb_build_object('items', v_items, 'total', v_total);
end;
$$;

revoke all on function public.admin_list_kofi_events(text, integer, integer) from public, anon;
grant execute on function public.admin_list_kofi_events(text, integer, integer) to authenticated;

create or replace function public.admin_link_kofi_email(p_email text, p_user_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('supporters');
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_applied integer := 0;
  v_until timestamptz;
  v_event record;
begin
  if v_email = '' or v_email !~ '^[^@[:space:]]+@[^@[:space:]]+$' or length(v_email) > 320 then
    raise exception 'A Ko-fi email address is required' using errcode = '22023';
  end if;
  if p_user_id is null or not exists (
    select 1 from public.profiles as profile where profile.user_id = p_user_id
  ) then
    raise exception 'User not found' using errcode = 'P0002';
  end if;

  insert into private.kofi_links (email, user_id, created_by)
  values (v_email, p_user_id, v_admin_id)
  on conflict (email) do update
    set user_id = excluded.user_id,
        created_by = excluded.created_by,
        created_at = now();

  update private.kofi_events
  set user_id = p_user_id,
      matched_by = 'link',
      applied_at = null,
      granted_until = null,
      error = null
  where email = v_email
    and user_id is distinct from p_user_id;

  for v_event in
    select event.message_id
    from private.kofi_events as event
    where event.email = v_email
      and event.user_id = p_user_id
      and event.qualifies
      and event.paid_at + private.kofi_grant_interval() > now()
    order by event.paid_at
  loop
    if private.kofi_apply_event(v_event.message_id) then
      v_applied := v_applied + 1;
    end if;
  end loop;

  select status.active_until
  into v_until
  from public.supporter_status as status
  where status.user_id = p_user_id;

  perform private.record_admin_action(
    v_admin_id,
    'link_kofi_email',
    p_user_id,
    jsonb_build_object('email', v_email, 'applied_events', v_applied, 'active_until', v_until)
  );

  return jsonb_build_object(
    'linked', true,
    'email', v_email,
    'user_id', p_user_id,
    'applied_events', v_applied,
    'active_until', v_until
  );
end;
$$;

revoke all on function public.admin_link_kofi_email(text, uuid) from public, anon;
grant execute on function public.admin_link_kofi_email(text, uuid) to authenticated;

create or replace function public.admin_unlink_kofi_email(p_email text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('supporters');
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_user_id uuid;
begin
  delete from private.kofi_links as link
  where link.email = v_email
  returning link.user_id into v_user_id;

  if v_user_id is null then
    return jsonb_build_object('unlinked', false, 'email', v_email);
  end if;

  perform private.record_admin_action(
    v_admin_id,
    'unlink_kofi_email',
    v_user_id,
    jsonb_build_object('email', v_email)
  );
  return jsonb_build_object('unlinked', true, 'email', v_email);
end;
$$;

revoke all on function public.admin_unlink_kofi_email(text) from public, anon;
grant execute on function public.admin_unlink_kofi_email(text) to authenticated;

create or replace function public.admin_set_supporter(
  p_user_id uuid,
  p_active_until timestamptz,
  p_reason text
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('supporters');
  v_reason text := btrim(coalesce(p_reason, ''));
  v_old timestamptz;
  v_new timestamptz;
begin
  if length(v_reason) < 3 or length(v_reason) > 500 then
    raise exception 'Reason must be 3 to 500 characters' using errcode = '22023';
  end if;
  if p_user_id is null or not exists (
    select 1 from public.profiles as profile where profile.user_id = p_user_id
  ) then
    raise exception 'User not found' using errcode = 'P0002';
  end if;

  select status.active_until
  into v_old
  from public.supporter_status as status
  where status.user_id = p_user_id
  for update;

  if p_active_until is null then
    if v_old is not null and v_old > now() then
      update public.supporter_status
      set active_until = now(),
          source = 'admin',
          updated_at = now()
      where user_id = p_user_id;
    end if;
    select status.active_until
    into v_new
    from public.supporter_status as status
    where status.user_id = p_user_id;
  else
    insert into public.supporter_status (user_id, active_until, source, updated_at)
    values (p_user_id, p_active_until, 'admin', now())
    on conflict (user_id) do update
      set active_until = excluded.active_until,
          source = 'admin',
          lapsed_notified_at = case
            when excluded.active_until > now() then null
            else public.supporter_status.lapsed_notified_at
          end,
          updated_at = now();
    v_new := p_active_until;
    if v_new > now() and (v_old is null or v_new > v_old) then
      insert into public.notifications (recipient_id, kind, title, body)
      values (
        p_user_id,
        'system',
        'Thanks for supporting PocketPass!',
        'Every hat is unlocked until ' || to_char(v_new, 'DD Mon YYYY') || '.'
      );
    end if;
  end if;

  perform private.record_admin_action(
    v_admin_id,
    'set_supporter',
    p_user_id,
    jsonb_build_object('old', v_old, 'new', v_new, 'reason', v_reason)
  );

  return jsonb_build_object('user_id', p_user_id, 'active_until', v_new);
end;
$$;

revoke all on function public.admin_set_supporter(uuid, timestamptz, text) from public, anon;
grant execute on function public.admin_set_supporter(uuid, timestamptz, text) to authenticated;

create or replace function public.admin_list_supporters(
  p_limit integer default 50,
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_total bigint;
  v_items jsonb;
begin
  perform private.require_permission('supporters');

  select count(*)
  into v_total
  from public.supporter_status as status
  where status.active_until > now() - interval '30 days';

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'user_id', page.user_id,
        'username', profile.username::text,
        'display_name', profile.display_name,
        'active_until', page.active_until,
        'active', page.active_until > now(),
        'source', page.source,
        'last_paid_at', event.paid_at,
        'last_tier_name', event.tier_name,
        'kofi_emails', (
          select coalesce(jsonb_agg(link.email order by link.email), '[]'::jsonb)
          from private.kofi_links as link
          where link.user_id = page.user_id
        )
      )
      order by (page.active_until > now()) desc, page.active_until desc
    ),
    '[]'::jsonb
  )
  into v_items
  from (
    select status.*
    from public.supporter_status as status
    where status.active_until > now() - interval '30 days'
    order by (status.active_until > now()) desc, status.active_until desc
    limit v_limit
    offset v_offset
  ) as page
  left join public.profiles as profile on profile.user_id = page.user_id
  left join private.kofi_events as event on event.message_id = page.last_event_id;

  return jsonb_build_object('items', v_items, 'total', v_total);
end;
$$;

revoke all on function public.admin_list_supporters(integer, integer) from public, anon;
grant execute on function public.admin_list_supporters(integer, integer) to authenticated;

create or replace function public.admin_get_user(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_result jsonb;
begin
  perform private.require_permission('users');

  select jsonb_build_object(
    'user_id', account.id,
    'email', account.email::text,
    'email_confirmed_at', account.email_confirmed_at,
    'last_sign_in_at', account.last_sign_in_at,
    'providers', coalesce(account.raw_app_meta_data -> 'providers', '[]'::jsonb),
    'created_at', account.created_at,
    'is_admin', exists (
      select 1 from private.admin_users as admin where admin.user_id = account.id
    ),
    'profile', case
      when profile.user_id is null then null
      else jsonb_build_object(
        'username', profile.username::text,
        'display_name', profile.display_name,
        'bio', profile.bio,
        'age', profile.age,
        'country_code', profile.country_code,
        'legacy_account', profile.legacy_account,
        'last_seen_at', profile.last_seen_at,
        'created_at', profile.created_at,
        'updated_at', profile.updated_at
      )
    end,
    'token_balance', coalesce(token_balance.balance, 0),
    'supporter_until', supporter.active_until,
    'supporter_source', supporter.source,
    'kofi_emails', (
      select coalesce(jsonb_agg(link.email order by link.email), '[]'::jsonb)
      from private.kofi_links as link
      where link.user_id = account.id
    ),
    'counts', jsonb_build_object(
      'friends', (
        select count(*)
        from public.friendships as friendship
        where account.id in (friendship.user_low, friendship.user_high)
      ),
      'encounters_confirmed', (
        select count(*)
        from public.nearby_encounters as encounter
        where account.id in (encounter.user_low, encounter.user_high)
          and encounter.confirmed_at is not null
      ),
      'messages_sent', (
        select count(*)
        from public.messages as message
        where message.sender_id = account.id
      )
    ),
    'achievements', (
      select coalesce(
        jsonb_agg(
          jsonb_build_object(
            'key', keyed.key,
            'unlocked', unlock.unlocked_at is not null,
            'unlocked_at', unlock.unlocked_at
          )
          order by keyed.ordinality
        ),
        '[]'::jsonb
      )
      from unnest(private.achievement_keys()) with ordinality as keyed(key, ordinality)
      left join public.achievement_unlocks as unlock
        on unlock.user_id = account.id
        and unlock.achievement_key = keyed.key
    ),
    'audit', (
      select coalesce(
        jsonb_agg(
          jsonb_build_object(
            'id', entry.id,
            'created_at', entry.created_at,
            'admin_id', entry.admin_id,
            'admin_email', admin_account.email::text,
            'action', entry.action,
            'payload', entry.payload
          )
          order by entry.id desc
        ),
        '[]'::jsonb
      )
      from (
        select *
        from private.admin_audit as audit
        where audit.target_user_id = account.id
        order by audit.id desc
        limit 20
      ) as entry
      left join auth.users as admin_account on admin_account.id = entry.admin_id
    )
  )
  into v_result
  from auth.users as account
  left join public.profiles as profile on profile.user_id = account.id
  left join public.token_balances as token_balance on token_balance.user_id = account.id
  left join public.supporter_status as supporter on supporter.user_id = account.id
  where account.id = p_user_id;

  if v_result is null then
    raise exception 'User not found' using errcode = 'P0002';
  end if;
  return v_result;
end;
$$;

revoke all on function public.admin_get_user(uuid) from public, anon;
grant execute on function public.admin_get_user(uuid) to authenticated;

comment on table private.kofi_events is 'Every Ko-fi webhook delivery (verification token stripped); Subscription payments grant supporter time to the matched user.';
comment on table private.kofi_links is 'Admin-made links from a Ko-fi payer email to a PocketPass account; they take precedence over the sign-in email match.';
comment on table public.supporter_status is 'Supporter entitlement per user; every Mii hat is wearable while active_until is in the future. Rows are expired, never deleted.';

commit;
