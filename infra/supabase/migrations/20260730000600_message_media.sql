begin;

-- Message image attachments live in a private bucket keyed by
--   <sender_id>/<conversation_id>/<file>
-- so that the insert policy can pin the folder to the uploader and the read policy can
-- authorise on conversation membership without a join back to public.messages.
insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'message-media',
  'message-media',
  false,
  10485760,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create or replace function private.message_media_conversation_id(p_name text)
returns uuid
language sql
immutable
set search_path = ''
as $$
  select case
    when (storage.foldername(p_name))[2] ~
      '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
      then ((storage.foldername(p_name))[2])::uuid
    else null
  end;
$$;

revoke all on function private.message_media_conversation_id(text) from public;

create policy pocketpass_message_media_read_members
on storage.objects
for select
to authenticated
using (
  bucket_id = 'message-media'
  and private.is_active_conversation_member(
    private.message_media_conversation_id(name),
    auth.uid()
  )
);

create policy pocketpass_message_media_insert_own_folder
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'message-media'
  and (storage.foldername(name))[1] = auth.uid()::text
  and private.is_active_conversation_member(
    private.message_media_conversation_id(name),
    auth.uid()
  )
  and name ~ (
    '^'
    || auth.uid()::text
    || '/[0-9a-fA-F-]{36}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
  )
);

create policy pocketpass_message_media_update_own_folder
on storage.objects
for update
to authenticated
using (
  bucket_id = 'message-media'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'message-media'
  and (storage.foldername(name))[1] = auth.uid()::text
  and name ~ (
    '^'
    || auth.uid()::text
    || '/[0-9a-fA-F-]{36}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
  )
);

create policy pocketpass_message_media_delete_own_folder
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'message-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

commit;
