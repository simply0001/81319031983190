begin;

-- Connected apps may read the user's Puzzle Swap collection behind a new
-- puzzles:read scope. Buying pieces stays first-party.

create or replace function private.api_scope_keys()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array[
    'profile:read',
    'friends:read',
    'friends:write',
    'messages:read',
    'messages:write',
    'groups:write',
    'notifications:read',
    'presence:read',
    'presence:write',
    'tokens:read',
    'encounters:read',
    'puzzles:read'
  ]::text[];
$$;

create or replace function private.api_scope_descriptions()
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'profile:read', 'See your profile (name, bio, avatar, age, country)',
    'friends:read', 'See your friends list and friend requests',
    'friends:write', 'Add and remove friends and answer friend requests as you',
    'messages:read', 'Read your conversations and messages',
    'messages:write', 'Send, edit and delete messages as you',
    'groups:write', 'Create group chats and manage their members as you',
    'notifications:read', 'See and clear your notifications',
    'presence:read', 'See which of your friends are online and who is active in your chats',
    'presence:write', 'Show you as online and typing to your friends',
    'tokens:read', 'See your token balance and supporter status',
    'encounters:read', 'See the people you have met nearby',
    'puzzles:read', 'See your Puzzle Swap progress'
  );
$$;

-- The collection as one JSON object, shared by the app RPC and the public API.
-- Reads only: a player who never opened Puzzle Swap gets the own-Piip entry
-- with no pieces and no current puzzle.

create or replace function private.puzzle_collection_json(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_puzzles jsonb;
  v_current_index integer;
  v_owned_total integer;
begin
  with entry as (
    select
      0 as play_order,
      'own_piip' as kind,
      'own_piip' as puzzle_key,
      null::uuid as panel_id,
      null::text as slug,
      null::text as title,
      null::text as image_path,
      4 as grid_columns,
      4 as grid_rows
    union all
    select
      1000 + row_number() over (order by panel.sort_order, panel.id),
      'panel',
      'panel:' || panel.id::text,
      panel.id,
      panel.slug,
      panel.title,
      panel.image_path,
      panel.grid_columns,
      panel.grid_rows
    from private.available_puzzle_panels() as panel
    union all
    select
      2000 + row_number() over (order by progress.started_at, panel.id),
      'panel',
      progress.puzzle_key,
      panel.id,
      panel.slug,
      panel.title,
      panel.image_path,
      panel.grid_columns,
      panel.grid_rows
    from public.puzzle_progress as progress
    join public.puzzle_panels as panel on panel.id = progress.panel_id
    where progress.user_id = p_user_id
      and not exists (
        select 1
        from private.available_puzzle_panels() as available
        where available.id = panel.id
      )
  ),
  ordered as (
    select
      (row_number() over (order by entry.play_order))::integer - 1 as idx,
      entry.kind,
      entry.puzzle_key,
      entry.panel_id,
      entry.slug,
      entry.title,
      entry.image_path,
      coalesce(progress.grid_columns, entry.grid_columns) as grid_columns,
      coalesce(progress.grid_rows, entry.grid_rows) as grid_rows,
      progress.started_at,
      progress.completed_at,
      coalesce(progress.completed_by_handover, false) as completed_by_handover,
      progress.user_id is not null and progress.completed_at is null as is_open
    from entry
    left join public.puzzle_progress as progress
      on progress.user_id = p_user_id
      and progress.puzzle_key = entry.puzzle_key
  )
  select
    jsonb_agg(
      jsonb_build_object(
        'index', ordered.idx,
        'kind', ordered.kind,
        'puzzle_key', ordered.puzzle_key,
        'panel_id', ordered.panel_id,
        'slug', ordered.slug,
        'title', ordered.title,
        'image_path', ordered.image_path,
        'columns', ordered.grid_columns,
        'rows', ordered.grid_rows,
        'total_pieces', ordered.grid_columns * ordered.grid_rows,
        'owned_pieces', coalesce(
          (
            select array_agg(piece.piece_index order by piece.piece_index)
            from public.puzzle_pieces as piece
            where piece.user_id = p_user_id
              and piece.puzzle_key = ordered.puzzle_key
          ),
          '{}'::integer[]
        ),
        'started_at', ordered.started_at,
        'completed_at', ordered.completed_at,
        'completed_by_handover', ordered.completed_by_handover
      )
      order by ordered.idx
    ),
    min(ordered.idx) filter (where ordered.is_open)
  into v_puzzles, v_current_index
  from ordered;

  select count(*)::integer
  into v_owned_total
  from public.puzzle_pieces as piece
  where piece.user_id = p_user_id;

  return jsonb_build_object(
    'current_index', v_current_index,
    'piece_price', private.puzzle_piece_price(),
    'pieces_owned_total', v_owned_total,
    'puzzles', coalesce(v_puzzles, '[]'::jsonb)
  );
end;
$$;

revoke all on function private.puzzle_collection_json(uuid) from public;

comment on function private.puzzle_collection_json(uuid) is 'One player''s Puzzle Swap collection as the JSON object both get_puzzle_collection and the public API return. Read-only.';

create or replace function public.get_puzzle_collection()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if not exists (
    select 1
    from public.puzzle_progress as progress
    where progress.user_id = v_actor_id
      and progress.puzzle_key = 'own_piip'
  ) or not exists (
    select 1
    from public.puzzle_progress as progress
    where progress.user_id = v_actor_id
      and progress.completed_at is null
  ) then
    perform 1
    from public.profiles as profile
    where profile.user_id = v_actor_id
    for update;
    if not found then
      raise exception 'Profile is unavailable' using errcode = 'P0002';
    end if;
    perform private.ensure_puzzle_collection(v_actor_id);
  end if;

  return private.puzzle_collection_json(v_actor_id);
end;
$$;

-- puzzles.get

create or replace function public.api_v1_puzzles_get(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('puzzles:read');
  v_user_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, '{}'::text[]);
    return private.puzzle_collection_json(v_user_id);
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

revoke all on function public.api_v1_puzzles_get(jsonb) from public, anon, authenticated, service_role;
grant execute on function public.api_v1_puzzles_get(jsonb) to api_client;

comment on function public.api_v1_puzzles_get(jsonb) is
  'Public API: puzzles.get — the connected user''s Puzzle Swap collection in play order (puzzles:read).';

commit;
