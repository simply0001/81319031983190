begin;

set local search_path = public, extensions;

select extensions.plan(70);

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
    ('99500000-0000-4000-8000-000000000001'::uuid, 'lim-owner@pocketpass.test', 'Lim Owner pgtap'),
    ('99500000-0000-4000-8000-000000000002'::uuid, 'lim-other@pocketpass.test', 'Lim Other pgtap'),
    ('99500000-0000-4000-8000-000000000005'::uuid, 'lim-admin@pocketpass.test', 'Lim Admin pgtap'),
    ('99500000-0000-4000-8000-000000000006'::uuid, 'lim-plain@pocketpass.test', 'Lim Plain pgtap')
) as seed(id, email, name);

insert into private.admin_users (user_id, note, permissions)
values
  ('99500000-0000-4000-8000-000000000005', 'pgtap limits admin', array['apps']),
  ('99500000-0000-4000-8000-000000000006', 'pgtap plain admin', '{}');

do $$
begin
  if not exists (select 1 from vault.secrets where name = 'discord_limit_requests_webhook') then
    perform vault.create_secret(
      'https://discord.com/api/webhooks/000000000000000000/pgtap',
      'discord_limit_requests_webhook',
      'pgtap'
    );
  end if;
end
$$;

select pg_catalog.set_config(
  'lim_test.webhook',
  (
    select secret.decrypted_secret
    from vault.decrypted_secrets as secret
    where secret.name = 'discord_limit_requests_webhook'
    order by secret.created_at desc
    limit 1
  ),
  true
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claims', '', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config(
  'lim_test.app',
  public.developer_create_app(
    'Lim App pgtap',
    '',
    '',
    '',
    array['https://lim.example/callback'],
    array['profile:read', 'messages:read'],
    'public'
  ) -> 'app' ->> 'client_id',
  true
);

select extensions.is(
  public.developer_list_apps() -> 'items' -> 0 -> 'limits',
  '{"user_per_minute":120,"app_per_minute":600,"app_per_second":100,"realtime_connections":null}'::jsonb,
  'a new app carries the default limits'
);

select extensions.is(
  public.developer_list_apps() -> 'items' -> 0 -> 'pending_limit_request',
  'false'::jsonb,
  'a new app has no pending limit request'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":60,"app_per_minute":600,"app_per_second":100}', 'We need less for some reason here')$$,
    current_setting('lim_test.app')
  ),
  '22023',
  'Requested limits cannot be lower than the current ones',
  'limits below the current ones are refused'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":120,"app_per_minute":600,"app_per_second":100}', 'Nothing changes in this request at all')$$,
    current_setting('lim_test.app')
  ),
  'PT400',
  'Ask for at least one limit above the current one',
  'unchanged limits are refused'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":600,"app_per_minute":600,"app_per_second":100}', 'too short')$$,
    current_setting('lim_test.app')
  ),
  '22023',
  'Reason must be 20 to 1000 characters',
  'a short reason is refused'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":"lots","app_per_minute":600,"app_per_second":100}', 'The app is growing quickly this month')$$,
    current_setting('lim_test.app')
  ),
  '22023',
  'user_per_minute must be a whole number between 1 and 1000000',
  'non-numeric limits are refused'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":600,"app_per_minute":600,"app_per_second":100,"bogus":1}', 'The app is growing quickly this month')$$,
    current_setting('lim_test.app')
  ),
  '22023',
  'Unknown limit: bogus',
  'unknown limit keys are refused'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":600,"app_per_minute":600,"app_per_second":2000000}', 'The app is growing quickly this month')$$,
    current_setting('lim_test.app')
  ),
  '22023',
  'app_per_second must be a whole number between 1 and 1000000',
  'limits above one million are refused'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":600,"app_per_minute":6000}', 'The app is growing quickly this month')$$,
    current_setting('lim_test.app')
  ),
  '22023',
  'app_per_second is required',
  'every request limit is required'
);

select pg_catalog.set_config(
  'lim_test.request',
  public.developer_request_limits(
    current_setting('lim_test.app')::uuid,
    '{"user_per_minute":600,"app_per_minute":6000,"app_per_second":300,"realtime_connections":500}',
    'Cocoon has 2000 daily users and the chat view polls every five seconds.'
  )::text,
  true
);

select extensions.is(
  current_setting('lim_test.request')::jsonb -> 'request' ->> 'status',
  'pending',
  'a limit request starts pending'
);

select extensions.is(
  current_setting('lim_test.request')::jsonb -> 'request' -> 'requested_limits',
  '{"user_per_minute":600,"app_per_minute":6000,"app_per_second":300,"realtime_connections":500}'::jsonb,
  'the requested limits are stored normalised'
);

