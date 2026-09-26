begin;

-- Puzzle Swap: every account collects puzzles in a fixed order. The first is
-- always the player's own Piip (4x4, the picture is rendered on the device),
-- followed by artwork panels from the catalogue below, one at a time. Pieces
-- arrive from confirmed encounters (a handover of a piece the other player owns
-- when possible), from step milestones and from token purchases.

create table public.puzzle_panels (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  image_path text not null,
  grid_columns integer not null default 5,
  grid_rows integer not null default 4,
  sort_order integer not null unique,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  constraint puzzle_panels_slug_format check (slug ~ '^[a-z0-9_]{1,64}$'),
  constraint puzzle_panels_title_length check (char_length(btrim(title)) between 1 and 64),
  constraint puzzle_panels_image_path_shape check (image_path ~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}$'),
  constraint puzzle_panels_grid_range check (
    grid_columns between 2 and 10 and grid_rows between 2 and 10
  )
);

alter table public.puzzle_panels enable row level security;

create policy puzzle_panels_select_active
on public.puzzle_panels
for select
to authenticated
using (is_active);

revoke all on table public.puzzle_panels from anon, authenticated;
grant select on table public.puzzle_panels to authenticated;

comment on table public.puzzle_panels is 'Catalogue of artwork puzzles. A panel joins players'' collections once it is active and its artwork object exists in the puzzle-panels bucket; retire one with is_active = false (rows that reference it cannot be deleted).';

insert into public.puzzle_panels (id, slug, title, image_path, grid_columns, grid_rows, sort_order)
values (
  '7a3c1e10-9d2f-4b7e-8c51-0f6a2b4d9e01',
  'pocki_happy',
  'Pocki Happy',
  'panels/pocki_happy.png',
  3,
  5,
  10
);

create table public.puzzle_progress (
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  puzzle_key text not null,
  panel_id uuid references public.puzzle_panels (id) on delete restrict,
  grid_columns integer not null,
  grid_rows integer not null,
  total_pieces integer generated always as (grid_columns * grid_rows) stored,
  started_at timestamptz not null default now(),
  completed_at timestamptz,
  completed_by_handover boolean not null default false,
  primary key (user_id, puzzle_key),
  constraint puzzle_progress_key_shape check (
    (puzzle_key = 'own_piip' and panel_id is null)
    or (panel_id is not null and puzzle_key = 'panel:' || panel_id::text)
  ),
  constraint puzzle_progress_grid_range check (
    grid_columns between 2 and 10 and grid_rows between 2 and 10
  ),
  constraint puzzle_progress_completed_after_started check (
    completed_at is null or completed_at >= started_at
  )
);

create unique index puzzle_progress_one_open_per_user
  on public.puzzle_progress (user_id)
  where completed_at is null;

create index puzzle_progress_panel_idx
  on public.puzzle_progress (panel_id)
  where panel_id is not null;

alter table public.puzzle_progress enable row level security;

create policy puzzle_progress_select_owner
on public.puzzle_progress
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.puzzle_progress from anon, authenticated;
grant select on table public.puzzle_progress to authenticated;

comment on table public.puzzle_progress is 'One row per puzzle a player has started. The grid is copied from the catalogue when the puzzle starts so later catalogue edits cannot move pieces; the partial unique index keeps exactly one puzzle open per player.';

create table public.puzzle_pieces (
  user_id uuid not null,
  puzzle_key text not null,
  piece_index integer not null,
  source text not null,
  from_user_id uuid references public.profiles (user_id) on delete set null,
  encounter_id uuid references public.nearby_encounters (id) on delete set null,
  acquired_at timestamptz not null default now(),
  primary key (user_id, puzzle_key, piece_index),
  foreign key (user_id, puzzle_key)
    references public.puzzle_progress (user_id, puzzle_key) on delete cascade,
  constraint puzzle_pieces_index_non_negative check (piece_index >= 0),
  constraint puzzle_pieces_source_known check (
    source in ('start', 'encounter', 'handover', 'steps', 'purchase')
  )
);

