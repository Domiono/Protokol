#!/usr/bin/env python3
"""Regression tests: responsive images preserve originals and gallery markup."""
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from prepare_rentmor_images import records, plan, convert
from patch_performance_template import patch

class TestImageOptimization(unittest.TestCase):
    def test_template_idempotent(self):
        source = '<?php echo "ok"; ?>\n<!DOCTYPE html>\n<html></html>'
        result = patch(source)
        self.assertEqual(patch(result), result)
        self.assertLess(result.index("rentmor_start_image_optimizer"), result.index("<!DOCTYPE"))
        self.assertIn("performance_images.php", result)
        with self.assertRaises(ValueError): patch("<?php foo(); ?></html>")

    def test_fullscreen_gallery_uses_original_source(self):
        gallery=(ROOT/"server/rentmor-gallery.js").read_text()
        self.assertIn("image.src = slideImage.src;", gallery)
        self.assertNotIn("image.src = slideImage.currentSrc", gallery)
        self.assertIn("/rentmor-gallery.js?v=2", patch('<?php foo(); ?>\\n<!DOCTYPE html><script src="/rentmor-gallery.js?v=1"></script>'))

    def test_manifest_and_webp_variants(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow not installed")
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); original = root / "in"; optimized = root / "webp"
            original.mkdir()
            (root/"data.json").write_text(json.dumps([{"name":"test","images":["photo.jpg"]}]))
            Image.new("RGB",(2300,1300),color=(73,146,200)).save(original/"photo.jpg",quality=96)
            Image.new("RGBA",(800,500),color=(255,0,0,0)).save(original/"rentmor-logo.png")
            plan(root/"data.json","-",original,root/"download.txt")
            self.assertIn("public_html/uploads/photo.jpg", (root/"download.txt").read_text())
            old_content=(original/"photo.jpg").read_bytes()
            convert(root/"data.json","-",original,optimized,root/"manifest.json",root/"upload.txt")
            m=json.loads((root/"manifest.json").read_text())
            self.assertIn("photo.jpg",m)
            self.assertEqual((original/"photo.jpg").read_bytes(),old_content)
            self.assertIn("card",m["photo.jpg"])
            self.assertTrue((root/"upload.txt").read_text().count("put ")>=1)
            with self.assertRaises(ValueError):
                (root/"data.json").write_text(json.dumps([{"images":["../../private.php"]}]))
                records(root/"data.json")

    def test_php_srcset_changes_no_orig_src(self):
        if not shutil.which("php"): self.skipTest("PHP not installed")
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            shutil.copy(ROOT/"server/performance_images.php",root/"performance_images.php")
            (root/"rentmor-image-manifest.json").write_text(json.dumps({
                "photo.jpg":{"width":1600,"height":900,
                    "card":{"url":"/uploads/rentmor-webp/photo-123-card.webp","width":640},
                    "detail":{"url":"/uploads/rentmor-webp/photo-123-detail.webp","width":1440}}
            }))
            html='<div class="gallery"><img src="/uploads/photo.jpg" loading="lazy" class="object-cover" /></div>'
            code = "require " + json.dumps(str(root/"performance_images.php")) + ";" + (
                "$html=" + json.dumps(html) + ";"
                "echo rentmor_optimize_public_html($html);"
            )
            p=subprocess.run(["php","-r",code],capture_output=True,text=True,check=True)
            self.assertIn('src="/uploads/photo.jpg"',p.stdout)
            self.assertIn('width="1600"',p.stdout)
            self.assertIn('height="900"',p.stdout)
            self.assertIn('srcset="/uploads/rentmor-webp/photo-123-card.webp 640w',p.stdout)
            self.assertIn('loading="lazy"',p.stdout)
            self.assertIn('class="object-cover"',p.stdout)
            self.assertIn('data-rentmor-optimized="1"',p.stdout)

if __name__=="__main__":
    unittest.main()
