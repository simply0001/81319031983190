import { PGlite } from '../../push/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile } from 'node:fs/promises';
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setupApiDatabase } from './api-fixture.mjs';

// Fast local PostgreSQL contract suite. The production schema/pgcrypto integration
// is checked separately; these two deterministic crypto stand-ins are test-only.
const db = new PGlite();
const id = n => `99290000-0000-4000-8000-${String(n).padStart(12,'0')}`;
const owner=id(1), member=id(2), outsider=id(3), staff=id(4), moderator=id(5), other=id(6);
const scalar = async(sql,args=[]) => Object.values((await db.query(sql,args)).rows[0])[0];
const as = (who=owner,extra={}) => db.query("select set_config('request.jwt.claims',$1,false)",[JSON.stringify({sub:who,role:'authenticated',...extra})]);
const query = (operation,args={}) => scalar('select public.boards_query($1,$2)',[operation,args]);
const mutate = (operation,args={},operationId=randomUUID()) => scalar('select public.boards_mutate($1,$2,$3)',[operation,args,operationId]);
const board = async(visibility='public') => {
  await as(staff); const {board_id}=await mutate('staff_create',{name:'Test board',visibility,user_id:owner}); await as(owner); return board_id;
};
const join = async(bid,who=member) => { await as(who); await mutate('join',{board_id:bid}); };
const reject = async(fn,pattern=/./) => {
  await db.exec('savepoint expected_failure');
  await assert.rejects(fn,pattern);
  await db.exec('rollback to savepoint expected_failure');
};
const isolated = fn => async() => {
  await db.exec('begin');
  try { await as(); await fn(); } finally { await db.exec('rollback'); }
};
const drawing = {version:1,width:800,height:600,strokes:[{pen:'smooth',color:'#222222',size:4,points:[[10,20],[100,200]]}]};

test('pixel previews use connected 320×240 grid pixels and keep smooth lines', async()=>{
  const pixel={version:1,width:800,height:600,strokes:[
    {pen:'pixel',color:'#222222',size:2,points:[[2.5,2.5],[50,25]]},
    {pen:'smooth',color:'#E84A5F',size:4,points:[[10,20],[100,200]]},
  ]};
  const svg=await scalar('select private.board_drawing_preview($1)',[pixel]);
  assert.match(svg,/<g fill="#222222" shape-rendering="crispEdges">/);
  assert.match(svg,/<rect x="2.5" y="2.5" width="5.0" height="2.5"\/>/);
  const pixelRects=[...svg.matchAll(/<rect x="([0-9.]+)" y="([0-9.]+)" width="([0-9.]+)" height="2.5"\/>/g)];
  const coveredColumns=new Set(pixelRects.flatMap(([,left,,width])=>
    Array.from({length:Number(width)/2.5},(_,offset)=>Number(left)/2.5+offset)));
  assert.deepEqual([...coveredColumns].sort((a,b)=>a-b),Array.from({length:20},(_,index)=>index+1));
  assert.match(svg,/<polyline.*stroke="#E84A5F"/);
  assert.doesNotMatch(svg,/<polyline.*stroke="#222222"/);
});

test('pixel preview backfill reads the artwork inside a published stationery snapshot', isolated(async()=>{
  const bid=await board();
  const pixel={version:1,width:800,height:600,strokes:[
    {pen:'pixel',color:'#3379D6',size:2,points:[[5,5],[25,5]]},
  ]};
  const post=await mutate('publish',{board_id:bid,drawing:pixel});
  await db.query('update private.board_posts set drawing_preview=$1 where id=$2',['stale',post.id]);
  await db.query(`update private.board_posts
    set drawing_preview=private.board_note_preview(drawing,nullif(stationery->'artwork','null'::jsonb))
    where id=$1 and drawing::text like '%"pixel"%'`,[post.id]);
  assert.match((await query('preview',{post_id:post.id})).svg,/<g fill="#3379D6"/);
}));

test('bucket fills preview as crisp rectangles and reject malformed runs', isolated(async()=>{
  const filled={version:1,width:800,height:600,strokes:[
    {pen:'bucket',color:'#3379D6',size:2,points:[[2.5,5],[12.5,5],[5,7.5],[10,7.5]]},
  ]};
  const svg=await scalar('select private.board_drawing_preview($1)',[filled]);
  assert.match(svg,/<g fill="#3379D6" shape-rendering="crispEdges">/);
  assert.match(svg,/<rect x="2.5" y="5" width="10.0" height="2.5"\/>/);
  const bid=await board();
  const post=await mutate('publish',{board_id:bid,drawing:filled});
  assert.deepEqual((await query('post',{post_id:post.id})).drawing,filled);
  assert.match((await query('preview',{post_id:post.id})).svg,/<g fill="#3379D6"/);
  await reject(()=>scalar('select private.board_validate_drawing($1)',[
    {...filled,strokes:[{...filled.strokes[0],points:[[12.5,5],[2.5,5]]}]},
  ]),/Invalid bucket run/);
}));

