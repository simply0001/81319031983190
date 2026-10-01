# PocketPass

StreetPass-style social app for Android dual-screen handhelds (AYN Thor first), phones, tablets and iOS. Kotlin Multiplatform + Compose Multiplatform client, self-hosted Supabase backend on one Oracle VM.

This file is the shared brief for every coding agent here (OpenAI Codex reads it directly; Claude Code loads it through `CLAUDE.md`). Put durable lessons here, not in a private memory: date them, never add secrets, and keep this file under 32 KB (Codex truncates beyond that).

## Owner rules

### Approval
- Ask first and get a fresh, explicit yes for each of these, every time:
  - publishing a GitHub release, replacing a release APK, or changing the update feed;
  - any production change: migrations, website, admin console, developer docs, Caddy, Kong, workers, env.
- One approval covers one action. Approving a release does not approve the migrations it depends on. When you ask, list every pending prerequisite deploy together. "Dont publish without asking me again please" (2026-09-19) came after an unapproved release. The approval log is `docs/release-approval.md`.
- Standing permission (2026-09-23): build and `adb install -r` onto the owner's Thor without asking. "Push to the thor" means exactly that.
- Commit or push only when asked. `main` carries untracked work (`video/`, `.claude/`), so never reset, clean, or stage the whole tree.
- "Push to the githubs" means:
  - `origin`, plus a snapshot to the public iOS-build mirror;
  - never the release repo;
  - never `.claude/` or `video/`.
- "Make a release APK in Downloads" means build a signed APK with a bumped versionCode, and publish nothing.
- The website stays a local preview until the owner says "push the website".

### Devices, accounts, data
- The Thor runs the owner's real account. Install only release-signed production builds. Before each install, check the generated release `BuildConfig`:
  - `BACKEND_ENABLED=true`
  - URL `https://api.pocketpass.xyz`
  - a non-empty publishable key
  - package `com.pocketpass.app`, signed with the same certificate as the previous production APK
- Never install a fixture or debug build over the real app on the Thor. A custom `GRADLE_USER_HOME` skips `~/.gradle/gradle.properties` and silently produces a fixture build; that happened on 2026-09-23.
- After installing, confirm `firstInstallTime` is still 2026-09-02 13:43:54. Never uninstall the real app; `connectedDebugAndroidTest` does, so never run it on the Thor.
- UI tests may run on the Thor (owner, 2026-10-01) as a side-by-side copy: build with `-I scripts/thor-uitest.init.gradle` (app id `com.pocketpass.app.uitest`), install without `-g`, instrument `com.pocketpass.app.uitest.test`, then uninstall both.
- The owner often tests himself. "Just install, no need to test" means exactly that; otherwise do what the current request asks. The physical screen is the truth: don't "fix" things that only look wrong in adb captures.
- Keep emulator windows visible when the owner wants to watch.
- Never probe with real users' accounts or emails. Use disposable test accounts and delete them afterwards.
  - Never message real contacts from the owner's account.
  - Ask before changing the owner's live settings, and restore them afterwards.
  - The owner signs in on emulators himself; never ask for a password.
- Secrets never go into chat, the repo, logs, or an APK.
  - The owner keeps them in `C:\Users\super\Documents\PocketPass-backups\` (`backend\`, `signing\`, `production-backups\`) and Downloads.
  - A Resend key once leaked through diagnostic output and had to be rotated.
- OneDrive is gone (2026-09-26). The only path is `C:\Users\super\Documents\PocketPass`; never recreate `C:\Users\super\OneDrive`.

### Code
- No code comments: no KDoc, no `//`, no XML/SVG `<!-- -->`. Name things instead. Before finishing, grep your diff for `/**`, `//` and `<!--`. The owner asked for this twice (07-29, 09-04).
- Kotlin official style, 4 spaces, trailing commas.
- Naming patterns:
  - classes: `*StateHolder`, `*UiState`, `*Event`
  - repositories: `Fixture*` / `Room*` / `Production*` / `Supabase*`
  - platform code: `Ios*` actuals, `.android.kt` / `.ios.kt` files
  - test tags: snake_case (`"message_send"`)
- Applied migrations are immutable; fix mistakes with a new migration. Deploy SQL before an APK that needs it.

### Design and motion
- Match Figma 1:1 with the real exported assets. Never redraw them, and never swap in platform icons or system emoji. When the owner asks for your own design, don't copy Figma.
- Match the rest of the app:
  - glyphs use the round badge;
  - selected options use the Theme selector's green gradient and border;
  - conversation rows share one blue style.
