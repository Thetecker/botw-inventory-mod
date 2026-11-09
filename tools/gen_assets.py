"""Generates the BOTW Inventory textures (category icons + food ring).
Usage: python tools/gen_assets.py [output_dir]
Everything is drawn at 4x and downsampled, so edges are smooth.
Replace any generated PNG by your own (same name) and delete this step from the workflow."""
import math, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/botw_inventory/textures/gui"
S = 4
N = 128 * S
CYAN = (70, 230, 215)


def canvas():
    return Image.new("RGBA", (N, N), (0, 0, 0, 0))


def mask():
    return Image.new("L", (N, N), 0)


def soften(m, r=8):
    return m.filter(ImageFilter.GaussianBlur(r)).point(lambda v: 255 if v > 128 else 0).filter(ImageFilter.GaussianBlur(1.2))


def fit(img, box=400):
    """Crops to the glyph and rescales it so every icon has the same visual size."""
    bb = img.split()[3].getbbox()
    if not bb:
        return img
    g = img.crop(bb)
    k = box / max(g.size)
    g = g.resize((max(1, int(g.width * k)), max(1, int(g.height * k))), Image.LANCZOS)
    out = canvas()
    out.alpha_composite(g, ((N - g.width) // 2, (N - g.height) // 2))
    return out


def colorize(m, top=(255, 255, 255), bottom=(176, 238, 232)):
    grad = Image.linear_gradient("L").resize((N, N))
    img = Image.composite(Image.new("RGBA", (N, N), bottom + (255,)), Image.new("RGBA", (N, N), top + (255,)), grad)
    img.putalpha(m)
    return img


def tint(glyph, rgb, alpha):
    a = glyph.split()[3].point(lambda v: int(v * alpha))
    img = Image.new("RGBA", (N, N), rgb + (255,))
    img.putalpha(a)
    return img


def glow_under(glyph, strength, radius=16, color=CYAN):
    a = glyph.split()[3].filter(ImageFilter.GaussianBlur(radius)).point(lambda v: min(255, int(v * strength)))
    g = Image.new("RGBA", (N, N), color + (255,))
    g.putalpha(a)
    return Image.alpha_composite(g, glyph)


def bright(glyph):
    return glow_under(glyph, 0.9)


def dim(glyph):
    return glow_under(tint(glyph, (122, 196, 191), 0.62), 0.25, 12)


def save(img, name):
    path = os.path.join(OUT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize((128, 128), Image.LANCZOS).save(path)
    with open(path + ".mcmeta", "w") as f:
        f.write('{"texture":{"blur":true}}')


# ------------------------------------------------------------------ glyphs
def sword():
    m = mask(); d = ImageDraw.Draw(m); cx = 256
    d.polygon([(cx, 34), (cx + 44, 98), (cx + 44, 312), (cx - 44, 312), (cx - 44, 98)], fill=255)
    d.rounded_rectangle((cx - 6, 116, cx + 6, 294), radius=6, fill=0)
    d.rounded_rectangle((cx - 112, 312, cx + 112, 354), radius=20, fill=255)
    d.rounded_rectangle((cx - 18, 350, cx + 18, 430), radius=9, fill=255)
    d.ellipse((cx - 32, 418, cx + 32, 482), fill=255)
    return colorize(m.rotate(-45, resample=Image.BICUBIC, center=(256, 256)))


def chestplate():
    m = mask(); d = ImageDraw.Draw(m)
    d.polygon([(118, 124), (190, 80), (232, 98), (256, 122), (280, 98), (322, 80), (394, 124), (442, 198),
               (374, 230), (356, 230), (356, 432), (156, 432), (156, 230), (138, 230), (70, 198)], fill=255)
    d.rounded_rectangle((251, 158, 261, 408), radius=5, fill=0)
    d.rectangle((156, 326, 356, 338), fill=0)
    return colorize(m)


def pickaxe():
    m = mask(); d = ImageDraw.Draw(m)
    d.rounded_rectangle((236, 100, 276, 470), radius=16, fill=255)
    outer, inner = [], []
    for i in range(41):
        t = -1 + i / 20
        yo = 96 + 150 * t * t
        th = 84 * (1 - t * t) ** 0.75
        outer.append((256 + 205 * t, yo))
        inner.append((256 + 205 * t, yo + th))
    d.polygon(outer + inner[::-1], fill=255)
    return colorize(m.rotate(-45, resample=Image.BICUBIC, center=(256, 256)))


def cube():
    im = canvas(); d = ImageDraw.Draw(im)
    top = [(256, 56), (434, 152), (256, 248), (78, 152)]
    left = [(78, 152), (256, 248), (256, 460), (78, 364)]
    right = [(434, 152), (256, 248), (256, 460), (434, 364)]
    d.polygon(top, fill=(255, 255, 255, 255))
    d.polygon(left, fill=(198, 234, 231, 255))
    d.polygon(right, fill=(142, 202, 202, 255))
    for a, b in [((78, 152), (256, 248)), ((434, 152), (256, 248)), ((256, 248), (256, 460))]:
        d.line([a, b], fill=(0, 0, 0, 0), width=10)
    return im


def gem():
    im = canvas(); d = ImageDraw.Draw(im)
    facets = [
        ([(82, 190), (176, 80), (214, 190)], (205, 240, 236)),
        ([(176, 80), (336, 80), (298, 190), (214, 190)], (255, 255, 255)),
        ([(336, 80), (430, 190), (298, 190)], (180, 226, 224)),
        ([(82, 190), (214, 190), (256, 440)], (160, 214, 212)),
        ([(214, 190), (298, 190), (256, 440)], (230, 250, 247)),
        ([(298, 190), (430, 190), (256, 440)], (132, 192, 194)),
    ]
    for pts, c in facets:
        d.polygon(pts, fill=c + (255,))
    for a, b in [((82, 190), (430, 190)), ((176, 80), (214, 190)), ((336, 80), (298, 190)),
                 ((214, 190), (256, 440)), ((298, 190), (256, 440))]:
        d.line([a, b], fill=(0, 0, 0, 0), width=7)
    return im


def apple():
    m = mask(); d = ImageDraw.Draw(m)
    d.ellipse((92, 140, 276, 430), fill=255)
    d.ellipse((236, 140, 420, 430), fill=255)
    d.ellipse((150, 330, 362, 446), fill=255)
    d.polygon([(246, 56), (270, 56), (284, 160), (256, 160)], fill=255)
    d.polygon([(284, 120), (312, 76), (362, 54), (398, 62), (386, 104), (340, 128)], fill=255)
    return colorize(soften(m, 5))


def star():
    pts = []
    for i in range(10):
        r = 204 if i % 2 == 0 else 92
        a = math.radians(-90 + i * 36)
        pts.append((256 + r * math.cos(a), 280 + r * math.sin(a)))
    m = mask(); ImageDraw.Draw(m).polygon(pts, fill=255)
    return colorize(soften(m, 9))


ICONS = {"weapons": sword, "armor": chestplate, "tools": pickaxe, "blocks": cube,
         "materials": gem, "food": apple, "misc": star}

# -------------------------------------------------------------------- ring
# The food wheel is built in code from small PIECES (1/20 of a gauge), each one
# drawn pointing UP and saved tight, at 4 px per design unit (design wheel = 128 units).
#   green gauge  (outer): piece box 21 x 21 units -> 84 x 84 px
#   yellow gauge (inner): piece box 17 x 14 units -> 68 x 56 px
# Each gauge has an ON piece and an OFF piece; the code rotates them around the center.

def piece(w, h, top_w, bot_w, thick, col, hi, hi_h, glow=0.0, alpha=255):
    W, H = w * S, h * S
    cx, cy, t = W / 2, H / 2, thick * S / 2
    body = Image.new("L", (W, H), 0)
    ImageDraw.Draw(body).polygon([(cx - top_w * S / 2, cy - t), (cx + top_w * S / 2, cy - t),
                                  (cx + bot_w * S / 2, cy + t), (cx - bot_w * S / 2, cy + t)], fill=255)
    body = body.filter(ImageFilter.GaussianBlur(3)).point(lambda v: 255 if v > 128 else 0).filter(ImageFilter.GaussianBlur(1.1))
    im = Image.new("RGBA", (W, H), col + (255,))
    im.putalpha(body.point(lambda v: int(v * alpha / 255)))
    strip = Image.new("L", (W, H), 0)
    ImageDraw.Draw(strip).rectangle((0, cy - t, W, cy - t + hi_h * S), fill=255)
    hi_img = Image.new("RGBA", (W, H), hi + (255,))
    hi_img.putalpha(ImageChops.multiply(strip, body).point(lambda v: int(v * alpha / 255)))
    im = Image.alpha_composite(im, hi_img)
    if glow:
        g = im.split()[3].filter(ImageFilter.GaussianBlur(7)).point(lambda v: min(255, int(v * glow)))
        gl = Image.new("RGBA", (W, H), col + (255,))
        gl.putalpha(g)
        im = Image.alpha_composite(gl, im)
    return im


def green(col, hi, glow=0.0, alpha=255):
    return piece(21, 21, 14.0, 10.4, 15, col, hi, 3.5, glow, alpha)


def yellow(col, hi, glow=0.0, alpha=255):
    return piece(17, 14, 9.8, 8.0, 7.5, col, hi, 2.0, glow, alpha)


def save_native(img, name):
    path = os.path.join(OUT, name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    with open(path + ".mcmeta", "w") as f:
        f.write('{"texture":{"blur":true}}')


def ring_bg(rim=CYAN):
    im = canvas(); d = ImageDraw.Draw(im); c = N / 2
    d.ellipse((c - 244, c - 244, c + 244, c + 244), fill=(5, 20, 22, 175))
    ring = mask(); rd = ImageDraw.Draw(ring)
    rd.ellipse((c - 246, c - 246, c + 246, c + 246), fill=255)
    rd.ellipse((c - 236, c - 236, c + 236, c + 236), fill=0)
    rd.ellipse((c - 124, c - 124, c + 124, c + 124), fill=255)
    rd.ellipse((c - 118, c - 118, c + 118, c + 118), fill=0)
    line = Image.new("RGBA", (N, N), rim + (190,)); line.putalpha(ring.point(lambda v: int(v * 0.6)))
    im = Image.alpha_composite(im, line)
    ap = dim(apple()).resize((190, 190), Image.LANCZOS)
    im.alpha_composite(ap, (int(c - 95), int(c - 95)))
    return im


def halo(strength):
    ring = mask(); rd = ImageDraw.Draw(ring); c = N / 2
    rd.ellipse((c - 238, c - 238, c + 238, c + 238), fill=255)
    rd.ellipse((c - 170, c - 170, c + 170, c + 170), fill=0)
    a = ring.filter(ImageFilter.GaussianBlur(22)).point(lambda v: min(255, int(v * strength)))
    im = Image.new("RGBA", (N, N), (110, 255, 150, 255)); im.putalpha(a)
    return im


def main():
    for name, fn in ICONS.items():
        g = fit(fn())
        save(bright(g), f"icons/{name}.png")
        save(dim(g), f"icons/{name}_dim.png")
    food = os.path.join(OUT, "food")
    if os.path.isdir(food):
        for fn in os.listdir(food):
            if fn.startswith(("seg_", "sat_")):
                os.remove(os.path.join(food, fn))
    save(ring_bg(), "food/ring_bg.png")
    save_native(green((70, 226, 112), (176, 255, 190), glow=0.8), "food/green_on.png")
    save_native(green((28, 66, 62), (44, 96, 90), alpha=170), "food/green_off.png")
    save_native(green((255, 168, 56), (255, 214, 140), glow=0.8), "food/green_low.png")
    save_native(green((146, 160, 52), (196, 206, 98), glow=0.5), "food/green_sick.png")
    save_native(green((112, 28, 30), (170, 48, 48), alpha=210), "food/green_alert.png")
    save_native(green((225, 255, 232), (255, 255, 255), glow=1.0), "food/green_preview.png")
    save_native(yellow((255, 214, 72), (255, 244, 170), glow=0.7), "food/yellow_on.png")
    save_native(yellow((60, 56, 30), (84, 78, 40), alpha=150), "food/yellow_off.png")
    save(ring_bg((255, 168, 56)), "food/ring_bg_warn.png")
    save(ring_bg((255, 70, 70)), "food/ring_bg_alert.png")
    save(ring_bg((170, 184, 70)), "food/ring_bg_sick.png")
    save(halo(0.55), "food/glow_1.png")
    save(halo(1.0), "food/glow_2.png")
    print("assets written to", OUT)


if __name__ == "__main__":
    main()
