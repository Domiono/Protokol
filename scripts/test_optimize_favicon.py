#!/usr/bin/env python3
"""Guard image/favicon dimensions, transparency, hash and URL idempotence."""
import unittest,tempfile,sys,hashlib,re
from pathlib import Path
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parent))
from optimize_favicon import build
class TestFav(unittest.TestCase):
    def test_small_transparent_and_hashed(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); source=root/"old.png"; template=root/"index.php";out=root/"icons"
            Image.new("RGBA",(2400,1800),color=(0,100,200,255)).save(source,"PNG")
            original=source.read_bytes()
            template.write_text('<html><head><link rel="icon" type="image/png" href="/rentmor-kask-favicon.png"></head></html>')
            optimized=build(source,template,out)
            self.assertTrue(optimized.exists())
            self.assertLess(optimized.stat().st_size,30000)
            self.assertEqual(Image.open(optimized).size,(64,64))
            self.assertIn(optimized.name,template.read_text())
            self.assertEqual(source.read_bytes(),original)
            self.assertEqual(build(source,template,out).name,optimized.name)
    def test_fails_without_expected_public_favicon(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);source=root/"old.png";template=root/"index.php"
            Image.new("RGB",(640,640)).save(source)
            template.write_text("<head></head>")
            with self.assertRaises(ValueError):build(source,template,root/"o")
if __name__=="__main__":unittest.main()
