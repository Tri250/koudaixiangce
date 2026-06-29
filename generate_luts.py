#!/usr/bin/env python3
"""Generate simple film-style 3D LUT .cube files for RapidRAW built-ins."""
import os
import math

OUT_DIR = "/workspace/RapidRAW-Android/app/src/main/assets/built_in_luts"
os.makedirs(OUT_DIR, exist_ok=True)

def clamp(v):
    return max(0.0, min(1.0, v))

def rgb_to_hsv(r, g, b):
    mx = max(r, g, b)
    mn = min(r, g, b)
    d = mx - mn
    if d == 0:
        h = 0
    elif mx == r:
        h = (60 * ((g - b) / d) + 360) % 360
    elif mx == g:
        h = (60 * ((b - r) / d) + 120) % 360
    else:
        h = (60 * ((r - g) / d) + 240) % 360
    s = 0 if mx == 0 else d / mx
    v = mx
    return h, s, v

def hsv_to_rgb(h, s, v):
    c = v * s
    x = c * (1 - abs((h / 60) % 2 - 1))
    m = v - c
    if h < 60:
        r, g, b = c, x, 0
    elif h < 120:
        r, g, b = x, c, 0
    elif h < 180:
        r, g, b = 0, c, x
    elif h < 240:
        r, g, b = 0, x, c
    elif h < 300:
        r, g, b = x, 0, c
    else:
        r, g, b = c, 0, x
    return clamp(r + m), clamp(g + m), clamp(b + m)

def luma(r, g, b):
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def generate_lut(size, transform):
    lines = []
    for b in range(size):
        for g in range(size):
            for r in range(size):
                rf = r / (size - 1)
                gf = g / (size - 1)
                bf = b / (size - 1)
                rt, gt, bt = transform(rf, gf, bf)
                lines.append(f"{rt:.6f} {gt:.6f} {bt:.6f}")
    return lines

def write_cube(name, title, size, transform):
    path = os.path.join(OUT_DIR, name)
    lines = [f'TITLE "{title}"', f"LUT_3D_SIZE {size}"]
    lines.extend(generate_lut(size, transform))
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"Wrote {path} ({size}^3)")

def portra(r, g, b):
    r = r * 0.98 + 0.02
    g = g * 0.97 + 0.015
    b = b * 0.94 + 0.01
    r = r ** 0.95
    g = g ** 0.95
    b = b ** 0.95
    return clamp(r), clamp(g), clamp(b)

def ektar(r, g, b):
    h, s, v = rgb_to_hsv(r, g, b)
    s = clamp(s * 1.15)
    v = clamp(v ** 0.92)
    r, g, b = hsv_to_rgb(h, s, v)
    r = clamp(r * 1.05)
    b = clamp(b * 0.97)
    return r, g, b

def superia(r, g, b):
    lum = luma(r, g, b)
    shadow_mask = 1.0 - min(lum * 1.5, 1.0)
    g = clamp(g + shadow_mask * 0.025)
    r = clamp(r - shadow_mask * 0.01)
    b = clamp(b - shadow_mask * 0.015)
    r = r ** 0.97
    g = g ** 0.96
    b = b ** 0.98
    return clamp(r), clamp(g), clamp(b)

def velvia(r, g, b):
    h, s, v = rgb_to_hsv(r, g, b)
    s = clamp(s * 1.25)
    v = clamp(v ** 0.88)
    r, g, b = hsv_to_rgb(h, s, v)
    return r, g, b

def agfa(r, g, b):
    r = r * 0.96 + 0.02
    g = g * 0.98 + 0.015
    b = b * 1.02 + 0.01
    r = r * 0.92 + 0.04
    g = g * 0.92 + 0.04
    b = b * 0.92 + 0.04
    return clamp(r), clamp(g), clamp(b)

SIZE = 17
write_cube("kodak_portra_400.cube", "Kodak Portra 400", SIZE, portra)
write_cube("kodak_ektar_100.cube", "Kodak Ektar 100", SIZE, ektar)
write_cube("fuji_superia_400.cube", "Fuji Superia 400", SIZE, superia)
write_cube("fuji_velvia_50.cube", "Fuji Velvia 50", SIZE, velvia)
write_cube("agfa_vista_400.cube", "Agfa Vista 400", SIZE, agfa)
print("Done.")
