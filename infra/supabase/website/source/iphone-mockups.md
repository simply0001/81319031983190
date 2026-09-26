# PocketPass iPhone mockups

The five `iphone-*.png` files are editable Figma exports; their optimized WebP
versions in `../assets/` are used by the homepage hero and device tour.

- Template: [iPhone 14 Pro by Anshuman Jha](https://www.figma.com/design/fhHJmkgn1U838IEK2nWIez/).
- Completed mockups: [PocketPass phone screenshots](https://www.figma.com/design/fhHJmkgn1U838IEK2nWIez/?node-id=105-2).
- Figma phone nodes: Home `105:3`, Messages `105:63`, Friends `105:123`,
  Activities `105:183`, Settings `105:243`.
- Original template frames are preserved. Screenshots use the app's Android
  phone layout and synthetic `FixtureData`, captured on 2026-09-12. They do not
  represent an iOS release or contain real conversations.
- Source captures: `captures/phone-website/Light-<Tab>.png`, 780 × 1580 px.
  The existing `TabletLayoutUiTest#allTabsAndTheirActivityPagesRenderInBothThemes`
  captured these with emulator size 780 × 1580 at 320 dpi.
- Each 442 × 888 Figma phone contains a 390 × 844 clipped screen. The capture
  is placed at 390 × 790, below 54 px reserved for the camera, without stretching
  or cropping. The camera area's fill matches the corresponding screenshot.
- Exports use the template's 2× PNG setting (884 × 1776). WebP copies use quality
  88 and method 6. The five files total approximately 390 KiB.

The Piip Creator tour capture remains available with AYN Thor selected. Switching
back to Phone while it is selected returns to Home. Both layouts retain the
selected tab for the other five screens.
