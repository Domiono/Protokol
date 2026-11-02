import json,re
from pathlib import Path
from urllib.request import urlopen
m=json.loads(Path('reports/font-assets/manifest.json').read_text())
for path in ['/', '/odkurzacz-pioracy-karcher-puzzi-10-1/']:
    with urlopen('https://rentmor.pl'+path,timeout=40) as r:s=r.read().decode()
    assert m['css'] in s and m['logo'] in s
    assert not re.search(r'<link[^>]+href="https://(?:fonts.googleapis.com/css|cdnjs.cloudflare.com/ajax/libs/font-awesome)',s)
    assert 'aria-label="Zadzwoń do RentMor"' in s
for p in Path('reports/font-assets').iterdir():
    if p.suffix=='.json':continue
    with urlopen('https://rentmor.pl/assets/'+p.name,timeout=40) as r:
        assert r.read()==p.read_bytes(),p.name
        assert 'max-age=' in r.headers.get('Cache-Control','')
print('PASS: local fonts and current compact logo on home and offer, assets match byte-for-byte')
