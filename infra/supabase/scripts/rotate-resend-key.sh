#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

ENV_FILE="${ENV_FILE:-/opt/pocketpass/app/infra/supabase/.env.production}"
EXPORT_FILE="${EXPORT_FILE:-/home/ubuntu/.pocketpass-resend-new.tmp}"
API_BASE="https://api.resend.com"

[[ -f "${ENV_FILE}" ]] || {
  printf 'error: production environment not found\n' >&2
  exit 1
}
command -v python3 >/dev/null 2>&1 || {
  printf 'error: python3 is required for verified SMTP authentication\n' >&2
  exit 1
}

read_env_value() {
  local key="$1"
  awk -v wanted="${key}" '
    index($0, wanted "=") == 1 {
      sub("^[^=]*=", "")
      print
      exit
    }
  ' "${ENV_FILE}"
}

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
  unset old_key new_key
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
  curl --silent --show-error \
    --output "${work_directory}/created.json" \
    --write-out '%{http_code}' \
    --request POST \
    --header "Authorization: Bearer ${old_key}" \
    --header 'Content-Type: application/json' \
    --data '{"name":"PocketPass Production SMTP 2026-07-27","permission":"sending_access"}' \
    "${API_BASE}/api-keys"
)"
[[ "${create_status}" == "201" || "${create_status}" == "200" ]] || {
  printf 'error: key creation failed (HTTP %s)\n' "${create_status}" >&2
  exit 1
}

new_id="$(jq -r '.id // empty' "${work_directory}/created.json")"
new_key="$(jq -r '.token // empty' "${work_directory}/created.json")"
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
  printf 'error: new key failed verified SMTP authentication on port 465\n' >&2
  exit 1
fi

set_env_value SMTP_PASS "${new_key}"
printf '%s' "${new_key}" >"${EXPORT_FILE}"
chmod 600 "${EXPORT_FILE}"

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

printf 'Resend key rotated; SMTP verified; previous_key_count=%s; old_key_revoked=%s\n' \
  "${old_count}" "${revoked}"