create index puzzle_pieces_from_user_idx
  on public.puzzle_pieces (from_user_id)
  where from_user_id is not null;

create index puzzle_pieces_encounter_idx
  on public.puzzle_pieces (encounter_id)
  where encounter_id is not null;

alter table public.puzzle_pieces enable row level security;

create policy puzzle_pieces_select_owner
on public.puzzle_pieces
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.puzzle_pieces from anon, authenticated;
grant select on table public.puzzle_pieces to authenticated;

comment on table public.puzzle_pieces is 'Pieces a player owns, indexed row-major from 0, with how each one arrived; handover rows name the player who gave the piece.';

create table private.puzzle_encounter_grants (
  encounter_id uuid primary key references public.nearby_encounters (id) on delete cascade,
  granted_at timestamptz not null default now()
);

revoke all on table private.puzzle_encounter_grants from public;

comment on table private.puzzle_encounter_grants is 'One row per confirmed encounter that handed out puzzle pieces; the primary key makes retried receipts a no-op.';

alter table private.step_reward_days
  add column pieces_awarded integer not null default 0;

alter table private.step_reward_days
  add constraint step_reward_days_pieces_range check (pieces_awarded between 0 and 2);

insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'puzzle-panels',
  'puzzle-panels',
  true,
  5242880,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create policy pocketpass_puzzle_panels_read
on storage.objects
for select
to authenticated
using (bucket_id = 'puzzle-panels');

create or replace function private.puzzle_piece_price()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 15;
$$;

revoke all on function private.puzzle_piece_price() from public;

create or replace function private.puzzle_step_milestone_steps()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 5000;
$$;

revoke all on function private.puzzle_step_milestone_steps() from public;

create or replace function private.puzzle_step_pieces_per_day()
returns integer
language sql
immutable
set search_path = ''
as $$
  select 2;
$$;

revoke all on function private.puzzle_step_pieces_per_day() from public;

create or replace function private.available_puzzle_panels()
returns setof public.puzzle_panels
language sql
stable
security definer
set search_path = ''
as $$
  select panel.*
  from public.puzzle_panels as panel
  where panel.is_active
    and exists (
      select 1
      from storage.objects as object
      where object.bucket_id = 'puzzle-panels'
        and object.name = panel.image_path
    )
  order by panel.sort_order, panel.id;
$$;

revoke all on function private.available_puzzle_panels() from public;

comment on function private.available_puzzle_panels() is 'Active catalogue panels whose artwork has been uploaded, in play order.';

create or replace function private.start_next_puzzle(p_user_id uuid)
returns text
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_panel public.puzzle_panels;
  v_key text;
begin
  if exists (
    select 1
    from public.puzzle_progress as progress
    where progress.user_id = p_user_id
      and progress.completed_at is null
  ) then
    return null;
  end if;

  select panel.*
  into v_panel
  from private.available_puzzle_panels() as panel
  where not exists (
    select 1
    from public.puzzle_progress as progress
    where progress.user_id = p_user_id
      and progress.panel_id = panel.id
  )
  order by panel.sort_order, panel.id
  limit 1;
  if not found then
    return null;
  end if;

  v_key := 'panel:' || v_panel.id::text;

  insert into public.puzzle_progress (user_id, puzzle_key, panel_id, grid_columns, grid_rows)
  values (p_user_id, v_key, v_panel.id, v_panel.grid_columns, v_panel.grid_rows);

  insert into public.puzzle_pieces (user_id, puzzle_key, piece_index, source)
  values (
    p_user_id,
    v_key,
    floor(random() * (v_panel.grid_columns * v_panel.grid_rows))::integer,
    'start'
  );

  return v_key;
end;
$$;

revoke all on function private.start_next_puzzle(uuid) from public;

