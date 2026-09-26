# Account-wide chat colours

Open App Settings → Chat Colours. Choose Default, Blue, Purple, Pink, Red, Orange, Yellow, Green or Teal, then Save. Reset to Default changes the preview until saved. Back discards an unsaved draft. The preference belongs to the sender's account, not a device or conversation. Updated clients resolve both old and new messages from the sender's current profile; older clients retain their existing appearance.

Default preserves the original blue outgoing/yellow incoming bubbles. Custom colours use the same palette for senders and recipients, including bodies, tails, attachment frames/captions and message-action previews. Text contrast across every custom gradient is tested at 4.5:1 or higher.

Typing bubbles now use the typing member's palette on both the top display and the phone layout. Concurrent group typists have separate labelled indicators so each keeps their own colour. Custom-colour tail dots no longer draw the extra offset shadow. These follow-up fixes are local; they are not included in the published 0.1.9-alpha APK.

## Storage and sync

- `public.profiles.chat_bubble_colour` is a checked preset, defaulting to `default`.
- `set_chat_bubble_colour(p_colour, p_client_operation_id, p_account_id)` is an authenticated native-account RPC. It requires the queued account to match the authenticated user, updates no other profile fields and uses the existing idempotency ledger.
- Room version 19 adds the preset and pending/error metadata without deleting data. Saving updates the cached profile and existing outbox in one transaction. The UI shows “Saved on this device. Waiting to sync online.” until acknowledgement.
- Saves remain ordered across retry backoff. Stale acknowledgements cannot replace a newer local choice. Permanent errors remain visible and can be retried with Save.
- Colours are batch-fetched for conversation members and cached message sender IDs, so a member typing their first message and former group members are included. Missing, blocked or unknown profiles/presets fall back to Default. Colours are never copied into message records.
- Existing private conversation channels carry a separate `chat_colour` invalidation. The sender's private friends channel refreshes their other devices. Neither signal creates a message, unread count, push notification or sound.

## Deployment

Applied `20260912000200_chat_bubble_colours.sql` on 2026-09-12 using the standard migration runner. The runner verified previous migration checksums; no existing migration was modified. A custom-format database backup and its validated contents list were saved privately at:

`/opt/pocketpass/deploy-backups/chat-colours-20260912.pPNe9g`

All 20 SQL checks passed both in a rolled-back preflight and after deployment. Tests use synthetic accounts and roll back all fixtures; no messages were sent as existing users. Existing Firebase configuration and the message-push worker were preserved. No public release was published.

## Verification

```powershell
.\gradlew.bat :shared:testAndroidHostTest :ui:testAndroidHostTest :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease -PPOCKETPASS_REQUIRE_FIREBASE=true --console=plain
```

Run these instrumentation classes on a disposable emulator:

- `com.pocketpass.app.ChatColoursUiTest`: Thor-sized and phone layouts, touch/controller activation, save/reset/back, both themes, all preset bubbles and captions.
- `com.pocketpass.app.data.repository.ChatColourPersistenceTest`: queued saves, refresh races, retry ordering, disk reopen, account isolation, old/former-author messages and missing-profile fallback.
- `com.pocketpass.app.data.local.PocketPassDatabaseMigrationTest`: additive database upgrades, including 18 → 19.

On 2026-09-12, all 599 host tests (338 shared, 88 UI, 173 Android app), 19 targeted Android instrumentation tests and 20 backend checks passed. The configured release APK and shared/iOS/UI Kotlin metadata compiled successfully. Both themes and all nine presets were rendered using synthetic message history on the emulator. The build was subsequently installed on Thor at the user's explicit request, preserving its signed-in account and data.

The database regression test is `infra/supabase/tests/database/chat_bubble_colours.test.sql`; run against the complete Supabase schema with pgTAP installed. It rolls back its own fixtures.

## Live realtime check — 2026-09-12

With the user's permission, Thor's signed-in account temporarily changed from Default to Pink using the new settings screen. A separate authenticated WebSocket client received the private `friends` channel update and read Pink from the profile API. The settings screen confirmed the save had synced.

The separate client then saved Teal through the owner-only RPC. Thor selected Teal automatically while the settings page remained open, without restarting or navigating away. The original Default preference was restored and verified both on Thor and through realtime/API reads. No messages were sent as the user's account, and no existing chats were modified.

Two isolated temporary test accounts additionally exercised live private direct/group conversation channels. Purple and Teal updates reached the recipient and the sender's second realtime client, including after the sender became a former group member. Recipient API reads resolved the current colour for both older and newer synthetic messages. Message-history hashes, unread markers, notification hashes and push registrations were unchanged by colour saves. The two test accounts, their two conversations and four synthetic messages were removed after the checks.

These checks cover the actual Thor UI and production realtime/API transport. Cross-account rendering, offline/retry behaviour and both themes remain covered by the automated tests above; the temporary recipient client was a protocol-level test client, not another physical Android device.
