import { PGlite } from '../../push/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile } from 'node:fs/promises';
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setupApiDatabase } from './api-fixture.mjs';

const db=new PGlite();
const id=n=>`99310000-0000-4000-8000-${String(n).padStart(12,'0')}`;
const owner=id(1), member=id(2), outsider=id(3), staff=id(4), moderator=id(5), client=id(10), secondClient=id(11);
const scalar=async(sql,args=[])=>Object.values((await db.query(sql,args)).rows[0])[0];
const claims=(who=owner,app=null)=>db.query("select set_config('request.jwt.claims',$1,false)",[
  JSON.stringify({sub:who,role:app?'api_client':'authenticated',...(app?{client_id:app}:{})})]);
const contract=JSON.parse(await readFile(new URL('../../public-api/boards.json',import.meta.url),'utf8'));
const drawing={version:1,width:800,height:600,strokes:[{pen:'pixel',color:'#222222',size:4,points:[[10,10],[30,40]]}]};
const native=(op,args={},operation=randomUUID())=>scalar('select public.boards_mutate($1,$2,$3)',[op,args,operation]);
const api=async(endpoint,args={},who=owner,app=client)=>{
  assert.match(endpoint,/^[a-z]+\.[a-z_]+$/);
  await claims(who,app);await db.exec('set local role api_client');
  const result=await scalar(`select public.api_v1_${endpoint.replace('.','_')}($1)`,[args]);
  await db.exec('reset role');return result;
};
const write=(endpoint,args={},who=owner,app=client)=>api(`boards.${endpoint}`,{...args,operation_id:args.operation_id||randomUUID()},who,app);
const ok=value=>{assert.doesNotMatch(value?.code||'',/^PT\d{3}$/,JSON.stringify(value));return value;};
const denied=value=>assert.equal(value.code,'PT403',JSON.stringify(value));
const board=async(visibility='public',ownerId=owner)=>{
  await claims(staff);const result=await native('staff_create',{name:'API board',visibility,user_id:ownerId});return result.board_id;
};
const scopes=async values=>db.query('update private.developer_apps set scopes=$1 where client_id=$2',[values,client]);
const isolated=fn=>async()=>{await db.exec('begin');try{await claims();await fn();}finally{await db.exec('rollback');}};
const reject=async(fn,pattern)=>{
  await db.exec('savepoint expected_failure');
  await assert.rejects(fn,pattern);
  await db.exec('rollback to savepoint expected_failure');
};

before(async()=>{
  await setupApiDatabase(db);
  for(const user of [owner,member,outsider,staff,moderator]) await db.query('insert into public.profiles(user_id,display_name) values($1,$2)',[user,`Person ${user.slice(-1)}`]);
  await db.query('insert into private.admin_users(user_id,is_owner) values($1,true)',[staff]);
  for(const app of [client,secondClient]) {
    await db.query("insert into private.developer_apps(client_id,owner_user_id,name,scopes) values($1,$2,'API test',private.api_scope_keys())",[app,owner]);
    for(const user of [owner,member,outsider,staff,moderator]) await db.query('insert into auth.oauth_consents(user_id,client_id) values($1,$2)',[user,app]);
  }
  await db.exec('update private.board_settings set enabled=true where singleton=true');
});
after(()=>db.close());

test('Board staff case and audit RPCs are native-only',isolated(async()=>{
  for(const signature of ['public.boards_staff_suspensions(integer,integer,text)',
    'public.boards_staff_audit(integer,integer,boolean)']) {
    assert.equal(await scalar('select has_function_privilege($1,$2,\'execute\')',['authenticated',signature]),true);
    for(const role of ['anon','api_client','service_role'])
      assert.equal(await scalar('select has_function_privilege($1,$2,\'execute\')',[role,signature]),false,`${role}: ${signature}`);
  }
}));

