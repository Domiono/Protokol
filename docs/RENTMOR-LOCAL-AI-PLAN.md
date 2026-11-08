# RentMor AI — plan własnego, lokalnie uruchamianego asystenta (projekt)
Data decyzji: 08.11.2026. Status: PLAN, bez wdrożenia na produkcji.

## Cel i granice

Zbudować polskojęzycznego asystenta na stronie RentMor.pl, zasilanego samodzielnie hostowanym modelem językowym (bez płatnego API zewnętrznego dostawcy modelu). Asystent pomaga w doborze sprzętu, rozwiązywaniu prostych problemów, sprawdza ceny i zgłasza rezerwacje. Właściciel ma panel zatwierdzanej wiedzy oraz tygodniowe harmonogramy odbiorów i zwrotów.

**Własny model** oznacza własną instancję gotowych otwartych wag, NIE trenowanie modelu od podstaw. Weryfikować licencję i zgodność użytkowania komercyjnego przed publikacją.

## Ustalony podział infrastruktury

- AZ.pl Hosting Basic: nadal obsługuje RentMor.pl, PHP, publiczny czat, katalog, kalendarz, autoryzację, rezerwacje, prywatne dane i zaakceptowaną bazę wiedzy.
- Oddzielny komputer/mini-PC lub niezależny VPS: pracująca stale usługa inferencji modelu i jej bezpieczny gateway. Na start test na istniejącym laptopie Windows 11 z 8 GB RAM; test NIE oznacza gotowości do pracy 24/7.
- Model na próbę: Qwen3 1.7B (kwantyzowany, ok. 1,4 GB pliku w Ollama), następnie Qwen3 4B instruct (ok. 2,5 GB pliku) w razie wystarczającej pamięci i prędkości. Plik modelu to nie pełne zużycie RAM — przetestować rzeczywistą pamięć, kontekst i opóźnienia. Modele są kandydatami, nie deklaracją jakości polskiego języka.
- Silnik na próbę: Ollama (Windows) lub llama.cpp (Linux/CPU). Wymiana modelu ma nie wymagać przeróbek rezerwacji i panelu.
- Hosting Basic AZ.pl ma limit 256 MB na proces i 4 aktywne CGI: brak możliwości sensownej inferencji modelu na tym samym hostingu. Dla AZ.pl koniecznie zmierzyć dostępność wychodzących połączeń HTTPS do chronionego gateway.
- Jeśli niezależny komputer jest wyłączony albo nieosiągalny, czat pokazuje uczciwy komunikat i tradycyjny formularz zapytania/rezerwacji. Nie blokować pozostałej strony.

## Przepływ danych i bezpieczeństwo

Klient -> widget HTTPS na RentMor -> backend PHP (rate limit, limity długości i sesji) -> osobny gateway HTTPS z autoryzacją -> lokalny model -> odpowiedź do PHP -> klient.

- Żadnych bezpośrednich połączeń przeglądarki z silnikiem Ollama; port 11434 pozostaje lokalny, bez otwierania go publicznie. Gateway z tajnym kluczem, limitami, HTTPS, monitoringiem i wąskim allowlist, bez dowolnych funkcji systemowych.
- Klucz i konfiguracja wyłącznie poza webroot, bez dodawania sekretów do publicznego GitHuba. Oddzielne konta/role, żądania API tylko z serwera RentMor, a nie z przeglądarki; sprawdzić CSRF i ochronę przed spamem.
- Dane o klientach i rezerwacjach pozostają w prywatnym magazynie hostingu. Do modelu wysyłać tylko minimalny kontekst potrzebny do wypowiedzi, bez surowych pełnych list klientów, tokenów i haseł. Retencja logów zgodna z polityką prywatności i zgoda/obowiązek informacyjny w UI.
- Instrukcje podawane przez klienta i wczytane dokumenty traktować jako niezaufany materiał. Model nie może sam zmieniać danych, zatwierdzać wiedzy, wywoływać administracji ani ustalać innych cen.
- Przy naprawach urządzeń zwracać tylko bezpieczne czynności użytkownika; niebezpieczne naprawy elektryczne, ciśnieniowe i mechaniczne kierować do obsługi. Wpis w bazie ma oznaczenie źródła i zakresu: dla klienta / prywatne serwisowe.
- Utrzymać poprawną obsługę awarii, limity promptów, ograniczenie rozmiaru odpowiedzi i kolejkę przy wielu pytaniach.

