"""Bake beveled figures and 2.5D area-light shadows outside app startup.

Run with Pillow installed after replacing drawable/app_icon.png. The original
icon remains the exact final frame; these layers are only used during assembly.
"""
from pathlib import Path
from math import cos, sin, sqrt, pi
from PIL import Image, ImageChops, ImageFilter

res = Path(__file__).resolve().parents[1] / "app/src/main/res"
original = Image.open(res / "drawable/app_icon.png").convert("RGBA").resize((512, 512))
background = Image.new("RGBA", original.size)
parts = [Image.new("RGBA", original.size) for _ in range(6)]
names = ["parent_left", "parent_right", "links", "child_left", "child_middle", "child_right"]
bounds = [(145,112,145,178), (230,112,145,178), (120,210,285,145),
          (95,263,150,165), (185,263,150,165), (275,263,150,165)]
SHADOW_STEPS = 5
silhouette_bounds = [(181,150,248,236), (264,150,332,236), (159,230,353,300),
                     (133,294,200,373), (223,294,289,373), (313,294,380,373)]


def channel(value):
    return max(0, min(255, int(value + .5)))


def shifted(mask, x, y):
    # Transparent boundaries, unlike wrapped image offsets.
    return mask.transform(mask.size, Image.Transform.AFFINE, (1, 0, -x, 0, 1, -y),
                          resample=Image.Resampling.BICUBIC)


def bake_shadow(mask, height_fraction):
    """Project a raised silhouette onto the icon plane from a finite area light.

    Sampling the light produces an umbra/penumbra that widens with height. A
    separate narrow contact term fades as the object lifts off the plane.
    No dark pixels from the source PNG are used for either shadow term.
    """
    elevation = 20 * height_fraction
    z = 8 + elevation
    shadow = Image.new("L", mask.size)
    for sample in range(48):
        radius = sqrt((sample + .5) / 48) * z * .55
        angle = sample * pi * (3 - sqrt(5))
        projection = shifted(mask, z*.35 + radius*cos(angle),
                             z*.85 - elevation + radius*sin(angle))
        shadow = Image.blend(shadow, projection, 1 / (sample + 1))
    shadow = shadow.filter(ImageFilter.GaussianBlur(.8))
    shadow = shadow.point(lambda a: channel(a * (.34 - .10*height_fraction)))
    contact = shifted(mask.filter(ImageFilter.GaussianBlur(1.1)), 1.5, 2.5)
    contact = contact.point(lambda a: channel(a*.18*(1-height_fraction)**2))
    rgba = Image.new("RGBA", mask.size, (3, 22, 38, 0))
    rgba.putalpha(ImageChops.screen(shadow, contact))
    return rgba


for y in range(512):
    left, right = original.getpixel((64, y)), original.getpixel((448, y))
    for x in range(512):
        weight = (x - 64) / 384
        backing = [left[c] * (1 - weight) + right[c] * weight for c in range(3)]
        background.putpixel((x, y), (*map(channel, backing), 255))
        if not (120 <= x <= 408 and 136 <= y <= 416):
            continue
        pixel = original.getpixel((x, y))
        alpha = max(0, min(1, (pixel[0] - backing[0]) / (247 - backing[0])))
        if alpha >= .025:
            owner = (0 if x < 256 else 1) if y < 236 else (2 if y < 294 else
                (3 if x < 213 else 4 if x < 301 else 5))
            sx, sy, ex, ey = silhouette_bounds[owner]
            if not (sx <= x < ex and sy <= y < ey):
                continue
        else:
            continue
        rgb = [channel((pixel[c] - backing[c] * (1 - alpha)) / alpha) for c in range(3)]
        parts[owner].putpixel((x, y), (*rgb, channel(255 * alpha)))

output = res / "drawable-nodpi"
output.mkdir(exist_ok=True)
background.filter(ImageFilter.GaussianBlur(2)).save(output / "startup_logo_background.png", optimize=True)
for index, (name, part, (x, y, width, height)) in enumerate(zip(names, parts, bounds)):
    tile = part.crop((x, y, x+width, y+height))
    tile.save(output / f"startup_part_{name}.png", optimize=True)
    mask = tile.getchannel("A").point(lambda a: 0 if a < 24 else a)
    atlas = Image.new("RGBA", (width, height * SHADOW_STEPS))
    for step in range(SHADOW_STEPS):
        fraction = step / (SHADOW_STEPS - 1)
        if index == 2:
            # Bake the growing connectors before light projection, so the
            # penumbra is never cut by an animated rectangular clip.
            revealed = mask.copy()
            bottom = round(232 - y + 67*fraction)
            revealed.paste(0, (0, bottom, width, height))
            shadow = bake_shadow(revealed, 0)
            if step == 0:
                shadow.putalpha(Image.new("L", shadow.size))
        else:
            shadow = bake_shadow(mask, fraction)
        atlas.paste(shadow, (0, height*step))
    atlas.save(output / f"startup_shadow_{name}.png", optimize=True)
