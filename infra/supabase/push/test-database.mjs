import { PGlite } from '@electric-sql/pglite';
import { readFile } from 'node:fs/promises';
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';

const db = new PGlite();
const id = n => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const user = id(1), sender = id(2), session = id(3), device = id(4), conversation = id(5), notification = id(6);
const scalar = async (sql, args = []) => Object.values((await db.query(sql, args)).rows[0])[0];
const claims = async (account = user, sid = session, extra = {}) => {
  await db.query("select set_config('request.jwt.claims', $1, false)", [JSON.stringify({ sub: account, session_id: sid, ...extra })]);
};
const register = (installation = device, token = 't'.repeat(100), enabled = true) => scalar(
  'select public.register_message_push_device($1, $2, $3)', [installation, token, enabled]);
const ready = () => db.exec("update private.message_push_queue set available_at = now() - interval '1 second'");
const asWorker = async work => {
  const previous = await scalar("select current_setting('request.jwt.claims', true)");
  await db.query("select set_config('request.jwt.claims', $1, false)", [JSON.stringify({ role: 'service_role' })]);
  try { return await work(); } finally { await db.query("select set_config('request.jwt.claims', $1, false)", [previous ?? '']); }
};
const claim = async () => { await ready(); return asWorker(() => scalar('select public.claim_message_push_batch()')); };
const finish = (job, outcome = 'sent') => asWorker(() => db.query('select public.finish_message_push($1, $2, $3)', [job.id, job.lease_id, outcome]));
const count = () => scalar('select count(*)::int from private.message_push_queue');
const notify = (count = 1, title = 'Sam', body = 'Hello', recipient = user, actor = sender) => db.query(`
  insert into public.notifications(id, recipient_id, actor_id, conversation_id, kind, title, body, event_count, updated_at)
  values ($1,$2,$3,$4,'message',$5,$6,$7,now()) on conflict(id) do update set
  event_count = excluded.event_count, title = excluded.title, body = excluded.body, updated_at = now(), read_at = null, deleted_at = null
`, [notification, recipient, actor, conversation, title, body, count]);

before(async () => {
  await db.exec(`
    create role anon; create role authenticated; create role service_role;
    create schema auth; create schema private;
    grant usage on schema public, auth to anon, authenticated, service_role;
    create function auth.jwt() returns jsonb language sql as $$ select coalesce(nullif(current_setting('request.jwt.claims', true),''),'{}')::jsonb $$;
    create function auth.uid() returns uuid language sql as $$ select (auth.jwt()->>'sub')::uuid $$;
    create function auth.role() returns text language sql as $$ select auth.jwt()->>'role' $$;
    create table public.profiles(user_id uuid primary key);
    create table auth.sessions(id uuid primary key, user_id uuid not null references public.profiles(user_id));
    create table public.conversation_members(conversation_id uuid, user_id uuid references public.profiles(user_id), left_at timestamptz, last_read_at timestamptz, primary key(conversation_id,user_id));
    create table public.user_blocks(blocker_id uuid, blocked_id uuid);
    create function private.has_block_between(a uuid,b uuid) returns boolean language sql as $$
      select exists(select 1 from public.user_blocks where (blocker_id=a and blocked_id=b) or (blocker_id=b and blocked_id=a))
    $$;
    create table public.notifications(id uuid primary key, recipient_id uuid references public.profiles(user_id), actor_id uuid, conversation_id uuid,
      kind text, title text, body text, event_count integer, updated_at timestamptz, read_at timestamptz, deleted_at timestamptz);
    insert into public.profiles values ('${user}'), ('${sender}');
    insert into auth.sessions values ('${session}','${user}'), ('${id(30)}','${sender}');
    insert into public.conversation_members values ('${conversation}','${user}',null,null), ('${conversation}','${sender}',null,null);
  `);
  await db.exec(await readFile(new URL('../migrations/20260912000100_message_push.sql', import.meta.url), 'utf8'));
  await db.exec(await readFile(new URL('../migrations/20260912000300_ios_message_push.sql', import.meta.url), 'utf8'));
  await db.exec('set check_function_bodies = off');
  await db.exec(await readFile(new URL('../migrations/20260927000100_worker_rpc_service_role_checks.sql', import.meta.url), 'utf8'));
  await db.exec('reset check_function_bodies');
});
after(() => db.close());

async function isolated(fn) {
  await db.exec('begin');
  try { await claims(); await fn(); } finally { await db.exec('rollback'); }
}

