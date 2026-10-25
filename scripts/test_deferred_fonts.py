#!/usr/bin/env python3
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from patch_deferred_fonts import patch, TARGETS

TAILWIND='<link rel="stylesheet" href="/assets/rentmor-tailwind-4a9b40916c35.css">'
FA='<link rel="stylesheet" href="'+TARGETS["icons"]+'">'
GOOGLE='<link rel="stylesheet" href="'+TARGETS["roboto"].replace("&","&amp;")+'">'
PAGE='<html><head>'+TAILWIND+FA+GOOGLE+'</head><body><div class="fa fa-phone">Test</div></body></html>'
class DeferredFontsTests(unittest.TestCase):
    def test_rewrite_preserves_fallbacks_and_tailwind(self):
        p=patch(PAGE)
        self.assertEqual(p.count('media="print"'),2)
        self.assertEqual(p.count("<noscript>"),2)
        self.assertEqual(p.count("onload=" + chr(34) + "this.media=" + chr(39) + "all" + chr(39) + chr(34)), 2)
        self.assertIn(TAILWIND,p)
        self.assertIn("font-awesome/6.4.0",p)
        self.assertIn("Roboto",p)
        self.assertIn('href="https://fonts.gstatic.com" crossorigin',p)
        self.assertEqual(patch(p),p)
    def test_rejects_unknown_layout_and_partial_rewrites(self):
        with self.assertRaises(ValueError):patch('<html><head>'+GOOGLE+FA+'</head></html>')
        with self.assertRaises(ValueError):patch(PAGE+GOOGLE)
        p=patch(PAGE)
        with self.assertRaises(ValueError):patch(p.replace("<noscript>", "<!-- missing -->",1))
        with self.assertRaises(ValueError):patch(p.replace('data-rentmor-deferred-style="icons"', 'data-rentmor-deferred-style="roboto"',1))
if __name__=="__main__":unittest.main()
