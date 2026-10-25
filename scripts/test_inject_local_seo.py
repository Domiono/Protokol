import unittest
from inject_local_seo import patch, MARKER

class LocalSEOTests(unittest.TestCase):
    def example(self):
        return '<?php $stats="stats.json"; ?><!DOCTYPE html><html><body><h1>RentMor</h1><footer>Stopka</footer></body></html>'
    def test_injects_once_and_does_not_change_existing_code(self):
        source=self.example()
        result=patch(source)
        self.assertEqual(1,result.count(MARKER))
        self.assertEqual(1,result.count('<h1'))
        self.assertIn('<footer>Stopka</footer>',result)
        self.assertIn('<?php $stats="stats.json"; ?>',result)
        self.assertLess(result.index(MARKER),result.index('<footer'))
        self.assertEqual(result,patch(result))
    def test_links_to_canonical_commercial_offers_and_guide(self):
        result=patch(self.example())
        for path in ['/odkurzacz-pioracy-karcher-puzzi-10-1/',
                     '/szlifierka-stanley-zyrafa-do-gladzi-gipsu/',
                     '/zestaw-szlifierka-stanley-zyrafa-do-gladzi-i-odkurzacz-bosch-metabo/',
                     '/poradnik-pranie-tapicerki-karcher-puzzi.php']:
            self.assertIn('href="'+path+'"',result)
        self.assertIn('Wynajem sprzętu budowlanego',result)
        self.assertIn('Krakowie',result)
    def test_rejects_unexpected_homepage_without_mangling_it(self):
        for source in ['<footer>Footer</footer>','<?php $s="stats.json"; ?><footer>A</footer>',
                       '<?php $s="stats.json"; ?><h1>A</h1><footer>A</footer><footer>B</footer>',
                       self.example().replace('<footer','<aside').replace('</footer>','</aside>')]:
            with self.assertRaises(ValueError):patch(source)
    def test_duplicate_marker_fails_loudly(self):
        with self.assertRaises(ValueError):patch(self.example()+MARKER+MARKER)

if __name__=="__main__": unittest.main()
