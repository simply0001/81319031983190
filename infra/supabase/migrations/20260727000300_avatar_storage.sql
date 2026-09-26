begin;

insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'avatars',
  'avatars',
  false,
  5242880,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create policy pocketpass_avatars_read_visible_profiles
on storage.objects
for select
to authenticated
using (
  bucket_id = 'avatars'
  and private.can_view_profile(
    auth.uid(),
    private.avatar_owner_id(name)
  )
);

create policy pocketpass_avatars_insert_own_folder
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'avatars'
  and (storage.foldername(name))[1] = auth.uid()::text
  and name ~ (
    '^'
    || auth.uid()::text
    || '/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
  )
);

create policy pocketpass_avatars_update_own_folder
on storage.objects
for update
to authenticated
using (
  bucket_id = 'avatars'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'avatars'
  and (storage.foldername(name))[1] = auth.uid()::text
  and name ~ (
    '^'
    || auth.uid()::text
    || '/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
  )
);

create policy pocketpass_avatars_delete_own_folder
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'avatars'
  and (storage.foldername(name))[1] = auth.uid()::text
);

commit;
