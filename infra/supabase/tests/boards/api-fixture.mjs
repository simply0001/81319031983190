import { readFile } from 'node:fs/promises';

const migration = name => readFile(new URL(`../../migrations/${name}`, import.meta.url), 'utf8');

// Load the real production guard/validation/idempotency functions; only the
// surrounding Supabase services, auth tables and test crypto are fixture data.
export async function setupApiDatabase(db) {
  await db.exec(await readFile(new URL('./fixture.sql', import.meta.url), 'utf8'));
  await db.exec(`
    create role api_client;
    grant usage on schema public,private,auth to api_client;
    create function auth.role() returns text language sql as $$select auth.jwt()->>'role'$$;
    create table auth.oauth_consents(user_id uuid,client_id uuid,revoked_at timestamptz,granted_at timestamptz default now());
    create table private.developer_apps(client_id uuid primary key,owner_user_id uuid,name text,status text default 'active',
      scopes text[] default '{}',scopes_changed_at timestamptz default now(),rate_limit_per_minute integer default 600,
      user_rate_limit_per_minute integer default 120,rate_limit_per_second integer default 100);
    create table private.api_usage(client_id uuid,day date,user_id uuid,requests bigint default 0,denied bigint default 0,primary key(client_id,day,user_id));
    create table private.api_rate_buckets(client_id uuid,user_id uuid,bucket timestamptz,requests integer default 0,primary key(client_id,user_id,bucket));
    create table private.api_rate_buckets_app(client_id uuid,bucket timestamptz,requests integer default 0,primary key(client_id,bucket));
    create table private.api_rate_buckets_app_second(client_id uuid,bucket timestamptz,requests integer default 0,primary key(client_id,bucket));
    create table private.rpc_operations(actor_id uuid,client_operation_id uuid,operation_name text,request_payload jsonb,response_payload jsonb,primary key(actor_id,client_operation_id));
    create table public.friendships(user_low uuid,user_high uuid,created_at timestamptz default now(),primary key(user_low,user_high));
    create table public.friend_requests(requester_id uuid,addressee_id uuid,status text,responded_at timestamptz);
    create table public.conversations(id uuid primary key,kind text);
    create table public.conversation_members(conversation_id uuid,user_id uuid,left_at timestamptz);
    create table public.messages(id uuid primary key,conversation_id uuid,sender_id uuid,body text,metadata jsonb,deleted_at timestamptz);
    create table realtime.messages(extension text);
    alter table realtime.messages enable row level security;
    create function realtime.topic() returns text language sql as $$select current_setting('realtime.topic',true)$$;
    create function private.api_can_access_realtime_topic(text) returns boolean language sql as $$select false$$;
    create function private.api_can_read_presence_topic(text) returns boolean language sql as $$select false$$;
    create policy pocketpass_api_realtime_read on realtime.messages for select to api_client using(private.api_can_access_realtime_topic(realtime.topic()));
    create table private.test_broadcasts(payload jsonb,event text,topic text);
    create or replace function realtime.send(jsonb,text,text,boolean) returns void language sql as $$insert into private.test_broadcasts values($1,$2,$3)$$;
  `);
  const loadFunctions = async (file, names) => {
    const sql = await migration(file);
    for (const name of names) {
      const start = sql.indexOf(`create or replace function ${name}(`);
      if(start < 0) throw new Error(`Missing production function ${name}`);
      const body = sql.indexOf('as $$', start);
      const end = sql.indexOf('$$;', body + 5) + 3;
      await db.exec(sql.slice(start,end));
    }
  };
  await loadFunctions('20260829000100_public_api.sql', [
    'private.api_scope_keys','private.api_scope_descriptions','private.api_try_uuid','private.api_rate_limit_per_minute',
    'private.api_http_status','private.api_error','private.api_translate','private.api_failure',
    'private.api_meter','private.api_deny','private.api_set_rate_headers','private.api_guard','private.api_has_scope',
    'private.api_reject_unknown','private.api_arg_uuid','private.api_arg_text','private.api_arg_int',
    'private.api_encode_cursor','private.api_decode_cursor',
  ]);
  await loadFunctions('20260727000200_security_and_rpcs.sql', [
    'private.begin_rpc_operation','private.finish_rpc_operation','public.set_user_block',
  ]);
  await loadFunctions('20260829000400_developer_limit_requests.sql',['private.api_meter','private.api_guard']);
  // These functions have no PUBLIC grants in the complete production schema.
  await db.exec('revoke all on all functions in schema private from public; revoke all on function public.set_user_block(uuid,boolean,uuid) from public;');
  for(const file of ['20260919000200_boards_foundation.sql','20260919000300_boards_api.sql',
    '20260919000400_board_push.sql','20260919000500_board_branding.sql','20260919000600_board_staff_review.sql',
    '20260919000700_board_account_deletion.sql','20260919000800_board_settings_safe_update.sql']) {
    await db.exec(await migration(file));
  }
  // Profiles already have this column in the shared Boards fixture.
  await db.exec((await migration('20260919000100_message_privacy.sql')).replace(
    'alter table public.profiles add column block_messages boolean not null default false;', ''));
  for(const file of ['20260921000100_public_api_boards.sql','20260921000200_public_api_blocking.sql','20260921000300_public_api_board_media.sql','20260922000200_board_pixel_raster.sql','20260922000300_board_bucket_fill.sql','20260923000100_board_inbox_details.sql','20260923000200_board_admin_cases.sql']) {
    try { await db.exec(await migration(file)); }
    catch(error) { throw new Error(`${file}: ${error.message} at ${error.position}; ${error.where || ''}`); }
  }
  await db.exec(await migration('20260925000100_split_social_privacy.sql'));
  await db.exec(`create trigger friend_requests_enforce_message_privacy
    before insert or update of addressee_id,status on public.friend_requests
    for each row execute function private.enforce_friend_request_message_privacy()`);
}
