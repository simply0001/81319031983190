begin;

create or replace function private.admin_permission_keys() returns text[]
language sql immutable set search_path = '' as $$
  select array['users','audit','legacy','tokens','achievements','admins','apps','supporters',
    'board_requests','boards','board_content','board_members','board_suspensions',
    'board_private_review','board_delete','board_filters','board_stationery','board_settings',
    'bans']::text[];
$$;
alter table private.admin_users drop constraint admin_users_permissions_known;
alter table private.admin_users add constraint admin_users_permissions_known
  check (permissions <@ private.admin_permission_keys());

create table private.account_bans (
  id bigint generated always as identity primary key,
  user_id uuid not null,
  reason text not null check (char_length(reason) between 1 and 300),
  staff_note text not null default '' check (char_length(staff_note) <= 1000),
  banned_by uuid,
  created_at timestamptz not null default now(),
  ends_at timestamptz,
  lifted_at timestamptz,
  lifted_by uuid,
  lift_note text not null default '' check (char_length(lift_note) <= 300),
  constraint account_bans_ends_after_start check (ends_at is null or ends_at > created_at),
  constraint account_bans_lifted_after_start check (lifted_at is null or lifted_at >= created_at)
);
create index account_bans_user_idx on private.account_bans (user_id, id desc);
create index account_bans_open_idx on private.account_bans (user_id) where lifted_at is null;

create table private.ban_signals (
  ban_id bigint not null references private.account_bans (id) on delete cascade,
  kind text not null check (kind in ('email', 'discord', 'network')),
  value_hash bytea not null,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  primary key (ban_id, kind, value_hash)
);
create index ban_signals_lookup_idx on private.ban_signals (kind, value_hash);

create table private.ban_signal_key (
  singleton boolean primary key default true check (singleton),
  secret bytea not null check (octet_length(secret) = 32)
);
insert into private.ban_signal_key (secret) values (extensions.gen_random_bytes(32));

create table private.ban_signup_blocks (
  id bigint generated always as identity primary key,
  ban_id bigint references private.account_bans (id) on delete set null,
  kind text not null check (kind in ('email', 'discord', 'network')),
  method text not null check (method in ('email', 'discord', 'username')),
  created_at timestamptz not null default now()
);
create index ban_signup_blocks_created_idx on private.ban_signup_blocks (created_at desc, id desc);

revoke all on table private.account_bans from public, anon, authenticated;
revoke all on table private.ban_signals from public, anon, authenticated;
revoke all on table private.ban_signal_key from public, anon, authenticated;
revoke all on table private.ban_signup_blocks from public, anon, authenticated;

