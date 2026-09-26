# Alpha 0.1.9 replacement build 23

On 12 September 2026, the owner requested replacement of the existing release APK while preserving the GitHub version and release notes, and requiring users to update.

- Existing release: https://github.com/Hinoaaaaaf212/pocketpass-release/releases/tag/v0.1.9-alpha
- Android `versionCode`: 23; `versionName`: `0.1.9-alpha`.
- Assets replaced: `PocketPass.apk` and `update.json`.
- Minimum supported build: 23, verified on both `links.pocketpass.xyz/updates/latest.json` and `pocketpass.xyz/updates/latest.json`.
- APK SHA-256: `7c5019e572d0e370c735bdea657ad1a8ba756d6db06f76d20e7aac3af12350a3`; size: 36,606,960 bytes.

The release ID, tag, title, notes, publication date and draft/prerelease status were verified unchanged. The APK contains the previously tested message-image/GIF, typing-colour and keyboard fixes; only its internal build number was increased for this replacement. The production signature, Firebase/backend configuration, APK package/version, ZIP alignment and native ELF alignment were verified. The release build and all 21 updater tests passed. A fresh public APK download matched the published checksum.

GitHub briefly served cached metadata after replacement. The existing feed rejected mismatched asset sizes, retaining build 22 until metadata agreed. After cache revalidation, the normal release poller published build 23 and completed successfully. Its timer remains active. No server code or configuration was changed.

Original and final release assets, notes, manifest snapshots and the final R8 mapping are saved under `C:\Users\super\Documents\PocketPass-backups\releases\0.1.9-alpha-build23`. Previous server manifest/cache metadata is backed up under `/opt/pocketpass/deploy-backups/alpha-0.1.9-build23-20260912`.