before(async()=>{
  if(process.env.BOARDS_TEST_PUBLIC_API==='1') await setupApiDatabase(db);
  else {
  await db.exec(await readFile(new URL('./fixture.sql',import.meta.url),'utf8'));
  for(const file of ['20260919000200_boards_foundation.sql','20260919000300_boards_api.sql','20260919000400_board_push.sql','20260919000500_board_branding.sql','20260919000600_board_staff_review.sql','20260919000700_board_account_deletion.sql','20260919000800_board_settings_safe_update.sql','20260922000200_board_pixel_raster.sql','20260922000300_board_bucket_fill.sql','20260923000100_board_inbox_details.sql','20260923000200_board_admin_cases.sql']) {
    try { await db.exec(await readFile(new URL(`../../migrations/${file}`,import.meta.url),'utf8')); }
    catch(e) { throw new Error(`${file}: ${e.message} at ${e.position}, ${e.where || ''}`); }
  }
  await db.exec('alter table public.profiles add column block_invites boolean not null default false');
  const splitPrivacy=await readFile(new URL('../../migrations/20260925000100_split_social_privacy.sql',import.meta.url),'utf8');
  const rewriteStart=splitPrivacy.lastIndexOf('do $$');
  await db.exec(splitPrivacy.slice(rewriteStart,splitPrivacy.indexOf('end $$;',rewriteStart)+7));
  }
  for(const user of [owner,member,outsider,staff,moderator,other]) await db.query('insert into public.profiles(user_id) values($1)',[user]);
  await db.query('insert into private.admin_users(user_id,is_owner) values($1,true)',[staff]);
  await db.exec('update private.board_settings set enabled=true where singleton=true');
});
after(()=>db.close());

test('publishing unsynced edits retires their old cloud head and preserves a conflicting copy',isolated(async()=>{
  const bid=await board(),group=randomUUID(),base=randomUUID(),conflict=randomUUID();
  await mutate('save_draft',{board_id:bid,draft_id:group,revision_id:base,payload:{body:'Original'}});
  await mutate('save_draft',{board_id:bid,draft_id:group,revision_id:conflict,base_id:base,payload:{body:'Other device'}});
  await mutate('publish',{board_id:bid,body:'My offline edit',draft_id:group,draft_revision_id:randomUUID(),draft_base_id:base});
  const remaining=(await query('drafts')).items;
  assert.equal(remaining.length,1);assert.equal(remaining[0].id,conflict);
  assert.equal(remaining[0].payload.body,'Other device');
}));

test('stationery manifests reject external assets and metadata-only settings do not rescan old text',isolated(async()=>{
  const bid=await board();await mutate('update_board',{board_id:bid,description:'Historical phrase'});
  await as(staff);await mutate('staff_filter',{phrase:'Historical phrase',action:'block',reason:'Test filter'});
  await reject(()=>mutate('staff_stationery',{stationery_id:'bad-paper',name:'Bad',access:'free',artwork:{url:'https://example.invalid/asset.svg'}}),/manifest/);
  await as(owner);await mutate('update_board',{board_id:bid,accent:'green'});
  assert.equal((await query('board',{board_id:bid})).description,'Historical phrase');
  await reject(()=>mutate('update_board',{board_id:bid,description:'Historical phrase again'}),/Test filter/);
}));

test('branding imports authorize before processing, commit idempotently and revoke old artwork reads',isolated(async()=>{
  const bid=await board('private'),operation=randomUUID();
  const prepare=()=>scalar('select public.prepare_board_branding($1,$2,$3,$4)',[bid,'icon',operation,'a'.repeat(64)]);
  const ticket=await prepare(); assert.equal(await prepare(),ticket);
  await as(outsider);await reject(prepare,/Permission|unavailable|moderator/);
  assert.equal(await scalar("select has_function_privilege('authenticated','public.commit_board_branding(uuid,text,integer,integer)','execute')"),false);
  await as(owner);
  const commit=()=>scalar('select public.commit_board_branding($1,$2,64,64)',[ticket,Buffer.from('RIFF0000WEBPpayload').toString('base64')]);
  const first=await commit();assert.deepEqual(await commit(),first);
  assert.equal((await query('board',{board_id:bid})).icon_asset_id,first.asset_id);
  const invite=await mutate('invite',{board_id:bid,user_id:member});await as(member);await mutate('accept_invitation',{id:invite.id});
  assert.equal((await query('asset',{asset_id:first.asset_id})).mime,'image/webp');
  await as(owner);await mutate('draw_branding',{board_id:bid,kind:'icon',drawing});
  assert.deepEqual((await query('board',{board_id:bid})).icon_drawing,drawing);
  await as(member);await reject(()=>query('asset',{asset_id:first.asset_id}),/moderator/);
  await mutate('leave',{board_id:bid});await reject(()=>query('board',{board_id:bid}),/unavailable/);
}));

test('board push is independent of chats, grouped, spoiler safe and checked again after access changes',isolated(async()=>{
  const bid=await board();await join(bid);
  const installation=randomUUID();
  await scalar('select public.register_board_push_device($1,$2,false,true)',[installation,'fcm-test-token']);
  await as(owner);const post=await mutate('publish',{board_id:bid,body:'SECRET SPOILER',spoiler:true});
  await mutate('publish',{board_id:bid,thread_id:post.id,body:'second'});
  assert.equal(await scalar('select count(*)::int from private.board_push_queue'),1);
  await db.exec("update private.board_push_queue set available_at=now()-interval '1 second'");
  const jobs=await scalar('select public.claim_board_push_batch()');
  assert.equal(jobs.length,1);assert.equal(jobs[0].data.event_count,'2');
  assert.equal(JSON.stringify(jobs).includes('SECRET'),false);assert.equal(jobs[0].data.type,'board');
  await as(member);await mutate('push_preference',{enabled:false});
  await scalar('select public.finish_board_push($1,$2,$3)',[jobs[0].id,jobs[0].lease_id,'retry']);
  assert.deepEqual(await scalar('select public.claim_board_push_batch()'),[]);
  await mutate('push_preference',{enabled:true});
  await as(owner);await mutate('publish',{board_id:bid,thread_id:post.id,body:'third'});
  await mutate('update_board',{board_id:bid,visibility:'private'});
  await as(member);await mutate('leave',{board_id:bid});
  assert.deepEqual(await scalar('select public.claim_board_push_batch()'),[]);
}));

