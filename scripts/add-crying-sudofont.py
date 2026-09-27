from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.pens.recordingPen import RecordingPen
from fontTools.pens.ttGlyphPen import TTGlyphPen

FONT = Path(__file__).resolve().parents[1] / "ui/src/commonMain/composeResources/font/sudofont.ttf"
NAME = "face-cry-1"
OVERLAP_SIMPLE = 0x40


def rounded_rectangle(pen, left, bottom, right, top, radius):
    pen.moveTo((left + radius, top))
    pen.lineTo((right - radius, top))
    pen.qCurveTo((right, top), (right, top - radius))
    pen.lineTo((right, bottom + radius))
    pen.qCurveTo((right, bottom), (right - radius, bottom))
    pen.lineTo((left + radius, bottom))
    pen.qCurveTo((left, bottom), (left, bottom + radius))
    pen.lineTo((left, top - radius))
    pen.qCurveTo((left, top), (left + radius, top))
    pen.closePath()


def main():
    font = TTFont(FONT)
    recording = RecordingPen()
    font["glyf"]["face-sad-1"].draw(recording, font["glyf"])
    contours, current = [], []
    for operation in recording.value:
        current.append(operation)
        if operation[0] == "closePath":
            contours.append(current)
            current = []
    assert len(contours) == 5
    pen = TTGlyphPen(None)
    for index in (0, 1, 3, 4):
        for operation, points in contours[index]:
            offset = 20 if index >= 3 else 0
            getattr(pen, operation)(*((x, y + offset) for x, y in points))
    rounded_rectangle(pen, 60, 10, 80, 118, 10)
    rounded_rectangle(pen, 160, 10, 180, 118, 10)
    rounded_rectangle(pen, 100, 10, 140, 75, 20)
    glyph = pen.glyph()
    glyph.flags[0] |= OVERLAP_SIMPLE
    order = font.getGlyphOrder()
    if NAME not in order:
        font.setGlyphOrder(order + [NAME])
    font["glyf"][NAME] = glyph
    font["hmtx"][NAME] = font["hmtx"]["face-sad-1"]
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[0xE029] = NAME
            if table.format == 12:
                table.cmap[0x1F62D] = NAME
    font.save(FONT)


if __name__ == "__main__":
    main()
