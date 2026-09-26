# Single-screen tablet layout

## Layout changes

- Landscape handhelds now use the same width-based two-pane fitting as tablets. At 1920 × 1080 and 320 dpi (960 × 540 dp), the Odin 3 layout gets an evenly spaced navigation rail, a preview pane and a content pane instead of narrowly falling back to a centred portrait column. Portrait scaling and the minimum readable pane width remain unchanged.
- `PhoneSurface` grows the design-unit scale smoothly beyond large-phone sizes, up to 0.48 dp per unit (previous maximum: 0.36). A full-width settings panel can now use roughly 595 dp instead of 446 dp on an 800 dp short-side tablet.
- Landscape scaling accounts for the navigation rail, both panes, their divider and horizontal system insets. A smaller 4:3 tablet keeps two usable panes; a narrower window stays compact instead of shrinking below the old phone maximum to force two panes.
- Compact settings subpages, profile pages and notifications share a centred content width. Portrait navigation buttons use that same maximum width instead of spreading across the entire tablet.
- The home profile stacks vertically in roomy portrait windows so its biography and country have room. Normal phone portrait and dual-display layouts retain their existing profile arrangement.
- Two widget descriptions are shorter to fit their existing settings rows.

No profile, message, database, Firebase or backend behaviour was changed. No public release was published.

## Reproduce locally

The visible AVD is `pocketpass_tablet`, using the Pixel Tablet profile and the installed Android 36 Google APIs 16 KB x86-64 image. Its native display is 2560 × 1600 px at 320 dpi (1280 × 800 dp). It uses port 5556.

From the project directory:

```powershell
Start-Process -FilePath '.toolchains/android-sdk/emulator/emulator.exe' -ArgumentList '-avd','pocketpass_tablet','-port','5556','-no-audio','-gpu','auto' -WindowStyle Normal
```

The emulator demonstration uses fixture/sample data, without signing in to or changing any real account:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PPOCKETPASS_BACKEND_ENABLED=false -PPOCKETPASS_REQUIRE_FIREBASE=true
```

Install the resulting debug app and instrumentation APK, then run:

```powershell
.toolchains/android-sdk/platform-tools/adb.exe -s emulator-5556 shell am instrument -w -r -e class com.pocketpass.app.TabletLayoutUiTest com.pocketpass.app.test/androidx.test.runner.AndroidJUnitRunner
```

Use `settings put system user_rotation 0` / `1` with `accelerometer_rotation 0` to switch this AVD between landscape and portrait. `wm size 2048x1536` simulates a 1024 × 768 dp tablet at its native density. Restore overrides with `wm size reset` and `wm density reset`.

## Verification scope

`PhoneSurfaceTest` covers normal-phone scale, minimum sizing, continuous tablet scaling, maximum sizing, narrow windows, orientation, multiple densities, system insets and pane geometry.

`TabletLayoutUiTest` contains five tests and now captures 51 UI states per viewport (40 in the initial tablet pass). Coverage includes all five tabs in both themes, settings and chat colours, scroll-to-control behaviour, profile and empty states, direct/group messages, new-group selection, notification and add-friend overlays, purchase confirmation, shop, achievements, leaderboard and all three game boards. Assertions check navigation/control bounds, portrait settings centring and the message composer/Send position above a real software keyboard. Screenshot files are under the test app's external files directory in `tablet-layout-WIDTHxHEIGHT`.

The suite uses synthetic state and does not claim to test live authentication, Bluetooth encounters, push delivery or external camera/editor integrations. Dual-display chat-colour and shop-controller behaviour is covered by the existing `ChatColoursUiTest` and `ShopSubscriptionUiTest` regression classes.

The configured production release build (backend enabled and Firebase configured) and all 602 host tests passed on 2026-09-12: 338 shared, 91 UI and 173 app tests. The release APK remains local; the emulator runs the separate fixture debug APK.

Successful visible-emulator runs on 2026-09-12:

| Viewport (dp) | Layout | Result |
| --- | --- | --- |
| 1280 × 800 | Native landscape tablet | 5/5 layout tests |
| 800 × 1280 | Portrait tablet | 5/5 layout tests |
| 1024 × 768 | 4:3 landscape tablet | 5/5 layout tests |
| 600 × 960 | Smaller portrait tablet | 5/5 layout tests |
| 411 × 914 | Phone regression viewport | 21/21: layout, chat colours and shop/controller tests |

Screenshots were visually reviewed across the tablet layouts and both themes. The final emulator is restored to its native tablet dimensions and left open with the interactive sample-data app. No real accounts or chats were modified.

## Seamless single-screen games

World Tour now uses an edge-to-edge space/map blend; Bingo and Puzzle Swap share one continuous wood backdrop. The separate screen-preview borders, shadows and surrounding Activities pattern are removed. Foreground artwork and interactive boards retain their aspect ratios, with more room allocated to controls than to decorative titles. The header stays readable in both themes.

Game pop-ups use a full-scene scrim and centred content instead of introducing rectangular patches of background. The region list, Bingo goal note, puzzle information and purchase confirmation have an explicit on-screen Back control. Existing purchase and navigation events are unchanged. Dual-display rendering keeps its original backgrounds and layout through backward-compatible defaults.

The game UI test now checks all three populated games and their pop-ups in both themes (14 captures per viewport), verifies full-scene background bounds and visible controls, and checks open/close events without buying pieces. `PhoneGameLayoutTest` covers aspect ratios, sizing bounds and compact/landscape allocation.

The configured release build and 604 host tests passed after these changes: 338 shared, 93 UI and 173 app tests. The release APK was also checked for the new game scene code. Live accounts, game progress and purchases were not changed; the visible emulator uses fixtures.

The expanded game test passed at 411 × 914 dp, 914 × 411 dp, 800 × 1280 dp and 1024 × 768 dp. The entire five-test layout suite then passed again at native 1280 × 800 dp, including the expanded game coverage and the existing settings, chat and keyboard checks.
