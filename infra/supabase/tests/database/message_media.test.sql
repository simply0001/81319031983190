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
    '98300000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'media-sender@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Media Sender"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98300000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'media-peer@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Media Peer"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98300000-0000-4000-8000-000000000003',
    'authenticated',
    'authenticated',
    'media-stranger@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Media Stranger"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

insert into public.conversations (
  id,
  kind,
  created_by,
  direct_user_low,
  direct_user_high
)
values (
  '98310000-0000-4000-8000-000000000001',
  'direct',
  '98300000-0000-4000-8000-000000000001',
  least(
    '98300000-0000-4000-8000-000000000001'::uuid,
    '98300000-0000-4000-8000-000000000002'::uuid
  ),
  greatest(
    '98300000-0000-4000-8000-000000000001'::uuid,
    '98300000-0000-4000-8000-000000000002'::uuid
  )
);

insert into public.conversation_members (conversation_id, user_id, role)
values
  (
    '98310000-0000-4000-8000-000000000001',
    '98300000-0000-4000-8000-000000000001',
    'owner'
  ),
  (
    '98310000-0000-4000-8000-000000000001',
    '98300000-0000-4000-8000-000000000002',
    'member'
  );

select extensions.is(
  private.message_media_conversation_id(
    '98300000-0000-4000-8000-000000000001/'
    || '98310000-0000-4000-8000-000000000001/photo.png'
  ),
  '98310000-0000-4000-8000-000000000001'::uuid,
  'the conversation id is read from the second path segment'
);

select extensions.is(
  private.message_media_conversation_id('not-a-uuid/also-not/photo.png'),
  null::uuid,
  'a malformed path yields no conversation id'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98300000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.lives_ok(
  $$
    insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
    values (
      'message-media',
      '98300000-0000-4000-8000-000000000001/'
        || '98310000-0000-4000-8000-000000000001/photo.png',
      '98300000-0000-4000-8000-000000000001',
      '98300000-0000-4000-8000-000000000001',
      '{"mimetype":"image/png","size":2048}'
    );
  $$,
  'a member uploads into their own folder for a conversation they belong to'
);

select extensions.throws_ok(
  $$
    insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
    values (
      'message-media',
      '98300000-0000-4000-8000-000000000002/'
        || '98310000-0000-4000-8000-000000000001/spoofed.png',
      '98300000-0000-4000-8000-000000000001',
      '98300000-0000-4000-8000-000000000001',
      '{"mimetype":"image/png","size":2048}'
    );
  $$,
  '42501',
  'new row violates row-level security policy for table "objects"',
  'uploading into another user''s folder is rejected'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98300000-0000-4000-8000-000000000002',
  true
);

select extensions.is(
  (
    select count(*)
    from storage.objects
    where bucket_id = 'message-media'
  ),
  1::bigint,
  'the other conversation member can read the attachment'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98300000-0000-4000-8000-000000000003',
  true
);

select extensions.is(
  (
    select count(*)
    from storage.objects
    where bucket_id = 'message-media'
  ),
  0::bigint,
  'a non-member cannot read the attachment'
);

reset role;

select * from extensions.finish();

rollback;