test('every declared endpoint has a scope guard and only api_client execute access',isolated(async()=>{
  assert.deepEqual(await scalar('select private.api_board_contract()'),contract);
  await scopes(['profile:read']);
  for(const endpoint of Object.keys(contract)) {
    const signature=`public.api_v1_boards_${endpoint}(jsonb)`;
    for(const role of ['anon','authenticated','service_role']) assert.equal(await scalar('select has_function_privilege($1,$2,\'execute\')',[role,signature]),false,`${role}: ${endpoint}`);
    assert.equal((await api(`boards.${endpoint}`)).hint,'SCOPE_REQUIRED',endpoint);
  }
  for(const signature of ['public.boards_query(text,jsonb)','public.boards_mutate(text,jsonb,uuid)','public.boards_inbox(jsonb,integer)',
    'public.prepare_board_branding(uuid,text,uuid,text)','public.commit_board_branding(uuid,text,integer,integer)',
    'private.api_boards_request(text,jsonb)','public.set_user_block(uuid,boolean,uuid)','public.set_message_privacy(boolean,uuid)','public.set_invite_privacy(boolean,uuid)']) {
    assert.equal(await scalar("select has_function_privilege('api_client',$1,'execute')",[signature]),false,signature);
  }
  assert.equal(await scalar("select has_table_privilege('api_client','private.board_posts','select,insert,update,delete')"),false);
}));

test('real API guard rejects revoked consent, scope expansion, suspension and native tokens',isolated(async()=>{
  await db.query('update auth.oauth_consents set revoked_at=now() where client_id=$1',[client]);
  assert.equal((await api('boards.settings')).hint,'CONSENT_REVOKED');
  await db.query('update auth.oauth_consents set revoked_at=null where client_id=$1',[client]);
  await db.query("update private.developer_apps set scopes_changed_at=now()+interval '1 second' where client_id=$1",[client]);
  assert.equal((await api('boards.settings')).hint,'CONSENT_REVOKED');
  await db.query("update private.developer_apps set status='suspended' where client_id=$1",[client]);
  assert.equal((await api('boards.settings')).hint,'APP_SUSPENDED');
  await claims(owner);assert.equal((await scalar("select public.api_v1_boards_settings('{}')")).hint,'API_TOKEN_REQUIRED');
}));

test('public/private reads, membership, spoiler reveal and private media enforce current audience',isolated(async()=>{
  const bid=await board('private');
  denied(await api('boards.get',{board_id:bid},outsider));
  ok(await write('draw_branding',{board_id:bid,kind:'icon',drawing}));
  const note=ok(await write('publish',{board_id:bid,body:'Secret note',drawing,spoiler:true}));
  denied(await api('boards.preview',{post_id:note.id},outsider));
  assert.equal(ok(await api('boards.post',{post_id:note.id})).body,'');
  assert.equal(ok(await api('boards.post',{post_id:note.id,reveal:true})).body,'Secret note');
  const invite=ok(await write('invite',{board_id:bid,user_id:member}));
  denied(await api('boards.get',{board_id:bid},member));
  ok(await write('accept_invitation',{id:invite.id},member));
  assert.equal(ok(await api('boards.get',{board_id:bid},member)).role,'member');
  ok(await write('leave',{board_id:bid},member));
  denied(await api('boards.get',{board_id:bid},member));
}));

test('publishing/reactions/edits/replies/deletion share validation and duplicate protection',isolated(async()=>{
  const bid=await board();await write('join',{board_id:bid},member);
  const operation=randomUUID(),payload={board_id:bid,body:'A note',drawing,operation_id:operation};
  const note=ok(await write('publish',payload));assert.deepEqual(await write('publish',payload),note);
  assert.equal((await write('publish',{...payload,body:'Other'})).code,'PT409');
  const reply=ok(await write('publish',{board_id:bid,thread_id:note.id,reply_to:note.id,body:'A reply'},member));
  ok(await write('react',{board_id:bid,post_id:note.id,yeah:true},member));
  assert.equal(ok(await api('boards.post',{post_id:note.id})).yeah_count,1);
  ok(await write('react',{board_id:bid,post_id:note.id,yeah:false},member));
  ok(await write('edit',{board_id:bid,post_id:note.id,body:'Edited'}));
  assert.equal((await write('edit',{board_id:bid,post_id:note.id,body:'Edit drawing',drawing})).hint,'UNKNOWN_FIELD');
  ok(await write('delete_post',{board_id:bid,post_id:note.id}));
  const thread=ok(await api('boards.replies',{post_id:note.id}));
  assert.equal(thread.post.removed,true);assert.equal(thread.items[0].id,reply.id);
  assert.equal((await write('publish',{board_id:bid,body:'x'.repeat(1001)})).code,'PT400');
  assert.equal((await write('publish',{board_id:bid,drawing:{...drawing,width:999}})).code,'PT400');
}));

