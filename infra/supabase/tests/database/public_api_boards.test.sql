-- Production-schema smoke test. All fixtures, broadcasts and writes roll back.
begin;
set local lock_timeout='10s';
set local statement_timeout='30s';
set local search_path=public,extensions;
load 'safeupdate';
select extensions.no_plan();

insert into auth.users(instance_id,id,aud,role,email,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,
  confirmation_token,email_change,email_change_token_new,recovery_token)
select '00000000-0000-0000-0000-000000000000',id,'authenticated','authenticated',email,now(),
  '{"provider":"email","providers":["email"]}',jsonb_build_object('display_name','Boards API test'),now(),now(),'','','',''
from (values
  ('99321000-0000-4000-8000-000000000001'::uuid,'boards-api-owner@pocketpass.test'),
  ('99321000-0000-4000-8000-000000000002'::uuid,'boards-api-member@pocketpass.test'),
  ('99321000-0000-4000-8000-000000000003'::uuid,'boards-api-staff@pocketpass.test')
) seed(id,email);
insert into private.admin_users(user_id,note,is_owner) values('99321000-0000-4000-8000-000000000003','Rollback-only test',true);

select set_config('request.jwt.claims','{"sub":"99321000-0000-4000-8000-000000000001","role":"authenticated"}',true);
select set_config('boards_api_test.client',public.developer_create_app('Boards API rollback test','','','',
  array['https://example.invalid/callback'],private.api_scope_keys(),'public')->'app'->>'client_id',true);
insert into auth.oauth_consents(id,user_id,client_id,scopes,granted_at)
select gen_random_uuid(),id,current_setting('boards_api_test.client')::uuid,'openid',now() from auth.users
where id in ('99321000-0000-4000-8000-000000000001','99321000-0000-4000-8000-000000000002','99321000-0000-4000-8000-000000000003');
select set_config('request.jwt.claims','{"sub":"99321000-0000-4000-8000-000000000003","role":"authenticated"}',true);
select set_config('boards_api_test.board',public.boards_mutate('staff_create',
  '{"name":"Rollback-only API board","visibility":"private","user_id":"99321000-0000-4000-8000-000000000001"}',gen_random_uuid())->>'board_id',true);

-- These helpers are invoker functions, so calls below run with api_client's ACL.
create function pg_temp.become(who text) returns void language plpgsql as $$
begin perform set_config('request.jwt.claims',jsonb_build_object('sub',who,'role','api_client','client_id',current_setting('boards_api_test.client'))::text,true);end $$;
create function pg_temp.args(extra jsonb default '{}') returns jsonb language sql as $$
  select jsonb_build_object('board_id',current_setting('boards_api_test.board'),'operation_id',gen_random_uuid())||extra;
$$;
grant execute on function pg_temp.become(text),pg_temp.args(jsonb) to api_client;

