begin;

-- Staff need the actual restriction record to find and revoke a global Board
-- suspension. The mutation audit has its own ID, so it cannot serve as a case list.
create function public.boards_staff_suspensions(
  p_offset integer default 0, p_limit integer default 50, p_search text default null
) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare
  off integer := greatest(coalesce(p_offset,0),0);
  lim integer := least(greatest(coalesce(p_limit,50),1),100);
  search text := lower(left(btrim(coalesce(p_search,'')),80));
  result jsonb;
begin
  perform private.require_permission('board_suspensions');
  with page as (
    select r.id,r.user_id,coalesce(nullif(p.display_name,''),'PocketPass member') as display_name,
      r.reason,r.created_at,r.expires_at
    from private.board_restrictions r
    left join public.profiles p on p.user_id=r.user_id
    where r.board_id is null and r.kind='suspension' and r.revoked_at is null
      and (r.expires_at is null or r.expires_at>now())
      and (search='' or strpos(r.id::text,search)>0 or strpos(r.user_id::text,search)>0
        or strpos(lower(coalesce(p.display_name,'')),search)>0)
    order by r.created_at desc,r.id desc
    limit lim+1 offset off
  )
  select jsonb_build_object(
    'items',coalesce((select jsonb_agg(to_jsonb(item) order by item.created_at desc,item.id desc)
      from (select * from page limit lim) item),'[]'::jsonb),
    'has_more',(select count(*)>lim from page)
  ) into result;
  return result;
end $$;

-- Keep the complete audit available while making the common view about
-- decisions and changes rather than repetitive read-access entries.
create function public.boards_staff_audit(
  p_offset integer default 0, p_limit integer default 50,
  p_include_reads boolean default false
) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare
  off integer := greatest(coalesce(p_offset,0),0);
  lim integer := least(greatest(coalesce(p_limit,50),1),100);
  result jsonb;
begin
  perform private.require_permission('board_content');
  with page as (
    select a.id,a.board_id,b.name as board_name,a.actor_id,actor.display_name as actor_name,
      a.subject_id,subject.display_name as subject_name,a.post_id,a.action,a.reason,
      a.central,a.created_at
    from private.board_audit a
    left join private.boards b on b.id=a.board_id
    left join public.profiles actor on actor.user_id=a.actor_id
    left join public.profiles subject on subject.user_id=a.subject_id
    where (b.id is null or b.visibility='public' or private.has_permission('board_private_review'))
      and (coalesce(p_include_reads,false) or a.action not in
        ('review_access','member_review','management_review','history_review',
         'branding_review','retained_artwork_review','report_review'))
    order by a.created_at desc,a.id desc
    limit lim+1 offset off
  )
  select jsonb_build_object(
    'items',coalesce((select jsonb_agg(to_jsonb(item) order by item.created_at desc,item.id desc)
      from (select * from page limit lim) item),'[]'::jsonb),
    'has_more',(select count(*)>lim from page)
  ) into result;
  return result;
end $$;

revoke all on function public.boards_staff_suspensions(integer,integer,text) from public,anon,authenticated,service_role;
revoke all on function public.boards_staff_audit(integer,integer,boolean) from public,anon,authenticated,service_role;
grant execute on function public.boards_staff_suspensions(integer,integer,text) to authenticated;
grant execute on function public.boards_staff_audit(integer,integer,boolean) to authenticated;
notify pgrst, 'reload schema';

commit;
