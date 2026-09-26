begin;

set local search_path = public, extensions;

select extensions.plan(5);

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
  '98000000-0000-4000-8000-000000000001',
  'authenticated',
  'authenticated',
  'mii-publication@pocketpass.test',
  extensions.crypt('test-only', extensions.gen_salt('bf')),
  now(),
  '{"provider":"email","providers":["email"]}',
  '{"display_name":"Mii Publisher"}',
  now(),
  now(),
  '',
  '',
  '',
  ''
);

insert into storage.objects (
  bucket_id,
  name,
  owner,
  owner_id,
  metadata
)
values (
  'avatars',
  '98000000-0000-4000-8000-000000000001/mii-r1-98000000-0000-4000-8000-000000000011.png',
  '98000000-0000-4000-8000-000000000001',
  '98000000-0000-4000-8000-000000000001',
  '{"mimetype":"image/png","size":1024}'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98000000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.lives_ok(
  $$
    select *
    from public.save_profile_mii(
      '98000000-0000-4000-8000-000000000011',
      1,
      1,
      '{
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
      }'::jsonb,
      '98000000-0000-4000-8000-000000000001/mii-r1-98000000-0000-4000-8000-000000000011.png',
      'AQID'
    );
  $$,
  'a valid Mii portrait publication succeeds'
);

select extensions.is(
  (
    select profile.avatar_path
    from public.profiles as profile
    where profile.user_id = '98000000-0000-4000-8000-000000000001'
  ),
  '98000000-0000-4000-8000-000000000001/mii-r1-98000000-0000-4000-8000-000000000011.png',
  'publication updates the public avatar path'
);

select extensions.is(
  (
    select mii.revision
    from public.profile_miis as mii
    where mii.user_id = '98000000-0000-4000-8000-000000000001'
  ),
  1::bigint,
  'publication stores the private Mii revision'
);

select extensions.lives_ok(
  $$
    select *
    from public.save_profile_mii(
      '98000000-0000-4000-8000-000000000011',
      1,
      1,
      '{
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
      }'::jsonb,
      '98000000-0000-4000-8000-000000000001/mii-r1-98000000-0000-4000-8000-000000000011.png',
      'AQID'
    );
  $$,
  'replaying the same Mii publication remains idempotent'
);

select extensions.has_trigger(
  'public',
  'profiles',
  'profiles_broadcast_friend_change',
  'profile avatar changes invalidate accepted friends'
);

select * from extensions.finish();

rollback;
