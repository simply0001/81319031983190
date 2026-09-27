begin;

set local search_path = public, extensions;

select extensions.plan(12);

create function pg_temp.mii_appearance() returns jsonb
language sql
immutable
as $$
  select '{
    "schemaVersion":1,
    "gender":0,
    "favoriteColor":0,
    "build":64,
    "height":64,
    "faceType":0,
    "skinColor":0,
    "wrinklesType":0,
    "makeupType":0,
    "hairType":33,
    "hairColor":1,
    "flipHair":false,
    "eyeType":2,
    "eyeColor":0,
    "eyeScale":4,
    "eyeVerticalStretch":3,
    "eyeRotation":4,
    "eyeSpacing":2,
    "eyeYPosition":12,
    "eyebrowType":6,
    "eyebrowColor":1,
    "eyebrowScale":4,
    "eyebrowVerticalStretch":3,
    "eyebrowRotation":6,
    "eyebrowSpacing":2,
    "eyebrowYPosition":10,
    "noseType":1,
    "noseScale":4,
    "noseYPosition":9,
    "mouthType":23,
    "mouthColor":0,
    "mouthScale":4,
    "mouthHorizontalStretch":3,
    "mouthYPosition":13,
    "mustacheType":0,
    "mustacheScale":4,
    "mustacheYPosition":10,
    "beardType":0,
    "facialHairColor":0,
    "glassesType":0,
    "glassesColor":0,
    "glassesScale":4,
    "glassesYPosition":10,
    "moleEnabled":false,
    "moleScale":4,
    "moleXPosition":2,
    "moleYPosition":20,
    "extHatType":-1,
    "extHatColor":-1,
    "extFacePaintColor":-1
  }'::jsonb;
$$;

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
values (
  '00000000-0000-0000-0000-000000000000',
  '98100000-0000-4000-8000-000000000001',
  'authenticated',
  'authenticated',
  'mii-slots@pocketpass.test',
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  '{"display_name":"Mii Slots"}',
  now(),
  now(),
  '',
  '',
  '',
  ''
);

insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
values
  (
    'avatars',
    '98100000-0000-4000-8000-000000000001/mii-r1-98100000-0000-4000-8000-000000000011.png',
    '98100000-0000-4000-8000-000000000001',
    '98100000-0000-4000-8000-000000000001',
    '{"mimetype":"image/png","size":1024}'
  ),
  (
    'avatars',
    '98100000-0000-4000-8000-000000000001/mii-r1-98100000-0000-4000-8000-000000000012.png',
    '98100000-0000-4000-8000-000000000001',
    '98100000-0000-4000-8000-000000000001',
    '{"mimetype":"image/png","size":1024}'
  ),
  (
    'avatars',
    '98100000-0000-4000-8000-000000000001/mii-r2-98100000-0000-4000-8000-000000000014.png',
    '98100000-0000-4000-8000-000000000001',
    '98100000-0000-4000-8000-000000000001',
    '{"mimetype":"image/png","size":1024}'
  );

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98100000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.lives_ok(
  $$
    select * from public.save_profile_mii_slot(
      '98100000-0000-4000-8000-000000000011',
      1,
      1,
      pg_temp.mii_appearance(),
      '98100000-0000-4000-8000-000000000001/mii-r1-98100000-0000-4000-8000-000000000011.png',
      null,
      1
    );
  $$,
  'saving a Mii into slot 1 succeeds'
);

select extensions.ok(
  (
    select mii.is_active
    from public.profile_miis as mii
    where mii.user_id = '98100000-0000-4000-8000-000000000001'
      and mii.slot = 1
  ),
  'the first saved Mii becomes the active slot'
);

select extensions.is(
  (
    select profile.avatar_path
    from public.profiles as profile
    where profile.user_id = '98100000-0000-4000-8000-000000000001'
  ),
  '98100000-0000-4000-8000-000000000001/mii-r1-98100000-0000-4000-8000-000000000011.png',
  'the active slot publishes the public avatar path'
);

select extensions.lives_ok(
  $$
    select * from public.save_profile_mii_slot(
      '98100000-0000-4000-8000-000000000012',
      1,
      1,
      pg_temp.mii_appearance(),
      '98100000-0000-4000-8000-000000000001/mii-r1-98100000-0000-4000-8000-000000000012.png',
      null,
      2
    );
  $$,
  'saving a second Mii into slot 2 succeeds'
);

select extensions.ok(
  not (
    select mii.is_active
    from public.profile_miis as mii
    where mii.user_id = '98100000-0000-4000-8000-000000000001'
      and mii.slot = 2
  ),
  'saving an additional slot does not steal the active flag'
);

select extensions.is(
  (
    select profile.avatar_path
    from public.profiles as profile
    where profile.user_id = '98100000-0000-4000-8000-000000000001'
  ),
  '98100000-0000-4000-8000-000000000001/mii-r1-98100000-0000-4000-8000-000000000011.png',
  'saving an inactive slot leaves the public avatar untouched'
);

select extensions.lives_ok(
  $$
    select * from public.set_active_mii_slot(
      '98100000-0000-4000-8000-000000000013',
      2
    );
  $$,
  'activating a populated slot succeeds'
);

select extensions.is(
  (
    select profile.avatar_path
    from public.profiles as profile
    where profile.user_id = '98100000-0000-4000-8000-000000000001'
  ),
  '98100000-0000-4000-8000-000000000001/mii-r1-98100000-0000-4000-8000-000000000012.png',
  'activating a slot republishes its portrait as the public avatar'
);

select extensions.is(
  (
    select count(*)
    from public.profile_miis as mii
    where mii.user_id = '98100000-0000-4000-8000-000000000001'
      and mii.is_active
  ),
  1::bigint,
  'exactly one slot stays active per account'
);

select extensions.throws_ok(
  $$
    select * from public.set_active_mii_slot(
      '98100000-0000-4000-8000-000000000019',
      4
    );
  $$,
  '22023',
  'Mii slot must be between 1 and 3',
  'a slot outside 1 through 3 is rejected'
);

select extensions.lives_ok(
  $$
    select * from public.save_profile_mii_slot(
      '98100000-0000-4000-8000-000000000014',
      2,
      1,
      pg_temp.mii_appearance(),
      '98100000-0000-4000-8000-000000000001/mii-r2-98100000-0000-4000-8000-000000000014.png',
      null,
      2
    );
  $$,
  'saving the active slot again succeeds'
);

select extensions.is(
  (
    select mii.revision
    from public.profile_miis as mii
    where mii.user_id = '98100000-0000-4000-8000-000000000001'
      and mii.slot = 2
  ),
  2::bigint,
  'the save writes the new revision to that slot'
);

select * from extensions.finish();

rollback;
