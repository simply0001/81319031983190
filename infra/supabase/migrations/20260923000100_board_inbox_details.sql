begin;

-- The in-app inbox may show the subject of a note, but push payloads remain
-- generic. Apply the same audience and block checks as boards_query('inbox').
create function public.boards_inbox(p_cursor jsonb default null, p_limit integer default 30) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare
  actor uuid := private.board_actor(true);
  lim integer := least(greatest(coalesce(p_limit,30),1),100);
  rows jsonb;
begin
  with page as (
    select e.* from private.board_events e
    where e.recipient_id=actor and private.board_can_read(e.board_id,actor)
      and not private.board_blocked(actor,e.actor_id)
      and (e.kind<>'report' or private.board_role(e.board_id,actor) in ('owner','moderator'))
      and (e.thread_id is null or not private.board_blocked(actor,(select author_id from private.board_posts where id=e.thread_id)))
      and (p_cursor is null or (e.updated_at,e.id)<((p_cursor->>'updated_at')::timestamptz,(p_cursor->>'id')::uuid))
    order by e.updated_at desc,e.id desc limit lim
  )
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',e.id,'board_id',e.board_id,'board_name',b.name,'thread_id',e.thread_id,
    'kind',e.kind,'event_count',e.event_count,'read_at',e.read_at,'updated_at',e.updated_at,
    'latest_actor_name',case when e.kind='report' then null else nullif(actor_profile.display_name,'') end,
    'thread_author_name',case when p.id is null then null else coalesce(nullif(author_profile.display_name,''),'Deleted account') end,
    'subject_type',case when p.id is null then null when p.removed then 'removed'
      when p.spoiler then 'spoiler' when nullif(btrim(p.body),'') is not null then 'text'
      when p.drawing is not null then 'drawing' else 'note' end,
    'subject',case when p.id is not null and not p.removed and not p.spoiler
      then left(regexp_replace(btrim(p.body),'[[:space:]]+',' ','g'),100) else null end
  ) order by e.updated_at desc,e.id desc),'[]'::jsonb) into rows
  from page e
  join private.boards b on b.id=e.board_id
  left join private.board_posts p on p.id=e.thread_id
  left join public.profiles actor_profile on actor_profile.user_id=e.actor_id
  left join public.profiles author_profile on author_profile.user_id=p.author_id;
  return jsonb_build_object('items',rows,'cursor',case when jsonb_array_length(rows)=lim
    then jsonb_build_object('updated_at',rows->-1->'updated_at','id',rows->-1->'id') end);
end $$;

revoke all on function public.boards_inbox(jsonb,integer) from public,anon,authenticated;
grant execute on function public.boards_inbox(jsonb,integer) to authenticated;
notify pgrst, 'reload schema';

commit;
