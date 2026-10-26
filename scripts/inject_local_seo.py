#!/usr/bin/env python3
"""Idempotent commercial-local SEO section in the live public index.php.
Read the current server file first. Never replace it with a stale repository copy.
"""
from pathlib import Path
import re
import sys

MARKER = '<!-- rentmor-krakow-seo-v1 -->'
COMPACTOR_MARKER = '<!-- rentmor-compactor-link-v1 -->'
COMPACTOR_ANCHOR = '<h3 class="text-lg font-bold mt-7 mb-3">Jak wypożyczyć sprzęt w RentMor?</h3>'
COMPACTOR_BLOCK = '''<!-- rentmor-compactor-link-v1 -->
    <h3 class="text-lg font-bold mt-7 mb-3">Zagęszczarka do kostki brukowej — wynajem Kraków</h3>
    <p class="text-gray-700 mb-5">Potrzebujesz zagęścić podbudowę lub przygotować nawierzchnię z kostki? <a href="/zageszczarka-dro-masz-101-kg-do-kostki/" class="font-semibold underline">Sprawdź wynajem zagęszczarki Dro-Masz 18 kN, 101 kg w Krakowie</a>. Sprzęt ma koła transportowe i matę ochronną do kostki. Odbiór osobisty w Czyżynach.</p>
    '''

SECTION = '''<!-- rentmor-krakow-seo-v1 -->
<section id="wynajem-sprzetu-krakow" aria-labelledby="rentmor-local-title" class="max-w-7xl mx-auto w-full px-4 py-8">
  <div class="bg-white border border-gray-200 rounded-2xl p-5 sm:p-8 text-gray-800">
    <h2 id="rentmor-local-title" class="text-xl sm:text-2xl font-bold mb-4">Wynajem sprzętu budowlanego i do czyszczenia w Krakowie</h2>
    <p class="text-gray-700 mb-5">RentMor to lokalna wypożyczalnia sprzętu w Krakowie (Czyżyny, ul. Woźniców). Wynajmujemy narzędzia osobom prywatnym i firmom: do remontu mieszkania, szlifowania ścian, prania tapicerki czy prac ogrodniczych. Odbiór odbywa się osobiście po ustaleniu terminu. Dokładny adres podajemy przy rezerwacji.</p>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-5">
      <div class="rounded-xl bg-gray-50 border border-gray-200 p-4">
        <h3 class="font-bold text-lg mb-2"><a href="/odkurzacz-pioracy-karcher-puzzi-10-1/" class="underline">Wynajem odkurzacza piorącego Karcher Puzzi</a></h3>
        <p class="text-sm text-gray-700">Samodzielne czyszczenie kanap, dywanów i tapicerki samochodowej. W ofercie końcówki do dywanów i mebli oraz opcjonalny środek piorący.</p>
        <a class="inline-block mt-3 font-semibold underline" href="/poradnik-pranie-tapicerki-karcher-puzzi.php">Jak prać tapicerkę Puzzi 10/1?</a>
      </div>
      <div class="rounded-xl bg-gray-50 border border-gray-200 p-4">
        <h3 class="font-bold text-lg mb-2"><a href="/szlifierka-stanley-zyrafa-do-gladzi-gipsu/" class="underline">Wynajem szlifierki żyrafy do gładzi</a></h3>
        <p class="text-sm text-gray-700">Szlifowanie ścian i sufitów po pracach wykończeniowych. Możesz dobrać papier ścierny oraz wynająć sprzęt na potrzebny termin.</p>
        <a class="inline-block mt-3 font-semibold underline" href="/poradnik-jaki-papier-scierny-do-zyrafy.php">Jaką gradację papieru wybrać?</a>
      </div>
      <div class="rounded-xl bg-gray-50 border border-gray-200 p-4">
        <h3 class="font-bold text-lg mb-2"><a href="/zestaw-szlifierka-stanley-zyrafa-do-gladzi-i-odkurzacz-bosch-metabo/" class="underline">Żyrafa z odkurzaczem — wynajem zestawu</a></h3>
        <p class="text-sm text-gray-700">Zestaw do szlifowania z odsysaniem pyłu: żyrafa Stanley i odkurzacz przemysłowy Bosch lub Metabo. Sprawdź warunki oraz dostępność.</p>
      </div>
    </div>
    <h3 class="text-lg font-bold mt-7 mb-3">Jak wypożyczyć sprzęt w RentMor?</h3>
    <ol class="list-decimal pl-5 space-y-2 text-gray-700">
      <li>Wybierz maszynę i sprawdź aktualną cenę dobową oraz wysokość kaucji w szczegółach oferty.</li>
      <li>Skontaktuj się z nami i ustal termin odbioru oraz zwrotu.</li>
      <li>Przy odbiorze podpisujesz umowę najmu i wpłacasz kaucję. Sprzęt przewozisz we własnym zakresie.</li>
    </ol>
    <p class="text-gray-700 mt-5">Nie wiesz, jaką maszynę dobrać do zadania? <a href="tel:+48733891281" class="font-bold underline">Zadzwoń: 733 891 281</a> i zapytaj o dostępność. Możesz też zapoznać się z <a href="/zasady.php" class="underline">zasadami wynajmu</a>.</p>
  </div>
</section>
'''

def patch(html):
    if MARKER in html:
        if html.count(MARKER) != 1:
            raise ValueError("Duplicate SEO section markers: refusing patch")
        if html.count(COMPACTOR_MARKER) > 1:
            raise ValueError("Duplicate compactor link markers: refusing patch")
        if COMPACTOR_MARKER not in html:
            if html.count(COMPACTOR_ANCHOR) != 1:
                raise ValueError("Unknown local SEO layout: refusing compactor link update")
            return html.replace(COMPACTOR_ANCHOR, COMPACTOR_BLOCK + COMPACTOR_ANCHOR)
        return html
    if '<?php' not in html or 'stats.json' not in html:
        raise ValueError('Unknown homepage template; refusing to patch')
    footers = list(re.finditer(r'<footer\b', html, re.I))
    if len(footers) != 1:
        raise ValueError(f'Expected exactly one footer, found {len(footers)}')
    if '<h1' not in html.lower():
        raise ValueError('Missing homepage H1')
    start=footers[0].start()
    return patch(html[:start]+SECTION+html[start:])

if __name__ == '__main__':
    path=Path(sys.argv[1])
    raw=path.read_text(encoding='utf-8')
    updated=patch(raw)
    if updated!=raw:
        path.write_text(updated,encoding='utf-8')
        print('Inserted Kraków local rental links and useful service details before footer.')
    else:print('Local SEO section already present; no change.')
