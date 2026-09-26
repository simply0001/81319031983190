begin;

alter table public.shop_items
  add column if not exists mii_hat_type integer;

alter table public.shop_items
  drop constraint if exists shop_items_mii_hat_type_range;

alter table public.shop_items
  add constraint shop_items_mii_hat_type_range
    check (mii_hat_type is null or mii_hat_type between 0 and 8);

create unique index if not exists shop_items_mii_hat_type_unique
  on public.shop_items (mii_hat_type)
  where mii_hat_type is not null;

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
  hat.slug,
  hat.name,
  hat.price_tokens,
  'shop_item_' || hat.slug,
  hat.mii_hat_type,
  hat.mii_hat_type
from public.shop_categories as category
cross join (
  values
    ('baseball_cap', 'Baseball Cap', 20, 0),
    ('flat_cap', 'Flat Cap', 30, 1),
    ('top_hat', 'Top Hat', 120, 2),
    ('ribbons', 'Ribbons', 40, 3),
    ('bow', 'Bow', 40, 4),
    ('cat_ears', 'Cat Ears', 100, 5),
    ('straw_hat', 'Straw Hat', 60, 6),
    ('cat_hat', 'Cat Hat', 70, 7),
    ('bike_helmet', 'Bike Helmet', 150, 8)
) as hat(slug, name, price_tokens, mii_hat_type)
where category.slug = 'hats'
on conflict (slug) do update
set
  name = excluded.name,
  price_tokens = excluded.price_tokens,
  image_key = excluded.image_key,
  sort_order = excluded.sort_order,
  mii_hat_type = excluded.mii_hat_type,
  is_active = true;

create table if not exists public.user_shop_items (
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  item_id uuid not null references public.shop_items (id) on delete restrict,
  client_operation_id uuid not null,
  price_paid integer not null,
  purchased_at timestamptz not null default now(),
  primary key (user_id, item_id),
  constraint user_shop_items_operation_unique unique (user_id, client_operation_id),
  constraint user_shop_items_price_non_negative check (price_paid >= 0)
);

create index if not exists user_shop_items_user_purchased_idx
  on public.user_shop_items (user_id, purchased_at);

alter table public.user_shop_items enable row level security;

drop policy if exists user_shop_items_select_owner on public.user_shop_items;
create policy user_shop_items_select_owner
on public.user_shop_items
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.user_shop_items from anon, authenticated;
grant select on table public.user_shop_items to authenticated;

create or replace function private.owns_mii_hat(
  p_user_id uuid,
  p_hat_type integer
)
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
      from public.user_shop_items as owned
      join public.shop_items as item on item.id = owned.item_id
      where owned.user_id = p_user_id
        and item.mii_hat_type = p_hat_type
    );
$$;

revoke all on function private.owns_mii_hat(uuid, integer) from public;

create or replace function public.buy_shop_item(
  p_item_id uuid,
  p_client_operation_id uuid
)
returns table (
  user_id uuid,
  item_id uuid,
  price_paid integer,
  balance integer,
  purchased_at timestamptz
)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_item public.shop_items;
  v_balance integer;
  v_purchased_at timestamptz;
  v_is_replay boolean;
  v_replay_response jsonb;
  v_request_payload jsonb;
  v_response_payload jsonb;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_item_id is null or p_client_operation_id is null then
    raise exception 'Item and client operation id are required' using errcode = '22004';
  end if;

  v_request_payload := jsonb_build_object('item_id', p_item_id);

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_replay_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'buy_shop_item',
    v_request_payload
  ) as operation;

  if v_is_replay then
    return query
    select
      (v_replay_response ->> 'user_id')::uuid,
      (v_replay_response ->> 'item_id')::uuid,
      (v_replay_response ->> 'price_paid')::integer,
      (v_replay_response ->> 'balance')::integer,
      (v_replay_response ->> 'purchased_at')::timestamptz;
    return;
  end if;

  perform 1
  from public.profiles as profile
  where profile.user_id = v_actor_id
  for update;
  if not found then
    raise exception 'Profile is unavailable' using errcode = 'P0002';
  end if;

  select item.* into v_item
  from public.shop_items as item
  join public.shop_categories as category on category.id = item.category_id
  where item.id = p_item_id
    and item.is_active
    and category.is_active;
  if not found then
    raise sqlstate 'PT410' using
      message = 'This item is no longer available',
      hint = 'ITEM_UNAVAILABLE';
  end if;

  if exists (
    select 1
    from public.user_shop_items as owned
    where owned.user_id = v_actor_id
      and owned.item_id = p_item_id
  ) then
    raise sqlstate 'PT409' using
      message = 'You already own this item',
      hint = 'ALREADY_OWNED';
  end if;

  perform private.ensure_token_balance(v_actor_id);

  update public.token_balances as token_balance
  set
    balance = token_balance.balance - v_item.price_tokens,
    updated_at = now()
  where token_balance.user_id = v_actor_id
    and token_balance.balance >= v_item.price_tokens
  returning token_balance.balance into v_balance;
  if not found then
    raise sqlstate 'PT402' using
      message = 'Not enough tokens',
      detail = 'price=' || v_item.price_tokens::text,
      hint = 'INSUFFICIENT_TOKENS';
  end if;

  insert into public.user_shop_items (user_id, item_id, client_operation_id, price_paid)
  values (v_actor_id, p_item_id, p_client_operation_id, v_item.price_tokens)
  returning user_shop_items.purchased_at into v_purchased_at;

  v_response_payload := jsonb_build_object(
    'user_id', v_actor_id,
    'item_id', p_item_id,
    'price_paid', v_item.price_tokens,
    'balance', v_balance,
    'purchased_at', v_purchased_at
  );
  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'buy_shop_item',
    v_request_payload,
    v_response_payload
  );

  return query
  select v_actor_id, p_item_id, v_item.price_tokens, v_balance, v_purchased_at;
