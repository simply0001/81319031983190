begin;

set local search_path = public, extensions;

select extensions.plan(57);

select extensions.has_table('public', 'puzzle_panels', 'puzzle panels table exists');
select extensions.has_table('public', 'puzzle_progress', 'puzzle progress table exists');
select extensions.has_table('public', 'puzzle_pieces', 'puzzle pieces table exists');
select extensions.has_table('private', 'puzzle_encounter_grants', 'puzzle encounter ledger exists');

select extensions.ok(
  not pg_catalog.has_table_privilege('authenticated', 'public.puzzle_progress', 'insert, update, delete'),
  'clients cannot write puzzle progress'
);
select extensions.ok(
  not pg_catalog.has_table_privilege('authenticated', 'public.puzzle_pieces', 'insert, update, delete'),
  'clients cannot write puzzle pieces'
);
select extensions.ok(
  not pg_catalog.has_table_privilege('api_client', 'public.puzzle_pieces', 'select'),
  'the public API role cannot read puzzle pieces'
);
select extensions.is(
  (select bucket.public from storage.buckets as bucket where bucket.id = 'puzzle-panels'),
  true,
  'the puzzle panel bucket is public'
);
select extensions.is(
  (select relation.relrowsecurity from pg_catalog.pg_class as relation where relation.oid = 'public.puzzle_progress'::regclass),
  true,
  'puzzle progress has row level security'
);

select extensions.throws_ok(
  $$ select public.get_puzzle_collection() $$,
  '42501',
  null,
  'the collection requires authentication'
);
select extensions.throws_ok(
  $$ select * from public.buy_puzzle_piece(gen_random_uuid()) $$,
  '42501',
  null,
  'buying a piece requires authentication'
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
select
  '00000000-0000-0000-0000-000000000000',
  seed.id,
  'authenticated',
  'authenticated',
  seed.email,
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}'::jsonb,
  jsonb_build_object('display_name', seed.name),
  now(),
  now(),
  '',
  '',
  '',
  ''
from (
  values
    ('98f10000-0000-4000-8000-000000000001'::uuid, 'puzzle-a@pocketpass.test', 'Puzzle A'),
    ('98f10000-0000-4000-8000-000000000002'::uuid, 'puzzle-b@pocketpass.test', 'Puzzle B'),
    ('98f10000-0000-4000-8000-000000000003'::uuid, 'puzzle-c@pocketpass.test', 'Puzzle C')
) as seed(id, email, name);

update public.puzzle_panels set is_active = false;

insert into public.puzzle_panels (id, slug, title, image_path, grid_columns, grid_rows, sort_order)
values
  ('98f20000-0000-4000-8000-000000000001', 'pgtap_one', 'Test Panel One', 'panels/pgtap_one.png', 2, 2, 90001),
  ('98f20000-0000-4000-8000-000000000002', 'pgtap_two', 'Test Panel Two', 'panels/pgtap_two.png', 2, 3, 90002);

insert into storage.objects (bucket_id, name, metadata)
values ('puzzle-panels', 'panels/pgtap_one.png', '{"mimetype":"image/png","size":1024}');

create temporary table puzzle_test_log (
  label text primary key,
  piece_index integer,
  balance integer
);
grant all on puzzle_test_log to authenticated;

create temporary table puzzle_test_credentials (
  owner_id uuid not null,
  token uuid not null,
  signing_public_key text not null
);
grant all on puzzle_test_credentials to authenticated;

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (public.get_puzzle_collection() ->> 'current_index')::integer,
  0,
  'a fresh account starts on its own Piip'
);
select extensions.is(
  (public.get_puzzle_collection() -> 'puzzles' -> 0 ->> 'total_pieces')::integer,
  16,
  'the own Piip puzzle has 16 pieces'
);
select extensions.is(
  (public.get_puzzle_collection() -> 'puzzles' -> 0 -> 'owned_pieces')::text,
  '[5]',
  'the own Piip puzzle starts with piece 5'
);
select extensions.is(
  (public.get_puzzle_collection() ->> 'piece_price')::integer,
  15,
  'a piece costs 15 tokens'
);
select extensions.is(
  jsonb_array_length(public.get_puzzle_collection() -> 'puzzles'),
  2,
  'the collection lists the own Piip and the one panel with artwork'
);
select extensions.is(
  (public.get_puzzle_collection() -> 'puzzles' -> 1 ->> 'slug')
    || ':' || (public.get_puzzle_collection() -> 'puzzles' -> 1 -> 'owned_pieces')::text,
  'pgtap_one:[]',
  'the next panel is listed without pieces'
);

