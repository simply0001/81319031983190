begin;

-- Devices signed in to the same account used to pass each other like strangers:
-- the server refused the receipt, but only after both sides had spent a pass
-- and the Thor had flashed. Each account now has a secret from which its
-- devices derive a daily rotating tag for the advertisement, so they recognise
-- and skip each other before connecting. Nobody else can compute the tag.
create table private.nearby_device_tag_secrets (
  user_id uuid primary key references public.profiles (user_id) on delete cascade,
  secret bytea not null,
  created_at timestamptz not null default now(),
  constraint nearby_device_tag_secrets_length check (octet_length(secret) = 32)
);

create or replace function public.get_nearby_device_tag_secret()
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_secret bytea;
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  insert into private.nearby_device_tag_secrets (user_id, secret)
  values (v_user_id, extensions.gen_random_bytes(32))
  on conflict (user_id) do nothing;

  select tag_secret.secret
  into v_secret
  from private.nearby_device_tag_secrets as tag_secret
  where tag_secret.user_id = v_user_id;

  return encode(v_secret, 'base64');
end;
$$;

revoke all on function public.get_nearby_device_tag_secret() from public, anon;
grant execute on function public.get_nearby_device_tag_secret() to authenticated;

comment on function public.get_nearby_device_tag_secret() is 'Returns the account''s secret for the daily rotating Bluetooth device tag. Devices of the same account derive the tag from it and skip each other before connecting.';

commit;
