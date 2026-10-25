# RentMor 1.7 — obsługa najmu

Nowe pozycje panelu: **Dzisiaj** i **Dostępność**. Widok Dzisiaj pokazuje wydania, zwroty, opóźnienia, wiadomości i przeglądy. Lista aktywnych najmów prowadzi do przypisania egzemplarza, protokołu, przedłużenia i rozliczenia.

## Uruchomienie

1. Wdrożyć `server/protocol_sync.php` przed instalacją APK.
2. Zbudować APK przez dotychczasowy workflow, po przejściu testów.
3. Zaktualizować aplikację bez odinstalowywania. Baza SQLite przechodzi z wersji 6 do 7 bez usuwania danych.
4. Administrator dodaje rzeczywiste egzemplarze w Dostępność (np. Puzzi 01, Puzzi 02), a następnie przypisuje aktywne rezerwacje. Program nie zgaduje liczby urządzeń.
5. Zsynchronizować wszystkie urządzenia. Niewysłane operacje pozostają w kolejce; błąd konfliktu wymaga poprawienia rezerwacji i ponownej synchronizacji.

## Zasady

- Kalendarz obejmuje obie daty graniczne. Dwie rezerwacje tej samej sztuki nie mogą dzielić dnia zwrotu/odbioru. Dane nie obejmują godziny zwrotu, więc nie obiecują dostępności godzinowej.
- Rezerwacje bez przypisanej sztuki oznaczają termin do ręcznego sprawdzenia. Przypisanie nie usuwa tych pozostałych rezerwacji.
- Nowe rezerwacje automatycznie otrzymują pierwszy dostępny egzemplarz danego modelu, jeśli egzemplarze są skonfigurowane. Aplikacja pokazuje wstępny przydział, a serwer ponownie wybiera wolną sztukę podczas synchronizacji (z blokadą zapisu rezerwacji). Pełne terminy i starsze nakładające się rezerwacje bez numeru blokują automatyczny przydział.
- Jeśli nie dodano jeszcze fizycznych egzemplarzy danego modelu, rezerwacja może zostać zapisana bez numeru i wymaga późniejszego przypisania. Praca offline nie daje gwarancji przydziału do chwili udanej synchronizacji.
- Serwis blokuje nowe przydziały. Przeglądy są przypomnieniami wewnątrz aplikacji, według daty lub liczby wydań; nie wysyłają SMS-ów.
- Przedłużenie zachowuje ID rezerwacji i sprawdza konflikty lokalnie oraz na serwerze. Zmiana offline wymaga synchronizacji, zanim termin będzie potwierdzony na innych urządzeniach.
- Rozliczenie proponuje różnicę dat, minimum jedną dobę. Operator może skorygować liczbę dób zgodnie z ustaleniami godzinowymi. Dopłaty i rabaty są jawne; kaucja nie zwiększa przychodu.
- Zatwierdzenie rozliczenia potwierdza przyjęcie sprzętu i obsługę płatności. Aplikacja nie wykonuje płatności ani przelewów.
- Koszt dodanej naprawy trafia także do kosztów ogólnych. Nie należy wpisywać go ponownie.
- Protokół zwrotu zawiera checklistę akcesoriów i do 12 zdjęć. Podgląd aplikacji zestawia zdjęcia wydania i zwrotu; nie zakłada identycznego kadru.

## Weryfikacja

- Wszystkie źródła Java przeszły analizę składni i kompilację typów ECJ z biblioteką Android 14 oraz zależnościami AndroidX/Firebase. Zastępcze identyfikatory R służyły wyłącznie do sprawdzenia typów.
- Wykonano 10 kontroli logiki dat, kwot i konfliktów w JVM.
- PHP: poprawna składnia, testy czystych reguł oraz 11 kontroli API na odizolowanych danych (role, terminy, serwis, rozliczenie, blokada HTTP pliku egzemplarzy).
- Workflow `Test rental operations` uruchamia testy PHP, Robolectric i budowę debug APK.
- Pełny build Gradle/Robolectric, instalacja na urządzeniu i ocena interfejsu nie zostały jeszcze wykonane. Przed wydaniem konieczny jest test aparatu, podpisu, przejścia między ekranami i aktualizacji istniejącej bazy na urządzeniu.
