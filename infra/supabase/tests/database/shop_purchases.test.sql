begin;

set local search_path = public, extensions;

select extensions.plan(31);

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

create function pg_temp.item_id(p_slug text) returns uuid
language sql
stable
security definer
as $$
  select item.id from public.shop_items as item where item.slug = p_slug;
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
values
  (
    '00000000-0000-0000-0000-000000000000',
    '98500000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'hat-buyer@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Hat Buyer"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98500000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'hat-watcher@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Hat Watcher"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

insert into storage.objects (bucket_id, name, owner, owner_id, metadata)
select
  'avatars',
  '98500000-0000-4000-8000-000000000001/mii-r' || step || '-98500000-0000-4000-8000-0000000000' || (20 + step) || '.png',
  '98500000-0000-4000-8000-000000000001',
  '98500000-0000-4000-8000-000000000001',
  '{"mimetype":"image/png","size":1024}'
from generate_series(1, 4) as step;

update public.bingo_goals set is_active = false
where slug not in (
  'own_hat', 'buy_shop_item', 'own_3_items',
  'meet_poland', 'meet_japan', 'meet_brazil', 'meet_canada', 'meet_egypt',
  'meet_france', 'meet_india', 'meet_mexico', 'meet_italy', 'meet_kenya',
  'meet_spain', 'meet_germany', 'meet_sweden', 'meet_norway', 'meet_australia',
  'meet_south_korea', 'meet_china', 'meet_argentina', 'meet_nigeria',
  'meet_greece', 'meet_turkey'
);

select extensions.is(
  (
    select count(*)
    from public.shop_items as item
    where item.is_active
      and item.mii_hat_type between 0 and 10
  ),
  11::bigint,
  'every renderer hat is sold as an active shop item'
);

select extensions.is(
  (select count(distinct item.mii_hat_type) from public.shop_items as item),
  11::bigint,
  'each hat item maps to a distinct Mii hat type'
);

select extensions.is(
  (select item.price_tokens from public.shop_items as item where item.slug = 'baseball_cap'),
  20,
  'the baseball cap costs twenty tokens'
);

select extensions.throws_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('baseball_cap'),
      '98500000-0000-4000-8000-000000000011'
    );
  $$,
  '42501',
  'Authentication required',
  'buying rejects an unauthenticated caller'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98500000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.throws_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('baseball_cap'),
      '98500000-0000-4000-8000-000000000011'
    );
  $$,
  'PT402',
  'Not enough tokens',
  'an empty balance cannot buy a hat'
);

select extensions.is(
  (select count(*) from public.user_shop_items),
  0::bigint,
  'a rejected purchase leaves no ownership behind'
);

reset role;

update public.token_balances
set balance = 100
where user_id = '98500000-0000-4000-8000-000000000001';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98500000-0000-4000-8000-000000000001',
  true
);

select extensions.lives_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('baseball_cap'),
      '98500000-0000-4000-8000-000000000011'
    );
  $$,
  'a funded shopper buys the baseball cap'
);

select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '98500000-0000-4000-8000-000000000001'
  ),
  80,
  'buying debits the price from the balance'
);

select extensions.is(
  (
    select owned.price_paid
    from public.user_shop_items as owned
    where owned.item_id = pg_temp.item_id('baseball_cap')
  ),
  20,
  'the ownership row records the price paid'
);

select extensions.is(
  (
    select owned.client_operation_id
    from public.user_shop_items as owned
    where owned.item_id = pg_temp.item_id('baseball_cap')
  ),
  '98500000-0000-4000-8000-000000000011'::uuid,
  'the ownership row records the client operation'
);

select extensions.lives_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('baseball_cap'),
      '98500000-0000-4000-8000-000000000011'
    );
  $$,
  'replaying the same operation succeeds'
);

select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '98500000-0000-4000-8000-000000000001'
  ),
  80,
  'a replay does not charge twice'
);

select extensions.is(
  (select count(*) from public.user_shop_items),
  1::bigint,
  'a replay does not duplicate ownership'
);

select extensions.throws_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('baseball_cap'),
      '98500000-0000-4000-8000-000000000012'
    );
  $$,
  'PT409',
  'You already own this item',
  'an owned item cannot be bought again'
);

select extensions.throws_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('top_hat'),
      '98500000-0000-4000-8000-000000000011'
    );
  $$,
  '22023',
  null,
  'an operation id cannot be reused for a different item'
);

reset role;

update public.shop_items set is_active = false where slug = 'top_hat';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98500000-0000-4000-8000-000000000001',
  true
);

