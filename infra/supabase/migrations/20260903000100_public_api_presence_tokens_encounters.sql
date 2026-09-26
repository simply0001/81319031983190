begin;

-- Connected apps may now follow the tokens: and encounters: Realtime topics,
-- read and track Presence, and read the token balance and the encounter list,
-- each behind a new scope. The app_updates topic and sending Broadcast stay
-- first-party.

create or replace function private.api_scope_keys()
returns text[]
language sql
immutable
set search_path = ''
as $$
  select array[
    'profile:read',
    'friends:read',
    'friends:write',
    'messages:read',
    'messages:write',
    'groups:write',
    'notifications:read',
    'presence:read',
    'presence:write',
    'tokens:read',
    'encounters:read'
  ]::text[];
$$;

create or replace function private.api_scope_descriptions()
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'profile:read', 'See your profile (name, bio, avatar, age, country)',
    'friends:read', 'See your friends list and friend requests',
    'friends:write', 'Add and remove friends and answer friend requests as you',
    'messages:read', 'Read your conversations and messages',
    'messages:write', 'Send, edit and delete messages as you',
    'groups:write', 'Create group chats and manage their members as you',
    'notifications:read', 'See and clear your notifications',
    'presence:read', 'See which of your friends are online and who is active in your chats',
    'presence:write', 'Show you as online and typing to your friends',
    'tokens:read', 'See your token balance and supporter status',
    'encounters:read', 'See the people you have met nearby'
  );
$$;

-- Broadcast: the tokens: and encounters: topics open behind their scopes.

create or replace function private.api_can_access_realtime_topic(p_topic text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    auth.uid() is not null
    and (
      (
        private.conversation_id_from_topic(p_topic) is not null
        and private.api_has_scope('messages:read')
        and private.is_active_conversation_member(
          private.conversation_id_from_topic(p_topic),
          auth.uid()
        )
      )
      or (
        private.notification_user_id_from_topic(p_topic) = auth.uid()
        and private.api_has_scope('notifications:read')
      )
      or (
        private.friend_user_id_from_topic(p_topic) = auth.uid()
        and private.api_has_scope('friends:read')
      )
      or (
        private.token_user_id_from_topic(p_topic) = auth.uid()
        and private.api_has_scope('tokens:read')
      )
      or (
        private.encounter_user_id_from_topic(p_topic) = auth.uid()
        and private.api_has_scope('encounters:read')
      )
    ),
    false
  );
$$;

-- Presence: the same membership rules as the first-party app, plus the scope
-- that already reveals the relationship (friends:read for a friend pair,
-- messages:read for a conversation), so a presence-only app cannot probe
-- friendships or conversations by whether a join succeeds.

create or replace function private.api_presence_topic_member(p_topic text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    auth.uid() is not null
    and (
      (
        private.can_access_friend_presence_topic(p_topic, auth.uid())
        and private.api_has_scope('friends:read')
      )
      or (
        private.conversation_id_from_topic(p_topic) is not null
        and private.api_has_scope('messages:read')
        and private.is_active_conversation_member(
          private.conversation_id_from_topic(p_topic),
          auth.uid()
        )
      )
    ),
    false
  );
$$;

revoke all on function private.api_presence_topic_member(text) from public;

