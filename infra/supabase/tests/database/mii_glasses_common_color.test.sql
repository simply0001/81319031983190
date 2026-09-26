begin;

set local search_path = public, extensions;

select extensions.plan(6);

create temporary table appearance_fixture as
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
}'::jsonb as appearance;

select extensions.ok(
  private.is_sanitized_mii_appearance((select appearance from appearance_fixture), 1),
  'appearances from clients without extGlassesColor stay valid'
);

select extensions.ok(
  private.is_sanitized_mii_appearance(
    (select appearance || '{"extGlassesColor":-1}'::jsonb from appearance_fixture),
    1
  ),
  'extGlassesColor -1 means the legacy glasses color'
);

select extensions.ok(
  private.is_sanitized_mii_appearance(
    (select appearance || '{"extGlassesColor":99}'::jsonb from appearance_fixture),
    1
  ),
  'extGlassesColor accepts the last common color index'
);

select extensions.ok(
  not private.is_sanitized_mii_appearance(
    (select appearance || '{"extGlassesColor":100}'::jsonb from appearance_fixture),
    1
  ),
  'extGlassesColor rejects indexes past the common color table'
);

select extensions.ok(
  not private.is_sanitized_mii_appearance(
    (select appearance || '{"extGlassesColor":"8"}'::jsonb from appearance_fixture),
    1
  ),
  'extGlassesColor must be a number'
);

select extensions.ok(
  not private.is_sanitized_mii_appearance(
    (select appearance || '{"extGlassesFrameColor":3}'::jsonb from appearance_fixture),
    1
  ),
  'unknown appearance keys are still rejected'
);

select * from extensions.finish();

rollback;
