# PocketPass for AYN Thor

PocketPass is a native Android 11+ Jetpack Compose app built specifically for
the AYN Thor's two physical touch displays:

- top display: 1920×1080
- bottom presentation display: 1240×1080

The app renders the Figma design at native physical-pixel dimensions, shares state
between the activity and presentation display, and implements the five main
destinations, message detail, group chats (created from Messages, managed from
the thread header), activity shuffle, and local settings controls.

## Build

Install Android Studio with SDK 36 and JDK 17, then run:

```powershell
.\gradlew.bat assembleDebug
```

The Figma-exported resources live under `app/src/main/res/raw` and must not be
redrawn or replaced with platform icons.

## Thor verification

With the Thor connected:

```powershell
adb shell dumpsys display
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Launch PocketPass from the top screen. The activity detects the 1240×1080
presentation display and opens the lower Compose surface automatically.

## Android 16 and the minSdk floor

The app targets API 36. Two emulators cover what no owned device does: Android 16
on a 16 KB page-size kernel, and the API 30 minimum. Both come from the vendored
SDK (WHPX acceleration must be available; check with `emulator -accel-check`):

```powershell
$sdk = (Resolve-Path .toolchains\android-sdk).Path
$env:ANDROID_SDK_ROOT = $sdk; $env:ANDROID_HOME = $sdk
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$sdk emulator "system-images;android-36;google_apis_ps16k;x86_64" "system-images;android-30;google_apis;x86_64"
& "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n pp36 -k "system-images;android-36;google_apis_ps16k;x86_64" -d pixel_8
& "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n pp30 -k "system-images;android-30;google_apis;x86_64" -d pixel_4
& "$sdk\emulator\emulator.exe" -avd pp36 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel on -port 5554
& "$sdk\emulator\emulator.exe" -avd pp30 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel on -port 5556
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :app:connectedDebugAndroidTest
```

`adb -s emulator-5554 shell getconf PAGE_SIZE` must print `16384`.
`scripts/publish-release.ps1` refuses an APK whose zip entries or ELF load
segments are not 16 KB aligned (two transitive native libraries ship). Lint
(`.\gradlew.bat :app:lintRelease`) is expected to report no errors.

Behaviour worth knowing on API 36: the landscape lock is ignored on displays
of 600 dp or wider (none of the supported handhelds qualify); the temporary
`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out is deliberately not
used. Edge-to-edge is enforced; `MainActivity` calls `enableEdgeToEdge()` and
the phone layout reads its insets from `PhoneSurface`. The Nearby foreground
service always calls `startForeground` first and the controller defers a
`stopService` that would race a pending start.

## Single-screen devices

On a phone or any device with no presentation display the app runs its own
single-screen UI (`ui/phone`) instead of the two panel designs. One design
surface spans the screen: the short side is 1240 design units, so every touch
component keeps its handheld size, and screens are laid out as flowing columns and
grids rather than fixed coordinates. Each tab keeps its stage and deck: in portrait
the stage is a header above a scrolling deck and a bottom tab bar, with detail
content (threads, profiles, notifications, games, settings subpages) pushed as
pages; in landscape a navigation rail sits beside a stage pane and a native-width
deck pane on one continuous backdrop. Android's status and gesture bars stay
visible, the system keyboard replaces the on-screen one, and rotation is left to
the device — the landscape lock and immersive mode only apply while a companion
panel is attached.

## Nearby Encounters

Nearby Encounters uses raw Bluetooth Low Energy rather than Nearby Share. The
advertisement contains only the protocol version and a random invitation nonce.
Devices exchange server-issued one-time credentials through a mutually
authenticated P-256 ECDH handshake, then confirm the exchange with AES-256-GCM.
Profiles are resolved by the server after the encrypted receipt is uploaded; no
profile or account identifier is broadcast over BLE.

After authentication, PocketPass explains and requests the Android permissions
needed for the current OS version. If a required permission is later revoked,
opening PocketPass posts a repair notification with a **Fix** action. If Android's
notification permission itself is revoked, the same repair flow appears in-app.

The foreground service continues scanning and advertising while the app is
backgrounded or the screen is off and is restored after boot or app replacement.
No app can execute while a device is physically powered off; encounters resume
after Android boots and restores the service.
