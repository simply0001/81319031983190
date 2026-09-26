begin;

-- 20260730000600 revoked execute from public without granting it back to authenticated,
-- so every message-media storage policy failed with "permission denied for function".
-- Policies run with the caller's privileges, which is why the other policy helpers --
-- private.avatar_owner_id(text) among them -- are all granted to authenticated.
grant execute on function private.message_media_conversation_id(text) to authenticated;

commit;
