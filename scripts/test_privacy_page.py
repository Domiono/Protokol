"""Run: python3 scripts/test_privacy_page.py"""
from html.parser import HTMLParser
from pathlib import Path
import subprocess
import sys
import tempfile
from patch_privacy_link import patch, MARKER

class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
    def handle_starttag(self, tag, attrs):
        if tag == "a":
            self.links.append(dict(attrs).get("href"))

html = Path('server/polityka-prywatnosci.php').read_text(encoding="utf-8")
for required in ('Administratorem danych', 'art. 6 ust. 1', 'Cookies', 'Prezesa Urzędu Ochrony Danych Osobowych', 'PHPSESSID', 'rm_stats_exclude', '733 891 281', 'polityka-prywatnosci.php'):
    assert required in html, required
assert html.count('<h1>') == 1
assert 'noindex' not in html
subprocess.run(['php', '-l', 'server/polityka-prywatnosci.php'], check=True)
fixture = '<html><body><footer><a href="/zasady.php">Zasady</a></footer></body></html>'
out = patch(fixture)
assert out.count(MARKER) == 1 and patch(out) == out
assert out.index('Polityka prywatności') < out.index('</footer>')
for bad in ('<html></html>', '<footer></footer><footer></footer>', '<footer><a href="/polityka-prywatnosci.php"></a></footer>'):
    try:
        patch(bad)
        raise AssertionError('Unsafe footer structure was accepted')
    except ValueError:
        pass
parser = Links()
parser.feed(out)
assert '/polityka-prywatnosci.php' in parser.links
assert 'cookie-consent.js' in out
assert 'data-rm-cookie-settings' in out
guide = Path('server/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php').read_text(encoding="utf-8")
assert guide.count('class="video-frame video-gate"') == 2
assert guide.count('data-video-src="https://www.youtube-nocookie.com/embed/') == 2
assert '<iframe' not in guide, "YouTube must not load on page render"
assert 'document.createElement(\'iframe\')' in guide
assert 'href="/polityka-prywatnosci.php"' in guide
for article in ('server/poradnik-jaki-papier-scierny-do-zyrafy.php',
                'server/poradnik-pranie-tapicerki-karcher-puzzi.php'):
    assert 'href="/polityka-prywatnosci.php"' in Path(article).read_text(encoding='utf-8')
assert 'Dominik Morawski' in html
assert '30 dniach' in html and '35 dniach' in html
assert 'DO POTWIERDZENIA' in html, 'draft must stay blocked pending retention confirmation'
subprocess.run(['php', '-l', 'server/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php'], check=True)

print('PASS: privacy policy PHP, required information, safe footer patch')
