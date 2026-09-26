#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SECRET_FILE="${SECRET_FILE:?SECRET_FILE is required}"
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
    --header "Authorization: Bearer ${key}" \
    https://api.resend.com/api-keys
)"
[[ "${status}" == "200" ]] || {
  printf 'error: list request failed (HTTP %s)\n' "${status}" >&2
  exit 1
}

jq -c '[.data[] | {id, name, created_at, last_used_at}]' "${response}"
