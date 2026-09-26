#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/common.sh"

require_command curl
require_command docker
require_command base64
validate_compose

auth_only=0
for argument in "$@"; do
  case "${argument}" in
    --auth-only) auth_only=1 ;;
    *) die "unknown argument: ${argument}" ;;
  esac
done

API_URL="${API_URL:-https://api.pocketpass.xyz}"
LINKS_URL="${LINKS_URL:-https://links.pocketpass.xyz}"
DEVELOPER_URL="${DEVELOPER_URL:-https://developer.pocketpass.xyz}"

for origin in "${API_URL}" "${LINKS_URL}" "${DEVELOPER_URL}"; do
  [[ "${origin}" == https://* ]] || die "public API checks require HTTPS endpoints"
done

work_dir="$(mktemp -d)"
trap 'rm -rf -- "${work_dir}"' EXIT
body_file="${work_dir}/body"
headers_file="${work_dir}/headers"

failed=0

fail() {
  printf 'FAIL  %s\n' "$*" >&2
  failed=1
}

pass() {
  printf 'PASS  %s\n' "$*"
}

body_excerpt() {
  tr -d '\n' <"${body_file}" | head -c 200
}

request() {
  curl --silent --show-error --max-time 20 \
    --output "${body_file}" --dump-header "${headers_file}" \
    --write-out '%{http_code}' "$@"
}

json_string() {
  sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" "${body_file}" | head -n 1
}

base64url_encode() {
  base64 -w 0 | tr '+/' '-_' | tr -d '='
}

jwt_payload() {
  local segment
  segment="$(cut -d. -f2 <<<"$1" | tr '_-' '/+')"
  case $((${#segment} % 4)) in
    2) segment="${segment}==" ;;
    3) segment="${segment}=" ;;
  esac
  printf '%s' "${segment}" | base64 -d 2>/dev/null || true
}

jwt_number_claim() {
  sed -n "s/.*\"$2\":\([0-9]*\).*/\1/p" <<<"$1" | head -n 1
}

synthetic_jwt() {
  printf '%s.%s.%s' \
    "$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | base64url_encode)" \
    "$(printf '%s' "$1" | base64url_encode)" \
    "$(printf '%s' 'unsigned' | base64url_encode)"
}

container_environment() {
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$(compose ps -q "$1")"
}

assert_env() {
  local environment="$1" expected="$2"
  if grep -qx -- "${expected}" <<<"${environment}"; then
    pass "${expected}"
  else
    fail "expected ${expected} in the container environment"
  fi
}

publishable_key="$(require_real_value SUPABASE_PUBLISHABLE_KEY)"
secret_key="$(require_real_value SUPABASE_SECRET_KEY)"

printf 'Auth container environment\n'
auth_environment="$(container_environment auth)"
assert_env "${auth_environment}" 'GOTRUE_OAUTH_SERVER_ENABLED=true'
assert_env "${auth_environment}" 'GOTRUE_HOOK_CUSTOM_ACCESS_TOKEN_ENABLED=true'
assert_env "${auth_environment}" 'GOTRUE_HOOK_CUSTOM_ACCESS_TOKEN_URI=pg-functions://postgres/public/pocketpass_access_token_hook'
assert_env "${auth_environment}" 'GOTRUE_OAUTH_SERVER_AUTHORIZATION_PATH=/oauth/consent'
assert_env "${auth_environment}" 'GOTRUE_OAUTH_SERVER_ALLOW_DYNAMIC_REGISTRATION=false'
assert_env "${auth_environment}" 'GOTRUE_SITE_URL=https://links.pocketpass.xyz'
if grep -q '^GOTRUE_URI_ALLOW_LIST=.*https://developer.pocketpass.xyz/' <<<"${auth_environment}"; then
  pass 'GOTRUE_URI_ALLOW_LIST includes https://developer.pocketpass.xyz/'
else
  fail 'GOTRUE_URI_ALLOW_LIST does not include https://developer.pocketpass.xyz/'
fi
unset auth_environment

printf 'Database grants\n'
db_scalar() {
  compose exec -T db \
    psql --username postgres --dbname postgres --tuples-only --no-align --command "$1" \
    </dev/null | tr -d '[:space:]'
}
assert_db() {
  local label="$1" expected="$2" actual
  actual="$(db_scalar "$3")"
  if [[ "${actual}" == "${expected}" ]]; then
    pass "${label}"
  else
    fail "${label} (got ${actual:-none}, expected ${expected})"
  fi
}
assert_db 'api_client can use the realtime schema' t \
  "select has_schema_privilege('api_client', 'realtime', 'usage')"
assert_db 'api_client can read realtime.messages' t \
  "select has_table_privilege('api_client', 'realtime.messages', 'select')"
assert_db 'api_client can insert presence rows into realtime.messages' t \
  "select has_table_privilege('api_client', 'realtime.messages', 'insert')"
assert_db 'api_client cannot update or delete realtime.messages' f \
  "select has_table_privilege('api_client', 'realtime.messages', 'update, delete')"
assert_db 'the realtime read policy for api_client exists' 1 \
  "select count(*) from pg_policies where schemaname = 'realtime' and tablename = 'messages' and policyname = 'pocketpass_api_realtime_read'"
assert_db 'the presence-track policy for api_client exists' 1 \
  "select count(*) from pg_policies where schemaname = 'realtime' and tablename = 'messages' and policyname = 'pocketpass_api_presence_track'"
assert_db 'the message-media upload policy for api_client exists' 1 \
  "select count(*) from pg_policies where schemaname = 'storage' and tablename = 'objects' and policyname = 'pocketpass_api_message_media_insert'"
assert_db 'thirty-one api_v1 functions are installed' 31 \
  "select count(*) from pg_proc where pronamespace = 'public'::regnamespace and proname like 'api\_v1\_%'"
assert_db 'friends:write is in the scope catalog' t \
  "select 'friends:write' = any (private.api_scope_keys())"
assert_db 'the presence, tokens and encounters scopes are in the scope catalog' t \
  "select array['presence:read', 'presence:write', 'tokens:read', 'encounters:read'] <@ private.api_scope_keys()"

printf 'First-party sign-in\n'
first_party_token=""
if [[ -n "${SMOKE_EMAIL:-}" ]]; then
  status="$(
    request --request POST \
      --header "apikey: ${secret_key}" \
      --header 'Content-Type: application/json' \
      --data "{\"type\":\"magiclink\",\"email\":\"${SMOKE_EMAIL}\"}" \
      "${API_URL}/auth/v1/admin/generate_link"
  )"
  email_otp="$(json_string email_otp)"
  if [[ "${status}" == 200 && -n "${email_otp}" ]]; then
    pass 'admin generate_link issued an email OTP without sending mail'
  else
    fail "admin generate_link answered ${status}: $(body_excerpt)"
  fi

  if [[ -n "${email_otp}" ]]; then
    status="$(
      request --request POST \
        --header "apikey: ${publishable_key}" \
        --header 'Content-Type: application/json' \
        --data "{\"type\":\"email\",\"email\":\"${SMOKE_EMAIL}\",\"token\":\"${email_otp}\"}" \
        "${API_URL}/auth/v1/verify"
    )"
    first_party_token="$(json_string access_token)"
    refresh_token="$(json_string refresh_token)"
    payload="$(jwt_payload "${first_party_token}")"
    if [[ "${status}" == 200 && -n "${first_party_token}" ]] \
      && grep -q '"role":"authenticated"' <<<"${payload}" \
      && ! grep -q '"client_id"' <<<"${payload}"; then
      pass 'verify issued a first-party token with role authenticated and no client_id'
    else
      fail "verify answered ${status} or the first-party token shape changed"
    fi
    unset payload

    status="$(
      request --request POST \
        --header "apikey: ${publishable_key}" \
        --header 'Content-Type: application/json' \
        --data "{\"refresh_token\":\"${refresh_token}\"}" \
        "${API_URL}/auth/v1/token?grant_type=refresh_token"
    )"
    refreshed_token="$(json_string access_token)"
    if [[ "${status}" == 200 && -n "${refreshed_token}" ]]; then
      pass 'refresh_token grant works for first-party sessions'
      first_party_token="${refreshed_token}"
    else
      fail "refresh_token grant answered ${status}"
    fi
    unset refresh_token refreshed_token
  fi

  if [[ -n "${first_party_token}" ]]; then
    status="$(
      request --request GET \
        --header "apikey: ${publishable_key}" \
        --header "Authorization: Bearer ${first_party_token}" \
        "${API_URL}/auth/v1/user"
    )"
    if [[ "${status}" == 200 ]] && grep -q '"id"' "${body_file}" \
      && [[ "$(json_string email)" == "${SMOKE_EMAIL,,}" ]]; then
      pass 'GET /auth/v1/user shows the signed-in owner their email'
    else
      fail "GET /auth/v1/user did not return the test account email (HTTP ${status})"
    fi

    status="$(
      request --request POST \
        --header "apikey: ${publishable_key}" \
        --header "Authorization: Bearer ${first_party_token}" \
        "${API_URL}/auth/v1/logout?scope=local"
    )"
    if [[ "${status}" == 204 ]]; then
      pass 'smoke session signed out'
    else
      fail "logout answered ${status}"
    fi
  fi
