# Google Search Console — RentMor.pl (28.10.2026)

Źródło: połączone Windsor.ai → Google Search Console, konto `sc-domain:rentmor.pl`. Dane pobrane 28.10.2026; wybrano pełne, wcześniejsze okresy ze względu na opóźnienie raportowania. **Nie są to dane Google Analytics ani wewnętrznych statystyk odwiedzin RentMor.**

## Wyniki witryny: 28 dni vs poprzednie 28 dni

| Metryka | 28.09–25.10.2026 | 31.08–27.09.2026 | Zmiana |
| --- | ---: | ---: | ---: |
| Kliknięcia | 20 | 16 | +4 (+25%) |
| Wyświetlenia | 603 | 766 | −163 (−21,3%) |
| CTR | 3,32% | 2,09% | +1,23 punktu procentowego |
| Średnia pozycja | 19,80 | 25,09 | poprawa o 5,29 pozycji |

Mniejsza liczba wyświetleń mimo większej liczby kliknięć. Skala niewielka; nie wyciągać wniosków o trwałym trendzie po jednym miesiącu. Średnia pozycja zmienia się również wraz z miksem zapytań.

## Najważniejsze strony do dopracowania (wymiar Page, ostatnie 28 dni)

| Oferta / adres raportowany przez GSC | Wyświetlenia | Kliknięcia | CTR | Średnia pozycja |
| --- | ---: | ---: | ---: | ---: |
| Drabina KRAUSE Corda (stary /index.php?id=6938228c7c4bb) | 126 | 0 | 0% | 28,48 |
| Szlifierka żyrafa Stanley (stary /index.php?id=69384693e2cb9) | 110 | 1 | 0,91% | 9,03 |
| Zagęszczarka 18 kN (stary /index.php?id=693844ff66e57) | 109 | 0 | 0% | 13,22 |
| Zestaw żyrafa+odkurzacz (stary /index.php?id=69385df56774a) | 48 | 0 | 0% | 8,50 |
| Bruzdownica Stalco (nowy /bruzdownica-stalco/) | 4 | 0 | 0% | 4,50 |

Dane w wymiarze strony *nie muszą sumować się do sum witryny*. Nie traktować samego wiersza starego URL jako dowodu obecnej indeksacji — raport może obejmować dane sprzed migracji, a Google aktualizuje adresy stopniowo. Porównać bieżące 301, canonical i sitemap.

## Zapytania sygnalizujące popyt

| Zapytanie | Wyświetlenia | Kliknięcia | Średnia pozycja |
| --- | ---: | ---: | ---: |
| wynajem zagęszczarka kraków | 52 | 0 | 22,06 |
| wynajem drabina z podestem rozstawna | 35 | 0 | 27,86 |
| wynajem drabina z podestem | 15 | 0 | 25,47 |
| wypożyczalnia sprzętu budowlanego kraków | 28 | 0 | 41,43 |
| wynajem sprzętu budowlanego kraków | 24 | 0 | 49,29 |

Dane pojedynczych zapytań są ograniczone filtrowaniem i progami prywatności GSC. Nie sumować ich do ruchu całej domeny.

## Sitemapa i indeksacja

Windsor GSC: mapa `https://rentmor.pl/sitemap.xml` z **29 zgłoszonymi URL**, **0 raportowanych błędów** i **0 ostrzeżeń**. Ostatnie pobranie sitemapy: 27.10.2026 ok. 19:36 UTC. Pole „indexed” w konektorze jest oznaczone jako przestarzałe, więc **nie podajemy liczby faktycznie zaindeksowanych stron**. Do pełnego raportu pokrycia indeksacją potrzebne są dane „Strony” w interfejsie GSC lub odpowiednie API.

## Priorytety i plan

1. **Drabina KRAUSE** — 126 wyświetleń, 0 kliknięć. Rozszerzyć opis, odpowiedzieć na intencje „wynajem drabina z podestem” i „prace przy suficie”, poprawić wewnętrzne powiązania i sprawdzić nagłówek/snippet. Przygotowane w PR dla bruzdownicy/drabiny.
2. **Zagęszczarka 18 kN** — 109 wyświetleń, 0 kliknięć i 52 wyświetlenia zapytania „wynajem zagęszczarka kraków”. Opis poradnikowy już istnieje. Najpierw sprawdzić publiczne title/meta/canonical i zgodność stawki, nie mnożyć tej samej treści.
3. **Żyrafa Stanley (solo) i zestaw** — osobna żyrafa 110 wyświetleń i 1 kliknięcie, zestaw 48 i 0. Dodać opis sytuacji „sama żyrafa czy zestaw z odkurzaczem”, wyraźne linkowanie do poradnika papieru ściernego, bez powtarzania tych samych FAQ na kilku URL.
4. **Bruzdownica Stalco** — opis rozszerzony w PR #53; ma dopiero 4 wyświetlenia w analizowanym okresie, więc nie traktować jej jako największej szansy na krótkoterminowe kliknięcia.

**Weryfikacja po wdrożeniu:** porównać kolejne dwa 28-dniowe okresy, śledząc nowe adresy i kanoniczne URL, a także sprawdzić, czy Google nadal pokazuje adresy /index.php?id=... . Wyświetlenia nie są równoznaczne z wejściami, a średnia pozycja nie jest stałą pozycją konkretnej frazy.
