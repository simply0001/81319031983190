begin;

set local search_path = public, extensions;

select extensions.plan(22);

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
    ('99680000-0000-4000-8000-000000000001'::uuid, 'pz-player@pocketpass.test', 'Pz Player'),
    ('99680000-0000-4000-8000-000000000002'::uuid, 'pz-newcomer@pocketpass.test', 'Pz Newcomer'),
    ('99680000-0000-4000-8000-000000000009'::uuid, 'pz-developer@pocketpass.test', 'Pz Developer')
) as seed(id, email, name);

update public.puzzle_panels set is_active = false;

insert into public.puzzle_panels (id, slug, title, image_path, grid_columns, grid_rows, sort_order)
values ('99680000-0000-4000-8000-000000000100', 'pgtap_api_one', 'API Panel One', 'panels/pgtap_api_one.png', 2, 2, 90201);

insert into storage.objects (bucket_id, name, metadata)
values ('puzzle-panels', 'panels/pgtap_api_one.png', '{"mimetype":"image/png","size":1024}');

insert into public.puzzle_progress (user_id, puzzle_key, grid_columns, grid_rows)
values ('99680000-0000-4000-8000-000000000001', 'own_piip', 4, 4);

insert into public.puzzle_pieces (user_id, puzzle_key, piece_index, source)
values
  ('99680000-0000-4000-8000-000000000001', 'own_piip', 5, 'start'),
  ('99680000-0000-4000-8000-000000000001', 'own_piip', 6, 'purchase');

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99680000-0000-4000-8000-000000000009', true);

select pg_catalog.set_config(
  'pz_test.full',
  public.developer_create_app(
    'Pz Full pgtap',
    '',
    '',
    '',
    array['https://full.example/callback'],
    array['profile:read', 'puzzles:read'],
    'confidential'
  ) -> 'app' ->> 'client_id',
  true
);

select pg_catalog.set_config(
  'pz_test.bare',
  public.developer_create_app(
    'Pz Bare pgtap',
    '',
    '',
    '',
    array['https://bare.example/callback'],
    array['profile:read'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

reset role;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config('request.jwt.claim.sub', '', true);

update private.developer_apps
set rate_limit_per_second = 100000
where owner_user_id = '99680000-0000-4000-8000-000000000009';

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values
  (gen_random_uuid(), '99680000-0000-4000-8000-000000000001', current_setting('pz_test.full')::uuid, 'openid', now()),
  (gen_random_uuid(), '99680000-0000-4000-8000-000000000001', current_setting('pz_test.bare')::uuid, 'openid', now()),
  (gen_random_uuid(), '99680000-0000-4000-8000-000000000002', current_setting('pz_test.full')::uuid, 'openid', now());

select extensions.is(
  (private.api_scope_keys())[array_length(private.api_scope_keys(), 1)],
  'puzzles:read',
  'the scope catalog ends with puzzles:read'
);

select extensions.is(
  private.api_scope_descriptions() ->> 'puzzles:read',
  'See your Puzzle Swap progress',
  'puzzles:read has its consent line'
);

select extensions.is(
  private.api_normalize_scopes(array['puzzles:read', 'profile:read']),
  array['profile:read', 'puzzles:read']::text[],
  'normalisation keeps puzzles:read last'
);

select extensions.ok(
  pg_catalog.has_function_privilege('api_client', 'public.api_v1_puzzles_get(jsonb)', 'execute'),
  'api_client can execute puzzles.get'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('authenticated', 'public.api_v1_puzzles_get(jsonb)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'public.api_v1_puzzles_get(jsonb)', 'execute')
    and not pg_catalog.has_function_privilege('service_role', 'public.api_v1_puzzles_get(jsonb)', 'execute'),
  'first-party roles cannot execute puzzles.get'
);

select extensions.is(
  public.api_v1_puzzles_get('{}'::jsonb) ->> 'hint',
  'API_TOKEN_REQUIRED',
  'puzzles.get refuses a non-app caller'
);

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99680000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('pz_test.bare'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_puzzles_get('{}'::jsonb) ->> 'hint',
  'SCOPE_REQUIRED',
  'puzzles.get needs puzzles:read'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99680000-0000-4000-8000-000000000001',
    'role', 'api_client',
    'client_id', current_setting('pz_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key)
    from jsonb_object_keys(public.api_v1_puzzles_get('{}'::jsonb)) as keys(key)
  ),
  array['current_index', 'piece_price', 'pieces_owned_total', 'puzzles']::text[],
  'puzzles.get returns the collection object'
);

select extensions.is(
  (public.api_v1_puzzles_get('{}'::jsonb) ->> 'current_index')::integer,
  0,
  'the player is on the own Piip puzzle'
);

select extensions.is(
  (public.api_v1_puzzles_get('{}'::jsonb) ->> 'piece_price')::integer,
  15,
  'the piece price is reported'
);

select extensions.is(
  (public.api_v1_puzzles_get('{}'::jsonb) ->> 'pieces_owned_total')::integer,
  2,
  'the total piece count is reported'
);

select extensions.is(
  jsonb_array_length(public.api_v1_puzzles_get('{}'::jsonb) -> 'puzzles'),
  2,
  'the own Piip and the available panel are listed'
);

select extensions.is(
  public.api_v1_puzzles_get('{}'::jsonb) -> 'puzzles' -> 0 ->> 'kind',
  'own_piip',
  'the own Piip comes first'
);

select extensions.is(
  (public.api_v1_puzzles_get('{}'::jsonb) -> 'puzzles' -> 0 -> 'owned_pieces')::text,
  '[5, 6]',
  'owned pieces are listed in order'
);

select extensions.is(
  public.api_v1_puzzles_get('{}'::jsonb) -> 'puzzles' -> 1 ->> 'slug',
  'pgtap_api_one',
  'the panel follows the own Piip'
);

select extensions.is(
  public.api_v1_puzzles_get('{}'::jsonb) -> 'puzzles' -> 1 ->> 'image_path',
  'panels/pgtap_api_one.png',
  'the panel carries its artwork path'
);

select extensions.is(
  (public.api_v1_puzzles_get('{}'::jsonb) -> 'puzzles' -> 1 ->> 'total_pieces')::integer,
  4,
  'the panel grid is reported'
);

select extensions.ok(
  public.api_v1_puzzles_get('{"x": 1}'::jsonb) ? 'code',
  'unknown fields are rejected'
);

select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99680000-0000-4000-8000-000000000002',
    'role', 'api_client',
    'client_id', current_setting('pz_test.full'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_puzzles_get('{}'::jsonb) ->> 'current_index',
  null,
  'a player who never opened Puzzle Swap has no current puzzle'
);

select extensions.is(
  (public.api_v1_puzzles_get('{}'::jsonb) -> 'puzzles' -> 0 -> 'owned_pieces')::text,
  '[]',
  'reading does not grant that player any pieces'
);

reset role;
select pg_catalog.set_config('request.jwt.claims', '', true);

select extensions.is(
  (
    select count(*)
    from public.puzzle_progress as progress
    where progress.user_id = '99680000-0000-4000-8000-000000000002'
  ),
  0::bigint,
  'the public API writes nothing'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99680000-0000-4000-8000-000000000001', true);

select extensions.is(
  (public.get_puzzle_collection() ->> 'current_index')::integer
    || ':' || jsonb_array_length(public.get_puzzle_collection() -> 'puzzles'),
  '0:2',
  'the app RPC still returns the same collection'
);

reset role;

select * from extensions.finish();

rollback;
