begin;

set local search_path = public, extensions;

select extensions.plan(3);

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
    '98900000-0000-4000-8000-000000000001',
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
    '98900000-0000-4000-8000-000000000002',
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

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98900000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (select count(*) from public.get_bingo_card()),
  24::bigint,
  'a card deals twenty-four goals around the free center'
);

select extensions.is(
  (
    select array_agg(cell.goal_text order by cell.cell_position)
    from public.get_bingo_card() as cell
  ),
  (
    select array_agg(cell.goal_text order by cell.cell_position)
    from public.get_bingo_card() as cell
  ),
  'the same player draws the same card all week'
);

create temporary table board_a as
select cell.cell_position, cell.goal_text
from public.get_bingo_card() as cell;

select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98900000-0000-4000-8000-000000000002',
  true
);

select extensions.isnt(
  (
    select array_agg(cell.goal_text order by cell.cell_position)
    from public.get_bingo_card() as cell
  ),
  (
    select array_agg(board_a.goal_text order by board_a.cell_position)
    from board_a
  ),
  'different players draw different cards'
);

reset role;

select * from extensions.finish();

rollback;