test('local moderators explicitly review blocked reports; separate central member permission cannot read reports',isolated(async()=>{
  const bid=await board('private');const inv=await mutate('invite',{board_id:bid,user_id:member});
  await as(member);await mutate('accept_invitation',{id:inv.id});const p=await mutate('publish',{board_id:bid,body:'Note'});
  await as(owner);await db.query('insert into public.user_blocks values($1,$2)',[owner,member]);
  await reject(()=>query('post',{post_id:p.id}),/unavailable/);
  assert.equal((await query('post',{post_id:p.id,review:true})).body,'Note');
  await mutate('delete_post',{board_id:bid,post_id:p.id,review:true,reason:'Board rule'});
  assert.equal((await query('post',{post_id:p.id,review:true})).removed,true);
  await db.query("insert into private.admin_users(user_id,permissions) values($1,array['board_members','board_private_review'])",[outsider]);
  await as(outsider);assert.equal((await query('members',{board_id:bid,review:true})).items.length,2);
  await reject(()=>query('post',{post_id:p.id,review:true}),/moderator/);
  assert.deepEqual((await query('management',{board_id:bid,review:true})).reports,[]);
}));

test('boards have no direct client grants; anonymous and connected apps cannot use RPCs',isolated(async()=>{
  for(const role of ['anon','authenticated']) for(const table of ['boards','board_posts','board_draft_versions','board_history','board_assets','board_filter_reviews'])
    assert.equal(await scalar("select has_table_privilege($1,$2,'select,insert,update,delete')",[role,`private.${table}`]),false);
  await as(null); await reject(()=>query('settings'),/account is required/);
  await reject(()=>scalar('select public.boards_inbox()'),/account is required/);
  await as(owner,{is_anonymous:true}); await reject(()=>query('settings'),/account is required/);
  await as(owner,{client_id:'connected-app'}); await reject(()=>query('directory'),/account is required/);
  await reject(()=>scalar('select public.boards_inbox()'),/account is required/);
}));
test('public boards require membership for participation and private boards are hidden',isolated(async()=>{
  const pub=await board(),priv=await board('private'); await as(outsider);
  assert.equal((await query('directory',{scope:'explore'})).items.length,1);
  assert.equal((await query('board',{board_id:pub})).id,pub);
  await reject(()=>query('board',{board_id:priv}),/unavailable/);
  await reject(()=>mutate('publish',{board_id:pub,body:'Hello'}),/Join/);
  await reject(()=>mutate('join',{board_id:priv}),/invitation/);
  await join(pub); assert.equal((await mutate('publish',{board_id:pub,body:'Hello'})).ok,true);
}));
test('public join approvals and direct invitation acceptance are explicit',isolated(async()=>{
  const bid=await board(); await mutate('update_board',{board_id:bid,join_policy:'approval'});
  await as(member); assert.equal((await mutate('join',{board_id:bid})).status,'requested');
  await reject(()=>mutate('publish',{board_id:bid,body:'Hello'}),/Join/);
  await as(owner); await mutate('decide_join',{board_id:bid,user_id:member,approve:true});
  const priv=await board('private'); const inv=await mutate('invite',{board_id:priv,user_id:outsider});
  await as(outsider); await reject(()=>query('board',{board_id:priv}),/unavailable/);
  await mutate('accept_invitation',{id:inv.id}); assert.equal((await query('board',{board_id:priv})).role,'member');
}));
test('invitation codes can require approval and are revocable; invite privacy blocks direct invites',isolated(async()=>{
  const bid=await board('private');
  const code=await mutate('create_code',{board_id:bid});
  await db.query('update public.profiles set block_invites=true where user_id=$1',[member]);
  await reject(()=>mutate('invite',{board_id:bid,user_id:member}),/not accepting Board invitations/);
  await as(member); assert.equal((await mutate('join_code',{code:code.code})).status,'joined');
  await as(owner); await mutate('revoke_invitation',{board_id:bid,id:code.id});
  await as(outsider); await reject(()=>mutate('join_code',{code:code.code}),/unavailable/);
}));
test('blocks hide both directions and prevent reply/reaction/invite interactions',isolated(async()=>{
  const bid=await board(); const post=await mutate('publish',{board_id:bid,body:'Owner note'}); await join(bid);
  await db.query('insert into public.user_blocks values($1,$2)',[owner,member]);
  assert.equal((await query('feed',{board_id:bid})).items.length,0);
  await reject(()=>mutate('react',{board_id:bid,post_id:post.id,yeah:true}),/unavailable/);
  await reject(()=>mutate('publish',{board_id:bid,thread_id:post.id,body:'reply'}),/unavailable/);
  await as(owner); await reject(()=>mutate('invite',{board_id:bid,user_id:member}),/cannot be invited/);
}));
test('drawing validation, preview, spoilers, edits, and deletion placeholders preserve replies',isolated(async()=>{
  const bid=await board();
  await reject(()=>mutate('publish',{board_id:bid,drawing:{...drawing,width:900}}),/Invalid drawing/);
  const p=await mutate('publish',{board_id:bid,drawing,body:'Caption',spoiler:true});
  assert.equal((await query('post',{post_id:p.id})).drawing,null);
  assert.equal((await query('preview',{post_id:p.id})).svg,null);
  assert.match((await query('preview',{post_id:p.id,reveal:true})).svg,/<polyline/);
  await reject(()=>mutate('edit',{board_id:bid,post_id:p.id,body:'Edit',drawing}),/cannot be edited/);
  await mutate('edit',{board_id:bid,post_id:p.id,body:'Edited'});
  await join(bid); await mutate('publish',{board_id:bid,thread_id:p.id,reply_to:p.id,body:'Reply'});
  await as(owner); await mutate('delete_post',{board_id:bid,post_id:p.id});
  const thread=await query('replies',{post_id:p.id}); assert.equal(thread.post.removed,true); assert.equal(thread.post.body,''); assert.equal(thread.items.length,1);
  assert.equal(await scalar('select count(*)::int from private.board_history where post_id=$1',[p.id]),2);
}));
test('publishing retries are idempotent and changed retry payloads are refused',isolated(async()=>{
  const bid=await board(),key=randomUUID(),args={board_id:bid,body:'Once'};
  const first=await mutate('publish',args,key); assert.deepEqual(await mutate('publish',args,key),first);
  await reject(()=>mutate('publish',{...args,body:'Different'},key),/already used/);
  assert.equal((await query('feed',{board_id:bid})).items.length,1);
}));
test('draft conflicts keep both heads and staff cannot browse someone else’s drafts',isolated(async()=>{
  const bid=await board(),draft=randomUUID(),a=randomUUID(),b=randomUUID(),c=randomUUID();
  await mutate('save_draft',{board_id:bid,draft_id:draft,revision_id:a,payload:{body:'Base'}});
  await mutate('save_draft',{board_id:bid,draft_id:draft,revision_id:b,base_id:a,payload:{body:'Phone'}});
  assert.equal((await mutate('save_draft',{board_id:bid,draft_id:draft,revision_id:c,base_id:a,payload:{body:'Thor'}})).conflict,true);
  assert.equal((await query('drafts')).items.length,2);
  await as(staff); assert.equal((await query('drafts',{review:true,user_id:owner})).items.length,0);
}));
test('censor and block filters use literal whole words, preserve originals, and reject matching bio edits',isolated(async()=>{
  const bid=await board(); await as(staff);
  await mutate('staff_filter',{phrase:'bad word',action:'censor',reason:'Please revise this phrase'});
  await mutate('staff_filter',{phrase:'a.b',action:'block',reason:'Please revise this name'});
  await as(owner); const p=await mutate('publish',{board_id:bid,body:'BAD WORD! bad words are separate'});
  assert.equal((await query('post',{post_id:p.id})).body,'****! bad words are separate');
  await reject(()=>mutate('publish',{board_id:bid,body:'a.b'}),/revise this name/);
  assert.equal((await mutate('publish',{board_id:bid,body:'axb'})).ok,true);
  await reject(()=>db.query('update public.profiles set bio=$1 where user_id=$2',['bad word',owner]),/revise this phrase/);
  assert.equal(await scalar('select bio from public.profiles where user_id=$1',[owner]),'');
  assert.equal(await scalar('select original from private.board_filter_reviews limit 1'),'BAD WORD! bad words are separate');
}));
test('archiving, private conversion, bans, and accepted ownership transfers enforce access',isolated(async()=>{
  const bid=await board(); await join(bid); await as(owner);
  await reject(()=>mutate('leave',{board_id:bid}),/Transfer/);
  await mutate('transfer',{board_id:bid,user_id:member});
  assert.equal((await query('board',{board_id:bid})).role,'owner');
  await as(member); await mutate('accept_transfer',{board_id:bid});
  await mutate('update_board',{board_id:bid,visibility:'private'});
  await reject(()=>mutate('update_board',{board_id:bid,visibility:'public'}),/cannot become public/);
  await as(outsider); await reject(()=>query('board',{board_id:bid}),/unavailable/);
  await as(member); await mutate('restrict',{board_id:bid,user_id:owner,kind:'ban',reason:'Repeated rule violations'});
  await as(owner); await reject(()=>query('board',{board_id:bid}),/unavailable/);
  await as(member); await mutate('archive',{board_id:bid}); await reject(()=>mutate('publish',{board_id:bid,body:'Hi'}),/archived/);
  assert.equal((await query('board',{board_id:bid})).archived,true);
}));
test('staff need explicit private review rights and owners retain all capabilities',isolated(async()=>{
  const bid=await board('private'); await as(staff); assert.equal((await query('board',{board_id:bid,review:true})).id,bid);
  await db.query("insert into private.admin_users(user_id,permissions) values($1,array['board_content'])",[outsider]);
  await as(outsider); await reject(()=>query('board',{board_id:bid,review:true}),/board_private_review/);
  assert.equal(await scalar("select count(*)::int from private.board_audit where action='review_access'"),1);
}));
test('stationery is permanently purchasable while subscribed and published snapshots survive expiry',isolated(async()=>{
  const bid=await board(); await as(staff);
  await mutate('staff_stationery',{stationery_id:'stars',name:'Stars',access:'tokens',price:20,artwork:{version:1,background:'#FFFFFF',drawing}});
  await db.query('insert into public.token_balances(user_id,balance) values($1,100)',[owner]);
  await db.query("insert into public.supporter_status values($1,now()+interval '1 day')",[owner]);
  await as(owner); await mutate('buy_stationery',{stationery_id:'stars'});
  assert.equal(await scalar('select balance from public.token_balances where user_id=$1',[owner]),80);
  const p=await mutate('publish',{board_id:bid,drawing,stationery_id:'stars'});
  await db.query("update public.supporter_status set active_until=now()-interval '1 day'");
  await as(staff); await mutate('staff_stationery',{stationery_id:'stars',name:'New stars',access:'achievement',achievement_key:'day_one'});
  await as(owner); assert.equal((await query('stationery')).find(s=>s.id==='stars').available,true);
  assert.equal((await query('post',{post_id:p.id,reveal:true})).stationery.version,1);
  assert.equal((await query('post',{post_id:p.id,reveal:true})).stationery.name,'Stars');
}));
test('permanent deletion purges drawings, drafts, retained content, assets, and keeps a minimal audit',isolated(async()=>{
  const bid=await board(); const p=await mutate('publish',{board_id:bid,body:'Hi',drawing}); await mutate('edit',{board_id:bid,post_id:p.id,body:'New'});
  await mutate('save_draft',{board_id:bid,draft_id:randomUUID(),revision_id:randomUUID(),payload:{body:'Draft'}});
  await as(staff); await mutate('staff_delete',{board_id:bid,reason:'Testing deletion'});
  for(const table of ['board_posts','board_history','board_draft_versions','board_operations','board_reports','board_assets'])
    assert.equal(await scalar(`select count(*)::int from private.${table} where board_id=$1`,[bid]),0);
  assert.equal(await scalar('select count(*)::int from private.board_audit where board_id=$1',[bid]),1);
}));

