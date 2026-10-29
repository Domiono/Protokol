#!/usr/bin/env python3
"""Check tracker upgrades and verify public delivery without generating phone events."""
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def get(url):
    with urlopen(Request(url, headers={'User-Agent': 'RentMor-monitor/1.0'}), timeout=30) as response:
        return response.read().decode('utf-8')

def verify_live():
    home = get('https://rentmor.pl/')
    assert 'data-phone-source="home"' in home
    assert '/online.js?v=phone-history-3' in home
    assert get('https://rentmor.pl/online.js?v=phone-history-3') == (ROOT/'server/online.js').read_text()
    sitemap = ET.fromstring(get('https://rentmor.pl/sitemap.xml'))
    products = [e.text for e in sitemap.iter() if e.tag.endswith('loc') and e.text.endswith('/') and e.text != 'https://rentmor.pl/']
    assert len(products) >= 2
    for url in products[:2]:
        page = get(url)
        match = re.search(r'data-phone-source="([^"]+)"', page)
        assert match and match[1] != 'home', url
        assert '/online.js?v=phone-history-3' in page
    print('PASS: public tracker matches release; home and two product identities present; no test clicks sent')

def test_patch():
    legacy = '<script src="/online.js?v=browser-stats-2" data-view-token="<?php echo htmlspecialchars($rm_pageview_token ?? \'\', ENT_QUOTES, \'UTF-8\'); ?>" defer></script>'
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory)/'index.php'
        for old in ['', '<script src="/online.js" defer></script>', '<script src="/online.js?v=phone-events-1" defer></script>', legacy]:
            for detail in [False, True]:
                prefix = "<?php /* stats.json */ $detail_view=" + ('true' if detail else 'false') + "; $product=['id'=>'puzzi']; $rm_pageview_token='token'; ?>"
                path.write_text(prefix+'<body>'+old+'</body>')
                subprocess.run([sys.executable,str(ROOT/'scripts/inject_online_tracker.py'),str(path)],check=True,capture_output=True)
                first = path.read_text()
                subprocess.run([sys.executable,str(ROOT/'scripts/inject_online_tracker.py'),str(path)],check=True,capture_output=True)
                assert path.read_text() == first
                assert first.count('/online.js?') == 1
                assert 'phone-history-3' in first and 'data-phone-source=' in first
                if shutil.which('php'):
                    rendered = subprocess.check_output(['php',str(path)],text=True)
                    assert 'data-phone-source="'+('puzzi' if detail else 'home')+'"' in rendered
                    assert 'data-view-token="token"' in rendered
    print('PASS: tracker upgrades, one script, idempotence'+('; PHP rendering' if shutil.which('php') else '; PHP rendering deferred to CI'))

if __name__ == '__main__':
    verify_live() if '--verify-live' in sys.argv else test_patch()
