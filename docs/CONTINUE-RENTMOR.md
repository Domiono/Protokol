# RentMor — kontynuacja prac
Aktualizacja: 10.11.2026 (Europe/Warsaw). Ten plik i RENTMOR-PROJECT-LOG.md mają być zapisywane w repozytorium oraz identycznie na serwerze. Dokumentacja nie zastępuje sprawdzenia aktualnego kodu i wyników wdrożeń.

## 10.11.2026 — Search Console w aplikacji Android, w przygotowaniu

Na polecenie właściciela dodano natywny ekran Google / SEO, widoczny tylko dla administratora w panelu głównym i w sekcji Strona. Korzysta z tych samych raportów i połączeniaGoogle co WWW: wybór7/28/90dni i sprzętu, kliknięcia/wyświetlenia/CTR/średnia pozycja z porównaniem, do100fraz. Odśwież widok odczytuje zapisany raport, Pobierz dane zGoogle uruchamia jawnyPOST bez automatycznego ponawiania. KonfiguracjaOAuth otwierana w przeglądarce; tokenyGoogle pozostają wyłącznie na serwerze.

API view=search_console wymaga aktywnego urządzenia z roląadmin, a POST nagłówka aplikacji. Odpowiedź zawiera wyłącznie podsumowania i stan połączenia, bez sekretów i danych klientów. Kontrola roli przed odczytem, ograniczenie fraz, rozróżnienie braku raportu od zerowego ruchu, stare dane zachowane po błędzie. Aplikacja odrzuca odpowiedź po wyjściu z ekranu/zmianie roli. Nowa wersja1.7.30/code45, z zachowaniemAI — Wiedza i wszystkich funkcji1.7.29.

Status: kod przygotowany, PR/testy/podpisanie/publikacjaAPK i API do potwierdzenia. Dotychczas wydanyAPK pozostaje1.7.29/code44. Obie kopie dokumentów mają zostać zsynchronizowane po walidacji. Rzeczywiste kontoGoogle nadal wymaga jednorazowej konfiguracji wWWW; nie wysyłano zapytańGoogle ani nie odczytywano danych klientów.

## 10.11.2026 — aktualny zakres SEO i panel Search Console

Potwierdzono na publicznej głównej, kategorii budowlanej, szlifierce250 i agregacieWT20i: treści lokalne, opisy zastosowań/ograniczeń, FAQ, poradniki i kontekstowe linki oraz automatyczne powiązane urządzenia już działają. W kodzie admin.php istnieje edycja seo_title/seo_description. Nie proponować tych funkcji ponownie jako nowych. Odczytano main7ca178c7, najnowsze PR148–150 oraz ten dziennik. Połączenie Windsor Search Console sc-domain:rentmor.pl dostępne wChatGPT; nie daje ono hostingowi automatycznego dostępu Google.

Na polecenie właściciela przygotowano zakładkę WWW GOOGLE / SEO: sprzęt/frazy, kliknięcia, wyświetlenia, CTR, średnia pozycja i porównanie7/28/90dni; stare i noweURL łączone poID katalogu. Osobne sumy stron i zapytania, jawne ograniczenia danych, OAuthreadonly/PKCE/state/CSRF, prywatne tokeny i raporty0700/0600. Pobieranie ręczne zGoogle i zapisany raport; brak harmonogramu. Szczegóły: docs/SEARCH-CONSOLE.md. Android bez zmian,1.7.29/code44 pozostaje bieżącym potwierdzonym wydaniem.

Status: **OPUBLIKOWANE**, PR151. Test42905989222 SUCCESS:36kontroliPHP,3testypatchera,4testyHTTP uprawnień/CSRF/bezpośrednichhelperów i4testytransakcji/rollbacku na fikcyjnych danych. Publikacja przez przypięty kluczSSH iPHPCLI: bieżący admin.php miał dokładnySHA2564cbf795e663e73fe893a209138366cca4662ab8e982448242b86a2f4b2d03783; zachowano nowszy kod i dodano3hooki. NowySHA256admin=7e73d144c818e8bd5d49d5f4231b4a18307d5e9f9ee28cec8eff2dee2d231ef8. Nowehelpery0644, prywatna kopia źródeł i odczytzwrotny. Publiczny loginHTTP200, bezpośredniehelpery404, niezalogowanycallback403. Bez logowania do prawdziwych danych, bez wywołańGoogleAI/rezerwacji/SMS. Pierwsza próba42905143918 zatrzymana błędemSFTP; odczytSSH42905446836 potwierdził brak zmian i zgodność prywatnych dokumentów zmain. Ostateczna publikacja42905989222 poprawnie zapisała oba dokumenty0700/0600 i potwierdziła identycznośćSHA256 zrepozytorium. Końcowa aktualizacja niniejszego wpisu podlega ponownej synchronizacji i sprawdzeniuTWO_PROGRESS_DOCUMENTS_SHA256_AND_0600_VERIFIED; sam zapisGit nie potwierdza synchronizacji.

