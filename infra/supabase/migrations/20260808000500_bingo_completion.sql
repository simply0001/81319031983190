begin;

alter table public.bingo_goals
  add column kind text,
  add column target integer not null default 1,
  add column country_param text;

update public.bingo_goals set is_active = false
where slug in ('own_hat', 'buy_shop_item', 'own_3_items', 'change_mood', 'update_bio');

update public.bingo_goals set kind = 'meet_country', country_param = mapping.code
from (values
  ('meet_lebanon', 'LB'), ('meet_poland', 'PL'), ('meet_japan', 'JP'),
  ('meet_brazil', 'BR'), ('meet_canada', 'CA'), ('meet_egypt', 'EG'),
  ('meet_france', 'FR'), ('meet_india', 'IN'), ('meet_mexico', 'MX'),
  ('meet_italy', 'IT'), ('meet_kenya', 'KE'), ('meet_spain', 'ES'),
  ('meet_germany', 'DE'), ('meet_sweden', 'SE'), ('meet_norway', 'NO'),
  ('meet_australia', 'AU'), ('meet_south_korea', 'KR'), ('meet_china', 'CN'),
  ('meet_argentina', 'AR'), ('meet_nigeria', 'NG'), ('meet_greece', 'GR'),
  ('meet_turkey', 'TR'), ('meet_portugal', 'PT'), ('meet_netherlands', 'NL'),
  ('meet_ireland', 'IE'), ('meet_iceland', 'IS'), ('meet_thailand', 'TH')
) as mapping(goal_slug, code)
where public.bingo_goals.slug = mapping.goal_slug;

update public.bingo_goals set kind = 'meet_distinct_people', target = 5
where slug = 'wave_5';
update public.bingo_goals set short_label = '5 People'
where slug = 'wave_5';

update public.bingo_goals set kind = 'meet_distinct_people', target = 10,
  short_label = '10 People'
where slug = 'wave_10';

update public.bingo_goals set kind = 'meet_new_person',
  goal_text = 'Wave at someone new!', short_label = 'Fresh Face'
where slug = 'waves_back_5';

update public.bingo_goals set kind = 'meet_total_encounters', target = 10,
  goal_text = 'Rack up 10 waves!', short_label = '10 Waves'
where slug = 'waves_back_10';

update public.bingo_goals set kind = 'meet_distinct_people', target = 7,
  goal_text = 'Meet 7 people in one week!', short_label = '7 a Week'
where slug = 'meet_5_one_week';

update public.bingo_goals set kind = 'send_messages', target = 10
where slug = 'send_10_messages';
update public.bingo_goals set kind = 'send_messages', target = 25
where slug = 'send_25_messages';
update public.bingo_goals set kind = 'meet_n_in_one_day', target = 3
where slug = 'meet_3_one_day';
update public.bingo_goals set kind = 'meet_n_in_one_hour', target = 2
where slug = 'meet_2_one_hour';
update public.bingo_goals set kind = 'add_friends', target = 1
where slug = 'add_friend';
update public.bingo_goals set kind = 'add_friends', target = 3
where slug = 'add_3_friends';
update public.bingo_goals set kind = 'meet_at_night'
where slug = 'meet_at_night';
update public.bingo_goals set kind = 'meet_in_morning'
where slug = 'meet_morning';
update public.bingo_goals set kind = 'meet_on_weekend'
where slug = 'meet_weekend';
update public.bingo_goals set kind = 'meet_same_person_twice', target = 2
where slug = 'meet_same_twice';
update public.bingo_goals set kind = 'send_emoji'
where slug = 'send_emoji';
update public.bingo_goals set kind = 'send_photo'
where slug = 'send_photo';
update public.bingo_goals set kind = 'chat_streak_days', target = 3
where slug = 'chat_streak_3';
update public.bingo_goals set kind = 'quick_reply'
where slug = 'quick_reply';
update public.bingo_goals set kind = 'message_new_friend'
where slug = 'message_new_friend';
update public.bingo_goals set kind = 'message_distinct_friends', target = 3
where slug = 'message_3_friends';
update public.bingo_goals set kind = 'trophies_total', target = 10
where slug = 'reach_10_trophies';
update public.bingo_goals set kind = 'trophies_total', target = 25
where slug = 'reach_25_trophies';
update public.bingo_goals set kind = 'encounters_total', target = 50
where slug = 'reach_50_encounters';
update public.bingo_goals set kind = 'tokens_total', target = 100
where slug = 'save_100_tokens';
update public.bingo_goals set kind = 'meet_other_continent'
where slug = 'meet_other_continent';
update public.bingo_goals set kind = 'discover_region'
where slug = 'discover_region';
update public.bingo_goals set kind = 'mii_makeover'
where slug = 'mii_makeover';

