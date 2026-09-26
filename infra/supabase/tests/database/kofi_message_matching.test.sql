begin;

set local search_path = public, extensions;

select extensions.plan(27);

create function pg_temp.kofi_event(
  p_id uuid,
  p_email text,
  p_message text,
  p_qualifies boolean default true
) returns uuid
language sql
volatile
as $$
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
    p_id,
    'pgtap-' || p_id::text,
    case when p_qualifies then 'Subscription' else 'Donation' end,
    lower(p_email),
    'Kofi Fan pgtap',
    case when p_qualifies then 'Bronze' else null end,
    p_qualifies,
    3.00,
    'USD',
    now(),
    jsonb_build_object(
      'message_id', p_id::text,
      'type', case when p_qualifies then 'Subscription' else 'Donation' end,
      'email', p_email,
      'message', p_message,
      'from_name', 'Kofi Fan pgtap'
    ),
    p_qualifies
  )
  returning message_id;
$$;

insert into auth.users (
  instance_id,
  id,
  aud,
  role,
  email,
  encrypted_password,
  email_confirmed_at,
  raw_app_meta_data,
  raw_user_meta_data,
  created_at,
  updated_at,
  confirmation_token,
  email_change,
  email_change_token_new,
  recovery_token
)
select
  '00000000-0000-0000-0000-000000000000',
  seed.id,
  'authenticated',
  'authenticated',
  seed.email,
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  jsonb_build_object('username', seed.name),
  now(),
  now(),
  '',
  '',
  '',
  ''
from (
  values
    ('99410000-0000-4000-8000-000000000001'::uuid, 'pp.fan@users.pocketpass.xyz', 'pp.fan'),
    ('99410000-0000-4000-8000-000000000002'::uuid, 'pp.other@users.pocketpass.xyz', 'pp.other')
) as seed(id, email, name);

select pg_catalog.set_config(
  'pgtap.fan_code',
  private.ensure_friend_code('99410000-0000-4000-8000-000000000001'),
  true
);

select extensions.is(
  (select username::text from public.profiles where user_id = '99410000-0000-4000-8000-000000000001'),
  'pp.fan',
  'the username account owns its username'
);

select extensions.ok(
  private.kofi_apply_event(pg_temp.kofi_event(
    '99410000-0000-4000-8000-000000000101',
    'payer.one@example.com',
    'Thanks for the app! @pp.fan'
  )),
  'a payment whose message names @username is applied'
);

select extensions.is(
  (select matched_by from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000101'),
  'message',
  'the match is recorded as a message match'
);

select extensions.is(
  (select user_id from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000101'),
  '99410000-0000-4000-8000-000000000001'::uuid,
  'the payment is attributed to the named account'
);

select extensions.ok(
  (
    select status.active_until > now()
    from public.supporter_status as status
    where status.user_id = '99410000-0000-4000-8000-000000000001'
  ),
  'the named account becomes a supporter'
);

select extensions.is(
  (select user_id from private.kofi_links where email = 'payer.one@example.com'),
  '99410000-0000-4000-8000-000000000001'::uuid,
  'the payer email is linked to the named account for later payments'
);

select extensions.ok(
  private.kofi_apply_event(pg_temp.kofi_event(
    '99410000-0000-4000-8000-000000000102',
    'payer.one@example.com',
    null
  )),
  'a renewal from the same payer without a message is applied'
);

select extensions.is(
  (select matched_by from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000102'),
  'link',
  'the renewal matches through the automatic link'
);

select extensions.ok(
  private.kofi_apply_event(pg_temp.kofi_event(
    '99410000-0000-4000-8000-000000000103',
    'payer.two@example.com',
    'my code is '
      || left(current_setting('pgtap.fan_code', true), 4)
      || ' '
      || right(current_setting('pgtap.fan_code', true), 4)
  )),
  'a payment whose message contains the friend code is applied'
);

select extensions.is(
  (select user_id from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000103'),
  '99410000-0000-4000-8000-000000000001'::uuid,
  'the friend code resolves to its owner'
);

select extensions.is(
  (select matched_by from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000103'),
  'message',
  'the friend code match is recorded as a message match'
);

select extensions.ok(
  not private.kofi_apply_event(pg_temp.kofi_event(
    '99410000-0000-4000-8000-000000000104',
    'payer.three@example.com',
    '@pp.fan and @pp.other'
  )),
  'a message naming two accounts is not applied'
);

select extensions.is(
  (select user_id from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000104'),
  null::uuid,
  'an ambiguous message leaves the payment unmatched'
);

select extensions.is(
  (select count(*) from private.kofi_links where email = 'payer.three@example.com'),
  0::bigint,
  'an ambiguous message creates no link'
);

select extensions.ok(
  not private.kofi_apply_event(pg_temp.kofi_event(
    '99410000-0000-4000-8000-000000000105',
    'payer.four@example.com',
    'hello @nobody.here'
  )),
  'an unknown username is not applied'
);

select extensions.is(
  (select user_id from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000105'),
  null::uuid,
  'an unknown username leaves the payment unmatched'
);

select extensions.ok(
  private.kofi_apply_event(pg_temp.kofi_event(
    '99410000-0000-4000-8000-000000000106',
    'payer.five@example.com',
    'Username: PP.OTHER!'
  )),
  'a "username:" prefix with different casing and punctuation is applied'
);

select extensions.is(
  (select user_id from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000106'),
  '99410000-0000-4000-8000-000000000002'::uuid,
  'the prefixed username resolves to its owner'
);

select extensions.ok(
  not private.kofi_apply_event(pg_temp.kofi_event(
    '99410000-0000-4000-8000-000000000107',
    'linked.fan@pocketpass.test',
    null
  )),
  'a payment from an address no account uses stays unapplied'
);

update auth.users
set email = 'linked.fan@pocketpass.test'
where id = '99410000-0000-4000-8000-000000000001';

select extensions.is(
  (select user_id from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000107'),
  '99410000-0000-4000-8000-000000000001'::uuid,
  'linking that email address attributes the waiting payment'
);

select extensions.is(
  (select matched_by from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000107'),
  'email_change',
  'the match is recorded as an email change match'
);

select extensions.ok(
  (select applied_at is not null from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000107'),
  'the waiting payment is applied when the email is linked'
);

select extensions.ok(
  not private.kofi_apply_event(pg_temp.kofi_event(
    '99410000-0000-4000-8000-000000000108',
    'payer.six@example.com',
    '@pp.other',
    false
  )),
  'a donation naming an account does not unlock hats'
);

select extensions.is(
  (select user_id from private.kofi_events where message_id = '99410000-0000-4000-8000-000000000108'),
  '99410000-0000-4000-8000-000000000002'::uuid,
  'the donation is still attributed to the named account'
);

select extensions.is(
  (select user_id from private.kofi_links where email = 'payer.six@example.com'),
  '99410000-0000-4000-8000-000000000002'::uuid,
  'the donor email is linked so a later membership matches'
);

select extensions.ok(
  private.kofi_apply_event(pg_temp.kofi_event(
    '99410000-0000-4000-8000-000000000109',
    'payer.seven@example.com',
    'for @pp.fan.'
  )),
  'trailing punctuation after the username is ignored'
);

select extensions.throws_ok(
  $$
    update private.kofi_events
    set matched_by = 'bogus'
    where message_id = '99410000-0000-4000-8000-000000000109'
  $$,
  '23514',
  null,
  'unknown match kinds are still rejected'
);

select * from extensions.finish();

rollback;
