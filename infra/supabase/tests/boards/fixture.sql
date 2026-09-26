create role anon; create role authenticated; create role service_role;
    create schema auth; create schema private; create schema extensions; create schema realtime;
    grant usage on schema auth,public to authenticated,anon;
    create function auth.jwt() returns jsonb language sql as $$select coalesce(nullif(current_setting('request.jwt.claims',true),''),'{}')::jsonb$$;
    create function auth.uid() returns uuid language sql as $$select (auth.jwt()->>'sub')::uuid$$;
    create table public.profiles(user_id uuid primary key,display_name text default 'Person',avatar_path text,bio text not null default '',block_messages boolean default false);
    create table public.user_blocks(blocker_id uuid,blocked_id uuid,created_at timestamptz not null default now(),primary key(blocker_id,blocked_id));
    create table private.admin_users(user_id uuid primary key,permissions text[] default '{}',is_owner boolean default false,
      constraint admin_users_permissions_known check(permissions<@array['users','audit','legacy','tokens','achievements','admins','apps','supporters']::text[]));
    create function private.has_permission(p_permission text) returns boolean language sql as $$select exists(select 1 from private.admin_users where user_id=auth.uid() and (is_owner or p_permission=any(permissions)))$$;
    create function private.require_permission(p text) returns uuid language plpgsql as $$begin if not private.has_permission(p) then raise exception 'Permission required: %',p using errcode='42501'; end if; return auth.uid(); end$$;
    create table public.token_balances(user_id uuid primary key,balance integer not null default 0,updated_at timestamptz default now());
    create function private.ensure_token_balance(p uuid) returns void language sql as $$insert into public.token_balances(user_id) values(p) on conflict do nothing$$;
    create table public.achievement_unlocks(user_id uuid,achievement_key text);
    create table public.supporter_status(user_id uuid primary key,active_until timestamptz);
    create function private.is_supporter(p uuid) returns boolean language sql as $$select exists(select 1 from public.supporter_status where user_id=p and active_until>now())$$;
    create function private.achievement_keys() returns text[] language sql as $$select array['day_one','icebreaker']::text[]$$;
    create function realtime.send(jsonb,text,text,boolean) returns void language sql as $$select$$;
    create function extensions.digest(text,text) returns bytea language sql as $$select decode(md5($1),'hex')$$;
    create function extensions.gen_random_bytes(integer) returns bytea language sql as $$select decode(replace(gen_random_uuid()::text,'-',''),'hex')$$;
    create table private.message_push_devices(installation_id uuid primary key,user_id uuid,binding_id uuid default gen_random_uuid(),token text,platform text default 'android',enabled boolean default true,refreshed_at timestamptz default now());
    create function public.register_message_push_device(p_installation_id uuid,p_token text,p_enabled boolean) returns uuid language plpgsql as $$
    declare binding uuid;
    begin
      insert into private.message_push_devices(installation_id,user_id,token,enabled) values(p_installation_id,auth.uid(),p_token,p_enabled)
      on conflict(installation_id) do update set user_id=auth.uid(),token=p_token,enabled=p_enabled returning binding_id into binding;
      return binding;
    end $$;
