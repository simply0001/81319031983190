import base64
import json
from pathlib import Path
import plistlib
import tempfile
import unittest

from package_check import APP_ID, WIDGET_ID, check_configuration, check_source, write_export_options


class PackagingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "Config").mkdir()
        self.config = {"BUNDLE_ID": APP_ID, "PROJECT_ID": "pocketpass-e005c", "GOOGLE_APP_ID": "test-app", "GCM_SENDER_ID": "test-sender"}
        self.save_config()
        self.env = {
            "APPLE_TEAM_ID": "ABC1234567", "APP_VERSION": "0.1.8", "APP_BUILD": "22",
            "POCKETPASS_BACKEND_ENABLED": "true", "POCKETPASS_SUPABASE_PUBLISHABLE_KEY": "sb_publishable_test_only",
            "POCKETPASS_NON_EXEMPT_ENCRYPTION": "false",
        }

    def save_config(self):
        (self.root / "Config/GoogleService-Info.plist").write_bytes(plistlib.dumps(self.config))

    def test_source_icons_and_manifests(self):
        check_source()

    def test_valid_distribution_inputs(self):
        check_configuration(self.env, self.root)

    def test_refuses_fixture_build_or_unanswered_export_compliance(self):
        for key in ("POCKETPASS_BACKEND_ENABLED", "POCKETPASS_NON_EXEMPT_ENCRYPTION", "APPLE_TEAM_ID"):
            env = {k: v for k, v in self.env.items() if k != key}
            with self.assertRaises(ValueError):
                check_configuration(env, self.root)

    def test_refuses_server_credential_in_mobile_app(self):
        payload = base64.urlsafe_b64encode(json.dumps({"role": "service_role"}).encode()).decode().rstrip("=")
        self.env["POCKETPASS_SUPABASE_PUBLISHABLE_KEY"] = "header." + payload + ".signature"
        with self.assertRaisesRegex(ValueError, "Only the anon"):
            check_configuration(self.env, self.root)

    def test_refuses_wrong_firebase_app_or_project(self):
        for key in ("BUNDLE_ID", "PROJECT_ID"):
            original = self.config[key]
            self.config[key] = "wrong"
            self.save_config()
            with self.assertRaises(ValueError):
                check_configuration(self.env, self.root)
            self.config[key] = original

    def test_manual_signing_requires_both_profiles(self):
        self.env["POCKETPASS_APP_PROFILE"] = "PocketPass Store"
        with self.assertRaisesRegex(ValueError, "both"):
            check_configuration(self.env, self.root)
        self.env["POCKETPASS_WIDGET_PROFILE"] = "PocketPass Widget Store"
        check_configuration(self.env, self.root)
        output = self.root / "ExportOptions.plist"
        write_export_options(output, self.env)
        exported = plistlib.loads(output.read_bytes())
        self.assertEqual("manual", exported["signingStyle"])
        self.assertEqual({APP_ID, WIDGET_ID}, set(exported["provisioningProfiles"]))
        self.assertEqual("export", exported["destination"])
        self.assertFalse(exported["manageAppVersionAndBuildNumber"])


if __name__ == "__main__":
    unittest.main()
