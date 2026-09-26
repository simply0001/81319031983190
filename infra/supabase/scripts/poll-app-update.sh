#!/usr/bin/env bash

set -Eeuo pipefail

INFRA_DIR="${INFRA_DIR:-/opt/pocketpass/app/infra/supabase}"

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

command -v curl >/dev/null 2>&1 || die "curl is required"
command -v python3 >/dev/null 2>&1 || die "python3 is required"

RELEASE_REPO="Hinoaaaaaf212/pocketpass-release"
UPDATES_DIR="${INFRA_DIR}/updates"
CACHE_DIR="${UPDATES_DIR}/.cache"
SERVE_FILE="${UPDATES_DIR}/latest.json"
API_URL="https://api.github.com/repos/${RELEASE_REPO}/releases/latest"

mkdir -p "${CACHE_DIR}"
touch "${CACHE_DIR}/etag.txt"

broadcast_manifest_change() {
  command -v docker >/dev/null 2>&1 || return 0
  local payload
  payload="$(python3 - "${SERVE_FILE}" <<'PY'
import json
import sys

manifest = json.load(open(sys.argv[1]))
print(json.dumps({
    "versionCode": int(manifest.get("versionCode", 0)),
    "minSupportedVersionCode": manifest.get("minSupportedVersionCode"),
}))
PY
)"
  if ! printf '%s\n' \
    "select realtime.send(:'payload'::jsonb, 'app_update', 'app_updates', true);" \
    | docker exec -i supabase-db psql -U postgres -d postgres -q \
      --set ON_ERROR_STOP=1 --set payload="${payload}" \
      >/dev/null 2>&1; then
    printf 'warning: app_updates broadcast failed; clients will poll instead\n' >&2
  fi
}

publish_file() {
  python3 -c 'import json, sys; json.load(open(sys.argv[1]))' "$1" \
    || die "refusing to publish invalid JSON"
  if [[ -f "${SERVE_FILE}" ]] && cmp -s "$1" "${SERVE_FILE}"; then
    rm -f "$1"
    return
  fi
  chmod 0644 "$1"
  mv "$1" "${SERVE_FILE}"
  broadcast_manifest_change
}

RELEASE_TMP="${CACHE_DIR}/release.json.tmp"
http_code="$(curl --silent --show-error --max-time 30 \
  --output "${RELEASE_TMP}" --write-out '%{http_code}' \
  --etag-compare "${CACHE_DIR}/etag.txt" \
  --etag-save "${CACHE_DIR}/etag.txt.new" \
  --header 'Accept: application/vnd.github+json' \
  --header 'X-GitHub-Api-Version: 2022-11-28' \
  "${API_URL}")"

case "${http_code}" in
  304)
    exit 0
    ;;
  404)
    printf '{"schemaVersion":1,"versionCode":0}\n' > "${CACHE_DIR}/latest.json.tmp"
    publish_file "${CACHE_DIR}/latest.json.tmp"
    rm -f "${CACHE_DIR}/etag.txt.new"
    exit 0
    ;;
  200)
    ;;
  *)
    die "GitHub releases/latest returned HTTP ${http_code}"
    ;;
esac

META_URL="$(python3 - "${RELEASE_TMP}" <<'PY'
import json
import sys

release = json.load(open(sys.argv[1]))
assets = {asset.get("name"): asset for asset in release.get("assets", [])}
meta = assets.get("update.json")
if not meta:
    sys.exit("release has no update.json asset")
print(meta["browser_download_url"])
PY
)"

curl --fail --silent --show-error --location --max-time 30 \
  --output "${CACHE_DIR}/update.json.tmp" "${META_URL}"

python3 - "${RELEASE_TMP}" "${CACHE_DIR}/update.json.tmp" \
  "${CACHE_DIR}/latest.json.tmp" <<'PY'
import json
import re
import sys

release = json.load(open(sys.argv[1]))
meta = json.load(open(sys.argv[2]))
assets = {asset.get("name"): asset for asset in release.get("assets", [])}
apk = assets.get("PocketPass.apk")
if not apk:
    sys.exit("release has no PocketPass.apk asset")
code = meta.get("versionCode")
if not isinstance(code, int) or code <= 0:
    sys.exit("update.json versionCode must be a positive integer")
sha = str(meta.get("apkSha256", "")).lower()
if not re.fullmatch(r"[0-9a-f]{64}", sha):
    sys.exit("update.json apkSha256 is not a sha-256 hex digest")
if int(meta.get("apkSizeBytes", -1)) != int(apk.get("size", -2)):
    sys.exit("update.json apkSizeBytes does not match the release asset size")
min_code = meta.get("minSupportedVersionCode")
if min_code is not None and (not isinstance(min_code, int) or min_code < 0):
    sys.exit("update.json minSupportedVersionCode must be a non-negative integer")
out = {
    "schemaVersion": 1,
    "versionCode": code,
    "versionName": meta.get("versionName"),
    "apkUrl": apk["browser_download_url"],
    "apkSha256": sha,
    "apkSizeBytes": int(meta["apkSizeBytes"]),
    "notes": release.get("body") or "",
    "publishedAt": release.get("published_at") or "",
}
if min_code is not None:
    out["minSupportedVersionCode"] = min_code
with open(sys.argv[3], "w") as handle:
    json.dump(out, handle, indent=2)
    handle.write("\n")
PY

publish_file "${CACHE_DIR}/latest.json.tmp"
if [[ -f "${CACHE_DIR}/etag.txt.new" ]]; then
  mv "${CACHE_DIR}/etag.txt.new" "${CACHE_DIR}/etag.txt"
fi