alter table public.bingo_goals
  add constraint bingo_goals_target_positive check (target >= 1),
  add constraint bingo_goals_active_have_kind check (kind is not null or not is_active);

alter table public.notifications
  drop constraint notifications_kind_check;

alter table public.notifications
  add constraint notifications_kind_check check (
    kind in (
      'friend_request', 'friend_accepted', 'message', 'system',
      'nearby_encounter', 'bingo_award'
    )
  );

alter table public.notifications
  drop constraint notifications_shape_check;

alter table public.notifications
  add constraint notifications_shape_check check (
    (kind = 'friend_request' and friend_request_id is not null)
    or (kind = 'friend_accepted' and friend_request_id is not null)
    or (kind = 'message' and conversation_id is not null)
    or (kind = 'nearby_encounter' and actor_id is not null)
    or kind = 'system'
    or kind = 'bingo_award'
  );

create table public.bingo_stamps (
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  week_key text not null,
  slug text not null,
  stamped_at timestamptz not null default now(),
  primary key (user_id, week_key, slug),
  constraint bingo_stamps_week_format check (week_key ~ '^[0-9]{4}-[0-9]{2}$')
);

alter table public.bingo_stamps enable row level security;

create policy bingo_stamps_select_owner
on public.bingo_stamps
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.bingo_stamps from anon, authenticated;
grant select on table public.bingo_stamps to authenticated;

create table public.bingo_awards (
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  week_key text not null,
  lines_paid integer not null default 0,
  blackout_paid boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (user_id, week_key),
  constraint bingo_awards_week_format check (week_key ~ '^[0-9]{4}-[0-9]{2}$'),
  constraint bingo_awards_lines_range check (lines_paid between 0 and 12)
);

alter table public.bingo_awards enable row level security;

create policy bingo_awards_select_owner
on public.bingo_awards
for select
to authenticated
using (user_id = auth.uid());

revoke all on table public.bingo_awards from anon, authenticated;
grant select on table public.bingo_awards to authenticated;