test('RPCs work under the authenticated database role while private helpers stay inaccessible',isolated(async()=>{
  const bid=await board(); await db.exec('set local role authenticated');
  assert.equal((await query('board',{board_id:bid})).id,bid);
  assert.equal((await mutate('publish',{board_id:bid,body:'Authenticated write'})).ok,true);
  await reject(()=>db.exec('select * from private.board_draft_versions'),/permission denied/);
  await db.exec('reset role');
}));
test('only owners appoint moderators and moderators cannot restrict owners or peers',isolated(async()=>{
  const bid=await board(); await join(bid,moderator);await join(bid,member);await as(owner);
  await mutate('set_moderator',{board_id:bid,user_id:moderator,moderator:true});
  await as(member);await reject(()=>mutate('set_moderator',{board_id:bid,user_id:member,moderator:true}),/owner required/);
  await as(moderator);await reject(()=>mutate('restrict',{board_id:bid,user_id:owner,kind:'mute',reason:'No'}),/cannot restrict/);
  await as(owner);await mutate('set_moderator',{board_id:bid,user_id:member,moderator:true});
  await as(moderator);await reject(()=>mutate('restrict',{board_id:bid,user_id:member,kind:'ban',reason:'No'}),/cannot restrict/);
  await mutate('archive',{board_id:bid}).then(()=>assert.fail('moderator archived board'),()=>{});
}));
test('approval codes create join requests and member invitations are controlled by the owner',isolated(async()=>{
  const bid=await board('private');await mutate('update_board',{board_id:bid,code_policy:'approval'});
  const code=await mutate('create_code',{board_id:bid});await as(member);
  assert.equal((await mutate('join_code',{code:code.code})).status,'requested');
  await reject(()=>query('board',{board_id:bid}),/unavailable/);await as(owner);
  await mutate('decide_join',{board_id:bid,user_id:member,approve:true});await as(member);
  await reject(()=>mutate('create_code',{board_id:bid}),/Only board staff/);await as(owner);
  await mutate('update_board',{board_id:bid,members_can_invite:true});await as(member);
  const memberCode=await mutate('create_code',{board_id:bid});await as(owner);
  await mutate('update_board',{board_id:bid,members_can_invite:false});await as(outsider);
  await reject(()=>mutate('join_code',{code:memberCode.code}),/unavailable/);
}));
test('private drawing previews, stored media, and drafts cannot be read after leaving',isolated(async()=>{
  const bid=await board('private'),post=await mutate('publish',{board_id:bid,drawing});
  const inv=await mutate('invite',{board_id:bid,user_id:member});await as(member);await mutate('accept_invitation',{id:inv.id});
  assert.match((await query('preview',{post_id:post.id})).svg,/<svg/);
  const asset=randomUUID();await db.query("insert into private.board_assets(id,board_id,owner_id,mime,bytes,width,height) values($1,$2,$3,'image/png',decode('00','hex'),1,1)",[asset,bid,owner]);
  await db.query('update private.boards set icon_asset_id=$1 where id=$2',[asset,bid]);
  assert.equal((await query('asset',{asset_id:asset})).mime,'image/png');
  await mutate('leave',{board_id:bid});
  await reject(()=>query('preview',{post_id:post.id}),/unavailable/);await reject(()=>query('asset',{asset_id:asset}),/unavailable/);
}));
test('public board bans preserve read access but remove participation and direct invitations',isolated(async()=>{
  const bid=await board();await join(bid);await as(owner);
  await mutate('restrict',{board_id:bid,user_id:member,kind:'ban',reason:'Rule violation'});await as(member);
  assert.equal((await query('board',{board_id:bid})).id,bid);
  await reject(()=>mutate('join',{board_id:bid}),/cannot join/);
  await reject(()=>mutate('publish',{board_id:bid,body:'No'}),/Join/);
}));
test('board restrictions have in-app appeals; staff-level restrictions point to Discord',isolated(async()=>{
  const bid=await board();await join(bid);await as(owner);
  const mute=await mutate('restrict',{board_id:bid,user_id:member,kind:'mute',reason:'Board rule'});await as(member);
  assert.equal((await mutate('appeal',{board_id:bid,case_id:mute.case_id,reason:'Please review'})).ok,true);
  await as(staff);const central=await mutate('restrict',{board_id:bid,user_id:member,kind:'mute',reason:'Service rule'});await as(member);
  await reject(()=>mutate('appeal',{board_id:bid,case_id:central.case_id,reason:'Please review'}),/Discord/);
  const notices=await query('notices');assert.equal(notices.restrictions.find(r=>r.id===central.case_id).central,true);
}));
test('staff can find and revoke global Board suspensions by their case IDs',isolated(async()=>{
  await as(staff);
  const suspension=await mutate('restrict',{user_id:member,reason:'Repeated Board rules'});
  assert.equal(await scalar('select private.board_restricted(null,$1)',[member]),true);
  const page=await scalar('select public.boards_staff_suspensions(0,50,$1)',[suspension.case_id]);
  assert.equal(page.items.length,1);
  assert.equal(page.items[0].id,suspension.case_id);
  assert.equal(page.items[0].user_id,member);
  assert.equal(page.items[0].reason,'Repeated Board rules');
  assert.equal(page.has_more,false);
  const audit=await scalar('select public.boards_staff_audit()');
  const restriction=audit.items.find(entry=>entry.action==='restrict' && entry.subject_id===member);
  assert.equal(restriction.actor_name,'Person');
  assert.equal(restriction.subject_name,'Person');
  await db.query("insert into private.board_audit(actor_id,action) values($1,'review_access')",[staff]);
  assert.equal((await scalar('select public.boards_staff_audit()')).items.some(entry=>entry.action==='review_access'),false);
  assert.equal((await scalar('select public.boards_staff_audit(0,50,true)')).items.some(entry=>entry.action==='review_access'),true);
  await as(member);
  await reject(()=>scalar('select public.boards_staff_suspensions()'),/Permission required/);
  await reject(()=>scalar('select public.boards_staff_audit()'),/Permission required/);
  await as(staff);
  await mutate('revoke_restriction',{id:suspension.case_id});
  assert.equal((await scalar('select public.boards_staff_suspensions()')).items.length,0);
  assert.equal(await scalar('select private.board_restricted(null,$1)',[member]),false);
}));
test('reports against moderators go only to staff and report views never expose reporter identity',isolated(async()=>{
  const bid=await board();const post=await mutate('publish',{board_id:bid,body:'Owner note'});await join(bid);
  const report=await mutate('report',{board_id:bid,post_id:post.id,reason:'Review this'});await as(owner);
  assert.equal((await query('management',{board_id:bid})).reports.length,0);
  await reject(()=>mutate('resolve_report',{board_id:bid,id:report.case_id,reason:'No issue'}),/unavailable/);
  await as(staff);const queue=await query('staff_reports');assert.equal(queue.reports.length,1);assert.equal('reporter_id' in queue.reports[0],false);
  await mutate('resolve_report',{board_id:bid,id:report.case_id,reason:'Reviewed',status:'resolved'});
}));
test('retention expires at 30 days but open reports and appeals hold originals',isolated(async()=>{
  const bid=await board();const p=await mutate('publish',{board_id:bid,body:'Original'});await mutate('edit',{board_id:bid,post_id:p.id,body:'Updated'});
  await db.query("update private.board_history set expires_at=now()-interval '1 day'");
  await join(bid);const report=await mutate('report',{board_id:bid,post_id:p.id,reason:'Please review'});
  await db.exec('select private.board_cleanup()');assert.equal(await scalar('select count(*)::int from private.board_history'),1);
  await as(staff);await mutate('resolve_report',{board_id:bid,id:report.case_id,reason:'Resolved'});
  await db.exec('select private.board_cleanup()');assert.equal(await scalar('select count(*)::int from private.board_history'),0);
}));
test('Yeah updates are sets, sorting is deterministic, and pagination does not repeat rows',isolated(async()=>{
  const bid=await board();const ids=[];
  for(let n=0;n<5;n++) ids.push((await mutate('publish',{board_id:bid,body:`Note ${n}`})).id);
  await join(bid);await mutate('react',{board_id:bid,post_id:ids[1],yeah:true});await mutate('react',{board_id:bid,post_id:ids[1],yeah:true});
  let popular=await query('feed',{board_id:bid,sort:'popular',period:'all'});assert.equal(popular.items[0].id,ids[1]);assert.equal(popular.items[0].yeah_count,1);
  await mutate('react',{board_id:bid,post_id:ids[1],yeah:false});assert.equal((await query('post',{post_id:ids[1]})).yeah_count,0);
  const page1=await query('feed',{board_id:bid,limit:2});const page2=await query('feed',{board_id:bid,limit:2,cursor:page1.cursor});
  assert.equal(new Set([...page1.items,...page2.items].map(p=>p.id)).size,4);
  await db.query("update private.board_posts set activity_at=now()-interval '1 day'");
  await mutate('publish',{board_id:bid,thread_id:ids[0],body:'New activity'});
  assert.equal((await query('feed',{board_id:bid,sort:'activity'})).items[0].id,ids[0]);
}));
test('burst limits are per account, configurable, and idempotent retries consume no extra allowance',isolated(async()=>{
  const bid=await board();await as(staff);await mutate('staff_settings',{submissions_per_minute:2});await as(owner);
  const key=randomUUID(),args={board_id:bid,body:'Once'};await mutate('publish',args,key);await mutate('publish',args,key);
  await mutate('publish',{board_id:bid,body:'Twice'});await reject(()=>mutate('publish',{board_id:bid,body:'Third'}),/too many requests/);
  await join(bid);assert.equal((await mutate('publish',{board_id:bid,body:'Other member'})).ok,true);
}));
test('feature disable preserves data and staff can read feature controls and create boards while disabled',isolated(async()=>{
  const bid=await board();await mutate('publish',{board_id:bid,body:'Saved'});await as(staff);await mutate('staff_settings',{enabled:false});
  assert.equal((await query('staff_settings')).enabled,false);await mutate('staff_create',{name:'Later',visibility:'public'});
  await as(owner);await reject(()=>query('feed',{board_id:bid}),/temporarily unavailable/);
  assert.equal(await scalar('select count(*)::int from private.board_posts'),1);await as(staff);await mutate('staff_settings',{enabled:true});
}));
test('word rules do not rescan history, rejection is atomic, and metadata/proposals are filtered',isolated(async()=>{
  const bid=await board();const p=await mutate('publish',{board_id:bid,body:'Custom phrase'});await as(staff);
  await mutate('staff_filter',{phrase:'custom phrase',action:'block',reason:'Choose another phrase'});await as(owner);
  assert.equal((await query('post',{post_id:p.id})).body,'Custom phrase');
  await reject(()=>mutate('update_board',{board_id:bid,name:'Custom phrase'}),/Choose another/);
  await reject(()=>mutate('propose',{name:'Custom phrase',description:'',rules:'',visibility:'public'}),/Choose another/);
  assert.equal((await query('board',{board_id:bid})).name,'Test board');
  await reject(()=>mutate('edit',{board_id:bid,post_id:p.id,body:'Custom phrase again'}),/Choose another/);
  assert.equal(await scalar('select count(*)::int from private.board_history'),0);
}));
test('stationery achievement access, insufficient balance, supporter expiry and permanent ownership',isolated(async()=>{
  const bid=await board();await as(staff);
  await mutate('staff_stationery',{stationery_id:'reward',name:'Reward',access:'achievement',achievement_key:'icebreaker'});
  await mutate('staff_stationery',{stationery_id:'paid',name:'Paid',access:'tokens',price:30});await as(owner);
  await reject(()=>mutate('buy_stationery',{stationery_id:'paid'}),/Not enough tokens/);
  await reject(()=>mutate('publish',{board_id:bid,drawing,stationery_id:'reward'}),/not unlocked/);
  await db.query("insert into public.achievement_unlocks values($1,'icebreaker')",[owner]);
  assert.equal((await mutate('publish',{board_id:bid,drawing,stationery_id:'reward'})).ok,true);
  await db.query("insert into public.supporter_status values($1,now()+interval '1 day')",[owner]);
  const p=await mutate('publish',{board_id:bid,drawing,stationery_id:'paid'});
  await db.exec("update public.supporter_status set active_until=now()-interval '1 day'");
  await reject(()=>mutate('publish',{board_id:bid,drawing,stationery_id:'paid'}),/not unlocked/);
  assert.equal((await query('post',{post_id:p.id})).stationery.id,'paid');
}));
test('drawing documents reject missing dimensions, out-of-bounds coordinates, and unsupported media',isolated(async()=>{
  const bid=await board();const missing={...drawing};delete missing.width;
  for(const invalid of [missing,{...drawing,strokes:[{...drawing.strokes[0],points:[[801,20]]}]},{...drawing,strokes:[{pen:'image',color:'#222222',size:4,points:[[1,1]]}]}])
    await reject(()=>mutate('publish',{board_id:bid,drawing:invalid}),/drawing|Drawing/);
}));