select extensions.throws_ok(
  $$ select * from public.buy_puzzle_piece('98f30000-0000-4000-8000-000000000001') $$,
  'PT402',
  'Not enough tokens',
  'buying with an empty balance is refused'
);

reset role;

insert into public.token_balances (user_id, balance)
values ('98f10000-0000-4000-8000-000000000001', 100)
on conflict (user_id) do update set balance = 100;

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

insert into puzzle_test_log (label, piece_index, balance)
select 'first', purchase.piece_index, purchase.balance
from public.buy_puzzle_piece('98f30000-0000-4000-8000-000000000001') as purchase;

select extensions.is(
  (select log.balance from puzzle_test_log as log where log.label = 'first'),
  85,
  'a purchase debits 15 tokens'
);
select extensions.is(
  jsonb_array_length(public.get_puzzle_collection() -> 'puzzles' -> 0 -> 'owned_pieces'),
  2,
  'the bought piece joins the own Piip puzzle'
);

insert into puzzle_test_log (label, piece_index, balance)
select 'replay', purchase.piece_index, purchase.balance
from public.buy_puzzle_piece('98f30000-0000-4000-8000-000000000001') as purchase;

select extensions.is(
  (select log.piece_index from puzzle_test_log as log where log.label = 'replay'),
  (select log.piece_index from puzzle_test_log as log where log.label = 'first'),
  'a replayed purchase returns the same piece'
);
select extensions.is(
  (select log.balance from puzzle_test_log as log where log.label = 'replay'),
  85,
  'a replayed purchase does not charge again'
);

select extensions.is(
  (select purchase.balance from public.buy_puzzle_piece('98f30000-0000-4000-8000-000000000002') as purchase),
  70,
  'a new operation buys another piece'
);

reset role;

select extensions.is(
  (
    select count(*) = 3 and count(distinct piece.piece_index) = 3 and bool_and(piece.piece_index between 0 and 15)
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000001'
      and piece.puzzle_key = 'own_piip'
  ),
  true,
  'owned pieces are distinct and inside the grid'
);

with missing as (
  select candidate.idx
  from generate_series(0, 15) as candidate(idx)
  where not exists (
    select 1
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000001'
      and piece.puzzle_key = 'own_piip'
      and piece.piece_index = candidate.idx
  )
)
insert into public.puzzle_pieces (user_id, puzzle_key, piece_index, source)
select '98f10000-0000-4000-8000-000000000001', 'own_piip', missing.idx, 'start'
from missing
where missing.idx <> (select max(inner_missing.idx) from missing as inner_missing);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select purchase.completed || ':' || coalesce(purchase.next_puzzle_key, 'none')
    from public.buy_puzzle_piece('98f30000-0000-4000-8000-000000000003') as purchase
  ),
  'true:panel:98f20000-0000-4000-8000-000000000001',
  'buying the last piece completes the puzzle and opens the first panel'
);
select extensions.is(
  (public.get_puzzle_collection() ->> 'current_index')::integer,
  1,
  'the current puzzle moves to the first panel'
);

reset role;

