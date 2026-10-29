#!/usr/bin/env python3
"""Post-deploy smoke checks: meaningful SEO content and canonical host behavior."""
from html.parser import HTMLParser
import sys
from urllib.error import HTTPError
from urllib.request import Request, HTTPRedirectHandler, build_opener
from urllib.parse import urljoin

from verify_public_seo import BASE, Page, fetch, validate, GUIDE, PUZZI_GUIDE, AIRLESS_GUIDE
from verify_public_seo import PUZZI_OFFER, AIRLESS_OFFER, OFFERS

COMPACTOR_OFFER = '/zageszczarka-dro-masz-101-kg-do-kostki/'
FOREST_OFFER = '/kompaktowy-rebak-spalinowy-forest-master-fm6dd-6km/'
MSW_OFFER = '/szlifierka-do-betonu-i-posadzek-250mm-wynajem-krakow/'
CEDRUS_OFFER = '/rebak-cedrus-rb02-pro-rozdrabniacz-do-galezi/'
DEWALT_ANGLE_OFFER = '/szlifierka-katowa-125mm-dewalt-dwe4357-szlifierka-do-betonu/'

OFFER_GUIDES = {
    OFFERS[0]: (GUIDE, "69384693e2cb9"),
    PUZZI_OFFER: (PUZZI_GUIDE, "69388418830b8"),
    AIRLESS_OFFER: (AIRLESS_GUIDE, "69385f95de01c"),
    OFFERS[1]: (GUIDE, "69385df56774a"),
}
TITLE_PARTS = {
    "/ozonator-20000-mg-h-ulsonix/": "Ozonator ULSONIX",
    "/zaciskarka-praska-do-rur-pex-wynajem-systemu-u-th/": "Zaciskarka Energotytan",
    "/drabina-skladana-podest-krause-corda/": "Drabina Krause",
    "/bruzdownica-stalco/": "Bruzdownica Stalco",
    AIRLESS_OFFER: "Wabrotech WT20i",
    OFFERS[0]: "Szlifierka żyrafa Stanley",
    OFFERS[1]: "Żyrafa Stanley z odkurzaczem",
}

class Title(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.in_title = False
        self.title = ""
        self.feed(html)
    def handle_starttag(self, tag, attrs):
        if tag == "title":
            self.in_title = True
    def handle_endtag(self, tag):
        if tag == "title":
            self.in_title = False
    def handle_data(self, data):
        if self.in_title:
            self.title += data

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def verify_redirect(url, expected):
    req = Request(url, headers={"User-Agent": "RentMorSEOCheck/1.0", "Cache-Control": "no-cache"})
    try:
        response = build_opener(NoRedirect).open(req, timeout=20)
    except HTTPError as exc:
        response = exc
    with response:
        assert response.code in (301, 308), (url, response.code)
        location = response.headers.get("Location")
        assert location and urljoin(url, location) == expected, (url, location, expected)

def main():
    home, hdrs = fetch("/")
    homepage = validate(home, hdrs, BASE + "/")
    organizations = [node for schema in homepage.schemas
                     for node in (schema.get("@graph", []) if isinstance(schema, dict) and "@graph" in schema else [schema])
                     if isinstance(node, dict) and node.get("@type") == "Organization"
                     and node.get("@id") == BASE + "/#organization"]
    assert len(organizations) == 1, ("Organization JSON-LD", organizations)
    assert organizations[0]["telephone"] == "+48733891281"
    assert COMPACTOR_OFFER in homepage.links, ('Missing compactor homepage link', COMPACTOR_OFFER)
    for path, (guide, product_id) in OFFER_GUIDES.items():
        html, headers = fetch(path)
        parsed = validate(html, headers, BASE + path)
        assert f'data-rentmor-offer-details="{product_id}"' in html, ("Missing details", path)
        assert guide in parsed.links, ("Missing contextual guide", path)
        assert "/zasady.php" in parsed.links, ("Missing terms", path)
        assert 'id="wynajem-sprzetu-krakow"' not in html, ("Generic homepage section on offer", path)
        assert 'id="poradniki"' not in html, ("Generic guide card on offer", path)
        if path == OFFERS[0]:
            assert OFFERS[1] in parsed.links and 'Kiedy wybrać samą żyrafę' in html
        if path == AIRLESS_OFFER:
            assert "210 bar" in html and "Nie dopuszczamy farb olejnych ani rozpuszczalnikowych." in html
    for path, expected_title in TITLE_PARTS.items():
        html, _ = fetch(path)
        title = Title(html).title
        assert expected_title in title and title.endswith("| RentMor"), (path, title, expected_title)
    compactor_html, compactor_headers = fetch(COMPACTOR_OFFER)
    validate(compactor_html, compactor_headers, BASE + COMPACTOR_OFFER)
    assert 'data-rentmor-offer-details="693844ff66e57"' in compactor_html
    assert '18 kN' in compactor_html and '101 kg' in compactor_html
    for offer_path, expected_snippets, related_paths in (
        (FOREST_OFFER, ('Forest Master FM6DD', '91 × 48 × 78 cm', 'mokrych po deszczu'), (CEDRUS_OFFER,)),
        (MSW_OFFER, ('MSW-FG-250', '40 zł za każde rozpoczęte 0,5 mm', '30 zł za dobę'), (DEWALT_ANGLE_OFFER,)),
    ):
        html, headers = fetch(offer_path)
        parsed = validate(html, headers, BASE + offer_path)
        assert 'data-rentmor-offer-details=' in html, ('Missing offer details', offer_path)
        for phrase in expected_snippets:
            assert phrase in html, ('Missing new SEO content', offer_path, phrase)
        for related_path in related_paths:
            assert related_path in parsed.links, ('Missing contextual internal link', offer_path, related_path)
        assert '/zasady.php' in parsed.links
    verify_redirects()
    print("PASS: offers incl. Forest Master/MSW, canonical schema and 301 redirects.")

def verify_redirects():
    verify_redirect("http://rentmor.pl/robots.txt?seo_check=1",
                    "https://rentmor.pl/robots.txt?seo_check=1")
    verify_redirect("https://www.rentmor.pl/robots.txt?seo_check=1",
                    "https://rentmor.pl/robots.txt?seo_check=1")
    # Important while Google transitions from legacy ?id= links to clean URLs.
    verify_redirect("https://rentmor.pl/index.php?id=693844ff66e57", BASE + COMPACTOR_OFFER)
    verify_redirect("https://rentmor.pl/index.php?id=69388418830b8", BASE + PUZZI_OFFER)
    verify_redirect("https://rentmor.pl/index.php?id=69385df56774a", BASE + OFFERS[1])
    verify_redirect("https://rentmor.pl/index.php?id=69384693e2cb9", BASE + OFFERS[0])

if __name__ == "__main__":
    if "--redirects-only" in sys.argv:
        verify_redirects()
        print("PASS: canonical HTTP and www redirects.")
    else:
        main()
