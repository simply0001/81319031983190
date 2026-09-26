# Controller confirmation audio — 21 September 2026

Confirm feedback is explicitly enabled on selected silent Boards controls: About,
settings disclosures, Rules, Settings, Write a Note, feed sort/period filters,
note options, pinning, spoiler reveal, Yeah, replies and note editing/reporting
actions. Each control uses the same callback for controller A and touch.

The global controller/audio interception has been removed. Existing navigation,
keyboard, cancel and message sounds are unchanged. Navigation bars and rails do
not opt into the new confirmation feedback. Board buttons and tab rows default
to no added sound; individual callers opt in. Playback still respects the SFX
setting, foreground state and the player's repeat limiter.

The supplied `C:/Users/super/Downloads/PocketPass - Confirm.wav` replaces the old
confirmation clip in both platform resources. It is 1.714286 seconds, stereo
44.1 kHz PCM (302,584 bytes). Encoding starts from that source, strips metadata,
and preserves its full duration:

- Android: Vorbis quality 4, 20,853 bytes, decoded peak -10.3 dBFS.
- iOS: AAC 128 kb/s, 25,107 bytes, decoded peak -10.1 dBFS.

The original WAV is not bundled. Neither Boards music nor other effects change.
Build log: `captures/targeted-confirm-build.log`. Build and Thor installation only,
with no test runs or device interaction checks, as requested. This is a local
review build; no release version change or publication is authorised by this request.

## Other app screens

The existing event-to-sound mapping now explicitly covers previously silent
settings switches, theme choices, leaderboard scope/size, friend sorting,
mood choices, group-member selection, message tools, notification actions,
chat-colour saving, account-security buttons and manual update download/install.
Previously mapped sounds, including navigation-bar effects, are unchanged.

Local chat-colour swatches/reset and the blocked-group invitation OK button use
their shared touch/controller callbacks for Confirm. Account-security keyboard
submits defer to the corresponding event sound to avoid duplicate playback;
the keyboard's Next action retains its own confirmation.

Text-change events, message previews, audio sliders, automatic update checks and
background state changes remain without added confirmation. No global input or
audio override is installed. Build/install only, with no test run or device UI
checks. Follow-up build log: `captures/app-targeted-confirm-build.log`.

## Social, App Version and directory tabs

Social and App Version explicitly use Confirm instead of Navigation, as requested.
Joined and Explore opt into the same touch/controller confirmation as feed filters.
The main app navigation and Boards hub navigation retain their existing sounds.

Directory content fades/scales in when switching Joined/Explore, while the tab
controls stay mounted to preserve focus. A loading card replaces the old tab's
results while the new directory loads; arriving results fade in and the containing
panel changes height smoothly. Refreshing the same directory keeps its current
results visible. Reduced-motion preferences disable these transitions.

Build/install only. Log: `captures/directory-transition-confirm-build.log`.