select extensions.is(
  (
    select progress.completed_at is not null and not progress.completed_by_handover
    from public.puzzle_progress as progress
    where progress.user_id = '98f10000-0000-4000-8000-000000000001'
      and progress.puzzle_key = 'own_piip'
  ),
  true,
  'the own Piip puzzle is recorded as complete without a handover'
);
select extensions.is(
  (
    select count(*)
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000001'
      and piece.puzzle_key = 'panel:98f20000-0000-4000-8000-000000000001'
      and piece.source = 'start'
  ),
  1::bigint,
  'the new panel opens with one starting piece'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

insert into puzzle_test_credentials
select
  '98f10000-0000-4000-8000-000000000001'::uuid,
  issued.token,
  issued.signing_public_key
from public.issue_nearby_credentials(array[repeat('A', 90)]) as issued;

reset role;
set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000002', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

insert into puzzle_test_credentials
select
  '98f10000-0000-4000-8000-000000000002'::uuid,
  issued.token,
  issued.signing_public_key
from public.issue_nearby_credentials(array[repeat('B', 90)]) as issued;

reset role;
set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.submit_nearby_encounter(
      '98f40000-0000-4000-8000-000000000001',
      '98f50000-0000-4000-8000-000000000001',
      (select token from puzzle_test_credentials where owner_id = '98f10000-0000-4000-8000-000000000001'),
      (select token from puzzle_test_credentials where owner_id = '98f10000-0000-4000-8000-000000000002'),
      repeat('A', 90),
      repeat('B', 90),
      repeat('H', 43),
      repeat('S', 88),
      repeat('T', 88),
      clock_timestamp()
    )
  ),
  1::bigint,
  'the first receipt resolves the peer'
);

reset role;
set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000002', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.submit_nearby_encounter(
      '98f40000-0000-4000-8000-000000000002',
      '98f50000-0000-4000-8000-000000000002',
      (select token from puzzle_test_credentials where owner_id = '98f10000-0000-4000-8000-000000000002'),
      (select token from puzzle_test_credentials where owner_id = '98f10000-0000-4000-8000-000000000001'),
      repeat('B', 90),
      repeat('A', 90),
      repeat('H', 43),
      repeat('T', 88),
      repeat('S', 88),
      clock_timestamp()
    )
  ),
  1::bigint,
  'the reciprocal receipt confirms the encounter'
);

reset role;

select extensions.is(
  (
    select count(*)
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000001'
      and piece.puzzle_key = 'panel:98f20000-0000-4000-8000-000000000001'
      and piece.source = 'encounter'
      and piece.encounter_id = '98f40000-0000-4000-8000-000000000001'
  ),
  1::bigint,
  'the confirmed encounter hands the reporter a piece of the open panel'
);
select extensions.is(
  (
    select count(*)
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000002'
      and piece.puzzle_key = 'own_piip'
      and piece.source = 'encounter'
  ),
  1::bigint,
  'the peer receives a piece of their own Piip'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000002', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.submit_nearby_encounter(
      '98f40000-0000-4000-8000-000000000002',
      '98f50000-0000-4000-8000-000000000002',
      (select token from puzzle_test_credentials where owner_id = '98f10000-0000-4000-8000-000000000002'),
      (select token from puzzle_test_credentials where owner_id = '98f10000-0000-4000-8000-000000000001'),
      repeat('B', 90),
      repeat('A', 90),
      repeat('H', 43),
      repeat('T', 88),
      repeat('S', 88),
      clock_timestamp()
    )
  ),
  1::bigint,
  'a retried receipt still resolves'
);

reset role;

select extensions.is(
  (
    select count(*)
    from public.puzzle_pieces as piece
    where piece.user_id in ('98f10000-0000-4000-8000-000000000001', '98f10000-0000-4000-8000-000000000002')
      and piece.source = 'encounter'
  ),
  2::bigint,
  'a retried receipt hands out nothing more'
);
select extensions.is(
  (select count(*) from private.puzzle_encounter_grants),
  1::bigint,
  'the encounter ledger holds one row'
);

insert into public.puzzle_progress (user_id, puzzle_key, panel_id, grid_columns, grid_rows, completed_at)
values (
  '98f10000-0000-4000-8000-000000000002',
  'panel:98f20000-0000-4000-8000-000000000001',
  '98f20000-0000-4000-8000-000000000001',
  2,
  2,
  now()
);

