#!/usr/bin/env bash

set -Eeuo pipefail

BACKUP_DIRECTORY="${BACKUP_DIRECTORY:-/var/backups/pocketpass}"

cd "${BACKUP_DIRECTORY}" 2>/dev/null || {
  printf 'serve-latest-backup: backup directory missing\n' >&2
  exit 1
}

newest="$(sudo ls -1t pocketpass-*.tar.gz.age 2>/dev/null | head -1)"
[[ -n "${newest}" ]] || {
  printf 'serve-latest-backup: no backups found\n' >&2
  exit 1
}

sidecar="${newest}.sha256"
sudo test -f "${sidecar}" || {
  printf 'serve-latest-backup: checksum sidecar missing for %s\n' "${newest}" >&2
  exit 1
}

exec sudo tar -C "${BACKUP_DIRECTORY}" -cf - "${newest}" "${sidecar}"