test('board reads paginate with filter-bound opaque cursors',isolated(async()=>{
  const bid=await board();
  for(let n=0;n<4;n++) ok(await write('publish',{board_id:bid,body:`Note ${n}`}));
  const first=ok(await api('boards.feed',{board_id:bid,limit:2}));
  const second=ok(await api('boards.feed',{board_id:bid,limit:2,cursor:first.next_cursor}));
  assert.equal(first.items.length,2);assert.equal(second.items.length,2);
  assert.equal(new Set([...first.items,...second.items].map(x=>x.id)).size,4);
  assert.equal((await api('boards.feed',{board_id:bid,sort:'popular',cursor:first.next_cursor})).hint,'INVALID_CURSOR');
  assert.equal((await api('boards.feed',{board_id:bid,cursor:'malformed'})).hint,'INVALID_CURSOR');
  for(const args of [{limit:1.5},{review:'true'},{actor_id:outsider},{staff_review:true}]) assert.equal((await api('boards.feed',{board_id:bid,...args})).code,'PT400',JSON.stringify(args));
}));

test('staff accounts connected through OAuth cannot use central overrides',isolated(async()=>{
  const bid=await board('private');
  for(const endpoint of ['get','members','management','history']) denied(await api(`boards.${endpoint}`,{board_id:bid,...(['get','members'].includes(endpoint)?{review:true}:{})},staff));
  denied(await write('transfer',{board_id:bid,user_id:staff},staff));
  denied(await write('update_board',{board_id:bid,name:'Central override'},staff));
  assert.equal((await write('restrict',{user_id:outsider,reason:'Global',kind:'suspension'},staff)).hint,'MISSING_FIELD');
  await claims(staff);ok(await native('staff_settings',{requests_open:false}));
}));

test('drafts require their own permission, stay owner-private and preserve conflicts',isolated(async()=>{
  const bid=await board(),draft=randomUUID(),base=randomUUID(),one=randomUUID(),two=randomUUID();
  ok(await write('save_draft',{board_id:bid,draft_id:draft,revision_id:base,payload:{body:'Private draft'}}));
  ok(await write('save_draft',{board_id:bid,draft_id:draft,revision_id:one,base_id:base,payload:{body:'Device one'}}));
  ok(await write('save_draft',{board_id:bid,draft_id:draft,revision_id:two,base_id:base,payload:{body:'Device two'}}));
  assert.equal(ok(await api('boards.drafts')).items.length,2);
  assert.equal(ok(await api('boards.drafts',{},staff)).items.length,0);
  await scopes(['boards:read','boards:write','boards:moderate']);
  assert.equal((await api('boards.drafts')).hint,'SCOPE_REQUIRED');
  assert.equal((await write('publish',{board_id:bid,body:'Must not discard draft',draft_id:draft,draft_revision_id:one})).hint,'SCOPE_REQUIRED');
}));

test('block/unblock and message privacy APIs enforce recipient rules and preserve groups',isolated(async()=>{
  const bid=await board();
  ok(await api('privacy.set',{block_messages:true,operation_id:randomUUID()},member));
  assert.equal(ok(await api('privacy.get',{},member)).block_messages,true);
  const allowed=ok(await write('invite',{board_id:bid,user_id:member}));
  ok(await write('revoke_invitation',{board_id:bid,id:allowed.id}));
  await claims(member);await scalar('select public.set_invite_privacy(true)');
  assert.equal((await write('invite',{board_id:bid,user_id:member})).hint,'BOARD_INVITATIONS_BLOCKED');
  const code=ok(await write('create_code',{board_id:bid}));
  ok(await write('join_code',{code:code.code},member));
  const blocked=ok(await api('blocks.set',{user_id:member,blocked:true,operation_id:randomUUID()}));assert.equal(blocked.blocked,true);
  assert.equal(ok(await api('blocks.list')).items[0].user_id,member);
  assert.equal(ok(await api('blocks.list',{},member)).items.length,0);
  const own=ok(await write('publish',{board_id:bid,body:'Hidden from the other account'}));
  assert.equal(ok(await api('boards.feed',{board_id:bid},member)).items.length,0);
  denied(await write('react',{board_id:bid,post_id:own.id,yeah:true},member));
  denied(await write('publish',{board_id:bid,thread_id:own.id,body:'Blocked reply'},member));
  ok(await api('blocks.set',{user_id:member,blocked:false,operation_id:randomUUID()}));
  assert.equal(ok(await api('boards.feed',{board_id:bid},member)).items.length,1);
}));

