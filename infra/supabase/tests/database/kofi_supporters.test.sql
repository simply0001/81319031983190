begin;

set local search_path = public, extensions;

select extensions.plan(81);

create function pg_temp.mii_appearance() returns jsonb
language sql
immutable
as $$
  select '{
    "schemaVersion":1,
    "gender":0,
    "favoriteColor":3,
    "build":64,
    "height":64,
    "faceType":0,
    "skinColor":0,
    "wrinklesType":0,
    "makeupType":0,
    "hairType":33,
    "hairColor":1,
    "flipHair":false,
    "eyeType":2,
    "eyeColor":0,
    "eyeScale":4,
    "eyeVerticalStretch":3,
    "eyeRotation":4,
    "eyeSpacing":2,
    "eyeYPosition":12,
    "eyebrowType":6,
    "eyebrowColor":1,
    "eyebrowScale":4,
    "eyebrowVerticalStretch":3,
    "eyebrowRotation":6,
    "eyebrowSpacing":2,
    "eyebrowYPosition":10,
    "noseType":1,
    "noseScale":4,
    "noseYPosition":9,
    "mouthType":23,
    "mouthColor":0,
    "mouthScale":4,
    "mouthHorizontalStretch":3,
    "mouthYPosition":13,
    "mustacheType":0,
    "mustacheScale":4,
    "mustacheYPosition":10,
    "beardType":0,
    "facialHairColor":0,
    "glassesType":0,
    "glassesColor":0,
    "glassesScale":4,
    "glassesYPosition":10,
    "moleEnabled":false,
    "moleScale":4,
    "moleXPosition":2,
    "moleYPosition":20,
    "extHatType":-1,
    "extHatColor":-1,
    "extFacePaintColor":-1
  }'::jsonb;
$$;

create function pg_temp.hat_appearance(p_hat_type integer) returns jsonb
language sql
immutable
as $$
  select pg_temp.mii_appearance()
    || jsonb_build_object('extHatType', p_hat_type, 'extHatColor', 3);
$$;

create function pg_temp.kofi_payload(
  p_message_id text,
  p_type text,
  p_email text,
  p_paid_at timestamptz,
  p_extra jsonb default '{}'::jsonb
) returns text
language sql
stable
as $$
  select (
    jsonb_build_object(
      'verification_token', current_setting('pgtap.kofi_token', true),
      'message_id', p_message_id,
      'timestamp', p_paid_at::text,
      'type', p_type,
      'is_public', true,
      'from_name', 'Kofi Fan pgtap',
      'message', 'pgtap',
      'amount', '3.00',
      'url', 'https://ko-fi.com/pgtap',
      'email', p_email,
      'currency', 'USD',
      'is_subscription_payment', p_type = 'Subscription',
      'is_first_subscription_payment', p_type = 'Subscription',
      'kofi_transaction_id', 'pgtap-' || p_message_id,
      'shop_items', null,
      'tier_name', case when p_type = 'Subscription' then 'Bronze' else null end,
      'shipping', null
    ) || p_extra
  )::text;
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
  jsonb_build_object('display_name', seed.name),
  now(),
  now(),
  '',
  '',
  '',
  ''
from (
  values
    ('99400000-0000-4000-8000-000000000001'::uuid, 'kofi-fan@pocketpass.test', 'Kofi Fan pgtap'),
    ('99400000-0000-4000-8000-000000000002'::uuid, 'kofi-linked@pocketpass.test', 'Kofi Linked pgtap'),
    ('99400000-0000-4000-8000-000000000003'::uuid, 'kofi-ghost@pocketpass.test', 'Kofi Ghost pgtap'),
    ('99400000-0000-4000-8000-000000000005'::uuid, 'kofi-admin@pocketpass.test', 'Kofi Admin pgtap'),
    ('99400000-0000-4000-8000-000000000006'::uuid, 'kofi-plain@pocketpass.test', 'Kofi Plain pgtap')
) as seed(id, email, name);

update auth.users set deleted_at = now() where id = '99400000-0000-4000-8000-000000000003';

