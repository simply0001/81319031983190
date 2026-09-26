begin;

create or replace function public.issue_nearby_credentials(
  p_signing_public_keys text[]
)
returns table (
  token uuid,
  signing_public_key text,
  expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_key text;
  v_active_count integer;
begin
  if v_user_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  if p_signing_public_keys is null
    or cardinality(p_signing_public_keys) not between 1 and 32
  then
    raise exception 'Between 1 and 32 public keys are required' using errcode = '22023';
  end if;

  delete from private.nearby_credentials as credential
  where credential.owner_id = v_user_id
    and (
      credential.expires_at <= now() - interval '1 day'
      or credential.consumed_at <= now() - interval '1 day'
    );

  select count(*)
  into v_active_count
  from private.nearby_credentials as credential
  where credential.owner_id = v_user_id
    and credential.consumed_at is null
    and credential.expires_at > now();

  if v_active_count + cardinality(p_signing_public_keys) > 64 then
    raise exception 'Credential inventory limit reached' using errcode = '54000';
  end if;

  foreach v_key in array p_signing_public_keys loop
    if v_key is null or char_length(v_key) not between 80 and 1024 then
      raise exception 'Invalid signing public key' using errcode = '22023';
    end if;
    return query
    insert into private.nearby_credentials (owner_id, signing_public_key)
    values (v_user_id, v_key)
    returning
      nearby_credentials.token,
      nearby_credentials.signing_public_key,
      nearby_credentials.expires_at;
  end loop;
end;
$$;

revoke all on function public.issue_nearby_credentials(text[]) from public;
grant execute on function public.issue_nearby_credentials(text[]) to authenticated;

commit;
