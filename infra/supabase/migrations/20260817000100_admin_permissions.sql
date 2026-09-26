begin;

alter table private.admin_users
  add column permissions text[] not null default '{}'::text[],
  add column is_owner boolean not null default false,
  add column granted_by uuid,
  add column updated_at timestamptz not null default now(),
  add constraint admin_users_permissions_known check (
    permissions <@ array['users', 'audit', 'legacy', 'tokens', 'achievements', 'admins']::text[]
  );

comment on column private.admin_users.permissions is
  'Subset of private.admin_permission_keys(). Owners implicitly hold every permission.';

comment on column private.admin_users.is_owner is
  'Owners hold every permission, cannot be edited or removed from the console, and are promoted or demoted only with psql.';

create or replace function private.admin_permission_keys()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array['users', 'audit', 'legacy', 'tokens', 'achievements', 'admins']::text[];
$$;

revoke all on function private.admin_permission_keys() from public;

comment on function private.admin_permission_keys() is
  'users: list accounts with emails and open their detail; audit: read the audit log; legacy/tokens/achievements: the matching mutations; admins: manage other admins.';

update private.admin_users as admin
set is_owner = true,
    permissions = private.admin_permission_keys()
where admin.user_id in (
  select account.id
  from auth.users as account
  where lower(account.email) = 'nicolas.tobago@icloud.com'
);

do $$
begin
  if not exists (select 1 from private.admin_users as admin where admin.is_owner) then
    raise warning 'admin permissions: no owner row was found; promote one with psql (see README, Admin console)';
  end if;
end $$;

create or replace function private.effective_permissions(p_user_id uuid)
returns text[]
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when admin.is_owner then private.admin_permission_keys()
    else coalesce(admin.permissions, '{}'::text[])
  end
  from private.admin_users as admin
  where admin.user_id = p_user_id;
$$;

revoke all on function private.effective_permissions(uuid) from public;

create or replace function private.has_permission(p_permission text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from private.admin_users as admin
    where admin.user_id = auth.uid()
      and (admin.is_owner or p_permission = any (admin.permissions))
  );
$$;

revoke all on function private.has_permission(text) from public;

create or replace function private.require_permission(p_permission text)
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := private.require_admin();
begin
  if not private.has_permission(p_permission) then
    raise exception 'Permission required: %', p_permission using errcode = '42501';
  end if;
  return v_actor_id;
end;
$$;

revoke all on function private.require_permission(text) from public;

create or replace function public.admin_whoami()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  return (
    select jsonb_build_object(
      'user_id', account.id,
      'email', account.email::text,
      'display_name', profile.display_name,
      'is_admin', admin.user_id is not null,
      'is_owner', coalesce(admin.is_owner, false),
      'permissions', to_jsonb(coalesce(private.effective_permissions(account.id), '{}'::text[])),
      'granted_at', admin.granted_at
    )
    from auth.users as account
    left join public.profiles as profile on profile.user_id = account.id
    left join private.admin_users as admin on admin.user_id = account.id
    where account.id = v_actor_id
  );
end;
$$;

revoke all on function public.admin_whoami() from public, anon;
grant execute on function public.admin_whoami() to authenticated;

