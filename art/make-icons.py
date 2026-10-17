"""Regenerates the Android launcher and PWA icons from art/icon-source.png.

Run from the repo root: python art/make-icons.py
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "art" / "icon-source.png"
RES = ROOT / "app" / "src" / "main" / "res"
ICONS = ROOT / "web" / "public" / "icons"

TILE_BOX = (84, 93, 1169, 1156)  # the dark tile inside the source image
FG_SCALE = 0.80  # wallet stays inside the 66dp safe zone of the 108dp canvas
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def full_bleed_square() -> tuple[Image.Image, tuple[int, int, int]]:
    img = Image.open(SRC).convert("RGB")
    cx = (TILE_BOX[0] + TILE_BOX[2]) // 2
    cy = (TILE_BOX[1] + TILE_BOX[3]) // 2
    half = min(TILE_BOX[2] - TILE_BOX[0], TILE_BOX[3] - TILE_BOX[1]) // 2
    sq = img.crop((cx - half, cy - half, cx + half, cy + half))
    # Median of a small patch near the left edge, away from the wallet.
    patch = sq.crop((10, sq.height // 2 - 10, 30, sq.height // 2 + 10))
    px = sorted(patch.getdata())
    bg = px[len(px) // 2]
    for corner in [(0, 0), (sq.width - 1, 0), (0, sq.height - 1), (sq.width - 1, sq.height - 1)]:
        ImageDraw.floodfill(sq, corner, bg, thresh=80)
    return sq, bg


def resized(img: Image.Image, size: int) -> Image.Image:
    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    sq, bg = full_bleed_square()
    hex_bg = "#%02X%02X%02X" % bg
    print("background", hex_bg)

    (RES / "values").mkdir(parents=True, exist_ok=True)
    (RES / "values" / "ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{hex_bg}</color>\n</resources>\n',
        encoding="utf-8",
    )

    for name, size in DENSITIES.items():
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = int(size * FG_SCALE)
        art = resized(sq, inner).convert("RGBA")
        off = (size - inner) // 2
        canvas.paste(art, (off, off))
        out = RES / f"mipmap-{name}"
        out.mkdir(parents=True, exist_ok=True)
        canvas.save(out / "ic_launcher_foreground.png", optimize=True)

    ICONS.mkdir(parents=True, exist_ok=True)
    for name, size in [("apple-touch-icon.png", 180), ("icon-192.png", 192), ("icon-512.png", 512), ("favicon-32.png", 32)]:
        resized(sq, size).save(ICONS / name, optimize=True)
    maskable = Image.new("RGB", (512, 512), bg)
    inner = int(512 * 0.80)
    maskable.paste(resized(sq, inner), ((512 - inner) // 2, (512 - inner) // 2))
    maskable.save(ICONS / "icon-maskable-512.png", optimize=True)


if __name__ == "__main__":
    main()