select extensions.is(
  current_setting('lim_test.request')::jsonb -> 'request' -> 'current_limits',
  '{"user_per_minute":120,"app_per_minute":600,"app_per_second":100,"realtime_connections":null}'::jsonb,
  'the current limits are snapshotted'
);

select extensions.is(
  current_setting('lim_test.request')::jsonb -> 'request' ->> 'app_name',
  'Lim App pgtap',
  'the request carries the app name'
);

select extensions.is(
  (
    select array_agg(keys.key order by keys.key collate "C")
    from jsonb_object_keys(current_setting('lim_test.request')::jsonb -> 'request') as keys(key)
  ),
  array[
    'app_name',
    'client_id',
    'created_at',
    'current_limits',
    'granted_limits',
    'id',
    'owner_display_name',
    'owner_user_id',
    'reason',
    'requested_limits',
    'resolution_note',
    'resolved_at',
    'status'
  ]::text[],
  'the request projection has the documented keys'
);

select extensions.is(
  public.developer_list_apps() -> 'items' -> 0 -> 'pending_limit_request',
  'true'::jsonb,
  'the app reports a pending limit request'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":700,"app_per_minute":6000,"app_per_second":300}', 'Another request while one is still pending')$$,
    current_setting('lim_test.app')
  ),
  'PT409',
  'A request for this app is already waiting for review',
  'only one request may be pending per app'
);

select extensions.is(
  jsonb_array_length(public.developer_list_limit_requests(current_setting('lim_test.app')::uuid) -> 'items'),
  1,
  'the developer sees their request'
);

select extensions.is(
  public.developer_list_limit_requests(current_setting('lim_test.app')::uuid) -> 'items' -> 0 ->> 'id',
  current_setting('lim_test.request')::jsonb -> 'request' ->> 'id',
  'the listed request is the one just sent'
);

reset role;

select extensions.is(
  (
    select count(*)
    from net.http_request_queue as queued
    where queued.url = current_setting('lim_test.webhook')
  ),
  1::bigint,
  'a new request queues one Discord webhook call'
);

select extensions.ok(
  (
    select convert_from(queued.body, 'utf8') like '%Limit request%'
      and convert_from(queued.body, 'utf8') like '%Lim App pgtap%'
      and convert_from(queued.body, 'utf8') like '%Cocoon has 2000 daily users%'
      and convert_from(queued.body, 'utf8') like '%admin.pocketpass.xyz%'
      and convert_from(queued.body, 'utf8') like '%"allowed_mentions"%'
    from net.http_request_queue as queued
    where queued.url = current_setting('lim_test.webhook')
    order by queued.id desc
    limit 1
  ),
  'the Discord message names the app, the reason and the console'
);

select extensions.is(
  (
    select audit.action
    from private.developer_audit as audit
    where audit.client_id = current_setting('lim_test.app')::uuid
    order by audit.id desc
    limit 1
  ),
  'limit_request',
  'the request is audited'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000002', true);

select extensions.throws_ok(
  format($$select public.developer_list_limit_requests(%L)$$, current_setting('lim_test.app')),
  'PT404',
  'App not found',
  'another developer cannot list the requests'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":600,"app_per_minute":6000,"app_per_second":300}', 'Trying to ask for limits on someone else app')$$,
    current_setting('lim_test.app')
  ),
  'PT404',
  'App not found',
  'another developer cannot request limits'
);

