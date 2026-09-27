begin;

update public.bingo_goals
set goal_text = 'Give your Piip a new look!'
where slug = 'mii_makeover';

create or replace function private.notify_lapsed_supporters()
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  with due as (
    select status.user_id
    from public.supporter_status as status
    where status.active_until < now()
      and status.lapsed_notified_at is null
    for update skip locked
  ),
  notified as (
    insert into public.notifications (recipient_id, kind, title, body)
    select
      due.user_id,
      'system',
      'Supporter perks ended',
      'Your supporter perks have ended. Hats you have not bought come off the next time you save your Piip.'
    from due
    returning recipient_id
  )
  update public.supporter_status as status
  set lapsed_notified_at = now()
  from notified
  where status.user_id = notified.recipient_id;
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

revoke all on function private.notify_lapsed_supporters() from public;

commit;
