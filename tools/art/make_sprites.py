# /// script
# requires-python = ">=3.12"
# dependencies = ["pillow==12.3.0"]
# ///
"""Draws Hearthside's pixel-art GUI sprites (Spec A section 4) into the mod's resource-pack assets.

Run from anywhere with:  uv run tools/art/make_sprites.py

The output is deterministic (fixed seeds, no timestamps), so running it again changes nothing. The PNGs are
committed: the build needs no Python, and resource packs can restyle every sprite.

Palette: the approved "Cozy hearth" + "Ember glass" style draft.
"""

import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "fabric/src/main/resources/assets/hearthside/textures/gui/sprites"


def rgb(hex_colour, alpha=255):
    h = hex_colour.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


CLEAR = (0, 0, 0, 0)
OUTLINE = rgb("1a0e06")
OAK = rgb("3a2414")
OAK_DARK = rgb("2a180c")
OAK_MID = rgb("5a3a20")
OAK_LIGHT = rgb("7a4a24")
OAK_HI = rgb("9a6234")
NAIL = rgb("c8a070")
PARCH = rgb("f3dfb6")
PARCH_EDGE = rgb("e2c690")
PARCH_DARK = rgb("d9bf86")
PARCH_LIGHT = rgb("fbecc9")
PARCH_RIM = rgb("7a4a24")
EMBER = rgb("ffb35a")
GLOW = rgb("ff8a3a")


# ---- drawing helpers ----

def canvas(w, h):
    return Image.new("RGBA", (w, h), CLEAR)