set local role api_client;
select pg_temp.become('99321000-0000-4000-8000-000000000001');
select extensions.ok((public.api_v1_boards_settings('{}')->>'enabled')::boolean,'Boards remains enabled');
select extensions.is(public.api_v1_boards_get(pg_temp.args()-'operation_id')->>'role','owner','API recognizes board ownership');
select set_config('boards_api_test.note',public.api_v1_boards_publish(pg_temp.args('{"body":"Rollback test note"}'))->>'id',true);
select extensions.ok(current_setting('boards_api_test.note') is not null,'API publishes a note');
select extensions.is(jsonb_array_length(public.api_v1_boards_feed(pg_temp.args()-'operation_id')->'items'),1,'First feed page contains the note');
select set_config('boards_api_test.draft',gen_random_uuid()::text,true);
select extensions.ok((public.api_v1_boards_save_draft(pg_temp.args(jsonb_build_object('draft_id',gen_random_uuid(),'revision_id',current_setting('boards_api_test.draft'),'payload',jsonb_build_object('body','Private draft'))))->>'ok')::boolean,'Private draft saves');
select extensions.is(jsonb_array_length(public.api_v1_boards_drafts('{}')->'items'),1,'Owner can read draft');
select pg_temp.become('99321000-0000-4000-8000-000000000002');
select extensions.is(public.api_v1_boards_get(pg_temp.args()-'operation_id')->>'code','PT403','Nonmember cannot read private board');
select extensions.is(jsonb_array_length(public.api_v1_boards_drafts('{}')->'items'),0,'Other accounts cannot read owner draft');
select extensions.ok((public.api_v1_privacy_set(jsonb_build_object('block_messages',true,'operation_id',gen_random_uuid()))->>'block_messages')::boolean,'Block Messages enables');
select pg_temp.become('99321000-0000-4000-8000-000000000001');
select set_config('boards_api_test.invite',public.api_v1_boards_invite(pg_temp.args('{"user_id":"99321000-0000-4000-8000-000000000002"}'))->>'id',true);
select extensions.ok(current_setting('boards_api_test.invite') is not null,'Blocking direct messages still allows Board invitations');
select public.api_v1_boards_revoke_invitation(pg_temp.args(jsonb_build_object('id',current_setting('boards_api_test.invite'))));
reset role;
select set_config('request.jwt.claims','{"sub":"99321000-0000-4000-8000-000000000002","role":"authenticated"}',true);
select extensions.lives_ok($$select public.set_invite_privacy(true)$$,'Invite privacy is saved by the account owner');
set local role api_client;
select pg_temp.become('99321000-0000-4000-8000-000000000001');
select extensions.is(public.api_v1_boards_invite(pg_temp.args('{"user_id":"99321000-0000-4000-8000-000000000002"}'))->>'hint','BOARD_INVITATIONS_BLOCKED','Invite privacy prevents new Board invitations');
select set_config('boards_api_test.code',public.api_v1_boards_create_code(pg_temp.args())->>'code',true);
select pg_temp.become('99321000-0000-4000-8000-000000000002');
select extensions.is(public.api_v1_boards_join_code(jsonb_build_object('code',current_setting('boards_api_test.code'),'operation_id',gen_random_uuid()))->>'status','joined','Voluntary code joining remains available');
select extensions.ok((public.api_v1_blocks_set(jsonb_build_object('user_id','99321000-0000-4000-8000-000000000001','blocked',true,'operation_id',gen_random_uuid()))->>'blocked')::boolean,'Blocking works through API');
select extensions.is(jsonb_array_length(public.api_v1_blocks_list('{}')->'items'),1,'Block list contains target');
select extensions.is(jsonb_array_length(public.api_v1_boards_feed(pg_temp.args()-'operation_id')->'items'),0,'Blocked content disappears');
select extensions.is(public.api_v1_boards_react(pg_temp.args(jsonb_build_object('post_id',current_setting('boards_api_test.note'),'yeah',true)))->>'code','PT403','Blocked reactions are refused');
select pg_temp.become('99321000-0000-4000-8000-000000000003');
select extensions.is(public.api_v1_boards_get((pg_temp.args()-'operation_id')||'{"review":true}')->>'code','PT403','OAuth staff cannot review private board through central rights');
select extensions.is(public.api_v1_boards_update_board(pg_temp.args('{"name":"Should not change"}'))->>'code','PT403','OAuth staff cannot override owner settings');
select extensions.ok(not has_function_privilege('api_client','public.boards_mutate(text,jsonb,uuid)','execute'),'Native staff RPC is not granted to API role');
select extensions.ok(not has_table_privilege('api_client','private.board_draft_versions','select'),'Draft table has no direct API grant');
select pg_temp.become('99321000-0000-4000-8000-000000000001');
select extensions.ok(private.api_can_access_realtime_topic('boards:99321000-0000-4000-8000-000000000001'),'Own Boards realtime topic is allowed');
select extensions.ok(not private.api_can_access_realtime_topic('boards:99321000-0000-4000-8000-000000000002'),'Other account realtime topic is refused');
reset role;
select extensions.ok(not private.has_permission('board_private_review'),'Central permission rejects OAuth identity');

update private.developer_apps set scopes=array['boards:read'] where client_id=current_setting('boards_api_test.client')::uuid;
set local role api_client;
select extensions.is(public.api_v1_boards_publish(pg_temp.args('{"body":"Missing scope"}'))->>'hint','SCOPE_REQUIRED','Write scope is required');
select extensions.is(public.api_v1_boards_drafts('{}')->>'hint','SCOPE_REQUIRED','Draft scope is independent of read scope');
select extensions.ok(not private.api_can_access_realtime_topic('board-drafts:99321000-0000-4000-8000-000000000001'),'Draft realtime scope is independent');
reset role;
update auth.oauth_consents set revoked_at=now() where client_id=current_setting('boards_api_test.client')::uuid;
set local role api_client;
select extensions.is(public.api_v1_boards_settings('{}')->>'hint','CONSENT_REVOKED','Revoked consent stops reads immediately');
reset role;
select set_config('request.jwt.claims','{"sub":"99321000-0000-4000-8000-000000000003","role":"authenticated"}',true);
set local role authenticated;
select extensions.ok((public.boards_query('staff_settings')->>'enabled')::boolean,'Native dashboard staff retain access');
reset role;

select * from extensions.finish();
rollback;
