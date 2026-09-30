begin;

set local search_path = public, extensions;

select extensions.plan(10);

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
values
  (
    '00000000-0000-0000-0000-000000000000',
    '99670000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'opm-discord@pocketpass.test',
    '',
    now(),
    '{"provider":"discord","providers":["discord"]}',
    '{"name":"discord_handle","full_name":"Discord Person","picture":"https://cdn.discordapp.com/avatars/1/a.png","avatar_url":"https://cdn.discordapp.com/avatars/1/a.png","email":"opm-discord@pocketpass.test","provider_id":"123456789012345678","custom_claims":{"global_name":"Discord Person"},"sub":"123456789012345678","iss":"https://discord.com/api"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '99670000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'opm.user@users.pocketpass.xyz',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"username":"opm.user","email":"opm.user@users.pocketpass.xyz","email_verified":true,"sub":"99670000-0000-4000-8000-000000000002"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

select extensions.is(
  (select raw_user_meta_data from auth.users where id = '99670000-0000-4000-8000-000000000001'),
  '{"name": "PocketPass user"}'::jsonb,
  'a new Discord account keeps no Discord details or email in its metadata'
);

select extensions.is(
  (select display_name from public.profiles where user_id = '99670000-0000-4000-8000-000000000001'),
  'Discord Person',
  'the new profile still takes its starting display name from Discord'
);

select extensions.is(
  (select raw_user_meta_data from auth.users where id = '99670000-0000-4000-8000-000000000002'),
  '{"name": "opm.user", "username": "opm.user"}'::jsonb,
  'a username account exposes only its username'
);

update auth.users
set raw_user_meta_data = raw_user_meta_data || '{"name":"discord_handle","email":"opm-discord@pocketpass.test","provider_id":"123456789012345678"}'::jsonb
where id = '99670000-0000-4000-8000-000000000001';

select extensions.is(
  (select raw_user_meta_data from auth.users where id = '99670000-0000-4000-8000-000000000001'),
  '{"name": "PocketPass user"}'::jsonb,
  'Discord details copied in again at sign-in are removed'
);

update public.profiles
set username = 'opm.discord'
where user_id = '99670000-0000-4000-8000-000000000001';

select extensions.is(
  (select raw_user_meta_data from auth.users where id = '99670000-0000-4000-8000-000000000001'),
  '{"name": "opm.discord", "username": "opm.discord"}'::jsonb,
  'choosing a username puts it in the metadata'
);

update auth.users
set raw_user_meta_data = '{"name":"Staff","picture":"https://example.com/x.png"}'::jsonb
where id = '99670000-0000-4000-8000-000000000002';

select extensions.is(
  (select raw_user_meta_data ->> 'name' from auth.users where id = '99670000-0000-4000-8000-000000000002'),
  'opm.user',
  'a user cannot set their own name claim'
);

select extensions.is(
  (select count(*)::integer from auth.users where raw_user_meta_data ? 'email' or raw_user_meta_data ? 'provider_id' or raw_user_meta_data ? 'picture' or raw_user_meta_data ? 'avatar_url'),
  0,
  'no account keeps an email, Discord ID or picture in its metadata'
);

select extensions.is(
  (select count(*)::integer from auth.users where coalesce(raw_user_meta_data ->> 'name', '') = ''),
  0,
  'every account has a name claim, so GoTrue never falls back to the email'
);

select extensions.is(
  (
    select count(*)::integer
    from auth.users as account
    join public.profiles as profile on profile.user_id = account.id
    where profile.username::text <> replace(account.id::text, '-', '')
      and account.raw_user_meta_data ->> 'username' is distinct from profile.username::text
  ),
  0,
  'every account with a username exposes exactly that username'
);

select extensions.is(
  private.api_oidc_scope_descriptions() ->> 'profile',
  'your PocketPass username',
  'the consent screen describes the profile scope as the username'
);

select * from extensions.finish();

rollback;
