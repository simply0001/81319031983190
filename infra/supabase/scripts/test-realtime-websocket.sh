#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/common.sh"

require_command curl

probe_websocket() {
  local label="$1"
  local api_key="$2"
  local status

  status="$(
    curl \
      --silent \
      --show-error \
      --http1.1 \
      --max-time 3 \
      --output /dev/null \
      --write-out '%{http_code}' \
      --header 'Connection: Upgrade' \
      --header 'Upgrade: websocket' \
      --header 'Sec-WebSocket-Version: 13' \
      --header 'Sec-WebSocket-Key: cG9ja2V0cGFzcy10ZXN0' \
      "https://api.pocketpass.xyz/realtime/v1/websocket?apikey=${api_key}&vsn=1.0.0" \
      2>/dev/null || true
  )"
  printf '%s=%s\n' "${label}" "${status:-none}"
}

probe_websocket \
  publishable \
  "$(require_real_value SUPABASE_PUBLISHABLE_KEY)"
probe_websocket \
  legacy_anon \
  "$(require_real_value ANON_KEY)"
