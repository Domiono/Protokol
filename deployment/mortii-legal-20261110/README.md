# RentMor.pl — dokumenty Mortii i formularza rezerwacji (10.11.2026)

## Status na hostingu

Zaktualizowano `polityka-prywatnosci.php` bez naruszania działającej logiki czatu i rezerwacji. Dodano odrębny regulamin `/rentmor-ai-regulamin-mortii.html` i odnośnik do niego z polityki prywatności. Uzupełnienie reguluje korzystanie z AI i wysyłanie zgłoszenia chęci wynajmu, **nie** zastępuje obowiązujących zasad wynajmu `/zasady.php` ani umowy najmu zawieranej przy wydaniu sprzętu.

Pliki w tym katalogu są **kopią produkcyjnych plików**, zachowaną do przeglądu i ewentualnych zmian przez Work. Nie wdrażać automatycznie całego `main` i nie podmieniać plików strony starszym kodem z repozytorium.

- `polityka-prywatnosci.php`: SHA256 `8181f5905bd8445817b699b385cd73254c2c26e5763d91440a3a24f861642b35`.
- `rentmor-ai-regulamin-mortii.html`: SHA256 `63a2bb682596c6e0cfc88caf57e5948a557e579276d7a56ca7fa80d7ae673050`.
- Testy RentMor Control: `source` 26 PASS / 0 FAIL, `http` 2 PASS / 0 FAIL (testuje tylko publiczne metadane/robots, **nie** otwieranie stron czy rezerwacje w przeglądarce).
- Zmiany wykonano przez aktywną zatwierdzoną sesję RentMor Control z backupem pliku polityki. Rezerwacje, dane klientów, API, SMS i główny skrypt `mortii.js` pozostawiono bez zmian.

## Faktyczne działanie formularza ustalone na podstawie produkcyjnego mortii.js

Klient podaje imię i nazwisko, telefon, e-mail, datę/godzinę odbioru, liczbę dób i opcjonalną wiadomość. Zgłoszenie dostaje numer, po czym oczekuje na potwierdzenie przez obsługę. Zgłoszenie nie jest automatyczną rezerwacją ani umową najmu. Pola formularza trafiają do akcji `request` backendu; nie są przekazywane do modelu AI w ramach rozmowy.

## Nadal do sprawdzenia

1. Wtyczka RentMor Control odrzuca odczyt i zapis `zasady.php`. **Głównych zasad wynajmu nie zmieniano.** Jeśli dokument powinien zawierać to samo, Work musi odczytać aktualną wersję ze strony/hostingu i dopisać stosowną sekcję bez nadpisywania innych zasad.
2. Formularz obecnie wymaga wyłącznie potwierdzenia zapoznania z polityką prywatności. Należy udostępnić bezpośredni link do nowego regulaminu obok formularza i ocenić, czy potrzebne jest odrębne potwierdzenie akceptacji; narzędzie blokuje zmianę `mortii.js`.
3. Potwierdzić retencję historii czatu, rzeczywiste powiadomienia i procedurę usuwania zgłoszeń po 12 miesiącach oraz konfigurację OpenAI API (w tym transfer danych). Powyższe dokumenty nie zastępują audytu RODO ani konsultacji prawnej.
4. Przetestować nowy adres HTML i odnośnik z polityki w rzeczywistej przeglądarce.

Źródła prawne do przeglądu: UODO (art. 13 RODO — obowiązek informacyjny i czas przechowywania), UOKiK (informowanie o odpłatnych umowach), dokumentacja OpenAI API Data Controls.

## Poprawka po zgłoszeniu HTTP 403 (10.11.2026)

Klient otwierając opublikowany `/rentmor-ai-regulamin-mortii.html` widział „CLIENT ERROR 403 FORBIDDEN”, mimo że plik fizycznie istnieje w katalogu WWW (SHA `63a2bb682596c6e0cfc88caf57e5948a557e579276d7a56ca7fa80d7ae673050`). Aktualnych przyczyn konfiguracji hostingu nie dało się zweryfikować z poziomu narzędzia; **nie należy linkować zablokowanego URL jako działającego**.

Działające obejście na serwerze: w `polityka-prywatnosci.php` dodano osobną pełną sekcję „Regulamin korzystania z czatu Mortii i formularza zgłoszenia wynajmu” o identyfikatorze `regulamin-mortii`. Link w samej polityce, stopce i spisie treści prowadzi teraz do `/polityka-prywatnosci.php#regulamin-mortii` — publicznego dokumentu PHP zamiast statycznego HTML. Nowy SHA256 wersji produkcyjnej polityki: `4915876e1da5ed2e55a283280c356a2b66ac2b80b539fc88f344bc14f81eaadc`. Odczyt zwrotny i PHP źródłowe 26/26 PASS; testy HTTP 2/2 PASS dotyczą robots i metadanych OAuth, **nie otwarcia regulaminu na urządzeniu**. Potrzebne potwierdzenie użytkownika, że zaktualizowany link na telefonie otwiera dokument. Plik `.html` fizycznie pozostawiono, ale serwer zwraca na niego 403 i nie należy go już używać. Główne `zasady.php` wciąż niezmieniane.
