#!/usr/bin/env python3
import os
import math
from PIL import Image, ImageDraw, ImageFont

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

BG_COLOR = "#1A1A1A"
ACCENT = "#E8600C"
INNER = "#242424"
STROKE = "#F0F0F0"


def draw_icon(size: int, rounded: bool) -> Image.Image:
    img = Image.new("RGBA", (size, size), BG_COLOR)
    draw = ImageDraw.Draw(img)

    if rounded:
        mask = Image.new("L", (size, size), 0)
        mdraw = ImageDraw.Draw(mask)
        mdraw.ellipse((0, 0, size - 1, size - 1), fill=255)
        bg = Image.new("RGBA", (size, size), BG_COLOR)
        bg.paste(img, (0, 0), mask)
        img = bg
        draw = ImageDraw.Draw(img)

    cx = cy = size / 2
    # outer ring
    ring_outer = size * 0.28
    ring_inner = size * 0.19
    draw.ellipse(
        [cx - ring_outer, cy - ring_outer, cx + ring_outer, cy + ring_outer],
        outline=STROKE,
        width=max(1, int(size * 0.028)),
    )
    draw.ellipse(
        [cx - ring_inner, cy - ring_inner, cx + ring_inner, cy + ring_inner],
        fill=INNER,
    )

    # Draw "RR" text
    font_size = int(size * 0.22)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", font_size)
    except Exception:
        font = ImageFont.load_default(font_size)
    text = "RR"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    draw.text((cx - tw / 2, cy - th / 2 - size * 0.02), text, font=font, fill=ACCENT)

    return img


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    for name, size in DENSITIES.items():
        out_dir = os.path.join(base, "app", "src", "main", "res", "mipmap-" + name)
        os.makedirs(out_dir, exist_ok=True)
        draw_icon(size, rounded=False).save(os.path.join(out_dir, "ic_launcher.png"), "PNG")
        draw_icon(size, rounded=True).save(os.path.join(out_dir, "ic_launcher_round.png"), "PNG")
        print(f"Generated mipmap-{name}/ic_launcher.png ({size}x{size}) + ic_launcher_round.png")


if __name__ == "__main__":
    main()