- Motion stays restrained: slide, translate, expand or zoom. No fades, no big pop-ins, no flashes on refresh.
- Controller highlight:
  - It never disappears: not on A, B, tab switches, or entering and leaving Shop, Games or Leaderboard.
  - It animates smoothly, hugs borders rather than text, and moves in visual order.
  - A/tap on actionable items plays the confirm sound; the nav bar keeps its own sounds.
- Change only what was asked, and keep everything else exactly the same. Don't touch what was just fixed.
- For big features, ask many precise multiple-choice questions first. Video work starts with a storyboard and waits for approval.

### Copy
- Write short, plain sentences with no AI-sounding filler and no "you can" padding. Don't describe what a picture shows; alt text is a few words. Use the owner's exact wording when he gives it.
- Terms: "Piip", not Mii, in user-facing text. The 8-digit **Friend Code** (shown as `1234 5678`) replaces "account ID".
- Credits: k0o1, BrocoDev, simply (lowercase), and saby for "Official soundtrack, SFX".
- Release notes: the owner's text verbatim, formatted like earlier releases (blank line between paragraphs), app changes only unless told otherwise.

## Current state (2026-09-26)

### Android
Published: **0.2.2-beta, versionCode 28** (2026-10-01, floor 23): single-screen Piip editor and gamepads, Edit Info, Board alert levels, @mentions, 12-hour clock.

Earlier releases:
| Version | versionCode | Notes |
| --- | --- | --- |
| 0.2.1-beta | 27 | 09-26; Show Boards, Block Invites, Health Connect |
| 0.2.0-beta | 26 | 09-23; a rename of 0.1.11-alpha (25) |
| 0.1.10-alpha | 24 | tag exists, no GitHub release |
| 0.1.9-alpha | 23 | forced in-place APK replacement |
| 0.1.8-alpha | 21 | 09-12 |
| 0.1.7-alpha | 20 | 09-09 |

