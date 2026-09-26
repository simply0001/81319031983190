begin;

grant update (username) on table public.profiles to authenticated;

commit;