test('private tokens and queue cannot be read or modified by clients', () => isolated(async () => {
  for (const role of ['anon', 'authenticated']) {
    for (const table of ['message_push_devices', 'message_push_queue']) {
      assert.equal(await scalar(`select has_table_privilege($1,$2,'select,insert,update,delete')`, [role, `private.${table}`]), false);
    }
    assert.equal(await scalar("select has_function_privilege($1,'public.claim_message_push_batch()','execute')", [role]), false);
    assert.equal(await scalar("select has_function_privilege($1,'public.finish_message_push(uuid,uuid,text)','execute')", [role]), false);
  }
  assert.equal(await scalar("select has_function_privilege('authenticated','public.register_message_push_device(uuid,text,boolean)','execute')"), true);
  assert.equal(await scalar("select has_function_privilege('anon','public.register_message_push_device(uuid,text,boolean)','execute')"), false);
  assert.equal(await scalar("select has_function_privilege('service_role','public.claim_message_push_batch()','execute')"), true);
}));

test('worker RPCs refuse every caller except the service role', () => isolated(async () => {
  for (const extra of [{}, { role: 'anon' }, { role: 'authenticated' }, { role: 'api_client', client_id: 'third-party' }]) {
    await claims(user, session, extra);
    for (const sql of ['select public.claim_message_push_batch()', `select public.finish_message_push('${id(40)}', '${id(41)}', 'sent')`]) {
      await db.exec('savepoint attempt');
      await assert.rejects(scalar(sql), e => e.code === '42501');
      await db.exec('rollback to savepoint attempt');
    }
  }
}));

test('registration requires a real matching auth session and refuses OAuth connected apps', () => isolated(async () => {
  for (const [account, sid, extra] of [[null,null,{}], [user,id(999),{}], [sender,session,{}], [user,session,{client_id:'third-party'}]]) {
    await claims(account, sid, extra);
    await db.exec('savepoint attempt');
    await assert.rejects(register(), e => e.code === '42501');
    await db.exec('rollback to savepoint attempt');
  }
}));

test('registration is stable but rotates binding on token change and re-enable', () => isolated(async () => {
  const first = await register();
  assert.equal(await register(), first);
  const rotated = await register(device, 'r'.repeat(100));
  assert.notEqual(rotated, first);
  await register(device, 'r'.repeat(100), false);
  assert.notEqual(await register(device, 'r'.repeat(100), true), rotated);
}));

test('device limit and token validation are enforced', () => isolated(async () => {
  await db.exec('savepoint invalid');
  await assert.rejects(register(device, 'short'), e => e.code === '22023');
  await db.exec('rollback to savepoint invalid');
  for (let n = 100; n < 120; n++) await register(id(n), String(n).repeat(40));
  await db.exec('savepoint limited');
  await assert.rejects(register(), e => e.code === '54000');
  await db.exec('rollback to savepoint limited');
}));

test('direct and group previews fan out to each enabled recipient device', () => isolated(async () => {
  await register(); await register(id(7), 'b'.repeat(100));
  await notify(1, 'Friends group', 'Sam: Hello');
  const jobs = await claim();
  assert.equal(jobs.length, 2);
  assert.equal(jobs[0].data.body, 'Sam: Hello');
  assert.equal(jobs[0].data.title, 'Friends group');
  assert.equal(jobs[0].data.recipient_id, user);
  assert.equal(jobs[0].data.conversation_id, conversation);
  assert.equal(jobs[0].data.event_count, '1');
  assert.equal(await asWorker(() => scalar('select public.claim_message_push_batch()')).then(x => x.length), 0);
  for (const job of jobs) await finish(job);
  assert.equal(await count(), 0);
  await notify(2, 'Sam', 'Direct preview');
  assert.equal((await claim())[0].data.title, 'Sam');
}));

test('edits and read/delete changes do not enqueue another alert', () => isolated(async () => {
  await register(); await notify();
  await finish((await claim())[0]);
  await db.exec("update public.notifications set body = 'edited', updated_at = now()");
  assert.equal(await count(), 0);
  await db.exec('update public.notifications set read_at = now(), deleted_at = now()');
  assert.equal(await count(), 0);
}));