create or replace function private.account_banned(p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_user_id is not null and exists (
    select 1
    from private.account_bans as ban
    where ban.user_id = p_user_id
      and ban.lifted_at is null
      and (ban.ends_at is null or ban.ends_at > now())
  );
$$;

revoke all on function private.account_banned(uuid) from public;
grant execute on function private.account_banned(uuid) to authenticated;

create or replace function private.normalize_ban_email(p_email text)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_domain text;
  v_local text;
begin
  v_domain := substring(v_email from '@([^@]+)$');
  if v_domain is null then
    return null;
  end if;
  v_local := split_part(left(v_email, char_length(v_email) - char_length(v_domain) - 1), '+', 1);
  if v_domain in ('gmail.com', 'googlemail.com') then
    v_local := replace(v_local, '.', '');
    v_domain := 'gmail.com';
  end if;
  if v_local = '' then
    return null;
  end if;
  return v_local || '@' || v_domain;
end;
$$;

revoke all on function private.normalize_ban_email(text) from public;

create or replace function private.ban_network_value(p_address inet)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_address is null then null
    when p_address << any (array[
      '0.0.0.0/8', '10.0.0.0/8', '100.64.0.0/10', '127.0.0.0/8', '169.254.0.0/16',
      '172.16.0.0/12', '192.168.0.0/16', '::1/128', 'fc00::/7', 'fe80::/10'
    ]::inet[]) then null
    when family(p_address) = 4 then host(p_address)
    else host(network(set_masklen(p_address, 64))) || '/64'
  end;
$$;

revoke all on function private.ban_network_value(inet) from public;

create or replace function private.ban_signal_hash(p_kind text, p_value text)
returns bytea
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when nullif(p_value, '') is null then null
    else extensions.hmac(convert_to(p_kind || ':' || p_value, 'UTF8'), signal_key.secret, 'sha256')
  end
  from private.ban_signal_key as signal_key;
$$;

revoke all on function private.ban_signal_hash(text, text) from public;

create or replace function private.active_ban_for_signal(
  p_kind text,
  p_hash bytea,
  p_exclude_user_id uuid default null
)
returns bigint
language sql
stable
security definer
set search_path = ''
as $$
  select ban.id
  from private.ban_signals as signal
  join private.account_bans as ban on ban.id = signal.ban_id
  where signal.kind = p_kind
    and signal.value_hash = p_hash
    and (signal.expires_at is null or signal.expires_at > now())
    and ban.lifted_at is null
    and (ban.ends_at is null or ban.ends_at > now())
    and (p_exclude_user_id is null or ban.user_id <> p_exclude_user_id)
  order by ban.id desc
  limit 1;
$$;

revoke all on function private.active_ban_for_signal(text, bytea, uuid) from public;

create or replace function private.broadcast_account_ban_change(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform realtime.send('{}'::jsonb, 'ACCOUNT_BANNED', 'notifications:' || p_user_id::text, true);
  perform realtime.send(
    jsonb_build_object('user_id', p_user_id),
    'UPDATE',
    'friends:' || other.user_id::text,
    true
  )
  from (
    select case when friendship.user_low = p_user_id then friendship.user_high else friendship.user_low end as user_id
    from public.friendships as friendship
    where p_user_id in (friendship.user_low, friendship.user_high)
    union
    select case when request.requester_id = p_user_id then request.addressee_id else request.requester_id end
    from public.friend_requests as request
    where request.status = 'pending'
      and p_user_id in (request.requester_id, request.addressee_id)
  ) as other;
end;
$$;

revoke all on function private.broadcast_account_ban_change(uuid) from public;

create or replace function public.pocketpass_request_guard()
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if private.account_banned(auth.uid())
    and coalesce(current_setting('request.path', true), '') not like '%/rpc/get_my_account_ban'
  then
    raise exception 'This account is banned.' using errcode = '42501', hint = 'ACCOUNT_BANNED';
  end if;
end;
$$;

revoke all on function public.pocketpass_request_guard() from public;
grant execute on function public.pocketpass_request_guard() to public;

create or replace function public.get_my_account_ban()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_ban private.account_bans;
  v_address inet;
  v_network text;
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  select ban.*
  into v_ban
  from private.account_bans as ban
  where ban.user_id = v_user_id
    and ban.lifted_at is null
    and (ban.ends_at is null or ban.ends_at > now())
  order by ban.id desc
  limit 1;
  if not found then
    return null;
  end if;

  begin
    v_address := nullif(btrim(coalesce(
      current_setting('request.headers', true)::jsonb ->> 'x-pocketpass-client-ip',
      ''
    )), '')::inet;
  exception when others then
    v_address := null;
  end;
  v_network := private.ban_network_value(v_address);
  if v_network is not null then
    insert into private.ban_signals (ban_id, kind, value_hash, expires_at)
    values (
      v_ban.id,
      'network',
      private.ban_signal_hash('network', v_network),
      least(coalesce(v_ban.ends_at, 'infinity'::timestamptz), now() + interval '30 days')
    )
    on conflict (ban_id, kind, value_hash) do update
      set expires_at = greatest(private.ban_signals.expires_at, excluded.expires_at);
  end if;

  return jsonb_build_object(
    'reason', v_ban.reason,
    'ends_at', v_ban.ends_at,
    'created_at', v_ban.created_at
  );
end;
$$;

revoke all on function public.get_my_account_ban() from public, anon;
grant execute on function public.get_my_account_ban() to authenticated;

create or replace function public.pocketpass_before_user_created(event jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user jsonb := coalesce(event -> 'user', '{}'::jsonb);
  v_email text := nullif(btrim(coalesce(v_user ->> 'email', '')), '');
  v_provider text := lower(coalesce(v_user -> 'app_metadata' ->> 'provider', ''));
  v_method text;
  v_kind text;
  v_ban_id bigint;
  v_address inet;
  v_discord_id text;
begin
  begin
    v_method := case
      when v_provider = 'discord' then 'discord'
      when private.login_username_for_email(v_email) is not null then 'username'
      else 'email'
    end;

    if v_method = 'username' then
      begin
        v_address := nullif(btrim(coalesce(event -> 'metadata' ->> 'ip_address', '')), '')::inet;
      exception when others then
        v_address := null;
      end;
      v_kind := 'network';
      v_ban_id := private.active_ban_for_signal(
        'network',
        private.ban_signal_hash('network', private.ban_network_value(v_address))
      );
    else
      v_kind := 'email';
      v_ban_id := private.active_ban_for_signal(
        'email',
        private.ban_signal_hash('email', private.normalize_ban_email(v_email))
      );
      if v_ban_id is null and v_method = 'discord' then
        v_discord_id := nullif(coalesce(
          v_user -> 'user_metadata' ->> 'provider_id',
          v_user -> 'user_metadata' ->> 'sub',
          ''
        ), '');
        v_kind := 'discord';
        v_ban_id := private.active_ban_for_signal(
          'discord',
          private.ban_signal_hash('discord', v_discord_id)
        );
      end if;
    end if;
  exception when others then
    return '{}'::jsonb;
  end;

  if v_ban_id is null then
    return '{}'::jsonb;
  end if;

  begin
    insert into private.ban_signup_blocks (ban_id, kind, method)
    values (v_ban_id, v_kind, v_method);
  exception when others then
    null;
  end;

  return jsonb_build_object(
    'error',
    jsonb_build_object('http_code', 403, 'message', 'This sign-up is blocked because of a ban.')
  );
end;
$$;

revoke all on function public.pocketpass_before_user_created(jsonb) from public, anon, authenticated, service_role;
grant execute on function public.pocketpass_before_user_created(jsonb) to supabase_auth_admin;

create or replace function private.guard_banned_identity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.provider = 'discord' and private.active_ban_for_signal(
    'discord',
    private.ban_signal_hash('discord', new.provider_id),
    new.user_id
  ) is not null then
    raise exception 'This sign-up is blocked because of a ban.' using errcode = '42501';
  end if;
  if private.login_username_for_email(new.identity_data ->> 'email') is null
    and private.active_ban_for_signal(
      'email',
      private.ban_signal_hash('email', private.normalize_ban_email(new.identity_data ->> 'email')),
      new.user_id
    ) is not null
  then
    raise exception 'This sign-up is blocked because of a ban.' using errcode = '42501';
  end if;
  return new;
end;
$$;

revoke all on function private.guard_banned_identity() from public;

create trigger guard_banned_identity
before insert on auth.identities
for each row execute function private.guard_banned_identity();

create or replace function private.guard_banned_email_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if nullif(new.email_change, '') is not null
    and new.email_change is distinct from old.email_change
    and private.active_ban_for_signal(
      'email',
      private.ban_signal_hash('email', private.normalize_ban_email(new.email_change)),
      new.id
    ) is not null
  then
    raise exception 'This email is blocked because of a ban.' using errcode = '42501';
  end if;
  if new.email is distinct from old.email
    and private.login_username_for_email(new.email) is null
    and private.active_ban_for_signal(
      'email',
      private.ban_signal_hash('email', private.normalize_ban_email(new.email)),
      new.id
    ) is not null
  then
    raise exception 'This email is blocked because of a ban.' using errcode = '42501';
  end if;
  return new;
end;
$$;

revoke all on function private.guard_banned_email_change() from public;

create trigger guard_banned_email_change
before update of email, email_change on auth.users
for each row execute function private.guard_banned_email_change();

create or replace function private.has_block_between(p_user_a uuid, p_user_b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    p_user_a is not null
    and p_user_b is not null
    and (
      exists (
        select 1
        from public.user_blocks as block
        where
          (block.blocker_id = p_user_a and block.blocked_id = p_user_b)
          or (block.blocker_id = p_user_b and block.blocked_id = p_user_a)
      )
      or private.account_banned(p_user_a)
      or private.account_banned(p_user_b)
    );
$$;

create or replace function private.board_blocked(p_a uuid, p_b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.user_blocks where (blocker_id=p_a and blocked_id=p_b) or (blocker_id=p_b and blocked_id=p_a))
    or private.account_banned(p_a)
    or private.account_banned(p_b);
$$;

create or replace function private.can_access_friend_presence_topic(
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
    p_user_id is not null
    and private.friend_presence_low_from_topic(p_topic) is not null
    and private.friend_presence_high_from_topic(p_topic) is not null
    and private.friend_presence_low_from_topic(p_topic)
      < private.friend_presence_high_from_topic(p_topic)
    and p_user_id in (
      private.friend_presence_low_from_topic(p_topic),
      private.friend_presence_high_from_topic(p_topic)
    )
    and private.are_friends(
      private.friend_presence_low_from_topic(p_topic),
      private.friend_presence_high_from_topic(p_topic)
    )
    and not private.has_block_between(
      private.friend_presence_low_from_topic(p_topic),
      private.friend_presence_high_from_topic(p_topic)
    );
$$;

create policy friendships_hide_banned
on public.friendships
as restrictive
for select
to authenticated
using (not private.account_banned(user_low) and not private.account_banned(user_high));

create policy friend_requests_hide_banned
on public.friend_requests
as restrictive
for select
to authenticated
using (not private.account_banned(requester_id) and not private.account_banned(addressee_id));

create policy nearby_encounters_hide_banned
on public.nearby_encounters
as restrictive
for select
to authenticated
using (not private.account_banned(user_low) and not private.account_banned(user_high));

create policy pocketpass_banned_realtime
on realtime.messages
as restrictive
for all
to authenticated
using (
  not private.account_banned(auth.uid())
  or realtime.topic() = 'app_updates'
  or realtime.topic() = 'notifications:' || auth.uid()::text
)
with check (not private.account_banned(auth.uid()));

create policy pocketpass_banned_storage_insert
on storage.objects
as restrictive
for insert
to authenticated
with check (not private.account_banned(auth.uid()));

create policy pocketpass_banned_storage_update
on storage.objects
as restrictive
for update
to authenticated
using (not private.account_banned(auth.uid()))
with check (not private.account_banned(auth.uid()));

create policy pocketpass_banned_storage_delete
on storage.objects
as restrictive
for delete
to authenticated
using (not private.account_banned(auth.uid()));

create or replace function private.api_notification_json(p_notification public.notifications)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', p_notification.id,
    'kind', p_notification.kind,
    'title', p_notification.title,
    'body', p_notification.body,
    'event_count', p_notification.event_count,
    'actor', (
      select jsonb_build_object(
        'user_id', profile.user_id,
        'display_name', profile.display_name,
        'avatar_path', profile.avatar_path
      )
      from public.profiles as profile
      where profile.user_id = p_notification.actor_id
        and not private.account_banned(profile.user_id)
    ),
    'friend_request_id', p_notification.friend_request_id,
    'friend_request_status', p_notification.friend_request_status,
    'conversation_id', p_notification.conversation_id,
    'created_at', p_notification.created_at,
    'updated_at', p_notification.updated_at,
    'read_at', p_notification.read_at
  );
$$;

create or replace function private.api_conversation_json(p_conversation_id uuid, p_viewer_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', conversation.id,
    'kind', conversation.kind::text,
    'title', conversation.title,
    'members', coalesce(
      (
        select jsonb_agg(
          jsonb_build_object(
            'user_id', member.user_id,
            'display_name', profile.display_name,
            'avatar_path', profile.avatar_path
          )
          order by member.joined_at, member.user_id
        )
        from public.conversation_members as member
        join public.profiles as profile on profile.user_id = member.user_id
        where member.conversation_id = conversation.id
          and member.left_at is null
          and not private.account_banned(member.user_id)
      ),
      '[]'::jsonb
    ),
    'last_message', (
      select private.api_message_json(message)
      from public.messages as message
      where message.conversation_id = conversation.id
        and message.deleted_at is null
      order by message.created_at desc, message.id desc
      limit 1
    ),
    'unread_count', (
      select count(*)
      from public.messages as message
      where message.conversation_id = conversation.id
        and message.sender_id <> p_viewer_id
        and message.deleted_at is null
        and message.created_at > coalesce(viewer.last_read_at, '-infinity'::timestamptz)
    ),
    'last_read_at', viewer.last_read_at,
    'created_at', conversation.created_at,
    'updated_at', conversation.updated_at
  )
  from public.conversations as conversation
  left join public.conversation_members as viewer
    on viewer.conversation_id = conversation.id
    and viewer.user_id = p_viewer_id
  where conversation.id = p_conversation_id;
$$;

create or replace function public.api_v1_friends_list(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:read');
  v_user_id uuid;
  v_items jsonb;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, '{}'::text[]);

    select coalesce(
      jsonb_agg(
        private.api_profile_json(profile)
          || jsonb_build_object('friends_since', friendship.created_at)
        order by lower(profile.display_name), profile.user_id
      ),
      '[]'::jsonb
    )
    into v_items
    from public.friendships as friendship
    join public.profiles as profile
      on profile.user_id = case
        when friendship.user_low = v_user_id then friendship.user_high
        else friendship.user_low
      end
    where v_user_id in (friendship.user_low, friendship.user_high)
      and not private.account_banned(profile.user_id);

    return jsonb_build_object('items', v_items);
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.api_v1_friends_requests_list(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('friends:read');
  v_user_id uuid;
  v_incoming jsonb;
  v_outgoing jsonb;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, '{}'::text[]);

    select coalesce(
      jsonb_agg(
        private.api_friend_request_json(request)
        order by request.created_at desc, request.id
      ),
      '[]'::jsonb
    )
    into v_incoming
    from public.friend_requests as request
    where request.addressee_id = v_user_id
      and request.status = 'pending'
      and not private.account_banned(request.requester_id);

    select coalesce(
      jsonb_agg(
        private.api_friend_request_json(request)
        order by request.created_at desc, request.id
      ),
      '[]'::jsonb
    )
    into v_outgoing
    from public.friend_requests as request
    where request.requester_id = v_user_id
      and request.status = 'pending'
      and not private.account_banned(request.addressee_id);

    return jsonb_build_object('incoming', v_incoming, 'outgoing', v_outgoing);
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

create or replace function public.admin_ban_account(
  p_user_id uuid,
  p_reason text,
  p_staff_note text default null,
  p_duration text default 'permanent'
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('bans');
  v_reason text := nullif(btrim(coalesce(p_reason, '')), '');
  v_note text := btrim(coalesce(p_staff_note, ''));
  v_duration text := lower(btrim(coalesce(p_duration, '')));
  v_ends_at timestamptz;
  v_network_expires_at timestamptz;
  v_ban private.account_bans;
begin
  if v_reason is null or char_length(v_reason) > 300 then
    raise exception 'p_reason must be 1 to 300 characters' using errcode = '22023';
  end if;
  if char_length(v_note) > 1000 then
    raise exception 'p_staff_note must be at most 1000 characters' using errcode = '22023';
  end if;
  if v_duration not in ('permanent', '1d', '7d', '30d') then
    raise exception 'Unknown duration' using errcode = '22023';
  end if;
  if not exists (select 1 from auth.users as account where account.id = p_user_id) then
    raise exception 'User not found' using errcode = 'P0002';
  end if;
  if p_user_id = v_admin_id then
    raise exception 'You cannot ban yourself' using errcode = '22023';
  end if;
  if exists (select 1 from private.admin_users as admin where admin.user_id = p_user_id) then
    raise exception 'Admins and owners cannot be banned' using errcode = '42501';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('pocketpass.account_ban:' || p_user_id::text, 0));
  if private.account_banned(p_user_id) then
    raise exception 'This account is already banned' using errcode = '23505';
  end if;

  v_ends_at := case v_duration
    when '1d' then now() + interval '1 day'
    when '7d' then now() + interval '7 days'
    when '30d' then now() + interval '30 days'
    else null
  end;
  v_network_expires_at := least(coalesce(v_ends_at, 'infinity'::timestamptz), now() + interval '30 days');

  insert into private.account_bans (user_id, reason, staff_note, banned_by, ends_at)
  values (p_user_id, v_reason, v_note, v_admin_id, v_ends_at)
  returning * into v_ban;

  insert into private.ban_signals (ban_id, kind, value_hash)
  select distinct v_ban.id, 'email', private.ban_signal_hash('email', private.normalize_ban_email(candidate.email))
  from (
    select account.email::text as email
    from auth.users as account
    where account.id = p_user_id
    union
    select identity.identity_data ->> 'email'
    from auth.identities as identity
    where identity.user_id = p_user_id
  ) as candidate
  where private.login_username_for_email(candidate.email) is null
    and private.normalize_ban_email(candidate.email) is not null
  on conflict do nothing;

  insert into private.ban_signals (ban_id, kind, value_hash)
  select distinct v_ban.id, 'discord', private.ban_signal_hash('discord', identity.provider_id)
  from auth.identities as identity
  where identity.user_id = p_user_id
    and identity.provider = 'discord'
    and nullif(identity.provider_id, '') is not null
  on conflict do nothing;

  insert into private.ban_signals (ban_id, kind, value_hash, expires_at)
  select distinct
    v_ban.id,
    'network',
    private.ban_signal_hash('network', private.ban_network_value(session.ip)),
    v_network_expires_at
  from auth.sessions as session
  where session.user_id = p_user_id
    and coalesce(session.updated_at, session.created_at) > now() - interval '30 days'
    and private.ban_network_value(session.ip) is not null
  on conflict do nothing;

  update private.nearby_credentials as credential
  set consumed_at = now()
  where credential.owner_id = p_user_id
    and credential.consumed_at is null;

  delete from private.message_push_devices as device
  where device.user_id = p_user_id;

  delete from auth.sessions as session
  where session.user_id = p_user_id
    and session.oauth_client_id is not null;

  perform private.broadcast_account_ban_change(p_user_id);

  perform private.record_admin_action(
    v_admin_id,
    'ban_account',
    p_user_id,
    jsonb_build_object(
      'ban_id', v_ban.id,
      'reason', v_ban.reason,
      'duration', v_duration,
      'ends_at', v_ban.ends_at
    )
  );

  return jsonb_build_object(
    'id', v_ban.id,
    'user_id', v_ban.user_id,
    'reason', v_ban.reason,
    'staff_note', v_ban.staff_note,
    'created_at', v_ban.created_at,
    'ends_at', v_ban.ends_at
  );
end;
$$;

revoke all on function public.admin_ban_account(uuid, text, text, text) from public, anon;
grant execute on function public.admin_ban_account(uuid, text, text, text) to authenticated;

create or replace function public.admin_lift_ban(
  p_ban_id bigint,
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('bans');
  v_note text := btrim(coalesce(p_note, ''));
  v_ban private.account_bans;
begin
  if char_length(v_note) > 300 then
    raise exception 'p_note must be at most 300 characters' using errcode = '22023';
  end if;

  select ban.*
  into v_ban
  from private.account_bans as ban
  where ban.id = p_ban_id
  for update;
  if not found then
    raise exception 'Ban not found' using errcode = 'P0002';
  end if;
  if v_ban.lifted_at is not null or (v_ban.ends_at is not null and v_ban.ends_at <= now()) then
    raise exception 'This ban has already ended' using errcode = '22023';
  end if;

  update private.account_bans as ban
  set lifted_at = now(),
      lifted_by = v_admin_id,
      lift_note = v_note
  where ban.id = p_ban_id
  returning * into v_ban;

  delete from private.ban_signals as signal
  where signal.ban_id = p_ban_id;

  perform private.broadcast_account_ban_change(v_ban.user_id);

  perform private.record_admin_action(
    v_admin_id,
    'lift_ban',
    v_ban.user_id,
    jsonb_build_object('ban_id', v_ban.id, 'note', v_note)
  );

  return jsonb_build_object(
    'id', v_ban.id,
    'user_id', v_ban.user_id,
    'lifted_at', v_ban.lifted_at
  );
end;
$$;

revoke all on function public.admin_lift_ban(bigint, text) from public, anon;
grant execute on function public.admin_lift_ban(bigint, text) to authenticated;

create or replace function private.account_ban_rows(p_user_id uuid, p_active_only boolean)
returns table (
  id bigint,
  user_id uuid,
  username text,
  display_name text,
  reason text,
  staff_note text,
  banned_by uuid,
  banned_by_name text,
  created_at timestamptz,
  ends_at timestamptz,
  lifted_at timestamptz,
  lifted_by uuid,
  lifted_by_name text,
  lift_note text,
  active boolean
)
language sql
stable
security definer
set search_path = ''
as $$
  select
    ban.id,
    ban.user_id,
    profile.username::text,
    profile.display_name,
    ban.reason,
    ban.staff_note,
    ban.banned_by,
    coalesce(banner_profile.display_name, banner.email::text),
    ban.created_at,
    ban.ends_at,
    ban.lifted_at,
    ban.lifted_by,
    coalesce(lifter_profile.display_name, lifter.email::text),
    ban.lift_note,
    ban.lifted_at is null and (ban.ends_at is null or ban.ends_at > now())
  from private.account_bans as ban
  left join public.profiles as profile on profile.user_id = ban.user_id
  left join auth.users as banner on banner.id = ban.banned_by
  left join public.profiles as banner_profile on banner_profile.user_id = ban.banned_by
  left join auth.users as lifter on lifter.id = ban.lifted_by
  left join public.profiles as lifter_profile on lifter_profile.user_id = ban.lifted_by
  where (p_user_id is null or ban.user_id = p_user_id)
    and (
      not p_active_only
      or (ban.lifted_at is null and (ban.ends_at is null or ban.ends_at > now()))
    );
$$;

revoke all on function private.account_ban_rows(uuid, boolean) from public;

create or replace function public.admin_list_bans(
  p_status text default 'active',
  p_limit integer default 50,
  p_offset integer default 0
)
returns table (
  id bigint,
  user_id uuid,
  username text,
  display_name text,
  reason text,
  staff_note text,
  banned_by uuid,
  banned_by_name text,
  created_at timestamptz,
  ends_at timestamptz,
  lifted_at timestamptz,
  lifted_by uuid,
  lifted_by_name text,
  lift_note text,
  active boolean,
  total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_status text := lower(btrim(coalesce(p_status, 'active')));
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
begin
  perform private.require_permission('bans');
  if v_status not in ('active', 'all') then
    raise exception 'p_status must be active or all' using errcode = '22023';
  end if;
  return query
  select ban_row.*, count(*) over ()
  from private.account_ban_rows(null, v_status = 'active') as ban_row
  order by ban_row.id desc
  limit v_limit
  offset v_offset;
end;
$$;

revoke all on function public.admin_list_bans(text, integer, integer) from public, anon;
grant execute on function public.admin_list_bans(text, integer, integer) to authenticated;

create or replace function public.admin_get_user_bans(p_user_id uuid)
returns table (
  id bigint,
  user_id uuid,
  username text,
  display_name text,
  reason text,
  staff_note text,
  banned_by uuid,
  banned_by_name text,
  created_at timestamptz,
  ends_at timestamptz,
  lifted_at timestamptz,
  lifted_by uuid,
  lifted_by_name text,
  lift_note text,
  active boolean
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  perform private.require_permission('bans');
  return query
  select ban_row.*
  from private.account_ban_rows(p_user_id, false) as ban_row
  where p_user_id is not null
  order by ban_row.id desc;
end;
$$;

revoke all on function public.admin_get_user_bans(uuid) from public, anon;
grant execute on function public.admin_get_user_bans(uuid) to authenticated;

create or replace function public.admin_list_blocked_signups(
  p_limit integer default 50,
  p_offset integer default 0
)
returns table (
  id bigint,
  created_at timestamptz,
  method text,
  kind text,
  ban_id bigint,
  user_id uuid,
  username text,
  display_name text,
  total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
begin
  perform private.require_permission('bans');
  return query
  select
    block.id,
    block.created_at,
    block.method,
    block.kind,
    block.ban_id,
    ban.user_id,
    profile.username::text,
    profile.display_name,
    count(*) over ()
  from private.ban_signup_blocks as block
  left join private.account_bans as ban on ban.id = block.ban_id
  left join public.profiles as profile on profile.user_id = ban.user_id
  order by block.created_at desc, block.id desc
  limit v_limit
  offset v_offset;
end;
$$;

revoke all on function public.admin_list_blocked_signups(integer, integer) from public, anon;
grant execute on function public.admin_list_blocked_signups(integer, integer) to authenticated;

create or replace function private.prune_ban_records()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from private.ban_signals as signal
  where (signal.expires_at is not null and signal.expires_at <= now())
    or exists (
      select 1
      from private.account_bans as ban
      where ban.id = signal.ban_id
        and (ban.lifted_at is not null or (ban.ends_at is not null and ban.ends_at <= now()))
    );
  delete from private.ban_signup_blocks as block
  where block.created_at < now() - interval '90 days';
$$;

revoke all on function private.prune_ban_records() from public;

do $schedule$
begin
  if exists (
    select 1 from pg_available_extensions where name = 'pg_cron'
  ) then
    create extension if not exists pg_cron;
    if not exists (
      select 1 from cron.job where jobname = 'pocketpass-prune-ban-records'
    ) then
      perform cron.schedule(
        'pocketpass-prune-ban-records',
        '37 3 * * *',
        'select private.prune_ban_records();'
      );
    end if;
  end if;
end;
$schedule$;

commit;