test('management queues and members paginate; ordinary inviters see only their own invitations',isolated(async()=>{
  const bid=await board();await mutate('update_board',{board_id:bid,members_can_invite:true});
  await mutate('create_code',{board_id:bid});await mutate('create_code',{board_id:bid});
  const first=await query('management',{board_id:bid,limit:1});
  const second=await query('management',{board_id:bid,limit:1,offset:first.next_offset});
  assert.notEqual(first.invitations[0].id,second.invitations[0].id);
  await join(bid);const own=await mutate('create_code',{board_id:bid});
  const data=await query('management',{board_id:bid});assert.deepEqual(data.invitations.map(i=>i.id),[own.id]);assert.equal(data.reports,undefined);
  await mutate('revoke_invitation',{board_id:bid,id:own.id});assert.equal((await query('management',{board_id:bid})).invitations.length,0);
  await as(owner);const members=await query('members',{board_id:bid,limit:1});const next=await query('members',{board_id:bid,limit:1,cursor:members.cursor});
  assert.notEqual(members.items[0].user_id,next.items[0].user_id);
}));

test('board ownership remains usable with a limited central staff role',isolated(async()=>{
  const bid=await board('private');
  await db.query("insert into private.admin_users(user_id,permissions) values($1,array['boards'])",[owner]);
  await mutate('update_board',{board_id:bid,description:'Owner change'});
  await reject(()=>mutate('update_board',{board_id:bid,description:'Central review',review:true}),/board_private_review/);
  assert.equal((await query('board',{board_id:bid})).description,'Owner change');
}));


