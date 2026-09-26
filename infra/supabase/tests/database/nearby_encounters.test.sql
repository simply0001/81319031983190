begin;

set local search_path = public, extensions;

select extensions.plan(30);

select extensions.has_table(
  'public',
  'nearby_encounters',
  'nearby encounters table exists'
);
select extensions.has_table(
  'private',
  'nearby_credentials',
  'private nearby credentials table exists'
);
select extensions.has_table(
  'private',
  'nearby_receipts',
  'private nearby receipts table exists'
);
select extensions.ok(
  (
    select relrowsecurity
    from pg_catalog.pg_class
    where oid = 'public.nearby_encounters'::regclass
  ),
  'nearby encounters enforce RLS'
);
select extensions.ok(
  not pg_catalog.has_table_privilege(
    'authenticated',
    'public.nearby_encounters',
    'insert'
  ),
  'clients cannot insert encounters without the receipt RPC'
);
select extensions.ok(
  not pg_catalog.has_table_privilege(
    'authenticated',
    'private.nearby_credentials',
    'select'
  ),
  'clients cannot read the credential registry'
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
    '97000000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'nearby-a@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Nearby A"}'::jsonb,
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
    'nearby-b@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Nearby B"}'::jsonb,
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
    'nearby-c@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Nearby C"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

create temporary table nearby_test_credentials (
  owner_id uuid not null,
  token uuid not null,
  signing_public_key text not null
);
create temporary table nearby_test_result (
  encounter_id uuid not null,
  remote_user_id uuid not null
);
grant all on nearby_test_credentials to authenticated;
grant all on nearby_test_result to authenticated;

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '97000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

insert into nearby_test_credentials
select
  '97000000-0000-4000-8000-000000000001'::uuid,
  issued.token,
  issued.signing_public_key
from public.issue_nearby_credentials(
  array[repeat('A', 90), repeat('B', 90)]
) as issued;

select extensions.is(
  (
    select count(*)
    from nearby_test_credentials
    where owner_id = '97000000-0000-4000-8000-000000000001'
  ),
  2::bigint,
  'a signed-in user can provision a bounded local credential pool'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '97000000-0000-4000-8000-000000000002',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

insert into nearby_test_credentials
select
  '97000000-0000-4000-8000-000000000002'::uuid,
  issued.token,
  issued.signing_public_key
from public.issue_nearby_credentials(
  array[repeat('C', 90)]
) as issued;

select extensions.is(
  (
    select count(*)
    from nearby_test_credentials
    where owner_id = '97000000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'a second account receives a separate anonymous credential'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '97000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

insert into nearby_test_result
select encounter.encounter_id, encounter.remote_user_id
from public.submit_nearby_encounter(
  '97100000-0000-4000-8000-000000000001',
  '97200000-0000-4000-8000-000000000001',
  (
    select token from nearby_test_credentials
    where owner_id = '97000000-0000-4000-8000-000000000001'
      and signing_public_key = repeat('A', 90)
  ),
  (
    select token from nearby_test_credentials
    where owner_id = '97000000-0000-4000-8000-000000000002'
  ),
  repeat('A', 90),
  repeat('C', 90),
  repeat('H', 43),
  repeat('S', 88),
  repeat('T', 88),
  clock_timestamp()
) as encounter;

select extensions.is(
  (select remote_user_id from nearby_test_result),
  '97000000-0000-4000-8000-000000000002'::uuid,
  'one valid receipt resolves the peer profile'
);
select extensions.is(
  (select count(*) from public.nearby_encounters),
  1::bigint,
  'the reporter can read the resolved encounter'
);
select extensions.is(
  (select count(*) from public.get_nearby_encounters()),
  1::bigint,
  'the reporter receives one recent-interaction row'
);
select extensions.is(
  (
    select count(*)
    from public.notifications
    where kind = 'nearby_encounter'
      and actor_id = '97000000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'the reporter receives a nearby notification'
);
select extensions.is(
  (
    select count(*)
    from public.interaction_events
    where event_type = 'nearby_encounter'
      and subject_user_id = '97000000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'the reporter receives a nearby interaction event'
);
select extensions.is(
  (select confirmed_at from public.nearby_encounters),
  null::timestamptz,
  'a single-sided receipt leaves the encounter unconfirmed'
);
select extensions.is(
  (
    select trophy_count
    from public.get_leaderboard()
    where user_id = '97000000-0000-4000-8000-000000000001'
  ),
  0::bigint,
  'an unconfirmed encounter awards the reporter no trophy'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '97000000-0000-4000-8000-000000000002',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (select count(*) from public.get_nearby_encounters()),
  1::bigint,
  'one-sided receipt submission also resolves for the peer'
);
select extensions.is(
  (
    select count(*)
    from public.notifications
    where kind = 'nearby_encounter'
      and actor_id = '97000000-0000-4000-8000-000000000001'
  ),
  1::bigint,
  'the peer receives a nearby notification'
);
select extensions.is(
  (
    select encounter_id
    from public.submit_nearby_encounter(
      '97100000-0000-4000-8000-000000000002',
      '97200000-0000-4000-8000-000000000002',
      (
        select token from nearby_test_credentials
        where owner_id = '97000000-0000-4000-8000-000000000002'
      ),
      (
        select token from nearby_test_credentials
        where owner_id = '97000000-0000-4000-8000-000000000001'
          and signing_public_key = repeat('A', 90)
      ),
      repeat('C', 90),
      repeat('A', 90),
      repeat('H', 43),
      repeat('T', 88),
      repeat('S', 88),
      clock_timestamp()
    )
  ),
  (select encounter_id from nearby_test_result),
  'the reciprocal retry resolves to the same encounter'
);
select extensions.is(
  (select count(*) from public.nearby_encounters),
  1::bigint,
  'the same pair is deduplicated within the same UTC day'
);
select extensions.is(
  (select confirmed_at is not null from public.nearby_encounters),
  true,
  'a matching receipt from the peer confirms the encounter'
);
select extensions.is(
  (
    select trophy_count
    from public.get_leaderboard()
    where user_id = '97000000-0000-4000-8000-000000000002'
  ),
  1::bigint,
  'a confirmed encounter awards the trophy'
);

reset role;

select extensions.is(
  (
    select count(*)
    from private.nearby_receipts as receipt
    where receipt.reporter_id::text like '97%'
  ),
  2::bigint,
  'both parties file durable proof for the same encounter'
);
select extensions.is(
  (
    select count(*)
    from private.nearby_credentials as credential
    where credential.consumed_at is not null
      and credential.owner_id::text like '97%'
  ),
  2::bigint,
  'both one-time credentials are consumed atomically'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '97000000-0000-4000-8000-000000000003',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (select count(*) from public.nearby_encounters),
  0::bigint,
  'an unrelated account cannot read another pair encounter'
);
select extensions.is(
  (select count(*) from public.notifications),
  0::bigint,
  'an unrelated account cannot read either encounter notification'
);

insert into nearby_test_credentials
select
  '97000000-0000-4000-8000-000000000003'::uuid,
  issued.token,
  issued.signing_public_key
from public.issue_nearby_credentials(
  array[repeat('F', 90)]
) as issued;

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '97000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

insert into nearby_test_credentials
select
  '97000000-0000-4000-8000-000000000001'::uuid,
  issued.token,
  issued.signing_public_key
from public.issue_nearby_credentials(
  array[repeat('V', 90)]
) as issued;

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '97000000-0000-4000-8000-000000000003',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.submit_nearby_encounter(
      '97100000-0000-4000-8000-000000000003',
      '97200000-0000-4000-8000-000000000003',
      (
        select token from nearby_test_credentials
        where owner_id = '97000000-0000-4000-8000-000000000003'
      ),
      (
        select token from nearby_test_credentials
        where owner_id = '97000000-0000-4000-8000-000000000001'
          and signing_public_key = repeat('V', 90)
      ),
      repeat('F', 90),
      repeat('V', 90),
      repeat('Z', 43),
      repeat('X', 88),
      repeat('X', 88),
      clock_timestamp()
    )
  ),
  1::bigint,
  'a receipt carrying unverifiable signatures is still accepted for storage'
);
select extensions.is(
  (
    select count(*)
    from public.nearby_encounters
    where confirmed_at is null
  ),
  1::bigint,
  'a receipt built from a harvested peer token never confirms on its own'
);
select extensions.is(
  (
    select trophy_count
    from public.get_leaderboard()
    where user_id = '97000000-0000-4000-8000-000000000003'
  ),
  0::bigint,
  'a forged encounter awards the forger no trophy'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '97000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.submit_nearby_encounter(
      '97100000-0000-4000-8000-000000000004',
      '97200000-0000-4000-8000-000000000004',
      (
        select token from nearby_test_credentials
        where owner_id = '97000000-0000-4000-8000-000000000001'
          and signing_public_key = repeat('V', 90)
      ),
      (
        select token from nearby_test_credentials
        where owner_id = '97000000-0000-4000-8000-000000000003'
      ),
      repeat('V', 90),
      repeat('F', 90),
      repeat('Q', 43),
      repeat('X', 88),
      repeat('X', 88),
      clock_timestamp()
    )
  ),
  1::bigint,
  'the peer can file its own receipt against the same encounter'
);
select extensions.is(
  (
    select confirmed_at
    from public.nearby_encounters
    where user_low = '97000000-0000-4000-8000-000000000001'
      and user_high = '97000000-0000-4000-8000-000000000003'
  ),
  null::timestamptz,
  'receipts whose transcript hashes disagree describe different handshakes and do not confirm'
);

reset role;

select * from extensions.finish();
rollback;
