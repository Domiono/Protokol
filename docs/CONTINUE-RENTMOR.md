# RentMor — kontynuacja prac
Aktualizacja: 10.11.2026 (Europe/Warsaw). Ten plik i RENTMOR-PROJECT-LOG.md mają być zapisywane w repozytorium oraz identycznie na serwerze. Dokumentacja nie zastępuje sprawdzenia aktualnego kodu i wyników wdrożeń.

## Jak kontynuować
1. Przeczytaj ten plik, AGENTS.md i najnowsze wpisy docs/RENTMOR-PROJECT-LOG.md.
2. Porównaj SHA/wersje hostingu z planowaną zmianą. Kod produkcyjny Mortii powstał później niż obecny main: NIE nadpisuj całego hostingu plikami z main ani nie pushuj całej lokalnej historii.
3. Sprawdź repozytorium i prywatną kopię dokumentacji na serwerze: rentmor-control-private/progress/CONTINUE-RENTMOR.md oraz RENTMOR-PROJECT-LOG.md. Zapis w serwerowym katalogu0700, pliki0600, odczyt zwrotny SHA256. Niezgodność kopii wyjaśnić przed kontynuacją. W GitHub publicznie żadnych sekretów, kontaktów klientów ani pełnego adresu odbioru.
4. Po każdej zmianie zapisz: zakres, status przygotowane/testowane/opublikowane, commit/PR/run, walidację i konkretne następne kroki. Aktualizuj oba pliki w repo oraz na serwerze; nie deklaruj synchronizacji bez readback.
5. Korzystaj z istniejących autoryzacji właściciela dla ustalonego zakresu; zachowaj ochronę danych i kontrole wersji.

## Potwierdzone wdrożenia
- Publiczny Mortii i formularze są dostępne na RentMor. SMS wysyła ręcznie właściciel po zaakceptowaniu zgłoszenia; brak automatycznego SMS/e-mail. Zgłoszenia pending nie blokują sprzętu. Akceptacja ponownie sprawdza dostępność i bufor90min, doba24h; rozliczony/oddany najem zwalnia egzemplarz.
- Android1.7.26/code41 podpisany tym samym kluczem co poprzedni, instalator https://rentmor.pl/RentMor.apk. Aktualizacja bez odinstalowania aplikacji.
- Bosch GWS2200 230mm: po potwierdzeniu właściciela dodano dokładnie jeden fizyczny egzemplarz. Run42768160814 SUCCESS (SSH/PHP CLI, natywne blokady, backup/readback); termin11.11.2026 15:30→12.11.2026 15:30 sprawdzony jako dostępny w chwili testu. Nie jest to gwarancja bieżącej dostępności.
- Opcjonalna wiadomość klienta:2000znaków, prywatne zgłoszenie i widok właściciela WWW/Android; bez kontekstuAI. Run42768478237 SUCCESS,6plików SHA/backup/readback/HTTP.
- Czat: usunięto zaznaczone zdanie o OpenAI, pozostaje informacja oAI i niewpisywaniu danych osobowych. Dymek znika przy otwartym czacie, wraca przy Zamknij/Escape. Panel bottom22px. JSv6/CSSv3. Postać jest tworzona bezpośrednio w mortii.js, koło62×62px, WebP0644; starego dekoratora nie przywracać. Run42770178880 SUCCESS,5plików, kopia/readback/HTTP; działanie obejrzane w przeglądarce.
- Pakiety i szczegółowy dziennik w PR144: https://github.com/Domiono/Protokol/pull/144 . Tymczasowe workflow wdrożeniowe usunięto po użyciu. Nie uruchamiaj starych wdrożeń ponownie; manifesty SHA dotyczą poprzednich wersji.

## Dostęp techniczny
- WebFTP AZ.pl działa niestabilnie; obecnie potwierdzono działające SFTP oraz SSH/PHP CLI przez istniejące konto. Klucze hosta weryfikować przed hasłem. Istniejące sekrety GitHub są używane wyłącznie w runnerze, wartości nigdy nie czytać/drukować.
- GitHub pozostał publiczny, dotychczasowy skaner test-only nie został wyłączony. Jednorazowe wdrożenia są świadomymi wyjątkami właściciela; tymczasowe workflow usuwać po zakończeniu. Nie utożsamiać zielonego wdrożenia z całym CI: ogólny rentaloperations miał osobne błędy, przyczyna jeszcze nieprzeanalizowana.
- RentMor Control MCP i zaufana sesja24h wymagają osobnego aktualnego sprawdzenia; nie twierdzić, że pierwotny problem odnowienia OAuth został już rozwiązany.

## Ostatnia naprawa wyglądu Mortii
PR146: produkcyjny dekorator JS/CSS miał0600 i HTTP403; stary core tworzyłtekst230px. Usunięto ładowanie dekoratora, zintegrowano WebP/avatary w coreJS/CSS. Run42784823755 SUCCESS na4produkcyjnychstronach wChromiumAndroid390×844:62×62/radius50%, loadedimages, nowequeryJS6/CSS3, brakpageerrors, close/open/hidePASS. Artefakt13748920706 zawiera zrzuty iJSON. 17DOMPASS, bookingpodfunkcja zachowana bajtowo; API/klienci/SMS bez zmian. Pełnyopis: deployment/mortii-visual/README.md. Nie przywracać poprzednich nakładek ani zaufanychplików0600 jako publicznegoJS.

## Dalej
1. Właściciel ponawia swoje zgłoszenie i testuje na telefonie powiadomienie→akceptację→edytorSMS→ręczne wysłanie. Nie twórz rezerwacji na prawdziwe kontakty ani nie wysyłaj SMS bez konkretnego zlecenia.
2. Inne oferty mogą nie mieć fizycznych egzemplarzy; ustal rzeczywistą liczbę przed dodaniem, nie zakładaj po1.
3. Sprawdź końcowe CI/ogólny rentaloperations i aktualne możliwości MCP. Nie myl ich z działającym hostingiem.
4. Przy nowym poleceniu właściciela kontynuuj rozwój, zapisując stan w obu miejscach.
