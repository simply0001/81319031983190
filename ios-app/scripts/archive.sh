#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."
if [[ "$(uname -s)" != Darwin ]]; then
  echo "A Mac with Xcode is required to sign and archive the iOS app." >&2
  exit 1
fi
python3 - <<'PY'
import re, subprocess
version = subprocess.check_output(['xcodebuild', '-version'], text=True)
match = re.search(r'Xcode (\d+)\.(\d+)', version)
if not match or tuple(map(int, match.groups())) < (26, 2):
    raise SystemExit('Select Xcode 26.2 or newer before archiving.')
PY
python3 scripts/package_check.py
mkdir -p build
archive="$(pwd)/build/PocketPass-${APP_VERSION}-${APP_BUILD}.xcarchive"
export_path="$(pwd)/build/TestFlight-${APP_VERSION}-${APP_BUILD}"
if [[ -e "$archive" || -e "$export_path" ]]; then
  echo "This archive/export already exists. Use a new build number or move the old output." >&2
  exit 1
fi
xcodegen generate

plist_backup=$(mktemp)
cp Sources/Info.plist "$plist_backup"
trap 'cp "$plist_backup" Sources/Info.plist; rm -f "$plist_backup"' EXIT
python3 - <<'PY'
import os, plistlib
from pathlib import Path
path = Path('Sources/Info.plist')
data = plistlib.loads(path.read_bytes())
data['ITSAppUsesNonExemptEncryption'] = os.environ['POCKETPASS_NON_EXEMPT_ENCRYPTION'] == 'true'
path.write_bytes(plistlib.dumps(data))
PY

auth_args=()
if [[ -n "${ASC_AUTH_KEY_PATH:-}" ]]; then
  : "${ASC_KEY_ID:?Set ASC_KEY_ID}" "${ASC_ISSUER_ID:?Set ASC_ISSUER_ID}"
  auth_args=(-authenticationKeyPath "$ASC_AUTH_KEY_PATH" -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID")
fi
sign_args=(CODE_SIGN_STYLE=Automatic -allowProvisioningUpdates)
provision_args=(-allowProvisioningUpdates)
if [[ -n "${POCKETPASS_APP_PROFILE:-}" ]]; then
  provision_args=()
  sign_args=(CODE_SIGN_STYLE=Manual 'CODE_SIGN_IDENTITY=Apple Distribution'
    "POCKETPASS_APP_PROFILE=$POCKETPASS_APP_PROFILE" "POCKETPASS_WIDGET_PROFILE=$POCKETPASS_WIDGET_PROFILE")
fi
xcodebuild -project PocketPass.xcodeproj -scheme PocketPass -configuration Release \
  -sdk iphoneos -destination 'generic/platform=iOS' -archivePath "$archive" \
  "DEVELOPMENT_TEAM=$APPLE_TEAM_ID" "MARKETING_VERSION=$APP_VERSION" "CURRENT_PROJECT_VERSION=$APP_BUILD" \
  CODE_SIGNING_ALLOWED=YES CODE_SIGNING_REQUIRED=YES APS_ENVIRONMENT=production \
  "${sign_args[@]}" "${auth_args[@]}" archive
python3 scripts/package_check.py --archive "$archive" --export-options build/ExportOptions.plist
xcodebuild -exportArchive -archivePath "$archive" -exportPath "$export_path" \
  -exportOptionsPlist build/ExportOptions.plist "${provision_args[@]}" "${auth_args[@]}"
python3 scripts/package_check.py --ipa "$export_path/PocketPass.ipa"
echo "Signed archive and IPA for TestFlight prepared at $export_path"
