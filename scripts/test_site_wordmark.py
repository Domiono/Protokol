import tempfile
import unittest
from pathlib import Path
from PIL import Image
from build_site_wordmark import build
from patch_site_wordmark import patch, URL

ROOT = Path(__file__).resolve().parents[1]

class WordmarkTests(unittest.TestCase):
    def test_unmodified_rm_icon_and_wordmark_colors(self):
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / 'rentmor-logo.png'
            original_path = ROOT / 'assets/branding/rm-source.png'
            build(original_path, output_path)
            with Image.open(output_path) as result, Image.open(original_path) as original:
                self.assertEqual(result.mode, 'RGBA')
                self.assertGreater(result.width / result.height, 4.3)
                self.assertLess(result.width / result.height, 5.3)
                icon = original.convert('RGBA').resize((208, 208), Image.Resampling.LANCZOS)
                embedded = result.crop((8, 8, 216, 216))
                self.assertEqual(embedded.getchannel('A').tobytes(), icon.getchannel('A').tobytes())
                for y in range(208):
                    for x in range(208):
                        if icon.getpixel((x, y))[3] == 255:
                            self.assertEqual(embedded.getpixel((x, y)), icon.getpixel((x, y)))
                colors = set(result.convert('RGB').getdata())
                for expected in ((3, 29, 64), (255, 186, 8), (156, 163, 175)):
                    self.assertIn(expected, colors)

    def test_header_only_and_idempotency(self):
        html = '<html><head><title>RentMor</title></head><body><nav><a href="/"><img class="logo" src="/rentmor-logo.png" alt="RentMor.pl"></a></nav><main><img src="/product.png"></main></body></html>'
        result = patch(html)
        self.assertIn('src="' + URL + '"', result)
        self.assertEqual(patch(result), result)
        self.assertIn('src="/product.png"', result)
        self.assertIn('<title>RentMor</title>', result)

    def test_refuses_unknown_or_duplicate_logo(self):
        for html in ('<body><nav>RentMor</nav><main></main></body>',
                     '<body><nav><img src="/rentmor-logo.png"><img src="/rentmor-logo.png"></nav><main></main></body>'):
            with self.assertRaises(ValueError):
                patch(html)

if __name__ == '__main__':
    unittest.main()
