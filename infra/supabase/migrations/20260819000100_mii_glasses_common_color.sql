begin;

create or replace function private.is_sanitized_mii_appearance(
  p_appearance jsonb,
  p_schema_version integer
)
returns boolean
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_required_keys constant text[] := array[
    'schemaVersion',
    'gender',
    'favoriteColor',
    'build',
    'height',
    'faceType',
    'skinColor',
    'wrinklesType',
    'makeupType',
    'hairType',
    'hairColor',
    'flipHair',
    'eyeType',
    'eyeColor',
    'eyeScale',
    'eyeVerticalStretch',
    'eyeRotation',
    'eyeSpacing',
    'eyeYPosition',
    'eyebrowType',
    'eyebrowColor',
    'eyebrowScale',
    'eyebrowVerticalStretch',
    'eyebrowRotation',
    'eyebrowSpacing',
    'eyebrowYPosition',
    'noseType',
    'noseScale',
    'noseYPosition',
    'mouthType',
    'mouthColor',
    'mouthScale',
    'mouthHorizontalStretch',
    'mouthYPosition',
    'mustacheType',
    'mustacheScale',
    'mustacheYPosition',
    'beardType',
    'facialHairColor',
    'glassesType',
    'glassesColor',
    'glassesScale',
    'glassesYPosition',
    'moleEnabled',
    'moleScale',
    'moleXPosition',
    'moleYPosition',
    'extHatType',
    'extHatColor',
    'extFacePaintColor'
  ];
  v_optional_keys constant text[] := array[
    'extGlassesColor'
  ];
begin
  if p_schema_version <> 1
    or jsonb_typeof(p_appearance) <> 'object'
    or octet_length(p_appearance::text) > 16384
    or not (p_appearance ?& v_required_keys)
    or exists (
      select 1
      from jsonb_object_keys(p_appearance) as appearance_key(key)
      where not (appearance_key.key = any (v_required_keys || v_optional_keys))
    )
    or jsonb_typeof(p_appearance -> 'flipHair') <> 'boolean'
    or jsonb_typeof(p_appearance -> 'moleEnabled') <> 'boolean'
  then
    return false;
  end if;

  return
    private.mii_json_int_between(p_appearance, 'schemaVersion', 1, 1)
    and private.mii_json_int_between(p_appearance, 'gender', 0, 1)
    and private.mii_json_int_between(p_appearance, 'favoriteColor', 0, 11)
    and private.mii_json_int_between(p_appearance, 'build', 0, 127)
    and private.mii_json_int_between(p_appearance, 'height', 0, 127)
    and private.mii_json_int_between(p_appearance, 'faceType', 0, 11)
    and private.mii_json_int_between(p_appearance, 'skinColor', 0, 5)
    and private.mii_json_int_between(p_appearance, 'wrinklesType', 0, 11)
    and private.mii_json_int_between(p_appearance, 'makeupType', 0, 11)
    and private.mii_json_int_between(p_appearance, 'hairType', 0, 131)
    and private.mii_json_int_between(p_appearance, 'hairColor', 0, 7)
    and private.mii_json_int_between(p_appearance, 'eyeType', 0, 59)
    and private.mii_json_int_between(p_appearance, 'eyeColor', 0, 5)
    and private.mii_json_int_between(p_appearance, 'eyeScale', 0, 7)
    and private.mii_json_int_between(p_appearance, 'eyeVerticalStretch', 0, 6)
    and private.mii_json_int_between(p_appearance, 'eyeRotation', 0, 7)
    and private.mii_json_int_between(p_appearance, 'eyeSpacing', 0, 12)
    and private.mii_json_int_between(p_appearance, 'eyeYPosition', 0, 18)
    and private.mii_json_int_between(p_appearance, 'eyebrowType', 0, 23)
    and private.mii_json_int_between(p_appearance, 'eyebrowColor', 0, 7)
    and private.mii_json_int_between(p_appearance, 'eyebrowScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'eyebrowVerticalStretch', 0, 6)
    and private.mii_json_int_between(p_appearance, 'eyebrowRotation', 0, 11)
    and private.mii_json_int_between(p_appearance, 'eyebrowSpacing', 0, 12)
    and private.mii_json_int_between(p_appearance, 'eyebrowYPosition', 3, 18)
    and private.mii_json_int_between(p_appearance, 'noseType', 0, 17)
    and private.mii_json_int_between(p_appearance, 'noseScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'noseYPosition', 0, 18)
    and private.mii_json_int_between(p_appearance, 'mouthType', 0, 35)
    and private.mii_json_int_between(p_appearance, 'mouthColor', 0, 4)
    and private.mii_json_int_between(p_appearance, 'mouthScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'mouthHorizontalStretch', 0, 6)
    and private.mii_json_int_between(p_appearance, 'mouthYPosition', 0, 18)
    and private.mii_json_int_between(p_appearance, 'mustacheType', 0, 5)
    and private.mii_json_int_between(p_appearance, 'mustacheScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'mustacheYPosition', 0, 16)
    and private.mii_json_int_between(p_appearance, 'beardType', 0, 5)
    and private.mii_json_int_between(p_appearance, 'facialHairColor', 0, 7)
    and private.mii_json_int_between(p_appearance, 'glassesType', 0, 19)
    and private.mii_json_int_between(p_appearance, 'glassesColor', 0, 5)
    and private.mii_json_int_between(p_appearance, 'glassesScale', 0, 7)
    and private.mii_json_int_between(p_appearance, 'glassesYPosition', 0, 20)
    and private.mii_json_int_between(p_appearance, 'moleScale', 0, 8)
    and private.mii_json_int_between(p_appearance, 'moleXPosition', 0, 16)
    and private.mii_json_int_between(p_appearance, 'moleYPosition', 0, 30)
    and private.mii_json_int_between(p_appearance, 'extHatType', -1, 9)
    and private.mii_json_int_between(p_appearance, 'extHatColor', -1, 11)
    and private.mii_json_int_between(p_appearance, 'extFacePaintColor', -1, 11)
    and (
      not (p_appearance ? 'extGlassesColor')
      or private.mii_json_int_between(p_appearance, 'extGlassesColor', -1, 99)
    );
end;
$$;

commit;
