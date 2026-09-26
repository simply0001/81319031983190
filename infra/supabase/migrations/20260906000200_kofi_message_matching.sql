begin;

alter table private.kofi_events drop constraint if exists kofi_events_matched_by_known;
alter table private.kofi_events add constraint kofi_events_matched_by_known check (
  matched_by is null
  or matched_by in ('email', 'link', 'signup', 'admin', 'message', 'email_change')
);

create or replace function private.kofi_message_user(p_message text)
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_message text;
  v_users uuid[];
begin
  v_message := lower(coalesce(p_message, ''));
  if btrim(v_message) = '' then
    return null;
  end if;

  select array_agg(distinct candidate.user_id)
  into v_users
  from (
    select profile.user_id
    from regexp_matches(v_message, '(?:^|[^a-z0-9._-])@([a-z0-9][a-z0-9._-]{2,31})', 'g') as name_match
    join public.profiles as profile
      on profile.username = regexp_replace(name_match[1], '[._-]+$', '')::extensions.citext
    union
    select profile.user_id
    from regexp_matches(
      v_message,
      '(?:^|[^a-z0-9])(?:username|user|pocketpass|pp)\s*[:=]\s*@?([a-z0-9][a-z0-9._-]{2,31})',
      'g'
    ) as name_match
    join public.profiles as profile
      on profile.username = regexp_replace(name_match[1], '[._-]+$', '')::extensions.citext
    union
    select friend_code.user_id
    from regexp_matches(v_message, '(?:^|[^0-9])([0-9]{4})[ -]?([0-9]{4})(?:[^0-9]|$)', 'g') as code_match
    join public.friend_codes as friend_code
      on friend_code.code = code_match[1] || code_match[2]
  ) as candidate;

  if v_users is null or cardinality(v_users) <> 1 then
    return null;
  end if;

  return v_users[1];
end;
$$;

revoke all on function private.kofi_message_user(text) from public;

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

    if v_user_id is null then
      v_user_id := private.kofi_message_user(v_event.payload ->> 'message');
      if v_user_id is not null then
        v_matched_by := 'message';
        if v_event.email is not null then
          insert into private.kofi_links (email, user_id)
          values (v_event.email, v_user_id)
          on conflict (email) do nothing;
        end if;
      end if;
    end if;

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

create or replace function private.kofi_apply_events_for_changed_email()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_email text;
  v_event record;
begin
  v_email := lower(coalesce(new.email::text, ''));
  if v_email = '' or v_email = lower(coalesce(old.email::text, '')) then
    return new;
  end if;
  if not exists (
    select 1
    from public.profiles as profile
    where profile.user_id = new.id
  ) then
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
    set user_id = new.id,
        matched_by = 'email_change'
    where message_id = v_event.message_id;
    perform private.kofi_apply_event(v_event.message_id);
  end loop;
  return new;
exception
  when others then
    raise warning 'kofi_apply_events_for_changed_email: %', sqlerrm;
    return new;
end;
$$;

revoke all on function private.kofi_apply_events_for_changed_email() from public;

drop trigger if exists apply_kofi_events_on_email_change on auth.users;

create trigger apply_kofi_events_on_email_change
after update of email on auth.users
for each row
when (old.email is distinct from new.email)
execute function private.kofi_apply_events_for_changed_email();

commit;
