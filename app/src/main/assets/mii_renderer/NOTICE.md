# PocketPass Mii Renderer

This local renderer is adapted, with permission, from:

- `ariankordi/mii-creator`
- Branch: `renderer-ffl.js-prototype`
- Commit: `1cd6b7d1d09e75fffd5c116a10e3e162647ecb78`
- Source: https://github.com/ariankordi/mii-creator

The renderer uses the repository's FFL.js integration, Mii data codecs,
Three.js scene implementation, body models, clothing textures, and headwear
models. PocketPass supplies its own native editor interface; the bundled web
surface only renders and serializes Mii data.

Reproducible build records:

- Project documentation: `tools/mii-renderer/README.md`
- Machine-readable provenance: `tools/mii-renderer/provenance.json`
- Bundled `dist/renderer.js` SHA-256:
  `A32CD0A84EE89A13B8E387B3B94FA44181E86AAC956F33B394F5A3EA34DE2DD0`

FFL resource provenance:

- Archive:
  `asset_model_character_mii_AFLResHigh_2_3_dat.zip`
- Source URL documented by the pinned repository:
  https://web.archive.org/web/20180502054513/http://download-cdn.miitomo.com/native/20180125111639/android/v2/asset_model_character_mii_AFLResHigh_2_3_dat.zip
- Archive SHA-256:
  `42C106511E8D520583DD4F43360386AA77A5080E4CA8574E7E3D2288E2786D43`
- Extracted `AFLResHigh_2_3.dat` SHA-256:
  `4A4BE71D75162C20B48720EF89CD3D4E6CD4E8E21BCBC2AED63EE08D96DE7722`
- Packaged name: `FFLResHigh.dat`

FFL.js and mii-js retain their upstream attribution and applicable license
terms. The pinned `mii-creator` commit and archived FFL resource do not contain
repository-wide license grants. This integration relies on permission secured
by the PocketPass project owner. The application publisher is responsible for
retaining those permissions and the full third-party notices.
