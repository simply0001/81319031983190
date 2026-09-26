begin;

-- Scope-checked enrichment; never reveal a timestamp merely because a profile is visible.
create or replace function private.api_profile_json(p_profile public.profiles)
returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object(
    'user_id', p_profile.user_id,
    'username', p_profile.username::text,
    'display_name', p_profile.display_name,
    'bio', p_profile.bio,
    'avatar_path', p_profile.avatar_path,
    'age', p_profile.age,
    'country_code', p_profile.country_code,
    'created_at', p_profile.created_at,
    'updated_at', p_profile.updated_at,
    'setup_complete', p_profile.username::text <> replace(p_profile.user_id::text, '-', '')
  ) || case when private.api_has_scope('presence:read') and (
    p_profile.user_id = auth.uid()
    or (
      private.api_has_scope('friends:read')
      and private.are_friends(auth.uid(), p_profile.user_id)
      and not private.has_block_between(auth.uid(), p_profile.user_id)
    )
  ) then jsonb_build_object('last_seen_at', p_profile.last_seen_at)
    else '{}'::jsonb end;
$$;

revoke all on function private.api_profile_json(public.profiles) from public;

create or replace function private.api_scope_descriptions() returns jsonb
language sql immutable set search_path='' as $$
  select '{"profile:read":"See your profile (name, bio, avatar, age, country)","friends:read":"See your friends list and friend requests","friends:write":"Add and remove friends and answer friend requests as you","messages:read":"Read your conversations and messages","messages:write":"Send, edit and delete messages as you","groups:write":"Create group chats and manage their members as you","notifications:read":"See and clear your notifications","presence:read":"See when your friends were last online, who is online now and who is active in your chats","presence:write":"Show you as online and typing to your friends","tokens:read":"See your token balance and supporter status","encounters:read":"See the people you have met nearby","puzzles:read":"See your Puzzle Swap progress","boards:read":"Read public boards and your private boards, notes, drawings, members, invitations and activity","boards:write":"Publish, edit and remove your notes and replies, react, report content and submit appeals as you","boards:membership":"Join and leave boards, accept invitations and ownership offers, request boards and change board notifications","boards:invite":"Invite people to your boards and create or revoke invitation codes as you","boards:manage":"Change your boards, artwork, moderators and ownership, and archive or reopen them","boards:moderate":"Review your boards'' reports and retained content, manage membership requests, and mute or ban members as a board moderator","boards:drafts":"Read, save and discard your private cloud note drafts","boards:purchase":"Spend your PocketPass tokens on permanent stationery purchases","blocks:read":"See the accounts you have blocked","blocks:write":"Block and unblock accounts as you","privacy:read":"See whether you have Block Messages enabled","privacy:write":"Enable or disable Block Messages on your account"}'::jsonb;
$$;

-- This serializer calls the now request-dependent profile serializer.
alter function private.api_encounter_json(public.nearby_encounters, public.profiles) stable;

commit;
