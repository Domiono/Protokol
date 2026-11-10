# Search Console w panelu RentMor

Status: panel WWW opublikowany 10.11.2026, PR151, testy i publikacja42905989222 SUCCESS (36PHP +3patch +4HTTP +4transakcja). Konto Google nie zostało jeszcze połączone z hostingiem. Dostęp Windsor w ChatGPT jest oddzielnym połączeniem i nie jest kopiowany na serwer.

## Funkcje

- Panel WWW → GOOGLE / SEO, dostęp po dotychczasowym haśle i MFA administratora.
- Okresy 7, 28 i 90 dni, z poprzednim równym okresem. Daty zgodne z Pacific Time w GSC, ostatnie trzy dni pomijane.
- Kliknięcia, wyświetlenia, CTR i średnia pozycja sprzętu oraz różnice. Średnia pozycja ważona wyświetleniami; CTR liczony z sum.
- Stare `index.php?id=…` i bieżące adresy slugowe przypisywane do ID katalogowego. Niejednoznaczne, zmienione i nieznane adresy trafiają do pozostałych stron; brak zgadywania po fragmentach nazwy.
- Do 100 ujawnionych fraz wybranego sprzętu. Sumy stron pobierane osobno od fraz: ukryte zapytania nie zaniżają prezentowanego wyniku sprzętu.
- Dane `web` i `final`; maksymalnie cztery strony po 25000 wierszy na zapytanie. Limit jest jawnie oznaczany jako raport częściowy. GSC samo również nie gwarantuje kompletności.
- Pobranie na żądanie administratora; zmiana widoku korzysta z zapisanego raportu i nie wysyła żądania Google. Nie skonfigurowano automatycznego harmonogramu.
- Błąd Google zachowuje poprzedni raport i datę pobrania; stary zakres jest oznaczony. Puste dane nie są dowodem braku indeksacji.

## Pierwsze połączenie

1. W swoim projekcie Google Cloud włącz Google Search Console API.
2. Skonfiguruj ekran zgody OAuth. Jeżeli aplikacja jest w trybie testowym, dodaj własne konto jako użytkownika testowego; tokeny testowej aplikacji mogą wygasać i wymagać ponownego połączenia.
3. Utwórz klienta OAuth typu aplikacja internetowa. Jedyny wymagany adres przekierowania: `https://rentmor.pl/admin.php?tab=google&gsc_callback=1`.
4. W panelu WWW → GOOGLE / SEO → Pierwsze połączenie wpisz identyfikator i sekret klienta. Wpisuj je wyłącznie w zalogowanym panelu, nigdy w publicznym repozytorium ani na czacie.
5. Kliknij Połącz konto Google → Przejdź do Google i wybierz konto z dostępem do `sc-domain:rentmor.pl`. Przyznawany zakres to wyłącznie `webmasters.readonly`.
6. Po powrocie wybierz okres i Pobierz dane. Odmowa 403 wymaga sprawdzenia API oraz uprawnień do właściwości.

## Ochrona danych i wdrożenie

Konfiguracja klienta, tokeny i raporty znajdują się w `rentmor-private/search-console/` poza `public_html`, katalog0700/pliki0600. Operacje pod blokadą, pliki podmieniane atomowo, symlinki odrzucane. OAuth ma losowy, jednorazowy state ważny 10min i PKCE S256, callback jest stały. Formularze wymagają sesji i CSRF. Tokeny/sekrety i surowe błędy Google nie są wyświetlane ani logowane. Wylogowany dostęp do helperów zwraca404.

Nowe helpery publikować przed trzema hookami w aktualnym produkcyjnym admin.php. `scripts/patch_search_console.py` akceptuje wyłącznie znane, pojedyncze kotwice i zachowuje pozostałe bajty; nie nadpisywać całego produkcyjnego panelu wersją z main. Wymagane: kopia prywatna, preflight źródła, lint, readback i sprawdzenie niezalogowanej strony. Wraz z każdą zmianą aktualizować `CONTINUE-RENTMOR.md`, `RENTMOR-PROJECT-LOG.md` oraz ich identyczne prywatne kopie na hostingu; status synchronizacji potwierdzać odczytem SHA256.

Android1.7.30/code45 otrzymał natywny ekran Google / SEO na panelu głównym i w sekcji Strona (wyłącznie administrator). Korzysta ze wspólnych raportów, katalogu i połączeniaGoogle; konfiguracjaOAuth otwierana w zewnętrznej przeglądarce. API wymaga aktywnego urządzenia z roląadmin i nagłówkaPOST; tokenyGoogle nie trafiają do telefonu. Publikacja42908629092 SUCCESS, pełny opis wPR152 i dzienniku projektu. Instalator: https://rentmor.pl/download-app.php?release=45 .

Źródła: https://developers.google.com/webmaster-tools/v1/how-tos/authorizing oraz https://developers.google.com/webmaster-tools/v1/searchanalytics/query .
