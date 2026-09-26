#!/usr/bin/env bash

set -Eeuo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

[[ "$(id -u)" == 0 ]] || die "run as root"
command -v iptables >/dev/null 2>&1 || die "required command not found: iptables"

[[ -r /etc/default/pocketpass-firewall ]] && . /etc/default/pocketpass-firewall
external_interface="${POCKETPASS_EXT_IF:-$(ip -4 route show default | awk '{print $5; exit}')}"
[[ -n "${external_interface}" ]] || die "cannot determine the external interface"

apply_docker_user() {
  local ipt="$1"

  "${ipt}" -N DOCKER-USER 2>/dev/null || true
  "${ipt}" -F DOCKER-USER

  "${ipt}" -A DOCKER-USER ! -i "${external_interface}" -j RETURN

  "${ipt}" -A DOCKER-USER -i "${external_interface}" -m conntrack \
    --ctstate RELATED,ESTABLISHED -j RETURN

  "${ipt}" -A DOCKER-USER -i "${external_interface}" -p tcp -m conntrack \
    --ctdir ORIGINAL --ctorigdstport 80 -j RETURN
  "${ipt}" -A DOCKER-USER -i "${external_interface}" -p tcp -m conntrack \
    --ctdir ORIGINAL --ctorigdstport 443 -j RETURN
  "${ipt}" -A DOCKER-USER -i "${external_interface}" -p udp -m conntrack \
    --ctdir ORIGINAL --ctorigdstport 443 -j RETURN

  "${ipt}" -A DOCKER-USER -i "${external_interface}" -j DROP
}

apply_docker_user iptables

if command -v ip6tables >/dev/null 2>&1 \
  && ip6tables -S DOCKER-USER >/dev/null 2>&1; then
  apply_docker_user ip6tables
fi

iptables -S DOCKER-USER
printf 'DOCKER-USER hardening applied on %s.\n' "${external_interface}"
