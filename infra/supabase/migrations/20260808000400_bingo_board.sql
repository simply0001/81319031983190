begin;

create table public.bingo_goals (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  goal_text text not null,
  short_label text not null,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  constraint bingo_goals_slug_format check (slug ~ '^[a-z0-9_]{1,64}$'),
  constraint bingo_goals_text_length check (char_length(btrim(goal_text)) between 1 and 120),
  constraint bingo_goals_label_length check (char_length(btrim(short_label)) between 1 and 24)
);

revoke all on table public.bingo_goals from anon, authenticated;

insert into public.bingo_goals (slug, goal_text, short_label)
values
  ('meet_lebanon', 'Meet someone from Lebanon!', 'Lebanon'),
  ('meet_poland', 'Meet someone from Poland!', 'Poland'),
  ('own_hat', 'Own a hat item!', 'Hat Owner'),
  ('wave_5', 'Wave at 5 people!', '5 Waves'),
  ('meet_japan', 'Meet someone from Japan!', 'Japan'),
  ('send_10_messages', 'Send 10 messages!', '10 Msgs'),
  ('meet_3_one_day', 'Meet 3 people in one day!', '3 a Day'),
  ('meet_brazil', 'Meet someone from Brazil!', 'Brazil'),
  ('add_friend', 'Add a new friend!', 'New Friend'),
  ('meet_canada', 'Meet someone from Canada!', 'Canada'),
  ('buy_shop_item', 'Buy a shop item!', 'Go Shopping'),
  ('meet_at_night', 'Meet someone at night!', 'Night Owl'),
  ('meet_egypt', 'Meet someone from Egypt!', 'Egypt'),
  ('waves_back_5', 'Get 5 waves back!', '5 Waves Back'),
  ('meet_france', 'Meet someone from France!', 'France'),
  ('change_mood', 'Change your mood!', 'New Mood'),
  ('meet_india', 'Meet someone from India!', 'India'),
  ('meet_same_twice', 'Meet the same person twice!', 'Rematch'),
  ('meet_mexico', 'Meet someone from Mexico!', 'Mexico'),
  ('send_emoji', 'Send an emoji!', 'Emoji'),
  ('meet_italy', 'Meet someone from Italy!', 'Italy'),
  ('reach_10_trophies', 'Reach 10 trophies!', '10 Trophies'),
  ('meet_kenya', 'Meet someone from Kenya!', 'Kenya'),
  ('message_new_friend', 'Message a new friend!', 'Say Hi'),
  ('meet_spain', 'Meet someone from Spain!', 'Spain'),
  ('meet_germany', 'Meet someone from Germany!', 'Germany'),
  ('meet_sweden', 'Meet someone from Sweden!', 'Sweden'),
  ('meet_norway', 'Meet someone from Norway!', 'Norway'),
  ('meet_australia', 'Meet someone from Australia!', 'Australia'),
  ('meet_south_korea', 'Meet someone from South Korea!', 'Korea'),
  ('meet_china', 'Meet someone from China!', 'China'),
  ('meet_argentina', 'Meet someone from Argentina!', 'Argentina'),
  ('meet_nigeria', 'Meet someone from Nigeria!', 'Nigeria'),
  ('meet_greece', 'Meet someone from Greece!', 'Greece'),
  ('meet_turkey', 'Meet someone from Turkey!', 'Turkey'),
  ('meet_portugal', 'Meet someone from Portugal!', 'Portugal'),
  ('meet_netherlands', 'Meet someone from the Netherlands!', 'Netherlands'),
  ('meet_ireland', 'Meet someone from Ireland!', 'Ireland'),
  ('meet_iceland', 'Meet someone from Iceland!', 'Iceland'),
  ('meet_thailand', 'Meet someone from Thailand!', 'Thailand'),
  ('meet_5_one_week', 'Meet 5 people in one week!', '5 a Week'),
  ('meet_morning', 'Meet someone in the morning!', 'Early Bird'),
  ('meet_2_one_hour', 'Meet 2 people in one hour!', 'Busy Hour'),
  ('send_25_messages', 'Send 25 messages!', '25 Msgs'),
  ('send_photo', 'Send a photo!', 'Photo Drop'),
  ('chat_streak_3', 'Hold a 3-day chat streak!', '3-Day Streak'),
  ('quick_reply', 'Reply within a minute!', 'Quick Reply'),
  ('wave_10', 'Wave at 10 people!', '10 Waves'),
  ('waves_back_10', 'Get 10 waves back!', '10 Waves Back'),
  ('add_3_friends', 'Add 3 new friends!', 'Trio'),
  ('message_3_friends', 'Message 3 different friends!', 'Social Bee'),
  ('reach_25_trophies', 'Reach 25 trophies!', '25 Trophies'),
  ('reach_50_encounters', 'Reach 50 encounters!', '50 Meets'),
  ('own_3_items', 'Own 3 shop items!', 'Collector'),
  ('save_100_tokens', 'Save up 100 tokens!', 'Saver'),
  ('meet_other_continent', 'Meet someone from another continent!', 'Far Away'),
  ('meet_weekend', 'Meet someone new on a weekend!', 'Weekender'),
  ('update_bio', 'Update your bio!', 'New Bio'),
  ('mii_makeover', 'Give your Mii a new look!', 'Makeover'),
  ('discover_region', 'Discover a new region!', 'Explorer');

create or replace function public.get_bingo_board()
returns table (
  cell_position integer,
  goal_text text,
  short_label text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_week_key text;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  v_week_key := to_char(now() at time zone 'UTC', 'IYYY-IW');

  return query
  select
    (row_number() over (
      order by pg_catalog.hashtextextended(
        goal.slug || ':' || v_actor_id::text || ':' || v_week_key,
        0
      )
    ))::integer - 1,
    goal.goal_text,
    goal.short_label
  from public.bingo_goals as goal
  where goal.is_active
  order by pg_catalog.hashtextextended(
    goal.slug || ':' || v_actor_id::text || ':' || v_week_key,
    0
  )
  limit 25;
end;
$$;

revoke all on function public.get_bingo_board() from public, anon;
grant execute on function public.get_bingo_board() to authenticated;

comment on function public.get_bingo_board() is
  'The caller''s bingo card for the current ISO week: 25 active goals chosen and ordered by a deterministic hash of goal slug, caller id, and the server''s week key. The board is stable all week, differs per player, and rolls over automatically at the ISO week boundary on the server clock, so client clock changes cannot reroll it.';

commit;