select extensions.throws_ok(
  $$select public.admin_list_limit_requests('pending', 50, 0)$$,
  '42501',
  'Admin access required',
  'a non-admin cannot list limit requests'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000006', true);

select extensions.throws_ok(
  $$select public.admin_list_limit_requests('pending', 50, 0)$$,
  '42501',
  'Permission required: apps',
  'an admin without the apps permission cannot list limit requests'
);

select extensions.throws_ok(
  format(
    $$select public.admin_resolve_limit_request(%L, true, '', null)$$,
    current_setting('lim_test.request')::jsonb -> 'request' ->> 'id'
  ),
  '42501',
  'Permission required: apps',
  'an admin without the apps permission cannot resolve requests'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000005', true);

select pg_catalog.set_config(
  'lim_test.admin_list',
  public.admin_list_limit_requests('pending', 50, 0)::text,
  true
);

select extensions.is(
  (current_setting('lim_test.admin_list')::jsonb ->> 'total_count')::integer,
  1,
  'the admin list counts the pending request'
);

select extensions.is(
  current_setting('lim_test.admin_list')::jsonb -> 'items' -> 0 ->> 'owner_display_name',
  'Lim Owner pgtap',
  'the admin list names the developer'
);

select extensions.is(
  jsonb_array_length(public.admin_list_limit_requests('approved', 50, 0) -> 'items'),
  0,
  'nothing is approved yet'
);

select extensions.throws_ok(
  $$select public.admin_list_limit_requests('bogus', 50, 0)$$,
  '22023',
  'p_status must be pending, approved, denied or all',
  'unknown statuses are refused'
);

select extensions.throws_ok(
  $$select public.admin_resolve_limit_request('99500000-0000-4000-8000-0000000000ff', true, '', null)$$,
  'PT404',
  'Limit request not found',
  'resolving an unknown request is not found'
);

select extensions.throws_ok(
  format(
    $$select public.admin_resolve_limit_request(%L, true, '', '{"user_per_minute":0,"app_per_minute":6000,"app_per_second":300}')$$,
    current_setting('lim_test.request')::jsonb -> 'request' ->> 'id'
  ),
  '22023',
  'user_per_minute must be a whole number between 1 and 1000000',
  'granted limits are validated'
);

select pg_catalog.set_config(
  'lim_test.approved',
  public.admin_resolve_limit_request(
    (current_setting('lim_test.request')::jsonb -> 'request' ->> 'id')::uuid,
    true,
    'Granted half for now, ask again when you pass 5000 users.',
    '{"user_per_minute":300,"app_per_minute":3000,"app_per_second":200,"realtime_connections":null}'
  )::text,
  true
);

select extensions.is(
  current_setting('lim_test.approved')::jsonb -> 'request' ->> 'status',
  'approved',
  'the admin approves the request'
);

select extensions.is(
  current_setting('lim_test.approved')::jsonb -> 'request' -> 'granted_limits',
  '{"user_per_minute":300,"app_per_minute":3000,"app_per_second":200,"realtime_connections":null}'::jsonb,
  'the granted limits are the adjusted ones'
);

select extensions.is(
  current_setting('lim_test.approved')::jsonb -> 'request' ->> 'resolution_note',
  'Granted half for now, ask again when you pass 5000 users.',
  'the note is stored'
);

select extensions.ok(
  (current_setting('lim_test.approved')::jsonb -> 'request' ->> 'resolved_at') is not null,
  'an approved request carries resolved_at'
);

select extensions.throws_ok(
  format(
    $$select public.admin_resolve_limit_request(%L, false, '', null)$$,
    current_setting('lim_test.request')::jsonb -> 'request' ->> 'id'
  ),
  'PT409',
  'This request was already resolved',
  'a resolved request cannot be resolved again'
);

select extensions.is(
  jsonb_array_length(public.admin_list_limit_requests('pending', 50, 0) -> 'items'),
  0,
  'the pending list is empty after approval'
);

select extensions.is(
  jsonb_array_length(public.admin_list_limit_requests('all', 50, 0) -> 'items'),
  1,
  'the all filter lists every request'
);

reset role;

select extensions.is(
  (
    select jsonb_build_object(
      'user', app.user_rate_limit_per_minute,
      'app', app.rate_limit_per_minute,
      'second', app.rate_limit_per_second,
      'realtime', app.realtime_connection_limit
    )
    from private.developer_apps as app
    where app.client_id = current_setting('lim_test.app')::uuid
  ),
  '{"user":300,"app":3000,"second":200,"realtime":null}'::jsonb,
  'approval applies the granted limits to the app'
);

select extensions.is(
  (
    select count(*)
    from net.http_request_queue as queued
    where queued.url = current_setting('lim_test.webhook')
  ),
  2::bigint,
  'the approval queues a second Discord webhook call'
);

select extensions.ok(
  (
    select convert_from(queued.body, 'utf8') like '%Approved%'
      and convert_from(queued.body, 'utf8') like '%Lim Admin pgtap%'
      and convert_from(queued.body, 'utf8') like '%Granted half for now%'
    from net.http_request_queue as queued
    where queued.url = current_setting('lim_test.webhook')
    order by queued.id desc
    limit 1
  ),
  'the approval message names the admin and the note'
);

select extensions.is(
  (
    select audit.payload ->> 'approved'
    from private.admin_audit as audit
    where audit.admin_id = '99500000-0000-4000-8000-000000000005'
      and audit.action = 'resolve_limit_request'
    order by audit.id desc
    limit 1
  ),
  'true',
  'the decision is in the admin audit log'
);

select extensions.is(
  (
    select audit.action
    from private.developer_audit as audit
    where audit.client_id = current_setting('lim_test.app')::uuid
    order by audit.id desc
    limit 1
  ),
  'limit_request_approved',
  'the decision is in the developer audit'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000001', true);

select extensions.is(
  public.developer_list_apps() -> 'items' -> 0 -> 'limits',
  '{"user_per_minute":300,"app_per_minute":3000,"app_per_second":200,"realtime_connections":null}'::jsonb,
  'the developer sees the new limits'
);

select extensions.is(
  public.developer_list_limit_requests(current_setting('lim_test.app')::uuid) -> 'items' -> 0 ->> 'status',
  'approved',
  'the developer sees the decision'
);

select extensions.throws_ok(
  format(
    $$select public.developer_request_limits(%L, '{"user_per_minute":200,"app_per_minute":3000,"app_per_second":200}', 'Asking for less than the new grant is not allowed')$$,
    current_setting('lim_test.app')
  ),
  '22023',
  'Requested limits cannot be lower than the current ones',
  'the new limits are the floor for the next request'
);

select pg_catalog.set_config(
  'lim_test.second',
  public.developer_request_limits(
    current_setting('lim_test.app')::uuid,
    '{"user_per_minute":300,"app_per_minute":3000,"app_per_second":200,"realtime_connections":800}',
    'We only need more Realtime capacity for the launch weekend.'
  )::text,
  true
);

select extensions.is(
  current_setting('lim_test.second')::jsonb -> 'request' -> 'requested_limits' ->> 'realtime_connections',
  '800',
  'a request may raise only the Realtime number'
);

select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000005', true);

select pg_catalog.set_config(
  'lim_test.denied',
  public.admin_resolve_limit_request(
    (current_setting('lim_test.second')::jsonb -> 'request' ->> 'id')::uuid,
    false,
    'Not before the shared cap is raised.',
    null
  )::text,
  true
);

select extensions.is(
  current_setting('lim_test.denied')::jsonb -> 'request' ->> 'status',
  'denied',
  'the admin denies the second request'
);

select extensions.is(
  current_setting('lim_test.denied')::jsonb -> 'request' -> 'granted_limits',
  'null'::jsonb,
  'a denied request grants nothing'
);

reset role;

select extensions.is(
  (
    select app.realtime_connection_limit
    from private.developer_apps as app
    where app.client_id = current_setting('lim_test.app')::uuid
  ),
  null::integer,
  'a denial leaves the app limits alone'
);

select extensions.ok(
  (
    select convert_from(queued.body, 'utf8') like '%Denied%'
      and convert_from(queued.body, 'utf8') like '%Not before the shared cap%'
    from net.http_request_queue as queued
    where queued.url = current_setting('lim_test.webhook')
    order by queued.id desc
    limit 1
  ),
  'the denial message carries the note'
);

update private.developer_limit_requests
set created_at = created_at - interval '1 minute'
where client_id = current_setting('lim_test.app')::uuid
  and status <> 'denied';

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000001', true);

select extensions.is(
  jsonb_array_length(public.developer_list_limit_requests(current_setting('lim_test.app')::uuid) -> 'items'),
  2,
  'the developer sees both requests'
);

select extensions.is(
  public.developer_list_limit_requests(current_setting('lim_test.app')::uuid) -> 'items' -> 0 ->> 'status',
  'denied',
  'the newest request is listed first'
);

reset role;

delete from vault.secrets where name = 'discord_limit_requests_webhook';

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config(
  'lim_test.third',
  public.developer_request_limits(
    current_setting('lim_test.app')::uuid,
    '{"user_per_minute":400,"app_per_minute":3000,"app_per_second":200}',
    'A request that must work even without a webhook configured.'
  )::text,
  true
);

select extensions.is(
  current_setting('lim_test.third')::jsonb -> 'request' ->> 'status',
  'pending',
  'requests work without a webhook secret'
);

reset role;

select extensions.is(
  (
    select count(*)
    from net.http_request_queue as queued
    where queued.url = current_setting('lim_test.webhook')
  ),
  4::bigint,
  'no webhook call is queued without a secret'
);

insert into auth.oauth_consents (id, user_id, client_id, scopes, granted_at)
values (gen_random_uuid(), '99500000-0000-4000-8000-000000000002', current_setting('lim_test.app')::uuid, 'openid', now());

set local role api_client;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claim.role', '', true);
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99500000-0000-4000-8000-000000000002',
    'role', 'api_client',
    'client_id', current_setting('lim_test.app'),
    'aud', 'authenticated'
  )::text,
  true
);

select pg_catalog.set_config('lim_test.session', public.api_v1_session_get('{}'::jsonb)::text, true);

select extensions.is(
  current_setting('lim_test.session')::jsonb -> 'rate_limit' ->> 'per_minute',
  '300',
  'session.get reports the granted per-user limit'
);

select extensions.is(
  current_setting('lim_test.session')::jsonb -> 'rate_limit' ->> 'app_per_minute',
  '3000',
  'session.get reports the granted per-app limit'
);

select extensions.is(
  current_setting('lim_test.session')::jsonb -> 'rate_limit' ->> 'per_second',
  '200',
  'session.get reports the granted burst limit'
);

select extensions.is(
  (current_setting('lim_test.session')::jsonb -> 'rate_limit' ->> 'remaining')::integer,
  299,
  'remaining counts down from the granted per-user limit'
);

reset role;

insert into private.api_rate_buckets_app_second (client_id, bucket, requests)
select
  current_setting('lim_test.app')::uuid,
  date_trunc('second', clock_timestamp()) + make_interval(secs => offsets.n),
  1000
from generate_series(0, 10) as offsets(n)
on conflict (client_id, bucket, shard) do update set requests = 1000;

set local role api_client;
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99500000-0000-4000-8000-000000000002',
    'role', 'api_client',
    'client_id', current_setting('lim_test.app'),
    'aud', 'authenticated'
  )::text,
  true
);

