"""Read-only production checks for the requested SEO changes."""
import json
import re
import xml.etree.ElementTree as ET
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from verify_public_seo import fetch, validate, BASE
from verify_seo_improvements import verify_redirect, verify_redirects, MSW_OFFER

def main():
    home, headers = fetch('/')
    page = validate(home, headers, BASE+'/')
    assert 'data-rm-home-structure="v1"' in home
    assert home.count('id="poradniki"') == home.count('id="wynajem-sprzetu-krakow"') == 1
    assert 'Szukasz sprzętu do remontu' not in home
    assert home.count('id="rm-category-menu"') == 1
    assert 'class="rm-seo-links"' not in home
    assert '/assets/rentmor-blueprint-mobile-20261103.webp' in home
    assert '/assets/rentmor-blueprint-mobile-20261102.webp' not in home
    assert 'src="/assets/rentmor-logo-mobile-20261103.webp"' in home
    assert 'srcset="/assets/rentmor-logo-mobile-20261103.webp 1x"' in home
    assert 'sprzęt budowlany na wynajem' in home
    for path, size_limit in [('/assets/rentmor-blueprint-mobile-20261103.webp', 145000),
                              ('/assets/rentmor-logo-mobile-20261103.webp', 50000)]:
        with urlopen(Request(BASE + path, headers={'User-Agent': 'RentMorSeoReleaseCheck/1.0'}),timeout=30) as response:
            assert response.status == 200 and 'image/webp' in response.headers.get('Content-Type','')
            assert len(response.read()) < size_limit

    assert home.index('id="rm-category-menu"') < home.index('<h1')
    categories = ['sprzet-budowlany', 'sprzet-ogrodniczy', 'sprzet-czyszczacy']
    xml, _ = fetch('/sitemap.xml')
    urls = [x.text for x in ET.fromstring(xml).findall('{*}url/{*}loc')]
    assert len(urls) == len(set(urls))
    for slug in categories:
        path = '/'+slug+'/'
        assert path in page.links and BASE+path in urls
        body, hdrs = fetch(path)
        assert body.count('id="rm-category-menu"') == 1
        category = validate(body, hdrs, BASE+path)
        collection = next(s for s in category.schemas if s.get('@type') == 'CollectionPage')
        items = collection['mainEntity']['itemListElement']
        assert items and collection['mainEntity']['numberOfItems'] == len(items)
        assert all(item['url'] in urls for item in items)
        assert '/polityka-prywatnosci.php' in category.links
        assert 'cookie-consent.js' in body and not hdrs.get('Set-Cookie')
        verify_redirect(BASE+'/'+slug, BASE+path)
    offer, hdrs = fetch(MSW_OFFER)
    assert offer.count('id="rm-category-menu"') == 1
    parsed = validate(offer, hdrs, BASE+MSW_OFFER)
    schema = next(s for s in parsed.schemas if s.get('@type') == 'Product')
    rental = schema['offers']
    assert rental['businessFunction'].endswith('#LeaseOut')
    assert rental['priceSpecification']['price'] == rental['price']
    assert rental['priceSpecification']['referenceQuantity'] == {'@type':'QuantitativeValue','value':1,'unitCode':'DAY'}
    assert 'availability' not in rental and 'data-rm-rental-terms="v1"' in offer
    assert 'id="poradniki"' not in offer
    verify_redirect(BASE+'/index.php', BASE+'/')
    verify_redirects() # legacy query-based products, www and HTTP must remain functional
    print('PASS: public categories, sitemap, consolidated home, rental schema and legacy redirects')

if __name__ == '__main__': main()