insert into private.admin_users (user_id, note, permissions)
values
  ('99400000-0000-4000-8000-000000000005', 'pgtap supporters admin', array['users', 'supporters']),
  ('99400000-0000-4000-8000-000000000006', 'pgtap plain admin', '{}');

insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
select
  'avatars',
  '99400000-0000-4000-8000-000000000001/mii-r' || step || '-99400000-0000-4000-8000-0000000000' || (20 + step) || '.png',
  '99400000-0000-4000-8000-000000000001',
  '99400000-0000-4000-8000-000000000001',
  '{"mimetype":"image/png","size":1024}'
from generate_series(1, 2) as step;

do $$
begin
  if not exists (select 1 from vault.secrets where name = 'kofi_verification_token') then
    perform vault.create_secret('pgtap-kofi-verification-token', 'kofi_verification_token', 'pgtap');
  end if;
end;
$$;

select pg_catalog.set_config(
  'pgtap.kofi_token',
  (
    select secret.decrypted_secret
    from vault.decrypted_secrets as secret
    where secret.name = 'kofi_verification_token'
    order by secret.created_at desc
    limit 1
  ),
  true
);

select extensions.has_table('private', 'kofi_events', 'kofi_events table exists');
select extensions.has_table('private', 'kofi_links', 'kofi_links table exists');
select extensions.has_table('public', 'supporter_status', 'supporter_status table exists');

select extensions.is(
  private.admin_permission_keys(),
  array['users', 'audit', 'legacy', 'tokens', 'achievements', 'admins', 'apps', 'supporters']::text[],
  'the admin permission catalog gained supporters'
);

select extensions.ok(
  has_function_privilege('anon', 'public.kofi_webhook(text)', 'execute'),
  'anon can execute the Ko-fi webhook'
);

select extensions.ok(
  not has_function_privilege('authenticated', 'public.kofi_webhook(text)', 'execute'),
  'authenticated cannot execute the Ko-fi webhook'
);

select extensions.ok(
  not has_function_privilege('api_client', 'public.kofi_webhook(text)', 'execute'),
  'api_client cannot execute the Ko-fi webhook'
);

select extensions.ok(
  not has_table_privilege('api_client', 'public.supporter_status', 'select'),
  'api_client cannot read supporter_status'
);

select extensions.ok(
  has_table_privilege('authenticated', 'public.supporter_status', 'select'),
  'authenticated can read supporter_status'
);

select extensions.ok(
  not has_table_privilege('authenticated', 'private.kofi_events', 'select'),
  'authenticated cannot read kofi_events'
);

select extensions.has_trigger(
  'public',
  'supporter_status',
  'supporter_status_broadcast_change',
  'supporter status changes broadcast to the tokens topic'
);

select extensions.has_trigger(
  'public',
  'profiles',
  'profiles_apply_kofi_events',
  'new profiles pick up pending Ko-fi payments'
);

select extensions.ok(
  not private.is_supporter('99400000-0000-4000-8000-000000000001'),
  'a user without a status row is not a supporter'
);

select extensions.ok(
  not private.owns_mii_hat('99400000-0000-4000-8000-000000000001', 9),
  'the halo is locked before any payment'
);

