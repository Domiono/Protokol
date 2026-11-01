"""Read-only checks of every sitemap URL and each distinct sharing image."""
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
from urllib.request import urlopen
import xml.etree.ElementTree as ET

class Page(HTMLParser):
    def __init__(self):
        super().__init__(); self.og = {}; self.detail = None
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'meta' and attrs.get('property', '').startswith('og:'):
            key = attrs['property']
            assert key not in self.og, ('Duplicate', key)
            self.og[key] = attrs.get('content', '')
        if tag == 'img' and attrs.get('data-rm-image-kind') == 'detail' and self.detail is None:
            self.detail = attrs.get('src')

def get(url):
    with urlopen(url, timeout=45) as r:
        assert r.status == 200, url
        return r.read(), r.headers.get_content_type()

def verify(url):
    data, _ = get(url); page = Page(); page.feed(data.decode('utf-8'))
    for key in ('og:title', 'og:type', 'og:url', 'og:image'):
        assert page.og.get(key), (url, key)
    image = page.og['og:image']
    assert image.startswith('https://rentmor.pl/'), (url, image)
    if page.detail:
        assert image == 'https://rentmor.pl' + page.detail, (url, 'wrong product image', image, page.detail)
    return image

if __name__ == '__main__':
    xml, _ = get('https://rentmor.pl/sitemap.xml')
    urls = [x.text for x in ET.fromstring(xml).iter() if x.tag.endswith('loc')]
    assert len(urls) >= 29
    with ThreadPoolExecutor(max_workers=4) as pool:
        images = set(pool.map(verify, urls))
    for image in images:
        _, mime = get(image)
        assert mime.startswith('image/'), (image, mime)
    print(f'PASS: sharing metadata on {len(urls)} pages and {len(images)} reachable images')
