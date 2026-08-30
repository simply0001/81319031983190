from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw


source = Path(sys.argv[1]).resolve()
output = Path(sys.argv[2]).resolve()
indices = [int(value) for value in sys.argv[3].split(",")]
left, top, right, bottom = [int(value) for value in sys.argv[4].split(",")]
scale = int(sys.argv[5]) if len(sys.argv) > 5 else 2

image = Image.open(source)
frames: list[Image.Image] = []
starts: list[int] = []
elapsed = 0
for index in range(image.n_frames):
    image.seek(index)
    if index in indices:
        frame = image.convert("RGB").crop((left, top, right, bottom))
        frames.append(frame.resize((frame.width * scale, frame.height * scale)))
        starts.append(elapsed)
    elapsed += int(image.info.get("duration", 0))

columns = 3
label_height = 30
frame_width, frame_height = frames[0].size
rows = (len(frames) + columns - 1) // columns
sheet = Image.new(
    "RGB",
    (columns * frame_width, rows * (frame_height + label_height)),
    "white",
)
draw = ImageDraw.Draw(sheet)
for slot, (frame, index, start) in enumerate(zip(frames, indices, starts)):
    x = slot % columns * frame_width
    y = slot // columns * (frame_height + label_height)
    sheet.paste(frame, (x, y + label_height))
    draw.text((x + 6, y + 6), f"frame {index}, {start} ms", fill="black")

sheet.save(output)
