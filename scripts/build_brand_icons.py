#!/usr/bin/env python3
"""Resize approved artwork without redrawing it. Pillow 11.3.0."""
from pathlib import Path
import json
from PIL import Image
ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / 'server/assets'
RES = ROOT / 'app/src/main/res'
VERSION = 'rm-20261030'

def build():
    source = Image.open(ROOT / 'assets/branding/rm-source.png').convert('RGBA')
    def tile(size, opaque=False):
        im = source.resize((size, size), Image.Resampling.LANCZOS)
        if opaque:
            bg = Image.new('RGBA', im.size, '#031d40')
            bg.alpha_composite(im)
            im = bg.convert('RGB')
        return im
    WEB.mkdir(parents=True, exist_ok=True)
    for size in (16, 32, 64, 180, 192, 512):
        tile(size, size >= 180).save(WEB / f'{VERSION}-{size}.png', optimize=True)
    # Entire approved tile fits inside the maskable safe circle.
    mask = Image.new('RGB', (512,512), '#031d40')
    mask.paste(tile(288, True), (112,112))
    mask.save(WEB / f'{VERSION}-maskable-512.png', optimize=True)
    tile(64).save(WEB / f'{VERSION}.ico', sizes=[(16,16),(32,32),(48,48),(64,64)])
    manifest = dict(id='/', name='RentMor', short_name='RentMor', lang='pl', start_url='/', scope='/', display='standalone', background_color='#031d40', theme_color='#031d40', icons=[
        dict(src=f'/assets/{VERSION}-{s}.png', sizes=f'{s}x{s}', type='image/png', purpose='any') for s in (192,512)])
    manifest['icons'].append(dict(src=f'/assets/{VERSION}-maskable-512.png', sizes='512x512', type='image/png', purpose='maskable'))
    (WEB / f'{VERSION}.webmanifest').write_text(json.dumps(manifest, indent=2)+'\n')
    for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        folder = RES / f'mipmap-{density}'
        folder.mkdir(exist_ok=True)
        tile(size).save(folder / 'ic_launcher.png', optimize=True)
    folder = RES / 'drawable-nodpi'
    folder.mkdir(exist_ok=True)
    tile(512).save(folder / 'rm_launcher_art.png', optimize=True)
    folder = RES / 'mipmap-anydpi-v26'
    folder.mkdir(exist_ok=True)
    (folder / 'ic_launcher.xml').write_text('''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/rm_icon_background" />
    <foreground><inset android:inset="22%" android:drawable="@drawable/rm_launcher_art" /></foreground>
</adaptive-icon>
''')
    (RES / 'values/rm_icon_colors.xml').write_text('<resources><color name="rm_icon_background">#031d40</color></resources>\n')

if __name__ == '__main__':
    build()
