begin;

set local search_path = public, extensions;

select extensions.plan(39);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_class
    where relnamespace = 'public'::regnamespace
      and relname = any (array[
        'profiles',
        'friend_requests',
        'friendships',
        'user_blocks',
        'conversations',
        'conversation_members',
        'messages',
        'interaction_events'
      ])
      and relkind = 'r'
  ),
  8::bigint,
  'all PocketPass tables exist'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_class
    where relnamespace = 'public'::regnamespace
      and relname = any (array[
        'profiles',
        'friend_requests',
        'friendships',
        'user_blocks',
        'conversations',
        'conversation_members',
        'messages',
        'interaction_events'
      ])
      and relrowsecurity
  ),
  8::bigint,
  'RLS is enabled on every PocketPass table'
);

select extensions.is(
  (
    select public
    from storage.buckets
    where id = 'avatars'
  ),
  false,
  'avatars bucket is private'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and policyname like 'pocketpass_avatars_%'
  ),
  4::bigint,
  'avatars has read, insert, update, and delete policies'
);

select extensions.is(
  (
    select count(*)
    from pg_catalog.pg_policies
    where schemaname = 'realtime'
      and tablename = 'messages'
      and policyname like 'pocketpass_conversation_%'
  ),
  2::bigint,
  'private conversation Broadcast and Presence authorization exists'
);

select extensions.ok(
  not pg_catalog.has_table_privilege('anon', 'public.profiles', 'select'),
  'anonymous users cannot read profiles'
);

select extensions.ok(
  pg_catalog.has_table_privilege('authenticated', 'public.profiles', 'select'),
  'authenticated users receive profile select privilege'
);

select extensions.ok(
  not exists (
    select 1 from pg_catalog.pg_attribute
    where attrelid = 'public.profiles'::regclass
      and attname = 'email' and attnum > 0 and not attisdropped
  ),
  'ordinary profiles have no email column to expose'
);

select extensions.ok(
  not pg_catalog.has_table_privilege('anon', 'auth.users', 'select')
    and not pg_catalog.has_table_privilege('authenticated', 'auth.users', 'select')
    and not pg_catalog.has_table_privilege('api_client', 'auth.users', 'select'),
  'anonymous, ordinary and connected-app roles cannot read Auth emails'
);

select extensions.ok(
  not pg_catalog.has_table_privilege(
    'authenticated',
    'public.friend_requests',
    'insert'
  ),
  'friend requests cannot bypass their RPC'
);

select extensions.ok(
  not pg_catalog.has_table_privilege(
    'authenticated',
    'public.messages',
    'insert'
  ),
  'messages cannot bypass their RPC'
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
    '90000000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'security-a@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Security A"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '90000000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'security-b@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Security B"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '90000000-0000-4000-8000-000000000003',
    'authenticated',
    'authenticated',
    'security-c@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Security C"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '90000000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    select count(*)
    from public.profiles
    where user_id in (
      '90000000-0000-4000-8000-000000000001',
      '90000000-0000-4000-8000-000000000002',
      '90000000-0000-4000-8000-000000000003'
    )
  ),
  3::bigint,
  'an authenticated user initially sees the three unblocked test profiles'
);

update public.profiles
set age = 43, country_code = 'US'
where user_id = '90000000-0000-4000-8000-000000000001';

select extensions.ok(
  (
    select age = 43 and country_code = 'US'
    from public.profiles
    where user_id = '90000000-0000-4000-8000-000000000001'
  ),
  'a user can update their public display age and country'
);

update public.profiles
set age = 44
where user_id = '90000000-0000-4000-8000-000000000003';

select extensions.ok(
  (
    select age is null
    from public.profiles
    where user_id = '90000000-0000-4000-8000-000000000003'
  ),
  'profile RLS prevents updating another user'
);

update public.profiles
set username = 'setup.tester', display_name = 'setup.tester'
where user_id = '90000000-0000-4000-8000-000000000001';

select extensions.ok(
  (
    select username::text = 'setup.tester' and display_name = 'setup.tester'
    from public.profiles
    where user_id = '90000000-0000-4000-8000-000000000001'
  ),
  'a user can claim a username and matching display name during setup'
);

select extensions.throws_ok(
  $$
    update public.profiles
    set username = 'Ab'
    where user_id = '90000000-0000-4000-8000-000000000001'
  $$,
  '23514',
  null,
  'the username format check rejects short or uppercase names'
);

select extensions.throws_ok(
  $$
    update public.profiles
    set username = replace('90000000-0000-4000-8000-000000000002', '-', '')
    where user_id = '90000000-0000-4000-8000-000000000001'
  $$,
  '23505',
  null,
  'usernames are unique across accounts'
);

update public.profiles
set username = 'stolen.name1'
where user_id = '90000000-0000-4000-8000-000000000003';

select extensions.ok(
  (
    select username::text <> 'stolen.name1'
    from public.profiles
    where user_id = '90000000-0000-4000-8000-000000000003'
  ),
  'profile RLS prevents claiming a username for another user'
);

select extensions.is(
  (
    public.send_friend_request(
      '90000000-0000-4000-8000-000000000003',
      '91000000-0000-4000-8000-000000000001'
    )
  ).id,
  (
    public.send_friend_request(
      '90000000-0000-4000-8000-000000000003',
      '91000000-0000-4000-8000-000000000001'
    )
  ).id,
  'friend request retries are idempotent'
);

select extensions.throws_ok(
  $$
    select public.send_friend_request(
      '90000000-0000-4000-8000-000000000002',
      '91000000-0000-4000-8000-000000000001'
    )
  $$,
  '22023',
  'Client operation id was already used for another request',
  'a friend request operation id cannot be reused with different input'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '90000000-0000-4000-8000-000000000003',
  true
);

