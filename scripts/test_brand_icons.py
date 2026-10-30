import json, sys, tempfile, unittest
from pathlib import Path
from PIL import Image
sys.path.insert(0, str(Path(__file__).resolve().parent))
from patch_brand_icons import patch
from optimize_favicon import build
ROOT = Path(__file__).resolve().parents[1]
class BrandIcons(unittest.TestCase):
    def test_metadata_only_and_repeat_deployment(self):
        with tempfile.TemporaryDirectory() as d:
            index = Path(d)/'index.php'
            body = '<body><img src="/rentmor-kask-favicon.png" alt="logo"><p>RentMor</p></body>'
            index.write_text('<head><link\n rel="icon" sizes="512x512" href="/rentmor-kask-favicon.png"></head>'+body)
            # The old optimizer matches all references, so use current production hashed favicon.
            index.write_text(index.read_text().replace('href="/rentmor-kask-favicon.png"','href="/assets/rentmor-favicon-123456789abc.png"').replace('src="/rentmor-kask-favicon.png"','src="/logo.png"'))
            body = body.replace('/rentmor-kask-favicon.png','/logo.png')
            build(ROOT/'assets/branding/rm-source.png',index,Path(d)/'icons')
            once = patch(index.read_text())
            self.assertEqual(patch(once),once)
            self.assertEqual(once.split('</head>')[1],body)
            self.assertIn('sizes="64x64"',once)
            self.assertNotIn('sizes="512x512"',once)
            index.write_text(once)
            build(ROOT/'assets/branding/rm-source.png',index,Path(d)/'icons')
            self.assertEqual(patch(index.read_text()),once)
    def test_manifest_assets(self):
        manifest=json.loads((ROOT/'server/assets/rm-20261030.webmanifest').read_text())
        for icon in manifest['icons']:
            with Image.open(ROOT/'server'/icon['src'].lstrip('/')) as image:
                w,h=map(int,icon['sizes'].split('x'))
                self.assertEqual(image.size,(w,h))
                self.assertEqual(image.mode,'RGB')
        for size in (16,32,64,180,192,512):
            with Image.open(ROOT/f'server/assets/rm-20261030-{size}.png') as image:
                self.assertEqual(image.size,(size,size))
        for page in (ROOT/'server').glob('poradnik-*.php'):
            self.assertIn('rentmor-favicon-01d8e2bf2ef1.png',page.read_text())
            self.assertEqual(patch(page.read_text()),page.read_text())
if __name__=='__main__':unittest.main()