select extensions.throws_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('top_hat'),
      '98500000-0000-4000-8000-000000000013'
    );
  $$,
  'PT410',
  'This item is no longer available',
  'a retired item cannot be bought'
);

reset role;

update public.shop_items set is_active = true where slug = 'top_hat';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98500000-0000-4000-8000-000000000002',
  true
);

select extensions.is(
  (select count(*) from public.user_shop_items),
  0::bigint,
  'another shopper cannot see someone else''s purchases'
);

select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98500000-0000-4000-8000-000000000001',
  true
);

select extensions.lives_ok(
  $$
    select * from public.save_profile_mii_slot(
      '98500000-0000-4000-8000-000000000021',
      1,
      1,
      pg_temp.hat_appearance(0),
      '98500000-0000-4000-8000-000000000001/mii-r1-98500000-0000-4000-8000-000000000021.png',
      null,
      1
    );
  $$,
  'a Mii can wear a purchased hat'
);

select extensions.throws_ok(
  $$
    select * from public.save_profile_mii_slot(
      '98500000-0000-4000-8000-000000000022',
      2,
      1,
      pg_temp.hat_appearance(2),
      '98500000-0000-4000-8000-000000000001/mii-r2-98500000-0000-4000-8000-000000000022.png',
      null,
      1
    );
  $$,
  'PT403',
  'That hat has not been purchased',
  'a Mii cannot wear a hat that was not bought'
);

select extensions.throws_ok(
  $$
    select * from public.save_profile_mii_slot(
      '98500000-0000-4000-8000-000000000023',
      3,
      1,
      pg_temp.hat_appearance(11),
      '98500000-0000-4000-8000-000000000001/mii-r3-98500000-0000-4000-8000-000000000023.png',
      null,
      1
    );
  $$,
  '22023',
  null,
  'a hat type beyond the renderer range is rejected'
);

select extensions.lives_ok(
  $$
    select * from public.save_profile_mii_slot(
      '98500000-0000-4000-8000-000000000024',
      4,
      1,
      pg_temp.hat_appearance(-1),
      '98500000-0000-4000-8000-000000000001/mii-r4-98500000-0000-4000-8000-000000000024.png',
      null,
      1
    );
  $$,
  'a Mii can take its hat off'
);

select extensions.is(
  (
    select cell.completed
    from public.get_bingo_card() as cell
    where cell.slug = 'buy_shop_item'
  ),
  true,
  'buying any item stamps the shopping goal'
);

select extensions.is(
  (
    select cell.completed
    from public.get_bingo_card() as cell
    where cell.slug = 'own_hat'
  ),
  true,
  'owning a hat stamps the hat goal'
);

select extensions.is(
  (
    select cell.progress_current || '/' || cell.progress_target
    from public.get_bingo_card() as cell
    where cell.slug = 'own_3_items'
  ),
  '1/3',
  'the collector goal counts owned items'
);

select extensions.lives_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('ribbons'),
      '98500000-0000-4000-8000-000000000014'
    );
  $$,
  'the shopper buys the ribbons'
);

select extensions.lives_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('bow'),
      '98500000-0000-4000-8000-000000000015'
    );
  $$,
  'the shopper buys the bow with their last tokens'
);

select extensions.is(
  (
    select cell.completed
    from public.get_bingo_card() as cell
    where cell.slug = 'own_3_items'
  ),
  true,
  'three owned items stamp the collector goal'
);

reset role;

select extensions.ok(
  private.is_sanitized_mii_appearance(pg_temp.hat_appearance(10), 1),
  'the last renderer hat is a valid appearance'
);

select extensions.ok(
  not private.is_sanitized_mii_appearance(pg_temp.hat_appearance(11), 1),
  'a twelfth hat type is not a valid appearance'
);

update public.token_balances
set balance = 100
where user_id = '98500000-0000-4000-8000-000000000002';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98500000-0000-4000-8000-000000000002',
  true
);

select extensions.lives_ok(
  $$
    select * from public.buy_shop_item(
      pg_temp.item_id('straw_hat'),
      '98500000-0000-4000-8000-000000000031'
    );
  $$,
  'the second shopper buys the straw hat'
);

reset role;

delete from public.profiles
where user_id = '98500000-0000-4000-8000-000000000002';

select extensions.is(
  (
    select count(*)
    from public.user_shop_items as owned
    where owned.user_id = '98500000-0000-4000-8000-000000000002'
  ),
  0::bigint,
  'deleting a profile removes its purchases'
);

select * from extensions.finish();

rollback;
