# PocketPass TestFlight distribution

The first iOS distribution target is TestFlight, as requested by the owner. On 12 September 2026 the owner deferred iOS distribution to release Android 0.1.9-alpha first. The project now has Firebase message notifications, iPhone/iPad icons, privacy manifests, versioned app/widget packaging, and a signed archive/export path. No signed IPA, TestFlight upload or native iPhone test has been completed for these changes.

TestFlight uses Apple Distribution signing and App Store Connect provisioning profiles. The existing certificate and the two requested profiles are the correct inputs for the beta. The export method remains `app-store-connect`; uploading a build does not publish it on the App Store.

## Identifiers and Firebase

| Item | Value |
| --- | --- |
| App bundle | `xyz.pocketpass.PocketPass` |
| Widget bundle | `xyz.pocketpass.PocketPass.widget` |
| App Group | `group.xyz.pocketpass` |
| Apple Developer Team ID | `72PMGDSD3B` |
| APNs Key ID | `5N63P8T433` |
| Firebase project | `pocketpass-e005c` — shared with Android |
| Firebase Apple app | PocketPass iOS, registered for the app bundle above |
| Firebase config | `Config/GoogleService-Info.plist` — ignored by Git |
| Version defaults | 0.1.8, build 21; choose a new build number for each upload |

Register both explicit App IDs and the App Group in the Apple Developer account. Enable Push Notifications on the main app and App Groups on both targets. The widget does not need push capability. Both must be signed by the same team.

The local Firebase config is also backed up at `C:\Users\super\Documents\PocketPass-backups\backend\GoogleService-Info.plist`. It contains Firebase client configuration, never the server service-account credential or APNs private key.

Apple Team ID `72PMGDSD3B` is saved in the Xcode project and Firebase iOS app. APNs key `5N63P8T433` was checked as a P-256 private key and backed up outside the repository. The owner confirmed on 12 September 2026 that the APNs keys were uploaded to Firebase. Upload completion is owner-confirmed; actual delivery still needs a signed iPhone build and notification test.

In Firebase **Project settings → Cloud Messaging → Apple app configuration**, upload the APNs authentication key with its Apple Key ID and Team ID. Use a production key for TestFlight/App Store, and configure a development key too if testing a development-signed app. The Firebase project must match the worker's service account. The APNs `.p8` key stays with Firebase; it is never bundled in the app or committed to Git.

## Message notifications

Android and iOS share the database queue, account/session authorization, notification payload parser, message preferences and conversation navigation. Swift handles notification permission, APNs registration, Firebase token changes and notification taps. The iOS registration controller retries after network changes and failed requests, and refreshes an active registration every 12 hours while the app is open.

iOS alerts say **PocketPass — You have a new message.** The server and worker remove sender names and message previews from the iOS payload. A tap is held until session restoration completes, then checked against the current account and device binding. Foreground messages use the existing in-app UI. Existing Nearby notifications keep their own behavior. Turning Message Alerts back on after denying permission opens the app's iOS Settings page.

Signing out immediately invalidates local taps, clears delivered message alerts, attempts to unregister the server binding and deletes the Firebase token. Revoking the Supabase session also cascades its registrations. If the device is offline during logout, an already queued generic OS alert can still appear; it cannot reveal message content or open another account's chat. APNs can coalesce pending alerts for a conversation, but iOS may still keep more than one delivered alert in Notification Center. Alerts are not a guaranteed message-delivery receipt; conversations remain the source of truth.

Backend migration: `infra/supabase/migrations/20260912000300_ios_message_push.sql`. Keep the original Android migration unchanged. Existing Android clients continue using the same registration RPC. The new worker accepts both old Android jobs and the new platform-tagged jobs.

The migration and worker were deployed to the PocketPass server on 12 September 2026. The previous worker and image identifier are backed up under `/opt/pocketpass/deploy-backups/ios-push-20260912`. APNs delivery remains unverified until a signed iPhone registers and receives a test message.

## Build on a Mac

Use Xcode 26.2 or newer, XcodeGen and JDK 21. A signed-in Xcode account with suitable signing access can use automatic signing:

```bash
export APPLE_TEAM_ID=72PMGDSD3B
export APP_VERSION=0.1.8
export APP_BUILD=22 # Must be unused in App Store Connect.
export POCKETPASS_BACKEND_ENABLED=true
export POCKETPASS_SUPABASE_URL=https://api.pocketpass.xyz
export POCKETPASS_SUPABASE_PUBLISHABLE_KEY='your publishable key'
export POCKETPASS_NON_EXEMPT_ENCRYPTION='your assessed true/false answer'
bash ios-app/scripts/archive.sh
```