create or replace function private.bingo_goal_progress(
  p_actor uuid,
  p_kind text,
  p_target integer,
  p_country text,
  p_week_start timestamptz
)
returns integer
language plpgsql
stable
set search_path = ''
as $$
begin
  if p_kind = 'meet_country' then
    return (
      select count(*)::integer
      from public.nearby_encounters as encounter
      join public.profiles as peer
        on peer.user_id = case
          when encounter.user_low = p_actor then encounter.user_high
          else encounter.user_low
        end
      where p_actor in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
        and encounter.occurred_at >= p_week_start
        and peer.country_code = p_country
    );
  elsif p_kind = 'meet_other_continent' then
    return (
      select count(*)::integer
      from public.nearby_encounters as encounter
      join public.profiles as peer
        on peer.user_id = case
          when encounter.user_low = p_actor then encounter.user_high
          else encounter.user_low
        end
      join public.profiles as me on me.user_id = p_actor
      join private.country_continents as peer_region
        on peer_region.country_code = peer.country_code
      join private.country_continents as own_region
        on own_region.country_code = me.country_code
      where p_actor in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
        and encounter.occurred_at >= p_week_start
        and peer_region.continent <> own_region.continent
    );
  elsif p_kind = 'meet_distinct_people' then
    return (
      select count(distinct case
        when encounter.user_low = p_actor then encounter.user_high
        else encounter.user_low
      end)::integer
      from public.nearby_encounters as encounter
      where p_actor in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
        and encounter.occurred_at >= p_week_start
    );
  elsif p_kind = 'meet_total_encounters' then
    return (
      select count(*)::integer
      from public.nearby_encounters as encounter
      where p_actor in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
        and encounter.occurred_at >= p_week_start
    );
  elsif p_kind = 'meet_new_person' then
    return (
      select count(*)::integer
      from (
        select case
          when encounter.user_low = p_actor then encounter.user_high
          else encounter.user_low
        end as peer_id
        from public.nearby_encounters as encounter
        where p_actor in (encounter.user_low, encounter.user_high)
          and encounter.confirmed_at is not null
          and encounter.occurred_at >= p_week_start
        group by 1
      ) as weekly_peer
      where not exists (
        select 1
        from public.nearby_encounters as prior
        where prior.user_low = least(p_actor, weekly_peer.peer_id)
          and prior.user_high = greatest(p_actor, weekly_peer.peer_id)
          and prior.confirmed_at is not null
          and prior.occurred_at < p_week_start
      )
    );
  elsif p_kind = 'meet_same_person_twice' then
    return coalesce((
      select max(meetings.total)::integer
      from (
        select count(*) as total
        from public.nearby_encounters as encounter
        where p_actor in (encounter.user_low, encounter.user_high)
          and encounter.confirmed_at is not null
          and encounter.occurred_at >= p_week_start
        group by case
          when encounter.user_low = p_actor then encounter.user_high
          else encounter.user_low
        end
      ) as meetings
    ), 0);
  elsif p_kind = 'meet_at_night' then
    return (
      select count(*)::integer
      from public.nearby_encounters as encounter
      where p_actor in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
        and encounter.occurred_at >= p_week_start
        and (
          extract(hour from encounter.occurred_at at time zone 'utc') >= 21
          or extract(hour from encounter.occurred_at at time zone 'utc') < 5
        )
    );
  elsif p_kind = 'meet_in_morning' then
    return (
      select count(*)::integer
      from public.nearby_encounters as encounter
      where p_actor in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
        and encounter.occurred_at >= p_week_start
        and extract(hour from encounter.occurred_at at time zone 'utc') >= 5
        and extract(hour from encounter.occurred_at at time zone 'utc') < 10
    );
  elsif p_kind = 'meet_on_weekend' then
    return (
      select count(*)::integer
      from public.nearby_encounters as encounter
      where p_actor in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
        and encounter.occurred_at >= p_week_start
        and extract(isodow from encounter.occurred_at at time zone 'utc') in (6, 7)
    );
  elsif p_kind = 'meet_n_in_one_day' then
    return coalesce((
      select max(daily.total)::integer
      from (
        select count(*) as total
        from public.nearby_encounters as encounter
        where p_actor in (encounter.user_low, encounter.user_high)
          and encounter.confirmed_at is not null
          and encounter.occurred_at >= p_week_start
        group by (encounter.occurred_at at time zone 'utc')::date
      ) as daily
    ), 0);
  elsif p_kind = 'meet_n_in_one_hour' then
    return coalesce((
      select max(windowed.total)::integer
      from (
        select count(*) as total
        from public.nearby_encounters as anchor
        join public.nearby_encounters as other
          on p_actor in (other.user_low, other.user_high)
          and other.confirmed_at is not null
          and other.occurred_at >= anchor.occurred_at
          and other.occurred_at < anchor.occurred_at + interval '1 hour'
        where p_actor in (anchor.user_low, anchor.user_high)
          and anchor.confirmed_at is not null
          and anchor.occurred_at >= p_week_start
        group by anchor.id
      ) as windowed
    ), 0);
  elsif p_kind = 'send_messages' then
    return (
      select count(*)::integer
      from public.messages as message
      where message.sender_id = p_actor
        and message.created_at >= p_week_start
    );
  elsif p_kind = 'send_photo' then
    return (
      select count(*)::integer
      from public.messages as message
      where message.sender_id = p_actor
        and message.created_at >= p_week_start
        and message.metadata ? 'attachment'
    );
  elsif p_kind = 'send_emoji' then
    return (
      select count(*)::integer
      from public.messages as message
      where message.sender_id = p_actor
        and message.created_at >= p_week_start
        and message.body ~ '[\U0001F300-\U0001FAFF]|[\U00002600-\U000027BF]|\U00002764'
    );
  elsif p_kind = 'chat_streak_days' then
    return coalesce((
      select max(runs.length)::integer
      from (
        select count(*) as length
        from (
          select
            sent.day,
            sent.day - (row_number() over (order by sent.day))::integer as bucket
          from (
            select distinct (message.created_at at time zone 'utc')::date as day
            from public.messages as message
            where message.sender_id = p_actor
              and message.created_at >= p_week_start
          ) as sent
        ) as grouped
        group by grouped.bucket
      ) as runs
    ), 0);
  elsif p_kind = 'quick_reply' then
    return (
      select count(*)::integer
      from public.messages as own_message
      where own_message.sender_id = p_actor
        and own_message.created_at >= p_week_start
        and exists (
          select 1
          from public.messages as peer_message
          where peer_message.conversation_id = own_message.conversation_id
            and peer_message.sender_id <> p_actor
            and own_message.created_at >= peer_message.created_at
            and own_message.created_at
              < peer_message.created_at + interval '60 seconds'
        )
    );
  elsif p_kind = 'add_friends' then
    return (
      select count(*)::integer
      from public.friendships as friendship
      where p_actor in (friendship.user_low, friendship.user_high)
        and friendship.created_at >= p_week_start
    );
  elsif p_kind = 'message_new_friend' then
    return (
      select count(distinct fresh.other_id)::integer
      from (
        select
          case
            when friendship.user_low = p_actor then friendship.user_high
            else friendship.user_low
          end as other_id,
          friendship.created_at
        from public.friendships as friendship
        where p_actor in (friendship.user_low, friendship.user_high)
          and friendship.created_at >= p_week_start
      ) as fresh
      join public.conversations as conversation
        on conversation.kind = 'direct'
        and conversation.direct_user_low = least(p_actor, fresh.other_id)
        and conversation.direct_user_high = greatest(p_actor, fresh.other_id)
      join public.messages as message
        on message.conversation_id = conversation.id
      where message.sender_id = p_actor
        and message.created_at >= fresh.created_at
    );
  elsif p_kind = 'message_distinct_friends' then
    return (
      select count(distinct case
        when conversation.direct_user_low = p_actor then conversation.direct_user_high
        else conversation.direct_user_low
      end)::integer
      from public.messages as message
      join public.conversations as conversation
        on conversation.id = message.conversation_id
        and conversation.kind = 'direct'
      join public.friendships as friendship
        on friendship.user_low = conversation.direct_user_low
        and friendship.user_high = conversation.direct_user_high
      where message.sender_id = p_actor
        and message.created_at >= p_week_start
        and p_actor in (conversation.direct_user_low, conversation.direct_user_high)
    );
  elsif p_kind = 'trophies_total' then
    return (
      select count(distinct case
        when encounter.user_low = p_actor then encounter.user_high
        else encounter.user_low
      end)::integer
      from public.nearby_encounters as encounter
      where p_actor in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
    );
  elsif p_kind = 'encounters_total' then
    return (
      select count(*)::integer
      from public.nearby_encounters as encounter
      where p_actor in (encounter.user_low, encounter.user_high)
        and encounter.confirmed_at is not null
    );
  elsif p_kind = 'tokens_total' then
    return coalesce((
      select token_balance.balance
      from public.token_balances as token_balance
      where token_balance.user_id = p_actor
    ), 0);
  elsif p_kind = 'discover_region' then
    return (
      select count(*)::integer
      from (
        select peer.country_code, min(encounter.occurred_at) as first_met
        from public.nearby_encounters as encounter
        join public.profiles as peer
          on peer.user_id = case
            when encounter.user_low = p_actor then encounter.user_high
            else encounter.user_low
          end
        where p_actor in (encounter.user_low, encounter.user_high)
          and encounter.confirmed_at is not null
          and peer.country_code is not null
        group by peer.country_code
      ) as region
      where region.first_met >= p_week_start
    );
  elsif p_kind = 'mii_makeover' then
    return (
      select count(*)::integer
      from public.profile_miis as mii
      where mii.user_id = p_actor
        and mii.updated_at >= p_week_start
    );
  end if;

  return 0;
