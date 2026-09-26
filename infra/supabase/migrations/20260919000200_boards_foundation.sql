begin;

-- All board data is private. Only the authenticated first-party RPC boundary below
-- is exposed through PostgREST; connected applications receive no new capability.
create or replace function private.admin_permission_keys() returns text[]
language sql immutable set search_path = '' as $$
  select array['users','audit','legacy','tokens','achievements','admins','apps','supporters',
    'board_requests','boards','board_content','board_members','board_suspensions',
    'board_private_review','board_delete','board_filters','board_stationery','board_settings']::text[];
$$;
alter table private.admin_users drop constraint admin_users_permissions_known;
alter table private.admin_users add constraint admin_users_permissions_known
  check (permissions <@ private.admin_permission_keys());

create table private.board_settings (
  singleton boolean primary key default true check(singleton),
  enabled boolean not null default false,
  requests_open boolean not null default true,
  submissions_per_minute integer not null default 30 check(submissions_per_minute between 1 and 300),
  reactions_per_minute integer not null default 120 check(reactions_per_minute between 1 and 1000),
  invitations_reports_per_minute integer not null default 10 check(invitations_reports_per_minute between 1 and 100)
);
insert into private.board_settings default values;
create table private.board_preferences (
  user_id uuid primary key references public.profiles(user_id) on delete cascade,
  push_enabled boolean not null default true
);

