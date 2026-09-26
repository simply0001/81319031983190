import { PGlite } from '../../push/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile } from 'node:fs/promises';
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
const db = new PGlite();
const id = n => `99660000-0000-4000-8000-${String(n).padStart(12,'0')}`;
const me=id(1), friend=id(2), stranger=id(3), client=id(10);
const migration = name => readFile(new URL(`../../migrations/${name}`,import.meta.url),'utf8');
async function load(file,name) {
  const text=await migration(file), start=text.indexOf(`create or replace function ${name}(`);
  assert.ok(start>=0);
  const end=text.indexOf('$$;',text.indexOf('as $$',start)+5)+3;
  await db.exec(text.slice(start,end));
}
const scalar=async(sql,args=[])=>Object.values((await db.query(sql,args)).rows[0])[0];
const profile=who=>scalar('select private.api_profile_json(p) from public.profiles p where user_id=$1',[who]);
const scopes=values=>db.query('update private.developer_apps set scopes=$1 where client_id=$2',[values,client]);
const isolated=fn=>async()=>{await db.exec('begin');try{await fn();}finally{await db.exec('rollback');}};
before(async()=>{
 await db.exec(`create schema private; create schema auth;
 create role anon; create role api_client;
 create function auth.jwt() returns jsonb language sql stable as $$select current_setting('request.jwt.claims',true)::jsonb$$;
 create function auth.uid() returns uuid language sql stable as $$select (auth.jwt()->>'sub')::uuid$$;
 create function auth.role() returns text language sql stable as $$select auth.jwt()->>'role'$$;
 create table private.developer_apps(client_id uuid primary key,status text,scopes text[],scopes_changed_at timestamptz);
 create table auth.oauth_consents(user_id uuid,client_id uuid,revoked_at timestamptz,granted_at timestamptz);
 create table public.profiles(user_id uuid primary key,username text,display_name text,bio text,avatar_path text,age integer,country_code text,created_at timestamptz,updated_at timestamptz,last_seen_at timestamptz);
 create table public.friendships(user_low uuid,user_high uuid);
 create table public.user_blocks(blocker_id uuid,blocked_id uuid);
 create table public.nearby_encounters(id uuid);
 create function private.api_encounter_json(public.nearby_encounters,public.profiles) returns jsonb language sql immutable as $$select '{}'::jsonb$$;
 `);
 await load('20260829000100_public_api.sql','private.api_try_uuid');
 await load('20260829000100_public_api.sql','private.api_has_scope');
 await load('20260727000200_security_and_rpcs.sql','private.are_friends');
 await load('20260727000200_security_and_rpcs.sql','private.has_block_between');
 await db.exec(await migration('20260922000100_public_api_last_seen.sql'));
 for(const user of [me,friend,stranger]) await db.query("insert into public.profiles(user_id,username,last_seen_at) values($1,'person','2026-09-22T10:00:00Z')",[user]);
 await db.query('insert into public.friendships values($1,$2)',[me,friend]);
 await db.query("insert into private.developer_apps values($1,'active',array['profile:read','friends:read','presence:read'],now()-interval '1 day')",[client]);
 await db.query('insert into auth.oauth_consents values($1,$2,null,now())',[me,client]);
 await db.query("select set_config('request.jwt.claims',$1,false)",[JSON.stringify({sub:me,role:'api_client',client_id:client})]);
});
after(()=>db.close());
test('accepted friend and own profile expose recorded timestamp',isolated(async()=>{
 for(const user of [me,friend]) assert.equal(Date.parse((await profile(user)).last_seen_at),Date.parse('2026-09-22T10:00:00Z'));
}));
test('missing timestamp is JSON null when authorized',isolated(async()=>{
 await db.query('update public.profiles set last_seen_at=null where user_id=$1',[friend]);
 assert.equal((await profile(friend)).last_seen_at,null);
}));
test('no presence:read, including write-only presence, omits timestamp',isolated(async()=>{
 for(const values of [['profile:read','friends:read'],['friends:read','presence:write']]) {
 await scopes(values);assert.equal('last_seen_at' in await profile(friend),false);assert.equal('last_seen_at' in await profile(me),false);
 }
}));
test('presence without friends scope exposes only own timestamp',isolated(async()=>{
 await scopes(['profile:read','presence:read']);assert.ok('last_seen_at' in await profile(me));assert.equal('last_seen_at' in await profile(friend),false);
}));
test('visible nonfriend profile does not reveal last seen',isolated(async()=>{assert.equal('last_seen_at' in await profile(stranger),false);}));
test('either-direction blocks and removed friendship hide timestamp',isolated(async()=>{
 for(const pair of [[me,friend],[friend,me]]) {
 await db.query('insert into public.user_blocks values($1,$2)',pair);assert.equal('last_seen_at' in await profile(friend),false);await db.exec('delete from public.user_blocks');
 }
 await db.exec('delete from public.friendships');assert.equal('last_seen_at' in await profile(friend),false);
}));
test('revoked consent hides last seen',isolated(async()=>{
 await db.exec('update auth.oauth_consents set revoked_at=now()');assert.equal('last_seen_at' in await profile(friend),false);
}));
test('suspended app and stale consent hide last seen',isolated(async()=>{
 await db.exec("update private.developer_apps set status='suspended'");assert.equal('last_seen_at' in await profile(friend),false);
 await db.exec("update private.developer_apps set status='active',scopes_changed_at=now()+interval '1 second'");assert.equal('last_seen_at' in await profile(friend),false);
}));
test('serializer remains private and is stable, not immutable',async()=>{
 assert.equal(await scalar("select has_function_privilege('api_client','private.api_profile_json(public.profiles)','execute')"),false);
 assert.equal(await scalar("select provolatile from pg_proc where oid='private.api_profile_json(public.profiles)'::regprocedure"),'s');
});