end;
$$;

revoke all on function private.bingo_goal_progress(uuid, text, integer, text, timestamptz) from public;

drop function public.get_bingo_board();

create or replace function public.get_bingo_card()
returns table (
  cell_position integer,
  slug text,
  goal_text text,
  short_label text,
  completed boolean,
  progress_current integer,
  progress_target integer
)
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_week_key text;
  v_week_start timestamptz;
  v_cell record;
  v_position integer;
  v_current integer;
  v_done boolean;
  v_grid boolean[];
  v_lines integer := 0;
  v_blackout boolean := true;
  v_lines_paid integer;
  v_blackout_paid boolean;
  v_payout integer := 0;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  v_week_key := to_char(now() at time zone 'utc', 'IYYY-IW');
  v_week_start := (date_trunc('week', now() at time zone 'utc')) at time zone 'utc';

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('bingo:' || v_actor_id::text || ':' || v_week_key, 0)
  );

  v_grid := array_fill(false, array[25]);
  v_grid[13] := true;

  for v_cell in
    select
      goal.slug as goal_slug,
      goal.goal_text as goal_body,
      goal.short_label as goal_label,
      goal.kind as goal_kind,
      goal.target as goal_target,
      goal.country_param as goal_country,
      (row_number() over (
        order by pg_catalog.hashtextextended(
          goal.slug || ':' || v_actor_id::text || ':' || v_week_key,
          0
        )
      ))::integer as goal_rank
    from public.bingo_goals as goal
    where goal.is_active
    order by pg_catalog.hashtextextended(
      goal.slug || ':' || v_actor_id::text || ':' || v_week_key,
      0
    )
    limit 24
  loop
    v_position := case
      when v_cell.goal_rank <= 12 then v_cell.goal_rank - 1
      else v_cell.goal_rank
    end;

    v_done := exists (
      select 1
      from public.bingo_stamps as stamp
      where stamp.user_id = v_actor_id
        and stamp.week_key = v_week_key
        and stamp.slug = v_cell.goal_slug
    );
    if v_done then
      v_current := v_cell.goal_target;
    else
      v_current := private.bingo_goal_progress(
        v_actor_id,
        v_cell.goal_kind,
        v_cell.goal_target,
        v_cell.goal_country,
        v_week_start
      );
      if v_current >= v_cell.goal_target then
        insert into public.bingo_stamps (user_id, week_key, slug)
        values (v_actor_id, v_week_key, v_cell.goal_slug)
        on conflict on constraint bingo_stamps_pkey do nothing;
        v_done := true;
      end if;
    end if;

    v_grid[v_position + 1] := v_done;

    cell_position := v_position;
    slug := v_cell.goal_slug;
    goal_text := v_cell.goal_body;
    short_label := v_cell.goal_label;
    completed := v_done;
    progress_current := least(v_current, v_cell.goal_target);
    progress_target := v_cell.goal_target;
    return next;
  end loop;

  for v_index in 0..4 loop
    if v_grid[v_index * 5 + 1] and v_grid[v_index * 5 + 2]
      and v_grid[v_index * 5 + 3] and v_grid[v_index * 5 + 4]
      and v_grid[v_index * 5 + 5]
    then
      v_lines := v_lines + 1;
    end if;
    if v_grid[v_index + 1] and v_grid[v_index + 6] and v_grid[v_index + 11]
      and v_grid[v_index + 16] and v_grid[v_index + 21]
    then
      v_lines := v_lines + 1;
    end if;
  end loop;
  if v_grid[1] and v_grid[7] and v_grid[13] and v_grid[19] and v_grid[25] then
    v_lines := v_lines + 1;
  end if;
  if v_grid[5] and v_grid[9] and v_grid[13] and v_grid[17] and v_grid[21] then
    v_lines := v_lines + 1;
  end if;

  for v_index in 1..25 loop
    if not v_grid[v_index] then
      v_blackout := false;
    end if;
  end loop;

  insert into public.bingo_awards (user_id, week_key)
  values (v_actor_id, v_week_key)
  on conflict on constraint bingo_awards_pkey do nothing;

  select award.lines_paid, award.blackout_paid
  into v_lines_paid, v_blackout_paid
  from public.bingo_awards as award
  where award.user_id = v_actor_id
    and award.week_key = v_week_key;

  if v_lines > v_lines_paid then
    v_payout := (v_lines - v_lines_paid) * 25;
  end if;
  if v_blackout and not v_blackout_paid then
    v_payout := v_payout + 150;
  end if;

  if v_payout > 0 then
    perform private.ensure_token_balance(v_actor_id);

    update public.token_balances as token_balance
    set balance = token_balance.balance + v_payout,
        updated_at = now()
    where token_balance.user_id = v_actor_id;

    insert into public.notifications (recipient_id, kind, title, body)
    values (
      v_actor_id,
      'bingo_award',
      'Bingo!',
      '+' || v_payout::text || ' tokens earned'
    );

    update public.bingo_awards as award
    set lines_paid = greatest(award.lines_paid, v_lines),
        blackout_paid = award.blackout_paid or v_blackout,
        updated_at = now()
    where award.user_id = v_actor_id
      and award.week_key = v_week_key;
  end if;
end;
$$;

revoke all on function public.get_bingo_card() from public, anon;
grant execute on function public.get_bingo_card() to authenticated;

comment on function public.get_bingo_card() is
  'The caller''s weekly bingo card with live completion. 24 goals fill positions 0-24 skipping 12, which the client renders as the pre-stamped FREE space. Activity goals count within the card''s ISO week, milestone goals check lifetime totals, and a satisfied cell is stamped permanently for the week in bingo_stamps. Each call recounts completed lines under an advisory lock and pays 25 tokens per new line plus a one-time 150 blackout bonus into token_balances, recording payouts in bingo_awards and announcing them through a bingo_award notification.';

create or replace function private.ensure_token_balance(p_user_id uuid)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_balance integer;
begin
  insert into public.token_balances (user_id, balance)
  values (p_user_id, 0)
  on conflict (user_id) do nothing;

  select token_balance.balance
  into v_balance
  from public.token_balances as token_balance
  where token_balance.user_id = p_user_id;

  return v_balance;
end;
$$;

update public.token_balances set balance = 0, updated_at = now()
where balance = 22;

commit;
