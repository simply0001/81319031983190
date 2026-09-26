begin;

set local search_path = public, extensions;

select extensions.plan(9);

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
    '98400000-0000-4000-8000-000000000001',
    'authenticated',
    'authenticated',
    'shopper@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Shopper"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  ),
  (
    '00000000-0000-0000-0000-000000000000',
    '98400000-0000-4000-8000-000000000002',
    'authenticated',
    'authenticated',
    'other-shopper@pocketpass.test',
    extensions.crypt('test-only', extensions.gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}',
    '{"display_name":"Other Shopper"}',
    now(),
    now(),
    '',
    '',
    '',
    ''
  );

select extensions.is(
  (
    select token_balance.balance
    from public.token_balances as token_balance
    where token_balance.user_id = '98400000-0000-4000-8000-000000000001'
  ),
  0,
  'a new account opens with an empty token balance'
);

select extensions.throws_ok(
  $$
    update public.token_balances
    set balance = -1
    where user_id = '98400000-0000-4000-8000-000000000001';
  $$,
  '23514',
  null,
  'a token balance cannot go negative'
);

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98400000-0000-4000-8000-000000000001',
  true
);
select pg_catalog.set_config('request.jwt.claim.role', 'authenticated', true);

select extensions.is(
  (select count(*) from public.token_balances),
  1::bigint,
  'a shopper reads their own balance and nobody else''s'
);

select extensions.is(
  (
    select token_balance.user_id
    from public.token_balances as token_balance
  ),
  '98400000-0000-4000-8000-000000000001'::uuid,
  'the visible balance is the shopper''s own row'
);

select extensions.is(
  (
    select category.title
    from public.shop_categories as category
    where category.slug = 'hats'
  ),
  'Hats',
  'an active catalog category is readable'
);

select extensions.is(
  (
    select item.price_tokens
    from public.shop_items as item
    where item.slug = 'baseball_cap'
  ),
  20,
  'an active catalog item is readable with its price'
);

reset role;

update public.shop_categories
set is_active = false
where slug = 'hats';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98400000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    select count(*)
    from public.shop_categories as category
    where category.slug = 'hats'
  ),
  0::bigint,
  'a retired category is hidden'
);

select extensions.is(
  (
    select count(*)
    from public.shop_items as item
    where item.slug = 'baseball_cap'
  ),
  0::bigint,
  'retiring a category also hides its items'
);

reset role;

update public.shop_categories
set is_active = true
where slug = 'hats';

update public.shop_items
set is_active = false
where slug = 'baseball_cap';

set local role authenticated;
select pg_catalog.set_config(
  'request.jwt.claim.sub',
  '98400000-0000-4000-8000-000000000001',
  true
);

select extensions.is(
  (
    select count(*)
    from public.shop_items as item
    where item.slug = 'baseball_cap'
  ),
  0::bigint,
  'a retired item is hidden even when its category is active'
);

reset role;

select * from extensions.finish();

rollback;
