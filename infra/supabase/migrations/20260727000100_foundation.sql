begin;

create schema if not exists private;
revoke all on schema private from public;

create extension if not exists pgcrypto with schema extensions;
create extension if not exists citext with schema extensions;
create extension if not exists pgtap with schema extensions;

create type public.friend_request_status as enum (
  'pending',
  'accepted',
  'rejected',
  'cancelled'
);

create type public.conversation_kind as enum (
  'direct',
  'group'
);

create type public.conversation_member_role as enum (
  'owner',
  'member'
);

create type public.interaction_event_type as enum (
  'nearby_encounter',
  'profile_view',
  'friend_code_scan'
);

create table public.profiles (
  user_id uuid primary key references auth.users (id) on delete cascade,
  username extensions.citext not null unique,
  display_name text not null,
  bio text not null default '',
  avatar_path text,
  age smallint,
  country_code text,
  last_seen_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint profiles_username_format check (
    username::text = lower(username::text)
    and username::text ~ '^[a-z0-9][a-z0-9_.-]{2,31}$'
  ),
  constraint profiles_display_name_length check (
    char_length(btrim(display_name)) between 1 and 48
  ),
  constraint profiles_bio_length check (char_length(bio) <= 280),
  constraint profiles_avatar_path_owned check (
    avatar_path is null
    or (
      avatar_path like user_id::text || '/%'
      and avatar_path ~ '^[0-9a-f-]{36}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
    )
  ),
  constraint profiles_age_public_range check (
    age is null or age between 13 and 120
  ),
  constraint profiles_country_code_iso_shape check (
    country_code is null or country_code ~ '^[A-Z]{2}$'
  )
);

create table private.rpc_operations (
  actor_id uuid not null references public.profiles (user_id) on delete cascade,
  client_operation_id uuid not null,
  operation_name text not null,
  request_payload jsonb not null,
  response_payload jsonb not null,
  created_at timestamptz not null default now(),
  primary key (actor_id, client_operation_id),
  constraint rpc_operations_name_length check (
    char_length(operation_name) between 1 and 80
  ),
  constraint rpc_operations_payloads_are_objects check (
    jsonb_typeof(request_payload) = 'object'
    and jsonb_typeof(response_payload) = 'object'
  )
);

revoke all on table private.rpc_operations from public;

create table public.friend_requests (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles (user_id) on delete cascade,
  addressee_id uuid not null references public.profiles (user_id) on delete cascade,
  status public.friend_request_status not null default 'pending',
  client_operation_id uuid not null,
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint friend_requests_not_self check (requester_id <> addressee_id),
  constraint friend_requests_response_time check (
    (status = 'pending' and responded_at is null)
    or (status <> 'pending' and responded_at is not null)
  ),
  constraint friend_requests_requester_operation_unique
    unique (requester_id, client_operation_id)
);

create unique index friend_requests_one_pending_pair_idx
  on public.friend_requests (
    least(requester_id, addressee_id),
    greatest(requester_id, addressee_id)
  )
  where status = 'pending';

create index friend_requests_addressee_status_idx
  on public.friend_requests (addressee_id, status, created_at desc);

create index friend_requests_requester_status_idx
  on public.friend_requests (requester_id, status, created_at desc);

create table public.friendships (
  user_low uuid not null references public.profiles (user_id) on delete cascade,
  user_high uuid not null references public.profiles (user_id) on delete cascade,
  created_by uuid not null references public.profiles (user_id) on delete restrict,
  created_at timestamptz not null default now(),
  primary key (user_low, user_high),
  constraint friendships_canonical_pair check (user_low < user_high),
  constraint friendships_creator_is_member check (
    created_by = user_low or created_by = user_high
  )
);

create index friendships_user_high_idx
  on public.friendships (user_high, created_at desc);