**Pozostaje konfiguracja właściciela:** wpaneluWWW → GOOGLE / SEO utworzyć/zapisać klientaGoogleOAuth i połączyć konto z dostępem do sc-domain:rentmor.pl, następnie Pobierz dane. KontoGoogle hostingu nie zostało jeszcze połączone; brak rzeczywistych raportów nie jest zerowym ruchem. PołączenieWindsor wChatGPT pozostaje osobne. Android1.7.29/code44 bez zmian, brak harmonogramu automatycznego odświeżania.


## 10.11.2026 — wiedza osobno dla sprzętu, PR150, OPUBLIKOWANE
Nowy wybór „Sprzęt: Zasady ogólne” wAI — Wiedza: osobne notatki dla produktów z katalogu, oddzielnie publiczne/prywatne. Dotychczasowyknowledge.json pozostaje bez migracji wZasadachogólnych. Osobne rewizje/blokady/kopie0600/limit12000bajtów dla każdejnotatki, katalogi0700; nazwy sprzętu są hashowane, nigdy ścieżkami. Wpisy po zmianie/usunięciu nazwy katalogowej pozostają na liście, bez automatycznego przypisania do nowejnazwy. AI dobiera do kontekstu do dwóch notatek dopasowanych sprzętów, prywatne notatki nie trafiają do publicznegoMortii. Ochrona niezapisanego tekstu przy zmianie sprzętu. Android1.7.29/code44. Publikacja42834469996 SUCCESS:46kontroli PHPwiedzy,4testyHTTPpobierania,15testówAndroid/UI/HTTP i podpisanyassembleRelease. Zgodny podpis z1.7.28, preflight6plików, prywatne kopie, pełnyodczytzwrotny, publiczny download no-store/filename/SHA, niezalogowaneAPI401. APK1.7.29/code44: https://rentmor.pl/download-app.php?release=44 , SHA256146b0e810235bffed327d7c613be0d4814a16acb6474bad366708fa44452316d. Istniejącytekst nie jest automatycznie rozdzielany ani odczytywany do repo. Fizyczna instalacja na telefonie do wykonania. Bez zmian klientów/rezerwacji, bez płatnegoAI.

## 10.11.2026 — pobieranie aktualnej aplikacji, PR149, OPUBLIKOWANE
Telefon właściciela wskazywał1.7.25 mimo próby aktualizacji. Diagnostyka42829878778 potwierdziła, że trzy warianty publicznegoURL zawierały prawidłowy1.7.27/code42 zAI wDEX; cache lub wybór poprzednio pobranego pliku pozostaje hipotezą, bez dowodu błędu menu1.7.27.
Poprawka1.7.28/code43 pokazuje zainstalowaną wersję przy „Pobierz aplikację” i w dialogu. Każde pobieranie otwiera download-app.php?request=czas. Endpoint odczytuje bieżącyAPK i publiczne metadane wydania; sprawdza SHA/rozmiar, blokuje symlinki, niezgodność daje503. No-store/no-cache, nazwa RentMor-1.7.28.apk zamiast powtarzalnego RentMor.apk. Stary1.7.25 wymaga jednorazowej ręcznej instalacji z nowegolinku https://rentmor.pl/download-app.php?release=43 . Nie odinstalowywać obecnejaplikacji.
Run42830958339 SUCCESS:4testyHTTPendpointu na fikcyjnejpaczce,31kontroliPHPwiedzy,11testówAndroid/UI/HTTP, podpisanyassembleRelease. Produkcja: zgodny podpis z1.7.27, ścisły preflightAPK_SHA, prywatna kopia, odczytzwrotny, oba publiczneURL zgodne bajtowo, no-store i filenamePASS. APK_SHA256=2c292bb74425dde8822ad7cdac8fe22bb60a0539fa56449a87ce1c485085d386. ZachowaneAI/uprawnienia zPR148. Bez zmian rezerwacji/klientów/płatnegoAI. Fizyczna instalacja1.7.28 na telefonie do wykonania.
Dokumenty synchronizowane prywatnie z dokładnymSHA i odczytemzwrotnym. Tymczasoweworkflow zostaną usunięte, kontrolatest-only zachowana; testyendpointu i świeżegoURL pozostają wCI. FinalnyCI/merge do sprawdzenia wPR149.