create or replace function private.ensure_puzzle_collection(p_user_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  insert into public.puzzle_progress (user_id, puzzle_key, grid_columns, grid_rows)
  values (p_user_id, 'own_piip', 4, 4)
  on conflict on constraint puzzle_progress_pkey do nothing;

  if found then
    insert into public.puzzle_pieces (user_id, puzzle_key, piece_index, source)
    values (p_user_id, 'own_piip', 5, 'start')
    on conflict do nothing;
  end if;

  if not exists (
    select 1
    from public.puzzle_progress as progress
    where progress.user_id = p_user_id
      and progress.completed_at is null
  ) then
    perform private.start_next_puzzle(p_user_id);
  end if;
end;
$$;

revoke all on function private.ensure_puzzle_collection(uuid) from public;

comment on function private.ensure_puzzle_collection(uuid) is 'Gives a player the own-Piip puzzle with its first piece (always piece 5, which the app also shows before its first sync) and opens the next catalogue panel when nothing is in progress. Callers hold the profile row lock.';

create or replace function private.grant_puzzle_piece(
  p_user_id uuid,
  p_source text,
  p_from_user_id uuid,
  p_encounter_id uuid
)
returns table (
  granted_key text,
  granted_panel_id uuid,
  granted_index integer,
  granted_source text,
  owned_count integer,
  total_count integer,
  puzzle_completed boolean,
  next_key text
)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_progress public.puzzle_progress;
  v_index integer;
  v_source text := p_source;
  v_from uuid;
  v_owned integer;
  v_completed boolean := false;
  v_next text;
  v_title text;
  v_giver text;
begin
  perform private.ensure_puzzle_collection(p_user_id);

  select progress.*
  into v_progress
  from public.puzzle_progress as progress
  where progress.user_id = p_user_id
    and progress.completed_at is null
  for update;
  if not found then
    return;
  end if;

  if p_from_user_id is not null and v_progress.panel_id is not null then
    select candidate.piece_index
    into v_index
    from public.puzzle_pieces as candidate
    where candidate.user_id = p_from_user_id
      and candidate.puzzle_key = v_progress.puzzle_key
      and not exists (
        select 1
        from public.puzzle_pieces as owned
        where owned.user_id = p_user_id
          and owned.puzzle_key = v_progress.puzzle_key
          and owned.piece_index = candidate.piece_index
      )
    order by random()
    limit 1;
    if v_index is not null then
      v_source := 'handover';
      v_from := p_from_user_id;
    end if;
  end if;

  if v_index is null then
    select missing.piece_index
    into v_index
    from generate_series(0, v_progress.total_pieces - 1) as missing(piece_index)
    where not exists (
      select 1
      from public.puzzle_pieces as owned
      where owned.user_id = p_user_id
        and owned.puzzle_key = v_progress.puzzle_key
        and owned.piece_index = missing.piece_index
    )
    order by random()
    limit 1;
    if v_index is null then
      return;
    end if;
  end if;

  insert into public.puzzle_pieces (
    user_id,
    puzzle_key,
    piece_index,
    source,
    from_user_id,
    encounter_id
  )
  values (p_user_id, v_progress.puzzle_key, v_index, v_source, v_from, p_encounter_id);

  select count(*)::integer
  into v_owned
  from public.puzzle_pieces as owned
  where owned.user_id = p_user_id
    and owned.puzzle_key = v_progress.puzzle_key;

  if v_progress.panel_id is not null then
    select panel.title
    into v_title
    from public.puzzle_panels as panel
    where panel.id = v_progress.panel_id;
  end if;
  v_title := coalesce(v_title, 'Your Piip');

  if v_owned >= v_progress.total_pieces then
    update public.puzzle_progress as progress
    set completed_at = now(),
        completed_by_handover = (v_source = 'handover')
    where progress.user_id = p_user_id
      and progress.puzzle_key = v_progress.puzzle_key;
    v_completed := true;
    v_next := private.start_next_puzzle(p_user_id);

    insert into public.notifications (recipient_id, kind, title, body)
    values (p_user_id, 'system', 'Puzzle complete', v_title || ' is finished!');
  end if;

  if v_source = 'handover' then
    select profile.display_name
    into v_giver
    from public.profiles as profile
    where profile.user_id = v_from;

    insert into public.notifications (recipient_id, kind, actor_id, title, body)
    values (
      p_user_id,
      'system',
      v_from,
      'Puzzle piece received',
      coalesce(v_giver, 'Someone') || ' handed you a piece of ' || v_title
    );
  end if;

  return query
  select
    v_progress.puzzle_key,
    v_progress.panel_id,
    v_index,
    v_source,
    v_owned,
    v_progress.total_pieces,
    v_completed,
    v_next;
end;
$$;

revoke all on function private.grant_puzzle_piece(uuid, text, uuid, uuid) from public;

comment on function private.grant_puzzle_piece(uuid, text, uuid, uuid) is 'Adds one piece to the player''s open puzzle: a piece the giver owns and the player lacks when a giver is named (a handover), otherwise a random missing piece. Completes the puzzle and opens the next one when the last piece lands. Callers hold the profile row lock.';

create or replace function private.grant_encounter_puzzle_pieces(p_encounter_id uuid)
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_encounter public.nearby_encounters;
  v_count integer := 0;
begin
  select encounter.*
  into v_encounter
  from public.nearby_encounters as encounter
  where encounter.id = p_encounter_id
    and encounter.confirmed_at is not null;
  if not found then
    return 0;
  end if;

  insert into private.puzzle_encounter_grants (encounter_id)
  values (v_encounter.id)
  on conflict do nothing;
  if not found then
    return 0;
  end if;

  perform 1
  from public.profiles as profile
  where profile.user_id in (v_encounter.user_low, v_encounter.user_high)
  order by profile.user_id
  for update;

  if exists (
    select 1
    from private.grant_puzzle_piece(
      v_encounter.user_low,
      'encounter',
      v_encounter.user_high,
      v_encounter.id
    )
  ) then
    v_count := v_count + 1;
  end if;

  if exists (
    select 1
    from private.grant_puzzle_piece(
      v_encounter.user_high,
      'encounter',
      v_encounter.user_low,
      v_encounter.id
    )
  ) then
    v_count := v_count + 1;
  end if;

  return v_count;
end;
$$;

revoke all on function private.grant_encounter_puzzle_pieces(uuid) from public;

create or replace function private.grant_puzzle_pieces_for_encounter()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.confirmed_at is null then
    return null;
  end if;
  if tg_op = 'UPDATE' and old.confirmed_at is not null then
    return null;
  end if;
  perform private.grant_encounter_puzzle_pieces(new.id);
  return null;
end;
$$;

revoke all on function private.grant_puzzle_pieces_for_encounter() from public;

drop trigger if exists nearby_encounters_grant_puzzle_pieces on public.nearby_encounters;
create trigger nearby_encounters_grant_puzzle_pieces
after insert or update of confirmed_at on public.nearby_encounters
for each row execute function private.grant_puzzle_pieces_for_encounter();

drop function public.report_daily_steps(date, integer, integer);

create function public.report_daily_steps(
  p_local_day date,
  p_steps integer,
  p_utc_offset_minutes integer
)
returns table (
  local_day date,
  steps integer,
  tokens_awarded integer,
  tokens_credited integer,
  balance integer,
  pieces_awarded integer,
  pieces_credited integer
)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_client_today date;
  v_row private.step_reward_days;
  v_target integer;
  v_credit integer;
  v_balance integer;
  v_piece_target integer;
  v_piece_credit integer;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_local_day is null or p_steps is null or p_utc_offset_minutes is null then
    raise exception 'Step report is incomplete' using errcode = '22004';
  end if;

  if p_steps < 0 or p_steps > 200000 then
    raise exception 'Step count is out of range' using errcode = '22023';
  end if;

  if p_utc_offset_minutes < -840 or p_utc_offset_minutes > 840 then
    raise exception 'UTC offset is out of range' using errcode = '22023';
  end if;

  v_client_today := (
    (now() at time zone 'UTC') + make_interval(mins => p_utc_offset_minutes)
  )::date;
  if p_local_day <> v_client_today and p_local_day <> v_client_today - 1 then
    raise sqlstate 'PT422' using
      message = 'Step day is outside the accepted window',
      detail = 'client_today=' || v_client_today::text,
      hint = 'STEP_DAY_REJECTED';
  end if;

  perform 1
  from public.profiles as profile
  where profile.user_id = v_actor_id
  for update;
  if not found then
    raise exception 'Profile is unavailable' using errcode = 'P0002';
  end if;

  insert into private.step_reward_days as reward_day (user_id, local_day, steps)
  values (v_actor_id, p_local_day, p_steps)
  on conflict on constraint step_reward_days_pkey do update
    set steps = greatest(reward_day.steps, excluded.steps),
        updated_at = now()
  returning reward_day.* into v_row;

  v_target := least(25, v_row.steps / 400);
  v_credit := greatest(v_target - v_row.tokens_awarded, 0);
  v_piece_target := least(
    private.puzzle_step_pieces_per_day(),
    v_row.steps / private.puzzle_step_milestone_steps()
  );
  v_piece_credit := greatest(v_piece_target - v_row.pieces_awarded, 0);

  perform private.ensure_token_balance(v_actor_id);

  if v_credit > 0 then
    update public.token_balances as token_balance
    set balance = token_balance.balance + v_credit,
        updated_at = now()
    where token_balance.user_id = v_actor_id
    returning token_balance.balance into v_balance;

    update private.step_reward_days as reward_day
    set tokens_awarded = v_target,
        updated_at = now()
    where reward_day.user_id = v_actor_id
      and reward_day.local_day = p_local_day;
  else
    select token_balance.balance
    into v_balance
    from public.token_balances as token_balance
    where token_balance.user_id = v_actor_id;
  end if;

  if v_piece_credit > 0 then
    for i in 1..v_piece_credit loop
      perform private.grant_puzzle_piece(v_actor_id, 'steps', null, null);
    end loop;

    update private.step_reward_days as reward_day
    set pieces_awarded = v_piece_target,
        updated_at = now()
    where reward_day.user_id = v_actor_id
      and reward_day.local_day = p_local_day;
  end if;

  if v_target >= 25 and v_row.cap_notified_at is null then
    insert into public.notifications (recipient_id, kind, title, body)
    values (
      v_actor_id,
      'system',
      'Tokens earned',
      'Step goal reached: +25 tokens for 10,000 steps today'
    );

    update private.step_reward_days as reward_day
    set cap_notified_at = now()
    where reward_day.user_id = v_actor_id
      and reward_day.local_day = p_local_day;
  end if;

  return query
  select p_local_day, v_row.steps, v_target, v_credit, v_balance, v_piece_target, v_piece_credit;
end;
$$;

revoke all on function public.report_daily_steps(date, integer, integer) from public, anon;
grant execute on function public.report_daily_steps(date, integer, integer) to authenticated;

comment on function public.report_daily_steps(date, integer, integer) is 'Records the caller''s step count for a local day (today or yesterday by the supplied UTC offset), keeps the highest count reported, pays 1 token per 400 steps up to 25 per day and one puzzle piece per 5,000 steps up to 2 per day; the once-per-day notification fires when the token cap is reached. Returns the day''s totals and the new balance.';

create or replace function public.buy_puzzle_piece(p_client_operation_id uuid)
returns table (
  puzzle_key text,
  panel_id uuid,
  piece_index integer,
  pieces_owned integer,
  total_pieces integer,
  completed boolean,
  next_puzzle_key text,
  price_paid integer,
  balance integer
)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_price integer := private.puzzle_piece_price();
  v_balance integer;
  v_grant record;
  v_is_replay boolean;
  v_replay_response jsonb;
  v_request_payload jsonb := '{}'::jsonb;
  v_response_payload jsonb;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;
  if p_client_operation_id is null then
    raise exception 'Client operation id is required' using errcode = '22004';
  end if;

  select operation.is_replay, operation.response_payload
  into v_is_replay, v_replay_response
  from private.begin_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'buy_puzzle_piece',
    v_request_payload
  ) as operation;

  if v_is_replay then
    return query
    select
      v_replay_response ->> 'puzzle_key',
      (v_replay_response ->> 'panel_id')::uuid,
      (v_replay_response ->> 'piece_index')::integer,
      (v_replay_response ->> 'pieces_owned')::integer,
      (v_replay_response ->> 'total_pieces')::integer,
      (v_replay_response ->> 'completed')::boolean,
      v_replay_response ->> 'next_puzzle_key',
      (v_replay_response ->> 'price_paid')::integer,
      (v_replay_response ->> 'balance')::integer;
    return;
  end if;

  perform 1
  from public.profiles as profile
  where profile.user_id = v_actor_id
  for update;
  if not found then
    raise exception 'Profile is unavailable' using errcode = 'P0002';
  end if;

  perform private.ensure_puzzle_collection(v_actor_id);

  if not exists (
    select 1
    from public.puzzle_progress as progress
    where progress.user_id = v_actor_id
      and progress.completed_at is null
  ) then
    raise sqlstate 'PT409' using
      message = 'Every puzzle is complete',
      hint = 'COLLECTION_COMPLETE';
  end if;

  perform private.ensure_token_balance(v_actor_id);

  update public.token_balances as token_balance
  set
    balance = token_balance.balance - v_price,
    updated_at = now()
  where token_balance.user_id = v_actor_id
    and token_balance.balance >= v_price
  returning token_balance.balance into v_balance;
  if not found then
    raise sqlstate 'PT402' using
      message = 'Not enough tokens',
      detail = 'price=' || v_price::text,
      hint = 'INSUFFICIENT_TOKENS';
  end if;

  select granted.*
  into v_grant
  from private.grant_puzzle_piece(v_actor_id, 'purchase', null, null) as granted;
  if v_grant.granted_key is null then
    raise sqlstate 'PT409' using
      message = 'Every puzzle is complete',
      hint = 'COLLECTION_COMPLETE';
  end if;

  v_response_payload := jsonb_build_object(
    'puzzle_key', v_grant.granted_key,
    'panel_id', v_grant.granted_panel_id,
    'piece_index', v_grant.granted_index,
    'pieces_owned', v_grant.owned_count,
    'total_pieces', v_grant.total_count,
    'completed', v_grant.puzzle_completed,
    'next_puzzle_key', v_grant.next_key,
    'price_paid', v_price,
    'balance', v_balance
  );
  perform private.finish_rpc_operation(
    v_actor_id,
    p_client_operation_id,
    'buy_puzzle_piece',
    v_request_payload,
    v_response_payload
  );

  return query
  select
    v_grant.granted_key,
    v_grant.granted_panel_id,
    v_grant.granted_index,
    v_grant.owned_count,
    v_grant.total_count,
    v_grant.puzzle_completed,
    v_grant.next_key,
    v_price,
    v_balance;