test('invite privacy can only be changed by its signed-in PocketPass owner',isolated(async()=>{
  await claims(member);
  await reject(()=>scalar('select public.set_invite_privacy(true,$1)',[owner]),/PocketPass account is required/);
  await reject(()=>scalar('select public.set_invite_privacy(null)'),/blocking preference is required/);
  await claims(member,client);
  await reject(()=>scalar('select public.set_invite_privacy(true)'),/PocketPass account is required/);
  await claims(member);
  await scalar('select public.set_invite_privacy(true)');
  assert.equal(await scalar('select block_invites from public.profiles where user_id=$1',[member]),true);
  assert.equal(await scalar('select block_invites from public.profiles where user_id=$1',[owner]),false);
}));

test('friend requests follow invite privacy, independently of direct-message privacy',isolated(async()=>{
  await claims(member);await scalar('select public.set_message_privacy(true)');
  await db.query("insert into public.friend_requests(requester_id,addressee_id,status) values($1,$2,'pending')",[owner,member]);
  await claims(member);await scalar('select public.set_invite_privacy(true)');
  await reject(()=>db.query("insert into public.friend_requests(requester_id,addressee_id,status) values($1,$2,'pending')",[outsider,member]),/not accepting friend requests/);
  assert.equal(await scalar('select count(*)::int from public.friend_requests where addressee_id=$1',[member]),1);
}));

test('approval joins, invitation codes, declines and member invitations use local roles',isolated(async()=>{
  const bid=await board();
  ok(await write('update_board',{board_id:bid,join_policy:'approval',code_policy:'approval'}));
  assert.equal(ok(await write('join',{board_id:bid},member)).status,'requested');
  denied(await write('publish',{board_id:bid,body:'Not accepted yet'},member));
  assert.equal(ok(await api('boards.management',{board_id:bid})).requests[0].user_id,member);
  ok(await write('decide_join',{board_id:bid,user_id:member,approve:true}));
  denied(await write('invite',{board_id:bid,user_id:outsider},member));
  ok(await write('update_board',{board_id:bid,members_can_invite:true}));
  const inv=ok(await write('invite',{board_id:bid,user_id:outsider},member));
  const outgoing=ok(await api('boards.outgoing_invitations',{board_id:bid},member));
  assert.deepEqual(Object.keys(outgoing).sort(),['items','next_cursor']);
  assert.equal(outgoing.items[0].id,inv.id);assert.equal(outgoing.items[0].code_hash,undefined);
  assert.equal(ok(await api('boards.invitations',{},outsider)).items[0].id,inv.id);
  denied(await write('decline_invitation',{board_id:bid,id:inv.id},member));
  ok(await write('decline_invitation',{board_id:bid,id:inv.id},outsider));
  assert.equal(ok(await api('boards.invitations',{},outsider)).items.length,0);
  const code=ok(await write('create_code',{board_id:bid}));
  assert.equal(ok(await write('join_code',{code:code.code},outsider)).status,'requested');
  ok(await write('decide_join',{board_id:bid,user_id:outsider,approve:false}));
  ok(await write('revoke_invitation',{board_id:bid,id:code.id}));
  denied(await write('join_code',{code:code.code},outsider));
}));

test('archive, visibility, ownership transfer and moderator hierarchy work through the API',isolated(async()=>{
  const bid=await board();
  for(const person of [member,moderator]) ok(await write('join',{board_id:bid},person));
  denied(await write('leave',{board_id:bid}));
  ok(await write('set_moderator',{board_id:bid,user_id:moderator,moderator:true}));
  denied(await write('set_moderator',{board_id:bid,user_id:member,moderator:true},moderator));
  denied(await write('restrict',{board_id:bid,user_id:owner,kind:'ban',reason:'Cannot ban owner'},moderator));
  ok(await write('archive',{board_id:bid,archived:true}));
  assert.equal(ok(await api('boards.get',{board_id:bid},member)).archived,true);
  denied(await write('publish',{board_id:bid,body:'Archived'},member));
  denied(await write('invite',{board_id:bid,user_id:outsider}));
  ok(await write('archive',{board_id:bid,archived:false}));
  ok(await write('update_board',{board_id:bid,visibility:'private'}));
  denied(await api('boards.get',{board_id:bid},outsider));
  assert.equal((await write('update_board',{board_id:bid,visibility:'public'})).code,'PT400');
  ok(await write('transfer',{board_id:bid,user_id:member}));
  assert.equal(ok(await api('boards.get',{board_id:bid})).owner_id,owner);
  ok(await write('accept_transfer',{board_id:bid},member));
  assert.equal(ok(await api('boards.get',{board_id:bid},member)).owner_id,member);
  denied(await write('archive',{board_id:bid,archived:true}));
  ok(await write('leave',{board_id:bid}));
  denied(await api('boards.get',{board_id:bid}));
}));

