#!/usr/bin/env bash

set -Eeuo pipefail

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

[[ "$(id -u)" == 0 ]] || die "run as root"

SSHD_DROPIN=/etc/ssh/sshd_config.d/10-pocketpass.conf

[[ -s /home/ubuntu/.ssh/authorized_keys ]] \
  || die "ubuntu has no authorized_keys; aborting before disabling password auth"

install -m 0644 /dev/stdin "${SSHD_DROPIN}" <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
MaxAuthTries 3
LoginGraceTime 20
AllowUsers ubuntu
X11Forwarding no
EOF

if ! sshd -t; then
  rm -f "${SSHD_DROPIN}"
  die "sshd -t rejected the drop-in; it was removed and sshd is unchanged"
fi
systemctl reload ssh
printf 'SSH policy applied: %s\n' "${SSHD_DROPIN}"

export DEBIAN_FRONTEND=noninteractive
apt-get install -y fail2ban unattended-upgrades

install -m 0644 /dev/stdin /etc/fail2ban/jail.d/pocketpass.local <<'EOF'
[sshd]
enabled = true
backend = systemd
maxretry = 5
findtime = 10m
bantime = 1h
EOF
systemctl enable --now fail2ban
systemctl restart fail2ban
printf 'fail2ban sshd jail active.\n'

install -m 0644 /dev/stdin /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

install -m 0644 /dev/stdin /etc/apt/apt.conf.d/52pocketpass-unattended <<'EOF'
// Security pocket only. Containers are pinned separately
// (SUPABASE_UPSTREAM_REF plus the digest-pinned Caddy image) and are never
// auto-updated.
Unattended-Upgrade::Allowed-Origins {
    "${distro_id}:${distro_codename}-security";
    "${distro_id}ESMApps:${distro_codename}-apps-security";
    "${distro_id}ESM:${distro_codename}-infra-security";
};
// A surprise dockerd restart is a full-stack outage; update Docker manually.
Unattended-Upgrade::Package-Blacklist {
    "docker.io";
    "docker-ce";
    "docker-ce-cli";
    "containerd";
    "containerd.io";
};
Unattended-Upgrade::Automatic-Reboot "false";
Unattended-Upgrade::Remove-Unused-Dependencies "true";
EOF
printf 'unattended-upgrades configured (security pocket only, Docker excluded).\n'