create table private.boards (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(user_id) on delete restrict,
  name text not null check(length(name) between 1 and 60),
  description text not null default '' check(length(description)<=1000),
  rules text not null default '' check(length(rules)<=4000),
  visibility text not null check(visibility in ('public','private')),
  join_policy text not null default 'open' check(join_policy in ('open','approval')),
  code_policy text not null default 'open' check(code_policy in ('open','approval')),
  members_can_invite boolean not null default false,
  accent text not null default 'blue' check(accent in ('blue','green','pink','purple','orange','teal')),
  icon_asset_id uuid,
  cover_asset_id uuid,
  archived boolean not null default false,
  transfer_to uuid references public.profiles(user_id) on delete set null,
  access_revision bigint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table private.board_members (
  board_id uuid not null references private.boards(id) on delete cascade,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  role text not null default 'member' check(role in ('owner','moderator','member')),
  muted boolean not null default false,
  push_enabled boolean not null default true,
  joined_at timestamptz not null default now(),
  primary key(board_id,user_id)
);
create index board_members_user_idx on private.board_members(user_id,board_id);
create table private.board_viewers (
  board_id uuid not null references private.boards(id) on delete cascade,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  seen_at timestamptz not null default now(),
  primary key(board_id,user_id)
);
create table private.board_proposals (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.profiles(user_id) on delete cascade,
  name text not null check(length(name) between 1 and 60),
  description text not null check(length(description)<=1000),
  rules text not null check(length(rules)<=4000),
  visibility text not null check(visibility in ('public','private')),
  status text not null default 'pending' check(status in ('pending','approved','rejected')),
  reason text not null default '',
  board_id uuid references private.boards(id) on delete set null,
  created_at timestamptz not null default now()
);
create table private.board_join_requests (
  board_id uuid not null references private.boards(id) on delete cascade,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(board_id,user_id)
);
create table private.board_invitations (
  id uuid primary key default gen_random_uuid(),
  board_id uuid not null references private.boards(id) on delete cascade,
  inviter_id uuid not null references public.profiles(user_id) on delete cascade,
  recipient_id uuid references public.profiles(user_id) on delete cascade,
  code_hash text,
  revoked boolean not null default false,
  accepted_at timestamptz,
  created_at timestamptz not null default now(),
  check((recipient_id is null) <> (code_hash is null))
);
create unique index board_invitation_code_idx on private.board_invitations(code_hash) where code_hash is not null;
create index board_invitation_recipient_idx on private.board_invitations(recipient_id,created_at desc);

create table private.board_stationery (
  id text primary key check(length(id) between 1 and 80),
  name text not null check(length(name) between 1 and 80),
  version integer not null default 1 check(version>0),
  access text not null default 'free' check(access in ('free','tokens','achievement')),
  price integer not null default 0 check(price>=0),
  achievement_key text,
  active boolean not null default true,
  artwork jsonb not null default '{"background":"#FFFFFF"}',
  check(access<>'achievement' or achievement_key is not null)
);
insert into private.board_stationery(id,name) values('plain','Plain paper');
create table private.board_stationery_owned (
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  stationery_id text not null references private.board_stationery(id) on delete restrict,
  price_paid integer not null check(price_paid>=0),
  purchased_at timestamptz not null default now(),
  primary key(user_id,stationery_id)
);

create table private.board_posts (
  id uuid primary key default gen_random_uuid(),
  board_id uuid not null references private.boards(id) on delete cascade,
  author_id uuid references public.profiles(user_id) on delete set null,
  thread_id uuid references private.board_posts(id) on delete cascade,
  reply_to uuid references private.board_posts(id) on delete set null,
  body text not null default '',
  drawing jsonb,
  drawing_preview text,
  stationery jsonb,
  spoiler boolean not null default false,
  removed boolean not null default false,
  edited_at timestamptz,
  created_at timestamptz not null default now(),
  activity_at timestamptz not null default now(),
  check(length(body)<=case when thread_id is null then 1000 else 500 end),
  check(drawing is null or octet_length(drawing::text)<=524288)
);
create index board_posts_feed_idx on private.board_posts(board_id,created_at desc,id desc) where thread_id is null;
create index board_posts_activity_idx on private.board_posts(board_id,activity_at desc,id desc) where thread_id is null;
create index board_posts_thread_idx on private.board_posts(thread_id,created_at,id);
create table private.board_reactions (
  post_id uuid not null references private.board_posts(id) on delete cascade,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(post_id,user_id)
);
-- Every saved revision is immutable. A stale base creates a second head, which
-- devices must display as a recovered copy instead of replacing another device.
create table private.board_draft_versions (
  id uuid primary key,
  draft_id uuid not null,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  board_id uuid not null references private.boards(id) on delete cascade,
  base_id uuid references private.board_draft_versions(id) on delete set null,
  payload jsonb not null check(octet_length(payload::text)<=530000),
  is_head boolean not null default true,
  created_at timestamptz not null default now()
);
create index board_draft_heads_idx on private.board_draft_versions(user_id,draft_id) where is_head;
create table private.board_restrictions (
  id uuid primary key default gen_random_uuid(),
  board_id uuid references private.boards(id) on delete cascade,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  kind text not null check(kind in ('mute','ban','suspension')),
  reason text not null check(length(reason) between 1 and 1000),
  actor_id uuid references public.profiles(user_id) on delete set null,
  central boolean not null default false,
  expires_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  check((board_id is null)=(kind='suspension'))
);
create index board_restrictions_active_idx on private.board_restrictions(user_id,board_id) where revoked_at is null;
create table private.board_reports (
  id uuid primary key default gen_random_uuid(),
  board_id uuid not null references private.boards(id) on delete cascade,
  post_id uuid references private.board_posts(id) on delete cascade,
  reporter_id uuid references public.profiles(user_id) on delete set null,
  reason text not null check(length(reason) between 1 and 1000),
  central_only boolean not null default false,
  status text not null default 'open' check(status in ('open','resolved','dismissed')),
  resolution text not null default '',
  created_at timestamptz not null default now(),
  resolved_at timestamptz
);
create table private.board_appeals (
  id uuid primary key default gen_random_uuid(),
  board_id uuid not null references private.boards(id) on delete cascade,
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  case_id uuid not null,
  body text not null check(length(body) between 1 and 1000),
  status text not null default 'open' check(status in ('open','resolved','dismissed')),
  resolution text not null default '',
  created_at timestamptz not null default now()
);
create table private.board_history (
  id uuid primary key default gen_random_uuid(),
  board_id uuid not null references private.boards(id) on delete cascade,
  post_id uuid references private.board_posts(id) on delete cascade,
  subject_id uuid references public.profiles(user_id) on delete set null,
  content jsonb not null,
  expires_at timestamptz not null default(now()+interval '30 days')
);
create table private.board_audit (
  id uuid primary key default gen_random_uuid(),
  board_id uuid,
  actor_id uuid references public.profiles(user_id) on delete set null,
  subject_id uuid references public.profiles(user_id) on delete set null,
  post_id uuid,
  action text not null,
  reason text not null default '',
  central boolean not null default false,
  created_at timestamptz not null default now()
);
create table private.board_word_rules (
  id uuid primary key default gen_random_uuid(),
  phrase text not null check(length(btrim(phrase)) between 1 and 100),
  action text not null check(action in ('censor','block')),
  reason text not null check(length(reason) between 1 and 500),
  enabled boolean not null default true,
  created_at timestamptz not null default now()
);
create table private.board_filter_reviews (
  id uuid primary key default gen_random_uuid(),
  board_id uuid references private.boards(id) on delete cascade,
  actor_id uuid references public.profiles(user_id) on delete set null,
  operation_id uuid,
  proposal_id uuid references private.board_proposals(id) on delete cascade,
  context text not null,
  original text not null,
  filtered text not null,
  status text not null default 'open' check(status in ('open','resolved')),
  created_at timestamptz not null default now()
);
create table private.board_operations (
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  operation_id uuid not null,
  operation text not null,
  request_hash text not null,
  response jsonb,
  board_id uuid references private.boards(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(user_id,operation_id)
);
create table private.board_rate_events (
  user_id uuid not null references public.profiles(user_id) on delete cascade,
  kind text not null,
  occurred_at timestamptz not null default clock_timestamp()
);
create index board_rate_window_idx on private.board_rate_events(user_id,kind,occurred_at);
create table private.board_events (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references public.profiles(user_id) on delete cascade,
  actor_id uuid references public.profiles(user_id) on delete set null,
  board_id uuid not null references private.boards(id) on delete cascade,
  thread_id uuid references private.board_posts(id) on delete cascade,
  kind text not null,
  event_count integer not null default 1,
  read_at timestamptz,
  push_pending boolean not null default false,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);
create index board_events_recipient_idx on private.board_events(recipient_id,updated_at desc);
-- Branding bytes are written only by the image processing service, never by a
-- client-supplied path. Reads still pass the current board permission check.
create table private.board_assets (
  id uuid primary key default gen_random_uuid(),
  board_id uuid not null references private.boards(id) on delete cascade,
  owner_id uuid not null references public.profiles(user_id) on delete cascade,
  mime text not null check(mime in ('image/png','image/jpeg','image/webp')),
  bytes bytea not null check(octet_length(bytes)<=2097152),
  width integer not null check(width between 1 and 2048),
  height integer not null check(height between 1 and 2048),
  created_at timestamptz not null default now()
);
alter table private.boards add constraint boards_icon_asset_fk foreign key(icon_asset_id) references private.board_assets(id) on delete set null;
alter table private.boards add constraint boards_cover_asset_fk foreign key(cover_asset_id) references private.board_assets(id) on delete set null;

do $$ declare r record; begin
  for r in select tablename from pg_tables where schemaname='private' and (tablename like 'board\_%' escape '\' or tablename='boards') loop
    execute format('alter table private.%I enable row level security',r.tablename);
    execute format('revoke all on private.%I from public, anon, authenticated',r.tablename);
  end loop;
end $$;

create function private.board_actor(p_require_enabled boolean default true) returns uuid
language plpgsql stable security definer set search_path='' as $$
begin
  if auth.uid() is null or coalesce((auth.jwt()->>'is_anonymous')::boolean,false) or coalesce(auth.jwt()->>'client_id','')<>'' then
    raise exception 'A PocketPass account is required' using errcode='42501';
  end if;
  if p_require_enabled and not(select enabled from private.board_settings) then
    raise exception 'Boards are temporarily unavailable. Your notes are safe.' using errcode='42501',hint='BOARDS_DISABLED';
  end if;
  return auth.uid();
end $$;

create function private.board_blocked(p_a uuid,p_b uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select 1 from public.user_blocks where (blocker_id=p_a and blocked_id=p_b) or (blocker_id=p_b and blocked_id=p_a));
$$;
create function private.board_restricted(p_board uuid,p_user uuid,p_ban_only boolean default false) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select 1 from private.board_restrictions where user_id=p_user
    and (board_id=p_board or (board_id is null and not p_ban_only)) and revoked_at is null
    and (expires_at is null or expires_at>now()) and (not p_ban_only or kind='ban'));
$$;
create function private.board_role(p_board uuid,p_user uuid) returns text
language sql stable security definer set search_path='' as $$
  select role from private.board_members where board_id=p_board and user_id=p_user;
$$;
create function private.board_can_read(p_board uuid,p_user uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select 1 from private.boards b where b.id=p_board
    and (b.visibility='public' or (private.board_role(b.id,p_user) is not null and not private.board_restricted(b.id,p_user,true))));
$$;
create function private.board_require_read(p_board uuid,p_review boolean default false) returns void
language plpgsql security definer set search_path='' as $$
declare b private.boards; central boolean;
begin
  select * into b from private.boards where id=p_board;
  if not found then raise exception 'Board unavailable' using errcode='42501'; end if;
  if p_review then
    central:=private.board_require_moderator(p_board);
    insert into private.board_audit(board_id,actor_id,action,central) values(p_board,auth.uid(),'review_access',central);
  elsif not private.board_can_read(p_board,auth.uid()) then
    raise exception 'Board unavailable' using errcode='42501';
  end if;
  insert into private.board_viewers(board_id,user_id) values(p_board,auth.uid())
    on conflict(board_id,user_id) do update set seen_at=now();
end $$;
create function private.board_require_participant(p_board uuid) returns void
language plpgsql security definer set search_path='' as $$
begin
  perform private.board_require_read(p_board);
  if private.board_role(p_board,auth.uid()) is null then raise exception 'Join this board to take part' using errcode='42501'; end if;
  if (select archived from private.boards where id=p_board) then raise exception 'This board is archived' using errcode='42501'; end if;
  if private.board_restricted(p_board,auth.uid()) then raise exception 'Board participation is restricted. See your moderation notices for the reason and appeal options.' using errcode='42501'; end if;
end $$;
create function private.board_require_moderator(p_board uuid,p_permission text default 'board_content') returns boolean
language plpgsql security definer set search_path='' as $$
begin
  if coalesce(private.board_role(p_board,auth.uid()),'') in ('owner','moderator') and not private.board_restricted(p_board,auth.uid()) then
    perform private.board_require_read(p_board);
    return false;
  end if;
  if private.has_permission(p_permission) then
    if (select visibility='private' from private.boards where id=p_board) then perform private.require_permission('board_private_review'); end if;
    return true;
  end if;
  raise exception 'Board moderator required' using errcode='42501';
end $$;
create function private.board_rate(p_kind text) returns void
language plpgsql security definer set search_path='' as $$
declare n integer; maximum integer;
begin
  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text||':boards:rate',0));
  select case p_kind when 'submit' then submissions_per_minute when 'react' then reactions_per_minute
    else invitations_reports_per_minute end into maximum from private.board_settings;
  select count(*) into n from private.board_rate_events where user_id=auth.uid() and kind=p_kind and occurred_at>clock_timestamp()-interval '1 minute';
  if n>=maximum then raise exception 'A few too many requests at once. Try again in a minute.' using errcode='P0001',hint='BOARD_RATE_LIMIT'; end if;
  insert into private.board_rate_events(user_id,kind) values(auth.uid(),p_kind);
