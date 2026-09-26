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

set_env EMAIL_OTP_EXPIRY_SECONDS 600
set_env EMAIL_OTP_TEMPLATE_URL https://links.pocketpass.xyz/auth-email/otp.html
set_env EMAIL_OTP_SUBJECT 'Your PocketPass verification code'
set_env SMTP_MAX_FREQUENCY 60s
set_env AUTH_EMAILS_PER_HOUR 1000
set_env DISABLE_SIGNUP false
set_env ENABLE_EMAIL_SIGNUP true
set_env ENABLE_EMAIL_AUTOCONFIRM true
set_env PASSWORD_MIN_LENGTH 8

printf 'Email code and username sign-up enabled with gateway and Auth rate limits.\n'
