begin;

-- Keep message media private and retain the existing 10 MiB limit and RLS policies.
update storage.buckets
set allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp', 'image/gif']
where id = 'message-media';

create or replace function public.api_v1_messages_send(jsonb default '{}'::jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_guard jsonb := private.api_guard('messages:write');
  v_user_id uuid;
  v_conversation_id uuid;
  v_body text;
  v_operation_id uuid;
  v_reply_to_id uuid;
  v_attachment jsonb;
  v_path text;
  v_mime_type text;
  v_metadata jsonb := '{}'::jsonb;
  v_message_id uuid;
  v_sent public.messages;
  v_state text;
  v_message text;
  v_hint text;
begin
  if v_guard ? 'code' then
    return v_guard;
  end if;
  v_user_id := (v_guard ->> 'user_id')::uuid;
  begin
    perform private.api_reject_unknown(
      $1,
      array['conversation_id', 'body', 'client_operation_id', 'reply_to_id', 'attachment']
    );
    v_conversation_id := private.api_arg_uuid($1, 'conversation_id', true);
    v_body := private.api_arg_text($1, 'body', false, null);
    v_operation_id := private.api_arg_uuid($1, 'client_operation_id', true);
    v_reply_to_id := private.api_arg_uuid($1, 'reply_to_id', false);

    v_attachment := $1 -> 'attachment';
    if v_attachment is not null and jsonb_typeof(v_attachment) <> 'null' then
      if jsonb_typeof(v_attachment) <> 'object' then
        raise sqlstate 'PT400' using
          message = 'attachment must be an object with path and mime_type',
          hint = 'INVALID_FIELD';
      end if;
      perform private.api_reject_unknown(v_attachment, array['path', 'mime_type']);
      v_path := private.api_arg_text(v_attachment, 'path', true, 512);
      v_mime_type := private.api_arg_text(v_attachment, 'mime_type', true, 64);
      if v_mime_type not in ('image/jpeg', 'image/png', 'image/webp', 'image/gif') then
        raise sqlstate 'PT400' using
          message = 'attachment.mime_type must be image/jpeg, image/png, image/webp or image/gif',
          hint = 'UNSUPPORTED_MEDIA';
      end if;
      if v_path !~ (
        '^' || v_user_id::text || '/' || v_conversation_id::text
        || '/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
      ) then
        raise sqlstate 'PT400' using
          message = 'attachment.path must be <your user id>/<conversation id>/<file name> in the message-media bucket',
          hint = 'INVALID_ATTACHMENT_PATH';
      end if;
      if not exists (
        select 1
        from storage.objects as object
        where object.bucket_id = 'message-media'
          and object.name = v_path
      ) then
        raise sqlstate 'PT404' using
          message = 'attachment.path has not been uploaded to the message-media bucket',
          hint = 'ATTACHMENT_NOT_FOUND';
      end if;
      v_metadata := jsonb_build_object(
        'attachment', jsonb_build_object('path', v_path, 'mime_type', v_mime_type)
      );
      if v_body is null or btrim(v_body) = '' then
        v_body := '📷';
      end if;
    end if;

    if v_body is null then
      raise sqlstate 'PT400' using
        message = 'body is required',
        hint = 'MISSING_FIELD';
    end if;

    if char_length(btrim(v_body)) not between 1 and 4000 then
      raise sqlstate 'PT400' using
        message = 'Message body must contain 1 to 4000 characters',
        hint = 'BODY_LENGTH';
    end if;

    v_message_id := substr(
      encode(
        extensions.digest(v_user_id::text || ':' || v_operation_id::text, 'sha256'),
        'hex'
      ),
      1,
      32
    )::uuid;

    v_sent := public.send_message(
      v_message_id,
      v_conversation_id,
      v_operation_id,
      v_body,
      v_reply_to_id,
      v_metadata
    );

    return jsonb_build_object('message', private.api_message_json(v_sent));
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

commit;