end $$;

-- PostgreSQL word boundaries with escaped literals; no pattern is interpreted as
-- a staff-supplied regular expression. Blocking is evaluated before censoring.
create function private.board_rule_pattern(p_phrase text) returns text
language sql immutable set search_path='' as $$
  select '\y'||regexp_replace(btrim(p_phrase),'([.\\+*?\[\]^$(){}|])','\\\1','g')||'\y';
$$;
create function private.board_filter(p_text text,p_context text,p_board uuid default null,p_bio boolean default false) returns text
language plpgsql security definer set search_path='' as $$
declare r private.board_word_rules; result text:=coalesce(p_text,'');
begin
  for r in select * from private.board_word_rules where enabled order by case action when 'block' then 0 else 1 end,id loop
    if p_text ~* private.board_rule_pattern(r.phrase) then
      if p_bio or r.action='block' then raise exception '%',r.reason using errcode='22023',hint='BOARD_TEXT_REJECTED'; end if;
      result:=regexp_replace(result,private.board_rule_pattern(r.phrase),'****','gi');
    end if;
  end loop;
  if result is distinct from p_text then
    insert into private.board_filter_reviews(board_id,actor_id,operation_id,context,original,filtered)
      values(p_board,auth.uid(),nullif(current_setting('pocketpass.board_operation_id',true),'')::uuid,p_context,p_text,result);
  end if;
  return result;
