# Draws the pictures Sekai Tune hands to Discord for its Rich Presence: the play/pause badge with a
# ring that fills as the song goes, and the Sekai Tune logo. Discord fetches them straight from this
# repo on GitHub, so after changing them, bump VERSION (Discord keeps old copies by URL) and the
# same number in discord/PresenceImages.kt, then push.
#
#   python3 scripts/discord_presence_images.py

import math
from pathlib import Path

from PIL import Image, ImageDraw

VERSION = "v1"
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "assets" / "discord" / VERSION

SIZE = 128
# drawn this many times bigger, then shrunk, so the ring's edges come out smooth
SUPERSAMPLE = 4
# one picture per 5%, which is as fine as a 40px badge can show
STEPS = 20

DISC = (30, 31, 34, 255)
TRACK = (255, 255, 255, 56)
FILL = (255, 255, 255, 255)


def badge(progress: float, playing: bool) -> Image.Image:
    s = SIZE * SUPERSAMPLE
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    draw.ellipse((0, 0, s - 1, s - 1), fill=DISC)

    stroke = round(s * 0.085)
    inset = round(s * 0.16)
    box = (inset, inset, s - inset, s - inset)
    draw.arc(box, 0, 360, fill=TRACK, width=stroke)
    if progress > 0:
        start = -90
        end = start + 360 * progress
        draw.arc(box, start, end, fill=FILL, width=stroke)
        # round caps, so the ring's ends don't look chopped off
        radius = (s - 2 * inset) / 2 - stroke / 2
        centre = s / 2
        for angle in (start, end):
            a = math.radians(angle)
            x = centre + radius * math.cos(a)
            y = centre + radius * math.sin(a)
            draw.ellipse((x - stroke / 2, y - stroke / 2, x + stroke / 2, y + stroke / 2), fill=FILL)

    glyph = s * 0.30
    cx, cy = s / 2, s / 2
    if playing:
        # a triangle sits a touch right of centre, or it looks like it's leaning left
        left = cx - glyph * 0.38
        draw.polygon(
            [(left, cy - glyph / 2), (left, cy + glyph / 2), (left + glyph * 0.9, cy)],
            fill=FILL,
        )
    else:
        bar = glyph * 0.3
        gap = glyph * 0.26
        r = bar / 2
        for x in (cx - gap / 2 - bar, cx + gap / 2):
            draw.rounded_rectangle((x, cy - glyph / 2, x + bar, cy + glyph / 2), radius=r, fill=FILL)

    return im.resize((SIZE, SIZE), Image.LANCZOS)


def logo() -> Image.Image:
    # Sekai Tune launcher foreground + background composite cropped to the visible icon
    res = ROOT / "app" / "src" / "main" / "res"
    bg = Image.open(res / "mipmap-xxxhdpi" / "ic_launcher_background.png").convert("RGBA")
    fg = Image.open(res / "mipmap-xxxhdpi" / "ic_launcher_foreground.png").convert("RGBA")
    out = Image.new("RGBA", fg.size)
    out.alpha_composite(bg)
    out.alpha_composite(fg)
    edge = fg.width // 6
    return out.crop((edge, edge, fg.width - edge, fg.height - edge))


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for step in range(STEPS + 1):
        for playing in (True, False):
            name = f"status_{'playing' if playing else 'paused'}_{step:02d}.png"
            badge(step / STEPS, playing).save(OUT / name, optimize=True)
    logo().save(OUT / "sekaitune.png", optimize=True)
    print(f"wrote {2 * (STEPS + 1) + 1} images to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
