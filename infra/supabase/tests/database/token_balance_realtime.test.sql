begin;

set local search_path = public, extensions;

select extensions.plan(8);

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
    '99500000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'ivy@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Ivy"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '99500000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'jon@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Jon"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

select extensions.is(
  private.token_user_id_from_topic('tokens:99500000-0000-4000-8000-000000000001'),
  '99500000-0000-4000-8000-000000000001'::uuid,
  'the tokens topic carries the owner id'
);

select extensions.is(
  private.token_user_id_from_topic('tokens:99500000-0000-4000-8000-000000000001:spoofed'),
  null::uuid,
  'malformed tokens topics do not resolve'
);

select extensions.ok(
  private.can_access_realtime_topic(
    'tokens:99500000-0000-4000-8000-000000000001',
    '99500000-0000-4000-8000-000000000001'
  ),
  'a user may read their own tokens topic'
);

select extensions.ok(
  not private.can_access_realtime_topic(
    'tokens:99500000-0000-4000-8000-000000000001',
    '99500000-0000-4000-8000-000000000002'
  ),
  'another user may not read it'
);

select extensions.ok(
  private.can_access_realtime_topic(
    'notifications:99500000-0000-4000-8000-000000000001',
    '99500000-0000-4000-8000-000000000001'
  ),
  'existing notification topics still authorize'
);

select extensions.ok(
  private.can_access_realtime_topic(
    'friends:99500000-0000-4000-8000-000000000001',
    '99500000-0000-4000-8000-000000000001'
  ),
  'existing friends topics still authorize'
);

select extensions.has_trigger(
  'public',
  'token_balances',
  'token_balances_broadcast_change',
  'balance changes broadcast to the tokens topic'
);

select extensions.lives_ok(
  $$update public.token_balances set balance = balance + 5, updated_at = now()
    where user_id = '99500000-0000-4000-8000-000000000001'$$,
  'updating a balance runs the broadcast trigger cleanly'
);

select * from extensions.finish();

rollback;