end $$;
create function private.board_filter_bio() returns trigger
language plpgsql security definer set search_path='' as $$
begin
  if tg_op='INSERT' or new.bio is distinct from old.bio then
    perform private.board_filter(new.bio,'bio',null,true);
  end if;
  return new;
end $$;
create trigger profiles_board_bio_filter before insert or update of bio on public.profiles
  for each row execute function private.board_filter_bio();

create function private.board_validate_drawing(p_drawing jsonb) returns void
language plpgsql immutable set search_path='' as $$
declare s jsonb; p jsonb; points integer:=0;
begin
  if p_drawing is null then return; end if;
  if jsonb_typeof(p_drawing)<>'object' or p_drawing->>'version' is distinct from '1' or p_drawing->>'width' is distinct from '800' or p_drawing->>'height' is distinct from '600'
    or jsonb_typeof(p_drawing->'strokes') is distinct from 'array' or octet_length(p_drawing::text)>524288 then
    raise exception 'Invalid drawing document' using errcode='22023';
  end if;
  if jsonb_array_length(p_drawing->'strokes')>1000 then raise exception 'This note has too many strokes' using errcode='22023'; end if;
  if p_drawing - array['version','width','height','strokes'] <> '{}'::jsonb then raise exception 'Unsupported drawing fields' using errcode='22023'; end if;
  for s in select value from jsonb_array_elements(p_drawing->'strokes') loop
    if s - array['pen','color','size','points'] <> '{}'::jsonb then raise exception 'Unsupported drawing stroke fields' using errcode='22023'; end if;
    if coalesce(s->>'pen','') not in ('pixel','smooth','eraser') or coalesce(s->>'color','') not in
      ('#222222','#E84A5F','#F6B93B','#4CAF70','#3379D6','#9564C8','#FFFFFF')
      or jsonb_typeof(s->'size') is distinct from 'number' or (s->>'size')::numeric not between 1 and 40
      or jsonb_typeof(s->'points') is distinct from 'array' then raise exception 'Invalid drawing stroke' using errcode='22023'; end if;
    points:=points+jsonb_array_length(s->'points');
    if points>20000 or jsonb_array_length(s->'points')<1 then raise exception 'Invalid drawing point count' using errcode='22023'; end if;
    for p in select value from jsonb_array_elements(s->'points') loop
      if jsonb_typeof(p)<>'array' or jsonb_array_length(p)<>2 or jsonb_typeof(p->0)<>'number' or jsonb_typeof(p->1)<>'number'
        or (p->>0)::numeric not between 0 and 800 or (p->>1)::numeric not between 0 and 600 then
        raise exception 'Drawing points must stay on the paper' using errcode='22023';
      end if;
    end loop;
  end loop;
