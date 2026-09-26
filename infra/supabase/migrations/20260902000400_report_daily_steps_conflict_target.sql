begin;

-- The first version named the conflict columns, which PL/pgSQL resolves
-- against the function's own output columns ("local_day" is ambiguous).
-- Naming the constraint instead leaves nothing to substitute.
create or replace function public.report_daily_steps(
  p_local_day date,
  p_steps integer,
  p_utc_offset_minutes integer
)
returns table (
  local_day date,
  steps integer,
  tokens_awarded integer,
  tokens_credited integer,
  balance integer
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

  -- The client names its own calendar day; only today and yesterday (by the
  -- offset it supplied) are accepted, so a wrong clock can shift when a day
  -- is claimed but never claim a day twice.
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
  select p_local_day, v_row.steps, v_target, v_credit, v_balance;
end;
$$;

comment on function public.report_daily_steps(date, integer, integer) is 'Records the caller''s step count for a local day (today or yesterday by the supplied UTC offset), keeps the highest count reported, and pays 1 token per 400 steps up to 25 per day; the once-per-day notification fires when the cap is reached. Returns the day''s totals and the new balance.';

commit;
