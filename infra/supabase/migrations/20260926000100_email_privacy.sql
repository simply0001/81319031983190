begin;

-- Auth and Ko-fi matching still need their restricted, indexed email columns.
-- Do not keep a second copy in the raw webhook or administrative audit JSON.
create function private.strip_duplicate_email_payload()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.payload := new.payload - 'email';
  return new;
end;
$$;

revoke all on function private.strip_duplicate_email_payload() from public, anon, authenticated, service_role, api_client;

create trigger kofi_events_strip_duplicate_email
before insert or update of payload on private.kofi_events
for each row execute function private.strip_duplicate_email_payload();

create trigger admin_audit_strip_duplicate_email
before insert or update of payload on private.admin_audit
for each row execute function private.strip_duplicate_email_payload();

alter table private.kofi_events
  add constraint kofi_events_payload_no_email check (not (payload ? 'email')) not valid;

alter table private.admin_audit
  add constraint admin_audit_payload_no_email check (not (payload ? 'email')) not valid;

-- The pre-deployment encrypted backup preserves the original payment and audit
-- records for its normal retention period. Keep all non-email audit fields.
update private.kofi_events
set payload = payload - 'email'
where payload ? 'email';

update private.admin_audit
set payload = payload - 'email'
where payload ? 'email';

alter table private.kofi_events validate constraint kofi_events_payload_no_email;
alter table private.admin_audit validate constraint admin_audit_payload_no_email;

-- Discord is an alert channel, not the permissioned supporter dashboard.
-- Only derived status and identifiers go into the outbound message.
create function private.kofi_discord_notice(p_event private.kofi_events, p_until timestamptz)
returns text
language sql
stable
set search_path = ''
as $$
  select format(
    '☕ Ko-fi %s (%s): %s%s%s',
    case when p_event.qualifies then 'subscription' else 'payment' end,
    p_event.message_id,
    coalesce(p_event.amount::text, '?'),
    case when p_event.currency ~ '^[A-Z]{3}$' then ' ' || p_event.currency else '' end,
    case
      when not p_event.qualifies then E'\nRecorded only; no supporter time granted.'
      when p_event.user_id is not null then format(
        E'\nMatched account %s; supporter time until %s.',
        p_event.user_id,
        coalesce(to_char(p_until, 'DD Mon YYYY'), 'unknown')
      )
      else E'\nUnmatched; review in the admin Supporters tab.'
    end
  );
$$;

revoke all on function private.kofi_discord_notice(private.kofi_events, timestamptz)
  from public, anon, authenticated, service_role, api_client;

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
  v_until timestamptz;
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
    select status.active_until
    into v_until
    from public.supporter_status as status
    where status.user_id = v_event.user_id;
  end if;

  select net.http_post(
    url => v_url,
    body => jsonb_build_object(
      'content', left(private.kofi_discord_notice(v_event, v_until), 1900),
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

comment on table private.kofi_events is
  'Ko-fi deliveries with one restricted payer email for matching; raw payload omits its duplicate email and verification token.';
comment on table private.admin_audit is
  'Append-only administrative action log except for the one-time email-key scrub; actors, targets and timestamps are retained.';

commit;
