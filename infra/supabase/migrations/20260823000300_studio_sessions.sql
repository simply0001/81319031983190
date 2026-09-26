begin;

create table private.admin_studio_sessions (
  token_hash bytea primary key,
  user_id uuid not null references private.admin_users (user_id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);

create index admin_studio_sessions_user_idx on private.admin_studio_sessions (user_id);

revoke all on table private.admin_studio_sessions from public, anon, authenticated;

create or replace function public.admin_studio_session_create()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_admin();
  v_token text;
  v_expires_at timestamptz;
begin
  if not exists (
    select 1
    from private.admin_users as admin
    where admin.user_id = v_admin_id
      and admin.is_owner
  ) then
    raise sqlstate 'PT403' using
      message = 'Only owners can open Studio',
      hint = 'OWNER_REQUIRED';
  end if;

  delete from private.admin_studio_sessions as session
  where session.user_id = v_admin_id
     or session.expires_at <= now();

  v_token := encode(extensions.gen_random_bytes(32), 'hex');
  v_expires_at := now() + interval '12 hours';

  insert into private.admin_studio_sessions (token_hash, user_id, expires_at)
  values (extensions.digest(v_token::text, 'sha256'), v_admin_id, v_expires_at);

  perform private.record_admin_action(
    v_admin_id,
    'studio_session_create',
    null,
    jsonb_build_object('expires_at', v_expires_at)
  );

  return jsonb_build_object('token', v_token, 'expires_at', v_expires_at);
end;
$$;

revoke all on function public.admin_studio_session_create() from public, anon;
grant execute on function public.admin_studio_session_create() to authenticated;

create or replace function public.admin_studio_sessions_revoke()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_admin_id uuid := private.require_admin();
  v_revoked integer;
begin
  delete from private.admin_studio_sessions as session
  where session.user_id = v_admin_id;
  get diagnostics v_revoked = row_count;

  if v_revoked > 0 then
    perform private.record_admin_action(
      v_admin_id,
      'studio_sessions_revoke',
      null,
      jsonb_build_object('revoked', v_revoked)
    );
  end if;

  return v_revoked;
end;
$$;

revoke all on function public.admin_studio_sessions_revoke() from public, anon;
grant execute on function public.admin_studio_sessions_revoke() to authenticated;

create or replace function public.studio_gate()
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_token text := nullif(current_setting('request.cookies', true), '')::json ->> 'pocketpass_studio';
begin
  if v_token is null or v_token !~ '^[0-9a-f]{64}$' then
    raise sqlstate 'PT401' using
      message = 'Open Studio from the admin console',
      hint = 'STUDIO_SESSION_REQUIRED';
  end if;

  if not exists (
    select 1
    from private.admin_studio_sessions as session
    join private.admin_users as admin on admin.user_id = session.user_id
    where session.token_hash = extensions.digest(v_token::text, 'sha256')
      and session.expires_at > now()
      and admin.is_owner
  ) then
    raise sqlstate 'PT401' using
      message = 'Open Studio from the admin console',
      hint = 'STUDIO_SESSION_REQUIRED';
  end if;

  return true;
end;
$$;

revoke all on function public.studio_gate() from public, authenticated;
grant execute on function public.studio_gate() to anon;

commit;