select pg_catalog.set_config('lim_test.burst', public.api_v1_session_get('{}'::jsonb)::text, true);

select extensions.is(
  current_setting('lim_test.burst')::jsonb ->> 'code',
  'PT429',
  'exceeding the per-second burst is rate limited'
);

select extensions.is(
  current_setting('lim_test.burst')::jsonb ->> 'hint',
  'API_RATE_LIMITED',
  'the burst denial carries the rate-limit hint'
);

select extensions.is(
  current_setting('lim_test.burst')::jsonb ->> 'message',
  'Rate limit exceeded, retry after 1s',
  'the burst denial says to retry after a second'
);

select extensions.ok(
  current_setting('response.headers', true) like '%"Retry-After"%: %"1"%',
  'the burst denial sets Retry-After: 1'
);

reset role;

delete from private.api_rate_buckets_app_second
where client_id = current_setting('lim_test.app')::uuid;

set local role api_client;
select pg_catalog.set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', '99500000-0000-4000-8000-000000000002',
    'role', 'api_client',
    'client_id', current_setting('lim_test.app'),
    'aud', 'authenticated'
  )::text,
  true
);

select extensions.is(
  public.api_v1_session_get('{}'::jsonb) ->> 'client_id',
  current_setting('lim_test.app'),
  'calls succeed again once the burst window passes'
);

