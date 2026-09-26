begin;

set local search_path = public, extensions;

select extensions.plan(16);

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
    '98600000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'ada@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Ada"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98600000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'ben@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Ben"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98600000-0000-4000-8000-000000000003',
    'authenticated',
    'authenticated',
    'cleo@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Cleo"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98600000-0000-4000-8000-000000000004',
    'authenticated',
    'authenticated',
    'dane@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Dane"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98600000-0000-4000-8000-000000000005',
    'authenticated',
    'authenticated',
    'eve@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Eve"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

update public.profiles as profile
set username = names.username
from (
  values
    ('98600000-0000-4000-8000-000000000001'::uuid, 'ada'),
    ('98600000-0000-4000-8000-000000000002'::uuid, 'ben'),
    ('98600000-0000-4000-8000-000000000003'::uuid, 'cleo'),
    ('98600000-0000-4000-8000-000000000004'::uuid, 'dane'),
    ('98600000-0000-4000-8000-000000000005'::uuid, 'eve')
) as names(user_id, username)
where profile.user_id = names.user_id;

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
  '98600000-0000-4000-8000-000000000006',
  'authenticated',
  'authenticated',
  'finn@pocketpass.test',
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  '{}',
  now(),
  now(),
  '',
  '',
  '',
  ''
);

select extensions.throws_ok(
  $$select * from public.get_leaderboard()$$,
  '42501',
  'Authentication required',
  'the leaderboard rejects an unauthenticated caller'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98600000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (select count(*) from public.get_leaderboard()),
  1::bigint,
  'a player with no friends still sees their own row'
);

reset role;

insert into public.friendships (user_low, user_high, created_by)
values
  (
    '98600000-0000-4000-8000-000000000001',
    '98600000-0000-4000-8000-000000000002',
    '98600000-0000-4000-8000-000000000001'
  ),
  (
    '98600000-0000-4000-8000-000000000001',
    '98600000-0000-4000-8000-000000000004',
    '98600000-0000-4000-8000-000000000001'
  );

insert into public.nearby_encounters (
  id,
  user_low,
  user_high,
  reported_by,
  reporter_operation_id,
  occurred_at,
  confirmed_at
)
values
  (
    '98610000-0000-4000-8000-000000000001',
    '98600000-0000-4000-8000-000000000001',
    '98600000-0000-4000-8000-000000000002',
    '98600000-0000-4000-8000-000000000001',
    '98620000-0000-4000-8000-000000000001',
    now() - interval '2 days',
    now() - interval '2 days'
  ),
  (
    '98610000-0000-4000-8000-000000000002',
    '98600000-0000-4000-8000-000000000001',
    '98600000-0000-4000-8000-000000000002',
    '98600000-0000-4000-8000-000000000001',
    '98620000-0000-4000-8000-000000000002',
    now() - interval '1 day',
    now() - interval '1 day'
  ),
  (
    '98610000-0000-4000-8000-000000000003',
    '98600000-0000-4000-8000-000000000002',
    '98600000-0000-4000-8000-000000000005',
    '98600000-0000-4000-8000-000000000002',
    '98620000-0000-4000-8000-000000000003',
    now(),
    now()
  );

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98600000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    select count(*)
    from public.get_leaderboard() as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'an accepted friend appears on the leaderboard'
);

select extensions.is(
  (
    select count(*)
    from public.get_leaderboard() as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000003'
  ),
  0::bigint,
  'somebody who is not a friend stays off the leaderboard'
);

select extensions.is(
  (
    select entry.trophy_count
    from public.get_leaderboard() as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000001'
  ),
  2::bigint,
  'trophy_count counts unlocked achievements: plus_one and first_encounter here'
);

select extensions.is(
  (
    select entry.encounter_count
    from public.get_leaderboard() as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000001'
  ),
  2::bigint,
  'encounter_count counts every encounter, including repeats'
);

select extensions.is(
  (
    select entry.user_id
    from public.get_leaderboard() as entry
    limit 1
  ),
  '98600000-0000-4000-8000-000000000002'::uuid,
  'the most decorated player is listed first'
);

select extensions.is(
  (
    select entry.trophy_count
    from public.get_leaderboard() as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000004'
  ),
  1::bigint,
  'a friend who never opened the achievements screen still shows the unlock the friendship earned'
);

reset role;

insert into public.user_blocks (blocker_id, blocked_id)
values (
  '98600000-0000-4000-8000-000000000001',
  '98600000-0000-4000-8000-000000000004'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98600000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    select count(*)
    from public.get_leaderboard() as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000004'
  ),
  0::bigint,
  'blocking a friend removes them from the leaderboard'
);

select extensions.is(
  (select count(*) from public.get_leaderboard('friends')),
  (select count(*) from public.get_leaderboard()),
  'the explicit friends scope matches the argument-free call'
);

select extensions.is(
  (
    select count(*)
    from public.get_leaderboard('global') as entry
    where entry.user_id::text like '98600000-%'
  ),
  4::bigint,
  'the global scope lists every visible player'
);

select extensions.is(
  (
    select count(*)
    from public.get_leaderboard('global') as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000003'
  ),
  1::bigint,
  'a stranger appears on the global leaderboard'
);

select extensions.is(
  (
    select count(*)
    from public.get_leaderboard('global') as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000004'
  ),
  0::bigint,
  'a blocked player stays off the global leaderboard too'
);

select extensions.is(
  (
    select count(*)
    from public.get_leaderboard('global') as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000006'
  ),
  0::bigint,
  'a signup that never finished account setup stays off the global leaderboard'
);

select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98600000-0000-4000-8000-000000000006',
  true
);

select extensions.is(
  (
    select count(*)
    from public.get_leaderboard('global') as entry
    where entry.user_id = '98600000-0000-4000-8000-000000000006'
  ),
  1::bigint,
  'an unfinished signup still sees their own global row'
);

select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98600000-0000-4000-8000-000000000001',
  true
);

select extensions.throws_ok(
  $$select * from public.get_leaderboard('everyone')$$,
  '22023',
  'Unknown leaderboard scope: everyone',
  'an unknown scope is rejected'
);

reset role;

select * from extensions.finish();

rollback;
