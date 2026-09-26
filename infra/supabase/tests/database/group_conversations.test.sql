begin;

set local search_path = public, extensions;

select extensions.plan(68);

select extensions.ok(
  pg_catalog.has_function_privilege('authenticated', 'public.create_group_conversation(text, uuid[], uuid)', 'execute'),
  'authenticated can execute create_group_conversation'
);
select extensions.ok(
  pg_catalog.has_function_privilege('authenticated', 'public.add_group_members(uuid, uuid[], uuid)', 'execute'),
  'authenticated can execute add_group_members'
);
select extensions.ok(
  pg_catalog.has_function_privilege('authenticated', 'public.remove_group_member(uuid, uuid, uuid)', 'execute'),
  'authenticated can execute remove_group_member'
);
select extensions.ok(
  pg_catalog.has_function_privilege('authenticated', 'public.leave_group_conversation(uuid, uuid)', 'execute'),
  'authenticated can execute leave_group_conversation'
);
select extensions.ok(
  pg_catalog.has_function_privilege('authenticated', 'public.rename_group_conversation(uuid, text, uuid)', 'execute'),
  'authenticated can execute rename_group_conversation'
);
select extensions.ok(
  not pg_catalog.has_function_privilege('anon', 'public.create_group_conversation(text, uuid[], uuid)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'public.add_group_members(uuid, uuid[], uuid)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'public.remove_group_member(uuid, uuid, uuid)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'public.leave_group_conversation(uuid, uuid)', 'execute')
    and not pg_catalog.has_function_privilege('anon', 'public.rename_group_conversation(uuid, text, uuid)', 'execute'),
  'anon cannot execute the group RPCs'
);

select extensions.throws_ok(
  $$select public.create_group_conversation('Trip', array['98900000-0000-4000-8000-000000000002']::uuid[], '98930000-0000-4000-8000-000000000000')$$,
  '42501',
  'Authentication required',
  'create_group_conversation rejects an unauthenticated caller'
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
    ('98900000-0000-4000-8000-000000000001'::uuid, 'group-alice@pocketpass.test', 'Group Alice'),
    ('98900000-0000-4000-8000-000000000002'::uuid, 'group-bob@pocketpass.test', 'Group Bob'),
    ('98900000-0000-4000-8000-000000000003'::uuid, 'group-carol@pocketpass.test', 'Group Carol'),
    ('98900000-0000-4000-8000-000000000004'::uuid, 'group-dave@pocketpass.test', 'Group Dave'),
    ('98900000-0000-4000-8000-000000000005'::uuid, 'group-erin@pocketpass.test', 'Group Erin'),
    ('98900000-0000-4000-8000-000000000006'::uuid, 'group-frank@pocketpass.test', 'Group Frank')
  union all
  select
    ('98900000-0000-4000-8000-0000000000' || lpad(extra::text, 2, '0'))::uuid,
    'group-extra-' || extra::text || '@pocketpass.test',
    'Group Extra ' || extra::text
  from generate_series(7, 26) as extra
) as seed(id, email, name);

insert into public.friendships (user_low, user_high, created_by)
values
  ('98900000-0000-4000-8000-000000000001', '98900000-0000-4000-8000-000000000002', '98900000-0000-4000-8000-000000000001'),
  ('98900000-0000-4000-8000-000000000001', '98900000-0000-4000-8000-000000000003', '98900000-0000-4000-8000-000000000001'),
  ('98900000-0000-4000-8000-000000000002', '98900000-0000-4000-8000-000000000005', '98900000-0000-4000-8000-000000000002'),
  ('98900000-0000-4000-8000-000000000002', '98900000-0000-4000-8000-000000000006', '98900000-0000-4000-8000-000000000002');

insert into public.friendships (user_low, user_high, created_by)
select
  '98900000-0000-4000-8000-000000000001',
  ('98900000-0000-4000-8000-0000000000' || lpad(extra::text, 2, '0'))::uuid,
  '98900000-0000-4000-8000-000000000001'
from generate_series(7, 26) as extra;

insert into public.user_blocks (blocker_id, blocked_id)
values ('98900000-0000-4000-8000-000000000001', '98900000-0000-4000-8000-000000000005');

insert into public.conversations (id, kind, created_by, direct_user_low, direct_user_high)
values
  (
    '98910000-0000-4000-8000-000000000001',
    'direct',
    '98900000-0000-4000-8000-000000000001',
    '98900000-0000-4000-8000-000000000001',
    '98900000-0000-4000-8000-000000000002'
  ),
  (
    '98910000-0000-4000-8000-000000000002',
    'direct',
    '98900000-0000-4000-8000-000000000001',
    '98900000-0000-4000-8000-000000000001',
    '98900000-0000-4000-8000-000000000005'
  );

insert into public.conversation_members (conversation_id, user_id, role)
values
  ('98910000-0000-4000-8000-000000000001', '98900000-0000-4000-8000-000000000001', 'owner'),
  ('98910000-0000-4000-8000-000000000001', '98900000-0000-4000-8000-000000000002', 'member'),
  ('98910000-0000-4000-8000-000000000002', '98900000-0000-4000-8000-000000000001', 'owner'),
  ('98910000-0000-4000-8000-000000000002', '98900000-0000-4000-8000-000000000005', 'member');

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);
select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  $$select public.create_group_conversation('   ', array['98900000-0000-4000-8000-000000000002']::uuid[], '98930000-0000-4000-8000-000000000001')$$,
  '22023',
  'Group title must contain 1 to 80 characters',
  'a blank title is rejected'
);
select extensions.throws_ok(
  $$select public.create_group_conversation(repeat('x', 81), array['98900000-0000-4000-8000-000000000002']::uuid[], '98930000-0000-4000-8000-000000000002')$$,
  '22023',
  'Group title must contain 1 to 80 characters',
  'a title over 80 characters is rejected'
);
select extensions.throws_ok(
  $$select public.create_group_conversation('Trip', array['98900000-0000-4000-8000-000000000001', '98900000-0000-4000-8000-000000000001', null]::uuid[], '98930000-0000-4000-8000-000000000003')$$,
  '22023',
  'At least one other member is required',
  'self and null member ids collapse to an empty group'
);
select extensions.throws_ok(
  $$select public.create_group_conversation('Trip', array['98900000-0000-4000-8000-000000000004']::uuid[], '98930000-0000-4000-8000-000000000004')$$,
  '42501',
  'A friendship is required',
  'strangers cannot be added to a group'
);
select extensions.throws_ok(
  $$select public.create_group_conversation('Trip', array['98900000-0000-4000-8000-000000000005']::uuid[], '98930000-0000-4000-8000-000000000005')$$,
  '42501',
  'Conversation is not allowed',
  'blocked users cannot be added to a group'
);
select extensions.throws_ok(
  $$select public.create_group_conversation('Trip', array['98900000-0000-4000-8000-0000000000ff']::uuid[], '98930000-0000-4000-8000-000000000006')$$,
  'P0002',
  'Profile not found',
  'unknown member ids are rejected'
);