end;
$$;

revoke all on function public.buy_puzzle_piece(uuid) from public, anon;
grant execute on function public.buy_puzzle_piece(uuid) to authenticated;

comment on function public.buy_puzzle_piece(uuid) is 'Spends the piece price in tokens for one random missing piece of the caller''s open puzzle. Replays of the same client operation id return the original piece without charging again. Raises PT402 INSUFFICIENT_TOKENS and PT409 COLLECTION_COMPLETE.';

create or replace function public.get_puzzle_collection()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_puzzles jsonb;
  v_current_index integer;
  v_owned_total integer;
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
    where progress.user_id = v_actor_id
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
      on progress.user_id = v_actor_id
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
            where piece.user_id = v_actor_id
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
  where piece.user_id = v_actor_id;

  return jsonb_build_object(
    'current_index', v_current_index,
    'piece_price', private.puzzle_piece_price(),
    'pieces_owned_total', v_owned_total,
    'puzzles', coalesce(v_puzzles, '[]'::jsonb)
  );
end;
$$;

revoke all on function public.get_puzzle_collection() from public, anon;
grant execute on function public.get_puzzle_collection() to authenticated;

comment on function public.get_puzzle_collection() is 'The caller''s puzzles in play order: the own-Piip puzzle first, then every available catalogue panel (not yet started ones included, with no pieces), then any panel the caller started that has since left the catalogue. Initialises the collection on first use. Returns current_index (null when everything is complete), piece_price, pieces_owned_total and the puzzles array.';

