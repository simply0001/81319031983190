#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/common.sh"

require_command curl
require_command docker
require_command openssl
require_command timeout
validate_compose

failed=0

fail() {
  printf 'FAIL  %s\n' "$*" >&2
  failed=1
}

pass() {
  printf 'PASS  %s\n' "$*"
}

printf 'Container state\n'
while IFS= read -r service; do
  container_id="$(compose ps -q "${service}")"
  if [[ -z "${container_id}" ]]; then
    fail "${service}: no running container"
    continue
  fi

  state="$(
    docker inspect \
      --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' \
      "${container_id}"
  )"

  if [[ "${state}" == "running "* ]] \
    && [[ "${state}" != *"unhealthy"* ]] \
    && [[ "${state}" != *"starting"* ]]; then
    pass "${service}: ${state}"
  else
    fail "${service}: ${state}"
  fi
done < <(compose config --services)

while IFS= read -r internal_service; do
  if [[ "${internal_service}" == "caddy" ]]; then
    continue
  fi
  container_id="$(compose ps -q "${internal_service}")"
  if [[ -n "${container_id}" ]] \
    && [[ -n "$(docker port "${container_id}" 2>/dev/null)" ]]; then
    fail "${internal_service} unexpectedly publishes a host port"
  else
    pass "${internal_service} has no published host port"
  fi
done < <(compose config --services)

if compose exec -T db \
  psql --username postgres --dbname postgres --tuples-only --no-align \
    --command 'select 1' \
  | grep -qx '1'; then
  pass 'PostgreSQL accepts queries'
else
  fail 'PostgreSQL query failed'
fi

rls_count="$(
  compose exec -T db \
    psql --username postgres --dbname postgres --tuples-only --no-align \
      --command "
        select count(*)
        from pg_catalog.pg_class
        where relnamespace = 'public'::regnamespace
          and relname = any (array[
            'profiles',
            'friend_requests',
            'friendships',
            'user_blocks',
            'conversations',
            'conversation_members',
            'messages',
            'interaction_events',
            'friend_codes',
            'notifications',
            'nearby_encounters'
          ])
          and relrowsecurity
      " \
  | tr -d '[:space:]'
)"

if [[ "${rls_count}" == "11" ]]; then
  pass 'RLS is enabled on all eleven application tables'
else
  fail "expected RLS on 11 application tables, found ${rls_count:-none}"
fi

worker_rpc_exposure="$(
  compose exec -T db \
    psql --username postgres --dbname postgres --tuples-only --no-align \
      --command "
        select count(*)
        from unnest(array[
          'public.claim_message_push_batch()',
          'public.finish_message_push(uuid,uuid,text)',
          'public.claim_board_push_batch()',
          'public.finish_board_push(uuid,uuid,text)',
          'public.commit_board_branding(uuid,text,integer,integer)'
        ]::regprocedure[]) as worker_rpc(signature)
        cross join unnest(array['anon', 'authenticated']) as client_role(name)
        where has_function_privilege(client_role.name, worker_rpc.signature, 'execute')
      " \
    </dev/null | tr -d '[:space:]'
)"

if [[ "${worker_rpc_exposure}" == "0" ]]; then
  pass 'push worker and branding commit RPCs are closed to anon and authenticated'
else
  fail "anon or authenticated can execute push worker or branding commit RPCs (${worker_rpc_exposure:-query failed} grants; re-apply the migration grants, see the README)"
fi

if curl --fail --silent --show-error \
  --max-time 10 \
  https://api.pocketpass.xyz/healthz \
  | grep -qx 'ok'; then
  pass 'public Caddy health endpoint'
else
  fail 'https://api.pocketpass.xyz/healthz'
fi

SUPABASE_PUBLISHABLE_KEY="$(require_real_value SUPABASE_PUBLISHABLE_KEY)"

if curl --fail --silent --show-error \
  --max-time 10 \
  --header "apikey: ${SUPABASE_PUBLISHABLE_KEY}" \
  https://api.pocketpass.xyz/auth/v1/health \
  >/dev/null; then
  pass 'GoTrue health through Caddy and Kong'
else
  fail 'GoTrue health through Caddy and Kong'
fi

if curl --fail --silent --show-error \
  --max-time 10 \
  https://links.pocketpass.xyz/.well-known/assetlinks.json \
  >/dev/null; then
  pass 'Android App Links association is published'
else
  fail 'Android App Links association is missing'
fi

if curl --fail --silent --show-error \
  --max-time 10 \
  https://links.pocketpass.xyz/updates/latest.json \
  | python3 -c 'import json, sys; assert isinstance(json.load(sys.stdin).get("versionCode"), int)' \
  2>/dev/null; then
  pass 'app update manifest is published and valid'
