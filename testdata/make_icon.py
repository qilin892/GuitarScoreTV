from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import math

RES = Path(__file__).resolve().parents[1] / "tv-app/app/src/main/res"
INK = "#17130f"
ORANGE = "#d95832"
ORANGE_DARK = "#a83c20"
IVORY = "#f5efe2"
MUTED = "#a69b8a"


def bezier(points, steps=80):
    # Cubic Bezier with four control points.
    p0, p1, p2, p3 = points
    result = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u ** 3 * p0[0] + 3 * u ** 2 * t * p1[0] + 3 * u * t ** 2 * p2[0] + t ** 3 * p3[0]
        y = u ** 3 * p0[1] + 3 * u ** 2 * t * p1[1] + 3 * u * t ** 2 * p2[1] + t ** 3 * p3[1]
        result.append((x, y))
    return result


def pick_shape(scale, ox, oy):
    # A guitar pick: rounded shoulders and a tapered point.
    left = bezier([(0, 0), (0, -0.62), (-0.83, -0.45), (-0.83, 0.07)], 36)
    tip = bezier([(-0.83, 0.07), (-0.83, 0.52), (-0.34, 0.82), (0, 1.05)], 36)
    right = bezier([(0, 1.05), (0.34, 0.82), (0.83, 0.52), (0.83, 0.07)], 36)
    back = bezier([(0.83, 0.07), (0.83, -0.45), (0, -0.62), (0, 0)], 36)
    return [(ox + x * scale, oy + y * scale) for x, y in left + tip[1:] + right[1:] + back[1:]]


# Launcher icon, supersampled for clean TV edges.
S = 3
size = 432
image = Image.new("RGB", (size * S, size * S), INK)
d = ImageDraw.Draw(image)
d.rounded_rectangle((10 * S, 10 * S, 422 * S, 422 * S), radius=92 * S,
                    outline="#3a2b20", width=5 * S)
pick = pick_shape(139 * S, 216 * S, 140 * S)
d.polygon(pick, fill=ORANGE)
d.line(pick + [pick[0]], fill="#f08a62", width=5 * S, joint="curve")
# Six guitar strings crossing the pick.
for i, x in enumerate((-54, -32, -11, 11, 32, 54)):
    d.line((216 * S + x * S, 124 * S, 216 * S + x * 0.42 * S, 289 * S),
           fill=IVORY if i in (0, 5) else "#f9f4e8", width=(2 if i in (0, 5) else 1) * S)
# Sound-hole arc detail
d.arc((171 * S, 176 * S, 261 * S, 266 * S), 200, 340, fill=ORANGE_DARK, width=6 * S)
image = image.resize((size, size), Image.LANCZOS)
image.save(RES / "mipmap-xxxhdpi/ic_launcher.png", optimize=True)

# TV banner (16:9) for launchers that request one.
bw, bh = 1280, 720
banner = Image.new("RGB", (bw, bh), INK)
bd = ImageDraw.Draw(banner)
bd.rectangle((0, 0, bw - 1, bh - 1), outline="#33261d", width=3)
bd.polygon(pick_shape(205, 230, 170), fill=ORANGE)
for i, x in enumerate((-80, -48, -16, 16, 48, 80)):
    bd.line((230 + x, 185, 230 + x * 0.42, 390), fill=IVORY, width=2 if i in (0, 5) else 1)
bd.arc((158, 222, 302, 366), 200, 340, fill=ORANGE_DARK, width=8)
font_cn = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 124)
font_en = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 28)
bd.text((530, 222), "弦谱", font=font_cn, fill=IVORY)
bd.text((536, 374), "GUITAR SCORE  /  TV", font=font_en, fill=MUTED)
bd.line((536, 440, 1080, 440), fill="#5b4435", width=2)
bd.text((536, 472), "双页读谱  ·  遥控翻页  ·  专注演奏", font=ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 27), fill=MUTED)
banner.save(RES / "drawable-nodpi/tv_banner.png", optimize=True)

# Keep raster at exactly 320x180 for older launchers.
banner.resize((320, 180), Image.LANCZOS).save(RES / "drawable-nodpi/tv_banner_small.png", optimize=True)
print("icons ready")
