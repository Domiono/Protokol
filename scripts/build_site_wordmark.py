#!/usr/bin/env python3
"""Produce the public header wordmark from the previously approved RM artwork."""
from pathlib import Path
import sys
from PIL import Image, ImageDraw, ImageFont

FONT_CANDIDATES = (
    '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
    '/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf',
)

def build(source: Path, destination: Path) -> None:
    font_path = next((p for p in FONT_CANDIDATES if Path(p).exists()), None)
    if font_path is None:
        raise RuntimeError('Missing known bold sans font (not reproducible)')
    tile = Image.open(source).convert('RGBA')
    if tile.width != tile.height:
        raise ValueError('Approved RM icon must be square')
    height = 224
    icon_side = 208
    icon = tile.resize((icon_side, icon_side), Image.Resampling.LANCZOS)
    font = ImageFont.truetype(font_path, 127)
    parts = [('Rent', '#031D40'), ('Mor', '#FFBA08'), ('.pl', '#9CA3AF')]
    widths = [font.getlength(label) for label, _ in parts]
    gap = 24
    width = int(8 + icon_side + gap + sum(widths) + 12)
    out = Image.new('RGBA', (width, height), (255, 255, 255, 0))
    out.alpha_composite(icon, (8, (height - icon_side) // 2))
    draw = ImageDraw.Draw(out)
    bounds = font.getbbox('RentMor.pl')
    text_y = (height - (bounds[3] - bounds[1])) / 2 - bounds[1]
    x = 8 + icon_side + gap
    for label, color in parts:
        draw.text((round(x), round(text_y)), label, font=font, fill=color)
        x += font.getlength(label)
    destination.parent.mkdir(parents=True, exist_ok=True)
    out.save(destination, optimize=True)
    print(f'Created wordmark: {out.width}x{out.height} RGBA, approved tile retained')

if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Usage: build_site_wordmark.py ICON.png OUTPUT.png')
    build(Path(sys.argv[1]), Path(sys.argv[2]))
