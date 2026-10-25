#!/usr/bin/env python3
"""Post-deploy smoke checks: meaningful SEO content and canonical host behavior."""
from html.parser import HTMLParser
import sys
from urllib.error import HTTPError
from urllib.request import Request, HTTPRedirectHandler, build_opener

from verify_public_seo import BASE, Page, fetch, validate, GUIDE, PUZZI_GUIDE, AIRLESS_GUIDE
from verify_public_seo import PUZZI_OFFER, AIRLESS_OFFER, OFFERS

OFFER_GUIDES = {
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
        assert response.headers.get("Location") == expected, (url, response.headers.get("Location"), expected)

def main():
    home, hdrs = fetch("/")
    homepage = validate(home, hdrs, BASE + "/")
    organizations = [node for schema in homepage.schemas
                     for node in (schema.get("@graph", []) if isinstance(schema, dict) and "@graph" in schema else [schema])
                     if isinstance(node, dict) and node.get("@type") == "Organization"
                     and node.get("@id") == BASE + "/#organization"]
    assert len(organizations) == 1, ("Organization JSON-LD", organizations)
    assert organizations[0]["telephone"] == "+48733891281"
    for path, (guide, product_id) in OFFER_GUIDES.items():
        html, headers = fetch(path)
        parsed = validate(html, headers, BASE + path)
        assert f'data-rentmor-offer-details="{product_id}"' in html, ("Missing details", path)
        assert guide in parsed.links, ("Missing contextual guide", path)
        assert "/zasady.php" in parsed.links, ("Missing terms", path)
        if path == AIRLESS_OFFER:
            assert "210 bar" in html and "Nie dopuszczamy farb olejnych ani rozpuszczalnikowych." in html
    for path, expected_title in TITLE_PARTS.items():
        html, _ = fetch(path)
        title = Title(html).title
        assert expected_title in title and title.endswith("| RentMor"), (path, title, expected_title)
    verify_redirects()
    print("PASS: enriched offers, Organization schema, local SEO titles and 301 canonical host.")

def verify_redirects():
    verify_redirect("http://rentmor.pl/robots.txt?seo_check=1",
                    "https://rentmor.pl/robots.txt?seo_check=1")
    verify_redirect("https://www.rentmor.pl/robots.txt?seo_check=1",
                    "https://rentmor.pl/robots.txt?seo_check=1")

if __name__ == "__main__":
    if "--redirects-only" in sys.argv:
        verify_redirects()
        print("PASS: canonical HTTP and www redirects.")
    else:
        main()
