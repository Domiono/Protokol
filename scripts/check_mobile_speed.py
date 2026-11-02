import re
from urllib.request import urlopen
from patch_mobile_speed import ASSET
for path in ['/', '/odkurzacz-pioracy-karcher-puzzi-10-1/']:
    with urlopen('https://rentmor.pl'+path, timeout=40) as r:
        assert r.status==200
        html=r.read().decode()
    assert 'aria-label="Zadzwoń do RentMor"' in html
    assert ASSET in html
    assert 'cookie-consent.js' in html
with urlopen('https://rentmor.pl'+ASSET, timeout=40) as r:
    assert r.status==200 and 'image/webp' in r.headers['Content-Type']
    assert 'max-age=31536000' in r.headers.get('Cache-Control','')
    data=r.read(); assert len(data)<220000, len(data)
for path in ['/rentmor-gallery.js?v=3','/rentmor-gallery.css?v=1','/rentmor-logo.png?v=rm-20261030-wordmark-v2']:
    with urlopen('https://rentmor.pl'+path,timeout=40) as r:
        assert 'max-age=' in r.headers.get('Cache-Control',''), path
print('PASS: phone name, mobile background, static cache and consent script')