end;
$$;

revoke all on function public.buy_shop_item(uuid, uuid) from public, anon;
grant execute on function public.buy_shop_item(uuid, uuid) to authenticated;

update public.profile_miis
set appearance = appearance || jsonb_build_object('extHatType', -1)
where (appearance ->> 'extHatType')::integer > 8;

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
    and private.mii_json_int_between(p_appearance, 'extHatType', -1, 8)
    and private.mii_json_int_between(p_appearance, 'extHatColor', -1, 11)
    and private.mii_json_int_between(p_appearance, 'extFacePaintColor', -1, 11)
    and private.mii_optional_common_color(p_appearance, 'extGlassesColor')
    and private.mii_optional_common_color(p_appearance, 'extHairColor')
    and private.mii_optional_common_color(p_appearance, 'extEyebrowColor')
    and private.mii_optional_common_color(p_appearance, 'extMouthColor')
    and private.mii_optional_common_color(p_appearance, 'extFacialHairColor');
end;
$$;

insert into public.user_shop_items (user_id, item_id, client_operation_id, price_paid, purchased_at)
select
  worn.user_id,
  item.id,
  gen_random_uuid(),
  0,
  worn.first_seen
from (
  select
    mii.user_id,
    (mii.appearance ->> 'extHatType')::integer as hat_type,
    min(mii.created_at) as first_seen
  from public.profile_miis as mii
  where (mii.appearance ->> 'extHatType')::integer between 0 and 8
  group by mii.user_id, (mii.appearance ->> 'extHatType')::integer
) as worn
join public.shop_items as item on item.mii_hat_type = worn.hat_type
on conflict (user_id, item_id) do nothing;

create or replace function private.write_profile_mii(
  p_actor_id uuid,
  p_slot integer,
  p_client_operation_id uuid,
  p_revision bigint,
  p_schema_version integer,
  p_appearance jsonb,
  p_avatar_path text,
  p_canonical_miic_base64 text
)
returns public.profile_miis
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_expected_avatar_path text;
  v_canonical_miic bytea;
  v_existing public.profile_miis;
  v_row public.profile_miis;
  v_has_active boolean;
  v_hat_type integer;
  v_updated_at timestamptz := clock_timestamp();
