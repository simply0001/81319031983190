# Keyboard caps lock

The built-in keyboard uses one-shot Shift after a single tap. A second tap within the platform's double-tap interval locks uppercase; tapping again unlocks it. The active lock has the underlined caps-lock icon and an accessibility state description. Letters, spaces, backspace, symbols, emoji, and sending a message preserve the lock while the keyboard remains open. Controller activation uses the same state transitions.

The phone message composer now owns its complete `TextFieldValue`, including selection and the IME's composing region. It applies keystrokes immediately and recognizes delayed draft acknowledgments from the store without replacing newer local text. External draft changes still load edited messages or clear sent messages. Length limiting happens before dispatch, and cursor/composition-only changes do not dispatch another draft. The string-based `PhoneTextField` API remains available for existing forms.

This fixes a draft synchronization path that could reset the phone keyboard and lose its caps-lock state. Compose's [text-field state guidance](https://developer.android.com/develop/ui/compose/text/user-input) explains the requirement to feed edits back immediately. The app continues to accept the case supplied by the system keyboard.

Validation on 12 September 2026:

- All 103 UI host tests passed, including eight new shift/draft regression cases.
- Three Android emulator tests passed: touch double tap, one-shot Shift/controller activation, and composing uppercase input with deliberately delayed draft updates. The phone test confirmed that the input session was not restarted while typing, and that clearing the draft still worked.
- Android debug and iOS shared UI metadata compiled. Native iOS runtime testing remains deferred.
- The Android release build passed, and the APK's production signing certificate and 16 KiB alignment were verified. Local artifact: `captures/PocketPass-keyboard-fix.apk`.

Published on 12 September 2026 by replacing the APK on the existing `v0.1.9-alpha` GitHub release, at the owner's request. Android's internal version code is now 23, with `minSupportedVersionCode` 23 in both live update feeds. The visible version, release title, notes, tag and publication date are unchanged. The signed APK includes the earlier typing-colour, full-screen-image and GIF changes. No installation was performed on the owner's device during publication.