test('explicit central review works for staff with local roles; board moderators cannot revoke central restrictions',isolated(async()=>{
  const bid=await board();await join(bid,moderator);await as(owner);await mutate('set_moderator',{board_id:bid,user_id:moderator,moderator:true});
  await join(bid,staff);await as(owner);await mutate('set_moderator',{board_id:bid,user_id:staff,moderator:true});
  await join(bid,member);await as(staff);const restriction=await mutate('restrict',{board_id:bid,user_id:member,kind:'mute',reason:'Central action',review:true,staff_review:true});
  assert.equal(await scalar('select central from private.board_restrictions where id=$1',[restriction.case_id]),true);
  await as(moderator);await reject(()=>mutate('revoke_restriction',{board_id:bid,id:restriction.case_id}),/Only PocketPass staff/);
  const data=await query('management',{board_id:bid,staff_review:true});assert.equal(data.board.id,bid);
  await as(staff);await mutate('revoke_restriction',{board_id:bid,id:restriction.case_id,review:true,staff_review:true});
  assert.notEqual(await scalar('select revoked_at from private.board_restrictions where id=$1',[restriction.case_id]),null);
}));


test('owner account deletion archives a board without removing the remaining audience',isolated(async()=>{
  const bid=await board('private'),inv=await mutate('invite',{board_id:bid,user_id:member});
  await as(member);await mutate('accept_invitation',{id:inv.id});
  await db.query('delete from public.profiles where user_id=$1',[owner]);
  const archived=await query('board',{board_id:bid});assert.equal(archived.archived,true);assert.equal(archived.owner_id,'');assert.equal(archived.role,'member');
  await reject(()=>mutate('publish',{board_id:bid,body:'Archived'}),/archived/);
  await as(staff);await mutate('transfer',{board_id:bid,user_id:member,review:true,staff_review:true});
  await mutate('archive',{board_id:bid,archived:false,review:true,staff_review:true});
  await as(member);assert.equal((await query('board',{board_id:bid})).role,'owner');
}));