set local role anon;
select pg_catalog.set_config('request.jwt.claim.role', 'anon', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select extensions.throws_ok(
  $$select public.kofi_webhook(null)$$,
  'PT400',
  'Malformed Ko-fi payload',
  'an empty delivery is rejected'
);

select extensions.throws_ok(
  $$select public.kofi_webhook('not json')$$,
  'PT400',
  'Malformed Ko-fi payload',
  'a non-JSON delivery is rejected'
);

select extensions.throws_ok(
  $$select public.kofi_webhook('[1, 2]')$$,
  'PT400',
  'Malformed Ko-fi payload',
  'a JSON array is rejected'
);

select extensions.throws_ok(
  format(
    $$select public.kofi_webhook(%L)$$,
    pg_temp.kofi_payload(
      '99400000-0000-4000-8000-00000000a000',
      'Subscription',
      'kofi-fan@pocketpass.test',
      now(),
      '{"verification_token": "wrong-token"}'::jsonb
    )
  ),
  'PT401',
  'Invalid verification token',
  'a wrong verification token is rejected'
);

select extensions.throws_ok(
  format(
    $$select public.kofi_webhook(%L)$$,
    pg_temp.kofi_payload(
      'not-a-uuid',
      'Subscription',
      'kofi-fan@pocketpass.test',
      now()
    )
  ),
  'PT400',
  'Ko-fi payload has no message_id',
  'a delivery without a usable message_id is rejected'
);

select extensions.is(
  (
    select public.kofi_webhook(
      pg_temp.kofi_payload(
        '99400000-0000-4000-8000-00000000a001',
        'Subscription',
        'Kofi-Fan@pocketpass.test',
        now() - interval '2 days'
      )
    ) - 'duplicate'
  ),
  '{"received": true, "matched": true, "applied": true}'::jsonb,
  'a membership payment for a sign-in email is matched and applied'
);

select extensions.is(
  (
    select public.kofi_webhook(
      pg_temp.kofi_payload(
        '99400000-0000-4000-8000-00000000a001',
        'Subscription',
        'Kofi-Fan@pocketpass.test',
        now() - interval '2 days'
      )
    ) ->> 'duplicate'
  ),
  'true',
  'a retried message_id is reported as a duplicate'
);

select extensions.is(
  (
    select public.kofi_webhook(
      pg_temp.kofi_payload(
        '99400000-0000-4000-8000-00000000a002',
        'Donation',
        'kofi-fan@pocketpass.test',
        now() - interval '1 day'
      )
    ) - 'duplicate'
  ),
  '{"received": true, "matched": true, "applied": false}'::jsonb,
  'a donation is recorded but does not unlock anything'
);

select extensions.is(
  (
    select public.kofi_webhook(
      pg_temp.kofi_payload(
        '99400000-0000-4000-8000-00000000a003',
        'Subscription',
        'stranger@example.com',
        now() - interval '3 days'
      )
    ) - 'duplicate'
  ),
  '{"received": true, "matched": false, "applied": false}'::jsonb,
  'an unknown email is stored unmatched'
);

select extensions.is(
  (
    select public.kofi_webhook(
      pg_temp.kofi_payload(
        '99400000-0000-4000-8000-00000000a004',
        'Subscription',
        'kofi-ghost@pocketpass.test',
        now()
      )
    ) ->> 'matched'
  ),
  'false',
  'a soft-deleted account is not matched'
);

select extensions.is(
  (
    select public.kofi_webhook(
      pg_temp.kofi_payload(
        '99400000-0000-4000-8000-00000000a005',
        'Subscription',
        'late@pocketpass.test',
        now() - interval '10 days'
      )
    ) ->> 'matched'
  ),
  'false',
  'a payment made before signing up waits unmatched'
);

reset role;

select extensions.is(
  (select count(*) from private.kofi_events),
  5::bigint,
  'only accepted deliveries are stored'
);

select extensions.is(
  (
    select status.active_until
    from public.supporter_status as status
    where status.user_id = '99400000-0000-4000-8000-000000000001'
  ),
  (
    select event.paid_at + interval '36 days'
    from private.kofi_events as event
    where event.message_id = '99400000-0000-4000-8000-00000000a001'
  ),
  'a membership payment unlocks 36 days from the payment time'
);

select extensions.ok(
  private.is_supporter('99400000-0000-4000-8000-000000000001'),
  'the matched user is a supporter'
);

select extensions.ok(
  private.owns_mii_hat('99400000-0000-4000-8000-000000000001', 9),
  'a supporter may wear the halo without buying it'
);

select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.recipient_id = '99400000-0000-4000-8000-000000000001'
      and notification.kind = 'system'
      and notification.title = 'Thanks for supporting PocketPass!'
  ),
  1::bigint,
  'the supporter is thanked once even when Ko-fi retries'
);

select extensions.is(
  (select count(*) from private.kofi_events as event where event.payload ? 'verification_token'),
  0::bigint,
  'stored payloads never contain the verification token'
);

select extensions.is(
  (select count(*) from private.kofi_events as event where event.payload ? 'email'),
  0::bigint,
  'raw Ko-fi payloads do not duplicate payer emails'
);