def rect(img, x0, y0, x1, y1, colour):
    """Fills the inclusive rectangle (x0, y0)-(x1, y1)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), colour)


def ring(img, inset, colour_top_left, colour_bottom_right=None):
    """A one-pixel ring `inset` pixels in from the edge; the bottom and right sides may differ (bevels)."""
    w, h = img.size
    br = colour_bottom_right or colour_top_left
    for x in range(inset, w - inset):
        img.putpixel((x, inset), colour_top_left)
        img.putpixel((x, h - 1 - inset), br)
    for y in range(inset, h - inset):
        img.putpixel((inset, y), colour_top_left)
        img.putpixel((w - 1 - inset, y), br)


def step_corners(img, steps):
    """Pixel-stepped corners: clears a staircase of `steps` pixels at each corner."""
    w, h = img.size
    for i in range(steps):
        for j in range(steps - i):
            for x, y in ((i, j), (w - 1 - i, j), (i, h - 1 - j), (w - 1 - i, h - 1 - j)):
                img.putpixel((x, y), CLEAR)


def stamp(img, pattern, x, y, colour):
    for dy, row in enumerate(pattern):
        for dx, bit in enumerate(row):
            if bit == "1":
                img.putpixel((x + dx, y + dy), colour)


def darker(colour, factor):
    r, g, b, a = colour
    return (int(r * factor), int(g * factor), int(b * factor), a)


def lighter(colour, amount):
    r, g, b, a = colour
    return (min(255, r + amount), min(255, g + amount), min(255, b + amount), a)


def save(img, name, nine_slice_border=None, tile=False):
    path = OUT / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="PNG", optimize=False)
    meta = path.with_suffix(".png.mcmeta")
    w, h = img.size
    scaling = None
    if nine_slice_border is not None:
        scaling = {"type": "nine_slice", "width": w, "height": h, "border": nine_slice_border}
    elif tile:
        scaling = {"type": "tile", "width": w, "height": h}
    if scaling is not None:
        meta.write_text(json.dumps({"gui": {"scaling": scaling}}, indent=2) + "\n", encoding="utf-8", newline="\n")
    elif meta.exists():
        meta.unlink()
    return 1


# ---- Cozy hearth ----

def wood_panel():
    img = canvas(32, 32)
    rng = random.Random(7)
    for plank in range(4):
        base = OAK_MID if plank % 2 == 0 else darker(OAK_LIGHT, 0.85)
        top = plank * 8
        rect(img, 0, top, 31, top + 7, base)
        rect(img, 0, top, 31, top, lighter(base, 18))
        rect(img, 0, top + 7, 31, top + 7, OAK_DARK)
        joint = rng.randrange(6, 26)
        rect(img, joint, top, joint, top + 6, OAK_DARK)
        for _ in range(3):
            gx = rng.randrange(0, 26)
            gy = top + rng.randrange(2, 6)
            rect(img, gx, gy, gx + rng.randrange(3, 9), gy, darker(base, 0.72))
    ring(img, 0, OUTLINE)
    ring(img, 1, OAK_HI, OAK_DARK)
    ring(img, 2, OAK_LIGHT)
    ring(img, 3, OAK_LIGHT)
    ring(img, 4, OAK_DARK)
    ring(img, 5, OAK)
    for x, y in ((2, 2), (28, 2), (2, 28), (28, 28)):
        rect(img, x, y, x + 1, y + 1, NAIL)
        img.putpixel((x + 1, y + 1), darker(NAIL, 0.6))
    step_corners(img, 1)
    return save(img, "hearth/wood_panel", 6)


def parchment_card():
    img = canvas(32, 32)
    rect(img, 0, 0, 31, 31, PARCH)
    rng = random.Random(11)
    for colour, count in ((PARCH_DARK, 40), (PARCH_LIGHT, 30)):
        for _ in range(count):
            img.putpixel((rng.randrange(5, 27), rng.randrange(5, 27)), colour)
    ring(img, 0, PARCH_RIM)
    ring(img, 1, PARCH_DARK)
    ring(img, 2, PARCH_EDGE)
    ring(img, 3, PARCH_EDGE)
    ring(img, 4, lighter(PARCH_EDGE, 10))
    step_corners(img, 2)
    return save(img, "hearth/parchment_card", 6)


def card_frame():
    img = canvas(16, 16)
    white = (255, 255, 255, 255)
    ring(img, 0, white)
    ring(img, 1, white)
    ring(img, 2, (205, 205, 205, 255))
    step_corners(img, 1)
    return save(img, "hearth/card_frame", 3)


def button(name, base, hi, dark, outline, glow=None):
    img = canvas(48, 20)
    rect(img, 0, 0, 47, 19, base)
    rect(img, 1, 1, 46, 2, hi)
    rect(img, 1, 17, 46, 18, dark)
    rect(img, 1, 1, 1, 18, hi)
    rect(img, 46, 1, 46, 18, dark)
    rng = random.Random(name)
    for y in (7, 12):
        x = rng.randrange(4, 20)
        rect(img, x, y, x + rng.randrange(6, 16), y, darker(base, 0.8))
    ring(img, 0, outline)
    if glow:
        ring(img, 1, glow)
    step_corners(img, 1)
    return save(img, f"hearth/{name}", 4)


def board():
    """The lobby's backdrop: dark vertical oak planks, tiled (the hearth's wooden wall)."""
    img = canvas(32, 32)
    rng = random.Random(23)
    bases = [rgb("3a2414"), rgb("33200f"), rgb("3d2616"), rgb("301d0e")]
    for plank in range(4):
        base = bases[plank]
        x0 = plank * 8
        rect(img, x0, 0, x0 + 7, 31, base)
        rect(img, x0, 0, x0, 31, lighter(base, 10))
        rect(img, x0 + 7, 0, x0 + 7, 31, rgb("140a04"))
        for _ in range(4):
            gx = x0 + rng.randrange(2, 6)
            gy = rng.randrange(0, 26)
            rect(img, gx, gy, gx, gy + rng.randrange(3, 7), darker(base, 0.75))
        if rng.random() < 0.5:
            kx, ky = x0 + rng.randrange(2, 5), rng.randrange(4, 26)
            rect(img, kx, ky, kx + 1, ky + 1, darker(base, 0.6))
    return save(img, "hearth/board", tile=True)


def firelight():
    """A glow rising from the bottom centre, in pixel-stepped bands (stretched over the lower screen)."""
    w, h = 160, 80
    img = canvas(w, h)
    inner, outer = rgb("ffb35a"), rgb("c4562a")
    for y in range(h):
        for x in range(w):
            dx = (x + 0.5 - w / 2) / (w / 2)
            dy = (h - y - 0.5) / h
            strength = max(0.0, 1.0 - (dx * dx * 0.9 + dy * dy * 1.4) ** 0.5)
            # Quantised into many fine bands: soft, but still pixel-made.
            band = round(strength * 14) / 14
            if band <= 0:
                continue
            r = int(outer[0] + (inner[0] - outer[0]) * band)
            g = int(outer[1] + (inner[1] - outer[1]) * band)
            b = int(outer[2] + (inner[2] - outer[2]) * band)
            img.putpixel((x, y), (r, g, b, int(190 * band ** 1.9)))
    return save(img, "hearth/firelight")


def ghost_card():
    """An empty seat: a faint glass card with a dashed rim."""
    img = canvas(16, 16)
    rect(img, 0, 0, 15, 15, (255, 236, 210, 22))
    for i in range(16):
        if (i // 2) % 2 == 0:
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                img.putpixel((x, y), (255, 236, 210, 90))
    step_corners(img, 2)
    return save(img, "hearth/ghost_card", 4)


def tag():
    """A small white label, tinted with a player's colour (nine-slice)."""
    img = canvas(12, 12)
    rect(img, 0, 0, 11, 11, (255, 255, 255, 255))
    ring(img, 0, (150, 150, 150, 255))
    rect(img, 1, 10, 10, 10, (205, 205, 205, 255))
    step_corners(img, 1)
    return save(img, "hearth/tag", 3)


