begin;
set local search_path = public, extensions;
select extensions.plan(11);

insert into auth.users (instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token)
select '00000000-0000-0000-0000-000000000000', id, 'authenticated','authenticated',email,'',now(),'{"provider":"email","providers":["email"]}','{"display_name":"Friend privacy test"}',now(),now(),'','','',''
from (values
  ('99790000-0000-4000-8000-000000000101'::uuid,'friend-privacy-a@pocketpass.test'),
  ('99790000-0000-4000-8000-000000000102'::uuid,'friend-privacy-b@pocketpass.test'),
  ('99790000-0000-4000-8000-000000000103'::uuid,'friend-privacy-c@pocketpass.test'),
  ('99790000-0000-4000-8000-000000000104'::uuid,'friend-privacy-d@pocketpass.test')
) as fixture(id,email);

set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99790000-0000-4000-8000-000000000101","role":"authenticated"}',true);
select extensions.lives_ok($$select public.send_friend_request('99790000-0000-4000-8000-000000000102','99790000-0000-4000-8000-000000000111')$$, 'A request arrives before blocking');

set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99790000-0000-4000-8000-000000000102","role":"authenticated"}',true);
select extensions.lives_ok($$select public.set_message_privacy(true)$$, 'Recipient enables Block Messages');

set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99790000-0000-4000-8000-000000000103","role":"authenticated"}',true);
select extensions.lives_ok($$select public.send_friend_request('99790000-0000-4000-8000-000000000102','99790000-0000-4000-8000-000000000112')$$, 'Blocking messages does not block friend requests');

set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99790000-0000-4000-8000-000000000102","role":"authenticated"}',true);
select extensions.lives_ok($$select public.set_invite_privacy(true)$$, 'Recipient blocks new invitations and friend requests');

set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99790000-0000-4000-8000-000000000104","role":"authenticated"}',true);
select extensions.throws_ok($$select public.send_friend_request('99790000-0000-4000-8000-000000000102','99790000-0000-4000-8000-000000000114')$$, '42501', 'This person is not accepting friend requests.', 'New requests are refused by invite privacy');

reset role;
select extensions.ok(not exists(select 1 from public.friend_requests where requester_id='99790000-0000-4000-8000-000000000104' and addressee_id='99790000-0000-4000-8000-000000000102'), 'Refused request leaves no pending row');
select extensions.ok(exists(select 1 from public.friend_requests where requester_id='99790000-0000-4000-8000-000000000101' and addressee_id='99790000-0000-4000-8000-000000000102' and status='pending'), 'Earlier requests remain available to the recipient');
select extensions.throws_ok($$insert into public.friend_requests(requester_id,addressee_id,client_operation_id) values ('99790000-0000-4000-8000-000000000104','99790000-0000-4000-8000-000000000102','99790000-0000-4000-8000-000000000113')$$, '42501', 'This person is not accepting friend requests.', 'The table itself refuses bypasses');
select extensions.is(private.api_failure('42501','This person is not accepting friend requests.','FRIEND_REQUESTS_BLOCKED')->>'code', 'PT403', 'Connected apps receive a forbidden response');

set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99790000-0000-4000-8000-000000000102","role":"authenticated"}',true);
select extensions.lives_ok($$select public.set_invite_privacy(false)$$, 'Recipient accepts new invitations and requests again');

set local role authenticated;
select set_config('request.jwt.claims','{"sub":"99790000-0000-4000-8000-000000000104","role":"authenticated"}',true);
select extensions.lives_ok($$select public.send_friend_request('99790000-0000-4000-8000-000000000102','99790000-0000-4000-8000-000000000114')$$, 'New requests resume after unblocking, even while direct messages stay blocked');

reset role;
select * from extensions.finish();
rollback;
