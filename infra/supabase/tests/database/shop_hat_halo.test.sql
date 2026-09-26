begin;

set local search_path = public, extensions;

select extensions.plan(9);

create function pg_temp.mii_appearance() returns jsonb
language sql
immutable
as $$
  select '{
    "schemaVersion":1,
    "gender":0,
    "favoriteColor":3,
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

create function pg_temp.hat_appearance(p_hat_type integer) returns jsonb
language sql
immutable
as $$
  select pg_temp.mii_appearance()
    || jsonb_build_object('extHatType', p_hat_type, 'extHatColor', 3);
$$;


select extensions.is(
  (
    select item.mii_hat_type
    from public.shop_items as item
    join public.shop_categories as category on category.id = item.category_id
    where item.slug = 'halo'
      and category.slug = 'hats'
      and item.is_active
  ),
  9,
  'the halo is an active hat item mapped to renderer hat 9'
);

select extensions.is(
  (select item.price_tokens from public.shop_items as item where item.slug = 'halo'),
  90,
  'the halo costs ninety tokens'
);

select extensions.is(
  (select item.image_key from public.shop_items as item where item.slug = 'halo'),
  'shop_item_halo',
  'the halo uses its own shop picture'
);

select extensions.is(
  (
    select count(*)
    from public.shop_items as item
    where item.is_active
      and item.mii_hat_type between 0 and 9
  ),
  10::bigint,
  'ten renderer hats are sold as active shop items'
);

select extensions.throws_ok(
  $$
    update public.shop_items
    set mii_hat_type = 11
    where slug = 'halo';
  $$,
  '23514',
  null,
  'shop items cannot map beyond the eleventh renderer hat'
);

select extensions.ok(
  private.is_sanitized_mii_appearance(pg_temp.hat_appearance(9), 1),
  'a Mii may wear the halo'
);

select extensions.ok(
  not private.is_sanitized_mii_appearance(pg_temp.hat_appearance(11), 1),
  'a Mii cannot wear a hat the renderer does not ship'
);

select extensions.ok(
  not private.owns_mii_hat('98500000-0000-4000-8000-000000000001', 9),
  'the halo is not owned before it is bought'
);

select extensions.ok(
  private.owns_mii_hat('98500000-0000-4000-8000-000000000001', -1),
  'no hat never needs a purchase'
);

select * from extensions.finish();

rollback;
