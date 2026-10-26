#!/usr/bin/env python3
"""Read-only checks for public RentMor pages, shared by deployment and monitoring."""
import json
from html.parser import HTMLParser
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

BASE = 'https://rentmor.pl'
GUIDE = '/poradnik-jaki-papier-scierny-do-zyrafy.php'
PUZZI_GUIDE = '/poradnik-pranie-tapicerki-karcher-puzzi.php'
PUZZI_OFFER = '/odkurzacz-pioracy-karcher-puzzi-10-1/'
COMPACTOR_OFFER = '/zageszczarka-dro-masz-101-kg-do-kostki/'
AIRLESS_GUIDE = '/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php'
AIRLESS_OFFER = '/agregat-malarski-wabrotech-wt20i-wynajem-krakow-malowanie-natryskowe/'
OFFERS = ['/szlifierka-stanley-zyrafa-do-gladzi-gipsu/', '/zestaw-szlifierka-stanley-zyrafa-do-gladzi-i-odkurzacz-bosch-metabo/']

class Page(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.canonicals, self.links, self.robots, self.schemas = [], [], [], []
        self.h1 = 0
        self.ld = False
        self.buffer = ''
        self.feed(html)
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'link' and 'canonical' in a.get('rel', '').lower().split():
            self.canonicals.append(a.get('href'))
        if tag == 'a': self.links.append(a.get('href'))
        if tag == 'meta' and a.get('name', '').lower() in ('robots', 'googlebot'):
            self.robots.append(a.get('content', '').lower())
        if tag == 'h1': self.h1 += 1
        if tag == 'script' and a.get('type', '').lower() == 'application/ld+json':
            self.ld, self.buffer = True, ''
    def handle_data(self, data):
        if self.ld: self.buffer += data
    def handle_endtag(self, tag):
        if tag == 'script' and self.ld:
            self.schemas.append(json.loads(self.buffer))
            self.ld = False

def fetch(path):
    url = path if path.startswith('https://') else BASE + path
    with urlopen(Request(url, headers={'User-Agent': 'RentMorSEOCheck/1.0', 'Cache-Control': 'no-cache'}), timeout=30) as response:
        assert response.status == 200, (url, response.status)
        assert response.geturl() == url, ('Unexpected redirect', url, response.geturl())
        headers = response.headers
        html = response.read().decode('utf-8')
    return html, headers

def validate(html, headers, url):
    page = Page(html)
    assert page.canonicals == [url], ('Canonical', url, page.canonicals)
    directives = page.robots + [headers.get('X-Robots-Tag', '').lower()]
    for directive in directives:
        tokens = directive.replace(',', ' ').split()
        assert 'noindex' not in tokens and 'none' not in tokens, ('Indexing blocked', url)
    assert page.h1 == 1, ('H1 count', url, page.h1)
    return page

def main():
    xml, _ = fetch('/sitemap.xml')
    root = ET.fromstring(xml)
    assert root.tag == '{http://www.sitemaps.org/schemas/sitemap/0.9}urlset'
    urls = [n.text for n in root.findall('{*}url/{*}loc')]
    assert len(urls) == len(set(urls)) and len(urls) > 3
    for path in ['/', '/zasady.php', GUIDE, PUZZI_GUIDE, PUZZI_OFFER, COMPACTOR_OFFER, AIRLESS_GUIDE, AIRLESS_OFFER] + OFFERS:
        assert BASE + path in urls, ('Missing sitemap URL', path)
    html, headers = fetch(GUIDE)
    page = validate(html, headers, BASE + GUIDE)
    assert any(s.get('@type') == 'Article' and s.get('mainEntityOfPage') == BASE + GUIDE for s in page.schemas)
    cleaning_html, cleaning_headers = fetch(PUZZI_GUIDE)
    cleaning = validate(cleaning_html, cleaning_headers, BASE + PUZZI_GUIDE)
    assert any(node.get('@type') == 'Article' and node.get('mainEntityOfPage') == BASE + PUZZI_GUIDE
               for schema in cleaning.schemas
               for node in (schema.get('@graph', []) if '@graph' in schema else [schema]))
    assert PUZZI_OFFER in cleaning.links or BASE + PUZZI_OFFER in cleaning.links
    product_html, product_headers = fetch(PUZZI_OFFER)
    validate(product_html, product_headers, BASE + PUZZI_OFFER)
    compactor_html, compactor_headers = fetch(COMPACTOR_OFFER)
    validate(compactor_html, compactor_headers, BASE + COMPACTOR_OFFER)
    spray_html, spray_headers = fetch(AIRLESS_GUIDE)
    spray = validate(spray_html, spray_headers, BASE + AIRLESS_GUIDE)
    assert any(node.get('@type') == 'Article' and node.get('mainEntityOfPage') == BASE + AIRLESS_GUIDE
               for schema in spray.schemas for node in (schema.get('@graph',[]) if '@graph' in schema else [schema]))
    assert AIRLESS_OFFER in spray.links, ('Missing commercial WT20i link',AIRLESS_OFFER)
    assert 'youtube-nocookie.com/embed/-GtS3HWavo8' in spray_html
    assert 'youtube-nocookie.com/embed/exN_5Wl5EPY' not in spray_html
    assert 'wabrotech.pl/poradnik/jak-przygotowac-agregat-malarski-do-pracy' in spray_html
    assert 'youtube-nocookie.com/embed/8OgLnaFO1Jk' in spray_html
    offer_html, offer_headers = fetch(AIRLESS_OFFER)
    offer = validate(offer_html,offer_headers,BASE + AIRLESS_OFFER)
    assert 'https://static.wabrotech.pl/public/products/29/29/files/wt20i-user-manual_06.07.2026-1783331866.pdf' in spray.links
    assert 'są pokazane na filmie producenta poniżej' not in spray_html
    descriptions = [s.get('description', '') for s in offer.schemas if s.get('@type') == 'Product']
    assert len(descriptions) == 1
    assert '210 bar (3045 PSI)' in descriptions[0] and 'Pompa typu GRACO 395' in descriptions[0]
    assert '227 bar' not in descriptions[0]
    assert '210 bar (3045 PSI)' in offer_html and 'wszystkie farby wodne' in offer_html
    for path in OFFERS:
        assert path in page.links or BASE + path in page.links
        body, headers = fetch(path)
        offer = validate(body, headers, BASE + path)
        assert 'rentmor-product-description' in body
        assert 'rentmor_render_description' not in body
        assert GUIDE in offer.links or BASE + GUIDE in offer.links
    home, _ = fetch('/')
    homepage = Page(home)
    for path in [GUIDE, PUZZI_GUIDE, PUZZI_OFFER, AIRLESS_GUIDE, AIRLESS_OFFER] + OFFERS:
        assert path in homepage.links or BASE + path in homepage.links, ('Missing homepage link',path)
    robots, _ = fetch('/robots.txt')
    from urllib.robotparser import RobotFileParser
    parser = RobotFileParser(); parser.parse(robots.splitlines())
    for path in [GUIDE, PUZZI_GUIDE, PUZZI_OFFER, COMPACTOR_OFFER, AIRLESS_GUIDE, AIRLESS_OFFER] + OFFERS:
        assert parser.can_fetch('Googlebot', BASE + path), ('Robots blocked', path)
    assert BASE + '/sitemap.xml' in (parser.site_maps() or [])
    print('PASS: sitemap, robots, 3 original guides incl. WT20i video, canonical, offer and internal links.')

if __name__ == '__main__': main()
