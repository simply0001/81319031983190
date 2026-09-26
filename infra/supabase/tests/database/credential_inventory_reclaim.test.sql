begin;

set local search_path = public, extensions;

select extensions.plan(6);

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
  '98b70000-0000-4000-8000-000000000001',
  'authenticated',
  'authenticated',
  'inventory@pocketpass.test',
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  '{"display_name":"Inventory"}',
  now(),
  now(),
  '',
  '',
  '',
  ''
);

-- 48 stale unused passes and 12 fresh ones: a full inventory that keeps
-- refusing under the old rule.
insert into private.nearby_credentials (owner_id, signing_public_key, created_at, expires_at)
select
  '98b70000-0000-4000-8000-000000000001',
  repeat('O', 90),
  now() - interval '2 days',
  now() + interval '5 days'
from generate_series(1, 48);

insert into private.nearby_credentials (owner_id, signing_public_key, created_at, expires_at)
select
  '98b70000-0000-4000-8000-000000000001',
  repeat('F', 90),
  now() - interval '10 minutes',
  now() + interval '7 days'
from generate_series(1, 12);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98b70000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.issue_nearby_credentials(
      array(select repeat('N', 90) from generate_series(1, 24))
    )
  ),
  24::bigint,
  'a full request succeeds against a full inventory'
);

reset role;

select extensions.is(
  (
    select count(*)
    from private.nearby_credentials
    where owner_id = '98b70000-0000-4000-8000-000000000001'
      and consumed_at is null
      and expires_at > now()
  ),
  64::bigint,
  'the live inventory is trimmed back to the cap'
);
select extensions.is(
  (
    select count(*)
    from private.nearby_credentials
    where owner_id = '98b70000-0000-4000-8000-000000000001'
      and signing_public_key = repeat('O', 90)
      and consumed_at is not null
  ),
  20::bigint,
  'only the oldest unused passes are retired'
);
select extensions.is(
  (
    select count(*)
    from private.nearby_credentials
    where owner_id = '98b70000-0000-4000-8000-000000000001'
      and signing_public_key = repeat('F', 90)
      and consumed_at is not null
  ),
  0::bigint,
  'passes issued within the last hour are never retired'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98b70000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (
    select count(*)
    from public.issue_nearby_credentials(
      array(select repeat('N', 90) from generate_series(1, 24))
    )
  ),
  24::bigint,
  'a following request keeps working while stale passes remain'
);

-- Four stale passes are left; a request for 24 cannot be made room for.
select extensions.throws_ok(
  $sql$
    select count(*)
    from public.issue_nearby_credentials(
      array(select repeat('N', 90) from generate_series(1, 24))
    )
  $sql$,
  '54000',
  'Credential inventory limit reached',
  'an inventory of recent passes at the cap is still refused'
);

reset role;

select * from extensions.finish();

rollback;