test('reports, protected history, bans and appeals stay within board moderation',isolated(async()=>{
  const bid=await board();for(const person of [member,moderator]) ok(await write('join',{board_id:bid},person));
  ok(await write('set_moderator',{board_id:bid,user_id:moderator,moderator:true}));
  const note=ok(await write('publish',{board_id:bid,body:'Original'},member));
  ok(await write('edit',{board_id:bid,post_id:note.id,body:'Edited'},member));
  denied(await api('boards.history',{board_id:bid},member));
  assert.equal(ok(await api('boards.history',{board_id:bid,limit:1})).items[0].content.body,'Original');
  const report=ok(await write('report',{board_id:bid,post_id:note.id,reason:'Board rule'},outsider));
  const queue=ok(await api('boards.management',{board_id:bid},moderator));
  assert.equal(queue.reports[0].id,report.case_id);assert.equal(queue.reports[0].reporter_id,undefined);
  await scopes(['boards:read','boards:write']);
  assert.equal((await write('delete_post',{board_id:bid,post_id:note.id,reason:'Rule'},moderator)).hint,'SCOPE_REQUIRED');
  await scopes(await scalar('select private.api_scope_keys()'));
  ok(await write('spoiler',{board_id:bid,post_id:note.id,spoiler:true,reason:'Spoiler'},moderator));
  ok(await write('delete_post',{board_id:bid,post_id:note.id,reason:'Rule'},moderator));
  ok(await write('resolve_report',{board_id:bid,id:report.case_id,reason:'Removed'},moderator));
  const modNote=ok(await write('publish',{board_id:bid,body:'Moderator note'},moderator));
  const centralReport=ok(await write('report',{board_id:bid,post_id:modNote.id,reason:'Staff review'},member));
  assert.equal(ok(await api('boards.management',{board_id:bid})).reports.length,0);
  denied(await write('resolve_report',{board_id:bid,id:centralReport.case_id,reason:'Cannot resolve'}));
  const ban=ok(await write('restrict',{board_id:bid,user_id:member,kind:'ban',reason:'Board rule'},moderator));
  ok(await api('boards.get',{board_id:bid},member));
  denied(await write('publish',{board_id:bid,body:'Banned'},member));
  const appeal=ok(await write('appeal',{board_id:bid,case_id:ban.case_id,reason:'Please review'},member));
  assert.equal(ok(await api('boards.notices',{limit:1},member)).restrictions[0].id,ban.case_id);
  ok(await write('resolve_appeal',{board_id:bid,id:appeal.case_id,reason:'Accepted'},moderator));
  ok(await write('revoke_restriction',{board_id:bid,id:ban.case_id},moderator));
  ok(await write('join',{board_id:bid},member));
  ok(await write('update_board',{board_id:bid,visibility:'private'}));
  ok(await write('restrict',{board_id:bid,user_id:member,kind:'ban',reason:'Private access'},moderator));
  denied(await api('boards.get',{board_id:bid},member));
}));

