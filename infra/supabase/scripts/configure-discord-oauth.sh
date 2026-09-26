#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

OUTPUT_ENV="${OUTPUT_ENV:-/opt/pocketpass/app/infra/supabase/.env.production}"
CREDENTIAL_FILE="${CREDENTIAL_FILE:?CREDENTIAL_FILE is required}"
DISCORD_CALLBACK_URL="${DISCORD_CALLBACK_URL:-https://api.pocketpass.xyz/auth/v1/callback}"

[[ -f "${OUTPUT_ENV}" ]] || {
  printf 'error: production environment not found: %s\n' "${OUTPUT_ENV}" >&2
  exit 1
}
[[ -f "${CREDENTIAL_FILE}" ]] || {
  printf 'error: Discord credential file not found: %s\n' "${CREDENTIAL_FILE}" >&2
  exit 1
}

read_credential() {
  local key="$1"
  local count
  local value

  count="$(
    awk -v wanted="${key}" '
      index($0, wanted "=") == 1 {
        count += 1
      }
      END {
        print count + 0
      }
    ' "${CREDENTIAL_FILE}"
  )"
  [[ "${count}" == 1 ]] || {
    printf 'error: expected exactly one %s entry\n' "${key}" >&2
    exit 1
  }

  value="$(
    awk -v wanted="${key}" '
      index($0, wanted "=") == 1 {
        sub("^[^=]*=", "")
        sub("\r$", "")
        print
        exit
      }
    ' "${CREDENTIAL_FILE}"
  )"
  printf '%s' "${value}"
}

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

discord_client_id="$(read_credential DISCORD_CLIENT_ID)"
discord_client_secret="$(read_credential DISCORD_CLIENT_SECRET)"

[[ "${discord_client_id}" =~ ^[0-9]{15,25}$ ]] || {
  printf 'error: Discord client ID is invalid\n' >&2
  exit 1
}
[[ ${#discord_client_secret} -ge 20 ]] || {
  printf 'error: Discord client secret is unexpectedly short\n' >&2
  exit 1
}

set_env DISCORD_ENABLED true
set_env DISCORD_CLIENT_ID "${discord_client_id}"
set_env DISCORD_CLIENT_SECRET "${discord_client_secret}"
set_env DISCORD_REDIRECT_URI "${DISCORD_CALLBACK_URL}"

unset discord_client_id
unset discord_client_secret
chmod 600 "${OUTPUT_ENV}"

printf 'Discord OAuth enabled in %s (mode 600).\n' "${OUTPUT_ENV}"
