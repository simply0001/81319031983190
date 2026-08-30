from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


source = Path(sys.argv[1]).resolve()
output = Path(sys.argv[2]).resolve()
image = Image.open(source)
frames: list[Image.Image] = []
durations: list[int] = []

for index in range(image.n_frames):
    image.seek(index)
    frames.append(image.convert("RGB").copy())
    durations.append(int(image.info.get("duration", 0)))

if len(sys.argv) > 3:
    indices = [int(value) for value in sys.argv[3].split(",")]
else:
    sample_count = min(12, len(frames))
    indices = sorted(
        {
            round(position * (len(frames) - 1) / max(1, sample_count - 1))
            for position in range(sample_count)
        },
    )
label_height = 26
columns = 4
rows = (len(indices) + columns - 1) // columns
width, height = frames[0].size
sheet = Image.new("RGB", (columns * width, rows * (height + label_height)), "white")
draw = ImageDraw.Draw(sheet)

elapsed = 0
starts: list[int] = []
for duration in durations:
    starts.append(elapsed)
    elapsed += duration

for slot, index in enumerate(indices):
    x = (slot % columns) * width
    y = (slot // columns) * (height + label_height)
    sheet.paste(frames[index], (x, y + label_height))
    draw.text((x + 5, y + 5), f"frame {index}  t={starts[index]} ms", fill="black")

sheet.save(output)
print(f"size={width}x{height}")
print(f"frames={len(frames)}")
print(f"duration_ms={sum(durations)}")
print(f"durations={sorted(set(durations))}")
for index in range(1, len(frames)):
    bounds = ImageChops.difference(frames[index - 1], frames[index]).getbbox()
    if bounds:
        print(f"change[{index - 1}->{index}]={bounds}")
