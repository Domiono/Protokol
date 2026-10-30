import unittest
from pathlib import Path
from patch_hero_blueprint import patch, ASSET, MARKER

ROOT = Path(__file__).resolve().parents[1]
ASSET_PATH = ROOT / "server" / ASSET.lstrip("/")

class HeroBlueprintTests(unittest.TestCase):
    def test_home_only_and_repeated_deployment(self):
        source = """<!doctype html><head></head><body><header class="nav">Logo</header>
        <?php if ($detail_view): ?><section class="product"><h1>Wynajem młota</h1></section>
        <?php else: ?><section class="bg-slate-900"><div><h1>
        Wypożyczalnia sprzętu budowlanego i ogrodniczego w Krakowie
        </h1><p>Prosty i tani wynajem</p></div></section>
        <section class="catalog">Katalog</section><?php endif; ?></body>"""
        out = patch(source)
        self.assertIn('class="bg-slate-900" data-rentmor-blueprint="v1"', out)
        self.assertNotIn('class="product" data-rentmor-blueprint=', out)
        self.assertNotIn('class="catalog" data-rentmor-blueprint=', out)
        self.assertEqual(patch(out), out)
        self.assertIn(ASSET, out)
        self.assertNotIn('rentmor-hero-blueprint-20261030.webp', out)
        old = out.replace(ASSET, '/assets/rentmor-hero-blueprint-20261030.webp')
        upgraded = patch(old)
        self.assertIn(ASSET, upgraded)
        self.assertNotIn('rentmor-hero-blueprint-20261030.webp', upgraded)
        self.assertEqual(out.count("Prosty i tani wynajem"), 1)
        self.assertEqual(out.count("Katalog"), 1)

    def test_missing_hero_is_safe_failure(self):
        with self.assertRaises(ValueError):
            patch("<head></head><body><h1>Wynajem w Krakowie</h1></body>")

    def test_asset_signature_and_size(self):
        img = ASSET_PATH.read_bytes()
        self.assertEqual(img[0:4], b"RIFF")
        self.assertEqual(img[8:12], b"WEBP")
        self.assertGreater(len(img), 200_000, "Do not publish the extremely compressed preview again")
        self.assertLess(len(img), 500_000, "Hero background unexpectedly heavy")
        from PIL import Image
        with Image.open(ASSET_PATH) as picture:
            self.assertEqual(picture.format, "WEBP")
            self.assertGreaterEqual(picture.width, 1500)
            self.assertGreaterEqual(picture.height, 850)

if __name__ == "__main__":
    unittest.main()
