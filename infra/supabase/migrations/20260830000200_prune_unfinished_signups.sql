begin;

create or replace function private.prune_unfinished_signups()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_deleted integer := 0;
begin
  with abandoned as (
    select profile.user_id
    from public.profiles as profile
    where profile.username::text = replace(profile.user_id::text, '-', '')
      and profile.created_at < now() - interval '30 days'
      and not exists (
        select 1
        from public.messages as message
        where message.sender_id = profile.user_id
      )
      and not exists (
        select 1
        from public.conversations as conversation
        where profile.user_id in (
          conversation.created_by,
          conversation.direct_user_low,
          conversation.direct_user_high
        )
      )
      and not exists (
        select 1
        from public.friendships as friendship
        where profile.user_id in (
          friendship.created_by,
          friendship.user_low,
          friendship.user_high
        )
      )
      and not exists (
        select 1
        from public.nearby_encounters as encounter
        where profile.user_id in (
          encounter.user_low,
          encounter.user_high,
          encounter.reported_by
        )
      )
      and not exists (
        select 1
        from storage.objects as object
        where object.bucket_id in ('avatars', 'message-media')
          and object.name like profile.user_id::text || '/%'
      )
  ),
  removed as (
    delete from auth.users as account
    where account.id in (select abandoned.user_id from abandoned)
    returning account.id
  )
  select count(*) into v_deleted from removed;

  if v_deleted > 0 then
    raise log 'PocketPass: removed % account(s) that never finished setup', v_deleted;
  end if;

  return v_deleted;
end;
$$;

revoke all on function private.prune_unfinished_signups() from public;

comment on function private.prune_unfinished_signups() is
  'Deletes accounts that still carry the placeholder username handle_new_user() seeds, thirty days after signup, so abandoned signups do not linger as "PocketPass User". Only removes accounts with no messages, conversations, friendships, encounters or stored objects: those foreign keys are either on delete restrict, or would take another player''s history down with them, and storage.protect_delete stops SQL from tidying the bucket. Anything unfinished but not empty is left alone for a human to look at. Returns the number of accounts removed.';

do $schedule$
begin
  if exists (
    select 1 from pg_available_extensions where name = 'pg_cron'
  ) then
    create extension if not exists pg_cron;
    if not exists (
      select 1 from cron.job where jobname = 'pocketpass-prune-unfinished-signups'
    ) then
      perform cron.schedule(
        'pocketpass-prune-unfinished-signups',
        '53 4 * * *',
        'select private.prune_unfinished_signups();'
      );
    end if;
  end if;
end;
$schedule$;

commit;
