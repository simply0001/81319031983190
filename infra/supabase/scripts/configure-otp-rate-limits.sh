#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
INFRA_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
ENV_FILE="${ENV_FILE:-${INFRA_DIR}/.env.production}"

[[ -f "${ENV_FILE}" ]] || {
  printf 'error: environment file not found: %s\n' "${ENV_FILE}" >&2
  exit 1
}

set_env() {
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

remove_env() {
  local key="$1"
  local temporary

  temporary="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"
  awk -v unwanted="${key}" 'index($0, unwanted "=") != 1 { print }' \
    "${ENV_FILE}" >"${temporary}"
  chmod 600 "${temporary}"
  mv -f "${temporary}" "${ENV_FILE}"
}

set_env DISABLE_SIGNUP false
set_env ENABLE_EMAIL_SIGNUP true
set_env EMAIL_OTP_EXPIRY_SECONDS 600
set_env SMTP_MAX_FREQUENCY 60s
set_env AUTH_EMAILS_PER_HOUR 1000

for obsolete_key in \
  CAPTCHA_ENABLED \
  CAPTCHA_PROVIDER \
  CAPTCHA_TIMEOUT \
  TURNSTILE_SITE_KEY \
  TURNSTILE_SECRET
do
  remove_env "${obsolete_key}"
done

printf 'Direct email OTP enabled with 60-second and 1000-per-hour Auth limits.\n'
printf 'Kong separately enforces 10/minute and 60/hour per client IP.\n'
