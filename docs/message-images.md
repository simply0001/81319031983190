# Message images and GIFs

Tap an image attachment to open it full screen. Pinch to zoom, drag a zoomed image, or double-tap to switch between fitted and enlarged views. Close and Android Back return to the conversation. Long-pressing an outgoing attachment still opens the existing message actions.

Android's existing image attachment picker accepts GIF files. GIFs are detected by their file header, copied without flattening their frames, validated with Android's image decoder and uploaded as `image/gif` with a `.gif` filename. The Android client accepts GIFs up to 10 MiB and a 2048-pixel longest edge; oversized GIFs are rejected instead of being converted into still images. JPEG/PNG preparation keeps its existing resizing and metadata removal. This adds file attachments, not a third-party GIF search service.

Coil's animated image decoder plays GIFs in both thumbnails and the viewer. Private remote attachments use the same authenticated image loader as before. The viewer closes when its message is removed from the UI, including conversation/account changes.

Migration `20260912000400_message_gifs.sql` was deployed on 12 September 2026. The `message-media` bucket remains private with its existing 10 MiB limit and membership policies. The public message API now accepts GIF MIME types as well. Previous bucket settings, API function and developer docs are backed up at `/opt/pocketpass/deploy-backups/message-gifs-20260912.AuGaPP`. The migration runner now accepts CRLF transaction markers while checking the checksum of the original file bytes; already applied migrations were not changed.

Verification: 132 database checks passed against the deployed schema using temporary fixtures rolled back after each suite. Nine emulator tests covered GIF preservation, size/corrupt-input rejection, still-image regressions, visible animation frame changes in chat and full screen, zoom, closing and Android Back on the top-screen and phone thread renderers. The full-screen viewer uses shared UI code; native iOS GIF playback/upload remains deferred with the iOS release.

The Android changes were published on 12 September 2026 in replacement build 23 of the existing `v0.1.9-alpha` release, alongside the typing-colour and keyboard fixes. Both live update feeds require build 23. The GitHub release title, tag, notes and visible version remain unchanged; no device installation was performed during publication.
