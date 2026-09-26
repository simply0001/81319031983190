begin;

create or replace function private.login_email_domain()
returns text
language sql
immutable
set search_path = ''
as $$
  select 'users.pocketpass.xyz'::text
$$;

revoke all on function private.login_email_domain() from public;

create or replace function private.is_login_username(p_username text)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p_username is not null
    and char_length(p_username) between 3 and 12
    and p_username ~ '^[a-z0-9]+(\.[a-z0-9]+)*$'
$$;

revoke all on function private.is_login_username(text) from public;

create or replace function private.login_username_for_email(p_email text)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_email is not null
      and lower(p_email) like ('%@' || private.login_email_domain())
    then split_part(lower(p_email), '@', 1)
    else null
  end
$$;

revoke all on function private.login_username_for_email(text) from public;

create or replace function private.guard_password_signups()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  v_login_username text;
begin
  if current_user <> 'supabase_auth_admin'
    and coalesce(current_setting('pocketpass.enforce_signup_guard', true), '') <> 'on'
  then
    return new;
  end if;

  v_login_username := private.login_username_for_email(new.email);
  if v_login_username is not null then
    if not private.is_login_username(v_login_username) then
      raise exception 'PocketPass usernames are 3-12 lowercase letters, numbers or dots'
        using errcode = '22023';
    end if;
    if coalesce(new.encrypted_password, '') = '' then
      raise exception 'PocketPass username accounts need a password'
        using errcode = '22023';
    end if;
  elsif coalesce(new.encrypted_password, '') <> '' then
    raise exception 'Password sign-up is only available with a PocketPass username'
      using errcode = '22023';
  end if;

  return new;
end;
$$;

revoke all on function private.guard_password_signups() from public;

grant usage on schema private to supabase_auth_admin;
grant execute on function private.login_email_domain() to supabase_auth_admin;
grant execute on function private.is_login_username(text) to supabase_auth_admin;
grant execute on function private.login_username_for_email(text) to supabase_auth_admin;
grant execute on function private.guard_password_signups() to supabase_auth_admin;

drop trigger if exists guard_password_signups on auth.users;

create trigger guard_password_signups
before insert on auth.users
for each row execute function private.guard_password_signups();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_display_name text;
  v_login_username text;
begin
  v_login_username := private.login_username_for_email(new.email);
  if v_login_username is not null then
    insert into public.profiles (user_id, username, display_name)
    values (
      new.id,
      v_login_username::extensions.citext,
      v_login_username
    )
    on conflict (user_id) do nothing;

    return new;
  end if;

  v_display_name := left(
    coalesce(
      nullif(btrim(new.raw_user_meta_data ->> 'display_name'), ''),
      nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''),
      nullif(btrim(new.raw_user_meta_data ->> 'user_name'), ''),
      'PocketPass User'
    ),
    48
  );

  insert into public.profiles (user_id, username, display_name)
  values (
    new.id,
    replace(new.id::text, '-', '')::extensions.citext,
    v_display_name
  )
  on conflict (user_id) do nothing;

  return new;
end;
$$;

revoke all on function public.handle_new_user() from public;

create or replace function public.username_available(p_username text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select private.is_login_username(lower(btrim(coalesce(p_username, ''))))
    and not exists (
      select 1
      from public.profiles as profile
      where profile.username = lower(btrim(p_username))::extensions.citext
    )
$$;

revoke all on function public.username_available(text) from public;
grant execute on function public.username_available(text) to anon, authenticated;

commit;