select extensions.is(
  (select count(*) from public.friend_requests where status = 'pending'),
  1::bigint,
  'the friend request addressee can read the pending request'
);

select extensions.is(
  (
    public.respond_to_friend_request(
      (
        select id
        from public.friend_requests
        where requester_id = '90000000-0000-4000-8000-000000000001'
          and status = 'pending'
      ),
      true,
      '91000000-0000-4000-8000-000000000002'
    )
  ).status::text,
  'accepted',
  'the addressee can atomically accept the request'
);

select extensions.is(
  (
    public.respond_to_friend_request(
      (
        select id
        from public.friend_requests
        where requester_id = '90000000-0000-4000-8000-000000000001'
      ),
      true,
      '91000000-0000-4000-8000-000000000002'
    )
  ).status::text,
  'accepted',
  'repeating the same friend response is idempotent'
);

select extensions.is(
  (select count(*) from public.friendships),
  1::bigint,
  'accepting creates one canonical friendship'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '90000000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  public.get_or_create_direct_conversation(
    '90000000-0000-4000-8000-000000000003',
    '91000000-0000-4000-8000-000000000003'
  ),
  public.get_or_create_direct_conversation(
    '90000000-0000-4000-8000-000000000003',
    '91000000-0000-4000-8000-000000000003'
  ),
  'direct conversation creation is idempotent'
);

select extensions.is(
  (
    public.send_message(
      '92000000-0000-4000-8000-000000000002',
      (
        select id
        from public.conversations
        where direct_user_low = least(
          '90000000-0000-4000-8000-000000000001'::uuid,
          '90000000-0000-4000-8000-000000000003'::uuid
        )
          and direct_user_high = greatest(
            '90000000-0000-4000-8000-000000000001'::uuid,
            '90000000-0000-4000-8000-000000000003'::uuid
          )
      ),
      '92000000-0000-4000-8000-000000000001',
      'Idempotent hello'
    )
  ).id,
  (
    public.send_message(
      '92000000-0000-4000-8000-000000000002',
      (
        select id
        from public.conversations
        where direct_user_low = least(
          '90000000-0000-4000-8000-000000000001'::uuid,
          '90000000-0000-4000-8000-000000000003'::uuid
        )
          and direct_user_high = greatest(
            '90000000-0000-4000-8000-000000000001'::uuid,
            '90000000-0000-4000-8000-000000000003'::uuid
          )
      ),
      '92000000-0000-4000-8000-000000000001',
      'Idempotent hello'
    )
  ).id,
  'message retries create one durable message'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '90000000-0000-4000-8000-000000000002',
  true
);

select extensions.is(
  (select count(*) from public.conversations),
  0::bigint,
  'a non-member cannot read a private conversation'
);

select extensions.is(
  (select count(*) from public.messages),
  0::bigint,
  'a non-member cannot read private messages'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '90000000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    public.record_interaction_event(
      '93000000-0000-4000-8000-000000000002',
      '90000000-0000-4000-8000-000000000002',
      'nearby_encounter',
      '93000000-0000-4000-8000-000000000001',
      '{"source":"pgtap"}'::jsonb
    )
  ).id,
  (
    public.record_interaction_event(
      '93000000-0000-4000-8000-000000000002',
      '90000000-0000-4000-8000-000000000002',
      'nearby_encounter',
      '93000000-0000-4000-8000-000000000001',
      '{"source":"pgtap"}'::jsonb
    )
  ).id,
  'interaction event retries are idempotent'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '90000000-0000-4000-8000-000000000002',
  true
);

select extensions.is(
  (select count(*) from public.interaction_events),
  1::bigint,
  'an interaction subject can read the event'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '90000000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  public.set_user_block(
    '90000000-0000-4000-8000-000000000003',
    true,
    '94000000-0000-4000-8000-000000000001'
  ),
  true,
  'blocking reports the resulting blocked state'
);

select extensions.is(
  (select count(*) from public.user_blocks),
  1::bigint,
  'the blocker can read their own block row'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '90000000-0000-4000-8000-000000000003',
  true
);

select extensions.is(
  (select count(*) from public.user_blocks),
  0::bigint,
  'the blocked user cannot discover the block row'
);

select extensions.is(
  (select count(*) from public.friendships),
  0::bigint,
  'blocking atomically removes the friendship'
);

select extensions.is(
  (
    select count(*)
    from public.profiles
    where user_id = '90000000-0000-4000-8000-000000000001'
  ),
  0::bigint,
  'blocked profiles are hidden in both directions'
);

select extensions.throws_ok(
  $$
    select public.send_friend_request(
      '90000000-0000-4000-8000-000000000001',
      '91000000-0000-4000-8000-000000000002'
    )
  $$,
  '42501',
  'Friend request is not allowed',
  'a block prevents a new friend request'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '90000000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  public.set_user_block(
    '90000000-0000-4000-8000-000000000003',
    false,
    '94000000-0000-4000-8000-000000000002'
  ),
  false,
  'unblocking is idempotent and reports the final state'
);

reset role;

select extensions.is(
  private.avatar_owner_id(
    '90000000-0000-4000-8000-000000000001/avatar.webp'
  ),
  '90000000-0000-4000-8000-000000000001'::uuid,
  'avatar owner is derived from a valid owned path'
);

select extensions.is(
  private.avatar_owner_id('../not-a-user/avatar.webp'),
  null::uuid,
  'malformed avatar paths do not produce an owner'
);

select * from extensions.finish();

rollback;
