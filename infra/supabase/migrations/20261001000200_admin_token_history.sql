begin;

create function public.admin_get_user_token_history(p_user_id uuid, p_limit integer default 200)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 200), 1), 1000);
  v_result jsonb;
begin
  perform private.require_permission('tokens');
  if p_user_id is null then
    raise exception 'Choose an account' using errcode = '22023';
  end if;

  with entries as (
    select step_day.updated_at as at, 'steps'::text as source, step_day.tokens_awarded as amount,
      jsonb_build_object('day', step_day.local_day, 'steps', step_day.steps) as detail
    from private.step_reward_days as step_day
    where step_day.user_id = p_user_id and step_day.tokens_awarded > 0
    union all
    select reward.created_at, 'encounter', reward.amount,
      jsonb_build_object('day', reward.rewarded_on, 'partner_id', partner.user_id, 'partner_username', partner.username,
        'partner_name', partner.display_name)
    from private.encounter_token_rewards as reward
    left join public.profiles as partner
      on partner.user_id = case when reward.user_low = p_user_id then reward.user_high else reward.user_low end
    where p_user_id in (reward.user_low, reward.user_high)
    union all
    select award.updated_at, 'bingo', award.lines_paid * 25 + case when award.blackout_paid then 150 else 0 end,
      jsonb_build_object('week', award.week_key, 'lines', award.lines_paid, 'blackout', award.blackout_paid)
    from public.bingo_awards as award
    where award.user_id = p_user_id and (award.lines_paid > 0 or award.blackout_paid)
    union all
    select audit.created_at, 'staff',
      coalesce((audit.payload ->> 'delta')::integer,
        (audit.payload ->> 'balance_after')::integer - (audit.payload ->> 'balance_before')::integer, 0),
      jsonb_build_object('reason', audit.payload ->> 'reason', 'admin_id', audit.admin_id,
        'admin_username', admin_profile.username, 'admin_name', admin_profile.display_name)
    from private.admin_audit as audit
    left join public.profiles as admin_profile on admin_profile.user_id = audit.admin_id
    where audit.target_user_id = p_user_id and audit.action = 'adjust_tokens'
    union all
    select owned.purchased_at, 'shop', -owned.price_paid, jsonb_build_object('item', item.name)
    from public.user_shop_items as owned
    left join public.shop_items as item on item.id = owned.item_id
    where owned.user_id = p_user_id and owned.price_paid > 0
    union all
    select paper.purchased_at, 'stationery', -paper.price_paid, jsonb_build_object('item', stationery.name)
    from private.board_stationery_owned as paper
    left join private.board_stationery as stationery on stationery.id = paper.stationery_id
    where paper.user_id = p_user_id and paper.price_paid > 0
    union all
    select piece.acquired_at, 'puzzle', -private.puzzle_piece_price(),
      jsonb_build_object('puzzle', piece.puzzle_key, 'piece', piece.piece_index)
    from public.puzzle_pieces as piece
    where piece.user_id = p_user_id and piece.source = 'purchase'
  )
  select jsonb_build_object(
    'balance', coalesce((select balance.balance from public.token_balances as balance where balance.user_id = p_user_id), 0),
    'totals', coalesce((select jsonb_object_agg(total.source, total.amount)
      from (select entry.source, sum(entry.amount) as amount from entries as entry group by entry.source) as total), '{}'::jsonb),
    'entry_count', (select count(*) from entries),
    'entries', coalesce((select jsonb_agg(jsonb_build_object('at', latest.at, 'source', latest.source,
        'amount', latest.amount, 'detail', latest.detail) order by latest.at desc, latest.source)
      from (select * from entries order by entries.at desc, entries.source limit v_limit) as latest), '[]'::jsonb)
  )
  into v_result;

  return v_result;
end;
$$;

revoke all on function public.admin_get_user_token_history(uuid, integer) from public, anon;
grant execute on function public.admin_get_user_token_history(uuid, integer) to authenticated;

commit;
