"""Render the launcher / App Icon to match the splash lockup: padded X + wordmark."""

from __future__ import annotations

import os
from PIL import Image, ImageChops, ImageDraw, ImageFont

OUT_ANDROID = os.path.join(
    os.path.dirname(__file__),
    "..",
    "android-client",
    "app",
    "src",
    "main",
    "res",
    "drawable",
    "ic_launcher_foreground.png",
)
OUT_IOS = os.path.join(
    os.path.dirname(__file__),
    "..",
    "ios-client",
    "Resources",
    "Assets.xcassets",
    "AppIcon.appiconset",
    "AppIcon.png",
)

MARK_START = (167, 139, 250, 255)  # #A78BFA
MARK_END = (244, 114, 182, 255)  # #F472B6
CYAN = (34, 211, 238, int(255 * 0.55))
PINK = (244, 114, 182, int(255 * 0.42))
WORD_A = (226, 217, 255, 255)  # #E2D9FF
WORD_B = (249, 214, 238, 255)  # #F9D6EE
BG = (11, 11, 26, 255)  # #0B0B1A


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def rounded_bar(size: int, degrees: float) -> Image.Image:
    """One rounded bar rotated around centre — same proportions as XaniMark."""
    bar_h = size * 0.225
    bar_w = size * 1.32
    pad = int(size * 0.6)
    canvas = size + pad * 2
    m = Image.new("L", (canvas, canvas), 0)
    d = ImageDraw.Draw(m)
    left = (canvas - bar_w) / 2
    top = (canvas - bar_h) / 2
    d.rounded_rectangle(
        [left, top, left + bar_w, top + bar_h],
        radius=bar_h / 2,
        fill=255,
    )
    m = m.rotate(-degrees, resample=Image.BICUBIC, center=(canvas / 2, canvas / 2))
    x0 = (canvas - size) // 2
    return m.crop((x0, x0, x0 + size, x0 + size))


def cross_mask(size: int) -> Image.Image:
    return ImageChops.lighter(rounded_bar(size, 45), rounded_bar(size, -45))


def fill_gradient(mask: Image.Image, c0, c1) -> Image.Image:
    size = mask.size[0]
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = out.load()
    m = mask.load()
    denom = max(size * 2 - 2, 1)
    for y in range(size):
        for x in range(size):
            a = m[x, y]
            if a == 0:
                continue
            t = (x + y) / denom
            r, g, b, _ = lerp(c0, c1, t)
            px[x, y] = (r, g, b, a)
    return out


def fill_solid(mask: Image.Image, color) -> Image.Image:
    size = mask.size[0]
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    solid = Image.new("RGBA", (size, size), color)
    return Image.composite(solid, out, mask)


def stamp(base: Image.Image, layer: Image.Image, dx: int, dy: int) -> Image.Image:
    tmp = Image.new("RGBA", base.size, (0, 0, 0, 0))
    tmp.paste(layer, (dx, dy), layer)
    return Image.alpha_composite(base, tmp)


def draw_mark(size: int) -> Image.Image:
    shape = cross_mask(size)
    face = fill_gradient(shape, MARK_START, MARK_END)
    cyan = fill_solid(shape, CYAN)
    pink = fill_solid(shape, PINK)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer = stamp(layer, cyan, int(size * 0.09), int(-size * 0.065))
    layer = stamp(layer, pink, int(-size * 0.065), int(size * 0.09))
    return Image.alpha_composite(layer, face)


def gradient_text(text: str, font: ImageFont.FreeTypeFont, c0, c1) -> Image.Image:
    dummy = Image.new("L", (1, 1))
    d = ImageDraw.Draw(dummy)
    bbox = d.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    mask = Image.new("L", (w + 4, h + 4), 0)
    ImageDraw.Draw(mask).text((2 - bbox[0], 2 - bbox[1]), text, font=font, fill=255)
    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    px = out.load()
    m = mask.load()
    mw, mh = mask.size
    for y in range(mh):
        for x in range(mw):
            a = m[x, y]
            if not a:
                continue
            t = x / max(mw - 1, 1)
            r, g, b, _ = lerp(c0, c1, t)
            px[x, y] = (r, g, b, a)
    return out


def compose(canvas: int = 1024, with_bg: bool = False, safe_inset: float = 0.20) -> Image.Image:
    """Splash-like lockup: mark above wordmark, inset so masks never clip the X."""
    img = Image.new("RGBA", (canvas, canvas), BG if with_bg else (0, 0, 0, 0))
    inset = int(canvas * safe_inset)
    usable = canvas - inset * 2

    mark_box = int(usable * 0.58)
    mark = draw_mark(mark_box)

    font_size = max(24, int(usable * 0.18))
    font = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", font_size)
    word = gradient_text("xani", font, WORD_A, WORD_B)

    gap = int(usable * 0.06)
    total_h = mark_box + gap + word.size[1]
    top = inset + (usable - total_h) // 2
    mark_x = (canvas - mark_box) // 2
    word_x = (canvas - word.size[0]) // 2

    img.paste(mark, (mark_x, top), mark)
    img.paste(word, (word_x, top + mark_box + gap), word)
    return img


def main() -> None:
    fg = compose(1024, with_bg=False, safe_inset=0.22)
    os.makedirs(os.path.dirname(os.path.abspath(OUT_ANDROID)), exist_ok=True)
    fg.save(os.path.abspath(OUT_ANDROID), "PNG")
    print("wrote", os.path.abspath(OUT_ANDROID))

    ios = compose(1024, with_bg=True, safe_inset=0.18)
    os.makedirs(os.path.dirname(os.path.abspath(OUT_IOS)), exist_ok=True)
    ios_rgb = Image.new("RGB", ios.size, BG[:3])
    ios_rgb.paste(ios, mask=ios.split()[-1])
    ios_rgb.save(os.path.abspath(OUT_IOS), "PNG")
    print("wrote", os.path.abspath(OUT_IOS))


if __name__ == "__main__":
    main()
