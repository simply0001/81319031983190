#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ENV_FILE="${ENV_FILE:-/opt/pocketpass/app/infra/supabase/.env.production}"
API_BASE="https://api.resend.com"
KEY_NAME="PocketPass Production SMTP $(date -u +%F)"

[[ -f "${ENV_FILE}" ]] || {
  printf 'error: production environment not found\n' >&2
  exit 1
}
command -v python3 >/dev/null 2>&1 || {
  printf 'error: python3 is required for verified SMTP authentication\n' >&2
  exit 1
}
command -v jq >/dev/null 2>&1 || {
  printf 'error: jq is required\n' >&2
  exit 1
}

source "${SCRIPT_DIR}/common.sh"
require_command curl
require_command docker
validate_compose

set_env_value() {
  local key="$1"
  local value="$2"
  local temporary

  temporary="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"
  awk -v wanted="${key}" -v replacement="${key}=${value}" '
    BEGIN { written = 0 }
    index($0, wanted "=") == 1 {
      if (!written) {
        print replacement
        written = 1
      }
      next
    }
    { print }
    END {
      if (!written) {
        print replacement
      }
    }
  ' "${ENV_FILE}" >"${temporary}"
  chmod 600 "${temporary}"
  mv -f "${temporary}" "${ENV_FILE}"
}

old_key="$(read_env_value SMTP_PASS)"
[[ "${old_key}" == re_* ]] || {
  printf 'error: current Resend key has an unexpected format\n' >&2
  exit 1
}

work_directory="$(mktemp -d /home/ubuntu/.resend-rotate.XXXXXX)"
cleanup() {
  rm -rf -- "${work_directory}"
  unset old_key new_key running_key
}
trap cleanup EXIT

list_status="$(
  curl --silent --show-error \
    --output "${work_directory}/keys.json" \
    --write-out '%{http_code}' \
    --header "Authorization: Bearer ${old_key}" \
    "${API_BASE}/api-keys"
)"
[[ "${list_status}" == "200" ]] || {
  printf 'error: current key cannot list keys (HTTP %s); rotate in the Resend dashboard\n' \
    "${list_status}" >&2
  exit 2
}

old_count="$(jq -r '.data | length' "${work_directory}/keys.json")"
[[ "${old_count}" =~ ^[0-9]+$ ]] || {
  printf 'error: invalid API-key list response\n' >&2
  exit 1
}

create_status="$(
  jq -n --arg name "${KEY_NAME}" '{name: $name, permission: "sending_access"}' \
    | curl --silent --show-error \
        --output "${work_directory}/created.json" \
        --write-out '%{http_code}' \
        --request POST \
        --header "Authorization: Bearer ${old_key}" \
        --header 'Content-Type: application/json' \
        --data-binary @- \
        "${API_BASE}/api-keys"
)"
[[ "${create_status}" == "201" || "${create_status}" == "200" ]] || {
  printf 'error: key creation failed (HTTP %s)\n' "${create_status}" >&2
  exit 1
}

new_id="$(jq -r '.id // empty' "${work_directory}/created.json")"
new_key="$(jq -r '.token // empty' "${work_directory}/created.json")"
rm -f -- "${work_directory}/created.json"
[[ "${new_id}" =~ ^[0-9a-f-]{36}$ && "${new_key}" == re_* ]] || {
  printf 'error: invalid key-creation response\n' >&2
  exit 1
}

if ! printf '%s' "${new_key}" | python3 -c '
import smtplib
import ssl
import sys

try:
    with smtplib.SMTP_SSL(
        "smtp.resend.com", 465, timeout=20, context=ssl.create_default_context()
    ) as smtp:
        smtp.login("resend", sys.stdin.read())
except Exception:
    sys.exit(1)
'; then
  printf 'error: new key %s failed verified SMTP authentication on port 465; SMTP_PASS was not changed\n' \
    "${new_id}" >&2
  exit 1
fi

set_env_value SMTP_PASS "${new_key}"

keep_old_key() {
  printf 'error: %s; Auth may be using the new key %s, and the old key was kept\n' "$1" "${new_id}" >&2
  exit 4
}

printf 'Recreating Auth with the new key...\n'
compose up -d --no-deps --force-recreate --wait auth \
  || keep_old_key 'Auth did not come back after the recreate'
docker restart supabase-kong >/dev/null \
  || keep_old_key 'Kong did not restart'
gateway_state=""
for _ in $(seq 1 30); do
  gateway_state="$(
    docker inspect \
      --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
      supabase-kong 2>/dev/null || true
  )"
  [[ "${gateway_state}" == healthy || "${gateway_state}" == running ]] && break
  sleep 2
done
[[ "${gateway_state}" == healthy || "${gateway_state}" == running ]] \
  || keep_old_key "Kong is ${gateway_state:-unknown} after the restart"

running_key="$(compose exec -T auth printenv GOTRUE_SMTP_PASS </dev/null | tr -d '\r' || true)"
[[ "${running_key}" == "${new_key}" ]] \
  || keep_old_key 'Auth is not running with the new key'
unset running_key

ENV_FILE="${ENV_FILE}" "${SCRIPT_DIR}/validate-auth-production.sh" \
  || keep_old_key 'validate-auth-production.sh failed'

revoked=false
if [[ "${old_count}" == "1" ]]; then
  old_id="$(jq -r '.data[0].id // empty' "${work_directory}/keys.json")"
  if [[ "${old_id}" =~ ^[0-9a-f-]{36}$ && "${old_id}" != "${new_id}" ]]; then
    delete_status="$(
      curl --silent --show-error \
        --output "${work_directory}/deleted.json" \
        --write-out '%{http_code}' \
        --request DELETE \
        --header "Authorization: Bearer ${old_key}" \
        "${API_BASE}/api-keys/${old_id}"
    )"
    if [[ "${delete_status}" == "200" || "${delete_status}" == "204" ]]; then
      revoked=true
    else
      printf 'error: new key is active but old-key revocation failed (HTTP %s)\n' \
        "${delete_status}" >&2
      exit 3
    fi
  fi
fi

printf 'Resend key rotated to "%s"; Auth recreated and validated; previous_key_count=%s; old_key_revoked=%s\n' \
  "${KEY_NAME}" "${old_count}" "${revoked}"