CONTROLLER_ICON = [
    "0111111111110",
    "1111111111111",
    "1101111111011",
    "1000111110101",
    "1101111111011",
    "1111111111111",
    "1111100011111",
    "1111000001111",
    "0110000000110",
]


def controller_icon():
    """A small controller, for counts ("Co-op" shows how many are connected)."""
    img = shape(CONTROLLER_ICON, rgb("f3dfb6"))
    return save(img, "hearth/controller")


def pill():
    """A small wooden pill for the title and labels."""
    img = canvas(24, 14)
    rect(img, 0, 0, 23, 13, rgb("6a4020"))
    rect(img, 1, 1, 22, 2, rgb("8a5a30"))
    rect(img, 1, 11, 22, 12, rgb("4a2a12"))
    ring(img, 0, OUTLINE)
    step_corners(img, 2)
    return save(img, "hearth/pill", 5)


# ---- Ember glass ----

def glass(name, w, h, border, base, edge, hi):
    img = canvas(w, h)
    rect(img, 0, 0, w - 1, h - 1, base)
    for y in range(2, min(6, h - 2)):
        r, g, b, a = base
        rect(img, 2, y, w - 3, y, (r + 8, g + 6, b + 4, min(255, a + 6)))
    ring(img, 0, edge)
    ring(img, 1, hi, (0, 0, 0, 40))
    step_corners(img, 2 if border >= 4 else 1)
    return save(img, f"glass/{name}", border)


# ---- Glyphs ----

FONT = {
    "A": ["01110", "10001", "11111", "10001", "10001"],
    "B": ["11110", "10001", "11110", "10001", "11110"],
    "X": ["10001", "01010", "00100", "01010", "10001"],
    "Y": ["10001", "01010", "00100", "00100", "00100"],
    "L": ["10000", "10000", "10000", "10000", "11111"],
    "R": ["11110", "10001", "11110", "10010", "10001"],
    "T": ["11111", "00100", "00100", "00100", "00100"],
    "S": ["01111", "10000", "01110", "00001", "11110"],
    "Z": ["11111", "00010", "00100", "01000", "11111"],
    "1": ["00100", "01100", "00100", "00100", "01110"],
    "2": ["11110", "00001", "01110", "10000", "11111"],
    "3": ["11110", "00001", "01110", "00001", "11110"],
}

