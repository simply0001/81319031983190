#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/common.sh"

require_command docker
require_command sha256sum
validate_compose

compose exec -T db psql \
  --username postgres \
  --dbname postgres \
  --set ON_ERROR_STOP=1 \
  <<'SQL'
create schema if not exists pocketpass_migrations;
revoke all on schema pocketpass_migrations from public;

create table if not exists pocketpass_migrations.applied_migrations (
  version text primary key,
  checksum_sha256 text not null,
  applied_at timestamptz not null default now()
);

revoke all on table pocketpass_migrations.applied_migrations from public;
SQL

shopt -s nullglob
migration_files=("${INFRA_DIR}"/migrations/*.sql)
shopt -u nullglob
((${#migration_files[@]} > 0)) || die "no migration files found"

for migration_file in "${migration_files[@]}"; do
  filename="$(basename -- "${migration_file}")"
  version="${filename%%_*}"
  checksum="$(sha256sum "${migration_file}" | awk '{print $1}')"

  [[ "${version}" =~ ^[0-9]{14}$ ]] \
    || die "migration filename must begin with a 14-digit version: ${filename}"
  # Accept Windows line endings without changing the bytes used for checksum validation.
  [[ "$(head -n 1 "${migration_file}" | tr -d '\r')" == "begin;" ]] \
    || die "migration must start with begin;: ${filename}"
  [[ "$(tail -n 1 "${migration_file}" | tr -d '\r')" == "commit;" ]] \
    || die "migration must end with commit;: ${filename}"

  applied_checksum="$(
    compose exec -T db psql \
      --username postgres \
      --dbname postgres \
      --tuples-only \
      --no-align \
      --set ON_ERROR_STOP=1 \
      --command "
        select checksum_sha256
        from pocketpass_migrations.applied_migrations
        where version = '${version}'
      " \
    | tr -d '[:space:]'
  )"

  if [[ -n "${applied_checksum}" ]]; then
    [[ "${applied_checksum}" == "${checksum}" ]] \
      || die "applied migration ${version} has been modified"
    printf 'SKIP  %s\n' "${filename}"
    continue
  fi

  printf 'APPLY %s\n' "${filename}"
  {
    printf 'begin;\n'
    sed '1d;$d' "${migration_file}"
    printf '\ninsert into pocketpass_migrations.applied_migrations'
    printf ' (version, checksum_sha256) values '
    printf "('%s', '%s');\n" "${version}" "${checksum}"
    printf 'commit;\n'
  } | compose exec -T db psql \
        --username postgres \
        --dbname postgres \
        --set ON_ERROR_STOP=1
done

printf 'PocketPass migrations are current.\n'