insert into public.puzzle_pieces (user_id, puzzle_key, piece_index, source)
select '98f10000-0000-4000-8000-000000000002', 'panel:98f20000-0000-4000-8000-000000000001', candidate.idx, 'start'
from generate_series(0, 3) as candidate(idx);

insert into public.nearby_encounters (
  id,
  user_low,
  user_high,
  reported_by,
  reporter_operation_id,
  occurred_at,
  confirmed_at
)
values (
  '98f40000-0000-4000-8000-000000000003',
  '98f10000-0000-4000-8000-000000000001',
  '98f10000-0000-4000-8000-000000000002',
  '98f10000-0000-4000-8000-000000000001',
  gen_random_uuid(),
  (date_trunc('day', now() at time zone 'utc') + interval '1 day 12 hours') at time zone 'utc',
  (date_trunc('day', now() at time zone 'utc') + interval '1 day 12 hours') at time zone 'utc'
);

select extensions.is(
  (
    select count(*)
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000001'
      and piece.puzzle_key = 'panel:98f20000-0000-4000-8000-000000000001'
      and piece.source = 'handover'
      and piece.from_user_id = '98f10000-0000-4000-8000-000000000002'
      and piece.encounter_id = '98f40000-0000-4000-8000-000000000003'
  ),
  1::bigint,
  'a peer who owns the missing pieces hands one over'
);
select extensions.is(
  (
    select count(*)
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000001'
      and piece.puzzle_key = 'panel:98f20000-0000-4000-8000-000000000001'
  ),
  3::bigint,
  'the panel now has three of four pieces'
);
select extensions.is(
  (
    select count(*)
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000002'
      and piece.source = 'encounter'
  ),
  2::bigint,
  'the giver still receives an ordinary piece'
);
select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.recipient_id = '98f10000-0000-4000-8000-000000000001'
      and notification.title = 'Puzzle piece received'
      and notification.actor_id = '98f10000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'a handover is announced with the giver'
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
values (
  '98f40000-0000-4000-8000-000000000004',
  '98f10000-0000-4000-8000-000000000001',
  '98f10000-0000-4000-8000-000000000002',
  '98f10000-0000-4000-8000-000000000001',
  gen_random_uuid(),
  (date_trunc('day', now() at time zone 'utc') + interval '2 days 12 hours') at time zone 'utc',
  (date_trunc('day', now() at time zone 'utc') + interval '2 days 12 hours') at time zone 'utc'
);

select extensions.is(
  (
    select progress.completed_at is not null and progress.completed_by_handover
    from public.puzzle_progress as progress
    where progress.user_id = '98f10000-0000-4000-8000-000000000001'
      and progress.puzzle_key = 'panel:98f20000-0000-4000-8000-000000000001'
  ),
  true,
  'a handover that finishes a puzzle is recorded'
);
select extensions.is(
  (
    select count(*)
    from public.achievement_unlocks as unlock
    where unlock.user_id = '98f10000-0000-4000-8000-000000000001'
      and unlock.achievement_key = 'missing_piece'
  ),
  1::bigint,
  'finishing with a handed-over piece unlocks missing_piece'
);
select extensions.is(
  (
    select count(*)
    from public.achievement_unlocks as unlock
    where unlock.user_id = '98f10000-0000-4000-8000-000000000002'
      and unlock.achievement_key = 'missing_piece'
  ),
  0::bigint,
  'the giver does not unlock missing_piece'
);
select extensions.is(
  (
    select count(*)
    from public.puzzle_progress as progress
    where progress.user_id = '98f10000-0000-4000-8000-000000000001'
      and progress.completed_at is null
  ),
  0::bigint,
  'nothing opens while the next panel has no artwork'
);
select extensions.is(
  (
    select count(*)
    from public.achievement_unlocks as unlock
    where unlock.user_id = '98f10000-0000-4000-8000-000000000001'
      and unlock.achievement_key = 'full_set'
  ),
  1::bigint,
  'completing every available puzzle unlocks full_set'
);

