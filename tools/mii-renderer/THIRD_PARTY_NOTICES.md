# PocketPass Mii renderer third-party notices

This notice records the provenance and declared license metadata observed in
the exact renderer inputs. It does not replace the underlying license texts or
the project owner's separately secured permissions.

## Renderer and character assets

### ariankordi/mii-creator

- Source: <https://github.com/ariankordi/mii-creator>
- Branch: `renderer-ffl.js-prototype`
- Commit: `1cd6b7d1d09e75fffd5c116a10e3e162647ecb78`
- Repository-level license at that commit: **none present**

The upstream README credits ariankordi's FFL.js, mii-js/PretendoNetwork, and
utility code from datkat21's mii-creator/mii-frontend. Because the pinned
repository does not declare a repository-wide public license, PocketPass uses
the renderer under permission separately secured by the project owner.

### FFL resource

- Archive: `asset_model_character_mii_AFLResHigh_2_3_dat.zip`
- Archived source URL and exact SHA-256 values: `provenance.json`
- License file supplied with the archived resource: **none observed**

The build script does not download this resource. Its inclusion depends on the
project owner's secured permission, and its exact packaged bytes are verified.

### Body, clothing, and headwear assets

The packaged body models, clothing textures, and headwear archive are exact
files from the pinned `mii-creator` commit. The upstream README gives these
model credits:

- Top Hat: The Models Resource, *Super Smash Bros. Ultimate*
- Ribbon & Bow: The Models Resource, *Nintendogs + Cats*
- Cat Ears, Straw Hat, Hijab, and Bike Helmet: Timimimi

No unified asset-license file is present in the pinned repository. Retain the
project owner's permission records and the upstream creator/model credits.

## Declared JavaScript dependency licenses

The clean build uses the pinned upstream lockfile. The relevant package
manifests declare:

| Package | Version | Declared license |
| --- | ---: | --- |
| `three` | 0.173.0 | MIT |
| `camera-controls` | 2.10.1 | MIT |
| `postprocessing` | 6.37.2 | Zlib |
| `jszip` | 3.10.1 | MIT OR GPL-3.0-or-later |
| `localforage` | 1.10.0 | Apache-2.0 |
| `pako` | 1.0.11 | MIT AND Zlib |
| `buffer` | 6.0.3 | MIT |
| `immediate` | 3.0.6 | MIT |
| `lie` | 3.3.0 | MIT |
| `setimmediate` | 1.0.5 | MIT |
| `kaitai-struct` | 0.10.0 | Apache-2.0 |
| `iconv-lite` | 0.6.3 | MIT |

The vendored `struct-fu.js` header declares Apache-2.0 and BSD-2-Clause.
Review the pinned dependency tree and distribute the applicable full license
texts before public release.