test('new text is filtered and rejected publishing keeps its cloud draft intact',isolated(async()=>{
  const bid=await board(),draft=randomUUID(),revision=randomUUID();
  await claims(staff);await native('staff_filter',{phrase:'secret phrase',action:'censor',reason:'Review'});
  await native('staff_filter',{phrase:'blocked phrase',action:'block',reason:'Not permitted'});
  const note=ok(await write('publish',{board_id:bid,body:'SECRET PHRASE and secret phrases'}));
  assert.equal(ok(await api('boards.post',{post_id:note.id})).body,'**** and secret phrases');
  ok(await write('save_draft',{board_id:bid,draft_id:draft,revision_id:revision,payload:{body:'blocked phrase'}}));
  assert.equal((await write('publish',{board_id:bid,body:'blocked phrase',draft_id:draft,draft_revision_id:revision})).code,'PT400');
  assert.equal(ok(await api('boards.drafts')).items[0].id,revision);
  assert.equal((await write('save_draft',{board_id:bid,draft_id:draft,revision_id:randomUUID(),payload:{image_url:'https://invalid.test/image'}})).hint,'UNKNOWN_FIELD');
  ok(await write('discard_draft',{revision_id:revision}));
  assert.equal(ok(await api('boards.drafts')).items.length,0);
  ok(await write('propose',{name:'A new board',visibility:'private'}));
  assert.equal(ok(await api('boards.proposals',{limit:1})).items.length,1);
  assert.equal((await write('propose',{name:'blocked phrase',visibility:'public'})).code,'PT400');
}));

test('stationery purchases are explicit, idempotent and survive supporter expiry',isolated(async()=>{
  const bid=await board();await claims(staff);
  await native('staff_stationery',{stationery_id:'paper',name:'Paper',access:'tokens',price:5,artwork:{version:1,background:'#FFFFFF'}});
  await db.query("insert into public.supporter_status values($1,now()+interval '1 day')",[owner]);
  await db.query('insert into public.token_balances values($1,12,now())',[owner]);
  const purchase={stationery_id:'paper',operation_id:randomUUID()};
  ok(await write('buy_stationery',purchase));ok(await write('buy_stationery',purchase));
  assert.equal(await scalar('select balance from public.token_balances where user_id=$1',[owner]),7);
  const note=ok(await write('publish',{board_id:bid,drawing,stationery_id:'paper'}));
  await db.query("update public.supporter_status set active_until=now()-interval '1 day' where user_id=$1",[owner]);
  await claims(staff);await native('staff_stationery',{stationery_id:'paper',name:'New Paper',access:'achievement',achievement_key:'icebreaker',artwork:{version:1,background:'#FFFFFF'}});
  const papers=ok(await api('boards.stationery')).items;
  assert.equal(papers.find(x=>x.id==='paper').owned,true);assert.equal(papers.find(x=>x.id==='paper').available,true);
  assert.equal(ok(await api('boards.post',{post_id:note.id})).stationery.name,'Paper');
  ok(await write('publish',{board_id:bid,drawing,stationery_id:'paper'}));
  await scopes(['boards:read','boards:write']);
  assert.equal((await write('buy_stationery',purchase)).hint,'SCOPE_REQUIRED');
}));

test('imported artwork retains OAuth identity and rechecks consent before committing',isolated(async()=>{
  const bid=await board('private'),operation=randomUUID();
  const args={board_id:bid,kind:'icon',operation_id:operation,source_hash:'a'.repeat(64)};
  const {ticket}=ok(await api('boards.prepare_branding',args));
  assert.equal(ok(await api('boards.prepare_branding',args)).ticket,ticket);
  assert.equal((await api('boards.prepare_branding',args,owner,secondClient)).code,'PT409');
  denied(await api('boards.prepare_branding',{...args,operation_id:randomUUID()},staff));
  const commit=()=>scalar('select public.commit_board_branding($1,$2,40,30)',[ticket,Buffer.from('RIFF0000WEBPpayload').toString('base64')]);
  await db.query('update auth.oauth_consents set revoked_at=now() where client_id=$1 and user_id=$2',[client,owner]);
  await reject(commit,/permission is no longer/);
  assert.equal(await scalar('select count(*)::int from private.board_assets'),0);
  await db.query('update auth.oauth_consents set revoked_at=null where client_id=$1 and user_id=$2',[client,owner]);
  const result=await commit();assert.deepEqual(await commit(),result);
  assert.equal(await scalar("select client_id from private.board_audit where action='branding_import'"),client);
  assert.equal(ok(await api('boards.asset',{asset_id:result.asset_id})).mime,'image/webp');
  denied(await api('boards.asset',{asset_id:result.asset_id},outsider));
  ok(await write('draw_branding',{board_id:bid,kind:'icon',drawing}));
  assert.match(ok(await api('boards.branding_preview',{board_id:bid,kind:'icon'})).svg,/<svg/);
  await scopes(['boards:read']);
  assert.equal((await api('boards.asset',{asset_id:result.asset_id})).hint,'SCOPE_REQUIRED');
}));

