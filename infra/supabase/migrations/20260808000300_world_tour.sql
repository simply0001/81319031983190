begin;

create or replace function public.get_world_tour()
returns table (
  country_code text,
  first_met_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  return query
  select
    peer.country_code,
    min(encounter.occurred_at)
  from public.nearby_encounters as encounter
  join public.profiles as peer
    on peer.user_id = case
      when encounter.user_low = v_actor_id then encounter.user_high
      else encounter.user_low
    end
  where v_actor_id in (encounter.user_low, encounter.user_high)
    and encounter.confirmed_at is not null
    and peer.country_code is not null
  group by peer.country_code
  order by min(encounter.occurred_at) desc;
end;
$$;

revoke all on function public.get_world_tour() from public, anon;
grant execute on function public.get_world_tour() to authenticated;

comment on function public.get_world_tour() is
  'World Tour progress for the caller: the distinct countries of people met through confirmed nearby encounters, newest discovery first, with the time each country was first discovered.';

commit;
