#!/usr/bin/env bash

set -Eeuo pipefail

ENV_FILE="${ENV_FILE:-/opt/pocketpass/app/infra/supabase/.env.production}"
INFRA_DIR="${INFRA_DIR:-/opt/pocketpass/app/infra/supabase}"
API_BASE="${API_BASE:-https://api.pocketpass.xyz}"
LINKS_BASE="${LINKS_BASE:-https://links.pocketpass.xyz}"

[[ "${API_BASE}" == https://* && "${LINKS_BASE}" == https://* ]] \
  || { printf 'error: production auth checks require HTTPS endpoints\n' >&2; exit 1; }

fail() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

for required_file in \
  "${ENV_FILE}" \
  "${INFRA_DIR}/kong/kong.yml" \
  "${INFRA_DIR}/templates/auth-email/otp.html"; do
  [[ -f "${required_file}" ]] || fail "required file not found: ${required_file}"
done

auth_health=""
for _ in $(seq 1 20); do
  auth_health="$(
    sudo docker inspect \
      --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' \
      supabase-auth
  )"
  [[ "${auth_health}" == healthy ]] && break
  sleep 2
done
[[ "${auth_health}" == healthy ]] || fail "Supabase Auth health is ${auth_health}"

auth_environment="$(
  sudo docker inspect \
    --format '{{range .Config.Env}}{{println .}}{{end}}' \
    supabase-auth
)"

assert_env() {
  grep -qx "$1" <<<"${auth_environment}" || fail "Auth is missing ${1}"
}

assert_env 'GOTRUE_SECURITY_CAPTCHA_ENABLED=false'
assert_env 'GOTRUE_DISABLE_SIGNUP=false'
assert_env 'GOTRUE_EXTERNAL_EMAIL_ENABLED=true'
assert_env 'GOTRUE_MAILER_AUTOCONFIRM=true'
assert_env 'GOTRUE_PASSWORD_MIN_LENGTH=8'
assert_env 'GOTRUE_MAILER_SECURE_EMAIL_CHANGE_ENABLED=false'
assert_env 'GOTRUE_SECURITY_UPDATE_PASSWORD_REQUIRE_REAUTHENTICATION=true'
assert_env 'GOTRUE_SMTP_MAX_FREQUENCY=60s'
assert_env 'GOTRUE_RATE_LIMIT_EMAIL_SENT=1000'
assert_env 'GOTRUE_MAILER_SUBJECTS_MAGIC_LINK=Your PocketPass verification code'
assert_env 'GOTRUE_MAILER_SUBJECTS_EMAIL_CHANGE=Your PocketPass verification code'
assert_env 'GOTRUE_MAILER_SUBJECTS_REAUTHENTICATION=Your PocketPass verification code'
assert_env 'GOTRUE_SMTP_HOST=smtp.resend.com'
assert_env 'GOTRUE_SMTP_PORT=465'
assert_env 'GOTRUE_HOOK_BEFORE_USER_CREATED_ENABLED=true'
assert_env 'GOTRUE_HOOK_BEFORE_USER_CREATED_URI=pg-functions://postgres/public/pocketpass_before_user_created'
if sudo grep -qx 'PUBLIC_API_ENABLED=true' "${ENV_FILE}"; then
  assert_env 'GOTRUE_OAUTH_SERVER_DEFAULT_SCOPE=openid'
  assert_env 'GOTRUE_HOOK_CUSTOM_ACCESS_TOKEN_ENABLED=true'
fi
unset auth_environment

command -v openssl >/dev/null 2>&1 || fail 'openssl is required for SMTP TLS verification'
command -v timeout >/dev/null 2>&1 || fail 'timeout is required for SMTP TLS verification'
if ! timeout 15 openssl s_client \
  -connect smtp.resend.com:465 \
  -servername smtp.resend.com \
  -verify_hostname smtp.resend.com \
  -verify_return_error \
  -brief </dev/null >/dev/null 2>&1; then
  fail 'Resend implicit-TLS handshake or certificate verification failed'
fi
if timeout 15 openssl s_client \
  -connect smtp.resend.com:465 \
  -servername smtp.resend.com \
  -verify_hostname invalid.pocketpass.invalid \
  -verify_return_error \
  -brief </dev/null >/dev/null 2>&1; then
  fail 'SMTP TLS verification accepted a mismatched certificate name'
fi

grep -Fq '{{ .Token }}' "${INFRA_DIR}/templates/auth-email/otp.html" \
  || fail 'OTP template does not print the six-digit token'
if grep -Eiq '<a([[:space:]>])|\\.ConfirmationURL' \
  "${INFRA_DIR}/templates/auth-email/otp.html"; then
  fail 'OTP template contains a clickable authentication link'
fi

if sudo grep -Eq '^(TURNSTILE_|CAPTCHA_)' "${ENV_FILE}"; then
  fail "obsolete Turnstile/CAPTCHA values remain in ${ENV_FILE}"
fi

sudo docker exec supabase-kong kong config parse /home/kong/temp.yml >/dev/null

for route_name in \
  auth-v1-otp \
  auth-v1-signup \
  auth-v1-token-password \
  auth-v1-user-update \
  auth-v1-reauthenticate \
  auth-v1-recover-blocked; do
  grep -Fq "name: ${route_name}" "${INFRA_DIR}/kong/kong.yml" \
    || fail "kong.yml is missing the ${route_name} route"
done

source "${INFRA_DIR}/scripts/common.sh"
publishable_key="$(require_real_value SUPABASE_PUBLISHABLE_KEY)"
signup_probe="$(
  curl --silent --show-error \
    --write-out '\n%{http_code}' \
    --request POST \
    --header "apikey: ${publishable_key}" \
    --header 'Content-Type: application/json' \
    --data '{"email":"blocked-probe@example.invalid","password":"x"}' \
    "${API_BASE}/auth/v1/signup"
)"
signup_status="${signup_probe##*$'\n'}"
signup_body="${signup_probe%$'\n'*}"
[[ "${signup_status}" == 400 && "${signup_body}" == *'"username_signup_required"'* ]] \
  || fail 'direct email/password sign-up was not stopped at the gateway'
unset publishable_key signup_probe signup_status signup_body

curl --fail --silent --show-error \
  "${LINKS_BASE}/auth-email/otp.html" >/dev/null
curl --fail --silent --show-error \
  "${LINKS_BASE}/auth-email/assets/pocketpass-leaf.png" >/dev/null
curl --fail --silent --show-error \
  "${LINKS_BASE}/auth-email/assets/pocketpass-pattern.png" >/dev/null
curl --fail --silent --show-error \
  "${API_BASE}/healthz" >/dev/null

recover_status="$(
  curl --silent --show-error \
    --output /dev/null \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data '{"email":"probe@users.pocketpass.xyz"}' \
    "${API_BASE}/auth/v1/recover"
)"
[[ "${recover_status}" == 404 ]] \
  || fail "POST /auth/v1/recover returned ${recover_status} instead of 404"

for removed_path in \
  /auth/challenge \
  /auth/challenge.js \
  /auth/challenge.css \
  /auth/turnstile-callback; do
  status="$(
    curl --silent --show-error \
      --output /dev/null \
      --write-out '%{http_code}' \
      "${LINKS_BASE}${removed_path}"
  )"
  [[ "${status}" == 404 ]] \
    || fail "removed path ${removed_path} returned ${status} instead of 404"
done

printf 'Production auth checks passed: email codes, username accounts, verified SMTP TLS, no recovery, limits configured.\n'