create or replace function public.admin_list_users(
  p_search text default null,
  p_limit integer default 50,
  p_offset integer default 0
)
returns table (
  user_id uuid,
  username text,
  display_name text,
  email text,
  country_code text,
  legacy_account boolean,
  created_at timestamptz,
  last_seen_at timestamptz,
  token_balance integer,
  achievements_unlocked bigint,
  friend_count bigint,
  encounter_count bigint,
  is_admin boolean,
  total_count bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_search text := nullif(btrim(coalesce(p_search, '')), '');
  v_pattern text;
  v_uuid uuid;
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
begin
  perform private.require_permission('users');

  if v_search is not null then
    v_pattern := '%' || replace(replace(replace(v_search, '\', '\\'), '%', '\%'), '_', '\_') || '%';
    begin
      v_uuid := v_search::uuid;
    exception when others then
      v_uuid := null;
    end;
  end if;

  return query
  select
    account.id,
    profile.username::text,
    profile.display_name,
    account.email::text,
    profile.country_code,
    coalesce(profile.legacy_account, false),
    account.created_at,
    profile.last_seen_at,
    coalesce(token_balance.balance, 0),
    (
      select count(*)
      from public.achievement_unlocks as unlock
      where unlock.user_id = account.id
    ),
    (
      select count(*)
      from public.friendships as friendship
      where account.id in (friendship.user_low, friendship.user_high)
    ),
    (
      select count(*)
      from public.nearby_encounters as encounter
      where account.id in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
    ),
    exists (
      select 1
      from private.admin_users as admin
      where admin.user_id = account.id
    ),
    count(*) over ()
  from auth.users as account
  left join public.profiles as profile on profile.user_id = account.id
  left join public.token_balances as token_balance on token_balance.user_id = account.id
  where v_search is null
    or account.id = v_uuid
    or profile.username::text ilike v_pattern
    or profile.display_name ilike v_pattern
    or account.email::text ilike v_pattern
  order by account.created_at desc, account.id
  limit v_limit
  offset v_offset;
end;
$$;

revoke all on function public.admin_list_users(text, integer, integer) from public, anon;
grant execute on function public.admin_list_users(text, integer, integer) to authenticated;

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
  where account.id = p_user_id;

  if v_result is null then
    raise exception 'User not found' using errcode = 'P0002';
  end if;
  return v_result;
end;
$$;

revoke all on function public.admin_get_user(uuid) from public, anon;
grant execute on function public.admin_get_user(uuid) to authenticated;

create or replace function public.admin_list_audit(
  p_target_user_id uuid default null,
  p_limit integer default 100,
  p_offset integer default 0
)
returns table (
  id bigint,
  created_at timestamptz,
  admin_id uuid,
  admin_email text,
  action text,
  target_user_id uuid,
  target_username text,
  target_email text,
  payload jsonb
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 100), 1), 500);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
begin
  perform private.require_permission('audit');
  return query
  select
    entry.id,
    entry.created_at,
    entry.admin_id,
    admin_account.email::text,
    entry.action,
    entry.target_user_id,
    target_profile.username::text,
    target_account.email::text,
    entry.payload
  from private.admin_audit as entry
  left join auth.users as admin_account on admin_account.id = entry.admin_id
  left join auth.users as target_account on target_account.id = entry.target_user_id
  left join public.profiles as target_profile on target_profile.user_id = entry.target_user_id
  where p_target_user_id is null or entry.target_user_id = p_target_user_id
  order by entry.id desc
  limit v_limit
  offset v_offset;
end;
$$;

revoke all on function public.admin_list_audit(uuid, integer, integer) from public, anon;
grant execute on function public.admin_list_audit(uuid, integer, integer) to authenticated;

create or replace function public.admin_set_legacy_account(
  p_user_id uuid,
  p_legacy boolean
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('legacy');
begin
  if p_legacy is null then
    raise exception 'p_legacy is required' using errcode = '22023';
  end if;

  update public.profiles as profile
  set legacy_account = p_legacy
  where profile.user_id = p_user_id;
  if not found then
    raise exception 'Profile not found' using errcode = 'P0002';
  end if;

  if p_legacy then
    insert into public.achievement_unlocks (user_id, achievement_key)
    values (p_user_id, 'day_one')
    on conflict on constraint achievement_unlocks_pkey do nothing;
  end if;

  perform private.record_admin_action(
    v_admin_id,
    'set_legacy_account',
    p_user_id,
    jsonb_build_object('legacy', p_legacy)
  );

  return jsonb_build_object('user_id', p_user_id, 'legacy_account', p_legacy);
end;
$$;

revoke all on function public.admin_set_legacy_account(uuid, boolean) from public, anon;
grant execute on function public.admin_set_legacy_account(uuid, boolean) to authenticated;

create or replace function public.admin_adjust_tokens(
  p_user_id uuid,
  p_delta integer,
  p_reason text,
  p_user_message text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('tokens');
  v_reason text := nullif(btrim(coalesce(p_reason, '')), '');
  v_message text := nullif(btrim(coalesce(p_user_message, '')), '');
  v_before integer;
  v_after integer;
begin
  if p_delta is null or p_delta = 0 or abs(p_delta) > 100000 then
    raise exception 'p_delta must be a non-zero integer up to 100000' using errcode = '22023';
  end if;
  if v_reason is null or length(v_reason) > 200 then
    raise exception 'p_reason must be 1 to 200 characters' using errcode = '22023';
  end if;
  if not exists (
    select 1 from public.profiles as profile where profile.user_id = p_user_id
  ) then
    raise exception 'Profile not found' using errcode = 'P0002';
  end if;

  perform private.ensure_token_balance(p_user_id);

  select token_balance.balance
  into v_before
  from public.token_balances as token_balance
  where token_balance.user_id = p_user_id
  for update;

  if v_before + p_delta < 0 then
    raise exception 'Insufficient balance: user has % tokens', v_before using errcode = '22023';
  end if;

  update public.token_balances as token_balance
  set balance = token_balance.balance + p_delta,
      updated_at = now()
  where token_balance.user_id = p_user_id
  returning token_balance.balance into v_after;

  insert into public.notifications (recipient_id, kind, title, body)
  values (
    p_user_id,
    'system',
    case when p_delta > 0 then 'Tokens added' else 'Tokens removed' end,
    left(
      coalesce(
        v_message,
        to_char(p_delta, 'FMSG999999') || ' tokens from the PocketPass team'
      ),
      500
    )
  );

  perform private.record_admin_action(
    v_admin_id,
    'adjust_tokens',
    p_user_id,
    jsonb_build_object(
      'delta', p_delta,
      'reason', v_reason,
      'user_message', v_message,
      'balance_before', v_before,
      'balance_after', v_after
    )
  );

  return jsonb_build_object(
    'user_id', p_user_id,
    'balance_before', v_before,
    'balance_after', v_after
  );
end;
$$;

revoke all on function public.admin_adjust_tokens(uuid, integer, text, text) from public, anon;
grant execute on function public.admin_adjust_tokens(uuid, integer, text, text) to authenticated;

create or replace function public.admin_set_achievement(
  p_user_id uuid,
  p_achievement_key text,
  p_unlocked boolean
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('achievements');
  v_changed integer := 0;
begin
  if p_achievement_key is null or not (p_achievement_key = any (private.achievement_keys())) then
    raise exception 'Unknown achievement key' using errcode = '22023';
  end if;
  if p_unlocked is null then
    raise exception 'p_unlocked is required' using errcode = '22023';
  end if;
  if not exists (
    select 1 from public.profiles as profile where profile.user_id = p_user_id
  ) then
    raise exception 'Profile not found' using errcode = 'P0002';
  end if;

  if p_unlocked then
    insert into public.achievement_unlocks (user_id, achievement_key)
    values (p_user_id, p_achievement_key)
    on conflict on constraint achievement_unlocks_pkey do nothing;
  else
    delete from public.achievement_unlocks as unlock
    where unlock.user_id = p_user_id
      and unlock.achievement_key = p_achievement_key;
  end if;
  get diagnostics v_changed = row_count;

  perform private.record_admin_action(
    v_admin_id,
    'set_achievement',
    p_user_id,
    jsonb_build_object(
      'key', p_achievement_key,
      'unlocked', p_unlocked,
      'changed', v_changed > 0
    )
  );

  return jsonb_build_object(
    'user_id', p_user_id,
    'achievement_key', p_achievement_key,
    'unlocked', p_unlocked,
    'changed', v_changed > 0
  );
end;
$$;

revoke all on function public.admin_set_achievement(uuid, text, boolean) from public, anon;
grant execute on function public.admin_set_achievement(uuid, text, boolean) to authenticated;

create or replace function public.admin_list_admins()
returns table (
  user_id uuid,
  email text,
  display_name text,
  is_owner boolean,
  permissions text[],
  granted_at timestamptz,
  granted_by uuid,
  granted_by_email text,
  note text,
  updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  perform private.require_permission('admins');
  return query
  select
    admin.user_id,
    account.email::text,
    profile.display_name,
    admin.is_owner,
    private.effective_permissions(admin.user_id),
    admin.granted_at,
    admin.granted_by,
    granter.email::text,
    admin.note,
    admin.updated_at
  from private.admin_users as admin
  left join auth.users as account on account.id = admin.user_id
  left join public.profiles as profile on profile.user_id = admin.user_id
  left join auth.users as granter on granter.id = admin.granted_by
  order by admin.is_owner desc, admin.granted_at, admin.user_id;
end;
$$;

revoke all on function public.admin_list_admins() from public, anon;
grant execute on function public.admin_list_admins() to authenticated;

create or replace function private.validate_admin_permissions(p_permissions text[])
returns text[]
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_permissions text[] := coalesce(p_permissions, '{}'::text[]);
begin
  if not (v_permissions <@ private.admin_permission_keys()) then
    raise exception 'Unknown permission key' using errcode = '22023';
  end if;
  return (
    select coalesce(array_agg(distinct permission order by permission), '{}'::text[])
    from unnest(v_permissions) as permission
  );
end;
$$;

revoke all on function private.validate_admin_permissions(text[]) from public;

create or replace function public.admin_add_admin(
  p_email text,
  p_permissions text[],
  p_note text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('admins');
  v_permissions text[] := private.validate_admin_permissions(p_permissions);
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_target_id uuid;
begin
  if v_email = '' then
    raise exception 'p_email is required' using errcode = '22023';
  end if;

  select account.id
  into v_target_id
  from auth.users as account
  where lower(account.email) = v_email
  order by account.created_at
  limit 1;
  if v_target_id is null then
    raise exception 'No PocketPass account with that email' using errcode = 'P0002';
  end if;
  if exists (select 1 from private.admin_users as admin where admin.user_id = v_target_id) then
    raise exception 'Already an admin' using errcode = '23505';
  end if;

  insert into private.admin_users (user_id, permissions, granted_by, note)
  values (v_target_id, v_permissions, v_admin_id, left(coalesce(p_note, ''), 200));

  perform private.record_admin_action(
    v_admin_id,
    'add_admin',
    v_target_id,
    jsonb_build_object(
      'email', v_email,
      'permissions', to_jsonb(v_permissions),
      'note', left(coalesce(p_note, ''), 200)
    )
  );

  return jsonb_build_object(
    'user_id', v_target_id,
    'email', v_email,
    'permissions', to_jsonb(v_permissions)
  );
end;
$$;

revoke all on function public.admin_add_admin(text, text[], text) from public, anon;
grant execute on function public.admin_add_admin(text, text[], text) to authenticated;

create or replace function public.admin_set_admin_permissions(
  p_user_id uuid,
  p_permissions text[],
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('admins');
  v_permissions text[] := private.validate_admin_permissions(p_permissions);
  v_before text[];
  v_owner boolean;
begin
  select admin.permissions, admin.is_owner
  into v_before, v_owner
  from private.admin_users as admin
  where admin.user_id = p_user_id;
  if not found then
    raise exception 'Admin not found' using errcode = 'P0002';
  end if;
  if v_owner then
    raise exception 'Owners are managed with psql' using errcode = '42501';
  end if;
  if p_user_id = v_admin_id and not ('admins' = any (v_permissions)) then
    raise exception 'You cannot remove your own admin-management permission' using errcode = '22023';
  end if;

  update private.admin_users as admin
  set permissions = v_permissions,
      note = coalesce(left(p_note, 200), admin.note),
      updated_at = now()
  where admin.user_id = p_user_id;

  perform private.record_admin_action(
    v_admin_id,
    'set_admin_permissions',
    p_user_id,
    jsonb_build_object(
      'before', to_jsonb(v_before),
      'after', to_jsonb(v_permissions)
    )
  );

  return jsonb_build_object(
    'user_id', p_user_id,
    'permissions', to_jsonb(v_permissions)
  );
end;
$$;

revoke all on function public.admin_set_admin_permissions(uuid, text[], text) from public, anon;
grant execute on function public.admin_set_admin_permissions(uuid, text[], text) to authenticated;

create or replace function public.admin_remove_admin(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_permission('admins');
  v_owner boolean;
  v_email text;
begin
  select admin.is_owner, account.email::text
  into v_owner, v_email
  from private.admin_users as admin
  left join auth.users as account on account.id = admin.user_id
  where admin.user_id = p_user_id;
  if not found then
    raise exception 'Admin not found' using errcode = 'P0002';
  end if;
  if v_owner then
    raise exception 'Owners are managed with psql' using errcode = '42501';
  end if;
  if p_user_id = v_admin_id then
    raise exception 'You cannot remove yourself' using errcode = '22023';
  end if;

  delete from private.admin_users as admin
  where admin.user_id = p_user_id;

  perform private.record_admin_action(
    v_admin_id,
    'remove_admin',
    p_user_id,
    jsonb_build_object('email', v_email)
  );

  return jsonb_build_object('user_id', p_user_id, 'removed', true);
end;
$$;

revoke all on function public.admin_remove_admin(uuid) from public, anon;
grant execute on function public.admin_remove_admin(uuid) to authenticated;

commit;
