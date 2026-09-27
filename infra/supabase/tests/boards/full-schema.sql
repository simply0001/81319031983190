begin;
insert into auth.users (instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token)
select '00000000-0000-0000-0000-000000000000', id, 'authenticated','authenticated',email,'',now(),'{"provider":"email","providers":["email"]}','{"display_name":"Boards validation"}',now(),now(),'','','',''
from (values ('99290000-0000-4000-8000-000000000001'::uuid,'boards-owner@example.invalid'),('99290000-0000-4000-8000-000000000002'::uuid,'boards-member@example.invalid'),('99290000-0000-4000-8000-000000000003'::uuid,'boards-staff@example.invalid')) as fixture(id,email);
insert into private.admin_users(user_id,is_owner) values('99290000-0000-4000-8000-000000000003',true);
insert into auth.sessions(id,user_id,created_at,updated_at) values('99290000-0000-4000-8000-000000000012','99290000-0000-4000-8000-000000000002',now(),now());
do $$
declare b uuid; p uuid; r jsonb; ticket uuid; operation uuid:=gen_random_uuid();
  owner_id uuid:='99290000-0000-4000-8000-000000000001'; member_id uuid:='99290000-0000-4000-8000-000000000002'; staff_id uuid:='99290000-0000-4000-8000-000000000003';
  drawing jsonb:='{"version":1,"width":800,"height":600,"strokes":[{"pen":"pixel","color":"#222222","size":4,"points":[[10,10],[20,20]]}]}';
begin
  perform set_config('request.jwt.claims',jsonb_build_object('sub',staff_id,'role','authenticated')::text,true);
  perform public.boards_mutate('staff_settings','{"enabled":true}',gen_random_uuid());
  r:=public.boards_mutate('staff_create',jsonb_build_object('name','Validation board','visibility','public','user_id',owner_id),gen_random_uuid());b:=(r->>'board_id')::uuid;
  assert b is not null;
  perform set_config('request.jwt.claims',jsonb_build_object('sub',member_id,'role','authenticated','session_id','99290000-0000-4000-8000-000000000012')::text,true);
  perform public.boards_mutate('join',jsonb_build_object('board_id',b),gen_random_uuid());
  perform public.register_board_push_device('99290000-0000-4000-8000-000000000022',repeat('test-token-',10),false,true);
  perform set_config('request.jwt.claims',jsonb_build_object('sub',owner_id,'role','authenticated')::text,true);
  r:=public.boards_mutate('publish',jsonb_build_object('board_id',b,'body','Full-schema note','drawing',drawing,'spoiler',true),operation);p:=(r->>'id')::uuid;
  assert r=public.boards_mutate('publish',jsonb_build_object('board_id',b,'body','Full-schema note','drawing',drawing,'spoiler',true),operation);
  assert public.boards_query('post',jsonb_build_object('post_id',p))->>'body'='';
  assert public.boards_query('post',jsonb_build_object('post_id',p,'reveal',true))->>'body'='Full-schema note';
  assert (select count(*) from private.board_push_queue)=1;
  update private.board_push_queue set available_at=now()-interval '1 second';
  begin
    perform public.claim_board_push_batch();
    raise exception 'Push claim accepted a signed-in user';
  exception when sqlstate '42501' then null; end;
  perform set_config('request.jwt.claims',jsonb_build_object('role','service_role')::text,true);
  r:=public.claim_board_push_batch();assert jsonb_array_length(r)=1;
  assert r->0->'data'->>'body'='There is new activity in your boards.';
  perform set_config('request.jwt.claims',jsonb_build_object('sub',owner_id,'role','authenticated')::text,true);
  ticket:=public.prepare_board_branding(b,'cover',gen_random_uuid(),repeat('a',64));
  perform set_config('request.jwt.claims',jsonb_build_object('role','service_role')::text,true);
  r:=public.commit_board_branding(ticket,encode(convert_to('RIFF0000WEBPtest','UTF8'),'base64'),32,32);
  perform set_config('request.jwt.claims',jsonb_build_object('sub',owner_id,'role','authenticated')::text,true);
  assert (public.boards_query('board',jsonb_build_object('board_id',b))->>'cover_asset_id')::uuid=(r->>'asset_id')::uuid;
  perform public.boards_mutate('draw_branding',jsonb_build_object('board_id',b,'kind','icon','drawing',drawing),gen_random_uuid());
  perform set_config('request.jwt.claims',jsonb_build_object('sub',staff_id,'role','authenticated')::text,true);
  perform public.boards_mutate('staff_stationery','{"stationery_id":"validation-paper","name":"Validation paper","access":"tokens","price":5}',gen_random_uuid());
  perform private.ensure_token_balance(owner_id);
  update public.token_balances set balance=10 where user_id=owner_id;
  perform set_config('request.jwt.claims',jsonb_build_object('sub',owner_id,'role','authenticated')::text,true);
  perform public.boards_mutate('buy_stationery','{"stationery_id":"validation-paper"}',gen_random_uuid());
  assert (select balance from public.token_balances where user_id=owner_id)=5;
  perform set_config('request.jwt.claims',jsonb_build_object('sub',staff_id,'role','authenticated')::text,true);
  perform public.boards_mutate('staff_filter','{"phrase":"validation phrase","action":"censor","reason":"Test rule"}',gen_random_uuid());
  perform set_config('request.jwt.claims',jsonb_build_object('sub',owner_id,'role','authenticated')::text,true);
  r:=public.boards_mutate('publish',jsonb_build_object('board_id',b,'body','Validation phrase is matched'),gen_random_uuid());
  assert public.boards_query('post',jsonb_build_object('post_id',r->>'id'))->>'body'='**** is matched';
  begin
    update public.profiles set bio='Validation phrase' where user_id=owner_id;
    raise exception 'Bio filter did not reject';
  exception when sqlstate '22023' then null; end;
  perform private.board_cleanup();
  perform set_config('request.jwt.claims',jsonb_build_object('sub',staff_id,'role','authenticated')::text,true);
  perform public.boards_mutate('staff_delete',jsonb_build_object('board_id',b,'reason','Validation cleanup'),gen_random_uuid());
  assert not exists(select 1 from private.board_assets where board_id=b);
  assert not exists(select 1 from private.board_posts where board_id=b);
  r:=public.boards_mutate('staff_create',jsonb_build_object('name','Owner deletion validation','visibility','public','user_id',owner_id),gen_random_uuid()); b:=(r->>'board_id')::uuid;
  perform set_config('request.jwt.claims',jsonb_build_object('sub',owner_id,'role','authenticated')::text,true);
  perform public.delete_my_account();
  assert not exists(select 1 from auth.users where id=owner_id);
  assert (select q.archived and q.owner_id is null from private.boards q where q.id=b);
  raise notice 'Full-schema board, drawing, crypto, push, branding, token, bio, board deletion and account deletion checks passed';
end $$;
rollback;