### Backend
Migrations are applied through `20261001000200` (Boards alerts; admin token history). `20260928000400_oauth_profile_username`: auth metadata keeps only the username, so OpenID `profile` exposes nothing else, and connected apps can join `friend-presence:` channels (Realtime v2.102.3 needs broadcast read even for presence). Bans applied 2026-09-28; the privacy and delete-account pages describe ban records. `20260928000200_api_hardening`: bans close connected apps, per-app counters are sharded, all `/v1` errors use the envelope (Caddy rewrites PostgREST's 400/401/404; header `X-PocketPass-Error: api`), Kong's `/v1` per-IP limit is 1,000,000/min, and the privacy API covers Block Invites. The 09-26 changes:
Applied 2026-09-27 (dry run, fresh backup): `20260927000100_worker_rpc_service_role_checks` (push and branding worker RPCs refuse all but `service_role`; `health.sh` checks grants), `20260927000200_drop_unused_rpcs`, `20260927000300_piip_wording`. Backups keep privileges.
- `20260925000100_split_social_privacy` and `20260926000100_email_privacy`;
- Resend SMTP moved to implicit TLS on port 465;
- privacy notice live at `pocketpass.xyz/privacy`;
- Kong allows public `POST /auth/v1/signup` only for `@users.pocketpass.xyz`.


### iOS
Not released; TestFlight comes first.
- Sideload IPAs come from mirror CI; the APNs key is in Firebase.
- No updater on iOS (2026-10-01): `appUpdatesSupported=false` hides Update Alerts and the App Update page.
- The Distribution certificate and the encrypted signing package are in `PocketPass-backups\backend\apple-signing\`.
- App Store profiles (app and `.widget`, with the App Group) are in `apple-signing` and mirror secrets (10-01). The upload key (`ASC_*`, Admin) is in `backend\app-store-connect-api\`.
- Never tested on an iPhone: street-pass with the Thor, APNs delivery, widgets under Sideloadly signing, and the third-party OAuth callback.

### Unverified
- End-to-end email sign-up after the OTP hotfix.
- The consented OAuth `email` flow.
- A backup restore.
- Same-account BLE skip on two real devices.
- Boards push on a real handset.
- An external OAuth app running the Boards flow.

### Test drift (not regressions)
- `public_api_followups.test.sql` has 3 stale expectations.
- Older pgTAP files assume an empty database.
- `kofi_supporters.test.sql` has 6 failures on production: counts that assume an empty database, plus the permission catalog (8 expected, 19 real).
- `WidgetBindingStoreTest` has 3 failures on Windows.
- On the Thor, 3 `TabletLayoutUiTest` cases fail: no soft keyboard with the controller attached, and App Settings scrolls under the tab bar.
- `PuzzleSwapFocusUiTest` compiles but has never run.
- `:app:lintRelease` stops on 2 `MissingPermission` errors at `notify()` in `push/BoardNotifications.kt` and `push/MessageNotifications.kt`; both are guarded by `allowed()` and predate 2026-09-27.
- There is no local Postgres.

### Thor quirks
- System WebView 109.
- `PocketPassReducer.reduce` was over the ART compile limit (17,833 instructions); on 2026-09-27 it was split into per-area helpers. Check on the Thor that it is no longer interpreted.

### Open asks
- The top-screen notification ticker should scroll a little, then reset (09-12).
- Declutter the consent/permission review, move it to the top screen, and flag dangerous permissions (09-21).
- Before any store release: update the privacy policy and declare `READ_STEPS`.
- Moving Kong to Envoy is not started.
- Realtime uses protocol V2 (supabase-kt 3.8 default) since 2026-09-27, checked on the Thor. The server (Realtime v2.102.3) serves V1 and V2 side by side.
- The public API cannot read or set Block Invites: `set_invite_privacy` refuses OAuth tokens, and `privacy.get`/`privacy.set` cover Block Messages only.

### Video (`video/pocketpass-presentation`, Remotion + Cavalry, untracked)
- Current review: v9 (1:41.2, 60 fps, 2026-09-27): Remotion `PocketPassOpeningV6` + the Cavalry body (`cavalry-v6/PocketPass`, lossless PNGs), built by `scripts/assemble-presentation-v9.py`; shared background `src/opening-v6/background-motion.ts`. Steps: project README.
- The accepted base is Cavalry v2 (1:34).
- Cavalry MP4s encode BT.601 but label BT.709 (reds too hot, greens too dark, v3 included). Render finals as PNG sequences; read old MP4s with `scale=in_color_matrix=bt601`.
- Retime a Cavalry scene in the `.cv` JSON: double `keyframe.timeOffset`, time markers and the composition range. Footage `time` counts frames at the composition rate, so recorded clips need `/2`. Write keyframes into the JSON; thousands of `api.keyframe` calls stall Cavalry.
- Cavalry has no CLI on this licence (Enterprise-only). Drive it with computer use: the JavaScript Editor runs `out/cavalry-check/next.js`; scripts report back by writing files.
- Rules: real UI only, no fades or full-page slides, Cocoon-style continuous background, upright Thor PNGs.

### Docs
- `docs/` holds feature notes and dated logs. `docs/2026-09-22-handoff.md` is the detailed log for 22 to 26 September; this file wins where they differ.
- Developer docs last deployed 2026-10-01 (Boards alerts).
- `public-api/build-examples.py` regenerates examples, but `workflow-examples.json` has pre-Block Invites wording for `example-blocking`; regenerating would revert it.
- iOS still defaults to 0.1.8 (build 21) in `ios-app/project.yml` and `APP_STORE.md`. Pick the version at the first TestFlight upload.

## Repos
- `origin` = `Hinoaaaaaf212/pocketpass`: private source of truth.
- `mirror` / `public` = `simply0001/81319031983190`: public, the only place CI runs.
  - It carries the private tree exactly, `infra/` and README included: one commit per private commit, with the same tree and message, and no private history.
  - Recipe per private commit: `git commit-tree <commit>^{tree} -p mirror/main -m "<same message>"`, then `git push mirror <new>:refs/heads/main`. Before 09-26 the mirror got HEAD minus `infra/` with a blank README.
  - `.claude/` and `video/` are never committed, so they never reach it. Docs-only commits carry `[skip ci]`.
- `Hinoaaaaaf212/pocketpass-release`: public releases, written only by `scripts/publish-release.ps1`.
- Every workflow is guarded by `if: github.repository == 'simply0001/81319031983190'`. Never remove the guard; macOS minutes are billed on the private repo.
  - `ci.yml`: push/PR; Ubuntu JDK 21 build + tests, macOS simulator tests.
  - `ios-app.yml`: unsigned IPA.
  - `ios-smoke.yml`: 30 s simulator launch crash watch. Run it before handing out any IPA.
  - `ios-distribution.yml`: signed archive, never dispatched.
  - `ios-spike.yml`.

## Layout
- `app/`: Android host (`com.pocketpass.app`): activities, `AppContainer` manual DI, BLE, workers, FCM, updater, Glance widgets, the WebView Mii renderer, gamepad input.
- `shared/`: KMP (androidLibrary, iosArm64, iosSimulatorArm64). Contents:
  - domain, Room DB (version **22**; schemas in `shared/schemas`), repositories;
  - Supabase sources, `sync/` (outbox, `RealtimeRuntime`), `nearby/` protocol + crypto;
  - `feature/` state holders, `boards/`, `steps/`, `widget/`, `state/PocketPassStore`, `model/`.
- `ui/`: Compose Multiplatform screens:
  - `PocketPassDisplays.kt`, `DesignSystem.kt`, `screens/`, `phone/`, `controller/ControllerFocus.kt`, `theme/PocketPalette.kt`;
  - resources in `composeResources/`. `Res` is internal, so anything that reads fonts or assets must live in `:ui`.
  - `:ui` needs `androidResources.enable = true` in its `android {}` block, or `Res.readBytes` crashes on Android. iOS reads its backend config from generated Kotlin, not Info.plist.
- `ios-app/`: XcodeGen `project.yml`, a UIKit host (`AppDelegate` → `PhoneEntryKt.PhoneAppViewController()`), the Swift WidgetKit `PocketPassWidget`, `scripts/archive.sh`, `APP_STORE.md`.
- `ios-spike/`: a separate Gradle build (`./gradlew -p ios-spike`), historical.
- `infra/supabase/`: the backend. `README.md` (58 KB) is the operations manual.
- `tools/mii-renderer/`: reproducible renderer build.
- `scripts/`: release and asset helpers.
- `releases/<version>.md`: release notes (APKs there are ignored).
- `docs/`: feature notes, the handoff, the approval log.
- `captures/`: ignored scratch space for review APKs and screenshots.
- `.toolchains/`: ignored vendored SDK/JDK/emulators.

## Architecture
- **Displays.** `MainActivity` shows `TopDisplayApp`. `CompanionDisplayPresentation` puts `BottomDisplayApp` on the `DISPLAY_CATEGORY_PRESENTATION` display, preferring 1240x1080.
  - Design sizes: top 1920x1080, bottom 1240x1080. `DesignSurface` treats Figma coordinates as units.
  - Without a companion display, the app shows `PhoneApp` (1240 units on the short side). Tablets get a rail + preview + content layout.
  - `DisplayRoles.BOTTOM_PRIMARY_DEVICES` (Anbernic RG DS) swaps roles.
  - `PocketPassLauncherActivity` relaunches on the top screen.
  - On 4:3 panels, full-width cards (`PocketPanel` x 50 / w 1140) stretch, and their children must use `anchoredBounds`.
  - `PocketPassTheme` forces left-to-right (10-01); `designBounds` broke on RTL phones. Test: `RightToLeftLayoutUiTest`.
- **State.** `PocketPassStore` (shared) owns `PocketPassUiState` and sends `dispatch(PocketPassEvent)` to the pure `PocketPassReducer` or feature holders. `routes` is a hand-rolled back stack; navigation3 is unused except `NavKeyMarker`, which must stay `api()`. Android wraps the store in `PocketPassViewModel`, iOS in `PhoneEntry.kt`.
- **Data.** Room is the UI source of truth; DataStore holds only preferences. Offline writes use an outbox with client operation UUIDs, drained by WorkManager or BGTaskScheduler (iOS). Realtime only invalidates; the app reconciles from REST.
  - Fixture mode is on when `!BACKEND_ENABLED` or the key is blank.
- **Backend.**
  - Stack: Oracle VM (OCI eu-stockholm-1), Supabase v0.8.0 pinned `241bb11c`, Postgres 17, Kong, Caddy, `board-media` (Python), `message-push` (compose profile `push`). No Edge Functions.
  - Hosts:
    - `pocketpass.xyz`: website + feed mirror
    - `api.`
    - `links.`: App Links, auth callback, `/oauth/consent`, update feed
    - `developer.`: portal + `/docs`
    - `admin.`: console, 18 permissions; owners are set only via psql
    - `studio.`: owner-gated
  - DNS is at Dynadot; keep the Resend records.
- **Auth.** Three ways to sign in:
  - email 6-digit OTP (10 min) via Resend;
  - Discord OAuth, enabled in production (`infra/supabase/config.toml` is the local CLI config, not production);
  - username accounts, `<username>@users.pocketpass.xyz` with a password (no reset; `/auth/v1/recover` returns 404; link an email in Settings > Account).

  Kong rate-limits OTP, signup, password and friend-code requests. The mobile callback is exactly `pocketpass://auth/callback`.
- **Public API.**
  - OAuth 2.1 with PKCE S256 via GoTrue; the token role is `api_client`.
  - Calls are `POST /v1/<resource>.<action>`, mapped to `api_v1_*` RPCs.
  - New scopes need re-consent. Staff tools are refused to OAuth identities.
  - Docs are generated by `infra/supabase/public-api/update-docs.mjs` (`--check`). The Boards contract is `public-api/boards.json`.
  - Adding a scope means updating the scope assertions in the `public_api_*`, `developer_portal` and `public_api_role` tests, plus the docs and README tables.
  - Koji ("Sign in with PocketPass") is in beta.
- **Nearby (raw BLE).**
  - The advert carries a version + nonce. The handshake is P-256 ECDH + AES-256-GCM, and the server resolves profiles.
  - The server caps live passes at 64 per account. The client returns unexposed passes to the pool.
  - Never delete credentials server-side (receipts FK cascade); mark `consumed_at`.
  - Receipts use PK (encounter_id, reporter_id, transcript_hash).
  - Same-account devices skip each other via a daily HMAC tag in the low half of the nonce.
  - iOS cannot advertise service data, so iOS always initiates. Android scanners accept bare-UUID adverts.
  - Tokens: 30 for a first meeting, then 5 per later day, 1 per pair per UTC day.
  - On a confirmed encounter the Thor LEDs pulse (`ThorEncounterLedFlasher`, `joystick_light_enabled`); rejected receipts don't pulse.
- **Steps.** Health Connect is preferred when read access is granted; manual/unknown records are excluded and overlaps de-duplicated. Fallback is `TYPE_STEP_COUNTER`; iOS uses CoreMotion. The Thor has no Health Connect provider.
  - Server: `report_daily_steps` pays 1 token per 400 steps, up to 25 per day.
  - The setting defaults to off.
- **Puzzle Swap.**
  - The first puzzle is the player's own Piip (4x4); after that come art panels in order.
  - Pieces come from encounters, from 15 tokens each, or from steps (at 5,000 and 10,000 a day).
  - To add panels, follow the README "Puzzle Swap" section. Retire a panel with `is_active=false`; the migration cannot be rolled back.
- **Streaks.** A streak counts local days with an encounter. The weekly recap (`pocketpass-weekly-recap` cron) runs Sunday after 18:00 local, using `private.user_clock_offsets`.
- **Boards.**
  - An app-within-the-app inside Messages; L/R cycles Boards, Messages, Activity.
  - Posts are text or one 4:3 drawing: 800x600 paper, with the pixel pen on a 320x240 grid. Limits: posts 1,000 characters, replies 500.
  - Private tables behind `boards_query` / `boards_mutate`. The settings table is a singleton, so updates need `WHERE singleton = true`.
  - Emergency switch: dashboard → Feature controls. Never drop tables or roll back Room.
  - Retention cron runs 03:23 UTC. Music: `bgm_boards`.
  - Alerts (`20261001000100`, applied 2026-10-01): one kind per member per event: `mention`, `reply` (to their note or reply), `yeah`, `note`, else `activity`. `board_preferences.push_level` (`all`/`personal`/`mentions`) filters pushes in `board_push_eligible`; claims add `kind`/`actor_name`/`board_name`. @ picks come from `boards_mention_candidates`; mentions live in `board_post_mentions`.
  - Settings > Notifications has Board Alerts (switch + slider) and Board Notifications (each board's `push_enabled`). Show Boards off hides them, sends `p_board_enabled=false` and restores the old Messages shell.
- **Privacy.** Block Messages rejects new DMs. Block Invites rejects group/board invites and friend requests. Triggers enforce both at the write boundary; the API returns 403 `FRIEND_REQUESTS_BLOCKED`. Show Boards is local only.
- **Bans** (`20260927000400_account_bans`, permission `bans`). A PostgREST pre-request guard (`PGRST_DB_PRE_REQUEST`) answers a banned account with 403 `ACCOUNT_BANNED`, except `get_my_account_ban` (the ban screen). `has_block_between`/`board_blocked` treat banned accounts as blocked by all; nothing is deleted, so lifting restores. The `before-user-created` hook refuses the same email, Discord ID or (username accounts) network; signals are HMAC hashes. README: "Account bans".
  - Email is visible only to the owner, permissioned admins, and apps granted the `email` scope.
- **Chat.** Account-wide `chat_bubble_colour` presets. Images and GIFs up to 10 MiB. Push goes through Firebase `pocketpass-e005c` as data-only FCM; iOS alerts are generic.
  - Emoji are only the Sudofont DS glyphs, plus the added crying face (`ui/.../components/Sudofont.kt`).
- **Shop/Ko-fi.** Wear equips immediately and rolls back if the server refuses. Ko-fi supporters get a 36-day window; payments match by email or by `@username`/friend code in the message.
  - The leaderboard server returns 100 players; the app shows 20/50/75/100 (default 20).
- **Updater.** `AppUpdateCheckWorker` reads `https://links.pocketpass.xyz/updates/latest.json`. The VM poller rewrites that file every 5 minutes from the release repo.

## Build and test on this PC
- JDK: use the Android Studio JBR (`C:\Program Files\Android\Android Studio\jbr`); the java on PATH is Oracle 25.
  - PowerShell: `$env:JAVA_HOME=...; .\gradlew.bat ...`
  - Git Bash: `export JAVA_HOME=...; ./gradlew ...` (`MSYS_NO_PATHCONV=1` breaks the wrapper).
- Build output is redirected to `%LOCALAPPDATA%\PocketPass\gradle\{app,shared,ui}`. The APKs are at `...\app\outputs\apk\{debug,release}\`, not `app/build`.
- Production values come from `~/.gradle/gradle.properties` (`POCKETPASS_BACKEND_ENABLED`, `POCKETPASS_SUPABASE_PUBLISHABLE_KEY`).
  - Fixture build: `-PPOCKETPASS_BACKEND_ENABLED=false` (emulators only; the Petah Griffin profile; the Mii editor is off).
  - Release: `:app:assembleRelease -PPOCKETPASS_REQUIRE_FIREBASE=true`. Signing comes from `~/.pocketpass/signing/signing.properties`; `google-services.json` from `..\PocketPass-backups\backend\`.
- Host tests: `:shared:testAndroidHostTest :ui:testAndroidHostTest :app:testDebugUnitTest`.
  - For shared/ui changes, also run `:shared:compileCommonMainKotlinMetadata :ui:compileCommonMainKotlinMetadata`.
  - iOS linking and simulator tests need macOS, so use mirror CI.
  - Lint: `:app:lintRelease`.
- Instrumented tests: `$env:ANDROID_SERIAL="emulator-5554"; .\gradlew.bat :app:connectedDebugAndroidTest`. For one class: `adb shell am instrument -w -r -e class com.pocketpass.app.<Test> com.pocketpass.app.test/androidx.test.runner.AndroidJUnitRunner`.
- Backend tests:
  - pgTAP `infra/supabase/tests/database/*.test.sql` needs the VM.
  - Boards: `BOARDS_TEST_PUBLIC_API=1 node --test infra/supabase/tests/boards/*.test.mjs` (PGlite via `npm ci` in `push/`).
  - Push: `npm test` and `python -m unittest -v test_worker.py` in `infra/supabase/push`.
  - Board media: `python -m unittest discover -s infra/supabase/board-media -p test_server.py`.
- Tooling quirks:
  - Bash heredocs mangle large files. Create files with a write tool; do multi-line edits with a Python script file (`newline=''`).
  - `core.autocrlf=true`, and working-tree endings are mixed. Keep `.sh`, `.sql` and everything under `infra/supabase/**` LF.
  - `gradlew wrapper` swaps the repo's short, comment-free `gradlew`/`gradlew.bat` for Gradle's stock scripts. To bump Gradle, edit `distributionUrl` in `gradle-wrapper.properties` only.
  - `git cherry-pick` has no `-q`.
  - `adb shell input text` scrambles capitals, so type lowercase. Wait between taps with `adb shell sleep N`.

## Devices and emulators
- **Thor.** The owner's Thor is `38c90fe5`: Android 13, top 1920x1080, bottom 1240x1080.
  - Key events go to logical display 0. Read touch and capture ids from `dumpsys display` / `dumpsys SurfaceFlinger --display-id`, because they change.
  - `run-as` does not work. `scripts/capture-thor-tabs.sh` captures both screens.
- **Other hardware.** Samsung A54 `RZCW90YL9LV`: USB drops in and out. Other targets: AYANEO Pocket DS (top-primary), Anbernic RG DS (bottom-primary), Odin 3, tablets.
- **AVDs.** They live in `.toolchains/android-sdk`; start them with `emulator/emulator.exe -avd <name>`.
  - `pp36`: API 36 with 16 KB pages, Pixel 8, `-port 5554`.
  - `pp30`: API 30.
  - `pocketpass_tablet`: Pixel Tablet.
  - `pocketds`: two displays, 1920x1080 plus 1024x768, `-gpu host -no-snapshot -port 5556`.
- **pocketds details.**
  - Bottom is display 2. Tap at (71 + x*0.7111, y*0.7111) via `input -d 2 tap`.
  - In-app back is `KEYCODE_BUTTON_B`; `KEYCODE_BACK` closes the app.
  - Scroll by swiping in the side bar: `input -d 2 swipe 20 700 20 200 1200`.
- **Dual-screen on a phone AVD.** `settings put global overlay_display_devices 1240x1080/240`; read the overlay id each boot. Don't add or remove it while the app runs; it segfaults.
- **Emulator gotchas.**
  - QEMU 0xc0000005: use software graphics with Vulkan off.
  - With `-gpu swiftshader_indirect`, `pp36` segfaults (exit 139) within minutes, usually on Messages or Settings, whatever the app version. `-gpu host` got through the same screens without a crash (2026-09-27).
  - `pp36` has 2 GB of RAM: after hours of uptime it swaps, Bluetooth can stick in `BLE_TURNING_ON`, and the app ANRs inside Android's own view code. Cold boot (`-no-snapshot-load`) before judging performance.
  - Emulators have no step sensor.
  - Non-exported receivers ignore `am broadcast` on API 36.
  - Install with `adb install -r -g`, then tap Allow Permissions.

## Releasing Android
1. Feature commits first. Then bump `versionCode`/`versionName` in `app/build.gradle.kts` and put the owner's notes in `releases/<version>.md`.
2. Apply any backend migrations the build needs, with separate approval.
3. In PowerShell with `JAVA_HOME` set to the JBR, run `scripts\publish-release.ps1 -NotesFile releases\<v>.md -MinSupportedVersionCode <floor> -DryRun`. It builds, zipaligns, checks 16 KB zip/ELF alignment and stages in `%TEMP%`.
4. Verify with `aapt2 dump badging` and the BuildConfig checks above.
5. Rerun with `-SkipBuild` to publish. It needs `gh` auth, and it refuses if the tag already exists.
6. Always pass the current floor (23 today). `poll-app-update.sh` drops the floor when it's omitted, which silently un-forces old clients.
7. After at most 5 minutes, check both `links.pocketpass.xyz/updates/latest.json` and `pocketpass.xyz/updates/latest.json` for version, notes and SHA-256.
8. Push `origin main` yourself; the script never touches the source repo.
- To replace the APK in an existing release, bump only `versionCode` and keep the name, title and notes. Raise the floor to force the update.

## Backend operations
- Connect with `ssh pocketpass-vm` (user `ubuntu`, passwordless sudo, in the docker group). fail2ban bans for 1 h after 5 failures in 10 min, so never loop over users or keys.
- `/opt/pocketpass/app` is a plain file tree, not a git checkout.
  - To deploy a file: `scp` it to `/tmp/<stage>/`, then `sudo install -m 644` (scripts: `-m 755`).
  - `.env.production` is root-owned `0600`. Never replace it or copy local secrets over.
  - Run scripts as root: `cd /opt/pocketpass/app && sudo ENV_FILE=/opt/pocketpass/app/infra/supabase/.env.production infra/supabase/scripts/<script>.sh`.
  - Run remote scripts from an scp'd file, not a piped heredoc. `migrate.sh` and `psql` read stdin.
- Deploy pattern:
  1. `backup.sh`: encrypted `.age` plus a `.sha256`. Copy it off the VM to `PocketPass-backups\production-backups\`.
  2. Save the files you're about to change under `/opt/pocketpass/deploy-backups/<topic>-<date>/` (older copies are in `/opt/pocketpass/rollback/`).
  3. Dry run with rollback only: `begin; <migration> <pgTAP> rollback;` into `docker exec -i supabase-db psql -U postgres -d postgres -v ON_ERROR_STOP=1 -Atq -f -`.
  4. `migrate.sh`.
  5. pgTAP, `health.sh`, `validate-auth-production.sh`, `validate-public-api-production.sh`.
  6. Record the SHA-256 hashes.
- Caddy, compose and web changes:
  - Validate the Caddyfile in a `caddy:2.11.4-alpine` container.
  - Recreate with `source scripts/common.sh; compose up -d --no-deps --force-recreate --wait caddy`. Caddy runs `admin off`, so there is no reload. The first `health.sh` after a recreate may fail transiently.
  - The website, admin and developer files are bind-mounted and go live on copy.
  - After changing `PUBLIC_API_ENABLED`, recreate `auth` and `docker restart supabase-kong`; otherwise requests return 502.
- Never:
  - `docker compose down -v` or the upstream reset;
  - `restore.sh` on the live server;
  - flush iptables;
  - add `ports:` for Kong, Supavisor or Studio;
  - use Watchtower or floating image tags.
- Timers:
  - backup 03:30 UTC (7 days kept), off-site pull 04:15;
  - health every 30 min, update poll every 5 min;
  - alerts in `/var/log/pocketpass-alerts.log`.

## Subsystem recipes
- **Mii renderer.**
  - Built on upstream `ariankordi/mii-creator` at pinned `1cd6b7d`, plus `patches/pocketpass-renderer.patch`, output to `app/src/main/assets/mii_renderer/`. Never auto-update upstream.
  - Rebuild with `tools\mii-renderer\build.ps1 -BunExecutable C:\Users\super\.bun\bun-windows-x64\bun.exe`:
    - First rewrite `tools/mii-renderer/src/renderer.ts` and `dist/renderer.js` with LF endings.
    - Do a two-pass hash update: the build fails with the new hash; put it in `bundle.sha256`; run `-UpdateBundle`; then run once more without flags.
  - `RENDERER_VERSION` is duplicated in `IosMiiRenderController`; keep it in sync.
  - Hats: `hat_N.glb` is type N-1. hat_10 is the Halo, hat_11 the Hijab. Two-colour hats: `docs/two-colour-hats.md`.
  - Headless check: serve the renderer dir over http, drive it with puppeteer-core and Chrome (`--use-gl=angle --use-angle=swiftshader --enable-unsafe-swiftshader`), inject `window.PocketPassNative`, send base64 JSON to `PocketPassMiiRenderer.receiveBase64`. First boot takes ~2.5 min.
  - Framing (2026-10-01): WebView clamps the renderer's CSS width × DPR² buffer to 4096 px; `LEGACY_PROJECTION_X_SCALE` and the 92 px lift offset the Thor's clamp (4428→4096). Phones/iOS load `?fit=height`, rebuilding that framing clamp-free. Re-check the Thor if the other path changes.
- **iOS gotchas.**
  - Every CMP target needs `CADisableMinimumFrameDurationOnPhone=true` in Info.plist, or it crashes at launch.
  - `ui/.../files/figma/home_avatar_petah.svg` is really a PNG. Check magic bytes before copying assets into xcassets.
  - A top-level function added to an existing shared file may not resolve from `:app` (KMP incremental bug). Put it in a new file or make it a member.
  - The Keychain service is `xyz.pocketpass.securestore`.
  - Sideloadly's "Remove Extensions" must stay off.
  - Crash reports: Settings → Privacy & Security → Analytics Data; `lastExceptionBacktrace` names the function.
- **Glance widgets.** `Res` lives in `:ui`, so bitmap renderers go there. A Glance column holds at most 10 children.
- **Controller focus.** Horizontal card rows scroll themselves to the focused card via `snapshotFlow`. Check Up/Down/L/R order closely; the owner reports focus bugs often.
  - Single screen (2026-10-01): `PhoneApp` provides focus once `GamepadPresence` sees a gamepad or its key. Pages/dialogs add `LocalControllerFocusLayer` offsets (routes 100, dialogs 1000) behind barriers; `ControllerOverlayFocus`/`ControllerRouteFocus` hand focus over. Page back headers aren't targets; dialog close buttons are. No iOS gamepad input yet.
- **Single-screen Piip editor.** `ui/mii/MiiEditorSingleScreen.kt` + `MiiSingleScreenLayout.kt`: preview on top when upright, Thor-style panel on the right when width ≥ 1.2× height. Y saves.
