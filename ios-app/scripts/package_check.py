#!/usr/bin/env python3
"""Validate release inputs and the actual archive without printing credentials."""
import argparse
import json
import os
from pathlib import Path
import plistlib
import re
import struct
import subprocess

APP_ID = "xyz.pocketpass.PocketPass"
WIDGET_ID = APP_ID + ".widget"
GROUP_ID = "group.xyz.pocketpass"
ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_plist(path):
    with open(path, "rb") as file:
        return plistlib.load(file)


def check_source(root=ROOT):
    icons = root / "Resources/Assets.xcassets/AppIcon.appiconset"
    manifest = json.loads((icons / "Contents.json").read_text())
    require(any(i["idiom"] == "ios-marketing" for i in manifest["images"]), "Missing App Store icon")
    for item in manifest["images"]:
        data = (icons / item["filename"]).read_bytes()
        require(data[:8] == b"\x89PNG\r\n\x1a\n", "App icon must be PNG")
        width, height, _, color = struct.unpack(">IIBB", data[16:26])
        expected = int(float(item["size"].split("x")[0]) * int(item["scale"][:-1]))
        require(width == height == expected and color == 2, "App icons must have the correct dimensions and no alpha channel")
    for name in ("Resources/PrivacyInfo.xcprivacy", "Widget/PrivacyInfo.xcprivacy"):
        privacy = load_plist(root / name)
        require(privacy.get("NSPrivacyTracking") is False, "Unexpected tracking declaration")
        require(isinstance(privacy.get("NSPrivacyAccessedAPITypes"), list), "Missing required-reason API declarations")


def check_configuration(env=os.environ, root=ROOT):
    require(re.fullmatch(r"[A-Z0-9]{10}", env.get("APPLE_TEAM_ID", "")), "Set APPLE_TEAM_ID to the 10-character developer Team ID")
    require(re.fullmatch(r"\d+\.\d+(?:\.\d+)?", env.get("APP_VERSION", "")), "Set APP_VERSION, for example 0.1.8")
    require(re.fullmatch(r"[1-9]\d*", env.get("APP_BUILD", "")), "Set APP_BUILD to a new positive App Store build number")
    require(env.get("POCKETPASS_BACKEND_ENABLED") == "true", "Distribution builds must enable the production backend")
    key = env.get("POCKETPASS_SUPABASE_PUBLISHABLE_KEY", "")
    require(len(key) >= 20, "A Supabase publishable key is required; fixture builds cannot be distributed")
    if key.count(".") == 2:
        import base64
        try:
            payload = key.split(".")[1]
            claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        except (ValueError, UnicodeDecodeError):
            raise ValueError("Invalid Supabase publishable JWT") from None
        require(claims.get("role") == "anon", "Only the anon/publishable key may be embedded in the app")
    else:
        require(key.startswith("sb_publishable_"), "Only a Supabase publishable key may be embedded in the app")
    require(env.get("POCKETPASS_SUPABASE_URL", "https://api.pocketpass.xyz") == "https://api.pocketpass.xyz", "Distribution must use the PocketPass production backend")
    require(env.get("POCKETPASS_NON_EXEMPT_ENCRYPTION") in ("true", "false"), "Complete the export-compliance assessment and set POCKETPASS_NON_EXEMPT_ENCRYPTION explicitly")
    firebase = root / "Config/GoogleService-Info.plist"
    require(firebase.is_file(), "Save the Firebase iOS configuration at ios-app/Config/GoogleService-Info.plist")
    config = load_plist(firebase)
    require(config.get("BUNDLE_ID") == APP_ID, "Firebase configuration has the wrong iOS bundle identifier")
    require(config.get("PROJECT_ID") == "pocketpass-e005c", "Firebase iOS and the push worker must use the same project")
    require(bool(config.get("GOOGLE_APP_ID")) and bool(config.get("GCM_SENDER_ID")), "Firebase messaging configuration is incomplete")
    require(bool(env.get("POCKETPASS_APP_PROFILE")) == bool(env.get("POCKETPASS_WIDGET_PROFILE")), "Manual signing requires both the app and widget provisioning profiles")