end $$;
create function private.board_drawing_preview(p_drawing jsonb) returns text
language plpgsql immutable set search_path='' as $$
declare s jsonb; coords text; result text:='<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600"><rect width="800" height="600" fill="white"/>';
begin
  if p_drawing is null then return null; end if;
  perform private.board_validate_drawing(p_drawing);
  for s in select value from jsonb_array_elements(p_drawing->'strokes') loop
    if jsonb_array_length(s->'points')=1 then
      if s->>'pen'='pixel' then
        result:=result||format('<rect x="%s" y="%s" width="%s" height="%s" fill="%s" shape-rendering="crispEdges"/>',
          (s->'points'->0->>0)::numeric-(s->>'size')::numeric/2,(s->'points'->0->>1)::numeric-(s->>'size')::numeric/2,s->>'size',s->>'size',s->>'color');
      else result:=result||format('<circle cx="%s" cy="%s" r="%s" fill="%s"/>',s->'points'->0->>0,s->'points'->0->>1,(s->>'size')::numeric/2,case when s->>'pen'='eraser' then '#FFFFFF' else s->>'color' end); end if;
      continue;
    end if;
    select string_agg((value->>0)||','||(value->>1),' ' order by ordinality) into coords from jsonb_array_elements(s->'points') with ordinality;
    -- Duplicate the first point to render taps as a dot as well as continuous lines.
    result:=result||format('<polyline points="%s %s" fill="none" stroke="%s" stroke-width="%s" stroke-linecap="%s" stroke-linejoin="%s" shape-rendering="%s"/>',
      (s->'points'->0->>0)||','||(s->'points'->0->>1),coords,
      case when s->>'pen'='eraser' then '#FFFFFF' else s->>'color' end,s->>'size',
      case when s->>'pen'='pixel' then 'square' else 'round' end,case when s->>'pen'='pixel' then 'miter' else 'round' end,case when s->>'pen'='pixel' then 'crispEdges' else 'auto' end);
  end loop;
  return result||'</svg>';