ICONS = {
    "cross": ["10001", "01010", "00100", "01010", "10001"],
    "circle": ["01110", "10001", "10001", "10001", "01110"],
    "square": ["11111", "10001", "10001", "10001", "11111"],
    "triangle": ["00100", "01010", "01010", "10001", "11111"],
    "menu": ["11111", "00000", "11111", "00000", "11111"],
    "view": ["11100", "10100", "11111", "00101", "00111"],
    "create": ["10101", "10101", "10101", "10101", "10101"],
    "plus": ["00100", "00100", "11111", "00100", "00100"],
    "minus": ["00000", "00000", "11111", "00000", "00000"],
    "ring": ["01110", "10001", "10101", "10001", "01110"],
    "diamond": ["00100", "01110", "11111", "01110", "00100"],
    "home": ["00100", "01110", "11111", "01010", "01110"],
}

DISC = [
    "00011111000",
    "00111111100",
    "01111111110",
    "11111111111",
    "11111111111",
    "11111111111",
    "11111111111",
    "11111111111",
    "01111111110",
    "00111111100",
    "00011111000",
]

PILL = [
    "001111111111100",
    "011111111111110",
    "111111111111111",
    "111111111111111",
    "111111111111111",
    "111111111111111",
    "111111111111111",
    "111111111111111",
    "111111111111111",
    "011111111111110",
    "001111111111100",
]

TRIGGER = [
    "000011111110000",
    "001111111111100",
    "011111111111110",
    "111111111111111",
    "111111111111111",
    "111111111111111",
    "111111111111111",
    "111111111111111",
    "111111111111111",
    "011111111111110",
    "001111111111100",
]

INK_LIGHT = (240, 240, 240, 255)
INK_DARK = (40, 30, 10, 255)
SYSTEM = (52, 52, 60, 255)


def shape(mask, fill):
    """Draws a filled mask with a darker outline and a one-pixel highlight along its top."""
    h, w = len(mask), len(mask[0])
    img = canvas(w, h)
    inside = lambda x, y: 0 <= x < w and 0 <= y < h and mask[y][x] == "1"
    for y in range(h):
        for x in range(w):
            if not inside(x, y):
                continue
            edge = not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                img.putpixel((x, y), darker(fill, 0.45))
            elif not inside(x, y - 2):
                img.putpixel((x, y), lighter(fill, 40))
            else:
                img.putpixel((x, y), fill)
    return img