create table public.user_blocks (
  blocker_id uuid not null references public.profiles (user_id) on delete cascade,
  blocked_id uuid not null references public.profiles (user_id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  constraint user_blocks_not_self check (blocker_id <> blocked_id)
);

create index user_blocks_blocked_idx
  on public.user_blocks (blocked_id);

create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  kind public.conversation_kind not null,
  created_by uuid not null references public.profiles (user_id) on delete restrict,
  title text,
  direct_user_low uuid references public.profiles (user_id) on delete restrict,
  direct_user_high uuid references public.profiles (user_id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint conversations_shape check (
    (
      kind = 'direct'
      and title is null
      and direct_user_low is not null
      and direct_user_high is not null
      and direct_user_low < direct_user_high
      and (created_by = direct_user_low or created_by = direct_user_high)
    )
    or (
      kind = 'group'
      and direct_user_low is null
      and direct_user_high is null
      and char_length(btrim(title)) between 1 and 80
    )
  )
);

create unique index conversations_direct_pair_idx
  on public.conversations (direct_user_low, direct_user_high)
  where kind = 'direct';

create index conversations_updated_idx
  on public.conversations (updated_at desc);

create table public.conversation_members (
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  user_id uuid not null references public.profiles (user_id) on delete cascade,
  role public.conversation_member_role not null default 'member',
  joined_at timestamptz not null default now(),
  left_at timestamptz,
  last_read_at timestamptz,
  primary key (conversation_id, user_id),
  constraint conversation_members_left_after_joined check (
    left_at is null or left_at >= joined_at
  )
);

create index conversation_members_active_user_idx
  on public.conversation_members (user_id, conversation_id)
  where left_at is null;

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  sender_id uuid not null references public.profiles (user_id) on delete restrict,
  client_operation_id uuid not null,
  body text not null,
  reply_to_id uuid,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  edited_at timestamptz,
  deleted_at timestamptz,
  constraint messages_sender_operation_unique
    unique (sender_id, client_operation_id),
  constraint messages_conversation_id_id_unique
    unique (conversation_id, id),
  constraint messages_body_length check (
    char_length(btrim(body)) between 1 and 4000
  ),
  constraint messages_metadata_object check (
    jsonb_typeof(metadata) = 'object'
    and octet_length(metadata::text) <= 8192
  ),
  constraint messages_reply_same_conversation
    foreign key (conversation_id, reply_to_id)
    references public.messages (conversation_id, id)
    on delete set null (reply_to_id),
  constraint messages_edited_after_created check (
    edited_at is null or edited_at >= created_at
  ),
  constraint messages_deleted_after_created check (
    deleted_at is null or deleted_at >= created_at
  )
);

create index messages_conversation_created_idx
  on public.messages (conversation_id, created_at desc, id desc);

create index messages_reply_idx
  on public.messages (reply_to_id)
  where reply_to_id is not null;

create table public.interaction_events (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid not null references public.profiles (user_id) on delete cascade,
  subject_user_id uuid references public.profiles (user_id) on delete cascade,
  event_type public.interaction_event_type not null,
  client_operation_id uuid not null,
  payload jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now(),
  constraint interaction_events_actor_operation_unique
    unique (actor_id, client_operation_id),
  constraint interaction_events_payload_object check (
    jsonb_typeof(payload) = 'object'
    and octet_length(payload::text) <= 8192
  )
);

create index interaction_events_actor_time_idx
  on public.interaction_events (actor_id, occurred_at desc);

create index interaction_events_subject_time_idx
  on public.interaction_events (subject_user_id, occurred_at desc)
  where subject_user_id is not null;

create or replace function private.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function private.set_updated_at();

create trigger conversations_set_updated_at
before update on public.conversations
for each row execute function private.set_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_display_name text;
begin
  v_display_name := left(
    coalesce(
      nullif(btrim(new.raw_user_meta_data ->> 'display_name'), ''),
      nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''),
      nullif(btrim(new.raw_user_meta_data ->> 'user_name'), ''),
      'PocketPass User'
    ),
    48
  );

  insert into public.profiles (user_id, username, display_name)
  values (
    new.id,
    replace(new.id::text, '-', '')::extensions.citext,
    v_display_name
  )
  on conflict (user_id) do nothing;

  return new;
end;
$$;

revoke all on function public.handle_new_user() from public;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

commit;
