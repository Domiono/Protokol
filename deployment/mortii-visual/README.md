# Mortii – oprawa graficzna czatu (10.11.2026)

**Status: zapisane na produkcyjnym hostingu RentMor.pl**, z zatwierdzoną sesją RentMor Control. Testy źródłowe przeszły; test przeglądarkowy wyglądu i wysyłanie wiadomości AI nie zostały wykonane.

## Faktycznie wdrożone zasoby

- `/rentmor-ai-mortii-visual.js?v=1` – skrypt z obydwoma obrazami WebP jako wbudowane `data:image/webp;base64`. SHA256 na serwerze: `e279b979d15cb8ae1b9f689d34658697ef7ef662a16fed7552ccc8b469ac43f4`.
- `/rentmor-ai-mortii-visual.css?v=1` – styl z małą, okrągłą ikoną (62 × 62 px) na telefonie. SHA256: `aa6006645bcb7fca82de0d571ceead1a7bcaa02897f33734dba83487c515652e`.
- Dwa oryginalne WebP pozostają w repozytorium `deployment/mortii-visual/assets/`. Nie wysłano ich osobno do `/assets/`, bo hosting odrzucił zapis do tego katalogu. Wbudowanie w JS usuwa zależność od dodatkowych plików i zewnętrznego GitHuba.

## Zmiany na hostingu (bez zastępowania produkcji starszą wersją repo)

- `public_metadata.php` – automatyczne dołączanie skryptu oprawy po `/mortii.js?v=5`, bez duplikacji. SHA256 `819f0bfe530eb7fcaa00d8aa26b97435f33ff3f13c03376fadd8c7c23fcd2ff7`.
- `polityka-prywatnosci.php` – znacznik skryptu w nagłówku. SHA256 `baa0aa97e57efd123bcc577604b983d6801bb9d1fac440969e7dee43d6b04916`.
- `category.php` – dołączenie `/mortii.css?v=2`, `/mortii.js?v=5` i skryptu oprawy. SHA256 `fa429dfe1ca7648ea4599bd67eea29cdd0a07c2df240fa425bd3d3d5c827b5a9`.
- `rental_categories.php` **nie zmieniano** (plik niedostępny do edycji). Nie zmieniano API, rezerwacji, SMS, danych klientów ani sekretów.

**Uwaga:** główna gałąź repozytorium jest starsza od strony produkcyjnej. Nie uruchamiać automatycznej publikacji całego `main` ani podmiany PHP. Host ma przyrostowe modyfikacje z kontrolą SHA i kopiami zapasowymi.

## Weryfikacja

- Odczyt zwrotny pliku JS i zmienionego `public_metadata.php` potwierdził zapis.
- `run_site_tests(source)`: 26/26 testów PASS, parser PHP zaakceptował zmienione pliki.
- `run_site_tests(http)`: 2/2 PASS (robots oraz publiczne metadane OAuth). To nie jest test rzeczywistego interfejsu czatu.
- Strona główna i polityka prywatności odpowiadają publicznie. Klikanie dymka, zamykanie oraz wyświetlenie awatara po odpowiedzi wymagają potwierdzenia w normalnej przeglądarce.

Wdrożenie zmienia wyłącznie wygląd przycisku/awatarów. Zmiana wyglądu jest zapisana w gałęzi i PR #146; nie scalać jej ze starą produkcją przez automatyczne zastępowanie całego katalogu.

## Poprawka 10.11.2026 – stary przycisk na telefonie

Po zgłoszeniu, że na telefonie nadal widoczny jest stary przycisk, na hostingu zmieniono **wyłącznie** `public_metadata.php`: dołączenie `/rentmor-ai-mortii-visual.js?v=2` nie wymaga już dosłownego znacznika `/mortii.js?v=5`, tylko obecności `/mortii.js` w HTML i braku wizualnego dekoratora. Stary plik JS oraz obsługa rezerwacji pozostawione bez zmian.

Zmiana wdrożona przez aktywną zaufaną sesję RentMor Control (backup dostępny). Nowy SHA256 `public_metadata.php`: `e34ed59bfbc2835467c75c2eb2a3bb842b79a8318d56830b5c21b02d45f2aeae`. Zestaw testów źródłowych: 26 PASS / 0 FAIL. **Nie potwierdzono w prawdziwej mobilnej przeglądarce**, że strona główna przeszła przez `rentmor_public_metadata()`; należy przetestować odświeżenie bez cache/incognito i wyświetlenie przycisku. Nie synchronizować ślepo całego starego `main` na produkcję.


## Poprawka mobilna 10.11.2026 – kolejna weryfikacja po zgłoszeniu „na żadnej”

Zgłoszenie: nowa postać nie była widoczna ani na stronie głównej, ani na `/sprzet-budowlany/`. Odczyt kodu wykazał, że `mortii.js` sam tworzy przycisk z oryginalnym tekstem. Osobny dekorator w poprzedniej wersji nie czekał, jeśli czat nie był jeszcze w DOM – możliwy wyścig kolejności ładowania. **Nie mamy potwierdzenia, że to jedyna przyczyna; brak dostępnej przeglądarki do końcowego testu UI.**

Na hostingu w zatwierdzonej 24-godzinnej sesji wprowadzono:

- `rentmor-ai-mortii-visual.js`: `MutationObserver` oczekuje na późniejsze utworzenie `#mortii-chat` i dopiero wtedy obserwuje wiadomości; wersja CSS `?v=3`. Nowe SHA256: `b0cecd25b5b9b78ebd0a8ea141d383ff7b899df687bef9cbc2ee1fb380040646`. Zgodność składni JS sprawdzona.
- `public_metadata.php`: dekorator `?v=3` jest dołączany przy `</head>` także bez dosłownego wystąpienia `/mortii.js`, a stare wersje `?v=1/2` są zastępowane. SHA256: `0d5aef18802d8dbd11efb8829a18b33b6b255aa344cd3643b2815c479852ccc6`.
- `category.php`: podmiana wersji `?v=1` na `?v=3`, SHA256 `02c655066b1696b486d9501b9a2ebc5598f39a560dc4bec4f137e92ad835d85b`.
- `polityka-prywatnosci.php`: podmiana wersji `?v=1` na `?v=3`, SHA256 `e1ce19d6e1ec9dbe01e73e49794f4380e93c8587735d82bde996cccae213166d`.

Sprawdzono odczyt zwrotny i testy źródłowe 26/26 PASS; HTTP 2/2 PASS (testy robots i OAuth, **nie test czatu**). Formularzy rezerwacji, API, SMS i danych klientów nie dotykano. Próba edycji `mortii.js` przez RentMor Control została **odrzucona** jako plik poza dozwoloną listą – nie próbowano obchodzić ograniczenia. `index.php` również pozostaje niedostępny do odczytu/edycji przez to narzędzie.

**Pozostałe do zrobienia:** rzeczywisty test strony na urządzeniu, zwłaszcza home i `/sprzet-budowlany/`; jeśli problem utrzyma się, konieczne uprawnione otwarcie `mortii.js`/szablonu home do precyzyjnej poprawki, a nie kolejne zgadywanie. Nie scalać automatycznie z przestarzałym `main`.
