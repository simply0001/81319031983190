begin;

-- Token balances are per account. Nothing can earn or spend yet, so every account is
-- opened with a starter grant that matches the balance the shop design was drawn with;
-- replace this with a real award when games start paying out.
create table public.token_balances (
  user_id uuid primary key references public.profiles (user_id) on delete cascade,
  balance integer not null default 0,
  updated_at timestamptz not null default now(),
  constraint token_balances_non_negative check (balance >= 0)
);

create or replace function private.ensure_token_balance(p_user_id uuid)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_balance integer;
begin
  insert into public.token_balances (user_id, balance)
  values (p_user_id, 22)
  on conflict (user_id) do nothing;

  select token_balance.balance
  into v_balance
  from public.token_balances as token_balance
  where token_balance.user_id = p_user_id;

  return v_balance;
end;
$$;

revoke all on function private.ensure_token_balance(uuid) from public;

create or replace function private.create_token_balance_for_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.ensure_token_balance(new.user_id);
  return new;
end;
$$;

revoke all on function private.create_token_balance_for_profile() from public;

create trigger profiles_create_token_balance
after insert on public.profiles
for each row execute function private.create_token_balance_for_profile();

select private.ensure_token_balance(profile.user_id)
from public.profiles as profile;

alter table public.token_balances enable row level security;

create policy token_balances_select_owner
on public.token_balances
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.token_balances from anon, authenticated;
grant select on table public.token_balances to authenticated;

-- The catalog is shared rather than per-account, so every authenticated reader sees the
-- same active rows. icon_key and image_key name artwork bundled in the app, the same way
-- profile avatars reference bundled keys, so static art needs no storage bucket.
create table public.shop_categories (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  subtitle text not null,
  icon_key text not null,
  sort_order integer not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  constraint shop_categories_slug_format check (slug ~ '^[a-z0-9_]{1,64}$'),
  constraint shop_categories_title_length check (char_length(btrim(title)) between 1 and 64),
  constraint shop_categories_subtitle_length check (char_length(btrim(subtitle)) between 1 and 128)
);

create table public.shop_items (
  id uuid primary key default gen_random_uuid(),
  category_id uuid not null references public.shop_categories (id) on delete cascade,
  slug text not null unique,
  name text not null,
  price_tokens integer not null,
  image_key text not null,
  sort_order integer not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  constraint shop_items_slug_format check (slug ~ '^[a-z0-9_]{1,64}$'),
  constraint shop_items_name_length check (char_length(btrim(name)) between 1 and 64),
  constraint shop_items_price_non_negative check (price_tokens >= 0)
);

create index shop_items_category_order_idx
  on public.shop_items (category_id, sort_order, id);

alter table public.shop_categories enable row level security;
alter table public.shop_items enable row level security;

create policy shop_categories_select_active
on public.shop_categories
for select
to authenticated
using (is_active);

-- An item is only readable when its category is too, so retiring a category retires its
-- contents in one step.
create policy shop_items_select_active
on public.shop_items
for select
to authenticated
using (
  is_active
  and exists (
    select 1
    from public.shop_categories as category
    where category.id = shop_items.category_id
      and category.is_active
  )
);

revoke all on table public.shop_categories from anon, authenticated;
revoke all on table public.shop_items from anon, authenticated;
grant select on table public.shop_categories to authenticated;
grant select on table public.shop_items to authenticated;

insert into public.shop_categories (slug, title, subtitle, icon_key, sort_order)
values ('hats', 'Hats', 'Various headwear!', 'shop_category_hats', 0)
on conflict (slug) do update
set
  title = excluded.title,
  subtitle = excluded.subtitle,
  icon_key = excluded.icon_key,
  sort_order = excluded.sort_order;

insert into public.shop_items (category_id, slug, name, price_tokens, image_key, sort_order)
select
  category.id,
  'baseball_cap',
  'Baseball Cap',
  10,
  'shop_item_baseball_cap',
  0
from public.shop_categories as category
where category.slug = 'hats'
on conflict (slug) do update
set
  name = excluded.name,
  price_tokens = excluded.price_tokens,
  image_key = excluded.image_key,
  sort_order = excluded.sort_order;

commit;
