begin;

-- Account deletion must not be blocked by owning a community. Archive it for
-- its existing audience until central staff appoint a replacement owner.
alter table private.boards alter column owner_id drop not null;
alter table private.boards drop constraint boards_owner_id_fkey;
alter table private.boards add constraint boards_owner_id_fkey
  foreign key(owner_id) references public.profiles(user_id) on delete set null;

create function private.board_owner_account_deleted() returns trigger
language plpgsql security definer set search_path='' as $$
declare bid uuid;
begin
  for bid in select id from private.boards where owner_id=old.user_id for update loop
    update private.boards set owner_id=null,archived=true,transfer_to=null,
      access_revision=access_revision+1,updated_at=now() where id=bid;
    update private.board_invitations set revoked=true where board_id=bid;
    update private.board_events set push_pending=false where board_id=bid;
    insert into private.board_audit(board_id,action,reason,central)
      values(bid,'owner_account_deleted','Board archived after its owner deleted their account.',true);
    perform private.board_invalidate(bid);
  end loop;
  return old;
end $$;
revoke all on function private.board_owner_account_deleted() from public,anon,authenticated;
create trigger board_owner_account_deleted before delete on public.profiles
for each row execute function private.board_owner_account_deleted();

create or replace function private.board_json(p_board uuid) returns jsonb
language sql stable security definer set search_path='' as $$
  select (to_jsonb(b)-'transfer_to')||jsonb_build_object(
    'owner_id',coalesce(b.owner_id::text,''),'owner_deleted',b.owner_id is null,
    'role',private.board_role(b.id,auth.uid()),
    'member_count',(select count(*) from private.board_members where board_id=b.id),
    'muted',coalesce(m.muted,false),'push_enabled',coalesce(m.push_enabled,true),
    'join_requested',exists(select 1 from private.board_join_requests where board_id=b.id and user_id=auth.uid()),
    'transfer_pending',b.transfer_to=auth.uid())
  from private.boards b left join private.board_members m on m.board_id=b.id and m.user_id=auth.uid() where b.id=p_board;
$$;

notify pgrst, 'reload schema';
commit;
