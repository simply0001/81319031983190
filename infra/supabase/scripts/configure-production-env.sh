#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

BASE_ENV="${BASE_ENV:-/opt/pocketpass/supabase-upstream/docker/.env}"
OUTPUT_ENV="${OUTPUT_ENV:-/opt/pocketpass/app/infra/supabase/.env.production}"

[[ "$#" == 1 && "$1" == --bootstrap ]] || {
  printf 'usage: %s --bootstrap\n' "$0" >&2
  printf 'Creates a new %s from %s. To change one key of a live file, edit that line in place (see the README).\n' "${OUTPUT_ENV}" "${BASE_ENV}" >&2
  exit 2
}
[[ ! -e "${OUTPUT_ENV}" ]] || {
  printf 'error: %s already exists; this script never replaces it\n' "${OUTPUT_ENV}" >&2
  exit 1
}

RESEND_SECRET_FILE="${RESEND_SECRET_FILE:?RESEND_SECRET_FILE is required}"
BACKUP_AGE_RECIPIENT="${BACKUP_AGE_RECIPIENT:?BACKUP_AGE_RECIPIENT is required}"
PUBLIC_API_ENABLED="${PUBLIC_API_ENABLED:-true}"

[[ -f "${BASE_ENV}" ]] || {
  printf 'error: base environment not found: %s\n' "${BASE_ENV}" >&2
  exit 1
}
[[ -f "${RESEND_SECRET_FILE}" ]] || {
  printf 'error: Resend secret file not found: %s\n' "${RESEND_SECRET_FILE}" >&2
  exit 1
}
[[ "${BACKUP_AGE_RECIPIENT}" =~ ^age1[0-9a-z]+$ ]] || {
  printf 'error: invalid age recipient\n' >&2
  exit 1
}
[[ "${PUBLIC_API_ENABLED}" == true || "${PUBLIC_API_ENABLED}" == false ]] || {
  printf 'error: PUBLIC_API_ENABLED must be true or false\n' >&2
  exit 1
}

( set -o noclobber; cat -- "${BASE_ENV}" >"${OUTPUT_ENV}" ) || {
  printf 'error: could not create %s\n' "${OUTPUT_ENV}" >&2
  exit 1
}
chmod 600 "${OUTPUT_ENV}"

set_env() {
  local key="$1"
  local value="$2"
  local temporary

  [[ "${value}" != *$'\n'* ]] || {
    printf 'error: %s contains a newline\n' "${key}" >&2
    exit 1
  }

  temporary="$(mktemp "${OUTPUT_ENV}.tmp.XXXXXX")"
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
  ' "${OUTPUT_ENV}" >"${temporary}"
  chmod 600 "${temporary}"
  mv -f "${temporary}" "${OUTPUT_ENV}"
}

resend_secret="$(tr -d '\r\n' <"${RESEND_SECRET_FILE}")"
[[ ${#resend_secret} -ge 20 ]] || {
  printf 'error: Resend secret is unexpectedly short\n' >&2
  exit 1
}

set_env COMPOSE_PROJECT_NAME pocketpass
set_env POCKETPASS_INFRA_DIR /opt/pocketpass/app/infra/supabase
set_env SUPABASE_UPSTREAM_DIR /opt/pocketpass/supabase-upstream/docker
set_env SUPABASE_UPSTREAM_REF 241bb11c0627f2981746d37033f57dbfa81d29b0
set_env CADDY_IMAGE 'caddy:2.11.4-alpine@sha256:5f5c8640aae01df9654968d946d8f1a56c497f1dd5c5cda4cf95ab7c14d58648'

set_env SUPABASE_PUBLIC_URL https://api.pocketpass.xyz
set_env API_EXTERNAL_URL https://api.pocketpass.xyz/auth/v1
set_env SITE_URL https://links.pocketpass.xyz
set_env ADDITIONAL_REDIRECT_URLS 'https://links.pocketpass.xyz/auth/callback,pocketpass://auth/callback,https://developer.pocketpass.xyz/'
set_env PROXY_DOMAIN api.pocketpass.xyz
set_env ACME_EMAIL no-reply@pocketpass.xyz
set_env PUBLIC_API_ENABLED "${PUBLIC_API_ENABLED}"

set_env DISABLE_SIGNUP false
set_env ENABLE_EMAIL_SIGNUP true
set_env ENABLE_EMAIL_AUTOCONFIRM true
set_env PASSWORD_MIN_LENGTH 8
set_env ENABLE_ANONYMOUS_USERS false
set_env ENABLE_PHONE_SIGNUP false
set_env ENABLE_PHONE_AUTOCONFIRM false
set_env EMAIL_OTP_EXPIRY_SECONDS 600
set_env EMAIL_OTP_TEMPLATE_URL https://links.pocketpass.xyz/auth-email/otp.html
set_env EMAIL_OTP_SUBJECT 'Your PocketPass verification code'
set_env SMTP_MAX_FREQUENCY 60s
set_env AUTH_EMAILS_PER_HOUR 1000

set_env SMTP_ADMIN_EMAIL no-reply@pocketpass.xyz
set_env SMTP_HOST smtp.resend.com
set_env SMTP_PORT 465
set_env SMTP_USER resend
set_env SMTP_PASS "${resend_secret}"
set_env SMTP_SENDER_NAME PocketPass

set_env BACKUP_DIRECTORY /var/backups/pocketpass
set_env BACKUP_AGE_RECIPIENT "${BACKUP_AGE_RECIPIENT}"
set_env BACKUP_RETENTION_DAYS 7

unset resend_secret
chmod 600 "${OUTPUT_ENV}"

required_keys=(
  POSTGRES_PASSWORD
  JWT_SECRET
  ANON_KEY
  SERVICE_ROLE_KEY
  SUPABASE_PUBLISHABLE_KEY
  SUPABASE_SECRET_KEY
  JWT_KEYS
  JWT_JWKS
  SECRET_KEY_BASE
  REALTIME_DB_ENC_KEY
  VAULT_ENC_KEY
  PG_META_CRYPTO_KEY
  SMTP_PASS
)

for key in "${required_keys[@]}"; do
  value="$(
    awk -v wanted="${key}" '
      index($0, wanted "=") == 1 {
        sub("^[^=]*=", "")
        print
        exit
      }
    ' "${OUTPUT_ENV}"
  )"
  [[ -n "${value}" && "${value}" != REPLACE_ME_* ]] || {
    printf 'error: %s is empty or still a placeholder\n' "${key}" >&2
    exit 1
  }
done

printf 'Production environment created at %s (mode 600).\n' "${OUTPUT_ENV}"
printf 'Next: scripts/configure-discord-oauth.sh, then the Ko-fi, webhook and Firebase keys from .env.production.example.\n'
