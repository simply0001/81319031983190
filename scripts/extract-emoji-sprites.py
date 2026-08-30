"""Recover transparent emoji sprites from Figma backdrop-blended screenshots.

Figma's asset export omits the emoji pixels for these blend-mode text nodes,
while its node screenshot contains the composited emoji. Given both lossless
PNGs, this script solves the standard source-over equation per pixel and emits
the smallest-alpha foreground that recreates the screenshot over its backdrop.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


def extract(composite_path: Path, backdrop_path: Path, output_path: Path) -> None:
    composite = Image.open(composite_path).convert("RGB")
    backdrop = Image.open(backdrop_path).convert("RGB")
    if composite.size != backdrop.size:
        raise ValueError(
            f"{composite_path.name} and {backdrop_path.name} have different sizes"
        )

    output = Image.new("RGBA", composite.size)
    pixels: list[tuple[int, int, int, int]] = []

    for foreground_pixel, backdrop_pixel in zip(
        composite.getdata(), backdrop.getdata(), strict=True
    ):
        if foreground_pixel == backdrop_pixel:
            pixels.append((0, 0, 0, 0))
            continue

        alpha = 0.0
        for composited, background in zip(
            foreground_pixel, backdrop_pixel, strict=True
        ):
            if composited > background and background < 255:
                alpha = max(alpha, (composited - background) / (255 - background))
            elif composited < background and background > 0:
                alpha = max(alpha, (background - composited) / background)

        alpha_byte = max(1, min(255, round(alpha * 255)))
        alpha = alpha_byte / 255
        recovered = tuple(
            max(
                0,
                min(
                    255,
                    round(
                        (composited - (1 - alpha) * background) / alpha
                    ),
                ),
            )
            for composited, background in zip(
                foreground_pixel, backdrop_pixel, strict=True
            )
        )
        pixels.append((*recovered, alpha_byte))

    output.putdata(pixels)
    output.save(output_path, format="PNG", optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--screenshots", type=Path, required=True)
    parser.add_argument("--backdrops", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    for backdrop_path in sorted(args.backdrops.glob("*.png")):
        node_id = backdrop_path.stem
        composite_path = args.screenshots / f"home_emoji_{node_id}.asset"
        output_path = args.output / f"home_emoji_{node_id}.asset"
        extract(composite_path, backdrop_path, output_path)


if __name__ == "__main__":
    main()
