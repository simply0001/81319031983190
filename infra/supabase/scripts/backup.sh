#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/common.sh"

require_command age
require_command docker
require_command sha256sum
require_command tar
validate_compose

BACKUP_DIRECTORY="$(env_or_file BACKUP_DIRECTORY)"
BACKUP_AGE_RECIPIENT="$(require_real_value BACKUP_AGE_RECIPIENT)"
POSTGRES_DB="$(env_or_file POSTGRES_DB)"

[[ -n "${BACKUP_DIRECTORY}" ]] || die "BACKUP_DIRECTORY is empty"
[[ "${BACKUP_DIRECTORY}" = /* ]] || die "BACKUP_DIRECTORY must be absolute"
[[ "${BACKUP_DIRECTORY}" != "/" ]] || die "BACKUP_DIRECTORY cannot be /"
[[ -n "${POSTGRES_DB}" ]] || POSTGRES_DB="postgres"

install -d -m 0700 -- "${BACKUP_DIRECTORY}"

if command -v flock >/dev/null 2>&1; then
  exec 9>"${BACKUP_DIRECTORY}/.backup.lock"
  flock -n 9 || die "another PocketPass backup is already running"
fi

timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
archive_name="pocketpass-${timestamp}.tar.gz.age"
archive_path="${BACKUP_DIRECTORY}/${archive_name}"
partial_path="${archive_path}.partial"
temporary_directory="$(mktemp -d "${BACKUP_DIRECTORY}/.backup-${timestamp}.XXXXXX")"

cleanup() {
  rm -f -- "${partial_path}"
  rm -rf -- "${temporary_directory}"
}
trap cleanup EXIT

db_container_id="$(compose ps -q db)"
[[ -n "${db_container_id}" ]] || die "database container is not running"

printf 'Creating PostgreSQL logical backup...\n'
compose exec -T db \
  pg_dump \
    --username postgres \
    --dbname "${POSTGRES_DB}" \
    --format custom \
    --no-owner \
  >"${temporary_directory}/database.dump"

compose exec -T db \
  pg_dumpall \
    --username postgres \
    --roles-only \
  >"${temporary_directory}/roles.sql"

compose exec -T --user root db sh -c \
  'if [ -f /etc/postgresql-custom/pgsodium_root.key ]; then cat /etc/postgresql-custom/pgsodium_root.key; fi' \
  >"${temporary_directory}/pgsodium_root.key"

if [[ ! -s "${temporary_directory}/pgsodium_root.key" ]]; then
  rm -f -- "${temporary_directory}/pgsodium_root.key"
  warn "pgsodium root key was not present in the database container"
fi

storage_directory="${SUPABASE_UPSTREAM_DIR}/volumes/storage"
if [[ -d "${storage_directory}" ]]; then
  printf 'Archiving Storage objects...\n'
  tar -C "${storage_directory}" -czf \
    "${temporary_directory}/storage.tar.gz" .
else
  warn "local Storage directory was not found: ${storage_directory}"
fi

helper_data_directory="/opt/pocketpass/helper/data"
if [[ -d "${helper_data_directory}" ]]; then
  printf 'Archiving Helper bot state...\n'
  tar -C "${helper_data_directory}" -czf \
    "${temporary_directory}/helper-data.tar.gz" .
else
  warn "Helper bot data directory was not found: ${helper_data_directory}"
fi

install -d -m 0700 -- "${temporary_directory}/configuration"
cp -- "${ENV_FILE}" \
  "${temporary_directory}/configuration/env.production"
cp -- "${OVERLAY_COMPOSE_FILE}" \
  "${temporary_directory}/configuration/compose.production.yml"
cp -- "${INFRA_DIR}/caddy/Caddyfile" \
  "${temporary_directory}/configuration/Caddyfile"

{
  printf 'created_at=%s\n' "${timestamp}"
  printf 'supabase_upstream_ref=%s\n' "$(env_or_file SUPABASE_UPSTREAM_REF)"
  printf 'postgres_database=%s\n' "${POSTGRES_DB}"
  printf 'compose_project=%s\n' "$(env_or_file COMPOSE_PROJECT_NAME)"
  printf 'database_image=%s\n' "$(docker inspect --format '{{.Config.Image}}' "${db_container_id}")"
} >"${temporary_directory}/manifest.txt"

(
  cd -- "${temporary_directory}"
  find . -type f ! -name checksums.sha256 -print0 \
    | sort -z \
    | xargs -0 sha256sum \
    >checksums.sha256
)

printf 'Encrypting backup for the off-host recipient...\n'
tar -C "${temporary_directory}" -czf - . \
  | age --encrypt --recipient "${BACKUP_AGE_RECIPIENT}" \
      --output "${partial_path}"

mv -- "${partial_path}" "${archive_path}"
(
  cd -- "${BACKUP_DIRECTORY}"
  sha256sum "${archive_name}" >"${archive_name}.sha256"
)

printf 'Backup complete: %s\n' "${archive_path}"
printf 'Copy the .age and .sha256 files off this VM now.\n'

retention_days="$(env_or_file BACKUP_RETENTION_DAYS)"
if [[ "${retention_days}" =~ ^[0-9]+$ ]] && (( retention_days > 0 )); then
  printf 'Pruning backups older than %s days...\n' "${retention_days}"
  find "${BACKUP_DIRECTORY}" -maxdepth 1 -type f \
    \( -name 'pocketpass-*.tar.gz.age' -o -name 'pocketpass-*.tar.gz.age.sha256' \) \
    -mtime +"${retention_days}" -print -delete
else
  warn "BACKUP_RETENTION_DAYS is not a positive integer; skipping pruning"
fi

