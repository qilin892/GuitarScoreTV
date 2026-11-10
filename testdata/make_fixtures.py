from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

OUT = Path(__file__).parent
W, H = 1240, 1680
INK = "#211f1b"
MUTED = "#777066"
PAPER = "#fbf8ef"
ACCENT = "#c64b2b"
SERIF = "C:/Windows/Fonts/msyh.ttc"
MONO = "C:/Windows/Fonts/consola.ttf"


def font(path, size):
    return ImageFont.truetype(path, size)


def dashed(draw, xy, fill):
    x1, y1, x2, y2 = xy
    x = x1
    while x < x2:
        draw.line((x, y1, min(x + 7, x2), y2), fill=fill, width=2)
        x += 13


def page(title, subtitle, page_no, total, measure=True):
    image = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, W - 1, H - 1), outline="#d7cfc0", width=2)
    d.text((72, 56), "弦谱  /  GUITAR SCORE", font=font(SERIF, 20), fill=ACCENT)
    d.text((72, 112), title, font=font(SERIF, 58), fill=INK)
    d.text((76, 185), subtitle, font=font(SERIF, 25), fill=MUTED)
    d.line((72, 238, W - 72, 238), fill=INK, width=2)
    d.text((W - 135, 58), f"{page_no:02d} / {total:02d}", font=font(MONO, 20), fill=MUTED)

    top = 285
    block_h = 320
    for group in range(4):
        y = top + group * block_h
        # six-line standard notation staff
        for line in range(6):
            d.line((76, y + line * 17, W - 76, y + line * 17), fill="#a79e8e", width=2)
        # an intentionally airy six-string tablature staff
        tab_top = y + 125
        for line in range(6):
            d.line((76, tab_top + line * 23, W - 76, tab_top + line * 23), fill="#6e675b", width=2)
        d.text((78, y - 30), ["Am", "C", "G", "F"][group], font=font(SERIF, 23), fill=ACCENT)

        # note heads and stems, sufficient to check scale and legibility on TV
        for n in range(8):
            x = 230 + n * 108
            ny = y + 42 + (n % 3) * 17
            d.ellipse((x - 8, ny - 5, x + 8, ny + 5), fill=INK)
            d.line((x + 8, ny, x + 8, ny - 45), fill=INK, width=3)
        # tab numbers placed on the strings
        for n, fret in enumerate([0, 1, 0, 2, 3, 2, 0, 1]):
            x = 230 + n * 108
            d.text((x - 6, tab_top + (n % 6) * 23 - 17), str(fret),
                   font=font(MONO, 18), fill=INK)

    if measure:
        d.line((72, H - 105, W - 72, H - 105), fill="#d7cfc0", width=2)
        d.text((72, H - 82), "Moderate  = 76        Fingerstyle study", font=font(SERIF, 21), fill=MUTED)
        d.text((W - 220, H - 82), "弦谱 · 双页阅读", font=font(SERIF, 19), fill=MUTED)
    image.save(OUT / f"{title}-{page_no}.jpg", quality=91, optimize=True)


page("小星星", "Twinkle, Twinkle, Little Star  ·  吉他独奏练习", 1, 4)
page("小星星", "第二页  ·  主题重复与低音进行", 2, 4)
page("小星星", "第三页  ·  分解和弦", 3, 4)
page("小星星", "第四页  ·  尾奏", 4, 4)
page("送别", "长亭外，古道边  ·  指弹改编", 1, 2)
page("送别", "第二页  ·  第二段", 2, 2)
print("fixtures ready")
