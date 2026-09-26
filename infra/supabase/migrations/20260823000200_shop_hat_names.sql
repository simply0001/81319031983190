begin;

update public.shop_items
set
  slug = 'beanie',
  name = 'Beanie',
  image_key = 'shop_item_beanie'
where slug = 'flat_cap';

commit;
