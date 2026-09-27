#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/common.sh"

archive_path=""
identity_file="${AGE_IDENTITY_FILE:-}"
confirmation=""

usage() {
  cat <<'EOF'
Usage:
  restore.sh --archive /absolute/backup.tar.gz.age \
             --identity /secure/age-identity.txt \
             --confirm RESTORE_POCKETPASS

This destructively replaces the PostgreSQL database and local Storage objects.
The current Storage directory is retained beside it as .pre-restore-TIMESTAMP.
EOF
}

while (($# > 0)); do
  case "$1" in
    --archive)
      archive_path="${2:-}"
      shift 2
      ;;
    --identity)
      identity_file="${2:-}"
      shift 2
      ;;
    --confirm)
      confirmation="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "unknown argument: $1"
      ;;
  esac
done

[[ "${confirmation}" == "RESTORE_POCKETPASS" ]] \
  || die "explicit --confirm RESTORE_POCKETPASS is required"
[[ -f "${archive_path}" ]] || die "backup archive not found: ${archive_path}"
[[ -f "${identity_file}" ]] || die "age identity not found: ${identity_file}"
[[ "${archive_path}" = /* ]] || die "backup archive path must be absolute"
[[ "${identity_file}" = /* ]] || die "identity path must be absolute"

require_command age
require_command docker
require_command sha256sum
require_command tar
validate_compose

checksum_file="${archive_path}.sha256"
if [[ -f "${checksum_file}" ]]; then
  (
    cd -- "$(dirname -- "${archive_path}")"
    sha256sum --check "$(basename -- "${checksum_file}")"
  )
else
  warn "detached SHA-256 file is missing; archive transport integrity is unverified"
fi

temporary_directory="$(mktemp -d)"
cleanup() {
  rm -rf -- "${temporary_directory}"
}
trap cleanup EXIT

bundle_path="${temporary_directory}/bundle.tar.gz"
age --decrypt \
  --identity "${identity_file}" \
  --output "${bundle_path}" \
  "${archive_path}"

if tar -tzf "${bundle_path}" \
  | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
  die "outer backup archive contains an unsafe path"
fi

tar -C "${temporary_directory}" -xzf "${bundle_path}"
rm -f -- "${bundle_path}"

[[ -s "${temporary_directory}/database.dump" ]] \
  || die "backup does not contain database.dump"
[[ -s "${temporary_directory}/checksums.sha256" ]] \
  || die "backup does not contain checksums.sha256"

(
  cd -- "${temporary_directory}"
  sha256sum --check checksums.sha256
)

mapfile -t application_services < <(
  compose config --services | grep -v '^db$'
)

printf 'Stopping application services while the restore runs...\n'
if ((${#application_services[@]} > 0)); then
  compose stop "${application_services[@]}"
fi

if [[ -s "${temporary_directory}/pgsodium_root.key" ]]; then
  printf 'Restoring the encrypted Vault root key...\n'
  compose exec -T --user root db sh -c \
    'umask 077; cat > /etc/postgresql-custom/pgsodium_root.key.restore && mv /etc/postgresql-custom/pgsodium_root.key.restore /etc/postgresql-custom/pgsodium_root.key' \
    <"${temporary_directory}/pgsodium_root.key"
  compose restart db

  for _ in $(seq 1 60); do
    if compose exec -T db pg_isready --username postgres >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  compose exec -T db pg_isready --username postgres >/dev/null \
    || die "database did not become ready after restoring its root key"
fi

POSTGRES_DB="$(env_or_file POSTGRES_DB)"
[[ -n "${POSTGRES_DB}" ]] || POSTGRES_DB="postgres"

roles_file="${temporary_directory}/roles.sql"
[[ -s "${roles_file}" ]] || die "backup does not contain roles.sql"

declare -A existing_roles=()
while IFS= read -r role_name; do
  [[ -n "${role_name}" ]] && existing_roles["${role_name}"]=1
done < <(
  compose exec -T db \
    psql --username postgres --dbname "${POSTGRES_DB}" --tuples-only --no-align \
      --command 'select rolname from pg_catalog.pg_roles' \
    </dev/null | tr -d '\r'
)
((${#existing_roles[@]} > 0)) || die "could not list the database roles"

missing_roles=()
while IFS= read -r role_name; do
  role_name="${role_name//\"/}"
  [[ -n "${existing_roles[${role_name}]:-}" ]] && continue
  [[ "${role_name}" =~ ^[a-z_][a-z0-9_]*$ ]] \
    || die "roles.sql names a missing role that must be created by hand: ${role_name}"
  missing_roles+=("${role_name}")
done < <(sed -n 's/^CREATE ROLE \(.*\);$/\1/p' "${roles_file}")

if ((${#missing_roles[@]} > 0)); then
  printf 'Creating roles the backup grants to: %s\n' "${missing_roles[*]}"
  {
    for role_name in "${missing_roles[@]}"; do
      printf 'create role %s;\n' "${role_name}"
      sed -n -E "/^ALTER ROLE ${role_name} (WITH|SET) /{s/ PASSWORD '[^']*'//;p}" "${roles_file}"
    done
    for role_name in "${missing_roles[@]}"; do
      sed -n -E "/^GRANT (${role_name} TO [a-z0-9_\"]+|[a-z0-9_\"]+ TO ${role_name})[ ;]/{s/ GRANTED BY [^;]+;$/;/;p}" "${roles_file}"
    done
  } | compose exec -T db \
        psql --username postgres --dbname "${POSTGRES_DB}" \
          --quiet --set ON_ERROR_STOP=1
fi

printf 'Replacing PostgreSQL objects and their privileges...\n'
compose exec -T db \
  pg_restore \
    --username postgres \
    --dbname "${POSTGRES_DB}" \
    --clean \
    --if-exists \
    --no-owner \
    --exit-on-error \
  <"${temporary_directory}/database.dump"

storage_directory="${SUPABASE_UPSTREAM_DIR}/volumes/storage"
if [[ -s "${temporary_directory}/storage.tar.gz" ]]; then
  if tar -tzf "${temporary_directory}/storage.tar.gz" \
    | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
    die "Storage archive contains an unsafe path"
  fi

  restore_timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
  previous_storage="${storage_directory}.pre-restore-${restore_timestamp}"

  if [[ -e "${storage_directory}" ]]; then
    mv -- "${storage_directory}" "${previous_storage}"
    printf 'Previous Storage retained at: %s\n' "${previous_storage}"
  fi

  install -d -m 0750 -- "${storage_directory}"
  tar -C "${storage_directory}" -xzf \
    "${temporary_directory}/storage.tar.gz"
fi

printf 'Starting the restored stack...\n'
compose up -d
compose ps

printf '\nRestore complete.\n'
printf 'Roles missing from this cluster were created from roles.sql; the encrypted configuration snapshot was verified but not applied.\n'
printf 'Run scripts/health.sh, then validate auth, avatars, and message access manually.\n'

