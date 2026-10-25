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

## Wspólne magazyny: żyrafy i odkurzacze przemysłowe (1.7.5)

Zestaw to **dwa fizyczne egzemplarze**, a nie samodzielna trzecia pula. Nie twórz dodatkowych sztucznych egzemplarzy „zestaw” (to podwoiłoby stan).

1. W **Dostępność → Dodaj egzemplarz** wpisz każdą rzeczywistą żyrafę i każdy odkurzacz przemysłowy (np. Żyrafa 01/02/03, Odkurzacz 01/02/03). Ustaw odpowiednio grupę **Żyrafa** albo **Odkurzacz przemysłowy**. Zaktualizuj grupę już dodanych egzemplarzy, jeżeli trzeba. Odkurzacze piorące Puzzi nie są odkurzaczami przemysłowymi w tym magazynie.
2. Dodaj zwykłą rezerwację żyrafy i zaznacz **Zestaw z odkurzaczem przemysłowym** albo wybierz ofertę zawierającą „żyrafa + odkurzacz” lub „żyrafa zestaw”. Aplikacja przydzieli pierwsze wolne urządzenie z każdej puli. Wybór przydziału jest weryfikowany jeszcze raz na serwerze w tej samej blokadzie zapisu.
3. Wynajem samej żyrafy blokuje tylko żyrafę; wynajem osobnego odkurzacza przemysłowego blokuje tę samą pulę odkurzaczy co zestawy. Grupy mogą obejmować różne nazwy handlowe modelu (np. Bosch/Metabo), jeśli jest to rzeczywiście ten sam stan magazynowy.
4. **Starsze rezerwacje zestawów bez wskazanego odkurzacza wymagają jednorazowego uzupełnienia:** Aktywne najmy → Więcej opcji → Egzemplarze i warunki najmu. Do tego czasu system konserwatywnie nie potwierdza nowych kolidujących przydziałów odkurzaczy. Podobnie dla starych rezerwacji pojedynczych sztuk bez ich numerów.
5. Rezerwacji z grupy żyraf lub odkurzaczy przemysłowych bez przypisanego egzemplarza nie można już zaakceptować przez API. Pozostałe modele nadal zachowują dotychczasowe zachowanie bez zarejestrowanych egzemplarzy.
6. Data odbioru i data zwrotu nadal blokują obie doby w kalendarzu; zestaw jest zwalniany po zwrocie lub rozliczeniu obu urządzeń. Protokół wydania zapisuje etykiety obu sztuk.
7. Starszy panel admin.php WWW wprowadza rezerwacje bez wyboru fizycznych sztuk. Przy żyrafach, przemysłowych odkurzaczach i zestawach korzystaj z formularza rezerwacji w Androidzie; stare zapisy z WWW trzeba pojedynczo przypisać w aplikacji. Nie omijaj komunikatu o brakującym przydziale.

**Uwaga o starszych danych:** aplikacja nie wie, który konkretnie odkurzacz wydano w dawnym zestawie, dlatego nie przypisuje takiej sztuki na podstawie samej liczby rezerwacji.

## Szybkie akcje egzemplarza (1.7.6)

W widoku **Dostępność** każda karta ma przyciski **Edytuj**, **Awaria / wyłącz**, **Historia i serwis** oraz **Usuń egzemplarz** (administrator). Wyłączony egzemplarz jest oznaczony na czerwono, wszystkie dni widoczne jako niedostępne. Włączenie przywraca dostępność z uwzględnieniem dotychczasowych rezerwacji. Wyłączenie pokazuje ostrzeżenie o istniejących rezerwacjach i nie anuluje ich automatycznie. Historia napraw, przypisania oraz grupa żyrafa/odkurzacz pozostają zachowane.

**Usuń egzemplarz** wymaga potwierdzenia i działa wyłącznie, jeśli nie ma żadnej rezerwacji (także już rozliczonej), protokołu wydania ani historii serwisu odnoszącej się do tego egzemplarza. W przeciwnym wypadku aplikacja proponuje **Wyłącz z wynajmu**. Serwer kontroluje historię ponownie, nie ufa samemu telefonowi i sprawdza również odkurzacze pomocnicze w zestawach. Usunięcie może zostać zapisane offline i potwierdzone po synchronizacji, a oczekujące usunięcie nie znika z lokalnej bazy po odświeżeniu starszą migawką danych.
