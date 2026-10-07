"""Builds launcher icon layers, splash and banner resources from the owner's three source images.
Usage: python tools/make_brand_assets.py <icon_square.png> <splash_vertical.png> <banner_wide.png>   (needs Pillow)"""
import sys, os
from PIL import Image
icon, splash, banner = sys.argv[1:4]
res = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
os.makedirs(f"{res}/drawable-nodpi", exist_ok=True)

# --- adaptive icon: background = sampled edge colour, foreground = logo scaled into the 66dp safe zone of the 108dp canvas
im = Image.open(icon).convert("RGB"); w, h = im.size
edge = [im.getpixel(p) for p in [(2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3), (w // 2, 2), (w // 2, h - 3), (2, h // 2), (w - 3, h // 2)]]
bg = tuple(sum(c[i] for c in edge) // len(edge) for i in range(3))
# logo bounding box = pixels clearly brighter than the background
px = im.load(); xs, ys = [], []
for y in range(0, h, 2):
    for x in range(0, w, 2):
        r, g, b = px[x, y]
        if max(r, g, b) > 120: xs.append(x); ys.append(y)
bw, bh = max(xs) - min(xs), max(ys) - min(ys)
target = 0.55 * 108  # logo spans ~59dp: inside the 66dp always-visible circle
scale_dp_per_px = target / max(bw, bh)
cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
for dens, mult in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    size = int(108 * mult); canvas = Image.new("RGB", (size, size), bg)
    s = scale_dp_per_px * mult
    scaled = im.resize((max(1, int(w * s)), max(1, int(h * s))), Image.LANCZOS)
    canvas.paste(scaled, (int(size / 2 - cx * s), int(size / 2 - cy * s)))
    d = f"{res}/mipmap-{dens}"; os.makedirs(d, exist_ok=True)
    canvas.save(f"{d}/ic_launcher_foreground.png", optimize=True)
os.makedirs(f"{res}/mipmap-anydpi", exist_ok=True)
open(f"{res}/mipmap-anydpi/ic_launcher.xml", "w").write('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/ic_launcher_background"/>\n    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n</adaptive-icon>\n')
open(f"{res}/values/ic_launcher_colors.xml", "w").write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#%02X%02X%02X</color>\n    <color name="brand_navy">#%02X%02X%02X</color>\n</resources>\n' % (bg + bg))
# --- in-app art (WebP keeps the APK small)
im.resize((512, 512), Image.LANCZOS).save(f"{res}/drawable-nodpi/logo_ps.webp", quality=90)
s = Image.open(splash).convert("RGB"); s.thumbnail((1080, 1920), Image.LANCZOS); s.save(f"{res}/drawable-nodpi/splash_art.webp", quality=85)
b = Image.open(banner).convert("RGB"); b.thumbnail((1400, 800), Image.LANCZOS); b.save(f"{res}/drawable-nodpi/home_banner.webp", quality=85)
print("background", bg, "logo bbox px", bw, bh)
