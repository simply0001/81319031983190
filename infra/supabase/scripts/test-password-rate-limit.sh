#!/usr/bin/env bash

set -Eeuo pipefail

KONG_CONTAINER="${KONG_CONTAINER:-supabase-kong}"
CADDY_CONTAINER="${CADDY_CONTAINER:-pocketpass-caddy-1}"

publishable_key="$(
  sudo docker exec "${KONG_CONTAINER}" printenv SUPABASE_PUBLISHABLE_KEY
)"
[[ -n "${publishable_key}" ]] || {
  printf 'error: Kong publishable key is unavailable\n' >&2
  exit 1
}

primary_ip="198.51.100.$((($$ % 200) + 1))"
independent_ip="203.0.113.$(((($$ + 71) % 200) + 1))"

request_status() {
  local client_ip="$1"
  local output

  output="$(
    sudo docker exec "${CADDY_CONTAINER}" wget -S -O /dev/null \
      --header="apikey: ${publishable_key}" \
      --header="Content-Type: application/json" \
      --header="X-PocketPass-Client-IP: ${client_ip}" \
      --post-data='{"email":"limit.probe@users.pocketpass.xyz","password":"not-a-real-password"}' \
      'http://kong:8000/auth/v1/token?grant_type=password' 2>&1 || true
  )"
  sed -n 's/.*HTTP\/1\.1 \([0-9][0-9][0-9]\).*/\1/p' \
    <<<"${output}" | tail -n 1
}

for attempt in 1 2 3 4 5 6 7 8 9 10; do
  status="$(request_status "${primary_ip}")"
  [[ "${status}" != 429 ]] || {
    printf 'error: primary IP was limited before request %s\n' "${attempt}" >&2
    exit 1
  }
done

eleventh_status="$(request_status "${primary_ip}")"
[[ "${eleventh_status}" == 429 ]] || {
  printf 'error: eleventh primary-IP request returned %s instead of 429\n' \
    "${eleventh_status}" >&2
  exit 1
}

independent_status="$(request_status "${independent_ip}")"
[[ "${independent_status}" != 429 ]] || {
  printf 'error: independent IP incorrectly shared the primary allowance\n' >&2
  exit 1
}

unset publishable_key
printf 'Kong password sign-in limiter passed: first ten allowed, eleventh 429, independent IP allowed.\n'