The script refuses fixture builds, mismatched Firebase projects, server keys, missing icons or unanswered export compliance. It checks the actual app and widget in the archive, exports a distribution IPA, and checks the exported signatures, production APNs entitlement, App Group, versions and resources. Automatic signing may use a development certificate for the intermediate archive; the exported IPA must use distribution signing.

Output: `ios-app/build/TestFlight-<version>-<build>/PocketPass.ipa`, plus its `.xcarchive`. The script does not upload or release it. Use Xcode Organizer or Transporter to upload to App Store Connect, then install through TestFlight for native testing.

For manual signing, install an Apple Distribution certificate with its private key and App Store provisioning profiles for **both** targets. Set `POCKETPASS_APP_PROFILE` and `POCKETPASS_WIDGET_PROFILE` to their profile names. Optional `ASC_AUTH_KEY_PATH`, `ASC_KEY_ID` and `ASC_ISSUER_ID` support Xcode provisioning authentication; that App Store Connect API key is separate from the APNs key.

### Signing preparation on this Windows PC

A certificate request has been prepared at:

`C:\Users\super\Documents\PocketPass-backups\backend\apple-signing\PocketPass-AppleDistribution.certSigningRequest`

Upload that file in Apple Developer **Certificates → + → Apple Distribution**, then download the resulting `.cer` file. The matching encrypted private key and its password are kept in the same protected local signing directory. Only upload the `.certSigningRequest` file to Apple.

Once the certificate is downloaded, package it with the matching local key:

```powershell
.\infra\supabase\push\.venv\Scripts\python.exe ios-app/scripts/prepare_signing.py `
  C:\Users\super\Documents\PocketPass-backups\backend\apple-signing `
  --certificate C:\path\to\distribution.cer
```

The helper checks the key match, team, certificate type, validity dates and code-signing usage before creating the encrypted P12. Xcode still validates Apple's certificate chain.

The owner's `distribution.cer` was packaged successfully on 12 September 2026. It matches the local key and team `72PMGDSD3B`, and expires on 12 September 2027. The encrypted P12, password and backed-up certificate are in the protected `apple-signing` directory above. Loading the P12 back and matching its certificate/key was verified. The two App Store provisioning profiles are still needed; signing material has not been uploaded to GitHub.

### Create the app and widget profiles

In Apple Developer **Identifiers**, register the App Group `group.xyz.pocketpass` if it does not already exist. Then create or edit these explicit App IDs:

| App ID description | Bundle ID | Capabilities |
| --- | --- | --- |
| PocketPass | `xyz.pocketpass.PocketPass` | Push Notifications; App Groups assigned to `group.xyz.pocketpass` |
| PocketPass Widget | `xyz.pocketpass.PocketPass.widget` | App Groups assigned to `group.xyz.pocketpass` |

After both IDs and their App Group assignments are saved, open **Profiles → + → Distribution → App Store Connect**. Choose the main app ID, select the Apple Distribution certificate just created, name the profile **PocketPass App Store**, generate it and download the `.mobileprovision` file. Repeat for the widget ID, using the same certificate and naming its profile **PocketPass Widget App Store**. Generate profiles only after saving the capabilities, so the downloaded files include the correct entitlements.

## GitHub TestFlight package workflow

`.github/workflows/ios-distribution.yml` is manual and follows the existing public-mirror-only policy on paid Actions minutes. It has not been dispatched. Publishing source to the mirror is a separate action; these local changes have not been pushed there.

Configure a `testflight` environment in the repository where the workflow runs:

| Variable or secret | Contents |
| --- | --- |
| Variable `APPLE_TEAM_ID` | Developer Team ID |
| Secret `POCKETPASS_SUPABASE_PUBLISHABLE_KEY` | Production public client key |
| Secret `IOS_FIREBASE_PLIST_BASE64` | Base64 of the iOS Firebase plist |
| Secret `IOS_DISTRIBUTION_P12_BASE64` | Apple Distribution certificate and private key exported as P12 |
| Secret `IOS_DISTRIBUTION_P12_PASSWORD` | P12 export password |
| Secret `IOS_APP_PROFILE_BASE64` | Main app App Store provisioning profile |
| Secret `IOS_WIDGET_PROFILE_BASE64` | Widget App Store provisioning profile |

The job uses an ephemeral signing keychain and returns the IPA and symbols as a 14-day artifact. It does not submit to Apple. The older unsigned IPA workflow remains available for sideload builds.

## First TestFlight upload

