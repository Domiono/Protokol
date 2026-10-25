import sys
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent))
from patch_static_tailwind import patch

CDN='<script src="https://cdn.tailwindcss.com"></script>'
CSS='/assets/rentmor-tailwind-012345abcdef.css'
class TailwindSwitch(unittest.TestCase):
    def test_cdn_is_replaced_without_other_scripts(self):
        old='<html><head>'+CDN+'<script src="/rentmor-gallery.js?v=2"></script></head><body></body></html>'
        changed=patch(old,CSS)
        self.assertNotIn('cdn.tailwindcss.com',changed)
        self.assertIn('<link rel="stylesheet" href="'+CSS+'">',changed)
        self.assertIn('/rentmor-gallery.js?v=2',changed)
        self.assertEqual(patch(changed,CSS),changed)
        self.assertIn('rentmor-tailwind-ffffffffffff.css',patch(changed,'/assets/rentmor-tailwind-ffffffffffff.css'))
    def test_rejects_ambiguous_or_unknown_source(self):
        old='<head>'+CDN+'</head>'
        for source in [old+CDN,old+'<link rel="stylesheet" href="'+CSS+'">','<head></head>','<head>'+CDN+'<script>tailwind.config = {}</script></head>']:
            with self.assertRaises(ValueError): patch(source,CSS)
    def test_invalid_path_rejected(self):
        with self.assertRaises(ValueError): patch('<head>'+CDN+'</head>','https://evil.example/styles.css')
        with self.assertRaises(ValueError): patch('<head>'+CDN+'</head>','/assets/../../admin.css')

if __name__=='__main__': unittest.main()