select pg_catalog.set_config(
  'gc_test.group',
  (
    select conversation.id::text
    from public.create_group_conversation(
      ' Trip ',
      array[
        '98900000-0000-4000-8000-000000000002',
        '98900000-0000-4000-8000-000000000003',
        '98900000-0000-4000-8000-000000000003'
      ]::uuid[],
      '98930000-0000-4000-8000-000000000010'
    ) as conversation
  ),
  true
);

select extensions.is(
  (
    select conversation.kind::text || '|' || conversation.title
    from public.conversations as conversation
    where conversation.id = current_setting('gc_test.group')::uuid
  ),
  'group|Trip',
  'create_group_conversation stores a group with a trimmed title'
);
select extensions.is(
  (
    select count(*)
    from public.conversation_members as member
    where member.conversation_id = current_setting('gc_test.group')::uuid
      and member.left_at is null
  ),
  3::bigint,
  'the creator and both members are active'
);
select extensions.is(
  (
    select member.role::text
    from public.conversation_members as member
    where member.conversation_id = current_setting('gc_test.group')::uuid
      and member.user_id = '98900000-0000-4000-8000-000000000001'
  ),
  'owner',
  'the creator owns the group'
);
select extensions.is(
  (
    select count(*)
    from public.conversation_members as member
    where member.conversation_id = current_setting('gc_test.group')::uuid
      and member.role = 'member'
      and member.last_read_at is not null
  ),
  2::bigint,
  'new members start with the group marked read'
);
select extensions.is(
  (
    select conversation.id::text
    from public.create_group_conversation(
      'Trip',
      array[
        '98900000-0000-4000-8000-000000000003',
        '98900000-0000-4000-8000-000000000002'
      ]::uuid[],
      '98930000-0000-4000-8000-000000000010'
    ) as conversation
  ),
  current_setting('gc_test.group'),
  'replaying the operation id returns the same group regardless of member order'
);
select extensions.throws_ok(
  $$select public.create_group_conversation('Other', array['98900000-0000-4000-8000-000000000002', '98900000-0000-4000-8000-000000000003']::uuid[], '98930000-0000-4000-8000-000000000010')$$,
  '22023',
  'Client operation id was already used for another operation',
  'reusing the operation id for a different group is rejected'
);

