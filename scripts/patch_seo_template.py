#!/usr/bin/env python3
"""Idempotent additions to the current hosting template, never a stale copy."""
from pathlib import Path
import sys
import json

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
    return text

if __name__ == '__main__':
    p = Path(sys.argv[1]); p.write_text(patch(p.read_text()))
    print('Offer details, contextual guide links and organization schema prepared.')