1. Finish and download the two App Store Connect provisioning profiles above. Validate their team, bundle IDs, certificate, App Group and production push entitlement before installing them in the build environment.
2. In App Store Connect, create the PocketPass iOS app record if it does not exist, using `xyz.pocketpass.PocketPass`. The widget belongs to that app; it does not need a separate store record.
3. Complete the export compliance assessment, then build and validate the signed IPA on macOS using an unused build number. Upload through Xcode Organizer or Transporter. Automated uploads would need App Store Connect authentication, separate from the APNs key already uploaded to Firebase.
4. After Apple finishes processing, open **PocketPass → TestFlight**, resolve any build compliance questions and add the beta information from `testflight/en-US.json`. Supply the owner's real feedback email in App Store Connect.
5. For the owner's initial iPhone checks, add the build to an internal testing group. Internal testers are App Store Connect users with app access; do not grant ordinary community testers account access just to make them internal testers.
6. For friends or community testers, use an external testing group. Complete the beta review contact and login information, then submit the first external build to Beta App Review. Invite the selected testers or enable a public TestFlight link once it is approved and the owner chooses the audience.

The build is exported normally for App Store Connect so it can support both internal and external TestFlight testing. The internal-only export flag is not set. Public App Store submission remains a later step, after beta feedback and the review items below have been addressed.

## Listing and privacy

Draft TestFlight description and test instructions are in `testflight/en-US.json`. Future public listing text is in `app-store/en-US.json`. The icon is reused from Android's original 1024px artwork with an opaque background. The website's iPhone mockups contain Android-layout screenshots and must not be used as native App Store screenshots. Capture the actual iPhone and iPad builds at Apple's accepted sizes after the signed build works.

The app manifest declares account/profile identifiers, email, messages, attached images, social connections, selected country, game progress, purchase/supporter history, optional step totals and app activity used to provide the service. None is declared as advertising tracking. Required API reasons cover app preferences (`CA92.1`), files in the app/App Group containers (`C617.1`), and on-device duration measurements used by the runtime (`35F9.1`). The widget only reads the App Group snapshot and uploads no data. Firebase dependencies carry their own manifests. Review Xcode's aggregated Privacy Report against the actual archive and match the App Store Connect labels to it.

The public privacy policy previously said Firebase was absent. The factual correction in `infra/supabase/website/privacy.html` was published on 12 September 2026 before Android 0.1.9-alpha, and the live page was verified against the prepared file. The previous page is backed up at `/opt/pocketpass/deploy-backups/alpha-0.1.9-20260912/privacy.html`.

## Validate through TestFlight

- Connect the signing identities, build on macOS and test the signed app on a real iPhone. The owner has uploaded the APNs keys. Compilation of iOS Kotlin metadata on Windows is not a Swift/Xcode build.
- Exercise direct and group alerts while backgrounded, screen locked and after relaunch; tap into the correct chat. Repeat with permission denied, Message Alerts off, token rotation, logout, account switching and temporary loss of connectivity. Do not use Firebase Console's generic test notification as proof of the PocketPass message path.
- Test account creation, login, image attachment, in-app account deletion, Nearby encounters, steps and widget refresh on the release candidate. Confirm background Bluetooth behavior on two physical devices.

## Review preparation for external testing and later public release

External TestFlight distribution includes Beta App Review. Complete the beta information and address applicable review requirements before inviting external testers. Public store assets and launch decisions can be finished after initial internal testing.

- Resolve the login presentation for App Review: the phone UI offers Discord login but currently has no Sign in with Apple option. Assess Apple's equivalent-login requirement before submission.
- Resolve the shared shop's Ko-fi membership entitlements for the intended App Store storefronts. External payments unlocking digital hats are an App Review concern; packaging alone does not add StoreKit purchases or establish an exception.
- Verify reporting, blocking and moderation for messages and profiles, and supply a working support contact. Prepare a dedicated review account with sample friends/conversations; credentials go in App Store Connect's review fields, not this repo.
- Complete age-rating questions, content-rights declarations, export compliance and privacy labels. Nearby uses standard ECDH/ECDSA/AES/HMAC through platform-backed libraries; do not label it as “HTTPS only” without assessing that use. No exemption answer has been chosen automatically.

References: [TestFlight overview](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview), [App Store Connect provisioning profiles](https://developer.apple.com/help/account/provisioning-profiles/create-an-app-store-provisioning-profile), [Internal testers](https://developer.apple.com/help/app-store-connect/test-a-beta-version/add-internal-testers), [External testers](https://developer.apple.com/help/app-store-connect/test-a-beta-version/invite-external-testers), [Firebase Apple setup](https://firebase.google.com/docs/cloud-messaging/ios/get-started), [Apple SDK requirements](https://developer.apple.com/news/upcoming-requirements/), [App privacy details](https://developer.apple.com/app-store/app-privacy-details/), [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/).