else
  fail 'https://links.pocketpass.xyz/updates/latest.json'
fi

if curl --fail --silent --show-error \
  --max-time 10 \
  https://pocketpass.xyz/ \
  | grep '<title>PocketPass' >/dev/null; then
  pass 'public website is served'
else
  fail 'https://pocketpass.xyz/'
fi

if [[ "$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --max-time 10 \
  https://pocketpass.xyz/privacy)" == "200" ]]; then
  pass 'public website privacy page is served'
else
  fail 'https://pocketpass.xyz/privacy'
fi

if curl --fail --silent --show-error \
  --max-time 10 \
  https://pocketpass.xyz/updates/latest.json \
  | python3 -c 'import json, sys; assert isinstance(json.load(sys.stdin).get("versionCode"), int)' \
  2>/dev/null; then
  pass 'public website mirrors the app update manifest'
else
  fail 'https://pocketpass.xyz/updates/latest.json'
fi

if curl --fail --silent --show-error \
  --max-time 10 \
  https://admin.pocketpass.xyz/config.js \
  | grep -q "POCKETPASS_ADMIN_KEY='sb_publishable_"; then
  pass 'admin console is served with its runtime config'
else
  fail 'https://admin.pocketpass.xyz/config.js'
fi

if [[ "$(read_env_value PUBLIC_API_ENABLED)" == "true" ]]; then
  api_client_role="$(
    compose exec -T db \
      psql --username postgres --dbname postgres --tuples-only --no-align \
        --command "
          select count(*)
          from pg_catalog.pg_roles
          where rolname = 'api_client'
            and not rolcanlogin
            and pg_has_role('authenticator', oid, 'SET')
        " \
      </dev/null | tr -d '[:space:]'
  )"
  if [[ "${api_client_role}" == "1" ]]; then
    pass 'api_client role exists and authenticator can SET it'
  else
    fail "api_client role is missing or not granted to authenticator (found ${api_client_role:-none}; re-create it after a restore, see the README)"
  fi

  api_client_realtime="$(
    compose exec -T db \
      psql --username postgres --dbname postgres --tuples-only --no-align \
        --command "select has_schema_privilege('api_client', 'realtime', 'usage')" \
      </dev/null | tr -d '[:space:]'
  )"
  if [[ "${api_client_realtime}" == "t" ]]; then
    pass 'api_client can use the realtime schema'
  else
    fail "api_client lacks usage on schema realtime (found ${api_client_realtime:-none}; grant it as supabase_admin, see the README)"
  fi

  realtime_cap="$(
    compose exec -T db \
      psql --username postgres --dbname postgres --tuples-only --no-align \
        --command "select max_concurrent_users || '/' || max_joins_per_second || '/' || max_events_per_second || '/' || max_bytes_per_second from _realtime.tenants where external_id = 'realtime-dev'" \
      </dev/null | tr -d '[:space:]'
  )"
  if [[ "${realtime_cap}" == "5000/500/1000/1000000" ]]; then
    pass 'realtime tenant allows 5000 connections, 500 joins/s, 1000 events/s'
  else
    fail "realtime tenant limits are ${realtime_cap:-missing}, expected 5000/500/1000/1000000 (the self-host seed re-inserted the row; see the README)"
  fi

  if ! getent hosts developer.pocketpass.xyz >/dev/null 2>&1; then
    warn 'developer.pocketpass.xyz does not resolve yet; create its DNS A record'
  elif curl --fail --silent --show-error \
    --max-time 10 \
    https://developer.pocketpass.xyz/config.js \
    | grep -q "POCKETPASS_DEVELOPER_KEY='sb_publishable_"; then
    pass 'developer portal is served with its runtime config'
  else
    fail 'https://developer.pocketpass.xyz/config.js'
  fi
fi

for secret_spec in \
  'DISCORD_LIMIT_REQUESTS_WEBHOOK_URL|discord_limit_requests_webhook|developer limit requests notify Discord|developer limit requests will not notify Discord' \
  'DISCORD_SUPPORTERS_WEBHOOK_URL|discord_supporters_webhook|Ko-fi payments notify Discord|Ko-fi payments will not notify Discord' \
  'KOFI_VERIFICATION_TOKEN|kofi_verification_token|the Ko-fi webhook verifies deliveries|the Ko-fi webhook is inert'