select extensions.is(
  (select event.email from private.kofi_events as event where event.message_id = '99400000-0000-4000-8000-00000000a001'),
  'kofi-fan@pocketpass.test',
  'the restricted normalized payer email remains available for matching'
);

select extensions.ok(
  (
    select position(event.email in private.kofi_discord_notice(event, now())) = 0
    from private.kofi_events as event
    where event.message_id = '99400000-0000-4000-8000-00000000a001'
  ),
  'Discord supporter alerts omit payer emails'
);

update private.kofi_events
set payload = payload || '{"email":"second-copy@pocketpass.test"}'::jsonb
where message_id = '99400000-0000-4000-8000-00000000a001';

select extensions.is(
  (select count(*) from private.kofi_events as event where event.payload ? 'email'),
  0::bigint,
  'the payload guard also strips later duplicate-email updates'
);

select extensions.is(
  (
    select event.matched_by || ':' || event.qualifies::text || ':' || event.amount::text || ':' || coalesce(event.tier_name, '-')
    from private.kofi_events as event
    where event.message_id = '99400000-0000-4000-8000-00000000a001'
  ),
  'email:true:3.00:Bronze',
  'the membership event is normalised and matched by email'
);

select extensions.is(
  (
    select event.qualifies::text || ':' || coalesce(event.applied_at::text, 'null')
    from private.kofi_events as event
    where event.message_id = '99400000-0000-4000-8000-00000000a002'
  ),
  'false:null',
  'the donation event does not qualify'
);

select extensions.cmp_ok(
  (
    select count(*)
    from realtime.messages as message
    where message.topic = 'tokens:99400000-0000-4000-8000-000000000001'
      and message.event = 'supporter_status'
  ),
  '>=',
  1::bigint,
  'the grant is broadcast on the owner''s tokens topic'
);

set local role anon;
select pg_catalog.set_config('request.jwt.claim.role', 'anon', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select extensions.is(
  (
    select public.kofi_webhook(
      pg_temp.kofi_payload(
        '99400000-0000-4000-8000-00000000a006',
        'Subscription',
        'kofi-fan@pocketpass.test',
        now()
      )
    ) ->> 'applied'
  ),
  'true',
  'a later membership payment is applied'
);

reset role;

select extensions.is(
  (
    select status.active_until
    from public.supporter_status as status
    where status.user_id = '99400000-0000-4000-8000-000000000001'
  ),
  (
    select event.paid_at + interval '36 days'
    from private.kofi_events as event
    where event.message_id = '99400000-0000-4000-8000-00000000a006'
  ),
  'a later payment extends the supporter window'
);

select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.recipient_id = '99400000-0000-4000-8000-000000000001'
      and notification.title = 'Thanks for supporting PocketPass!'
  ),
  2::bigint,
  'an extension thanks the supporter again'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000001', true);

select extensions.lives_ok(
  $$
    select * from public.save_profile_mii_slot(
      '99400000-0000-4000-8000-000000000021',
      1,
      1,
      pg_temp.hat_appearance(9),
      '99400000-0000-4000-8000-000000000001/mii-r1-99400000-0000-4000-8000-000000000021.png',
      null,
      1
    );
  $$,
  'a supporter can save a Mii wearing an unbought hat'
);

select extensions.is(
  (select count(*) from public.supporter_status),
  1::bigint,
  'a user sees only their own supporter status'
);

