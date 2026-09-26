#!/usr/bin/env bash

set -Eeuo pipefail

PP_REMOTE="${PP_REMOTE:-ubuntu@79.72.17.89}"
PP_PULL_KEY="${PP_PULL_KEY:-${HOME}/.ssh/pocketpass-backup-pull}"
PP_BACKUP_DIR="${PP_BACKUP_DIR:-${HOME}/pocketpass-backups}"

log() {
  printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" \
    | tee -a "${PP_BACKUP_DIR}/pull.log"
}
die() {
  printf '%s ERROR %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" \
    | tee -a "${PP_BACKUP_DIR}/pull.log" >&2
  exit 1
}

mkdir -p "${PP_BACKUP_DIR}"

exec 9>"${PP_BACKUP_DIR}/.pull.lock"
flock -n 9 || die "another pull is already running"

[[ -f "${PP_PULL_KEY}" ]] || die "pull key not found: ${PP_PULL_KEY}"

staging="$(mktemp -d "${PP_BACKUP_DIR}/.staging.XXXXXX")"
trap 'rm -rf "${staging}"' EXIT

if ! ssh -i "${PP_PULL_KEY}" \
    -o BatchMode=yes \
    -o StrictHostKeyChecking=accept-new \
    -o ConnectTimeout=15 \
    "${PP_REMOTE}" \
  | tar -C "${staging}" -xf -; then
  die "transfer failed (ssh/tar pipeline)"
fi

archive="$(find "${staging}" -maxdepth 1 -type f -name 'pocketpass-*.tar.gz.age' -printf '%f\n' | head -1)"
[[ -n "${archive}" ]] || die "no archive received"
[[ -f "${staging}/${archive}.sha256" ]] || die "no checksum sidecar received"

if ! ( cd "${staging}" && sha256sum -c --status "${archive}.sha256" ); then
  die "checksum verification FAILED for ${archive}; keeping previous local copy"
fi

mv -f "${staging}/${archive}" "${staging}/${archive}.sha256" "${PP_BACKUP_DIR}/"
find "${PP_BACKUP_DIR}" -maxdepth 1 -type f \
  \( -name 'pocketpass-*.tar.gz.age' -o -name 'pocketpass-*.tar.gz.age.sha256' \) \
  ! -name "${archive}" ! -name "${archive}.sha256" \
  -delete

size="$(du -h "${PP_BACKUP_DIR}/${archive}" | cut -f1)"
log "OK pulled ${archive} (${size}), verified, older copies removed"