def write_export_options(path, env=os.environ):
    options = {
        "method": "app-store-connect", "destination": "export", "teamID": env["APPLE_TEAM_ID"],
        "signingStyle": "automatic", "manageAppVersionAndBuildNumber": False,
        "stripSwiftSymbols": True, "uploadSymbols": True,
    }
    if env.get("POCKETPASS_APP_PROFILE"):
        options.update(signingStyle="manual", signingCertificate="Apple Distribution", provisioningProfiles={
            APP_ID: env["POCKETPASS_APP_PROFILE"], WIDGET_ID: env["POCKETPASS_WIDGET_PROFILE"],
        })
    Path(path).write_bytes(plistlib.dumps(options))


def check_bundle(app, env=os.environ, distribution=False):
    app = Path(app)
    widget = app / "PlugIns/PocketPassWidget.appex"
    for bundle, identifier in ((app, APP_ID), (widget, WIDGET_ID)):
        info = load_plist(bundle / "Info.plist")
        require(info["CFBundleIdentifier"] == identifier, "Archived bundle identifier differs from the registered app")
        require(info["CFBundleShortVersionString"] == env["APP_VERSION"], "App and widget marketing versions must match")
        require(info["CFBundleVersion"] == env["APP_BUILD"], "App and widget build numbers must match")
        require("iPhoneOS" in info.get("CFBundleSupportedPlatforms", []), "Archive contains a simulator build")
        require((bundle / "PrivacyInfo.xcprivacy").is_file(), "Archive is missing a privacy manifest")
        subprocess.run(["codesign", "--verify", "--deep", "--strict", str(bundle)], check=True)
        entitlements = plistlib.loads(subprocess.check_output(["codesign", "-d", "--entitlements", ":-", str(bundle)], stderr=subprocess.DEVNULL))
        require(entitlements.get("com.apple.developer.team-identifier") == env["APPLE_TEAM_ID"], "Archive is signed by the wrong team")
        require(entitlements.get("application-identifier", "").endswith("." + identifier), "Archive has the wrong application identifier")
        require(GROUP_ID in entitlements.get("com.apple.security.application-groups", []), "App and widget must share the PocketPass App Group")
        if distribution:
            require(not entitlements.get("get-task-allow", False), "Export is signed for debugging")
        if bundle == app:
            if distribution:
                require(entitlements.get("aps-environment") == "production", "TestFlight/App Store pushes need the production APNs entitlement")
            require(info.get("ITSAppUsesNonExemptEncryption") is (env["POCKETPASS_NON_EXEMPT_ENCRYPTION"] == "true"), "Archive is missing its export-compliance answer")
            require(bool(info.get("CFBundleIcons")), "Archive has no compiled app icon")
            require(info.get("FirebaseAppDelegateProxyEnabled") is False, "Firebase delegate swizzling must stay disabled")
            require(info.get("FirebaseMessagingAutoInitEnabled") is False, "Push registration must wait for an account and permission")
            require((bundle / "GoogleService-Info.plist").is_file(), "Firebase configuration was not bundled")


def check_ipa(path, env=os.environ):
    import tempfile
    import zipfile
    with tempfile.TemporaryDirectory(prefix="pocketpass-ipa-") as directory:
        with zipfile.ZipFile(path) as archive:
            root = Path(directory).resolve()
            for name in archive.namelist():
                require((root / name).resolve().is_relative_to(root), "Invalid path in exported IPA")
            subprocess.run(["ditto", "-x", "-k", str(path), directory], check=True)
        check_bundle(Path(directory) / "Payload/PocketPass.app", env, distribution=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-only", action="store_true")
    parser.add_argument("--archive")
    parser.add_argument("--ipa")
    parser.add_argument("--export-options")
    args = parser.parse_args()
    try:
        check_source()
        if not args.source_only:
            check_configuration()
        if args.archive:
            check_bundle(Path(args.archive) / "Products/Applications/PocketPass.app")
        if args.ipa:
            check_ipa(args.ipa)
        if args.export_options:
            write_export_options(args.export_options)
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Packaging check failed: {error}\n")
    print("iOS packaging checks passed" + (" (source assets only)" if args.source_only else ""))
