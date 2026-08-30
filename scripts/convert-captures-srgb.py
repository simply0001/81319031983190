from __future__ import annotations

import sys
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageCms


def convert(source: Path) -> Path:
    image = Image.open(source)
    profile_bytes = image.info.get("icc_profile")
    output = source.with_name(f"{source.stem}-srgb{source.suffix}")
    if profile_bytes:
        image = ImageCms.profileToProfile(
            image.convert("RGB"),
            ImageCms.ImageCmsProfile(BytesIO(profile_bytes)),
            ImageCms.createProfile("sRGB"),
            outputMode="RGB",
        )
    else:
        image = image.convert("RGB")
    image.save(output)
    return output


for argument in sys.argv[1:]:
    print(convert(Path(argument).resolve()))
