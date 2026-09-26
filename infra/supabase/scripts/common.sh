#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
INFRA_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
ENV_FILE="${ENV_FILE:-${INFRA_DIR}/.env.production}"
OVERLAY_COMPOSE_FILE="${INFRA_DIR}/compose.production.yml"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

warn() {
  printf 'warning: %s\n' "$*" >&2
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

read_env_value() {
  local key="$1"
  local value

  [[ -f "${ENV_FILE}" ]] || die "environment file not found: ${ENV_FILE}"

  value="$(
    awk -v wanted="${key}" '
      index($0, wanted "=") == 1 {
        sub("^[^=]*=", "")
        gsub("\r$", "")
        print
        exit
      }
    ' "${ENV_FILE}"
  )"

  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi

  printf '%s' "${value}"
}

env_or_file() {
  local key="$1"
  local current="${!key:-}"
  if [[ -n "${current}" ]]; then
    printf '%s' "${current}"
  else
    read_env_value "${key}"
  fi
}

require_real_value() {
  local key="$1"
  local value
  value="$(env_or_file "${key}")"
  [[ -n "${value}" ]] || die "${key} is empty"
  [[ "${value}" != REPLACE_ME_* ]] || die "${key} still contains a placeholder"
  printf '%s' "${value}"
}

SUPABASE_UPSTREAM_DIR="$(
  env_or_file SUPABASE_UPSTREAM_DIR
)"
UPSTREAM_COMPOSE_FILE="${SUPABASE_UPSTREAM_DIR}/docker-compose.yml"
PG17_COMPOSE_FILE="${SUPABASE_UPSTREAM_DIR}/docker-compose.pg17.yml"
KONG_COMPOSE_FILE="${SUPABASE_UPSTREAM_DIR}/docker-compose.kong.yml"

[[ -f "${UPSTREAM_COMPOSE_FILE}" ]] \
  || die "upstream compose file not found: ${UPSTREAM_COMPOSE_FILE}"
[[ -f "${PG17_COMPOSE_FILE}" ]] \
  || die "upstream PG17 override not found: ${PG17_COMPOSE_FILE}"
[[ -f "${KONG_COMPOSE_FILE}" ]] \
  || die "upstream Kong override not found: ${KONG_COMPOSE_FILE}"
[[ -f "${OVERLAY_COMPOSE_FILE}" ]] \
  || die "production overlay not found: ${OVERLAY_COMPOSE_FILE}"

compose() {
  docker compose \
    --env-file "${ENV_FILE}" \
    -f "${UPSTREAM_COMPOSE_FILE}" \
    -f "${PG17_COMPOSE_FILE}" \
    -f "${KONG_COMPOSE_FILE}" \
    -f "${OVERLAY_COMPOSE_FILE}" \
    "$@"
}

verify_upstream_pin() {
  local expected_ref actual_ref repository_dir
  expected_ref="$(env_or_file SUPABASE_UPSTREAM_REF)"
  repository_dir="$(cd -- "${SUPABASE_UPSTREAM_DIR}/.." && pwd -P)"

  [[ -n "${expected_ref}" ]] || die "SUPABASE_UPSTREAM_REF is empty"
  [[ "${expected_ref}" != REPLACE_ME_* ]] \
    || die "SUPABASE_UPSTREAM_REF still contains a placeholder"

  if git -C "${repository_dir}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    actual_ref="$(git -C "${repository_dir}" rev-parse HEAD)"
    [[ "${actual_ref}" == "${expected_ref}" ]] \
      || die "upstream checkout ${actual_ref} does not match pin ${expected_ref}"
  else
    warn "upstream directory is not a Git checkout; pin cannot be verified"
  fi
}

validate_compose() {
  require_command docker
  verify_upstream_pin
  compose config --quiet
}
