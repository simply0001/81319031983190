begin;

set local search_path = public, extensions;

select extensions.plan(29);

select extensions.has_table('public', 'friend_codes', 'friend codes table exists');
select extensions.has_table('public', 'notifications', 'notifications table exists');
select extensions.ok(
  (select relrowsecurity from pg_catalog.pg_class
   where oid = 'public.friend_codes'::regclass),
  'friend codes enforce RLS'
);
select extensions.ok(
  (select relrowsecurity from pg_catalog.pg_class
   where oid = 'public.notifications'::regclass),
  'notifications enforce RLS'
);
select extensions.ok(
  not pg_catalog.has_table_privilege(
    'authenticated',
    'public.friend_codes',
    'insert'
  ),
  'friend codes cannot be inserted directly by clients'
);
select extensions.ok(
  not pg_catalog.has_table_privilege(
    'authenticated',
    'public.notifications',
    'insert'
  ),
  'notifications cannot be inserted directly by clients'
);

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
values
  (
    '00000000-0000-0000-0000-000000000000',
    '95000000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'friend-code-a@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Friend Code A"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '95000000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'friend-code-b@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Friend Code B"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '95000000-0000-4000-8000-000000000003',
    'authenticated',
    'authenticated',
    'friend-code-c@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Friend Code C"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

create temporary table friend_code_test_values as
select user_id, code from public.friend_codes
where user_id in (
  '95000000-0000-4000-8000-000000000001',
  '95000000-0000-4000-8000-000000000002',
  '95000000-0000-4000-8000-000000000003'
);
grant select on friend_code_test_values to authenticated;

select extensions.is(
  (select count(*) from friend_code_test_values),
  3::bigint,
  'profile creation backfills one permanent friend code per user'
);
select extensions.is(
  (
    select count(*)
    from friend_code_test_values
    where code ~ '^[0-9]{8}$'
  ),
  3::bigint,
  'every generated code has exactly eight numeric characters'
);
select extensions.is(
  (select count(distinct code) from friend_code_test_values),
  3::bigint,
  'generated friend codes are unique'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '95000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (select count(*) from public.get_my_friend_code()),
  1::bigint,
  'an authenticated user can retrieve their own friend code'
);
select extensions.is(
  (
    select count(*)
    from public.resolve_friend_code(
      (
        select code from friend_code_test_values
        where user_id = '95000000-0000-4000-8000-000000000002'
      )
    )
    where user_id = '95000000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'a valid code resolves to its public profile'
);
select extensions.is(
  (
    select count(*)
    from public.resolve_friend_code(
      (
        select code from friend_code_test_values
        where user_id = '95000000-0000-4000-8000-000000000001'
      )
    )
  ),
  0::bigint,
  'a caller cannot resolve their own code'
);
select public.set_user_block(
  '95000000-0000-4000-8000-000000000002',
  true,
  '95100000-0000-4000-8000-000000000001'
);
select extensions.is(
  (
    select count(*)
    from public.resolve_friend_code(
      (
        select code from friend_code_test_values
        where user_id = '95000000-0000-4000-8000-000000000002'
      )
    )
  ),
  0::bigint,
  'blocked and unavailable codes return the same empty result'
);
select public.set_user_block(
  '95000000-0000-4000-8000-000000000002',
  false,
  '95100000-0000-4000-8000-000000000002'
);

select extensions.is(
  (
    public.send_friend_request(
      '95000000-0000-4000-8000-000000000002',
      '95200000-0000-4000-8000-000000000001'
    )
  ).status::text,
  'pending',
  'sending by resolved user id creates a pending request'
);
select extensions.is(
  (select count(*) from public.notifications),
  0::bigint,
  'the requester cannot read the recipient notification'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '95000000-0000-4000-8000-000000000002',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.notifications
    where kind = 'friend_request'
      and friend_request_status = 'pending'
  ),
  1::bigint,
  'the recipient receives one incoming friend-request notification'
);
select extensions.throws_ok(
  $test$
    select public.delete_notification(
      (
        select id from public.notifications
        where kind = 'friend_request'
        limit 1
      )
    )
  $test$,
  'PT409',
  'respond_to_friend_request_before_delete',
  'an unresolved friend request notification cannot be deleted'
);
select extensions.is(
  (
    public.respond_to_friend_request(
      (
        select id from public.friend_requests
        where requester_id = '95000000-0000-4000-8000-000000000001'
          and addressee_id = '95000000-0000-4000-8000-000000000002'
      ),
      true,
      '95200000-0000-4000-8000-000000000002'
    )
  ).status::text,
  'accepted',
  'the addressee can accept the request'
);
select extensions.is(
  (
    select friend_request_status
    from public.notifications
    where kind = 'friend_request'
  ),
  'accepted',
  'acceptance updates the original inbox row'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '95000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.notifications
    where kind = 'friend_accepted'
      and actor_id = '95000000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'acceptance notifies the original requester'
);

select public.get_or_create_direct_conversation(
  '95000000-0000-4000-8000-000000000002',
  '95300000-0000-4000-8000-000000000001'
);
select public.send_message(
  '95400000-0000-4000-8000-000000000001',
  (
    select id from public.conversations
    where direct_user_low = least(
      '95000000-0000-4000-8000-000000000001'::uuid,
      '95000000-0000-4000-8000-000000000002'::uuid
    )
      and direct_user_high = greatest(
        '95000000-0000-4000-8000-000000000001'::uuid,
        '95000000-0000-4000-8000-000000000002'::uuid
      )
  ),
  '95400000-0000-4000-8000-000000000011',
  'First notification preview'
);
select public.send_message(
  '95400000-0000-4000-8000-000000000002',
  (
    select id from public.conversations
    where direct_user_low = least(
      '95000000-0000-4000-8000-000000000001'::uuid,
      '95000000-0000-4000-8000-000000000002'::uuid
    )
      and direct_user_high = greatest(
        '95000000-0000-4000-8000-000000000001'::uuid,
        '95000000-0000-4000-8000-000000000002'::uuid
      )
  ),
  '95400000-0000-4000-8000-000000000012',
  'Latest notification preview'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '95000000-0000-4000-8000-000000000002',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.notifications
    where kind = 'message'
  ),
  1::bigint,
  'message notifications group into one row per conversation'
);
select extensions.is(
  (
    select event_count
    from public.notifications
    where kind = 'message'
  ),
  2,
  'the grouped message row tracks its unread event count'
);
select extensions.is(
  (
    select body
    from public.notifications
    where kind = 'message'
  ),
  'Latest notification preview',
  'the grouped message row keeps the latest preview'
);
select public.mark_notification_read(
  (
    select id
    from public.notifications
    where kind = 'message'
  )
);
select extensions.ok(
  (
    select read_at is not null
    from public.notifications
    where kind = 'message'
  ),
  'mark-read updates only the recipient row'
);
select extensions.throws_ok(
  $$select public.publish_system_notification('Not allowed', 'client call')$$,
  '42501',
  'Service role required',
  'authenticated clients cannot author system notifications'
);
select extensions.ok(
  private.can_access_realtime_topic(
    'notifications:95000000-0000-4000-8000-000000000002',
    '95000000-0000-4000-8000-000000000002'
  ),
  'a user may subscribe to their private notification topic'
);
select extensions.ok(
  not private.can_access_realtime_topic(
    'notifications:95000000-0000-4000-8000-000000000001',
    '95000000-0000-4000-8000-000000000002'
  ),
  'a user cannot subscribe to another notification topic'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '95000000-0000-4000-8000-000000000003',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select extensions.is(
  (select count(*) from public.notifications),
  0::bigint,
  'notification RLS rejects cross-account inbox reads'
);

reset role;
delete from private.friend_code_lookup_attempts
where actor_id = '95000000-0000-4000-8000-000000000001';
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '95000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
do $rate_limit$
declare
  v_attempt integer;
begin
  for v_attempt in 1..50 loop
    perform public.resolve_friend_code('99999999');
  end loop;
end;
$rate_limit$;
select extensions.throws_ok(
  $$select public.resolve_friend_code('99999999')$$,
  'PT429',
  'friend_code_rate_limited',
  'friend-code lookup enforces fifty attempts per account per hour'
);

select * from extensions.finish();

rollback;
