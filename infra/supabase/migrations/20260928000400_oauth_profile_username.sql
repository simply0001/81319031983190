begin;

create or replace function private.oauth_profile_metadata(p_user_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when profile.username is not null
      and profile.username::text <> replace(p_user_id::text, '-', '')
    then jsonb_build_object('username', profile.username::text, 'name', profile.username::text)
    else jsonb_build_object('name', 'PocketPass user')
  end
  from (select 1) as anchor
  left join public.profiles as profile on profile.user_id = p_user_id;
$$;

revoke all on function private.oauth_profile_metadata(uuid) from public;

create or replace function private.sanitize_oauth_metadata()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  new.raw_user_meta_data := private.oauth_profile_metadata(new.id);
  return new;
end;
$$;

revoke all on function private.sanitize_oauth_metadata() from public;

create trigger pocketpass_oauth_metadata_before_update
before update of raw_user_meta_data on auth.users
for each row execute function private.sanitize_oauth_metadata();

create or replace function private.sanitize_new_user_oauth_metadata()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update auth.users as account
  set raw_user_meta_data = private.oauth_profile_metadata(new.id)
  where account.id = new.id;
  return null;
end;
$$;

revoke all on function private.sanitize_new_user_oauth_metadata() from public;

create trigger pocketpass_oauth_metadata_after_insert
after insert on auth.users
for each row execute function private.sanitize_new_user_oauth_metadata();

create or replace function private.sync_oauth_metadata_username()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update auth.users as account
  set raw_user_meta_data = private.oauth_profile_metadata(new.user_id)
  where account.id = new.user_id;
  return null;
end;
$$;

revoke all on function private.sync_oauth_metadata_username() from public;

create trigger profiles_sync_oauth_metadata
after update of username on public.profiles
for each row
when (old.username is distinct from new.username)
execute function private.sync_oauth_metadata_username();

update auth.users as account
set raw_user_meta_data = private.oauth_profile_metadata(account.id);

create or replace function private.api_oidc_scope_descriptions()
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'email', 'your email address',
    'phone', 'your phone number',
    'profile', 'your PocketPass username'
  );
$$;

commit;