## Funkcje i etapy realizacji

### Etap 0 — audyt integracji i pomiar realnych zasobów
1. Zidentyfikować w kodzie aktualny katalog produktów, panel admin, format danych, prywatny magazyn rezerwacji, synchronizację Android i mechanizmy konfliktów.
2. Na laptopie sprawdzić CPU/RAM/dysk, czy możliwa instalacja Ollama; bez ujawniania laptopa w internecie.
3. Zmierzyć odpowiedzi w języku polskim dla 30–50 realistycznych pytań RentMor, ich czas i poprawność; porównać 1.7B z 4B, ocenić zmyślanie.
4. Sprawdzić połączenie AZ.pl -> HTTPS gateway (na środowisku testowym), aktualną politykę hostingu i dostępny czas PHP.
**Warunek przejścia:** działający lokalny model i realne wyniki jakości/szybkości.

### Etap 1 — kontrolowana baza wiedzy z akceptacją
1. Prywatny magazyn wpisów: sprzęt, temat, objaw, rozwiązanie, ostrzeżenia, data, autor, źródło, status (propozycja / zaakceptowana / odrzucona), publiczne/prywatne.
2. Panel admin: dodawanie ręczne, edycja, zatwierdzanie propozycji AI, historia wersji i wycofanie wpisu; opcjonalnie upload instrukcji PDF w ograniczonym formacie po skanowaniu bezpieczeństwa.
3. Odpowiedzi o cenach, kaucjach i wyposażeniu zawsze ze źródeł aktualnych danych RentMor, nigdy z pamięci modelu. Przy braku danych komunikat o niepewności.
4. Do kontekstu modelu pobierać tylko trafne zatwierdzone artykuły; nie implementować automatycznego uczenia wag z rozmów klientów.
**Warunek przejścia:** wpis niezatwierdzony nigdy nie staje się faktem publicznym.

### Etap 2 — zarządzanie godzinami odbiorów i zwrotów
1. Stworzyć harmonogram bazowy wg dnia tygodnia, wyjątki na pojedyncze daty, nadpisanie zakresu tygodnia (np. 09–15.11.2026, 12:00–18:00), dzień wyłączony.
2. Osobne okna odbioru i zwrotu, krok slotów np. 30 minut, strefa Europe/Warsaw, walidacja przedziałów i zmian czasu DST; zmiany zapisuje wyłącznie admin z CSRF.
3. Istniejące rezerwacje zachować; przy zmianie godzin wyświetlić kolizje, nie przenosić spotkań automatycznie.
4. Rozszerzyć schemat przyszłych zapisów o pickup_at oraz return_at, zachować historyczne pole time i kompatybilność ze starszą aplikacją Android.
**Warunek przejścia:** klient nie wybierze godziny poza ustawieniami, a stara rezerwacja pozostanie czytelna.

### Etap 3 — publiczne zgłoszenia rezerwacji
1. Osobny publiczny endpoint: wybór sprzętu, dat, slotów, kontakt, zgoda na regulamin; zabezpieczenie limitami, walidacją, antyspamem i ochroną danych.
2. Sprawdzanie zajętości wykorzystuje serwerowe funkcje rental_inventory.php (zajętość obu dat granicznych). Dla zestawu żyrafa+odkurzacz obie sztuki muszą być dostępne. Uwzględnić serwis i starsze wpisy bez egzemplarza.
3. Przed zapisem ponownie sprawdzić pod blokadą pliku rezerwacji; wprowadzić rozdzielenie statusu propozycji/oczekującej rezerwacji od potwierdzonego najmu. Nie dopuszczać do overbookingu przez równoczesne zgłoszenia; rozważyć krótki czas wygaśnięcia blokady lub brak blokady z ponowną weryfikacją przy akceptacji.
4. Na start wyłącznie zgłoszenie oczekujące na potwierdzenie przez właściciela; odrębna akcja potwierdzenia pod autoryzacją. Nie reklamować zgłoszenia jako potwierdzonego. Cena i kaucja tylko z katalogu; przy niejasności wymagać kontaktu z właścicielem.
5. Powiadomienie właściciela i status dla klienta, bez ujawnienia wewnętrznych list rezerwacji.
**Warunek przejścia:** dwa równoczesne zgłoszenia tej samej ostatniej sztuki nigdy nie skutkują dwoma potwierdzeniami.

