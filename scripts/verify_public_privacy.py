#!/usr/bin/env python3
"""Public, read-only cookie/privacy smoke tests. No artificial customer actions."""
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import re
BASE = "https://rentmor.pl"
def get(path, headers=None):
    with urlopen(Request(BASE+path, headers={"User-Agent":"Mozilla/5.0 RentMorPrivacyVerification", "Cache-Control":"no-cache", **(headers or {})}),timeout=30) as r:
        return r.read().decode('utf-8'),r.headers
home,headers=get("/")
assert 'href="/polityka-prywatnosci.php"' in home
assert 'data-rm-cookie-settings' in home
assert 'src="/cookie-consent.js"' in home
# Verify visible form notice and consent behavior, not PHP comments:
# PHP comments disappear when index.php is rendered, and HTML comments
# may be stripped by page optimization.
# The contact form lives on individual offer pages, not the catalogue home.
offer,_=get("/odkurzacz-pioracy-karcher-puzzi-10-1/")
assert 'Administratorem danych z formularza jest' in offer, 'Offer contact privacy notice not visible'
assert 'Dominik Morawski (RentMor.pl)' in offer, 'Offer contact controller not shown'
assert 'href="/polityka-prywatnosci.php"' in offer, 'Offer missing privacy link'
assert "document.cookie.split('; ').indexOf('rm_stats_consent=allow')" in home, 'Legacy click tracking lacks an opt-in guard'
assert 'rm_stats_consent=allow' in home
assert not re.search(r'PHPSESSID=',str(headers.get_all("Set-Cookie",[])),re.I), "Statistical PHP session cookie set before consent"
script,_=get("/cookie-consent.js")
assert "rm_stats_consent" in script and 'Tylko niezbędne' in script
assert 'Zgadzam się na statystyki' in script
privacy,_=get("/polityka-prywatnosci.php")
assert 'Dominik Morawski' in privacy and '12 miesięcy' in privacy
assert 'DO POTWIERDZENIA' not in privacy
guide,_=get("/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php")
assert guide.count('class="video-frame video-gate"')==2
assert '<iframe' not in guide
# A syntactically correct token with consent denied must never update stats.
from urllib.parse import urlencode
try:
    with urlopen(Request(BASE+"/pageview.php",
          data=urlencode({'token':'a'*48}).encode('ascii'),
          headers={'Cookie':'rm_stats_consent=deny','Origin':BASE,'User-Agent':'Mozilla/5.0','Content-Type':'application/x-www-form-urlencoded'}),timeout=30) as r:
        raise AssertionError("Analytics endpoint accepted denied consent: "+str(r.status))
except HTTPError as e:
    assert e.code==403, ("Wrong analytics rejection",e.code)
print("PASS: production policy, footer, consent options, contact notice, gated session, declined statistics, YouTube click-to-load")
