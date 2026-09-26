begin;

set local search_path = public, extensions;

select extensions.plan(14);

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
    '97000000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'friends-realtime-a@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Realtime A"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '97000000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'friends-realtime-b@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Realtime B"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '97000000-0000-4000-8000-000000000003',
    'authenticated',
    'authenticated',
    'friends-realtime-c@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Realtime C"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

insert into public.friendships (user_low, user_high, created_by)
values (
  '97000000-0000-4000-8000-000000000001',
  '97000000-0000-4000-8000-000000000002',
  '97000000-0000-4000-8000-000000000001'
);

select extensions.ok(
  private.can_access_realtime_topic(
    'friends:97000000-0000-4000-8000-000000000001',
    '97000000-0000-4000-8000-000000000001'
  ),
  'a user may read their own friends invalidation topic'
);
select extensions.ok(
  not private.can_access_realtime_topic(
    'friends:97000000-0000-4000-8000-000000000001',
    '97000000-0000-4000-8000-000000000002'
  ),
  'a friend may not read another user invalidation topic'
);
select extensions.ok(
  not private.can_access_realtime_topic(
    'friends:97000000-0000-4000-8000-000000000001:spoofed',
    '97000000-0000-4000-8000-000000000001'
  ),
  'friends topics reject suffix spoofing'
);
select extensions.ok(
  private.can_access_realtime_topic(
    'friend-presence:97000000-0000-4000-8000-000000000001:97000000-0000-4000-8000-000000000002',
    '97000000-0000-4000-8000-000000000001'
  ),
  'the low accepted friend may join pair presence'
);
select extensions.ok(
  private.can_access_realtime_topic(
    'friend-presence:97000000-0000-4000-8000-000000000001:97000000-0000-4000-8000-000000000002',
    '97000000-0000-4000-8000-000000000002'
  ),
  'the high accepted friend may join pair presence'
);
select extensions.ok(
  not private.can_access_realtime_topic(
    'friend-presence:97000000-0000-4000-8000-000000000001:97000000-0000-4000-8000-000000000002',
    '97000000-0000-4000-8000-000000000003'
  ),
  'an unrelated user may not join pair presence'
);
select extensions.ok(
  not private.can_access_realtime_topic(
    'friend-presence:97000000-0000-4000-8000-000000000002:97000000-0000-4000-8000-000000000001',
    '97000000-0000-4000-8000-000000000001'
  ),
  'a noncanonical reversed pair topic is rejected'
);
select extensions.ok(
  not private.can_access_realtime_topic(
    'friend-presence:97000000-0000-4000-8000-000000000001:97000000-0000-4000-8000-000000000003',
    '97000000-0000-4000-8000-000000000001'
  ),
  'pending or unrelated pairs cannot join presence'
);
select extensions.has_trigger(
  'public',
  'friendships',
  'friendships_broadcast_change',
  'friendship changes broadcast invalidations'
);
select extensions.has_trigger(
  'public',
  'friend_requests',
  'friend_requests_broadcast_change',
  'friend-request changes broadcast invalidations'
);

delete from public.friendships
where user_low = '97000000-0000-4000-8000-000000000001'
  and user_high = '97000000-0000-4000-8000-000000000002';

select extensions.ok(
  not private.can_access_realtime_topic(
    'friend-presence:97000000-0000-4000-8000-000000000001:97000000-0000-4000-8000-000000000002',
    '97000000-0000-4000-8000-000000000001'
  ),
  'removed friends immediately lose pair presence access'
);

insert into public.friend_requests (
  requester_id,
  addressee_id,
  status,
  client_operation_id
)
values (
  '97000000-0000-4000-8000-000000000001',
  '97000000-0000-4000-8000-000000000002',
  'pending',
  '97000000-0000-4000-8000-000000000011'
);

select extensions.ok(
  not private.can_access_realtime_topic(
    'friend-presence:97000000-0000-4000-8000-000000000001:97000000-0000-4000-8000-000000000002',
    '97000000-0000-4000-8000-000000000001'
  ),
  'pending requests do not grant pair presence access'
);

insert into public.user_blocks (blocker_id, blocked_id)
values (
  '97000000-0000-4000-8000-000000000001',
  '97000000-0000-4000-8000-000000000002'
);

select extensions.ok(
  not private.can_access_realtime_topic(
    'friend-presence:97000000-0000-4000-8000-000000000001:97000000-0000-4000-8000-000000000002',
    '97000000-0000-4000-8000-000000000001'
  ),
  'blocked users do not gain pair presence access'
);
select extensions.ok(
  not private.can_access_realtime_topic(
    'friend-presence:97000000-0000-4000-8000-000000000001:97000000-0000-4000-8000-000000000002:extra',
    '97000000-0000-4000-8000-000000000001'
  ),
  'pair presence rejects suffix spoofing'
);

select * from extensions.finish();

rollback;
