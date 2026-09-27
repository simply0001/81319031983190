begin;

set local search_path = public, extensions;

select extensions.plan(10);

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
    ('98a70000-0000-4000-8000-000000000001'::uuid, 'handshake-a@pocketpass.test', 'Handshake A'),
    ('98a70000-0000-4000-8000-000000000002'::uuid, 'handshake-b@pocketpass.test', 'Handshake B')
) as seed(id, email, name);

insert into private.nearby_credentials (token, owner_id, signing_public_key, created_at, expires_at)
values
  ('98a80000-0000-4000-8000-0000000000a1', '98a70000-0000-4000-8000-000000000001', repeat('A', 90), now() - interval '2 days', now() + interval '5 days'),
  ('98a80000-0000-4000-8000-0000000000a2', '98a70000-0000-4000-8000-000000000001', repeat('C', 90), now() - interval '2 days', now() + interval '5 days'),
  ('98a80000-0000-4000-8000-0000000000b1', '98a70000-0000-4000-8000-000000000002', repeat('B', 90), now() - interval '2 days', now() + interval '5 days'),
  ('98a80000-0000-4000-8000-0000000000b2', '98a70000-0000-4000-8000-000000000002', repeat('D', 90), now() - interval '2 days', now() + interval '5 days');

set local role authenticated;

select pg_catalog.set_config('request.jwt.claim.sub', '98a70000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select extensions.is(
  (
    select encounter_id
    from public.submit_nearby_encounter(
      '98a90000-0000-4000-8000-000000000001',
      '98a60000-0000-4000-8000-000000000101',
      '98a80000-0000-4000-8000-0000000000a1',
      '98a80000-0000-4000-8000-0000000000b1',
      repeat('A', 90),
      repeat('B', 90),
      repeat('X', 43),
      repeat('S', 88),
      repeat('T', 88),
      (date_trunc('day', now() at time zone 'utc') + interval '30 seconds') at time zone 'utc'
    )
  ),
  '98a90000-0000-4000-8000-000000000001'::uuid,
  'the first receipt of the day opens the encounter'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98a70000-0000-4000-8000-000000000002', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select extensions.is(
  (
    select encounter_id
    from public.submit_nearby_encounter(
      '98a90000-0000-4000-8000-000000000002',
      '98a60000-0000-4000-8000-000000000102',
      '98a80000-0000-4000-8000-0000000000b2',
      '98a80000-0000-4000-8000-0000000000a2',
      repeat('D', 90),
      repeat('C', 90),
      repeat('Y', 43),
      repeat('U', 88),
      repeat('V', 88),
      (date_trunc('day', now() at time zone 'utc') + interval '5 minutes') at time zone 'utc'
    )
  ),
  '98a90000-0000-4000-8000-000000000001'::uuid,
  'a receipt for a different handshake still folds into the day''s encounter'
);

reset role;

select extensions.ok(
  (
    select confirmed_at is null
    from public.nearby_encounters
    where id = '98a90000-0000-4000-8000-000000000001'
  ),
  'receipts for two different handshakes do not confirm anything'
);
select extensions.is(
  (
    select count(*)
    from private.nearby_receipts
    where encounter_id = '98a90000-0000-4000-8000-000000000001'
  ),
  2::bigint,
  'both mismatched receipts are kept'
);

set local role authenticated;

select pg_catalog.set_config('request.jwt.claim.sub', '98a70000-0000-4000-8000-000000000002', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select extensions.is(
  (
    select encounter_id
    from public.submit_nearby_encounter(
      '98a90000-0000-4000-8000-000000000003',
      '98a60000-0000-4000-8000-000000000103',
      '98a80000-0000-4000-8000-0000000000b1',
      '98a80000-0000-4000-8000-0000000000a1',
      repeat('B', 90),
      repeat('A', 90),
      repeat('X', 43),
      repeat('T', 88),
      repeat('S', 88),
      (date_trunc('day', now() at time zone 'utc') + interval '30 seconds') at time zone 'utc'
    )
  ),
  '98a90000-0000-4000-8000-000000000001'::uuid,
  'a second receipt from the same reporter is accepted for another handshake'
);

reset role;

select extensions.ok(
  (
    select confirmed_at is not null
    from public.nearby_encounters
    where id = '98a90000-0000-4000-8000-000000000001'
  ),
  'the day confirms once both sides share one handshake'
);
select extensions.is(
  (
    select count(*)
    from private.nearby_receipts
    where encounter_id = '98a90000-0000-4000-8000-000000000001'
  ),
  3::bigint,
  'the encounter holds all three receipts'
);
select extensions.is(
  (
    select amount
    from private.encounter_token_rewards
    where encounter_id = '98a90000-0000-4000-8000-000000000001'
  ),
  30,
  'the first confirmed meeting still pays 30 tokens'
);

set local role authenticated;

select pg_catalog.set_config('request.jwt.claim.sub', '98a70000-0000-4000-8000-000000000002', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select extensions.is(
  (
    select encounter_id
    from public.submit_nearby_encounter(
      '98a90000-0000-4000-8000-000000000003',
      '98a60000-0000-4000-8000-000000000103',
      '98a80000-0000-4000-8000-0000000000b1',
      '98a80000-0000-4000-8000-0000000000a1',
      repeat('B', 90),
      repeat('A', 90),
      repeat('X', 43),
      repeat('T', 88),
      repeat('S', 88),
      (date_trunc('day', now() at time zone 'utc') + interval '30 seconds') at time zone 'utc'
    )
  ),
  '98a90000-0000-4000-8000-000000000001'::uuid,
  'a retried receipt resolves to the same encounter'
);

reset role;

select extensions.is(
  (
    select count(*)
    from private.nearby_receipts
    where encounter_id = '98a90000-0000-4000-8000-000000000001'
  ),
  3::bigint,
  'a retried receipt is not stored twice'
);

select * from extensions.finish();

rollback;
