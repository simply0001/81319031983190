begin;

set local search_path = public, extensions;

select extensions.plan(26);

select extensions.ok(
  pg_catalog.has_function_privilege('authenticated', 'public.edit_message(uuid, text)', 'execute'),
  'authenticated can execute edit_message'
);
select extensions.ok(
  pg_catalog.has_function_privilege('authenticated', 'public.delete_message(uuid)', 'execute'),
  'authenticated can execute delete_message'
);
select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.edit_message(uuid, text)', 'execute'),
  'anon cannot execute edit_message'
);
select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.delete_message(uuid)', 'execute'),
  'anon cannot execute delete_message'
);
select extensions.ok(
  not pg_catalog.has_table_privilege('authenticated', 'public.messages', 'update'),
  'messages cannot be updated directly by clients'
);
select extensions.ok(
  not pg_catalog.has_table_privilege('authenticated', 'public.messages', 'delete'),
  'messages cannot be deleted directly by clients'
);

select extensions.throws_ok(
  $$select public.edit_message('98720000-0000-4000-8000-000000000001', 'x')$$,
  '42501',
  'Authentication required',
  'edit_message rejects an unauthenticated caller'
);
select extensions.throws_ok(
  $$select public.delete_message('98720000-0000-4000-8000-000000000001')$$,
  '42501',
  'Authentication required',
  'delete_message rejects an unauthenticated caller'
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
    '98700000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'edit-owner@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Edit Owner"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98700000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'edit-peer@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Edit Peer"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98700000-0000-4000-8000-000000000003',
    'authenticated',
    'authenticated',
    'edit-stranger@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Edit Stranger"}',
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
  '98710000-0000-4000-8000-000000000001',
  'direct',
  '98700000-0000-4000-8000-000000000001',
  least(
    '98700000-0000-4000-8000-000000000001'::uuid,
    '98700000-0000-4000-8000-000000000002'::uuid
  ),
  greatest(
    '98700000-0000-4000-8000-000000000001'::uuid,
    '98700000-0000-4000-8000-000000000002'::uuid
  )
);

insert into public.conversation_members (conversation_id, user_id, role)
values
  (
    '98710000-0000-4000-8000-000000000001',
    '98700000-0000-4000-8000-000000000001',
    'owner'
  ),
  (
    '98710000-0000-4000-8000-000000000001',
    '98700000-0000-4000-8000-000000000002',
    'member'
  );

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select public.send_message(
  '98720000-0000-4000-8000-000000000001',
  '98710000-0000-4000-8000-000000000001',
  '98730000-0000-4000-8000-000000000001',
  'Original body'
);

reset role;
alter table public.conversations disable trigger conversations_set_updated_at;
update public.conversations
set updated_at = now() - interval '1 hour'
where id = '98710000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    public.edit_message(
      '98720000-0000-4000-8000-000000000001',
      'Edited body'
    )
  ).body,
  'Edited body',
  'the sender can edit their own message'
);

select extensions.ok(
  (
    select edited_at is not null and edited_at >= created_at
    from public.messages
    where id = '98720000-0000-4000-8000-000000000001'
  ),
  'editing stamps edited_at'
);

select extensions.throws_ok(
  $$select public.edit_message('98720000-0000-4000-8000-000000000001', '   ')$$,
  '22023',
  'Message body must contain 1 to 4000 characters',
  'a blank body is rejected'
);

select extensions.throws_ok(
  $$
    select public.edit_message(
      '98720000-0000-4000-8000-000000000001',
      repeat('x', 4001)
    )
  $$,
  '22023',
  'Message body must contain 1 to 4000 characters',
  'an oversized body is rejected'
);

reset role;

select extensions.is(
  (
    select body
    from public.notifications
    where kind = 'message'
      and recipient_id = '98700000-0000-4000-8000-000000000002'
  ),
  'Edited body',
  'editing the message behind the inbox preview refreshes the preview'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);

select public.send_message(
  '98720000-0000-4000-8000-000000000002',
  '98710000-0000-4000-8000-000000000001',
  '98730000-0000-4000-8000-000000000002',
  'Photo',
  null,
  '{"attachment":{"path":"98700000-0000-4000-8000-000000000001/98710000-0000-4000-8000-000000000001/photo.png","mime_type":"image/png"}}'::jsonb
);

reset role;
update public.conversations
set updated_at = now() - interval '1 hour'
where id = '98710000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    public.edit_message(
      '98720000-0000-4000-8000-000000000002',
      'Photo'
    )
  ).edited_at,
  null::timestamptz,
  'an unchanged body does not stamp edited_at'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000002',
  true
);

select extensions.throws_ok(
  $$select public.edit_message('98720000-0000-4000-8000-000000000001', 'Hijacked')$$,
  '42501',
  'Only the sender can edit this message',
  'another member cannot edit the message'
);

select extensions.throws_ok(
  $$select public.delete_message('98720000-0000-4000-8000-000000000001')$$,
  '42501',
  'Only the sender can delete this message',
  'another member cannot delete the message'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000003',
  true
);

select extensions.throws_ok(
  $$select public.delete_message('98720000-0000-4000-8000-000000000001')$$,
  '42501',
  'Only the sender can delete this message',
  'a non-member gets the same error and cannot probe message ids'
);

reset role;
set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);

select extensions.ok(
  (
    public.delete_message('98720000-0000-4000-8000-000000000002')
  ).deleted_at is not null,
  'the sender can soft delete their own message'
);

select extensions.is(
  (
    select body
    from public.messages
    where id = '98720000-0000-4000-8000-000000000002'
  ),
  'Message deleted',
  'deleting scrubs the body to the placeholder'
);

select extensions.is(
  (
    select metadata
    from public.messages
    where id = '98720000-0000-4000-8000-000000000002'
  ),
  '{}'::jsonb,
  'deleting clears the attachment metadata'
);

select extensions.throws_ok(
  $$select public.edit_message('98720000-0000-4000-8000-000000000002', 'Too late')$$,
  '22023',
  'Message has been deleted',
  'a deleted message cannot be edited'
);

reset role;

select extensions.is(
  (
    select body
    from public.notifications
    where kind = 'message'
      and recipient_id = '98700000-0000-4000-8000-000000000002'
  ),
  'Message deleted',
  'deleting the message behind the inbox preview scrubs the preview'
);

update public.messages
set
  created_at = now() - interval '2 minutes',
  deleted_at = now() - interval '1 minute'
where id = '98720000-0000-4000-8000-000000000002';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    public.delete_message('98720000-0000-4000-8000-000000000002')
  ).deleted_at,
  now() - interval '1 minute',
  'repeating a delete keeps the original deleted_at'
);

reset role;
update public.conversation_members
set left_at = now()
where conversation_id = '98710000-0000-4000-8000-000000000001'
  and user_id = '98700000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98700000-0000-4000-8000-000000000001',
  true
);

select extensions.throws_ok(
  $$select public.edit_message('98720000-0000-4000-8000-000000000001', 'After leaving')$$,
  '42501',
  'Active conversation membership is required',
  'a sender who left the conversation cannot edit'
);

select extensions.ok(
  (
    public.delete_message('98720000-0000-4000-8000-000000000001')
  ).deleted_at is not null,
  'a sender who left the conversation can still delete'
);

reset role;

select extensions.is(
  (
    select updated_at
    from public.conversations
    where id = '98710000-0000-4000-8000-000000000001'
  ),
  now() - interval '1 hour',
  'edit and delete never bump conversations.updated_at'
);

select * from extensions.finish();

rollback;
