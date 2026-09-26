# Firebase message notifications

iOS uses the same Firebase project and server queue. Its native setup, APNs keys, signing and TestFlight checks are covered in [iOS App Store packaging](../ios-app/APP_STORE.md). The iOS registration migration and sender support were deployed on 12 September 2026, and the owner confirmed the APNs keys were uploaded to Firebase. Delivery still needs a signed iPhone test.

The Android integration and self-hosted sender are implemented. Firebase project `pocketpass-e005c` is configured, migration `20260912000100` is applied on the PocketPass VM, the sender is running, and the configured release APK is installed on Thor. The Firebase project and credentials were supplied by the owner. Message notifications shipped publicly in Android [0.1.9-alpha, build 22](https://github.com/Hinoaaaaaf212/pocketpass-release/releases/tag/v0.1.9-alpha) on 12 September 2026. Both public update feeds and the APK download were verified. The privacy page was updated with the Firebase notification disclosure before publication.

## 1. Create the Firebase project

1. Open the [Firebase console](https://console.firebase.google.com/) and create a project named PocketPass (or select your existing project).
2. Google Analytics is optional; this implementation does not include Analytics, Firebase Authentication, Firestore, or Firebase Cloud Functions. PocketPass keeps using its existing Supabase accounts and database.
3. Add an **Android** app. Enter the exact package name **`com.pocketpass.app`**. The nickname can be PocketPass. A signing SHA fingerprint is not required for this messaging integration.
4. Download **`google-services.json`** and save it here, with exactly that filename:

   `C:\Users\super\Documents\PocketPass-backups\backend\google-services.json`

5. In **Project settings → Cloud Messaging**, confirm **Firebase Cloud Messaging API (V1)** is enabled. If necessary, follow the link to Google Cloud to enable it. Do not use the legacy server-key API.

The Google Services plugin and Messaging SDK are already added. Do not copy additional Gradle snippets from the console. The build automatically reads the configuration from the sibling `PocketPass-backups/backend` directory without copying it into the repository. You can override the location with `-PPOCKETPASS_GOOGLE_SERVICES_JSON=C:/absolute/path/google-services.json`; `app/google-services.json` is also supported as a fallback. The app configuration contains project identifiers, not the server private key. See [Firebase's Android setup guide](https://firebase.google.com/docs/android/setup).

FCM itself is a no-cost Firebase product. This design sends from the existing PocketPass VM and does not require a paid Cloud Functions deployment. Your existing server/network costs still apply. See [Firebase pricing](https://firebase.google.com/pricing).

## 2. Create the server credential

In the same Firebase project, open **Project settings → Service accounts → Firebase Admin SDK → Generate new private key**. Download the JSON file and keep it outside the repository, for example:

`C:\Users\super\Documents\PocketPass-backups\backend\firebase-service-account.json`

This is a secret. Do not paste its contents into chat, commit it, add it to the APK, or place it in a web-served directory. The Android `google-services.json` and this server JSON are different files and must belong to the same Firebase project. See [Firebase's server setup guide](https://firebase.google.com/docs/admin/setup).

For tighter permissions, create a dedicated service account in the linked Google Cloud project with only **Firebase Cloud Messaging API Admin** (`roles/firebasecloudmessaging.admin`) and generate a JSON key for that account instead of using the default Firebase Admin account. Store and rotate that key as a server credential.

Keep both originals in `PocketPass-backups\backend`. The downloaded Admin SDK filename can remain unchanged; only the deployed server copy uses the standardized filename below. You do not need to paste either file's contents.

## 3. Server deployment

These are operator steps for the existing PocketPass self-hosted Supabase VM, not Firebase Hosting or the hosted Supabase dashboard. Connect as `ubuntu` using `ssh pocketpass-vm`. `/opt/pocketpass/app` is a deployed file tree, not a Git checkout. Back up the database and existing Compose overlay, then deploy the updated overlay, push migration, `push/{Dockerfile,.dockerignore,requirements.txt,worker.py}`, and `scripts/start-message-push.sh`. Keep shell scripts and SQL files LF-terminated. Do not replace `.env.production` or copy local secrets into the deployed app directory.

Securely copy the server credential to `/opt/pocketpass/secrets/firebase-service-account.json`. The worker runs as UID/GID 10001 and needs read access to that file. On the VM, after uploading the file:

```bash
sudo chown 10001:10001 /opt/pocketpass/secrets/firebase-service-account.json
sudo chmod 600 /opt/pocketpass/secrets/firebase-service-account.json
```

The parent directory should not be web-served or world-readable. The credential is bind-mounted read-only; it is not baked into the image. A different absolute location can be set with `FIREBASE_SERVICE_ACCOUNT_FILE` in `infra/supabase/.env.production`.

The current server configuration is root-owned. Start a privileged shell, then run:

```bash
sudo -i
cd /opt/pocketpass/app/infra/supabase
bash scripts/migrate.sh
bash scripts/start-message-push.sh
```

The migration is `20260912000100_message_push.sql`. The worker uses the existing server-only `SERVICE_ROLE_KEY` to call PostgREST internally. There are no new public ports or database webhooks to configure. The `push` Compose profile is opt-in so deployments without Firebase credentials keep working.

Inspect status using the repository's Compose wrapper:

```bash
cd /opt/pocketpass/app/infra/supabase
source scripts/common.sh
compose --profile push ps message-push
compose --profile push logs --tail 50 message-push
```

To pause sending without changing messages or database data:

```bash
compose --profile push stop message-push
```

Before enabling delivery for other users, update the published privacy information to disclose Google/Firebase as a notification delivery provider. Push payloads include device routing identifiers, the sender/group title, and a short message preview. Analytics is not enabled by this integration. Review that disclosure as part of rollout; this change does not publish a new privacy policy automatically.

## 4. Build and install

After saving the Android configuration in `PocketPass-backups\backend`, run from PowerShell:

```powershell
Set-Location C:\Users\super\Documents\PocketPass
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleRelease -PPOCKETPASS_REQUIRE_FIREBASE=true --console=plain
```

The usual production backend properties and signing credentials must remain configured. On this PC, the signed APK is written to:

`C:\Users\super\AppData\Local\PocketPass\gradle\app\outputs\apk\release\app-release.apk`

The `POCKETPASS_REQUIRE_FIREBASE` flag makes the build fail if the configuration file is missing. Builds without that flag and without the JSON remain usable, but push is disabled and the Messages toggle is hidden. Nothing is automatically published as a new release.

## 5. Enable and test

1. Install the configured release build and sign in on Thor. Allow Android notification permission when prompted. Thor has Google Play services installed, which FCM needs.
2. Open **Settings → App Settings → Notifications** and leave **Message Alerts** enabled. Android's separate **Messages** notification channel must also be enabled.
3. Send a message from a second, consenting test account while PocketPass is in the background on Thor. Repeat in a group. Tap each notification and confirm it opens the correct chat.
4. Repeat with the screen off and Bluetooth disabled. Delivery is independent of Nearby. Do not force-stop PocketPass through Android Settings; Android blocks delivery to a force-stopped app until it is reopened.
5. Check that messages from yourself do not alert, messages while PocketPass is visible use the existing in-app UI, disabling Message Alerts suppresses alerts, and logout prevents alerts for the old account.

The Firebase console's **Send test message** sends a different, automatically displayed notification payload. It does not exercise PocketPass's data-only payload validation, account checks, or tap-to-chat path. Test through actual messages between your test accounts.

The worker uses high-priority, data-only FCM messages, allowing the Android receiver to check the current account, device binding, settings, and duplicate counter before showing an alert. Notifications replace the previous alert for the same chat. Lock-screen previews use Android's private visibility. See [Firebase message handling](https://firebase.google.com/docs/cloud-messaging/android/receive-messages) and [priority guidance](https://firebase.google.com/docs/cloud-messaging/android-message-priority).

Queued delivery expires after 15 minutes; FCM also has a 15-minute delivery TTL. Messages remain in the chat regardless of whether an alert expires. Existing unread history is not sent as a notification burst when you first enable the feature. Registrations expire after 30 days without refresh and refresh periodically while signed in. Logging out unregisters the device when online, clears its local binding immediately, and attempts to delete the Firebase token; revoked Supabase sessions also remove server registrations.

If alerts do not arrive, check Android permission/channel settings, that the APK was rebuilt after adding the JSON, and worker logs. A Firebase permission or sender-ID error usually means the server key lacks messaging access or belongs to a different project. Worker logs intentionally exclude tokens, message bodies, and private keys.

## Local verification

```powershell
.\gradlew.bat :app:testDebugUnitTest :shared:testAndroidHostTest :ui:compileCommonMainKotlinMetadata --console=plain
Set-Location infra\supabase\push
npm ci --ignore-scripts
npm test
python -m unittest -v test_worker.py
```

The database tests apply the real push migration to an isolated PGlite PostgreSQL instance with minimal surrounding PocketPass/auth tables. They test registration authorization, privacy boundaries, session revocation, queue coalescing, retries, leases, and stale/read/blocked recipients. They do not replace staging validation against the complete Supabase stack or a real Firebase delivery test.
