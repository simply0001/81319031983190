begin;

set local search_path = public, extensions;

select extensions.plan(24);

create function pg_temp.item_id(p_slug text) returns uuid
language sql
stable
security definer
as $$
  select id from public.shop_items where slug = p_slug;
$$;

create function pg_temp.shopping_progress() returns integer[]
language sql
stable
security definer
as $$
  select array[
    private.bingo_goal_progress('99510000-0000-4000-8000-000000000001', 'buy_shop_item', 1, null, now() - interval '1 day'),
    private.bingo_goal_progress('99510000-0000-4000-8000-000000000001', 'own_hat', 1, null, now() - interval '1 day'),
    private.bingo_goal_progress('99510000-0000-4000-8000-000000000001', 'own_shop_items', 3, null, now() - interval '1 day')
  ];
$$;

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, email_change, email_change_token_new, recovery_token
) values (
  '00000000-0000-0000-0000-000000000000',
  '99510000-0000-4000-8000-000000000001',
  'authenticated', 'authenticated', 'supporter-shop@pocketpass.test',
  extensions.crypt('test-only', extensions.gen_salt('bf')), now(),
  '{"provider":"email","providers":["email"]}', '{"display_name":"Supporter Shopper pgtap"}',
  now(), now(), '', '', '', ''
);

insert into public.supporter_status (user_id, active_until, source)
values ('99510000-0000-4000-8000-000000000001', now() + interval '30 days', 'kofi');

update public.token_balances set balance = 500
where user_id = '99510000-0000-4000-8000-000000000001';

select extensions.ok(
  private.owns_mii_hat('99510000-0000-4000-8000-000000000001', 2),
  'the subscription grants access to the top hat before buying'
);

select extensions.is(
  (select count(*) from public.user_shop_items where user_id = '99510000-0000-4000-8000-000000000001'),
  0::bigint,
  'subscription access does not create permanent ownership'
);

select extensions.is(pg_temp.shopping_progress(), array[0, 0, 0], 'subscription access alone does not advance shopping goals');

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '99510000-0000-4000-8000-000000000001', true);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.lives_ok(
  $$select * from public.buy_shop_item(pg_temp.item_id('top_hat'), '99510000-0000-4000-8000-000000000011')$$,
  'an active subscriber can buy an included hat'
);

select extensions.is((select balance from public.token_balances), 380, 'the subscriber pays the normal token price');
select extensions.is(
  (select price_paid from public.user_shop_items where item_id = pg_temp.item_id('top_hat')),
  120,
  'the purchase records permanent paid ownership'
);
select extensions.is(pg_temp.shopping_progress(), array[1, 1, 1], 'a subscriber purchase advances shopping and collection goals');

select extensions.lives_ok(
  $$select * from public.buy_shop_item(pg_temp.item_id('top_hat'), '99510000-0000-4000-8000-000000000011')$$,
  'retrying a subscriber purchase is idempotent'
);
select extensions.is((select balance from public.token_balances), 380, 'retrying does not charge again');
select extensions.throws_ok(
  $$select * from public.buy_shop_item(pg_temp.item_id('top_hat'), '99510000-0000-4000-8000-000000000012')$$,
  'PT409', 'You already own this item', 'a subscriber cannot buy a permanently owned item twice'
);
select extensions.is((select count(*) from public.user_shop_items), 1::bigint, 'retries do not duplicate ownership');

select extensions.lives_ok(
  $$select * from public.buy_shop_item(pg_temp.item_id('ribbons'), '99510000-0000-4000-8000-000000000013')$$,
  'the subscriber buys a second included item'
);
select extensions.lives_ok(
  $$select * from public.buy_shop_item(pg_temp.item_id('bow'), '99510000-0000-4000-8000-000000000014')$$,
  'the subscriber buys a third included item'
);
select extensions.is(pg_temp.shopping_progress(), array[3, 3, 3], 'three subscriber purchases satisfy the collector goal');
select extensions.is((select balance from public.token_balances), 300, 'all three purchases charge their token prices');

reset role;
update public.token_balances set balance = 0 where user_id = '99510000-0000-4000-8000-000000000001';
set local role authenticated;

select extensions.throws_ok(
  $$select * from public.buy_shop_item(pg_temp.item_id('baseball_cap'), '99510000-0000-4000-8000-000000000015')$$,
  'PT402', 'Not enough tokens', 'subscription access cannot replace payment for permanent ownership'
);
select extensions.is(
  (select count(*) from public.user_shop_items where item_id = pg_temp.item_id('baseball_cap')),
  0::bigint,
  'a rejected purchase does not grant permanent ownership'
);

reset role;
update public.supporter_status set active_until = now() - interval '1 second'
where user_id = '99510000-0000-4000-8000-000000000001';

select extensions.ok(not private.is_supporter('99510000-0000-4000-8000-000000000001'), 'the subscription has ended');
select extensions.ok(private.owns_mii_hat('99510000-0000-4000-8000-000000000001', 2), 'the purchased top hat stays wearable after expiry');
select extensions.ok(not private.owns_mii_hat('99510000-0000-4000-8000-000000000001', 0), 'an unpurchased paid hat loses subscription access');
select extensions.ok(private.owns_mii_hat('99510000-0000-4000-8000-000000000001', 10), 'free hats remain wearable after expiry');
select extensions.is(
  (select count(*) from public.user_shop_items where user_id = '99510000-0000-4000-8000-000000000001'),
  3::bigint,
  'all purchases remain owned after expiry'
);
select extensions.is(pg_temp.shopping_progress(), array[3, 3, 3], 'subscription expiry preserves purchased-item goal progress');

delete from public.supporter_status where user_id = '99510000-0000-4000-8000-000000000001';
select extensions.ok(private.owns_mii_hat('99510000-0000-4000-8000-000000000001', 2), 'removing subscription status cannot revoke a purchase');

select * from extensions.finish();

rollback;
