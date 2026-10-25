#!/usr/bin/env python3
"""Add an idempotent WT20i guide link to the deployed homepage.
Operate only on the actual current index.php copied from hosting.
"""
from pathlib import Path
import sys

MARKER = '<!-- rentmor-guide-airless-wt20i-v1 -->'
LOCAL = '<!-- rentmor-krakow-seo-v1 -->'
ANCHOR = '    <h3 class="text-lg font-bold mt-7 mb-3">Jak wypożyczyć sprzęt w RentMor?</h3>'
CARD = '''    <!-- rentmor-guide-airless-wt20i-v1 -->
    <div class="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-5">
      <h3 class="font-bold text-lg text-gray-900 mb-2">Agregat malarski — którą dyszę wybrać?</h3>
      <p class="text-gray-700 mb-2">Zobacz praktyczny poradnik o malowaniu natryskowym: dysze 511, 517 i 521, przygotowanie urządzenia, czyszczenie oraz filmy producenta WABROTECH.</p>
      <p><a href="/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php" class="font-bold underline">Jak korzystać z agregatu malarskiego?</a> · <a href="/agregat-malarski-wabrotech-wt20i-wynajem-krakow-malowanie-natryskowe/" class="font-bold underline">Wynajem agregatu WT20i w Krakowie</a></p>
    </div>
'''

def patch(html):
    if html.count(MARKER)>1:raise ValueError("Duplicate sprayer guide marker")
    if MARKER in html:return html
    if html.count(LOCAL)!=1:raise ValueError("Expected one existing local SEO section")
    start=html.index(LOCAL)
    tail=html[start:]
    if tail.count(ANCHOR)!=1:raise ValueError("Cannot find a unique local section insert point")
    pos=html.index(ANCHOR,start)
    if not (0 < pos-html.index(LOCAL)<9000):raise ValueError("Unexpected homepage layout")
    return html[:pos]+CARD+html[pos:]

if __name__ == '__main__':
    page=Path(sys.argv[1])
    raw=page.read_text(encoding="utf-8")
    updated=patch(raw)
    if raw!=updated:
        page.write_text(updated,encoding="utf-8")
        print("Added guide and rental links to home page, without replacing product data.")
    else:print("Guide link already present; unchanged.")
