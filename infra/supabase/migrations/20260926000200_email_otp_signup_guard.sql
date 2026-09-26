begin;

-- GoTrue generates a temporary password while creating an email OTP account.
-- The account is passwordless: discard that hash before it reaches auth.users.
-- Public /auth/v1/signup is restricted to PocketPass username addresses at Kong.
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
  else
    new.encrypted_password := '';
  end if;

  return new;
end;
$$;

revoke all on function private.guard_password_signups() from public;

commit;
