# Two-colour hats

The Piip editor can give a hat two colours. Colour 1 is the hat colour players already pick. Colour 2 is picked from a round button that appears above the hat button on the right rail, but only for hats whose model is marked as two-colour. Every other hat stays one colour.

## How the renderer colours a hat

The renderer reads the hat's colour texture (the image plugged into Base Color) one channel at a time:

| Channel | Becomes |
| --- | --- |
| Red | colour 1, the player's hat colour |
| Green | colour 2 |
| Blue | a part that isn't coloured: its own shade of grey or white |

The channel's brightness is the shade. 255 is the full colour, 128 is half as bright, 0 is none. The three channels are added together, so paint each part in one channel only.

One-colour hats use a grey texture and only its red channel counts, so they need no changes.

## Make the texture

1. Start from a grey shading texture of the hat: white where it's lit, darker in shadow.
2. Build an RGB image from it. Put the shading of the colour 1 parts in the red channel, the colour 2 parts in the green channel and the uncoloured parts in the blue channel. Leave every other channel black. In GIMP, Colors → Components → Compose makes one RGB image from three grey layers.
3. Keep the image fully opaque. Transparent pixels disappear.
4. Save it as a PNG.

| Pixel (R, G, B) | Renders as |
| --- | --- |
| 255, 0, 0 | colour 1 |
| 0, 180, 0 | colour 2, a little shaded |
| 0, 0, 255 | white |
| 0, 0, 90 | dark grey |

## Mark the model in Blender

1. In the hat's material, connect an Image Texture node with the PNG to Base Color of the Principled BSDF. The renderer ignores every other material setting.
2. In Material Properties → Custom Properties, add:
   - `pocketpass_colours`, Integer, value `2`. This turns on colour 2.
   - `pocketpass_colour_2`, Integer, `0` to `11`. This is the colour 2 a player starts with. Leave it out for white.
3. File → Export → glTF 2.0. Choose glTF Binary (`.glb`) and, under Include, tick Custom Properties. Without it the properties aren't saved and the hat stays one colour.

Colour numbers:

| 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Red | Orange | Yellow | Light green | Green | Blue | Light blue | Pink | Purple | Brown | White | Black |

To check the export, this prints the properties of each material:

```python
import json, struct, sys
data = open(sys.argv[1], "rb").read()
length = struct.unpack_from("<I", data, 12)[0]
gltf = json.loads(data[20:20 + length])
for material in gltf.get("materials", []):
    print(material.get("name"), material.get("extras"))
```

A two-colour material prints `{'pocketpass_colours': 2, ...}`.

## Put the hat in the app

Nothing in the code lists two-colour hats. When the renderer starts, it reads the models and tells the app which hats have two colours, and the editor shows the colour 2 button for those.

- **Changing an existing hat:** replace its `.glb` inside `app/src/main/assets/mii_renderer/assets/models/hat_models_bundle.zip`, keeping the same file name and position in the zip. Then update that zip's `sha256` under `pocketPassRuntimeAssets` in `tools/mii-renderer/provenance.json`. People already wearing the hat get the model's colour 2 until they pick one.
- **Adding a new hat:** follow the Hijab commit (`git show 28e321d`). It adds the model to the end of the hat zip (`hat_N.glb` is hat number N-1), adds a row to the hat table in `tools/mii-renderer/patches/pocketpass-renderer.patch`, raises the hat limits in `MiiAppearance`, `MiiEditorCatalog` and the `shop_items` range, and adds the editor icon and the shop item.

After either change, run `tools\mii-renderer\build.ps1` (see `tools/mii-renderer/README.md`). It fails if a hash in `provenance.json` is out of date.

## What players see

In the Piip editor, open Hair and press the hat button. With a two-colour hat on, a round button above it shows both colours. Press it to switch the colour column to colour 2, and again to go back to colour 1.

Colour 2 is saved with the Piip as `extHatSecondaryColor`. `-1` means the model's starting colour. Other people see it in the portrait. Older app versions show the portrait correctly but can't change colour 2.

## Where the code is

- `tools/mii-renderer/src/RendererHatColours.ts` reads the two properties and builds the three colours.
- `tools/mii-renderer/patches/pocketpass-renderer.patch` applies them in the 3D view (`3DScene.ts`, `ShaderUtils.ts`) and in portraits (`IconRendering.ts`).
- `tools/mii-renderer/src/renderer.ts` accepts `hatSecondaryColor` and sends `hatColours` when the renderer is ready.
- In the app: `MiiAppearance.extHatSecondaryColor`, `mii/MiiHatColours.kt`, `MiiColorField.HatSecondary`, and the rail button in `ui/.../mii/MiiEditorScreens.kt`.