test('realtime uses separate self-only scopes, contains no private content, and follows revoked consent',isolated(async()=>{
  const bid=await board('private');
  ok(await write('publish',{board_id:bid,body:'PRIVATE NOTE'}));
  ok(await write('invite',{board_id:bid,user_id:member}));
  ok(await write('save_draft',{board_id:bid,draft_id:randomUUID(),revision_id:randomUUID(),payload:{body:'PRIVATE DRAFT'}}));
  const broadcasts=(await db.query('select * from private.test_broadcasts')).rows;
  for(const event of broadcasts.filter(x=>['BOARDS','DRAFTS'].includes(x.event))) assert.deepEqual(event.payload,{});
  assert.ok(broadcasts.some(x=>x.topic===`board-drafts:${owner}`));
  await scopes(['boards:read']);await claims(owner,client);
  for(const [topic,allowed] of [[`boards:${owner}`,true],[`boards:${member}`,false],[`board-drafts:${owner}`,false],[`blocks:${owner}`,false],[`privacy:${owner}`,false]])
    assert.equal(await scalar('select private.api_can_access_realtime_topic($1)',[topic]),allowed,topic);
  await scopes(['boards:drafts','blocks:read','privacy:read']);
  for(const prefix of ['board-drafts','blocks','privacy']) assert.equal(await scalar('select private.api_can_access_realtime_topic($1)',[`${prefix}:${owner}`]),true);
  await db.query('update auth.oauth_consents set revoked_at=now() where client_id=$1',[client]);
  for(const prefix of ['board-drafts','blocks','privacy']) assert.equal(await scalar('select private.api_can_access_realtime_topic($1)',[`${prefix}:${owner}`]),false);
}));

test('Message blocking rejects DMs while invite blocking rejects new group adds without removing existing memberships',isolated(async()=>{
  const dm=randomUUID(),group=randomUUID(),existing=randomUUID();
  await db.query("insert into public.conversations values($1,'direct'),($2,'group')",[dm,group]);
  for(const chat of [dm,group]) for(const person of [owner,member]) await db.query('insert into public.conversation_members values($1,$2,null)',[chat,person]);
  await db.query('insert into public.messages values($1,$2,$3,\'Before\',\'{}\',null)',[existing,dm,owner]);
  const setting={block_messages:true,operation_id:randomUUID()};
  ok(await api('privacy.set',setting,member));ok(await api('privacy.set',setting,member));
  assert.equal((await api('privacy.set',{...setting,block_messages:false},member)).code,'PT409');
  await claims(owner,client);
  await reject(()=>db.query('insert into public.messages values($1,$2,$3,\'New\',\'{}\',null)',[randomUUID(),dm,owner]),/not accepting direct messages/);
  await reject(()=>db.query('update public.messages set body=\'Edited\' where id=$1',[existing]),/not accepting direct messages/);
  await db.query('insert into public.messages values($1,$2,$3,\'Existing group still works\',\'{}\',null)',[randomUUID(),group,owner]);
  assert.equal(await scalar('select count(*)::int from public.conversation_members where conversation_id=$1',[group]),2);
  await claims(outsider);await scalar('select public.set_invite_privacy(true)');await claims(owner,client);
  await reject(()=>db.query('insert into public.conversation_members values($1,$2,null)',[group,outsider]),/not accepting group invitations/);
  for(const hint of ['DIRECT_MESSAGES_BLOCKED','GROUP_MESSAGES_BLOCKED','BOARD_INVITATIONS_BLOCKED'])
    assert.equal((await scalar("select private.api_failure('42501','Privacy',$1)",[hint])).code,'PT403');
  await scopes(['profile:read']);
  for(const endpoint of ['blocks.list','blocks.set','privacy.get','privacy.set']) assert.equal((await api(endpoint)).hint,'SCOPE_REQUIRED');
}));

