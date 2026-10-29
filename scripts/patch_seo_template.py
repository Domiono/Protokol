#!/usr/bin/env python3
"""Idempotent additions to the current hosting template, never a stale copy."""
from pathlib import Path
import sys
import json
import re

ORG = {'@context':'https://schema.org','@type':'Organization','@id':'https://rentmor.pl/#organization',
       'name':'RentMor.pl','url':'https://rentmor.pl/',
       'telephone':'+48733891281','description':'Wypożyczalnia sprzętu budowlanego, ogrodniczego i czyszczącego w Krakowie, w Czyżynach.'}

def patch(text):
    marker = '<!-- rentmor-offer-details-v1 -->'
    if text.count(marker) > 1: raise ValueError('Duplicate offer details marker')
    if marker not in text:
        anchor = "echo rentmor_render_description($product['desc'] ?? '');"
        if text.count(anchor) != 1: raise ValueError('Unknown product description template')
        text = text.replace(anchor, anchor + "\n                        require_once __DIR__ . '/rental_offer_details.php';\n                        echo rentmor_offer_details($product); /* " + marker + " */")
    marker = '<!-- rentmor-organization-v1 -->'
    if text.count(marker) > 1: raise ValueError('Duplicate organization schema')
    if marker not in text:
        if text.count('</head>') != 1: raise ValueError('Expected one head')
        schema = marker + '\n<script type="application/ld+json">' + json.dumps(ORG, ensure_ascii=False) + '</script>\n'
        text = text.replace('</head>', schema + '</head>')
    # Every product detail gets recommendations from the *current* public catalogue.
    # On production, suppress the old editorial related list to avoid duplicated links.
    related_marker = '<!-- rentmor-related-offers-v1 -->'
    if text.count(related_marker) > 1: raise ValueError('Duplicate related-offers marker')
    if related_marker not in text:
        old = "echo rentmor_offer_details($product); /* <!-- rentmor-offer-details-v1 --> */"
        if text.count(old) != 1: raise ValueError('Unknown offer details insertion')
        new = """echo rentmor_offer_details($product, false); /* <!-- rentmor-offer-details-v1 --> */
                        require_once __DIR__ . '/related_offers.php';
                        echo rentmor_render_related_offers($product); /* <!-- rentmor-related-offers-v1 --> */"""
        text = text.replace(old, new, 1)
    elif "echo rentmor_render_related_offers($product)" not in text:
        raise ValueError('Incomplete related-offers integration')

    # General catalogue sections belong on the home page; offers have contextual content.
    for key in ('guide-link', 'krakow-seo'):
        marker = '<!-- rentmor-' + key + '-v1 -->'
        guard = '<!-- rentmor-home-only-' + key + '-v1 -->'
        opening = guard + "<?php if (empty($detail_view)): ?>"
        ending = "<?php endif; ?><!-- /rentmor-home-only-" + key + "-v1 -->"
        if text.count(marker) > 1 or text.count(guard) > 1:
            raise ValueError('Duplicate home section marker: ' + key)
        if marker not in text:
            continue
        match = re.search(re.escape(marker) + r'\s*<section\b[^>]*>.*?</section>', text, re.S)
        if not match or '<section' in match.group()[len(marker):].split('>', 1)[1]:
            raise ValueError('Unknown or nested home section: ' + key)
        if guard in text:
            if opening + match.group() + ending not in text:
                raise ValueError('Broken home section guard: ' + key)
            continue
        text = text[:match.start()] + opening + match.group() + ending + text[match.end():]
    return text

if __name__ == '__main__':
    p = Path(sys.argv[1]); p.write_text(patch(p.read_text()))
    print('Offer details, contextual guide links and organization schema prepared.')
