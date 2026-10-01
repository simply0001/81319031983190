begin;

alter table private.board_preferences
  add column push_level text not null default 'all' check (push_level in ('all','personal','mentions'));

create table private.board_post_mentions (
  post_id uuid not null references private.board_posts(id) on delete cascade,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  name text not null,
  primary key (post_id, user_id)
);
create index board_post_mentions_user_idx on private.board_post_mentions(user_id);
alter table private.board_post_mentions enable row level security;
revoke all on private.board_post_mentions from public, anon, authenticated;

create function private.board_save_mentions(p_post uuid, p_board uuid, p_actor uuid, p_body text, p_requested jsonb) returns void
language sql security definer set search_path = '' as $$
  insert into private.board_post_mentions(post_id, user_id, name)
  select p_post, profile.user_id, profile.display_name
  from (
    select distinct raw.value::uuid as user_id
    from (
      select case jsonb_typeof(item) when 'object' then item->>'user_id' when 'string' then item #>> '{}' end as value
      from jsonb_array_elements(case when jsonb_typeof(p_requested) = 'array' then p_requested else '[]'::jsonb end) item
    ) raw
    where raw.value ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    limit 10
  ) requested
  join private.board_members member on member.board_id = p_board and member.user_id = requested.user_id
  join public.profiles profile on profile.user_id = requested.user_id
  where requested.user_id <> p_actor
    and nullif(btrim(profile.display_name), '') is not null
    and strpos(lower(p_body), lower('@' || profile.display_name)) > 0
    and not private.board_blocked(p_actor, requested.user_id)
    and not private.board_restricted(p_board, requested.user_id, true)
  on conflict do nothing;
$$;

create function private.board_prune_mentions(p_post uuid) returns void
language sql security definer set search_path = '' as $$
  delete from private.board_post_mentions mention
  using private.board_posts post
  where mention.post_id = p_post and post.id = p_post
    and strpos(lower(post.body), lower('@' || mention.name)) = 0;
$$;

create function private.board_emit_post(p_board uuid, p_thread uuid, p_actor uuid, p_post uuid, p_reaction boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  m record;
  event_id uuid;
  event_kind text;
  root_author uuid;
  post_author uuid;
  answered_author uuid;
begin
  perform private.board_invalidate(p_board);
  select author_id into root_author from private.board_posts where id = p_thread;
  select author_id into post_author from private.board_posts where id = p_post;
  select answered.author_id into answered_author
    from private.board_posts posted join private.board_posts answered on answered.id = posted.reply_to
    where posted.id = p_post;
  for m in select * from private.board_members where board_id = p_board loop
    if m.user_id = p_actor or m.muted or private.board_blocked(m.user_id, p_actor) or private.board_restricted(p_board, m.user_id, true) then continue; end if;
    event_kind := case
      when p_reaction then case when m.user_id = post_author then 'yeah' else 'activity' end
      when exists(select 1 from private.board_post_mentions mention where mention.post_id = p_post and mention.user_id = m.user_id) then 'mention'
      when p_post <> p_thread and (m.user_id = root_author or m.user_id = answered_author) then 'reply'
      when p_post = p_thread then 'note'
      else 'activity'
    end;
    select e.id into event_id from private.board_events e where e.recipient_id = m.user_id and e.board_id = p_board
      and e.thread_id is not distinct from p_thread and e.read_at is null and e.updated_at > now() - interval '5 minutes'
      and (e.kind = event_kind or (event_kind in ('note','activity') and e.kind in ('note','activity')))
      order by e.updated_at desc limit 1 for update;
    if found then
      update private.board_events set event_count = event_count + 1, updated_at = now(), push_pending = m.push_enabled, actor_id = p_actor,
        kind = case when kind = event_kind then kind else 'activity' end where id = event_id;
    else
      insert into private.board_events(recipient_id, board_id, thread_id, kind, push_pending, actor_id)
        values (m.user_id, p_board, p_thread, event_kind, m.push_enabled, p_actor);
    end if;
  end loop;
end $$;

revoke all on function private.board_save_mentions(uuid,uuid,uuid,text,jsonb), private.board_prune_mentions(uuid),
  private.board_emit_post(uuid,uuid,uuid,uuid,boolean) from public, anon, authenticated;

do $migration$
declare
  definition text := pg_get_functiondef('public.boards_mutate(text,jsonb,uuid)'::regprocedure);
  edits text[] := array[
    'perform private.board_emit(bid,coalesce(parent,rid),actor,''activity'');',
    'perform private.board_save_mentions(rid,bid,actor,body,p_args->''mentions''); perform private.board_emit_post(bid,coalesce(parent,rid),actor,rid,false);',
    'if found then perform private.board_emit(bid,coalesce(post.thread_id,pid),actor,''activity''); end if;',
    'if found then perform private.board_emit_post(bid,coalesce(post.thread_id,pid),actor,pid,true); end if;',
    'update private.board_posts set body=private.board_filter(op.body,''edit'',bid),edited_at=now() where id=pid;',
    'update private.board_posts set body=private.board_filter(op.body,''edit'',bid),edited_at=now() where id=pid; perform private.board_prune_mentions(pid);',
    'delete from private.board_reactions where post_id=pid;',
    'delete from private.board_reactions where post_id=pid; delete from private.board_post_mentions where post_id=pid;',
    'when ''push_preference'' then',
    'when ''push_level'' then if coalesce(p_args->>''level'','''') not in (''all'',''personal'',''mentions'') then raise exception ''Choose all, personal or mentions'' using errcode=''22023''; end if; insert into private.board_preferences(user_id,push_level) values(actor,p_args->>''level'') on conflict(user_id) do update set push_level=excluded.push_level; when ''push_preference'' then'
  ];
  step integer := 1;
begin
  while step < array_length(edits, 1) loop
    if (length(definition) - length(replace(definition, edits[step], ''))) / length(edits[step]) <> 1 then
      raise exception 'boards_mutate no longer matches %', edits[step];
    end if;
    definition := replace(definition, edits[step], edits[step + 1]);
    step := step + 2;
  end loop;
  execute definition;
end
$migration$;

do $migration$
declare
  definition text := pg_get_functiondef('public.boards_query(text,jsonb)'::regprocedure);
  target text := '''push_enabled'',coalesce((select push_enabled from private.board_preferences where user_id=actor),true)) from private.board_settings);';