create or replace function private.api_can_read_presence_topic(p_topic text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    (private.api_has_scope('presence:read') or private.api_has_scope('presence:write'))
    and private.api_presence_topic_member(p_topic);
$$;

create or replace function private.api_can_track_presence_topic(p_topic text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    private.api_has_scope('presence:write')
    and private.api_presence_topic_member(p_topic);
$$;

revoke all on function private.api_can_read_presence_topic(text) from public;
revoke all on function private.api_can_track_presence_topic(text) from public;
grant execute on function private.api_can_read_presence_topic(text) to api_client;
grant execute on function private.api_can_track_presence_topic(text) to api_client;

comment on function private.api_can_read_presence_topic(text) is
  'Connected apps with presence:read (or presence:write) may see Presence on the friend-presence pairs and conversations they can already read.';
comment on function private.api_can_track_presence_topic(text) is
  'Connected apps with presence:write may track Presence (appear online or typing) on the same topics they may read it on.';

drop policy if exists pocketpass_api_realtime_read on realtime.messages;

create policy pocketpass_api_realtime_read
on realtime.messages
for select
to api_client
using (
  (
    extension = 'broadcast'
    and private.api_can_access_realtime_topic(realtime.topic())
  )
  or (
    extension = 'presence'
    and private.api_can_read_presence_topic(realtime.topic())
  )
);

grant insert on table realtime.messages to api_client;

create policy pocketpass_api_presence_track
on realtime.messages
for insert
to api_client
with check (
  extension = 'presence'
  and private.api_can_track_presence_topic(realtime.topic())
);

comment on policy pocketpass_api_realtime_read on realtime.messages is
  'Connected apps receive Broadcast on the scoped conversation:, notifications:, friends:, tokens: and encounters: topics and Presence on scoped conversation: and friend-presence: topics.';
comment on policy pocketpass_api_presence_track on realtime.messages is
  'Connected apps with presence:write may track Presence; there is no insert policy for Broadcast, so apps cannot send Broadcast messages.';

-- The encounters: topic now reaches connected apps, so it carries a curated
-- payload instead of the raw row (which named the reporter, the peer and the
-- client operation id). Event names stay INSERT/UPDATE; the first-party app
-- only uses the event as a refresh signal.

create or replace function private.broadcast_encounter_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_payload jsonb := jsonb_build_object(
    'encounter_id', new.id,
    'occurred_at', new.occurred_at,
    'confirmed_at', new.confirmed_at
  );
begin
  perform realtime.send(v_payload, tg_op, 'encounters:' || new.user_low::text, true);
  perform realtime.send(v_payload, tg_op, 'encounters:' || new.user_high::text, true);
  return new;
end;
$$;

-- tokens.get

create or replace function public.api_v1_tokens_get(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('tokens:read');
  v_user_id uuid;
  v_result jsonb;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, '{}'::text[]);

    -- Balance rows are created lazily; a missing row is a zero balance and
    -- must not be created here, because inserting one broadcasts.
    select jsonb_build_object(
      'tokens', jsonb_build_object(
        'balance', coalesce(balance.balance, 0),
        'updated_at', balance.updated_at
      ),
      'supporter', jsonb_build_object(
        'active', coalesce(supporter.active_until > now(), false),
        'active_until', supporter.active_until
      )
    )
    into v_result
    from (select v_user_id as user_id) as me
    left join public.token_balances as balance
      on balance.user_id = me.user_id
    left join public.supporter_status as supporter
      on supporter.user_id = me.user_id;

    return v_result;
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

-- encounters.list

create or replace function private.api_encounter_json(
  p_encounter public.nearby_encounters,
  p_peer public.profiles
)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'id', p_encounter.id,
    'peer', private.api_profile_json(p_peer),
    'occurred_at', p_encounter.occurred_at,
    'created_at', p_encounter.created_at,
    'confirmed_at', p_encounter.confirmed_at,
    'updated_at', greatest(
      p_encounter.created_at,
      coalesce(p_encounter.confirmed_at, p_encounter.created_at)
    )
  );
$$;

revoke all on function private.api_encounter_json(public.nearby_encounters, public.profiles) from public;

create or replace function public.api_v1_encounters_list(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('encounters:read');
  v_user_id uuid;
  v_limit integer;
  v_cursor text;
  v_cursor_ts timestamptz;
  v_cursor_id uuid;
  v_updated_after timestamptz;
  v_items jsonb;
  v_has_more boolean;
  v_last_ts timestamptz;
  v_last_id uuid;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown($1, array['limit', 'cursor', 'updated_after']);
    v_limit := private.api_arg_int($1, 'limit', 50, 1, 100);
    v_cursor := private.api_arg_text($1, 'cursor', false, null);
    v_updated_after := private.api_arg_timestamptz($1, 'updated_after');
    if v_cursor is not null then
      select decoded.ts, decoded.id
      into v_cursor_ts, v_cursor_id
      from private.api_decode_cursor(v_cursor) as decoded;
    end if;

    -- Newest created or confirmed first; the peer is hidden when a block
    -- exists in either direction, as in the first-party list.
    select
      coalesce(
        jsonb_agg(private.api_encounter_json(page.encounter_row, page.peer_row) order by page.rn)
          filter (where page.rn <= v_limit),
        '[]'::jsonb
      ),
      count(*) > v_limit,
      (array_agg(page.updated_at order by page.rn))[v_limit],
      (array_agg(page.id order by page.rn))[v_limit]
    into v_items, v_has_more, v_last_ts, v_last_id
    from (
      select
        scored.encounter_row,
        scored.peer_row,
        scored.id,
        scored.updated_at,
        row_number() over (order by scored.updated_at desc, scored.id desc) as rn
      from (
        select
          encounter as encounter_row,
          peer as peer_row,
          encounter.id,
          greatest(
            encounter.created_at,
            coalesce(encounter.confirmed_at, encounter.created_at)
          ) as updated_at
        from public.nearby_encounters as encounter
        join public.profiles as peer
          on peer.user_id = case
            when encounter.user_low = v_user_id then encounter.user_high
            else encounter.user_low
          end
        where v_user_id in (encounter.user_low, encounter.user_high)
          and not private.has_block_between(v_user_id, peer.user_id)
      ) as scored
      where (
          v_cursor_ts is null
          or (scored.updated_at, scored.id) < (v_cursor_ts, v_cursor_id)
        )
        and (v_updated_after is null or scored.updated_at > v_updated_after)
      order by scored.updated_at desc, scored.id desc
      limit v_limit + 1
    ) as page;

    return jsonb_build_object(
      'items', v_items,
      'next_cursor', case
        when v_has_more then private.api_encode_cursor(v_last_ts, v_last_id)
        else null
      end
    );
  exception
    when sqlstate '40001' or sqlstate '40P01' then
      raise;
    when others then
      get stacked diagnostics
        v_state = returned_sqlstate,
        v_message = message_text,
        v_hint = pg_exception_hint;
      return private.api_failure(v_state, v_message, v_hint);
  end;
end;
$$;

revoke all on function public.api_v1_tokens_get(jsonb) from public, anon, authenticated, service_role;
revoke all on function public.api_v1_encounters_list(jsonb) from public, anon, authenticated, service_role;
grant execute on function public.api_v1_tokens_get(jsonb) to api_client;
grant execute on function public.api_v1_encounters_list(jsonb) to api_client;

comment on function public.api_v1_tokens_get(jsonb) is
  'Public API: tokens.get — the connected user''s token balance and supporter status (tokens:read).';
comment on function public.api_v1_encounters_list(jsonb) is
  'Public API: encounters.list — the connected user''s nearby encounters with the peer profile, newest created or confirmed first (encounters:read).';

commit;