test('burst messages coalesce and a stale acknowledgement cannot delete newer work', () => isolated(async () => {
  await register(); await notify();
  const old = (await claim())[0];
  await notify(2); await notify(3);
  assert.equal(await count(), 1);
  await finish(old);
  assert.equal(await count(), 1);
  const current = (await claim())[0];
  assert.equal(current.data.event_count, '3');
  await finish(current); assert.equal(await count(), 0);
}));

test('temporary errors retry, invalid tokens delete the registration', () => isolated(async () => {
  await register(); await notify();
  await finish((await claim())[0], 'retry');
  assert.equal(await count(), 1);
  await finish((await claim())[0], 'unregistered');
  assert.equal(await count(), 0);
  assert.equal(await scalar('select count(*)::int from private.message_push_devices'), 0);
}));

test('own messages, disabled devices, left members, and blocks are skipped', () => isolated(async () => {
  await register();
  await notify(1, 'Self', 'Self', user, user); assert.equal(await count(), 0);
  await db.exec('delete from public.notifications');
  await register(device, 't'.repeat(100), false);
  await notify(); assert.equal(await count(), 0);
  await register();
  await db.exec('update public.conversation_members set left_at = now()');
  await notify(2); assert.equal(await count(), 0);
  await db.exec(`update public.conversation_members set left_at = null; insert into public.user_blocks values ('${user}','${sender}')`);
  await notify(3); assert.equal(await count(), 0);
}));

test('read messages and memberships are rechecked when claiming', () => isolated(async () => {
  await register(); await notify();
  await db.exec('update public.conversation_members set last_read_at = now()');
  assert.deepEqual(await claim(), []); assert.equal(await count(), 0);
  await db.exec('update public.conversation_members set last_read_at = null');
  await notify(2);
  await db.exec('update public.notifications set read_at = now()');
  assert.deepEqual(await claim(), []);
  await notify(3);
  await db.exec('update public.conversation_members set left_at = now()');
  assert.deepEqual(await claim(), []);
}));

test('a stale binding and expired notification are discarded', () => isolated(async () => {
  await register(); await notify();
  await register(device, 'new'.repeat(50));
  assert.equal(await count(), 0);
  await notify(2);
  await db.exec("update private.message_push_queue set created_at = now() - interval '16 minutes'");
  assert.deepEqual(await claim(), []);
}));

test('logout and revoking a session remove its device registrations and pending deliveries', () => isolated(async () => {
  await register(); await notify();
  await db.query('select public.unregister_message_push_device($1)', [device]);
  assert.equal(await count(), 0);
  await register(); await notify(2);
  await db.query('delete from auth.sessions where id = $1', [session]);
  assert.equal(await count(), 0);
  assert.equal(await scalar('select count(*)::int from private.message_push_devices'), 0);
}));

test('another account cannot unregister or steal an installation without its token', () => isolated(async () => {
  await register();
  await claims(sender, id(30));
  await db.query('select public.unregister_message_push_device($1)', [device]);
  assert.equal(await scalar('select count(*)::int from private.message_push_devices'), 1);
  await db.exec('savepoint theft');
  await assert.rejects(register(device, 'stolen'.repeat(20)), e => e.code === '42501');
  await db.exec('rollback to savepoint theft');
}));


test('iOS shares authorization and queue semantics, with generic alerts only', () => isolated(async () => {
  const ios = id(700);
  const binding = await scalar('select public.register_ios_message_push_device($1,$2)', [ios, 'i'.repeat(100)]);
  await register();
  await notify(1, 'Secret sender', 'Private message');
  const jobs = await claim();
  const apple = jobs.find(job => job.platform === 'ios');
  const android = jobs.find(job => job.platform === 'android');
  assert.equal(apple.data.binding_id, binding);
  assert.equal(apple.data.title, 'PocketPass');
  assert.equal(apple.data.body, 'You have a new message.');
  assert.equal(android.data.body, 'Private message');
  assert.equal(await scalar("select has_function_privilege('anon','public.register_ios_message_push_device(uuid,text)','execute')"), false);
  await claims(user, session, {client_id: 'untrusted-oauth'});
  await db.exec('savepoint denied');
  await assert.rejects(scalar('select public.register_ios_message_push_device($1,$2)', [ios, 'i'.repeat(100)]), e => e.code === '42501');
  await db.exec('rollback to savepoint denied');
  await claims();
  await scalar('select public.unregister_message_push_device($1)', [ios]);
  assert.equal(await scalar("select count(*)::int from private.message_push_devices where platform='ios'"), 0);
}));
