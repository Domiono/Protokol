import unittest
from inject_airless_guide_link import patch,MARKER,ANCHOR
from inject_local_seo import patch as add_base

class WT20LinkTests(unittest.TestCase):
    def template(self):
        text='<?php $statistics="stats.json"; ?><!DOCTYPE html><html><body><h1>RentMor</h1><footer>Stopka</footer></body></html>'
        return add_base(text)
    def test_one_link_without_duplicate_and_keeps_original_index(self):
        raw=self.template()
        rendered=patch(raw)
        self.assertIn(MARKER,rendered)
        self.assertEqual(1,rendered.count(MARKER))
        self.assertEqual(1,rendered.count('<h1'))
        self.assertIn('stats.json',rendered)
        self.assertLess(rendered.index(MARKER),rendered.index(ANCHOR))
        self.assertEqual(rendered,patch(rendered))
    def test_links_commercial_offer_and_new_guide(self):
        result=patch(self.template())
        for href in ['/agregat-malarski-wabrotech-wt20i-wynajem-krakow-malowanie-natryskowe/',
                     '/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php']:
            self.assertIn('href="'+href+'"',result)
    def test_prevents_dangerous_patch_if_local_section_missing(self):
        with self.assertRaises(ValueError):patch('<?php ?> <footer></footer>')
    def test_rejects_double_insert_markers(self):
        with self.assertRaises(ValueError):patch(self.template()+MARKER+MARKER)
    def test_article_contains_official_videos_and_safety(self):
        from pathlib import Path
        article=Path('server/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php').read_text(encoding='utf-8')
        for term in ['511','517','521','WABROTECH WT20i','ciśnienia',
                     'youtube-nocookie.com/embed/exN_5Wl5EPY',
                     'youtube-nocookie.com/embed/8OgLnaFO1Jk','rel="canonical"',
                     'type="application/ld+json"','SOR','0,021']:
            self.assertIn(term,article)
if __name__=='__main__':unittest.main()
