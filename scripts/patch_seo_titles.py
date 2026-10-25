#!/usr/bin/env python3
"""Correct public titles without changing names/slugs, prices or inventory."""
import copy
import json
from pathlib import Path
import sys

TITLES = {
    'wt20i': 'Agregat malarski WT20i – wynajem Kraków | RentMor',
    'ozonator': 'Ozonator 20 000 mg/h z UV – wynajem Kraków | RentMor',
    'zaciskarka': 'Zaciskarka do rur PEX U/TH – wynajem Kraków | RentMor',
    'krause': 'Drabina Krause Corda z podestem – wynajem Kraków | RentMor',
    'bruzdownica': 'Bruzdownica Stalco – wynajem Kraków | RentMor',
}

def patch(items):
    if not isinstance(items, list): raise ValueError('Expected inventory array')
    result = copy.deepcopy(items)
    for key, title in TITLES.items():
        matches = [p for p in result if key in p.get('name', '').lower()]
        if len(matches) != 1: raise ValueError('Expected one product: ' + key)
        matches[0]['seo_title'] = title
    for key, title in [
        ('69385df56774a', 'Żyrafa Stanley z odkurzaczem – wynajem Kraków | RentMor'),
    ]:
        matches = [p for p in result if str(p.get('id')) == key]
        if len(matches) != 1: raise ValueError('Expected known rental set')
        matches[0]['seo_title'] = title
        matches[0]['desc'] = matches[0].get('desc', '').replace('ZESTA ZAWIERA:', 'ZESTAW ZAWIERA:')
    singles = [p for p in result if 'stanley' in p.get('name', '').lower() and 'zestaw' not in p.get('name', '').lower()]
    if len(singles) != 1: raise ValueError('Expected one standalone Stanley sander')
    singles[0]['seo_title'] = 'Szlifierka żyrafa Stanley – wynajem Kraków | RentMor'
    return result

if __name__ == '__main__':
    source, target = map(Path, sys.argv[1:3])
    raw = source.read_bytes(); items = json.loads(raw); result = patch(items)
    target.write_bytes(raw if items == result else (json.dumps(result, ensure_ascii=False, indent=4) + '\n').encode())
    print('Public SEO titles validated; names, prices and inventory preserved.')