begin
  if p_client_operation_id is null
    or p_revision is null
    or p_schema_version is null
    or p_appearance is null
    or p_avatar_path is null
    or p_slot is null
  then
    raise exception 'Incomplete Mii publication' using errcode = '22004';
  end if;
  if p_slot not between 1 and 3 then
    raise exception 'Mii slot must be between 1 and 3' using errcode = '22023';
  end if;
  if p_revision <= 0 then
    raise exception 'Mii revision must be positive' using errcode = '22023';
  end if;
  if not private.is_sanitized_mii_appearance(p_appearance, p_schema_version) then
    raise exception 'Mii appearance is invalid or contains unsupported fields'
      using errcode = '22023';
  end if;

  v_hat_type := (p_appearance ->> 'extHatType')::integer;
  if v_hat_type >= 0 and not private.owns_mii_hat(p_actor_id, v_hat_type) then
    raise sqlstate 'PT403' using
      message = 'That hat has not been purchased',
      hint = 'HAT_NOT_OWNED';
  end if;

  v_expected_avatar_path := (
    p_actor_id::text
    || '/mii-r'
    || p_revision::text
    || '-'
    || p_client_operation_id::text
    || '.png'
  );
  if p_avatar_path <> v_expected_avatar_path then
    raise exception 'Mii avatar path does not belong to this publication'
      using errcode = '22023';
  end if;
  if not exists (
    select 1
    from storage.objects as object
    where object.bucket_id = 'avatars'
      and object.name = p_avatar_path
  ) then
    raise exception 'Mii portrait upload is missing' using errcode = '22023';
  end if;

  if p_canonical_miic_base64 is not null then
    begin
      v_canonical_miic := decode(p_canonical_miic_base64, 'base64');
    exception
      when others then
        raise exception 'Canonical Mii data is not valid base64'
          using errcode = '22023';
    end;
    if octet_length(v_canonical_miic) not between 1 and 4096 then
      raise exception 'Canonical Mii data is too large' using errcode = '22023';
    end if;
  end if;

  perform 1
  from public.profiles as profile
  where profile.user_id = p_actor_id
  for update;
  if not found then
    raise exception 'Profile is unavailable' using errcode = 'P0002';
  end if;

  select mii.* into v_existing
  from public.profile_miis as mii
  where mii.user_id = p_actor_id
    and mii.slot = p_slot
  for update;
  if found and p_revision <= v_existing.revision then
    raise exception 'Mii revision must increase' using errcode = '22023';
  end if;

  select exists (
    select 1
    from public.profile_miis as mii
    where mii.user_id = p_actor_id
      and mii.is_active
  ) into v_has_active;

  insert into public.profile_miis (
    user_id,
    slot,
    schema_version,
    appearance,
    canonical_miic,
    revision,
    avatar_path,
    client_operation_id,
    is_active,
    updated_at
  )
  values (
    p_actor_id,
    p_slot,
    p_schema_version,
    p_appearance,
    v_canonical_miic,
    p_revision,
    p_avatar_path,
    p_client_operation_id,
    coalesce(v_existing.is_active, not v_has_active),
    v_updated_at
  )
  on conflict on constraint profile_miis_pkey do update
  set
    schema_version = excluded.schema_version,
    appearance = excluded.appearance,
    canonical_miic = excluded.canonical_miic,
    revision = excluded.revision,
    avatar_path = excluded.avatar_path,
    client_operation_id = excluded.client_operation_id,
    updated_at = excluded.updated_at
  returning * into v_row;

  if v_row.is_active then
    update public.profiles as profile
    set
      avatar_path = v_row.avatar_path,
      updated_at = v_updated_at
    where profile.user_id = p_actor_id;
  end if;

  return v_row;
end;
$$;

revoke all on function private.write_profile_mii(
  uuid,
  integer,
  uuid,
  bigint,
  integer,
  jsonb,
  text,
  text
) from public, anon, authenticated;

update public.bingo_goals
set kind = 'buy_shop_item', target = 1, is_active = true
where slug = 'buy_shop_item';

update public.bingo_goals
set kind = 'own_hat', target = 1, is_active = true
where slug = 'own_hat';

update public.bingo_goals
set kind = 'own_shop_items', target = 3, is_active = true
where slug = 'own_3_items';

do $$
begin
  if to_regprocedure('private.bingo_goal_progress_pre_shop(uuid, text, integer, text, timestamptz)') is null then
    alter function private.bingo_goal_progress(uuid, text, integer, text, timestamptz)
      rename to bingo_goal_progress_pre_shop;
  end if;
end;
$$;

create or replace function private.bingo_goal_progress(
  p_actor uuid,
  p_kind text,
  p_target integer,
  p_country text,
  p_week_start timestamptz
)
returns integer
language plpgsql
stable
set search_path = ''
as $$
begin
  if p_kind = 'buy_shop_item' then
    return (
      select count(*)::integer
      from public.user_shop_items as owned
      where owned.user_id = p_actor
        and owned.purchased_at >= p_week_start
    );
  elsif p_kind = 'own_hat' then
    return (
      select count(*)::integer
      from public.user_shop_items as owned
      join public.shop_items as item on item.id = owned.item_id
      where owned.user_id = p_actor
        and item.mii_hat_type is not null
    );
  elsif p_kind = 'own_shop_items' then
    return (
      select count(*)::integer
      from public.user_shop_items as owned
      where owned.user_id = p_actor
    );
  end if;

  return private.bingo_goal_progress_pre_shop(
    p_actor,
    p_kind,
    p_target,
    p_country,
    p_week_start
  );
end;
$$;

revoke all on function private.bingo_goal_progress(uuid, text, integer, text, timestamptz) from public;
revoke all on function private.bingo_goal_progress_pre_shop(uuid, text, integer, text, timestamptz) from public;

comment on table public.user_shop_items is
  'Shop items a user has bought with tokens; Mii hats may only be worn when owned here.';
comment on function public.buy_shop_item(uuid, uuid) is
  'Buys an active shop item for the caller, debiting token_balances atomically; idempotent per client operation id.';

commit;
