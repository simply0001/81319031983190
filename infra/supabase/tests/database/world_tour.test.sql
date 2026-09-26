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
values
  (
    '00000000-0000-0000-0000-000000000000',
    '98800000-0000-4000-8000-000000000001',
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
    '98800000-0000-4000-8000-000000000002',
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
    '98800000-0000-4000-8000-000000000003',
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
    '98800000-0000-4000-8000-000000000004',
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

update public.profiles set country_code = 'FR'
where user_id = '98800000-0000-4000-8000-000000000002';
update public.profiles set country_code = 'FR'
where user_id = '98800000-0000-4000-8000-000000000003';

select extensions.throws_ok(
  $$select * from public.get_world_tour()$$,
  '42501',
  'Authentication required',
  'world tour rejects an unauthenticated caller'
);

insert into public.nearby_encounters (
  id, user_low, user_high, reported_by, reporter_operation_id, occurred_at, confirmed_at
)
values
  (
    '98810000-0000-4000-8000-000000000001',
    '98800000-0000-4000-8000-000000000001',
    '98800000-0000-4000-8000-000000000002',
    '98800000-0000-4000-8000-000000000001',
    '98820000-0000-4000-8000-000000000001',
    now() - interval '3 days',
    now() - interval '3 days'
  ),
  (
    '98810000-0000-4000-8000-000000000002',
    '98800000-0000-4000-8000-000000000001',
    '98800000-0000-4000-8000-000000000003',
    '98800000-0000-4000-8000-000000000001',
    '98820000-0000-4000-8000-000000000002',
    now() - interval '2 days',
    now() - interval '2 days'
  ),
  (
    '98810000-0000-4000-8000-000000000003',
    '98800000-0000-4000-8000-000000000001',
    '98800000-0000-4000-8000-000000000004',
    '98800000-0000-4000-8000-000000000001',
    '98820000-0000-4000-8000-000000000003',
    now() - interval '1 day',
    now() - interval '1 day'
  );

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98800000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (select count(*) from public.get_world_tour()),
  1::bigint,
  'two same-country peers count as one region and a countryless peer counts as none'
);

select extensions.is(
  (select region.country_code from public.get_world_tour() as region limit 1),
  'FR',
  'the discovered region is the peers'' country'
);

reset role;

update public.profiles set country_code = 'JP'
where user_id = '98800000-0000-4000-8000-000000000004';

insert into public.nearby_encounters (
  id, user_low, user_high, reported_by, reporter_operation_id, occurred_at, confirmed_at
)
values (
  '98810000-0000-4000-8000-000000000004',
  '98800000-0000-4000-8000-000000000002',
  '98800000-0000-4000-8000-000000000004',
  '98800000-0000-4000-8000-000000000002',
  '98820000-0000-4000-8000-000000000004',
  now(),
  null
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98800000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (select region.country_code from public.get_world_tour() as region limit 1),
  'JP',
  'the newest discovery is listed first'
);

select extensions.is(
  (select count(*) from public.get_world_tour()),
  2::bigint,
  'regions cover every confirmed encounter country'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98800000-0000-4000-8000-000000000002',
  true
);

select extensions.is(
  (
    select count(*)
    from public.get_world_tour() as region
    where region.country_code = 'JP'
  ),
  0::bigint,
  'an unconfirmed encounter discovers nothing'
);

reset role;

select * from extensions.finish();

rollback;
