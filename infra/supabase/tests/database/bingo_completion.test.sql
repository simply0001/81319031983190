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
    '99100000-0000-4000-8000-000000000001',
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
    '99100000-0000-4000-8000-000000000002',
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
  );

update public.bingo_goals set is_active = false
where slug not in (
  'send_10_messages',
  'meet_poland', 'meet_japan', 'meet_brazil', 'meet_canada', 'meet_egypt',
  'meet_france', 'meet_india', 'meet_mexico', 'meet_italy', 'meet_kenya',
  'meet_spain', 'meet_germany', 'meet_sweden', 'meet_norway', 'meet_australia',
  'meet_south_korea', 'meet_china', 'meet_argentina', 'meet_nigeria',
  'meet_greece', 'meet_turkey', 'meet_portugal', 'meet_netherlands'
);

select extensions.throws_ok(
  $$select * from public.get_bingo_card()$$,
  '42501',
  'Authentication required',
  'the bingo card rejects an unauthenticated caller'
);

select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '99100000-0000-4000-8000-000000000001'
  ),
  0,
  'a fresh account opens with zero tokens'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '99100000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (select count(*) from public.get_bingo_card()),
  24::bigint,
  'a card deals twenty-four goals'
);

select extensions.is(
  (
    select count(*)
    from public.get_bingo_card() as cell
    where cell.cell_position = 12
  ),
  0::bigint,
  'the center stays free for the client'
);

select extensions.is(
  (
    select count(distinct cell.cell_position)
    from public.get_bingo_card() as cell
    where cell.cell_position between 0 and 24
  ),
  24::bigint,
  'every goal lands on its own card position'
);

select extensions.is(
  (
    select count(*)
    from public.get_bingo_card() as cell
    where cell.completed
  ),
  0::bigint,
  'a brand-new player has nothing stamped'
);

reset role;

insert into public.conversations (id, kind, created_by, direct_user_low, direct_user_high)
values (
  '99110000-0000-4000-8000-000000000001',
  'direct',
  '99100000-0000-4000-8000-000000000001',
  '99100000-0000-4000-8000-000000000001',
  '99100000-0000-4000-8000-000000000002'
);

insert into public.messages (conversation_id, sender_id, client_operation_id, body)
select
  '99110000-0000-4000-8000-000000000001',
  '99100000-0000-4000-8000-000000000001',
  ('99120000-0000-4000-8000-0000000000' || lpad(step::text, 2, '0'))::uuid,
  'message number ' || step
from generate_series(1, 10) as step;

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '99100000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    select cell.completed
    from public.get_bingo_card() as cell
    where cell.slug = 'send_10_messages'
  ),
  true,
  'ten sent messages stamp the message goal'
);

select extensions.is(
  (
    select cell.progress_current || '/' || cell.progress_target
    from public.get_bingo_card() as cell
    where cell.slug = 'send_10_messages'
  ),
  '10/10',
  'progress reports the full tally'
);

reset role;

delete from public.messages
where sender_id = '99100000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '99100000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    select cell.completed
    from public.get_bingo_card() as cell
    where cell.slug = 'send_10_messages'
  ),
  true,
  'a stamp survives losing the underlying condition'
);

select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '99100000-0000-4000-8000-000000000001'
  ),
  0,
  'one stamped cell pays nothing'
);

reset role;

insert into public.bingo_stamps (user_id, week_key, slug)
select
  '99100000-0000-4000-8000-000000000001',
  to_char(now() at time zone 'utc', 'IYYY-IW'),
  goal.slug
from public.bingo_goals as goal
where goal.is_active
on conflict on constraint bingo_stamps_pkey do nothing;

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '99100000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (select count(*) from public.get_bingo_card() as cell where cell.completed),
  24::bigint,
  'a fully stamped card reports every goal complete'
);

select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '99100000-0000-4000-8000-000000000001'
  ),
  450,
  'a blackout pays twelve lines and the full-card bonus'
);

select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.recipient_id = '99100000-0000-4000-8000-000000000001'
      and notification.kind = 'bingo_award'
  ),
  1::bigint,
  'the payout announces itself once'
);

select extensions.lives_ok(
  $$select count(*) from public.get_bingo_card()$$,
  'the card deals again without complaint'
);

select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '99100000-0000-4000-8000-000000000001'
  ),
  450,
  'calling again pays nothing new'
);

select extensions.is(
  (
    select award.lines_paid || ':' || award.blackout_paid
    from public.bingo_awards as award
    where award.user_id = '99100000-0000-4000-8000-000000000001'
  ),
  '12:true',
  'the award ledger records twelve lines and the blackout'
);

reset role;

select * from extensions.finish();

rollback;