insert into public.puzzle_progress (user_id, puzzle_key, grid_columns, grid_rows, completed_at)
values ('98f10000-0000-4000-8000-000000000003', 'own_piip', 4, 4, now());

insert into public.puzzle_pieces (user_id, puzzle_key, piece_index, source)
select '98f10000-0000-4000-8000-000000000003', 'own_piip', candidate.idx, 'start'
from generate_series(0, 15) as candidate(idx);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000003', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select entry.progress_percent
    from public.get_achievements() as entry
    where entry.achievement_key = 'full_set'
  ),
  50,
  'full_set reports one of two puzzles complete'
);
select extensions.is(
  (public.get_puzzle_collection() ->> 'current_index')::integer,
  1,
  'opening the collection starts the first panel for a player whose Piip is done'
);

select extensions.is(
  (
    select report.pieces_awarded || '/' || report.pieces_credited
    from public.report_daily_steps((now() at time zone 'utc')::date, 10000, 0) as report
  ),
  '2/2',
  '10,000 steps grant two pieces'
);
select extensions.is(
  (
    select report.pieces_awarded || '/' || report.pieces_credited
    from public.report_daily_steps((now() at time zone 'utc')::date, 10000, 0) as report
  ),
  '2/0',
  'reporting the same day again grants nothing more'
);
select extensions.is(
  (
    select report.pieces_awarded || '/' || report.pieces_credited
    from public.report_daily_steps((now() at time zone 'utc')::date - 1, 4999, 0) as report
  ),
  '0/0',
  '4,999 steps grant no piece'
);

reset role;

select extensions.is(
  (
    select count(*)
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000003'
      and piece.source = 'steps'
  ),
  2::bigint,
  'the step pieces are recorded with their source'
);

insert into storage.objects (bucket_id, name, metadata)
values ('puzzle-panels', 'panels/pgtap_two.png', '{"mimetype":"image/png","size":1024}');

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (public.get_puzzle_collection() ->> 'current_index')::integer,
  2,
  'uploading the artwork lets the next panel start'
);
select extensions.is(
  (
    select entry.unlocked
    from public.get_achievements() as entry
    where entry.achievement_key = 'full_set'
  ),
  true,
  'full_set stays unlocked when a new panel arrives'
);

reset role;

insert into public.puzzle_pieces (user_id, puzzle_key, piece_index, source)
select '98f10000-0000-4000-8000-000000000001', 'panel:98f20000-0000-4000-8000-000000000002', candidate.idx, 'start'
from generate_series(0, 5) as candidate(idx)
on conflict do nothing;

update public.puzzle_progress as progress
set completed_at = now()
where progress.user_id = '98f10000-0000-4000-8000-000000000001'
  and progress.puzzle_key = 'panel:98f20000-0000-4000-8000-000000000002';

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98f10000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.throws_ok(
  $$ select * from public.buy_puzzle_piece('98f30000-0000-4000-8000-000000000004') $$,
  'PT409',
  'Every puzzle is complete',
  'buying with nothing left to fill is refused'
);
select extensions.is(
  public.get_puzzle_collection() ->> 'current_index',
  null,
  'a finished collection has no current puzzle'
);

reset role;

select extensions.is(
  (select count(*) from private.puzzle_encounter_grants),
  3::bigint,
  'every confirmed encounter is in the ledger'
);

delete from public.nearby_encounters where id = '98f40000-0000-4000-8000-000000000003';

select extensions.is(
  (select count(*) from private.puzzle_encounter_grants),
  2::bigint,
  'deleting an encounter removes its ledger row'
);

delete from public.profiles where user_id = '98f10000-0000-4000-8000-000000000003';

select extensions.is(
  (
    select count(*)
    from public.puzzle_pieces as piece
    where piece.user_id = '98f10000-0000-4000-8000-000000000003'
  ) + (
    select count(*)
    from public.puzzle_progress as progress
    where progress.user_id = '98f10000-0000-4000-8000-000000000003'
  ),
  0::bigint,
  'deleting a profile removes its puzzles and pieces'
);

select * from extensions.finish();

rollback;
