#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SECRET_FILE="${SECRET_FILE:?SECRET_FILE is required}"
KEY_ID="${KEY_ID:?KEY_ID is required}"
[[ "${KEY_ID}" =~ ^[0-9a-f-]{36}$ ]] || {
  printf 'error: invalid key id\n' >&2
  exit 1
}

key="$(tr -d '\r\n' <"${SECRET_FILE}")"
[[ "${key}" == re_* ]] || {
  printf 'error: invalid Resend key format\n' >&2
  exit 1
}

response="$(mktemp)"
trap 'rm -f -- "${response}"; unset key' EXIT

status="$(
  curl --silent --show-error \
    --output "${response}" \
    --write-out '%{http_code}' \
    --request DELETE \
    --header "Authorization: Bearer ${key}" \
    "https://api.resend.com/api-keys/${KEY_ID}"
)"
[[ "${status}" == "200" || "${status}" == "204" ]] || {
  printf 'error: revocation failed (HTTP %s)\n' "${status}" >&2
  exit 1
}

printf 'Resend API key revoked: %s\n' "${KEY_ID}"
