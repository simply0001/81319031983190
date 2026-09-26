#!/usr/bin/env bash

set -Eeuo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

[[ "$(id -u)" == 0 ]] || die "run as root"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
UNIT_DIR="$(cd -- "${SCRIPT_DIR}/../systemd" && pwd -P)"

install -m 0644 "${UNIT_DIR}"/pocketpass-*.service \
                "${UNIT_DIR}"/pocketpass-*.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now pocketpass-firewall.service
systemctl enable --now pocketpass-backup.timer pocketpass-health.timer \
                       pocketpass-app-update.timer
systemctl list-timers 'pocketpass-*' --no-pager