end $$;

create function private.board_validate_artwork(p_artwork jsonb) returns void
language plpgsql immutable set search_path='' as $$
begin
  if p_artwork is null then return; end if;
  if jsonb_typeof(p_artwork)<>'object' or p_artwork-array['version','background','drawing']<>'{}'::jsonb
    or coalesce(p_artwork->>'version','1')<>'1' or coalesce(p_artwork->>'background','#FFFFFF')<>'#FFFFFF' then
    raise exception 'Stationery uses a version 1 white-paper drawing manifest' using errcode='22023'; end if;
  perform private.board_validate_drawing(nullif(p_artwork->'drawing','null'::jsonb));
end $$;
create function private.board_note_preview(p_drawing jsonb,p_artwork jsonb) returns text
language plpgsql immutable set search_path='' as $$
declare preview text;
begin
  perform private.board_validate_artwork(p_artwork);
  preview:=private.board_drawing_preview(p_drawing);
  if p_artwork->'drawing' is not null and p_artwork->'drawing'<>'null'::jsonb then
    preview:=replace(preview,'<rect width="800" height="600" fill="white"/>',private.board_drawing_preview(p_artwork->'drawing'));
  end if;
  return preview;
end $$;

create function private.board_stationery_allowed(p_user uuid,p_id text) returns boolean
language sql stable security definer set search_path='' as $$
  select exists(select 1 from private.board_stationery s where s.id=p_id and
    (exists(select 1 from private.board_stationery_owned o where o.user_id=p_user and o.stationery_id=s.id)
      or (s.active and (s.access='free' or private.is_supporter(p_user)
        or (s.access='achievement' and exists(select 1 from public.achievement_unlocks a where a.user_id=p_user and a.achievement_key=s.achievement_key))))));
$$;

create function private.board_json(p_board uuid) returns jsonb
language sql stable security definer set search_path='' as $$
  select (to_jsonb(b)-'transfer_to')||jsonb_build_object('role',private.board_role(b.id,auth.uid()),
    'member_count',(select count(*) from private.board_members where board_id=b.id),
    'muted',coalesce(m.muted,false),'push_enabled',coalesce(m.push_enabled,true),
    'join_requested',exists(select 1 from private.board_join_requests where board_id=b.id and user_id=auth.uid()),
    'transfer_pending',b.transfer_to=auth.uid())
  from private.boards b left join private.board_members m on m.board_id=b.id and m.user_id=auth.uid() where b.id=p_board;
$$;
create function private.board_post_json(p_id uuid,p_reveal boolean default false) returns jsonb
language sql stable security definer set search_path='' as $$
  select jsonb_build_object('id',p.id,'board_id',p.board_id,'author_id',p.author_id,'author_name',coalesce(u.display_name,'Deleted account'),
    'author_avatar',u.avatar_path,'thread_id',p.thread_id,'reply_to',p.reply_to,'spoiler',p.spoiler,'removed',p.removed,
    'body',case when p.spoiler and not p_reveal then '' else p.body end,
    'drawing',case when p.spoiler and not p_reveal then null else p.drawing end,
    'stationery',case when p.spoiler and not p_reveal then null else p.stationery end,
    'has_drawing',p.drawing is not null,'created_at',p.created_at,'activity_at',p.activity_at,'edited_at',p.edited_at,
    'yeah_count',(select count(*) from private.board_reactions r where r.post_id=p.id and not private.board_blocked(auth.uid(),r.user_id)),
    'yeah',exists(select 1 from private.board_reactions r where r.post_id=p.id and r.user_id=auth.uid()),
    'reply_count',(select count(*) from private.board_posts r where r.thread_id=p.id and not private.board_blocked(auth.uid(),r.author_id)))
  from private.board_posts p left join public.profiles u on u.user_id=p.author_id where p.id=p_id;
$$;

-- Explicitly revoke helper execution, including functions with default arguments.
do $$ declare r record; begin
  for r in select p.oid::regprocedure signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where n.nspname='private' and p.proname like 'board\_%' escape '\' loop
    execute 'revoke all on function '||r.signature||' from public, anon, authenticated';
  end loop;
end $$;

commit;