def label(img, text, colour):
    """Centres one or two 5x5 letters in the glyph."""
    w, h = img.size
    width = len(text) * 5 + (len(text) - 1)
    x = (w - width) // 2
    for ch in text:
        stamp(img, FONT[ch], x, (h - 5) // 2, colour)
        x += 6


def icon(img, name, colour):
    w, h = img.size
    stamp(img, ICONS[name], (w - 5) // 2, (h - 5) // 2, colour)


def dpad(direction):
    img = canvas(11, 11)
    cross = ["".join("1" if (3 <= x <= 7 or 3 <= y <= 7) else "0" for x in range(11)) for y in range(11)]
    img = shape(cross, (70, 70, 78, 255))
    arms = {
        "dpad_up": (4, 1, 6, 3),
        "dpad_down": (4, 7, 6, 9),
        "dpad_left": (1, 4, 3, 6),
        "dpad_right": (7, 4, 9, 6),
    }
    x0, y0, x1, y1 = arms[direction]
    rect(img, x0, y0, x1, y1, INK_LIGHT)
    return img


FACE_LETTERS = {
    "xbox": {"a": "A", "b": "B", "x": "X", "y": "Y"},
    "generic": {"a": "A", "b": "B", "x": "X", "y": "Y"},
    # Nintendo prints its letters in other places: the south button says B.
    "nintendo": {"a": "B", "b": "A", "x": "Y", "y": "X"},
}
XBOX_FACE_COLOURS = {"a": rgb("3fa060"), "b": rgb("d04040"), "x": rgb("3f7fd0"), "y": rgb("e0b020")}
PS_SYMBOLS = {"a": ("cross", rgb("7ab8ff")), "b": ("circle", rgb("ff6b6b")),
              "x": ("square", rgb("ff8ad8")), "y": ("triangle", rgb("4fd8a0"))}
SHOULDER_LABELS = {
    "xbox": {"left_bumper": "LB", "right_bumper": "RB", "left_trigger": "LT", "right_trigger": "RT",
             "left_stick": "LS", "right_stick": "RS"},
    "playstation": {"left_bumper": "L1", "right_bumper": "R1", "left_trigger": "L2", "right_trigger": "R2",
                    "left_stick": "L3", "right_stick": "R3"},
    "nintendo": {"left_bumper": "L", "right_bumper": "R", "left_trigger": "ZL", "right_trigger": "ZR",
                 "left_stick": "LS", "right_stick": "RS"},
    "generic": {"left_bumper": "LB", "right_bumper": "RB", "left_trigger": "LT", "right_trigger": "RT",
                "left_stick": "LS", "right_stick": "RS"},
}
SYSTEM_ICONS = {
    "xbox": {"back": "view", "start": "menu", "guide": "ring"},
    "playstation": {"back": "create", "start": "menu", "guide": "diamond"},
    "nintendo": {"back": "minus", "start": "plus", "guide": "home"},
    "generic": {"back": "view", "start": "menu", "guide": "ring"},
}
CONTROLS = ["a", "b", "x", "y", "left_bumper", "right_bumper", "back", "start", "guide", "left_stick",
            "right_stick", "dpad_up", "dpad_down", "dpad_left", "dpad_right", "left_trigger", "right_trigger"]


def glyph(style, control):
    if control in ("a", "b", "x", "y"):
        if style == "playstation":
            img = shape(DISC, (40, 40, 52, 255))
            name, colour = PS_SYMBOLS[control]
            icon(img, name, colour)
        elif style == "xbox":
            fill = XBOX_FACE_COLOURS[control]
            img = shape(DISC, fill)
            label(img, FACE_LETTERS[style][control], INK_DARK if control == "y" else INK_LIGHT)
        else:
            img = shape(DISC, (58, 58, 62, 255) if style == "nintendo" else (90, 90, 96, 255))
            label(img, FACE_LETTERS[style][control], INK_LIGHT)
    elif control in ("left_trigger", "right_trigger"):
        img = shape(TRIGGER, SYSTEM)
        label(img, SHOULDER_LABELS[style][control], INK_LIGHT)
    elif control in SHOULDER_LABELS[style]:
        img = shape(PILL, SYSTEM)
        label(img, SHOULDER_LABELS[style][control], INK_LIGHT)
    elif control in SYSTEM_ICONS[style]:
        img = shape(DISC, SYSTEM)
        icon(img, SYSTEM_ICONS[style][control], INK_LIGHT)
    else:
        img = dpad(control)
    return save(img, f"glyph/{style}/{control}")


def main():
    count = 0
    count += wood_panel()
    count += parchment_card()
    count += card_frame()
    count += board()
    count += firelight()
    count += ghost_card()
    count += pill()
    count += tag()
    count += controller_icon()
    count += button("button", OAK_LIGHT, OAK_HI, OAK, OUTLINE)
    count += button("button_highlighted", rgb("9a5a2c"), rgb("c07a40"), OAK_LIGHT, EMBER, GLOW)
    count += button("button_disabled", rgb("4a3a30"), rgb("5a4a40"), rgb("2e241e"), rgb("1a1410"))
    count += glass("panel", 16, 16, 4, (34, 24, 22, 178), (255, 150, 80, 150), (255, 220, 180, 70))
    count += glass("chip", 12, 11, 3, (24, 16, 14, 190), (255, 170, 100, 120), (255, 220, 180, 50))
    for style in ("xbox", "playstation", "nintendo", "generic"):
        for control in CONTROLS:
            count += glyph(style, control)
    print(f"wrote {count} sprites to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