reset role;

select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.kind = 'system'
      and notification.conversation_id = current_setting('gc_test.group')::uuid
      and notification.title = 'Added to Trip'
      and notification.body = 'Group Alice added you'
      and notification.actor_id = '98900000-0000-4000-8000-000000000001'
      and notification.recipient_id in (
        '98900000-0000-4000-8000-000000000002',
        '98900000-0000-4000-8000-000000000003'
      )
  ),
  2::bigint,
  'both members are told they were added'
);
select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.conversation_id = current_setting('gc_test.group')::uuid
      and notification.recipient_id = '98900000-0000-4000-8000-000000000001'
  ),
  0::bigint,
  'the creator gets no membership notification'
);
select extensions.cmp_ok(
  (
    select count(*)
    from realtime.messages as message
    where message.topic = 'conversation:' || current_setting('gc_test.group')
      and message.event = 'membership'
  ),
  '>=',
  3::bigint,
  'membership changes broadcast on the conversation topic'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000002', true);

select extensions.is(
  public.add_group_members(
    current_setting('gc_test.group')::uuid,
    array['98900000-0000-4000-8000-000000000006']::uuid[],
    '98930000-0000-4000-8000-000000000020'
  ),
  1,
  'a member may add their own friend'
);
select extensions.throws_ok(
  $$select public.add_group_members(current_setting('gc_test.group')::uuid, array['98900000-0000-4000-8000-000000000004']::uuid[], '98930000-0000-4000-8000-000000000021')$$,
  '42501',
  'A friendship is required',
  'a member cannot add a stranger'
);
select extensions.is(
  public.add_group_members(
    current_setting('gc_test.group')::uuid,
    array['98900000-0000-4000-8000-000000000005']::uuid[],
    '98930000-0000-4000-8000-000000000022'
  ),
  1,
  'a member may add a friend the owner has blocked'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.is(
  public.add_group_members(
    current_setting('gc_test.group')::uuid,
    array['98900000-0000-4000-8000-000000000003']::uuid[],
    '98930000-0000-4000-8000-000000000023'
  ),
  0,
  'adding an active member changes nothing'
);
select extensions.throws_ok(
  $$select public.add_group_members('98910000-0000-4000-8000-000000000001', array['98900000-0000-4000-8000-000000000003']::uuid[], '98930000-0000-4000-8000-000000000024')$$,
  '22023',
  'A group conversation is required',
  'direct conversations cannot gain members'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000004', true);

select extensions.throws_ok(
  $$select public.add_group_members(current_setting('gc_test.group')::uuid, array['98900000-0000-4000-8000-000000000002']::uuid[], '98930000-0000-4000-8000-000000000025')$$,
  '42501',
  'Active conversation membership is required',
  'non-members cannot add anyone'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.is(
  public.add_group_members(
    current_setting('gc_test.group')::uuid,
    (
      select array_agg(('98900000-0000-4000-8000-0000000000' || lpad(extra::text, 2, '0'))::uuid)
      from generate_series(7, 21) as extra
    ),
    '98930000-0000-4000-8000-000000000026'
  ),
  15,
  'the owner fills the group to the cap'
);
select extensions.is(
  (
    select count(*)
    from public.conversation_members as member
    where member.conversation_id = current_setting('gc_test.group')::uuid
      and member.left_at is null
  ),
  20::bigint,
  'twenty members are active'
);
select extensions.throws_ok(
  $$select public.add_group_members(current_setting('gc_test.group')::uuid, array['98900000-0000-4000-8000-000000000022']::uuid[], '98930000-0000-4000-8000-000000000027')$$,
  'P0001',
  'Group conversations are limited to 20 members',
  'the member cap is enforced'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000002', true);

select extensions.throws_ok(
  $$select public.remove_group_member(current_setting('gc_test.group')::uuid, '98900000-0000-4000-8000-000000000003', '98930000-0000-4000-8000-000000000030')$$,
  '42501',
  'Only the group owner may remove members',
  'members cannot remove each other'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  $$select public.remove_group_member(current_setting('gc_test.group')::uuid, '98900000-0000-4000-8000-000000000001', '98930000-0000-4000-8000-000000000031')$$,
  '22023',
  'Use leave_group_conversation to remove yourself',
  'the owner cannot remove themselves'
);
select extensions.is(
  public.remove_group_member(
    current_setting('gc_test.group')::uuid,
    '98900000-0000-4000-8000-000000000003',
    '98930000-0000-4000-8000-000000000032'
  ),
  true,
  'the owner removes a member'
);
select extensions.is(
  public.remove_group_member(
    current_setting('gc_test.group')::uuid,
    '98900000-0000-4000-8000-000000000003',
    '98930000-0000-4000-8000-000000000033'
  ),
  false,
  'removing an inactive member is a no-op'
);

select extensions.is(
  (
    select message.body
    from public.send_message(
      '98920000-0000-4000-8000-000000000001',
      current_setting('gc_test.group')::uuid,
      '98930000-0000-4000-8000-000000000040',
      'hello group'
    ) as message
  ),
  'hello group',
  'messaging works in a group that contains a blocked pair'
);
select extensions.throws_ok(
  $$select public.send_message('98920000-0000-4000-8000-000000000002', '98910000-0000-4000-8000-000000000002', '98930000-0000-4000-8000-000000000041', 'hello direct')$$,
  '42501',
  'Messaging is not allowed',
  'direct conversations still refuse blocked pairs'
);
select extensions.is(
  (
    select message.body
    from public.send_message(
      '98920000-0000-4000-8000-000000000003',
      '98910000-0000-4000-8000-000000000001',
      '98930000-0000-4000-8000-000000000042',
      'direct hi'
    ) as message
  ),
  'direct hi',
  'direct messaging to a friend is unchanged'
);

reset role;

select extensions.ok(
  exists (
    select 1
    from public.conversation_members as member
    where member.conversation_id = current_setting('gc_test.group')::uuid
      and member.user_id = '98900000-0000-4000-8000-000000000003'
      and member.left_at is not null
  ),
  'the removed member is marked as left'
);
select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.kind = 'system'
      and notification.conversation_id = current_setting('gc_test.group')::uuid
      and notification.recipient_id = '98900000-0000-4000-8000-000000000003'
      and notification.title = 'Removed from Trip'
      and notification.body = 'Group Alice removed you'
      and notification.actor_id = '98900000-0000-4000-8000-000000000001'
  ),
  1::bigint,
  'the removed member is told'
);
select extensions.is(
  (
    select notification.title || '|' || notification.body
    from public.notifications as notification
    where notification.kind = 'message'
      and notification.conversation_id = current_setting('gc_test.group')::uuid
      and notification.recipient_id = '98900000-0000-4000-8000-000000000002'
  ),
  'Trip|Group Alice: hello group',
  'group message notifications carry the group title and the sender'
);
select extensions.is(
  (
    select notification.title || '|' || notification.body
    from public.notifications as notification
    where notification.kind = 'message'
      and notification.conversation_id = '98910000-0000-4000-8000-000000000001'
      and notification.recipient_id = '98900000-0000-4000-8000-000000000002'
  ),
  'Group Alice|direct hi',
  'direct message notifications are unchanged'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000003', true);

select extensions.is(
  (
    select count(*)
    from public.conversations as conversation
    where conversation.id = current_setting('gc_test.group')::uuid
  ),
  0::bigint,
  'a removed member no longer sees the group'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.is(
  (
    select message.body
    from public.edit_message('98920000-0000-4000-8000-000000000001', 'edited group') as message
  ),
  'edited group',
  'the sender edits the group message'
);

reset role;

select extensions.is(
  (
    select notification.body
    from public.notifications as notification
    where notification.kind = 'message'
      and notification.conversation_id = current_setting('gc_test.group')::uuid
      and notification.recipient_id = '98900000-0000-4000-8000-000000000002'
  ),
  'Group Alice: edited group',
  'editing refreshes the group preview with the sender'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.is(
  public.add_group_members(
    current_setting('gc_test.group')::uuid,
    array['98900000-0000-4000-8000-000000000003']::uuid[],
    '98930000-0000-4000-8000-000000000050'
  ),
  1,
  'a removed member can be added back'
);

reset role;

select extensions.is(
  (
    select (member.left_at is null)::text || ':' || member.role::text || ':' || (member.last_read_at is not null)::text
    from public.conversation_members as member
    where member.conversation_id = current_setting('gc_test.group')::uuid
      and member.user_id = '98900000-0000-4000-8000-000000000003'
  ),
  'true:member:true',
  'the returning member is reactivated as a read member'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000003', true);

select extensions.is(
  (
    select count(*)
    from public.messages as message
    where message.conversation_id = current_setting('gc_test.group')::uuid
  ),
  1::bigint,
  'a returning member sees the full history'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000002', true);

select extensions.throws_ok(
  $$select public.rename_group_conversation(current_setting('gc_test.group')::uuid, 'Nope', '98930000-0000-4000-8000-000000000060')$$,
  '42501',
  'Only the group owner may rename the group',
  'members cannot rename the group'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.throws_ok(
  $$select public.rename_group_conversation(current_setting('gc_test.group')::uuid, '   ', '98930000-0000-4000-8000-000000000061')$$,
  '22023',
  'Group title must contain 1 to 80 characters',
  'a blank rename is rejected'
);
select extensions.is(
  (
    select conversation.title
    from public.rename_group_conversation(
      current_setting('gc_test.group')::uuid,
      ' Trip 2 ',
      '98930000-0000-4000-8000-000000000062'
    ) as conversation
  ),
  'Trip 2',
  'the owner renames the group'
);

reset role;

select extensions.cmp_ok(
  (
    select count(*)
    from realtime.messages as message
    where message.topic = 'conversation:' || current_setting('gc_test.group')
      and message.event = 'conversation'
  ),
  '>=',
  1::bigint,
  'title changes broadcast on the conversation topic'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.is(
  public.leave_group_conversation(
    current_setting('gc_test.group')::uuid,
    '98930000-0000-4000-8000-000000000070'
  ),
  true,
  'the owner leaves the group'
);

reset role;

select extensions.is(
  (
    select member.user_id::text
    from public.conversation_members as member
    where member.conversation_id = current_setting('gc_test.group')::uuid
      and member.role = 'owner'
      and member.left_at is null
  ),
  '98900000-0000-4000-8000-000000000002',
  'ownership passes to the earliest-joined member'
);
select extensions.is(
  (
    select count(*)
    from public.notifications as notification
    where notification.kind = 'system'
      and notification.conversation_id = current_setting('gc_test.group')::uuid
      and notification.recipient_id = '98900000-0000-4000-8000-000000000002'
      and notification.title = 'You now own Trip 2'
  ),
  1::bigint,
  'the new owner is told'
);
select extensions.ok(
  not private.can_access_realtime_topic(
    'conversation:' || current_setting('gc_test.group'),
    '98900000-0000-4000-8000-000000000001'
  )
  and private.can_access_realtime_topic(
    'conversation:' || current_setting('gc_test.group'),
    '98900000-0000-4000-8000-000000000006'
  ),
  'the conversation topic closes for the leaver and stays open for members'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config(
  'gc_test.duo',
  (
    select conversation.id::text
    from public.create_group_conversation(
      'Duo',
      array['98900000-0000-4000-8000-000000000002']::uuid[],
      '98930000-0000-4000-8000-000000000080'
    ) as conversation
  ),
  true
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000002', true);

select extensions.is(
  public.leave_group_conversation(
    current_setting('gc_test.duo')::uuid,
    '98930000-0000-4000-8000-000000000081'
  ),
  true,
  'a member leaves a two-person group'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.is(
  public.leave_group_conversation(
    current_setting('gc_test.duo')::uuid,
    '98930000-0000-4000-8000-000000000082'
  ),
  true,
  'the last member leaves'
);

reset role;

select extensions.is(
  (
    select count(*)
    from public.conversations as conversation
    where conversation.id = current_setting('gc_test.duo')::uuid
  ),
  0::bigint,
  'a group with no members left is deleted'
);

select extensions.has_trigger(
  'public',
  'conversation_members',
  'conversation_members_broadcast_change',
  'membership changes broadcast invalidations'
);
select extensions.has_trigger(
  'public',
  'conversations',
  'conversations_broadcast_title_change',
  'title changes broadcast invalidations'
);
select extensions.has_trigger(
  'public',
  'conversation_members',
  'conversation_members_notify_group_change',
  'membership changes create system notifications'
);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select pg_catalog.set_config(
  'gc_test.trio',
  (
    select conversation.id::text
    from public.create_group_conversation(
      'Trio',
      array[
        '98900000-0000-4000-8000-000000000002',
        '98900000-0000-4000-8000-000000000003'
      ]::uuid[],
      '98930000-0000-4000-8000-000000000090'
    ) as conversation
  ),
  true
);
select pg_catalog.set_config(
  'gc_test.solo',
  (
    select conversation.id::text
    from public.create_group_conversation(
      'Solo',
      array['98900000-0000-4000-8000-000000000002']::uuid[],
      '98930000-0000-4000-8000-000000000091'
    ) as conversation
  ),
  true
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000002', true);

select public.leave_group_conversation(
  current_setting('gc_test.solo')::uuid,
  '98930000-0000-4000-8000-000000000092'
);

select pg_catalog.set_config('request.jwt.claim.sub', '98900000-0000-4000-8000-000000000001', true);

select extensions.lives_ok(
  $$ select public.delete_my_account(); $$,
  'deleting an account with group memberships succeeds'
);

reset role;

select extensions.is(
  (
    select member.role::text || ':' || (conversation.created_by = '98900000-0000-4000-8000-000000000002')::text
    from public.conversation_members as member
    join public.conversations as conversation on conversation.id = member.conversation_id
    where member.conversation_id = current_setting('gc_test.trio')::uuid
      and member.user_id = '98900000-0000-4000-8000-000000000002'
  ),
  'owner:true',
  'deleting the owner promotes the earliest member and moves created_by'
);
select extensions.is(
  (
    select (conversation.created_by = '98900000-0000-4000-8000-000000000002')::text
    from public.conversations as conversation
    where conversation.id = current_setting('gc_test.group')::uuid
  ),
  'true',
  'groups the deleted user created earlier move to their current owner'
);
select extensions.is(
  (
    select count(*)
    from public.conversations as conversation
    where conversation.id = current_setting('gc_test.solo')::uuid
  ),
  0::bigint,
  'a group the deleted user was alone in is removed'
);
select extensions.is(
  (
    select count(*)
    from public.conversations as conversation
    where conversation.id in (
      '98910000-0000-4000-8000-000000000001',
      '98910000-0000-4000-8000-000000000002'
    )
  ),
  0::bigint,
  'direct conversations of the deleted user are removed'
);
select extensions.is(
  (
    select count(*)
    from public.conversation_members as member
    where member.user_id = '98900000-0000-4000-8000-000000000001'
  ),
  0::bigint,
  'the deleted user has no membership rows left'
);

select * from extensions.finish();

rollback;
