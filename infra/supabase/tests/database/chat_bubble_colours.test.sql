begin;
set local search_path = public, extensions;
select extensions.plan(20);

insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token, email_change, email_change_token_new, recovery_token)
select '00000000-0000-0000-0000-000000000000', id, 'authenticated', 'authenticated', email, '', now(),
  '{"provider":"email","providers":["email"]}', '{"display_name":"Chat Colour Test"}', now(), now(), '', '', '', ''
from (values ('99620000-0000-4000-8000-000000000001'::uuid, 'colour-owner@pocketpass.test'),
  ('99620000-0000-4000-8000-000000000002'::uuid, 'colour-reader@pocketpass.test')) as fixture(id,email);
update public.profiles set bio = 'Keep this bio' where user_id = '99620000-0000-4000-8000-000000000001';
insert into public.conversations(id, kind, created_by, title) values
  ('99620000-0000-4000-8000-000000000010', 'group', '99620000-0000-4000-8000-000000000001', 'Colour test');
insert into public.conversation_members(conversation_id,user_id) values
  ('99620000-0000-4000-8000-000000000010','99620000-0000-4000-8000-000000000001'),
  ('99620000-0000-4000-8000-000000000010','99620000-0000-4000-8000-000000000002');
insert into public.messages(id,conversation_id,sender_id,client_operation_id,body) values
  ('99620000-0000-4000-8000-000000000020','99620000-0000-4000-8000-000000000010',
   '99620000-0000-4000-8000-000000000001','99620000-0000-4000-8000-000000000021','Test history');
create temp table colour_baseline as select
  (select to_jsonb(m) from public.messages m where id='99620000-0000-4000-8000-000000000020') as message,
  (select jsonb_agg(to_jsonb(n) order by id) from public.notifications n where conversation_id='99620000-0000-4000-8000-000000000010') as notifications;
grant select on colour_baseline to authenticated;

select extensions.is((select chat_bubble_colour from public.profiles where user_id='99620000-0000-4000-8000-000000000001'), 'default', 'existing users default to the original appearance');
select extensions.ok(not has_function_privilege('anon','public.set_chat_bubble_colour(text,uuid,uuid)','execute'), 'anonymous callers cannot save colours');
select extensions.ok(not has_column_privilege('authenticated','public.profiles','chat_bubble_colour','update'), 'clients cannot bypass the owner-only RPC');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99620000-0000-4000-8000-000000000001","role":"authenticated"}',true);
select extensions.lives_ok($$select public.set_chat_bubble_colour('pink','99620000-0000-4000-8000-000000000030')$$, 'owner saves a preset');
select extensions.is((select chat_bubble_colour from public.profiles where user_id=auth.uid()), 'pink', 'sender profile stores the preset');
select extensions.is((select bio from public.profiles where user_id=auth.uid()), 'Keep this bio', 'colour saves preserve other profile fields');
select extensions.is((select chat_bubble_colour from public.profiles where user_id='99620000-0000-4000-8000-000000000002'), 'default', 'another account is unchanged');
select extensions.is((select to_jsonb(m) from public.messages m where id='99620000-0000-4000-8000-000000000020'), (select message from colour_baseline), 'colour changes never edit message history');
select extensions.lives_ok($$select public.set_chat_bubble_colour('pink','99620000-0000-4000-8000-000000000030')$$, 'retrying a save is idempotent');
select extensions.throws_ok($$select public.set_chat_bubble_colour('invalid','99620000-0000-4000-8000-000000000031')$$, '22023', 'Unknown chat colour', 'invalid colours are rejected');
select extensions.throws_ok($$select public.set_chat_bubble_colour('teal','99620000-0000-4000-8000-000000000030')$$, '22023', 'Client operation id was already used for another operation', 'an operation id cannot be reused with another colour');
reset role;
select extensions.is((select jsonb_agg(to_jsonb(n) order by id) from public.notifications n where conversation_id='99620000-0000-4000-8000-000000000010'), (select notifications from colour_baseline), 'colour changes do not create notifications or unread changes');
select extensions.ok(exists(select 1 from realtime.messages where topic='conversation:99620000-0000-4000-8000-000000000010' and event='chat_colour'), 'the conversation receives a separate colour signal');
select extensions.ok(not private.can_access_realtime_topic('conversation:99620000-0000-4000-8000-000000000010','99620000-0000-4000-8000-000000000099'), 'outsiders cannot subscribe to the conversation');
update public.conversation_members set left_at=now() where user_id='99620000-0000-4000-8000-000000000001' and conversation_id='99620000-0000-4000-8000-000000000010';
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99620000-0000-4000-8000-000000000002","role":"authenticated"}',true);
select extensions.is((select chat_bubble_colour from public.profiles where user_id='99620000-0000-4000-8000-000000000001'), 'pink', 'non-friends can resolve colours for a former group sender');
select extensions.throws_ok($$select public.set_chat_bubble_colour('teal','99620000-0000-4000-8000-000000000039','99620000-0000-4000-8000-000000000001')$$, '42501', 'A PocketPass account is required', 'an old queued save cannot affect the next signed-in account');
select set_config('request.jwt.claims','{"sub":"99620000-0000-4000-8000-000000000001","role":"authenticated","client_id":"third-party"}',true);
select extensions.throws_ok($$select public.set_chat_bubble_colour('teal','99620000-0000-4000-8000-000000000032')$$, '42501', 'A PocketPass account is required', 'connected apps cannot mutate this preference');
select set_config('request.jwt.claims','{}',true);
select extensions.throws_ok($$select public.set_chat_bubble_colour('teal','99620000-0000-4000-8000-000000000033')$$, '42501', 'A PocketPass account is required', 'missing user identity is rejected');
select set_config('request.jwt.claims','{"sub":"99620000-0000-4000-8000-000000000001","role":"authenticated"}',true);
select extensions.is((public.set_chat_bubble_colour('default','99620000-0000-4000-8000-000000000034')).chat_bubble_colour, 'default', 'reset restores default');
reset role;
insert into public.user_blocks(blocker_id,blocked_id) values ('99620000-0000-4000-8000-000000000002','99620000-0000-4000-8000-000000000001');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99620000-0000-4000-8000-000000000002","role":"authenticated"}',true);
select extensions.is((select count(*) from public.profiles where user_id='99620000-0000-4000-8000-000000000001'), 0::bigint, 'blocked profile visibility is preserved');
reset role;
select * from extensions.finish();
rollback;
