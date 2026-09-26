begin;

alter table public.notifications
  drop constraint notifications_actor_id_fkey,
  add constraint notifications_actor_id_fkey
    foreign key (actor_id) references public.profiles (user_id) on delete cascade;

alter table public.notifications
  drop constraint notifications_friend_request_id_fkey,
  add constraint notifications_friend_request_id_fkey
    foreign key (friend_request_id) references public.friend_requests (id) on delete cascade;

alter table private.nearby_receipts
  drop constraint nearby_receipts_own_token_fkey,
  add constraint nearby_receipts_own_token_fkey
    foreign key (own_token) references private.nearby_credentials (token) on delete cascade;

alter table private.nearby_receipts
  drop constraint nearby_receipts_peer_token_fkey,
  add constraint nearby_receipts_peer_token_fkey
    foreign key (peer_token) references private.nearby_credentials (token) on delete cascade;

commit;
