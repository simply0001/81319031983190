begin;

create or replace function public.developer_create_app(
  p_name text,
  p_description text,
  p_website text,
  p_logo_url text,
  p_redirect_uris text[],
  p_scopes text[],
  p_client_type text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actor_id uuid := auth.uid();
  v_name text;
  v_description text;
  v_website text;
  v_logo_url text;
  v_redirect_uris text[];
  v_scopes text[];
  v_client_type text := lower(btrim(coalesce(p_client_type, '')));
  v_client_id uuid := gen_random_uuid();
  v_secret text;
  v_secret_hash text;
  v_app private.developer_apps;
begin
  if v_actor_id is null then
    raise exception 'Authentication required' using errcode = '42501';
  end if;

  perform 1
  from public.profiles as profile
  where profile.user_id = v_actor_id
  for update;
  if not found then
    raise exception 'Profile not found' using errcode = 'P0002';
  end if;

  v_name := private.developer_validate_name(p_name);
  v_description := private.developer_validate_description(p_description);
  v_website := private.developer_validate_url(p_website, 'Website');
  v_logo_url := private.developer_validate_url(p_logo_url, 'Logo URL');
  v_redirect_uris := private.developer_validate_redirect_uris(p_redirect_uris);
  v_scopes := private.developer_validate_scopes(p_scopes);
  if v_client_type not in ('public', 'confidential') then
    raise exception 'Client type must be public or confidential' using errcode = '22023';
  end if;

  if (
    select count(*)
    from private.developer_apps as app
    where app.owner_user_id = v_actor_id
  ) >= private.developer_max_apps() then
    raise sqlstate 'PT403' using
      message = format('You can register at most %s apps', private.developer_max_apps()),
      hint = 'APP_LIMIT';
  end if;

  if v_client_type = 'confidential' then
    v_secret := 'pp_secret_' || encode(extensions.gen_random_bytes(32), 'hex');
    v_secret_hash := private.developer_secret_hash(v_secret);
  end if;

  insert into auth.oauth_clients (
    id,
    client_secret_hash,
    registration_type,
    redirect_uris,
    grant_types,
    client_name,
    client_uri,
    logo_uri,
    client_type,
    token_endpoint_auth_method,
    created_at,
    updated_at
  )
  values (
    v_client_id,
    coalesce(v_secret_hash, ''),
    'manual'::auth.oauth_registration_type,
    array_to_string(v_redirect_uris, ','),
    'authorization_code,refresh_token',
    v_name,
    nullif(v_website, ''),
    nullif(v_logo_url, ''),
    v_client_type::auth.oauth_client_type,
    case when v_client_type = 'confidential' then 'client_secret_basic' else 'none' end,
    now(),
    now()
  );

  insert into private.developer_apps (
    client_id,
    owner_user_id,
    name,
    description,
    website,
    logo_url,
    client_type,
    scopes
  )
  values (
    v_client_id,
    v_actor_id,
    v_name,
    v_description,
    v_website,
    v_logo_url,
    v_client_type,
    v_scopes
  )
  returning * into v_app;

  insert into private.developer_audit (actor_id, action, client_id, payload)
  values (
    v_actor_id,
    'app_create',
    v_client_id,
    jsonb_build_object(
      'name', v_name,
      'client_type', v_client_type,
      'scopes', to_jsonb(v_scopes),
      'redirect_uris', to_jsonb(v_redirect_uris)
    )
  );

  return jsonb_build_object(
    'app', private.developer_app_json(v_app),
    'client_secret', v_secret
  );
end;
$$;

revoke all on function public.developer_create_app(text, text, text, text, text[], text[], text) from public, anon;
grant execute on function public.developer_create_app(text, text, text, text, text[], text[], text) to authenticated;

update auth.oauth_clients
set client_secret_hash = ''
where client_secret_hash is null;

commit;
