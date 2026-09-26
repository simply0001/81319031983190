import pathlib
import sys

import numpy as np
from PIL import Image

TABS = ["home", "messages", "friends", "activities", "settings", "piip"]
SCALE = 2
FRAME_QUALITY = 90
SCREEN_QUALITY = 85


def strip_shadow(frame, edge=2):
    pixels = np.array(frame)
    keep = pixels[:, :, 3] >= 250
    for _ in range(edge):
        padded = np.pad(keep, 1)
        keep = (
            padded[1:-1, 1:-1]
            | padded[:-2, 1:-1]
            | padded[2:, 1:-1]
            | padded[1:-1, :-2]
            | padded[1:-1, 2:]
            | padded[:-2, :-2]
            | padded[:-2, 2:]
            | padded[2:, :-2]
            | padded[2:, 2:]
        )
    pixels[:, :, 3] = np.where(keep, pixels[:, :, 3], 0)
    return Image.fromarray(pixels)


def screen_rects(frame):
    alpha = np.array(frame)[:, :, 3]
    opaque = alpha > 200
    transparent = alpha < 10
    left = np.cumsum(opaque, axis=1) > 0
    right = np.cumsum(opaque[:, ::-1], axis=1)[:, ::-1] > 0
    above = np.cumsum(opaque, axis=0) > 0
    below = np.cumsum(opaque[::-1, :], axis=0)[::-1, :] > 0
    enclosed = transparent & left & right & above & below
    height = enclosed.shape[0]
    rects = {}
    for name, rows in (("top", slice(0, height // 2)), ("bottom", slice(height // 2, height))):
        ys, _ = np.nonzero(enclosed[rows])
        y0, y1 = ys.min() + rows.start, ys.max() + rows.start
        spans = [np.nonzero(row)[0] for row in enclosed[y0 : y1 + 1]]
        x0 = int(np.median([span.min() for span in spans if span.size]))
        x1 = int(np.median([span.max() for span in spans if span.size]))
        rects[name] = (x0, y0, x1 + 1, y1 + 1)
    return rects


def main(frame_path, captures_dir, assets_dir):
    captures = pathlib.Path(captures_dir)
    assets = pathlib.Path(assets_dir)
    frame = strip_shadow(Image.open(frame_path).convert("RGBA"))
    rects = screen_rects(frame)
    sizes = {}

    frame_out = assets / "thor-black.webp"
    frame.resize((frame.width * SCALE, frame.height * SCALE), Image.LANCZOS).save(
        frame_out, "WEBP", quality=FRAME_QUALITY, method=6
    )
    sizes[frame_out.name] = frame_out.stat().st_size

    for tab in TABS:
        preview = Image.new("RGBA", frame.size, (0, 0, 0, 0))
        for screen in ("top", "bottom"):
            x0, y0, x1, y1 = rects[screen]
            shot = Image.open(captures / f"{tab}-{screen}.png").convert("RGB")
            out = assets / f"thor-{tab}-{screen}.webp"
            shot.resize(((x1 - x0) * SCALE, (y1 - y0) * SCALE), Image.LANCZOS).save(
                out, "WEBP", quality=SCREEN_QUALITY, method=6
            )
            sizes[out.name] = out.stat().st_size
            preview.paste(shot.resize((x1 - x0, y1 - y0), Image.LANCZOS), (x0, y0))
        Image.alpha_composite(preview, frame).save(captures / f"preview-{tab}.png")

    for name, size in sizes.items():
        print(f"{name}\t{size // 1024} KB")
    print(f"total\t{sum(sizes.values()) // 1024} KB")
    for name, rect in rects.items():
        x0, y0, x1, y1 = rect
        print(
            f"{name}: left {x0 / frame.width:.3%} top {y0 / frame.height:.3%} "
            f"width {(x1 - x0) / frame.width:.3%} height {(y1 - y0) / frame.height:.3%} "
            f"asset {(x1 - x0) * SCALE}x{(y1 - y0) * SCALE}"
        )


if __name__ == "__main__":
    main(*sys.argv[1:4])
