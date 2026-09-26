# Boards UI review — 19 September 2026

This work is a local review build. Publication requires fresh user approval;
see [release-approval.md](release-approval.md). No release feed, GitHub asset,
version number, server configuration, or dashboard deployment was changed.

## Fixes

- Reserve the Thor navigation area, keep the page header outside scrolling content,
  and apply phone/tablet safe areas and keyboard insets.
- Reuse Rubik section headers, the existing back arrow, round header actions,
  panel styling, selection green, and the existing settings switch renderer.
- Replace the stretched envelope header and incorrect Friends glyph with a
  Chats/Boards chooser whose icons retain their original proportions.
- Give Joined/Explore equal-width controls; put invitation codes in an expandable
  row and drafts, activity, notices, requests and push settings in Board options.
- Use compact board rows, consistent empty states and wrapping actions. Group
  owner/moderator settings into expandable sections, preserving all operations.
- Replace numbered ink buttons with colour swatches; keep extra drawing tools
  together. Drawing paper stays white in both themes.
- Make system/controller Back recognize Boards pages and dismiss local options or
  the Thor keyboard before navigating away.
- Reserve a separate area for Create Group on single-screen devices so friend
  cards never scroll behind the action button.
- Keep top-screen previews below status chrome. Wide previews put the drawing
  beside its author/caption; phone/tablet portrait previews remain stacked.

## Review scope

Real Thor: installed with the production signing certificate using `adb install -r`,
then checked the main tabs and read-only Boards navigation. Existing data retained.
No real boards, posts, invitations or moderation actions were created for review.
Populated feeds, threads, stationery, proposals, drafts and moderator/owner forms
were reviewed using emulator fixtures.

Screenshots and test logs are under `captures/boards-ui-review/` (ignored by Git).
Final validation and APK hash are recorded after the final build below.

## Final build

- Local APK: `releases/PocketPass-boards-ui-review.apk`, version 0.1.10-alpha (24).
- SHA-256: `a93afae2afbab9acb1ae29fe1ae4c69973ff2e3001aa949b97adb5bc60f6d053`.
- Host validation: 360 shared, 104 UI, 173 Android tests passed (637 total).
- Android release/debug builds and shared UI iOS metadata compilation passed.
- Thor controller B dismissed the field keyboard; system Back then returned to
  the directory. Key events target logical display 0, which owns keyboard focus;
  touches and lower-screen captures target logical display 4.
- Several broad emulator runs were interrupted by a Windows QEMU access violation
  (event 1000, exception 0xc0000005). Checks resumed with software graphics and
  Vulkan disabled; this was an emulator process failure, not an app crash.

## UI validation

- Phone and landscape tablet: Boards galleries in light/dark themes, all main
  tabs, activity lists, settings, chat colours, direct/group chats and dialogs.
- Tablet game layouts and their dialogs passed in both themes.
- Phone and tablet board forms keep Submit above the Android keyboard and remain
  reachable by touch scrolling. The automated scroll helper was corrected to
  use gestures on the scaled surface rather than unscaled programmatic distances.
- Thor field keyboard Back, fixed header/navigation bounds, and long-caption
  drawing preview have targeted UI checks. Back/B was also verified on the device.
- Test fixtures cover populated board and moderation screens. Production content
  creation, purchases, invitation sending and moderation were not exercised.
- The final New Group clearance fix is checked by asserting that the friend-list
  viewport ends above Create Group; updated screenshots are retained separately.


- Final New Group viewport regression passed at 411x914 dp and 1067x667 dp.
  Its focused test renders the actual page component; phone screenshots also
  capture the complete app shell. Broad phone traversal encountered an Espresso
  idling problem after chat-page transitions, so it is not reported as a full-suite
  pass. The affected New Group page was checked separately in both sizes.
- Final phone keyboard/Back/long-caption checks: 3 passed. Final tablet chat/dialog
  and game-layout checks: 2 passed, alongside the separately passed galleries,
  keyboard and settings checks.
- Final review APK reinstalled on Thor at 22:33 local time. Published artifacts
  remain unchanged; the Thor is left on the Boards directory.
