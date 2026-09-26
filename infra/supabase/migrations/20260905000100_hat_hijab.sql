begin;

alter table public.shop_items
  drop constraint if exists shop_items_mii_hat_type_range;

alter table public.shop_items
  add constraint shop_items_mii_hat_type_range
    check (mii_hat_type is null or mii_hat_type between 0 and 10);

insert into public.shop_items (
  category_id,
  slug,
  name,
  price_tokens,
  image_key,
  sort_order,
  mii_hat_type
)
select
  category.id,
  'hijab',
  'Hijab',
  0,
  'shop_item_hijab',
  10,
  10
from public.shop_categories as category
where category.slug = 'hats'
on conflict (slug) do update
set
  name = excluded.name,
  price_tokens = excluded.price_tokens,
  image_key = excluded.image_key,
  sort_order = excluded.sort_order,
  mii_hat_type = excluded.mii_hat_type,
  is_active = true;

create or replace function private.owns_mii_hat(p_user_id uuid, p_hat_type integer)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select p_hat_type is null
    or p_hat_type < 0
    or exists (
      select 1
      from public.shop_items as item
      where item.mii_hat_type = p_hat_type
        and item.is_active
        and item.price_tokens = 0
    )
    or exists (
      select 1
      from public.user_shop_items as owned
      join public.shop_items as item on item.id = owned.item_id
      where owned.user_id = p_user_id
        and item.mii_hat_type = p_hat_type
    )
    or private.is_supporter(p_user_id);
$$;

revoke all on function private.owns_mii_hat(uuid, integer) from public;

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
    'extGlassesColor',
    'extHairColor',
    'extEyebrowColor',
    'extMouthColor',
    'extFacialHairColor'
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
    and private.mii_json_int_between(p_appearance, 'skinColor', 0, 9)
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
    and private.mii_json_int_between(p_appearance, 'extHatType', -1, 10)
    and private.mii_json_int_between(p_appearance, 'extHatColor', -1, 11)
    and private.mii_json_int_between(p_appearance, 'extFacePaintColor', -1, 11)
    and private.mii_optional_common_color(p_appearance, 'extGlassesColor')
    and private.mii_optional_common_color(p_appearance, 'extHairColor')
    and private.mii_optional_common_color(p_appearance, 'extEyebrowColor')
    and private.mii_optional_common_color(p_appearance, 'extMouthColor')
    and private.mii_optional_common_color(p_appearance, 'extFacialHairColor');
end;
$$;

commit;
