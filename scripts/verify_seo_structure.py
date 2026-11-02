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
    categories = ['sprzet-budowlany', 'sprzet-ogrodniczy', 'sprzet-czyszczacy']
    xml, _ = fetch('/sitemap.xml')
    urls = [x.text for x in ET.fromstring(xml).findall('{*}url/{*}loc')]
    assert len(urls) == len(set(urls))
    for slug in categories:
        path = '/'+slug+'/'
        assert path in page.links and BASE+path in urls
        body, hdrs = fetch(path)
        category = validate(body, hdrs, BASE+path)
        collection = next(s for s in category.schemas if s.get('@type') == 'CollectionPage')
        items = collection['mainEntity']['itemListElement']
        assert items and collection['mainEntity']['numberOfItems'] == len(items)
        assert all(item['url'] in urls for item in items)
        assert '/polityka-prywatnosci.php' in category.links
        assert 'cookie-consent.js' in body and not hdrs.get('Set-Cookie')
        verify_redirect(BASE+'/'+slug, BASE+path)
    offer, hdrs = fetch(MSW_OFFER)
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