reset role;

select extensions.ok(
  (
    select stat.denied >= 1
    from private.api_usage as stat
    where stat.client_id = current_setting('lim_test.app')::uuid
      and stat.user_id = '99500000-0000-4000-8000-000000000002'
      and stat.day = (now() at time zone 'utc')::date
  ),
  'the burst denial is counted as denied usage'
);

insert into private.api_rate_buckets_app_second (client_id, bucket, requests)
values (current_setting('lim_test.app')::uuid, now() - interval '2 hours', 7);

select private.prune_api_rate_buckets();

select extensions.is(
  (
    select count(*)
    from private.api_rate_buckets_app_second as bucket
    where bucket.client_id = current_setting('lim_test.app')::uuid
      and bucket.bucket < now() - interval '1 hour'
  ),
  0::bigint,
  'pruning removes old per-second buckets'
);

select extensions.ok(
  not pg_catalog.has_table_privilege('authenticated', 'private.developer_limit_requests', 'select')
    and not pg_catalog.has_table_privilege('anon', 'private.developer_limit_requests', 'select')
    and not pg_catalog.has_table_privilege('api_client', 'private.developer_limit_requests', 'select'),
  'limit requests are reachable only through the RPCs'
);

select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.developer_request_limits(uuid, jsonb, text)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'public.admin_resolve_limit_request(uuid, boolean, text, jsonb)', 'execute')
    and not pg_catalog.has_function_privilege('api_client', 'public.developer_request_limits(uuid, jsonb, text)', 'execute')
    and not pg_catalog.has_function_privilege('api_client', 'public.admin_resolve_limit_request(uuid, boolean, text, jsonb)', 'execute'),
  'anon and connected apps cannot use the limit RPCs'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99500000-0000-4000-8000-000000000001', true);

select public.developer_delete_app(current_setting('lim_test.app')::uuid);

reset role;

select extensions.is(
  (
    select count(*)
    from private.developer_limit_requests as request
    where request.client_id = current_setting('lim_test.app')::uuid
  ),
  0::bigint,
  'deleting the app removes its limit requests'
);

select * from extensions.finish();

rollback;