do
  IFS='|' read -r secret_env secret_name secret_label secret_inert <<<"${secret_spec}"
  secret_count="$(
    compose exec -T db \
      psql --username postgres --dbname postgres --tuples-only --no-align \
        --command "select count(*) from vault.secrets where name = '${secret_name}'" \
      </dev/null | tr -d '[:space:]'
  )"
  if [[ -n "$(read_env_value "${secret_env}" 2>/dev/null || true)" ]]; then
    if [[ "${secret_count}" == "1" ]]; then
      pass "${secret_label} through the Vault secret ${secret_name}"
    else
      fail "${secret_env} is set but not in Vault (run scripts/sync-vault-secrets.sh)"
    fi
  elif [[ "${secret_count}" == "1" ]]; then
    warn "Vault still holds ${secret_name} although ${secret_env} is empty"
  else
    warn "${secret_env} is empty; ${secret_inert}"
  fi
done

kofi_status="$(
  curl --silent --output /dev/null --write-out '%{http_code}' \
    --max-time 10 \
    --request POST --data 'data=not-json' \
    https://api.pocketpass.xyz/webhooks/kofi || true
)"
if [[ "${kofi_status}" == "400" ]]; then
  pass 'Ko-fi webhook route reaches PostgREST'
else
  fail "Ko-fi webhook answered ${kofi_status:-nothing} to a malformed delivery (expected 400)"
fi

studio_status="$(
  curl --silent --output /dev/null --write-out '%{http_code} %{redirect_url}' \
    --max-time 10 \
    https://studio.pocketpass.xyz/api/platform/profile || true
)"
if [[ "${studio_status}" == "302 https://studio.pocketpass.xyz/_pocketpass/login" ]]; then
  pass 'Studio redirects unauthenticated requests to the sign-in page'
else
  fail "Studio answered '${studio_status:-nothing}' without a session (expected a 302 to /_pocketpass/login)"
fi

if curl --fail --silent --show-error \
  --max-time 10 \
  https://studio.pocketpass.xyz/_pocketpass/login \
  | grep -q 'pocketpass_studio='; then
  pass 'Studio sign-in page is served'
else
  fail 'https://studio.pocketpass.xyz/_pocketpass/login'
fi

studio_token="$(openssl rand -hex 32)"
studio_cleanup() {
  compose exec -T db \
    psql --username postgres --dbname postgres --quiet \
      --command "delete from private.admin_studio_sessions where token_hash = extensions.digest('${studio_token}'::text, 'sha256')" \
    </dev/null >/dev/null 2>&1 || true
}
trap studio_cleanup EXIT
studio_rows="$(
  compose exec -T db \
    psql --username postgres --dbname postgres --tuples-only --no-align \
      --command "insert into private.admin_studio_sessions (token_hash, user_id, expires_at) select extensions.digest('${studio_token}'::text, 'sha256'), user_id, now() + interval '60 seconds' from private.admin_users where is_owner limit 1 returning 1" \
    </dev/null | grep -c '^1$' || true
)"
if [[ "${studio_rows}" == "1" ]]; then
  studio_session_status="$(
    curl --silent --output /dev/null --write-out '%{http_code}' \
      --max-time 10 \
      --cookie "pocketpass_studio=${studio_token}" \
      https://studio.pocketpass.xyz/api/platform/profile || true
  )"
  if [[ "${studio_session_status}" == "200" ]]; then
    pass 'Studio admits an owner session through Caddy and PostgREST'
  else
    fail "Studio answered ${studio_session_status:-nothing} to a valid owner session (expected 200)"
  fi
else
  fail 'could not create a throwaway Studio session (no owner in private.admin_users?)'
fi
studio_cleanup
trap - EXIT

if openssl s_client \
  -connect api.pocketpass.xyz:443 \
  -servername api.pocketpass.xyz \
  </dev/null 2>/dev/null \
  | openssl x509 -noout -checkend 1209600 >/dev/null; then
  pass 'API TLS certificate is valid for at least 14 days'
else
  fail 'API TLS certificate expires within 14 days or cannot be read'
fi

if [[ "$(read_env_value SMTP_HOST)" == "smtp.resend.com" \
  && "$(read_env_value SMTP_PORT)" == "465" ]] \
  && compose exec -T auth printenv GOTRUE_SMTP_PORT </dev/null | tr -d '\r' | grep -qx '465'; then
  pass 'Auth is configured for Resend implicit TLS on port 465'
else
  fail 'Auth SMTP settings are not pinned to Resend implicit TLS on port 465'
fi

if timeout 15 openssl s_client \
  -connect smtp.resend.com:465 \
  -servername smtp.resend.com \
  -verify_hostname smtp.resend.com \
  -verify_return_error \
  -brief </dev/null >/dev/null 2>&1; then
  pass 'Resend SMTP TLS handshake and certificate verify'
else
  fail 'Resend SMTP TLS handshake or certificate verification failed'
fi

if ((failed != 0)); then
  exit 1
fi

printf '\nPocketPass backend health checks passed.\n'