select extensions.throws_ok(
  $$select public.admin_list_kofi_events()$$,
  '42501',
  'Admin access required',
  'a plain user cannot list Ko-fi payments'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000002', true);

select extensions.is(
  (select count(*) from public.supporter_status),
  0::bigint,
  'a user without supporter status sees no rows'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000006', true);

select extensions.throws_ok(
  $$select public.admin_list_kofi_events()$$,
  '42501',
  'Permission required: supporters',
  'an admin without the supporters permission cannot list Ko-fi payments'
);

select extensions.throws_ok(
  $$select public.admin_set_supporter('99400000-0000-4000-8000-000000000001', now() + interval '1 day', 'pgtap')$$,
  '42501',
  'Permission required: supporters',
  'an admin without the supporters permission cannot grant status'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000005', true);

select extensions.is(
  (select public.admin_list_kofi_events('unmatched') ->> 'total'),
  '3',
  'three deliveries are waiting to be linked'
);

select extensions.is(
  (select public.admin_list_kofi_events('all') ->> 'total'),
  '6',
  'all deliveries are listed on request'
);

select extensions.is(
  (
    select public.admin_link_kofi_email(' Stranger@Example.com ', '99400000-0000-4000-8000-000000000002')
      - 'active_until'
  ),
  '{"linked": true, "email": "stranger@example.com", "user_id": "99400000-0000-4000-8000-000000000002", "applied_events": 1}'::jsonb,
  'linking a Ko-fi email applies its pending membership payment'
);

select extensions.throws_ok(
  $$select public.admin_link_kofi_email('nobody@example.com', '99400000-0000-4000-8000-0000000000ff')$$,
  'P0002',
  'User not found',
  'linking to an unknown user is refused'
);

select extensions.throws_ok(
  $$select public.admin_link_kofi_email('not an email', '99400000-0000-4000-8000-000000000002')$$,
  '22023',
  'A Ko-fi email address is required',
  'linking a malformed email is refused'
);

select extensions.is(
  (select public.admin_list_supporters() ->> 'total'),
  '2',
  'both supporters are listed'
);

select extensions.is(
  (select public.admin_get_user('99400000-0000-4000-8000-000000000002') -> 'kofi_emails'),
  '["stranger@example.com"]'::jsonb,
  'user detail shows the linked Ko-fi email'
);

select extensions.ok(
  (select (public.admin_get_user('99400000-0000-4000-8000-000000000002') ->> 'supporter_until')::timestamptz > now()),
  'user detail shows the supporter window'
);

select extensions.ok(
  (select (public.admin_set_supporter('99400000-0000-4000-8000-000000000002', null, 'pgtap revoke') ->> 'active_until')::timestamptz <= now()),
  'revoking sets the window to now'
);

select extensions.is(
  (select (public.admin_set_supporter('99400000-0000-4000-8000-000000000001', now() - interval '1 day', 'pgtap lapse') ->> 'active_until')::timestamptz),
  now() - interval '1 day',
  'an admin can set an explicit window'
);

select extensions.throws_ok(
  $$select public.admin_set_supporter('99400000-0000-4000-8000-000000000001', now() + interval '1 day', 'no')$$,
  '22023',
  'Reason must be 3 to 500 characters',
  'a supporter change needs a reason'
);

reset role;

select extensions.is(
  (
    select count(*)
    from private.admin_audit as audit
    where audit.action in ('link_kofi_email', 'set_supporter')
      and audit.admin_id = '99400000-0000-4000-8000-000000000005'
  ),
  3::bigint,
  'supporter changes are audited'
);

select extensions.is(
  (select count(*) from private.admin_audit as audit where audit.payload ? 'email'),
  0::bigint,
  'supporter audit entries keep actor and target IDs without copying emails'
);

select extensions.ok(
  not private.is_supporter('99400000-0000-4000-8000-000000000002'),
  'a revoked supporter is no longer a supporter'
);

select extensions.is(
  (
    select count(*)
    from public.supporter_status as status
    where status.user_id = '99400000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'revoking keeps the status row'
);

select extensions.is(
  (
    select event.matched_by || ':' || (event.applied_at is not null)::text
    from private.kofi_events as event
    where event.message_id = '99400000-0000-4000-8000-00000000a003'
  ),
  'link:true',
  'the linked event is marked as applied through the link'
);

set local role anon;
select pg_catalog.set_config('request.jwt.claim.role', 'anon', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select extensions.is(
  (
    select public.kofi_webhook(
      pg_temp.kofi_payload(
        '99400000-0000-4000-8000-00000000a007',
        'Subscription',
        'STRANGER@example.com',
        now()
      )
    ) - 'duplicate'
  ),
  '{"received": true, "matched": true, "applied": true}'::jsonb,
  'a later payment from a linked email is applied'
);

reset role;

select extensions.is(
  (
    select event.matched_by
    from private.kofi_events as event
    where event.message_id = '99400000-0000-4000-8000-00000000a007'
  ),
  'link',
  'the link takes precedence for later payments'
);

select extensions.ok(
  private.is_supporter('99400000-0000-4000-8000-000000000002'),
  'the linked user is a supporter again'
);

select extensions.is(
  private.notify_lapsed_supporters(),
  1,
  'one lapsed supporter is notified'
);

select extensions.is(
  private.notify_lapsed_supporters(),
  0,
  'the lapse notice is sent once'
);

select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.recipient_id = '99400000-0000-4000-8000-000000000001'
      and notification.title = 'Supporter perks ended'
  ),
  1::bigint,
  'the lapsed supporter gets a system notification'
);

select extensions.ok(
  not private.is_supporter('99400000-0000-4000-8000-000000000001'),
  'a lapsed supporter is no longer a supporter'
);

select extensions.ok(
  not private.owns_mii_hat('99400000-0000-4000-8000-000000000001', 9),
  'the halo locks again after the lapse'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  $$
    select * from public.save_profile_mii_slot(
      '99400000-0000-4000-8000-000000000022',
      2,
      1,
      pg_temp.hat_appearance(9),
      '99400000-0000-4000-8000-000000000001/mii-r2-99400000-0000-4000-8000-000000000022.png',
      null,
      1
    );
  $$,
  'PT403',
  'That hat has not been purchased',
  'a lapsed supporter cannot save a Mii wearing an unbought hat'
);

reset role;

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
values (
  '00000000-0000-0000-0000-000000000000',
  '99400000-0000-4000-8000-000000000004',
  'authenticated',
  'authenticated',
  'Late@pocketpass.test',
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  '{"display_name":"Kofi Late pgtap"}',
  now(),
  now(),
  '',
  '',
  '',
  ''
);

select extensions.ok(
  private.is_supporter('99400000-0000-4000-8000-000000000004'),
  'signing up with an email that already paid unlocks immediately'
);

select extensions.is(
  (
    select event.matched_by
    from private.kofi_events as event
    where event.message_id = '99400000-0000-4000-8000-00000000a005'
  ),
  'signup',
  'the pending payment is matched at signup'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99400000-0000-4000-8000-000000000005', true);

select extensions.is(
  (select public.admin_unlink_kofi_email('stranger@example.com')),
  '{"unlinked": true, "email": "stranger@example.com"}'::jsonb,
  'an admin can unlink a Ko-fi email'
);

select extensions.is(
  (select public.admin_unlink_kofi_email('stranger@example.com') ->> 'unlinked'),
  'false',
  'unlinking twice is a no-op'
);

reset role;

select extensions.is(
  (select count(*) from private.kofi_links),
  0::bigint,
  'the link is gone'
);

select extensions.ok(
  private.is_supporter('99400000-0000-4000-8000-000000000002'),
  'unlinking does not revoke granted time'
);

delete from public.profiles where user_id = '99400000-0000-4000-8000-000000000004';

select extensions.is(
  (
    select count(*)
    from public.supporter_status as status
    where status.user_id = '99400000-0000-4000-8000-000000000004'
  ),
  0::bigint,
  'deleting a profile removes its supporter status'
);

select extensions.is(
  (
    select event.user_id::text
    from private.kofi_events as event
    where event.message_id = '99400000-0000-4000-8000-00000000a005'
  ),
  null,
  'deleting a profile detaches its Ko-fi events'
);

delete from vault.secrets where name = 'kofi_verification_token';

set local role anon;
select pg_catalog.set_config('request.jwt.claim.role', 'anon', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

select extensions.throws_ok(
  format(
    $$select public.kofi_webhook(%L)$$,
    pg_temp.kofi_payload(
      '99400000-0000-4000-8000-00000000a008',
      'Subscription',
      'kofi-fan@pocketpass.test',
      now()
    )
  ),
  'PT500',
  'Ko-fi webhook is not configured',
  'the webhook fails closed without a verification token'
);

reset role;

select * from extensions.finish();

rollback;
