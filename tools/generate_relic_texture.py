"""Generates the 32x32 "Astral Eclipse Relic" item texture for The Angel of Death.

A diamond core split vertically (pitch-black | pure white), framed by curved silver-and-obsidian angelic wing
blades. The silhouette is mirror-symmetrical. No stick. Run from the project root:

    python3 tools/generate_relic_texture.py
"""
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SIZE = 32
OUT = os.path.join("src", "main", "resources", "assets", "angelofdeath", "textures", "item", "angel_of_death.png")

OBSIDIAN = (20, 20, 28, 255)
OBSIDIAN_DEEP = (8, 8, 12, 255)
OBSIDIAN_EDGE = (58, 60, 74, 255)
SILVER = (196, 202, 214, 255)
SILVER_BRIGHT = (232, 236, 243, 255)
SILVER_SHADOW = (130, 137, 152, 255)
CORE_BLACK = (5, 5, 8, 255)
CORE_WHITE = (255, 255, 255, 255)


def bezier(p0, p1, p2, t):
    x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
    y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
    dx = 2 * (1 - t) * (p1[0] - p0[0]) + 2 * t * (p2[0] - p1[0])
    dy = 2 * (1 - t) * (p1[1] - p0[1]) + 2 * t * (p2[1] - p1[1])
    return x, y, dx, dy


def blade_mask(p0, p1, p2, half_width, steps=64):
    """Tapered curved blade as an 'L' mask (255 inside)."""
    left, right = [], []
    for i in range(steps + 1):
        t = i / steps
        x, y, dx, dy = bezier(p0, p1, p2, t)
        length = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / length, dx / length
        w = max(0.35, half_width * (1 - t) ** 0.85)
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).polygon(left + right[::-1], fill=255)
    return mask


def edge_of(mask):
    """1-pixel inner border of a mask."""
    eroded = mask.filter(ImageFilter.MinFilter(3))
    return ImageChops.subtract(mask, eroded)


def paint(img, mask, fill, outline, highlight_mask=None, highlight=None):
    img.paste(Image.new("RGBA", (SIZE, SIZE), fill), (0, 0), mask)
    img.paste(Image.new("RGBA", (SIZE, SIZE), outline), (0, 0), edge_of(mask))
    if highlight_mask is not None:
        img.paste(Image.new("RGBA", (SIZE, SIZE), highlight), (0, 0), highlight_mask)


def build_left_wing():
    wing = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    # Back to front: lower sweep, middle, upper sweep, plus short inner covert blades.
    blades = [
        # (p0, p1, p2, half_width, fill, outline)
        ((10, 19), (2, 22), (3, 30), 2.4, SILVER, OBSIDIAN_EDGE),
        ((10, 17), (-1, 18), (1, 9), 2.8, OBSIDIAN, SILVER),
        ((10, 14), (0, 13), (6, 2), 2.6, SILVER, OBSIDIAN_EDGE),
        ((11, 16), (5, 15), (4, 8), 1.6, OBSIDIAN, SILVER_BRIGHT),
    ]
    for p0, p1, p2, width, fill, outline in blades:
        mask = blade_mask(p0, p1, p2, width)
        paint(wing, mask, fill, outline)
    return wing


def build_core():
    core = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    diamond = [(16, 7), (24, 16), (16, 25), (8, 16)]
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).polygon(diamond, fill=255)

    px = core.load()
    mpx = mask.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if mpx[x, y]:
                px[x, y] = CORE_BLACK if x < 16 else CORE_WHITE

    # Silver rim around the diamond, brighter on the black half so it reads against the obsidian wings.
    rim = edge_of(mask)
    rpx = rim.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if rpx[x, y]:
                px[x, y] = SILVER_BRIGHT if x < 16 else SILVER_SHADOW

    # Faint facet highlights: grey glints on the black half, light-grey shading on the white half.
    for (x, y) in [(13, 13), (12, 14), (14, 11)]:
        if mpx[x, y] and not rpx[x, y]:
            px[x, y] = OBSIDIAN_EDGE
    for (x, y) in [(19, 19), (20, 18), (18, 21)]:
        if mpx[x, y] and not rpx[x, y]:
            px[x, y] = (220, 225, 233, 255)
    return core


def main():
    left = build_left_wing()
    # Mirror so the silhouette is perfectly symmetrical: the left wing is flipped to make the right wing.
    right = left.transpose(Image.FLIP_LEFT_RIGHT)

    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    img.alpha_composite(left)
    img.alpha_composite(right)
    img.alpha_composite(build_core())

    # No red anywhere: every pixel must have R <= B.
    for r, g, b, a in img.getdata():
        if a and r > b:
            raise SystemExit("red-ish pixel found: %r" % ((r, g, b, a),))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    img.resize((256, 256), Image.NEAREST).save(os.path.join("tools", "relic_preview.png"))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