test('feature settings preserve omitted values, enforce permissions and replay without duplicate audit',isolated(async()=>{
  await as(staff);
  await db.query('insert into private.admin_users(user_id,permissions) values($1,$2)',[moderator,['board_settings']]);
  await as(moderator);
  const operation=randomUUID();
  const settings={enabled:false,requests_open:false,submissions_per_minute:22,reactions_per_minute:80,invitations_reports_per_minute:7};
  const first=await mutate('staff_settings',settings,operation);
  assert.deepEqual(await mutate('staff_settings',settings,operation),first);
  const saved=await query('staff_settings');
  for(const [key,value] of Object.entries(settings)) assert.equal(saved[key],value);
  assert.equal(await scalar("select count(*)::int from private.board_audit where actor_id=$1 and action='staff_settings'",[moderator]),1);
  await mutate('staff_settings',{enabled:true});
  const enabled=await query('staff_settings');
  assert.equal(enabled.enabled,true);
  for(const [key,value] of Object.entries(settings)) if(key!=='enabled') assert.equal(enabled[key],value);
  await as(owner);
  await reject(()=>mutate('staff_settings',{enabled:false}),/Permission/);
  await as(moderator);
  assert.equal((await query('staff_settings')).enabled,true);
}));

test('Board inbox describes the note safely and keeps its thread destination',isolated(async()=>{
  const bid=await board();await join(bid);
  await as(owner);
  await db.query('update public.profiles set display_name=$1 where user_id=$2',['Ada',owner]);
  const note=await mutate('publish',{board_id:bid,body:'  Weekend\n   drawing plans  '});
  await mutate('publish',{board_id:bid,thread_id:note.id,body:'A reply'});
  await mutate('publish',{board_id:bid,body:'Hidden plans',spoiler:true});
  await as(member);
  const first=await scalar('select public.boards_inbox(null,1)');
  assert.equal(first.items.length,1);
  const second=await scalar('select public.boards_inbox($1,1)',[first.cursor]);
  const rows=[...first.items,...second.items];
  const thread=rows.find(x=>x.thread_id===note.id);
  const spoiler=rows.find(x=>x.thread_id!==note.id);
  assert.equal(spoiler.subject_type,'spoiler');
  assert.equal(spoiler.subject,null);
  assert.equal(thread.subject,'Weekend drawing plans');
  assert.equal(thread.thread_author_name,'Ada');
  assert.equal(thread.latest_actor_name,'Ada');
  assert.equal(thread.event_count,2);
  await mutate('read_event',{id:thread.id});
  assert.ok((await scalar('select public.boards_inbox(null,30)')).items.find(x=>x.id===thread.id).read_at);
  await db.query('update public.profiles set display_name=$1 where user_id=$2',['Reporter',outsider]);
  await db.query("insert into private.board_events(recipient_id,actor_id,board_id,kind) values($1,$2,$3,'report')",[owner,outsider,bid]);
  await as(owner);
  const report=(await scalar('select public.boards_inbox(null,30)')).items.find(x=>x.kind==='report');
  assert.equal(report.latest_actor_name,null);
  assert.equal(report.subject,null);
}));