## Potwierdzenie publikacji AI — Wiedza, 10.11.2026
PR148, walidacja42825160844 SUCCESS (31PHP +10Android), wdrożenie42825501988 SUCCESS. API i publiczny Mortii opublikowane z dokładnym preflightSHA, prywatnymi kopiami i pełnym readback. APK1.7.27/code42 opublikowany na https://rentmor.pl/RentMor.apk, zgodny podpis z poprzednią1.7.26/code41; SHA256584bb66dade23ecab75a2d77ccac496226c36fd624c6c1f199f81277908fd784. Publiczny download zgodny bajtowo; niezalogowane GET obu rodzajów wiedzy zwracają401bez treści. Testy uprawnień pracownika/konfliktów/izolacji wykonano na fikcyjnych danych; nie aktywowano testowego urządzenia produkcyjnego i nie wysyłano płatnegoAI. Fizyczna instalacja na telefonie właściciela pozostaje do wykonania. W aplikacji panelgłówny→AI — Wiedza→Zapisz i udostępnij Mortii. Dotychczasowa prywatna wiedza WWW nie została skopiowana do publicznej.

Końcowe dokumenty są synchronizowane przez osobny dokładnySHA workflow; potwierdzenie wymaga PROGRESS_SYNC_SHA256_OK dla obuplików. Docelowo tymczasowe workflow źródeł,walidacji,publikacji i synchronizacji usunięte z gałęzi, globalna polityka test-only zachowana. Nie uruchamiać ponownie starego manifestu wdrożenia; aktualna produkcja ma już noweSHA.


## Jak kontynuować
1. Przeczytaj ten plik, AGENTS.md i najnowsze wpisy docs/RENTMOR-PROJECT-LOG.md.
2. Porównaj SHA/wersje hostingu z planowaną zmianą. Kod produkcyjny Mortii powstał później niż obecny main: NIE nadpisuj całego hostingu plikami z main ani nie pushuj całej lokalnej historii.
3. Sprawdź repozytorium i prywatną kopię dokumentacji na serwerze: rentmor-control-private/progress/CONTINUE-RENTMOR.md oraz RENTMOR-PROJECT-LOG.md. Zapis w serwerowym katalogu0700, pliki0600, odczyt zwrotny SHA256. Niezgodność kopii wyjaśnić przed kontynuacją. W GitHub publicznie żadnych sekretów, kontaktów klientów ani pełnego adresu odbioru.
4. Po każdej zmianie zapisz: zakres, status przygotowane/testowane/opublikowane, commit/PR/run, walidację i konkretne następne kroki. Aktualizuj oba pliki w repo oraz na serwerze; nie deklaruj synchronizacji bez readback.
5. Korzystaj z istniejących autoryzacji właściciela dla ustalonego zakresu; zachowaj ochronę danych i kontrole wersji.

## Potwierdzone wdrożenia
- Publiczny Mortii i formularze są dostępne na RentMor. SMS wysyła ręcznie właściciel po zaakceptowaniu zgłoszenia; brak automatycznego SMS/e-mail. Zgłoszenia pending nie blokują sprzętu. Akceptacja ponownie sprawdza dostępność i bufor90min, doba24h; rozliczony/oddany najem zwalnia egzemplarz.
- Android1.7.29/code44 podpisany tym samym kluczem co poprzedni, instalator https://rentmor.pl/RentMor.apk. Aktualizacja bez odinstalowania aplikacji.
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

## W trakcie — AI Wiedza w Androidzie, 10.11.2026
Gałąź codex/admin-ai-knowledge-20261110, PR148. Przygotowano natywny edytor publicznej/prywatnej wiedzy z uprawnieniamiadmin i API view=ai_knowledge. Publiczny Mortii korzysta wyłącznie z osobnej wiedzy świadomie opublikowanej przez admina. Istniejący knowledge.json pozostaje prywatny. Android1.7.27/code42 na bazie wydanej1.7.26. Testy, podpisanie, publikacja i synchronizacja tego wpisu na serwer: do potwierdzenia. Nie nadpisywać hostingu zmain. Właściciel wyraził zgodę na jednorazowy GitHub/SFTP dla tego zadania.

Walidacja PR148: run42825160844 SUCCESS — 31kontroli PHP,10testów Robolectric/HTTP/UI, assembleRelease i podpisanie APK1.7.27/code42. Publikacja produkcji i zgodność podpisu z obecnym APK do potwierdzenia w osobnym workflow. Globalny test-only blokuje jednorazowe workflow zgodnie z regułą; nie został wyłączony.