test('blocking removes friendships without restoring them on unblock and is safely replayable',isolated(async()=>{
  await db.query('insert into public.friendships(user_low,user_high) values($1,$2)',[owner,member]);
  await db.query("insert into public.friend_requests values($1,$2,'pending',null)",[owner,member]);
  const action={user_id:member,blocked:true,operation_id:randomUUID()};
  assert.deepEqual(ok(await api('blocks.set',action)),ok(await api('blocks.set',action)));
  assert.equal(await scalar('select count(*)::int from public.friendships'),0);
  assert.notEqual(await scalar('select status from public.friend_requests'),'pending');
  assert.equal((await api('blocks.set',{...action,blocked:false})).code,'PT409');
  ok(await api('blocks.set',{user_id:member,blocked:false,operation_id:randomUUID()}));
  assert.equal(await scalar('select count(*)::int from public.friendships'),0);
  for(const person of [member,outsider]) ok(await api('blocks.set',{user_id:person,blocked:true,operation_id:randomUUID()}));
  const first=ok(await api('blocks.list',{limit:1})),second=ok(await api('blocks.list',{limit:1,cursor:first.next_cursor}));
  assert.equal(new Set([...first.items,...second.items].map(x=>x.user_id)).size,2);
}));

test('activity preferences, read markers, burst protection and the kill switch work together',isolated(async()=>{
  const bid=await board();ok(await write('join',{board_id:bid},member));
  const payload={board_id:bid,body:'One note',operation_id:randomUUID()};
  ok(await write('publish',payload));
  const inbox=ok(await api('boards.inbox',{},member));assert.equal(inbox.items.length,1);
  ok(await write('read_event',{id:inbox.items[0].id},member));
  ok(await write('read_thread',{board_id:bid},member));
  ok(await write('preferences',{board_id:bid,muted:true,push_enabled:false},member));
  ok(await write('push_preference',{enabled:false},member));
  assert.equal(ok(await api('boards.settings',{},member)).push_enabled,false);
  await db.exec('update private.board_settings set submissions_per_minute=1 where singleton=true');
  ok(await write('publish',payload));
  assert.equal((await write('publish',{board_id:bid,body:'Too soon'})).hint,'BOARD_RATE_LIMIT');
  assert.ok(JSON.parse(await scalar("select current_setting('response.headers')")).some(h=>h['Retry-After']==='60'));
  await db.exec('update private.board_settings set enabled=false where singleton=true');
  assert.equal(ok(await api('boards.settings')).enabled,false);
  for(const endpoint of ['get','management']) assert.equal((await api(`boards.${endpoint}`,{board_id:bid})).hint,'BOARDS_DISABLED');
  assert.equal((await api('boards.get',{board_id:bid,review:true})).hint,'BOARDS_DISABLED');
  assert.equal((await write('publish',payload)).hint,'BOARDS_DISABLED');
  assert.equal(await scalar('select count(*)::int from private.board_posts'),1);
  ok(await api('privacy.set',{block_messages:true,operation_id:randomUUID()}));
}));

test('artwork cannot reuse a committed note operation and ownership loss prevents upload commit',isolated(async()=>{
  const bid=await board(),operation=randomUUID();
  const args={board_id:bid,kind:'cover',operation_id:operation,source_hash:'b'.repeat(64)};
  const {ticket}=ok(await api('boards.prepare_branding',args));
  ok(await write('publish',{board_id:bid,body:'A separate action with a reused ID',operation_id:operation}));
  const commit=id=>scalar('select public.commit_board_branding($1,$2,40,30)',[id,Buffer.from('RIFF0000WEBPpayload').toString('base64')]);
  await reject(()=>commit(ticket),/operation ID/);
  const next=ok(await api('boards.prepare_branding',{...args,operation_id:randomUUID()}));
  ok(await write('join',{board_id:bid},member));
  ok(await write('transfer',{board_id:bid,user_id:member}));
  ok(await write('accept_transfer',{board_id:bid},member));
  await reject(()=>commit(next.ticket),/Permission/);
  assert.equal(await scalar('select count(*)::int from private.board_assets'),0);
}));

test('API budgets and global settings/catalogue invalidations use current grants',isolated(async()=>{
  await db.exec('delete from private.test_broadcasts');
  await db.exec('update private.board_settings set requests_open=false where singleton=true');
  assert.equal(await scalar("select count(distinct topic)::int from private.test_broadcasts where event='BOARDS'"),5);
  await db.query('update private.developer_apps set user_rate_limit_per_minute=1 where client_id=$1',[client]);
  ok(await api('boards.settings'));
  assert.equal((await api('privacy.get')).hint,'API_RATE_LIMITED');
  assert.equal(await scalar('select requests::int from private.api_usage where client_id=$1 and user_id=$2',[client,owner]),2);
}));
