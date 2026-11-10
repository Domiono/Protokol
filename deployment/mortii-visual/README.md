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
