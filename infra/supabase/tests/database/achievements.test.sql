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
    '98700000-0000-4000-8000-000000000001',
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
    '98700000-0000-4000-8000-000000000002',
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
    '98700000-0000-4000-8000-000000000003',
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
    '98700000-0000-4000-8000-000000000004',
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
  );

update public.profiles set country_code = 'US'
where user_id = '98700000-0000-4000-8000-000000000001';
update public.profiles set country_code = 'FR'
where user_id = '98700000-0000-4000-8000-000000000002';
update public.profiles set country_code = 'JP'
where user_id = '98700000-0000-4000-8000-000000000003';

select extensions.throws_ok(
  $$select * from public.get_achievements()$$,
  '42501',
  'Authentication required',
  'achievements reject an unauthenticated caller'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (select count(*) from public.get_achievements()),
  11::bigint,
  'every achievement key is returned'
);

select extensions.is(
  (select count(*) from public.get_achievements() as entry where entry.unlocked),
  0::bigint,
  'a fresh account has nothing unlocked'
);

select extensions.is(
  (
    select entry.progress_percent
    from public.get_achievements() as entry
    where entry.achievement_key = 'saving_up'
  ),
  0,
  'saving_up progress starts at zero now that accounts open empty'
);

reset role;

insert into public.conversations (id, kind, created_by, direct_user_low, direct_user_high)
values (
  '98710000-0000-4000-8000-000000000001',
  'direct',
  '98700000-0000-4000-8000-000000000001',
  '98700000-0000-4000-8000-000000000001',
  '98700000-0000-4000-8000-000000000002'
);

insert into public.messages (conversation_id, sender_id, client_operation_id, body, created_at)
values
  (
    '98710000-0000-4000-8000-000000000001',
    '98700000-0000-4000-8000-000000000001',
    '98720000-0000-4000-8000-000000000001',
    'day one',
    now() - interval '2 days'
  ),
  (
    '98710000-0000-4000-8000-000000000001',
    '98700000-0000-4000-8000-000000000001',
    '98720000-0000-4000-8000-000000000002',
    'day two',
    now() - interval '1 day'
  ),
  (
    '98710000-0000-4000-8000-000000000001',
    '98700000-0000-4000-8000-000000000001',
    '98720000-0000-4000-8000-000000000003',
    'day three',
    now()
  );

insert into public.friendships (user_low, user_high, created_by)
values (
  '98700000-0000-4000-8000-000000000001',
  '98700000-0000-4000-8000-000000000004',
  '98700000-0000-4000-8000-000000000001'
);

insert into public.nearby_encounters (
  id, user_low, user_high, reported_by, reporter_operation_id, occurred_at, confirmed_at
)
values
  (
    '98730000-0000-4000-8000-000000000001',
    '98700000-0000-4000-8000-000000000001',
    '98700000-0000-4000-8000-000000000002',
    '98700000-0000-4000-8000-000000000001',
    '98740000-0000-4000-8000-000000000001',
    now() - interval '1 day',
    now() - interval '1 day'
  ),
  (
    '98730000-0000-4000-8000-000000000002',
    '98700000-0000-4000-8000-000000000001',
    '98700000-0000-4000-8000-000000000003',
    '98700000-0000-4000-8000-000000000001',
    '98740000-0000-4000-8000-000000000002',
    now(),
    null
  );

select extensions.is(
  (
    select count(*)
    from public.achievement_unlocks as unlock
    where unlock.user_id = '98700000-0000-4000-8000-000000000001'
  ),
  4::bigint,
  'activity records unlocks as it happens, before the achievements screen is opened'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    select entry.trophy_count
    from public.get_leaderboard('global') as entry
    where entry.user_id = '98700000-0000-4000-8000-000000000001'
  ),
  (
    select count(*)
    from public.get_achievements() as entry
    where entry.unlocked
  ),
  'the leaderboard trophy count matches the unlocked achievements'
);

select extensions.is(
  (
    select entry.unlocked
    from public.get_achievements() as entry
    where entry.achievement_key = 'icebreaker'
  ),
  true,
  'sending a message unlocks icebreaker'
);

select extensions.is(
  (
    select entry.progress_percent
    from public.get_achievements() as entry
    where entry.achievement_key = 'streak'
  ),
  30,
  'three consecutive sending days is 30 percent of the streak'
);

select extensions.is(
  (
    select entry.unlocked
    from public.get_achievements() as entry
    where entry.achievement_key = 'plus_one'
  ),
  true,
  'an accepted friendship unlocks plus_one'
);

select extensions.is(
  (
    select entry.unlocked
    from public.get_achievements() as entry
    where entry.achievement_key = 'first_encounter'
  ),
  true,
  'a confirmed encounter unlocks first_encounter'
);

select extensions.is(
  (
    select entry.progress_percent
    from public.get_achievements() as entry
    where entry.achievement_key = 'small_world'
  ),
  10,
  'an unconfirmed encounter does not count toward small_world'
);

select extensions.is(
  (
    select entry.unlocked
    from public.get_achievements() as entry
    where entry.achievement_key = 'passport_stamped'
  ),
  true,
  'meeting somebody from another country unlocks passport_stamped'
);

select extensions.is(
  (
    select entry.progress_percent
    from public.get_achievements() as entry
    where entry.achievement_key = 'continental'
  ),
  16,
  'one continent met out of six is 16 percent'
);

reset role;

delete from public.friendships
where user_low = '98700000-0000-4000-8000-000000000001';

update public.profiles set legacy_account = true
where user_id = '98700000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    select entry.unlocked
    from public.get_achievements() as entry
    where entry.achievement_key = 'plus_one'
  ),
  true,
  'plus_one stays unlocked after the friendship is removed'
);

select extensions.is(
  (
    select entry.unlocked
    from public.get_achievements() as entry
    where entry.achievement_key = 'day_one'
  ),
  true,
  'the legacy account flag unlocks day_one'
);

select extensions.is(
  (
    select count(*)
    from public.get_achievements() as entry
    where entry.achievement_key in ('full_set', 'missing_piece')
      and not entry.unlocked
      and entry.progress_percent = 0
  ),
  2::bigint,
  'puzzle achievements stay locked while nothing is complete'
);

reset role;

select * from extensions.finish();

rollback;