### Etap 4 — konwersacyjny asystent i narzędzia o ograniczonych uprawnieniach
1. Publiczny widget z prostą rozmową po polsku, dostępnością klawiaturową, bez pop-upów zasłaniających stronę.
2. Z modelu wyciągać intencję i parametry (sprzęt, daty) do weryfikacji przez backend. Model nie może wymyślać slotów, cen ani dostępności.
3. Odczyt dostępu tylko do publicznego katalogu, zatwierdzonej wiedzy i zanonimizowanego wyniku dostępności; utworzenie zgłoszenia wymaga jasnego potwierdzenia klienta.
4. Brak danych / błąd modelu -> formularz kontaktowy. Właściciel widzi pytania bez odpowiedzi i propozycje wiedzy do zatwierdzenia.
**Warunek przejścia:** testy prompt injection, błędów dat, nieistniejących sprzętów, niedostępnych sztuk i kontrola limitu obciążenia.

### Etap 5 — bezpieczna publikacja i rozwój
1. Rozwój wyłącznie w odrębnej gałęzi/PR; publiczny GitHub test-only, bez tokenów, danych klientów i połączeń wdrożeniowych do AZ.pl.
2. Testy składni, jednostkowe reguł dat/harmonogramu i konfliktów, integracyjne na syntetycznych bazach, testy WCAG i bezpieczeństwa. Nie wykonywać testowych rezerwacji w danych produkcyjnych.
3. Publikacja na AZ.pl wyłącznie przez kontrolowaną prywatną ścieżkę RentMor Control (wymaga rozbudowy do bezpiecznych wieloplikowych zmian z rollbackiem), po zgodzie właściciela; brak domyślnego auto-deploy.
4. Włączać funkcje flagami: najpierw panel admin, potem wewnętrzny test czatu, następnie zgłoszenia dla klientów. Mierzyć czasy odpowiedzi, niepowodzenia, jakość odpowiedzi i próby nadużyć.
5. Docelowo przenieść model na całodobowy mini-PC lub VPS, jeśli laptop okaże się za słaby lub zbyt niestabilny. Wstrzymać publiczny czat, dopóki dostępność usługi nie jest zapewniona.
6. Uzupełniać docs/RENTMOR-PROJECT-LOG.md po każdej zmianie o status, testy i dowód ewentualnej publikacji.

## Ważne ograniczenia i decyzje właściciela

- Użytkownik wybrał **własny, samodzielnie hostowany model**, a nie zewnętrzne API modelu.
- Użytkownik wybrał **naukę z zatwierdzaniem**: może sam dostarczać instrukcje i procedury napraw; AI może proponować wpisy po rozmowach, ale nie zatwierdza ich samodzielnie.
- Harmonogram na wybrany tydzień, np. 12:00–18:00, z rozróżnieniem wydania i zwrotu.
- Klient może złożyć zgłoszenie rezerwacji przez AI. Początkowo potwierdza je właściciel. Publiczne potwierdzanie całkowicie automatyczne jest poza zakresem MVP.
- Zmiana rozliczania części dnia i ponowne wydanie tej samej sztuki po zwrocie w tym samym dniu to osobny projekt, nie wprowadzać niejawnie. Dotychczasowa zajętość obu dat pozostaje.
- Własny model nie oznacza zerowych kosztów: energia, komputer lub VPS, utrzymanie i kopie bezpieczeństwa. Nie kupować nowego sprzętu bez lokalnego benchmarku.
- Ten dokument jest wyłącznie planem. Brak instalacji na laptopie, wdrożenia na AZ.pl, tokenów czy wykonanych testów modelu.
