begin;

create or replace function public.touch_last_seen()
returns timestamptz
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_now timestamptz := now();
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  update public.profiles
  set last_seen_at = v_now
  where user_id = v_user_id
    and (last_seen_at is null or last_seen_at < v_now - interval '30 seconds');

  return v_now;
end;
$$;

revoke all on function public.touch_last_seen() from public, anon;
grant execute on function public.touch_last_seen() to authenticated;

comment on function public.touch_last_seen() is 'Stamps the caller''s profiles.last_seen_at; the app calls it when it comes to the foreground, every few minutes while open, and when it leaves.';

commit;