begin
  if (length(definition) - length(replace(definition, target, ''))) / length(target) <> 1 then
    raise exception 'boards_query no longer matches the expected settings result';
  end if;
  execute replace(definition, target,
    '''push_enabled'',coalesce((select push_enabled from private.board_preferences where user_id=actor),true),''push_level'',coalesce((select push_level from private.board_preferences where user_id=actor),''all'')) from private.board_settings);');
end
$migration$;

create or replace function private.board_post_json(p_id uuid,p_reveal boolean default false) returns jsonb
language sql stable security definer set search_path='' as $$
  select jsonb_build_object('id',p.id,'board_id',p.board_id,'author_id',p.author_id,'author_name',coalesce(u.display_name,'Deleted account'),
    'author_avatar',u.avatar_path,'thread_id',p.thread_id,'reply_to',p.reply_to,'spoiler',p.spoiler,'removed',p.removed,
    'body',case when p.spoiler and not p_reveal then '' else p.body end,
    'drawing',case when p.spoiler and not p_reveal then null else p.drawing end,
    'stationery',case when p.spoiler and not p_reveal then null else p.stationery end,
    'has_drawing',p.drawing is not null,'created_at',p.created_at,'activity_at',p.activity_at,'edited_at',p.edited_at,
    'yeah_count',(select count(*) from private.board_reactions r where r.post_id=p.id and not private.board_blocked(auth.uid(),r.user_id)),
    'yeah',exists(select 1 from private.board_reactions r where r.post_id=p.id and r.user_id=auth.uid()),
    'reply_count',(select count(*) from private.board_posts r where r.thread_id=p.id and not private.board_blocked(auth.uid(),r.author_id)),
    'mentions',case when p.spoiler and not p_reveal then '[]'::jsonb else coalesce((select jsonb_agg(jsonb_build_object('user_id',mention.user_id,'name',mention.name) order by mention.name)
      from private.board_post_mentions mention where mention.post_id=p.id and not private.board_blocked(auth.uid(),mention.user_id)),'[]'::jsonb) end)
  from private.board_posts p left join public.profiles u on u.user_id=p.author_id where p.id=p_id;
$$;

create or replace function private.board_push_eligible(p_event uuid,p_installation uuid,p_binding uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select 1 from private.board_events e join private.message_push_devices d on d.user_id=e.recipient_id
    join private.board_members m on m.board_id=e.board_id and m.user_id=e.recipient_id
    left join private.board_preferences pref on pref.user_id=e.recipient_id
    where e.id=p_event and d.installation_id=p_installation and d.binding_id=p_binding and d.boards_enabled
      and d.refreshed_at>now()-interval '30 days' and e.push_pending and e.read_at is null and e.kind<>'report'
      and coalesce(pref.push_enabled,true) and not m.muted and m.push_enabled
      and case coalesce(pref.push_level,'all') when 'mentions' then e.kind='mention'
        when 'personal' then e.kind in ('mention','reply','yeah') else true end
      and (select enabled from private.board_settings) and private.board_can_read(e.board_id,e.recipient_id)
      and not private.board_blocked(e.recipient_id,e.actor_id)
      and (e.thread_id is null or not private.board_blocked(e.recipient_id,(select author_id from private.board_posts where id=e.thread_id))));
$$;

create or replace function public.claim_board_push_batch() returns jsonb
language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
  if auth.role() is distinct from 'service_role' then raise exception 'Service role required' using errcode='42501'; end if;
  delete from private.board_push_queue q where q.created_at<now()-interval '15 minutes' or q.attempts>=8
    or not private.board_push_eligible(q.event_id,q.installation_id,q.binding_id)
    or q.event_count<>(select event_count from private.board_events where id=q.event_id);
  with candidates as (
    select id from private.board_push_queue where available_at<=now() and (lease_until is null or lease_until<now())
      order by available_at for update skip locked limit 10
  ), leased as (
    update private.board_push_queue q set lease_id=gen_random_uuid(),lease_until=now()+interval '5 minutes',attempts=attempts+1
      from candidates c where q.id=c.id returning q.*
  ) select coalesce(jsonb_agg(jsonb_build_object('id',l.id,'lease_id',l.lease_id,'token',d.token,'platform',d.platform,
    'data',jsonb_build_object('version','1','type','board','recipient_id',d.user_id::text,'binding_id',d.binding_id::text,
      'board_id',e.board_id::text,'thread_id',coalesce(e.thread_id::text,''),'notification_id',e.id::text,'event_count',l.event_count::text,
      'kind',e.kind,'actor_name',coalesce(actor_profile.display_name,''),'board_name',coalesce(b.name,''),
      'title','PocketPass Boards','body','There is new activity in your boards.'))),'[]') into result
    from leased l join private.message_push_devices d on d.installation_id=l.installation_id join private.board_events e on e.id=l.event_id
      join private.boards b on b.id=e.board_id left join public.profiles actor_profile on actor_profile.user_id=e.actor_id;
  return result;
end $$;

revoke all on function public.claim_board_push_batch() from public, anon, authenticated;
grant execute on function public.claim_board_push_batch() to service_role;

create function public.boards_mention_candidates(p_board_id uuid, p_search text default '') returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  actor uuid := private.board_actor(true);
  pattern text := replace(replace(replace(lower(left(btrim(coalesce(p_search, '')), 32)), '\', '\\'), '%', '\%'), '_', '\_');
begin
  if p_board_id is null or not private.board_can_read(p_board_id, actor) then
    raise exception 'Board unavailable' using errcode = '42501';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object('user_id', candidate.user_id, 'display_name', candidate.display_name, 'avatar_path', candidate.avatar_path)
      order by lower(candidate.display_name), candidate.user_id)
    from (
      select member.user_id, profile.display_name, profile.avatar_path
      from private.board_members member join public.profiles profile on profile.user_id = member.user_id
      where member.board_id = p_board_id and member.user_id <> actor
        and nullif(btrim(profile.display_name), '') is not null
        and (lower(profile.display_name) like pattern || '%' or lower(profile.display_name) like '% ' || pattern || '%')
        and not private.board_blocked(actor, member.user_id)
        and not private.board_restricted(p_board_id, member.user_id, true)
      order by lower(profile.display_name), member.user_id
      limit 8
    ) candidate
  ), '[]'::jsonb);
end $$;

revoke all on function public.boards_mention_candidates(uuid, text) from public, anon, authenticated;
grant execute on function public.boards_mention_candidates(uuid, text) to authenticated;

commit;