else
  warn 'SMOKE_EMAIL is not set; skipping the first-party sign-in smoke'
fi
unset first_party_token

if ((auth_only == 0)); then
  printf 'Gateway configuration\n'
  kong_environment="$(container_environment api-gw)"
  assert_env "${kong_environment}" 'KONG_UNTRUSTED_LUA=sandbox'
  if grep -q '^KONG_PLUGINS=.*post-function' <<<"${kong_environment}" \
    && grep -q '^KONG_PLUGINS=.*rate-limiting' <<<"${kong_environment}"; then
    pass 'KONG_PLUGINS enables post-function and rate-limiting'
  else
    fail 'KONG_PLUGINS lacks post-function or rate-limiting'
  fi
  unset kong_environment

  if compose exec -T api-gw kong config parse /home/kong/temp.yml >/dev/null 2>&1; then
    pass 'kong/kong.yml parses'
  else
    fail 'kong config parse rejected kong/kong.yml'
  fi

  if compose run --rm --no-deps -T caddy \
    caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null 2>&1; then
    pass 'caddy/Caddyfile validates'
  else
    fail 'caddy validate rejected caddy/Caddyfile'
  fi

  printf 'Connected-app token fence\n'
  client_jwt="$(synthetic_jwt '{"sub":"00000000-0000-4000-8000-000000000001","client_id":"00000000-0000-4000-8000-000000000002","role":"api_client","aud":"authenticated","exp":4102444800}')"
  unscoped_jwt="$(synthetic_jwt '{"sub":"00000000-0000-4000-8000-000000000001","client_id":"00000000-0000-4000-8000-000000000002","role":"authenticated","aud":"authenticated","exp":4102444800}')"

  expect_forbidden() {
    local method="$1" path="$2" authorization="$3"
    local status
    local -a data=()
    if [[ "${method}" != GET && "${method}" != DELETE ]]; then
      data=(--header 'Content-Type: application/json' --data '{}')
    fi
    status="$(
      request --request "${method}" \
        --header "apikey: ${publishable_key}" \
        --header "Authorization: ${authorization}" \
        "${data[@]}" \
        "${API_URL}${path}"
    )"
    if [[ "${status}" == 403 ]] && grep -q 'oauth_client_forbidden' "${body_file}"; then
      pass "${method} ${path} refuses the connected-app token"
    else
      fail "${method} ${path} answered ${status} to a connected-app token: $(body_excerpt)"
    fi
  }

  expect_forbidden GET /auth/v1/user "Bearer ${client_jwt}"
  expect_forbidden GET /auth/v1/user "bearer ${client_jwt}"
  expect_forbidden GET /auth/v1/user/ "Bearer ${client_jwt}"
  expect_forbidden GET /auth/v1//user "Bearer ${client_jwt}"
  expect_forbidden PUT /auth/v1/user "Bearer ${client_jwt}"
  expect_forbidden DELETE /auth/v1/user "Bearer ${client_jwt}"
  expect_forbidden POST /auth/v1/factors "Bearer ${client_jwt}"
  expect_forbidden POST /auth/v1/logout "Bearer ${client_jwt}"
  expect_forbidden GET /auth/v1/user/oauth/grants "Bearer ${client_jwt}"
  expect_forbidden GET /auth/v1/oauth/authorizations/00000000-0000-4000-8000-000000000003 "Bearer ${client_jwt}"
  expect_forbidden POST /graphql/v1 "Bearer ${client_jwt}"
  expect_forbidden GET /rest/v1/profiles "Bearer ${unscoped_jwt}"
  expect_forbidden POST /rest/v1/rpc/send_message "Bearer ${unscoped_jwt}"
  expect_forbidden GET /storage/v1/object/authenticated/avatars/x "Bearer ${unscoped_jwt}"

  status="$(
    request --request POST \
      --header "apikey: ${publishable_key}" \
      --header 'Content-Type: application/json' \
      --data '{}' \
      "${API_URL}/rest/v1/rpc/api_v1_session_get"
  )"
  if [[ "${status}" == 404 ]] && grep -q 'Use https:' "${body_file}"; then
    pass '/rest/v1/rpc/api_v1_* is blocked'
  else
    fail "/rest/v1/rpc/api_v1_session_get answered ${status}: $(body_excerpt)"
  fi

  printf 'Public API surface\n'
  status="$(
    request --request POST \
      --header 'Content-Type: application/json' \
      --data '{}' \
      "${API_URL}/v1/session.get"
  )"
  if [[ "${status}" != 200 ]] && grep -q '"code"' "${body_file}"; then
    pass "POST /v1/session.get without a token answers ${status} with a JSON error envelope"
  else
    fail "POST /v1/session.get without a token answered ${status}: $(body_excerpt)"
  fi

  status="$(
    request --request OPTIONS \
      --header 'Origin: https://example.com' \
      --header 'Access-Control-Request-Method: POST' \
      --header 'Access-Control-Request-Headers: authorization,content-type' \
      "${API_URL}/v1/me.get"
  )"
  if grep -qi '^access-control-allow-origin:' "${headers_file}"; then
    pass 'OPTIONS /v1/me.get answers a CORS preflight'
  else
    fail "OPTIONS /v1/me.get answered ${status} without Access-Control-Allow-Origin"
  fi

  status="$(
    request --request POST \
      --header 'Content-Type: application/json' \
      --data '{}' \
      "${API_URL}/v1/unknown"
  )"
  if [[ "${status}" == 404 ]] && grep -q 'UNKNOWN_ENDPOINT' "${body_file}"; then
    pass 'unknown /v1 paths answer the PT404 envelope'
  else
    fail "POST /v1/unknown answered ${status}: $(body_excerpt)"
  fi

  printf 'OAuth endpoints\n'
  status="$(request "${API_URL}/auth/v1/.well-known/openid-configuration")"
  if [[ "${status}" == 200 ]] && grep -q 'authorization_endpoint' "${body_file}"; then
    pass 'OpenID discovery is open'
  else
    fail "openid-configuration answered ${status}: $(body_excerpt)"
  fi

  status="$(request "${API_URL}/.well-known/oauth-authorization-server")"
  if [[ "${status}" == 200 ]] && grep -q 'authorization_endpoint' "${body_file}"; then
    pass 'OAuth authorization-server metadata is open'
  else
    fail "oauth-authorization-server answered ${status}: $(body_excerpt)"
  fi

  status="$(
    request --request POST \
      --header 'Content-Type: application/x-www-form-urlencoded' \
      --data '' \
      "${API_URL}/auth/v1/oauth/token"
  )"
  if grep -qE 'invalid_client|invalid_credentials' "${body_file}"; then
    pass 'POST /auth/v1/oauth/token reaches GoTrue without an apikey'
  else
    fail "POST /auth/v1/oauth/token answered ${status}: $(body_excerpt)"
  fi

  authorize_query='client_id=00000000-0000-4000-8000-000000000002&response_type=code&redirect_uri=https%3A%2F%2Fexample.com%2Fcb&scope=openid&code_challenge=abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrs'
  status="$(request "${API_URL}/auth/v1/oauth/authorize?${authorize_query}&code_challenge_method=S256")"
  if [[ "${status}" != 401 ]]; then
    pass "GET /auth/v1/oauth/authorize with S256 reaches GoTrue (${status})"
  else
    fail 'GET /auth/v1/oauth/authorize with S256 was stopped by key-auth'
  fi

  status="$(request "${API_URL}/auth/v1/oauth/authorize?${authorize_query}&code_challenge_method=plain" --header "apikey: ${publishable_key}")"
  if [[ "${status}" == 400 ]] && grep -q 'must be S256' "${body_file}"; then
    pass 'GET /auth/v1/oauth/authorize with code_challenge_method=plain is refused at the gateway even with an apikey'
  else
    fail "GET /auth/v1/oauth/authorize with plain answered ${status}"
  fi

  printf 'Ko-fi webhook\n'
  status="$(request --request POST --data 'data=not-json' "${API_URL}/webhooks/kofi")"
  if [[ "${status}" == 400 ]] && grep -q 'KOFI_PAYLOAD_INVALID' "${body_file}"; then
    pass 'POST /webhooks/kofi rejects a malformed delivery with 400'
  else
    fail "POST /webhooks/kofi (malformed) answered ${status}: $(body_excerpt)"
  fi

  kofi_probe_id="$(openssl rand -hex 16 | sed -E 's/^(.{8})(.{4})(.{4})(.{4})(.{12})$/\1-\2-\3-\4-\5/')"
  kofi_wrong='{"verification_token":"wrong","message_id":"'"${kofi_probe_id}"'","type":"Donation","email":"validate@pocketpass.test"}'
  status="$(request --request POST --data-urlencode "data=${kofi_wrong}" "${API_URL}/webhooks/kofi?select=message_id")"
  if [[ "${status}" == 401 ]] && grep -q 'KOFI_TOKEN_INVALID' "${body_file}"; then
    pass 'POST /webhooks/kofi refuses a wrong verification token'
  else
    fail "POST /webhooks/kofi (wrong token) answered ${status}: $(body_excerpt)"
  fi

  status="$(request "${API_URL}/webhooks/kofi")"
  if [[ "${status}" == 404 ]]; then
    pass 'GET /webhooks/kofi is not routed'
  else
    fail "GET /webhooks/kofi answered ${status}"
  fi

  status="$(request --request POST --header "apikey: ${publishable_key}" --data 'data=x' "${API_URL}/rest/v1/rpc/kofi_webhook")"
  if [[ "${status}" == 404 ]] && grep -q 'webhooks' "${body_file}"; then
    pass '/rest/v1/rpc/kofi_webhook is blocked at the gateway'
  else
    fail "/rest/v1/rpc/kofi_webhook answered ${status}: $(body_excerpt)"
  fi

  kofi_token="$(read_env_value KOFI_VERIFICATION_TOKEN 2>/dev/null || true)"
  if [[ -n "${kofi_token}" ]]; then
    kofi_payload='{"verification_token":"'"${kofi_token}"'","message_id":"'"${kofi_probe_id}"'","timestamp":"'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'","type":"Donation","is_public":false,"from_name":"validate","message":"validate","amount":"1.00","url":"https://ko-fi.com/validate","email":"validate-'"${kofi_probe_id}"'@pocketpass.test","currency":"USD","is_subscription_payment":false,"is_first_subscription_payment":false,"kofi_transaction_id":"validate-'"${kofi_probe_id}"'","shop_items":null,"tier_name":null,"shipping":null}'
    status="$(request --request POST --data-urlencode "data=${kofi_payload}" "${API_URL}/webhooks/kofi")"
    if [[ "${status}" == 200 ]] && grep -q '"received": *true' "${body_file}" && grep -q '"duplicate": *false' "${body_file}"; then
      pass 'a signed Ko-fi delivery is accepted'
    else
      fail "signed Ko-fi delivery answered ${status}: $(body_excerpt)"
    fi
    assert_db 'the delivery is stored without its verification token' 'Donation|false|false' \
      "select event_type || '|' || qualifies::text || '|' || (payload ? 'verification_token')::text from private.kofi_events where message_id = '${kofi_probe_id}'"
    status="$(request --request POST --data-urlencode "data=${kofi_payload}" "${API_URL}/webhooks/kofi")"
    if [[ "${status}" == 200 ]] && grep -q '"duplicate": *true' "${body_file}"; then
      pass 'a retried Ko-fi delivery is answered as a duplicate'
    else
      fail "retried Ko-fi delivery answered ${status}: $(body_excerpt)"
    fi
    db_scalar "delete from private.kofi_events where message_id = '${kofi_probe_id}'" >/dev/null
    assert_db 'the validation delivery was removed' 0 \
      "select count(*) from private.kofi_events where message_id = '${kofi_probe_id}'"
  else
    printf 'SKIP  KOFI_VERIFICATION_TOKEN is empty; the signed Ko-fi delivery probe was skipped\n'
  fi

  printf 'Static pages\n'
  status="$(request "${LINKS_URL}/oauth/consent")"
  if [[ "${status}" == 200 ]] && grep -qi '^content-security-policy:' "${headers_file}"; then
    pass '/oauth/consent is served with a CSP'
  else
    fail "${LINKS_URL}/oauth/consent answered ${status}"
  fi

  status="$(request "${LINKS_URL}/oauth/apps")"
  if [[ "${status}" == 200 ]]; then
    pass '/oauth/apps is served'
  else
    fail "${LINKS_URL}/oauth/apps answered ${status}"
  fi

  status="$(request "${LINKS_URL}/oauth/config.js")"
  if [[ "${status}" == 200 ]] && grep -q "POCKETPASS_OAUTH_KEY='sb_publishable_" "${body_file}"; then
    pass '/oauth/config.js carries the publishable key'
  else
    fail "${LINKS_URL}/oauth/config.js answered ${status}"
  fi

  status="$(request "${LINKS_URL}/")"
  if [[ "${status}" == 302 ]]; then
    pass 'links root redirects'
  else
    fail "${LINKS_URL}/ answered ${status}"
  fi

  status="$(request "${DEVELOPER_URL}/config.js")"
  if [[ "${status}" == 200 ]] && grep -q "POCKETPASS_DEVELOPER_KEY='sb_publishable_" "${body_file}"; then
    pass 'developer /config.js carries the publishable key'
  else
    fail "${DEVELOPER_URL}/config.js answered ${status}"
  fi

  status="$(request "${DEVELOPER_URL}/docs")"
  if [[ "${status}" == 200 ]]; then
    pass 'developer /docs is served'
  else
    fail "${DEVELOPER_URL}/docs answered ${status}"
  fi

  printf 'Connected-app token\n'
  if [[ -n "${CLIENT_TOKEN:-}" ]]; then
    payload="$(jwt_payload "${CLIENT_TOKEN}")"
    if grep -q '"role":"api_client"' <<<"${payload}" && grep -q '"client_id"' <<<"${payload}"; then
      pass 'CLIENT_TOKEN carries role api_client and client_id'
    else
      fail 'CLIENT_TOKEN is not a hooked connected-app token'
    fi
    unset payload

    status="$(
      request --request POST \
        --header "Authorization: Bearer ${CLIENT_TOKEN}" \
        --header 'Content-Type: application/json' \
        --data '{}' \
        "${API_URL}/v1/session.get"
    )"
    if [[ "${status}" == 200 ]] && grep -q '"user_id"' "${body_file}"; then
      pass 'POST /v1/session.get answers the connected-app token'
    else
      fail "POST /v1/session.get answered ${status}: $(body_excerpt)"
    fi

    expect_forbidden GET /auth/v1/user "Bearer ${CLIENT_TOKEN}"

    if [[ -n "${CLIENT_REFRESH_TOKEN:-}" ]]; then
      status="$(
        request --request POST \
          --header "apikey: ${publishable_key}" \
          --header 'Content-Type: application/json' \
          --data "{\"refresh_token\":\"${CLIENT_REFRESH_TOKEN}\"}" \
          "${API_URL}/auth/v1/token?grant_type=refresh_token"
      )"
      if [[ "${status}" != 200 ]]; then
        pass "first-party refresh_token grant refuses a connected-app refresh token (${status})"
      elif grep -q '"role":"api_client"' <<<"$(jwt_payload "$(json_string access_token)")"; then
        pass 'first-party refresh_token grant keeps the connected-app token scoped'
      else
        fail 'a connected-app refresh token was laundered into a first-party session'
      fi
    else
      warn 'CLIENT_REFRESH_TOKEN is not set; skipping the refresh-laundering check'
    fi

    status="$(
      request --request GET \
        --header "apikey: ${publishable_key}" \
        --header "Authorization: Bearer ${CLIENT_TOKEN}" \
        "${API_URL}/rest/v1/profiles?select=user_id&limit=1"
    )"
    if [[ "${status}" != 200 ]]; then
      pass "GET /rest/v1/profiles refuses the connected-app token (${status})"
    else
      fail 'GET /rest/v1/profiles answered 200 to a connected-app token'
    fi

    status="$(
      request --request POST \
        --header "Authorization: Bearer ${CLIENT_TOKEN}" \
        --header 'Content-Type: application/json' \
        --data '{}' \
        "${API_URL}/v1/me.get"
    )"
    avatar_path="$(json_string avatar_path)"
    if [[ "${status}" == 200 && -n "${avatar_path}" ]]; then
      status="$(
        request --request POST \
          --header "Authorization: Bearer ${CLIENT_TOKEN}" \
          --header 'Content-Type: application/json' \
          --data '{"expiresIn":315360000}' \
          "${API_URL}/storage/v1/object/sign/avatars/${avatar_path}"
      )"
      signed_url="$(json_string signedURL)"
      signed_payload="$(jwt_payload "${signed_url#*token=}")"
      signed_iat="$(jwt_number_claim "${signed_payload}" iat)"
      signed_exp="$(jwt_number_claim "${signed_payload}" exp)"
      if [[ "${status}" == 200 && -n "${signed_iat}" && -n "${signed_exp}" ]] \
        && ((signed_exp - signed_iat <= 900)); then
        pass "signed avatar URL TTL is clamped to $((signed_exp - signed_iat)) seconds"
      else
        fail "signed URL TTL clamp failed (${status}, iat ${signed_iat:-none}, exp ${signed_exp:-none})"
      fi
    else
      warn "skipping the signed-URL TTL check (me.get answered ${status} with avatar_path '${avatar_path}')"
    fi

    status="$(
      request --request POST \
        --header "Authorization: Bearer ${CLIENT_TOKEN}" \
        --header 'Content-Type: application/json' \
        --data '{}' \
        "${API_URL}/v1/friends.requests_list"
    )"
    if [[ "${status}" == 200 ]] && grep -q '"incoming"' "${body_file}"; then
      pass 'POST /v1/friends.requests_list answers the connected-app token'
    elif [[ "${status}" == 403 ]] && grep -q 'SCOPE_REQUIRED' "${body_file}"; then
      pass 'POST /v1/friends.requests_list is scope-gated for this app'
    else
      fail "POST /v1/friends.requests_list answered ${status}: $(body_excerpt)"
    fi

    status="$(
      request --request POST \
        --header "Authorization: Bearer ${CLIENT_TOKEN}" \
        --header 'Content-Type: image/png' \
        --data-binary 'not-an-image' \
        "${API_URL}/storage/v1/object/message-media/00000000-0000-4000-8000-000000000000/00000000-0000-4000-8000-000000000000/probe.png"
    )"
    if [[ "${status}" != 200 && "${status}" != 201 ]]; then
      pass "message-media upload outside the user folder is refused (${status})"
    else
      fail 'message-media upload outside the user folder was accepted'
    fi
  else
    warn 'CLIENT_TOKEN is not set; skipping the connected-app checks'
  fi
fi

unset publishable_key secret_key

if ((failed != 0)); then
  exit 1
fi

printf '\nPublic API production checks passed.\n'
