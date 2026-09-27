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
    '10000000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'ash@pocketpass.test',
    extensions.crypt('pocketpass-dev-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Ash"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '10000000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'spob@pocketpass.test',
    extensions.crypt('pocketpass-dev-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Spob"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '10000000-0000-4000-8000-000000000003',
    'authenticated',
    'authenticated',
    'sans@pocketpass.test',
    extensions.crypt('pocketpass-dev-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Sans"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '10000000-0000-4000-8000-000000000004',
    'authenticated',
    'authenticated',
    'matt@pocketpass.test',
    extensions.crypt('pocketpass-dev-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Matt"}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
  )
on conflict (id) do nothing;

insert into auth.identities (
  id,
  user_id,
  provider_id,
  identity_data,
  provider,
  last_sign_in_at,
  created_at,
  updated_at
)
values
  (
    '11000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000001',
    'ash@pocketpass.test',
    '{"sub":"10000000-0000-4000-8000-000000000001","email":"ash@pocketpass.test"}'::jsonb,
    'email',
    now(),
    now(),
    now()
  ),
  (
    '11000000-0000-4000-8000-000000000002',
    '10000000-0000-4000-8000-000000000002',
    'spob@pocketpass.test',
    '{"sub":"10000000-0000-4000-8000-000000000002","email":"spob@pocketpass.test"}'::jsonb,
    'email',
    now(),
    now(),
    now()
  ),
  (
    '11000000-0000-4000-8000-000000000003',
    '10000000-0000-4000-8000-000000000003',
    'sans@pocketpass.test',
    '{"sub":"10000000-0000-4000-8000-000000000003","email":"sans@pocketpass.test"}'::jsonb,
    'email',
    now(),
    now(),
    now()
  ),
  (
    '11000000-0000-4000-8000-000000000004',
    '10000000-0000-4000-8000-000000000004',
    'matt@pocketpass.test',
    '{"sub":"10000000-0000-4000-8000-000000000004","email":"matt@pocketpass.test"}'::jsonb,
    'email',
    now(),
    now(),
    now()
  )
on conflict do nothing;

update public.profiles
set
  username = 'ash',
  display_name = 'Ash',
  bio = 'Always ready for the next encounter.',
  age = 43,
  country_code = 'US',
  last_seen_at = now()
where user_id = '10000000-0000-4000-8000-000000000001';

update public.profiles
set
  username = 'spob',
  display_name = 'Spob',
  bio = 'Playing nearby.',
  age = 27,
  country_code = 'SE',
  last_seen_at = now() - interval '2 minutes'
where user_id = '10000000-0000-4000-8000-000000000002';

update public.profiles
set
  username = 'sans',
  display_name = 'Sans',
  bio = 'Up for a game.',
  age = 31,
  country_code = 'GB',
  last_seen_at = now() - interval '20 minutes'
where user_id = '10000000-0000-4000-8000-000000000003';

update public.profiles
set
  username = 'matt',
  display_name = 'Matt',
  bio = 'See you on the leaderboard.',
  age = 35,
  country_code = 'CA',
  last_seen_at = now() - interval '1 day'
where user_id = '10000000-0000-4000-8000-000000000004';

insert into public.friendships (
  user_low,
  user_high,
  created_by,
  created_at
)
values (
  '10000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000002',
  '10000000-0000-4000-8000-000000000001',
  now() - interval '30 days'
)
on conflict (user_low, user_high) do nothing;

insert into public.friend_requests (
  id,
  requester_id,
  addressee_id,
  status,
  client_operation_id,
  created_at
)
values (
  '12000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000003',
  '10000000-0000-4000-8000-000000000001',
  'pending',
  '13000000-0000-4000-8000-000000000001',
  now() - interval '10 minutes'
)
on conflict do nothing;

insert into public.conversations (
  id,
  kind,
  created_by,
  direct_user_low,
  direct_user_high,
  created_at,
  updated_at
)
values (
  '20000000-0000-4000-8000-000000000001',
  'direct',
  '10000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000002',
  now() - interval '7 days',
  now() - interval '5 minutes'
)
on conflict (id) do nothing;

insert into public.conversation_members (
  conversation_id,
  user_id,
  role,
  joined_at,
  last_read_at
)
values
  (
    '20000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000001',
    'owner',
    now() - interval '7 days',
    now() - interval '5 minutes'
  ),
  (
    '20000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000002',
    'member',
    now() - interval '7 days',
    now() - interval '8 minutes'
  )
on conflict (conversation_id, user_id) do nothing;

insert into public.messages (
  id,
  conversation_id,
  sender_id,
  client_operation_id,
  body,
  created_at
)
values
  (
    '21000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000002',
    '22000000-0000-4000-8000-000000000001',
    'Want to play something?',
    now() - interval '8 minutes'
  ),
  (
    '21000000-0000-4000-8000-000000000002',
    '20000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000001',
    '22000000-0000-4000-8000-000000000002',
    'Sure — give me five minutes.',
    now() - interval '5 minutes'
  )
on conflict (id) do nothing;

insert into public.interaction_events (
  id,
  actor_id,
  subject_user_id,
  event_type,
  client_operation_id,
  payload,
  occurred_at,
  created_at
)
values (
  '30000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000002',
  'nearby_encounter',
  '31000000-0000-4000-8000-000000000001',
  '{"source":"development_fixture"}'::jsonb,
  now() - interval '15 minutes',
  now() - interval '15 minutes'
)
on conflict (id) do nothing;