drop function private.achievement_metrics(uuid);

create function private.achievement_metrics(p_user_id uuid)
returns table (
  is_legacy boolean,
  token_balance integer,
  sent_any boolean,
  best_streak integer,
  has_friend boolean,
  confirmed_encounters integer,
  met_foreigner boolean,
  continents_met integer,
  puzzles_completed integer,
  puzzles_total integer,
  handover_completion boolean
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_country text;
begin
  select profile.country_code, coalesce(profile.legacy_account, false)
  into v_country, is_legacy
  from public.profiles as profile
  where profile.user_id = p_user_id;
  is_legacy := coalesce(is_legacy, false);

  select coalesce(max(balance_row.balance), 0)
  into token_balance
  from public.token_balances as balance_row
  where balance_row.user_id = p_user_id;

  sent_any := exists (
    select 1
    from public.messages as message
    where message.sender_id = p_user_id
  );

  select coalesce(max(run.length), 0)
  into best_streak
  from (
    select count(*) as length
    from (
      select
        sent.day,
        sent.day - (row_number() over (order by sent.day))::integer as bucket
      from (
        select distinct (message.created_at at time zone 'UTC')::date as day
        from public.messages as message
        where message.sender_id = p_user_id
      ) as sent
    ) as grouped
    group by grouped.bucket
  ) as run;

  has_friend := exists (
    select 1
    from public.friendships as friendship
    where p_user_id in (friendship.user_low, friendship.user_high)
  ) or exists (
    select 1
    from public.friend_requests as request
    where request.status = 'accepted'
      and p_user_id in (request.requester_id, request.addressee_id)
  );

  select count(*)::integer
  into confirmed_encounters
  from public.nearby_encounters as encounter
  where p_user_id in (encounter.user_low, encounter.user_high)
    and encounter.confirmed_at is not null;

  met_foreigner := v_country is not null and exists (
    select 1
    from public.nearby_encounters as encounter
    join public.profiles as peer
      on peer.user_id = case
        when encounter.user_low = p_user_id then encounter.user_high
        else encounter.user_low
      end
    where p_user_id in (encounter.user_low, encounter.user_high)
      and encounter.confirmed_at is not null
      and peer.country_code is not null
      and peer.country_code <> v_country
  );

  select count(distinct continent_map.continent)::integer
  into continents_met
  from public.nearby_encounters as encounter
  join public.profiles as peer
    on peer.user_id = case
      when encounter.user_low = p_user_id then encounter.user_high
      else encounter.user_low
    end
  join private.country_continents as continent_map
    on continent_map.country_code = peer.country_code
  where p_user_id in (encounter.user_low, encounter.user_high)
    and encounter.confirmed_at is not null
    and continent_map.continent <> 'Antarctica';

  select count(*)::integer
  into puzzles_completed
  from public.puzzle_progress as progress
  where progress.user_id = p_user_id
    and progress.completed_at is not null
    and (
      progress.panel_id is null
      or exists (
        select 1
        from private.available_puzzle_panels() as panel
        where panel.id = progress.panel_id
      )
    );

  select 1 + count(*)::integer
  into puzzles_total
  from private.available_puzzle_panels();

  handover_completion := exists (
    select 1
    from public.puzzle_progress as progress
    where progress.user_id = p_user_id
      and progress.completed_by_handover
  );

  return next;
end;
$$;

revoke all on function private.achievement_metrics(uuid) from public;

comment on function private.achievement_metrics(uuid) is
  'Raw tallies behind every achievement for one user, read from the authoritative tables. Shared by the achievements screen, the unlock sync and progress percentages.';

create or replace function private.sync_achievements(p_user_id uuid)
returns void
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  metric record;
begin
  if p_user_id is null
    or not exists (
      select 1
      from public.profiles as profile
      where profile.user_id = p_user_id
    )
  then
    return;
  end if;

  select * into metric from private.achievement_metrics(p_user_id);

  insert into public.achievement_unlocks (user_id, achievement_key)
  select p_user_id, candidate.key
  from (values
    ('day_one', metric.is_legacy),
    ('saving_up', metric.token_balance >= 500),
    ('icebreaker', metric.sent_any),
    ('streak', metric.best_streak >= 10),
    ('plus_one', metric.has_friend),
    ('first_encounter', metric.confirmed_encounters >= 1),
    ('small_world', metric.confirmed_encounters >= 10),
    ('passport_stamped', metric.met_foreigner),
    ('continental', metric.continents_met >= 6),
    ('full_set', metric.puzzles_total >= 2 and metric.puzzles_completed >= metric.puzzles_total),
    ('missing_piece', metric.handover_completion)
  ) as candidate(key, satisfied)
  where candidate.satisfied
  on conflict on constraint achievement_unlocks_pkey do nothing;
end;
$$;

revoke all on function private.sync_achievements(uuid) from public;

comment on function private.sync_achievements(uuid) is
  'Records every achievement the user currently satisfies. One-way: unlocks survive later regressions such as unfriending, spending tokens or new panels joining the catalogue. Called by table triggers as activity happens and by get_achievements for the caller.';

create or replace function public.get_achievements()
returns table (
  achievement_key text,
  unlocked boolean,
  unlocked_at timestamptz,
  progress_percent integer
)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  metric record;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  perform private.sync_achievements(v_actor_id);
  select * into metric from private.achievement_metrics(v_actor_id);

  return query
  select
    candidate.key,
    unlock.unlocked_at is not null,
    unlock.unlocked_at,
    case
      when unlock.unlocked_at is not null then 100
      else candidate.progress
    end
  from (values
    ('day_one', 0),
    ('saving_up', least(metric.token_balance * 100 / 500, 99)),
    ('icebreaker', 0),
    ('streak', least(metric.best_streak * 100 / 10, 99)),
    ('plus_one', 0),
    ('first_encounter', 0),
    ('small_world', least(metric.confirmed_encounters * 100 / 10, 99)),
    ('passport_stamped', 0),
    ('continental', least(metric.continents_met * 100 / 6, 99)),
    ('full_set', least(metric.puzzles_completed * 100 / metric.puzzles_total, 99)),
    ('missing_piece', 0)
  ) as candidate(key, progress)
  left join public.achievement_unlocks as unlock
    on unlock.user_id = v_actor_id
    and unlock.achievement_key = candidate.key;
end;
$$;

create or replace function private.sync_achievements_for_puzzle()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.sync_achievements(new.user_id);
  return null;
end;
$$;

revoke all on function private.sync_achievements_for_puzzle() from public;

drop trigger if exists puzzle_progress_sync_achievements on public.puzzle_progress;
create trigger puzzle_progress_sync_achievements
after insert or update of completed_at on public.puzzle_progress
for each row execute function private.sync_achievements_for_puzzle();

commit;
