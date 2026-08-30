from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageStat


source = Path(sys.argv[1]).resolve()
output = Path(sys.argv[2]).resolve()
frames = [Image.open(path).convert("RGB") for path in sorted(source.glob("*.png"))]

scores: list[tuple[int, float]] = []
for index in range(1, len(frames)):
    difference = ImageChops.difference(frames[index - 1], frames[index])
    scores.append((index, sum(ImageStat.Stat(difference).mean)))

indices = sorted(index for index, _ in sorted(scores, key=lambda item: item[1], reverse=True)[:16])
columns = 4
rows = (len(indices) + columns - 1) // columns
label_height = 28
frame_width, frame_height = frames[0].size
sheet = Image.new(
    "RGB",
    (columns * frame_width, rows * (frame_height + label_height)),
    "white",
)
draw = ImageDraw.Draw(sheet)

for slot, index in enumerate(indices):
    x = slot % columns * frame_width
    y = slot // columns * (frame_height + label_height)
    sheet.paste(frames[index], (x, y + label_height))
    score = next(score for score_index, score in scores if score_index == index)
    draw.text((x + 6, y + 6), f"frame {index:03d}  delta={score:.2f}", fill="black")

sheet.save(output)
print(",".join(str(index) for index in indices))
