# RentMor — autonomiczny agent poprawiający kod (bez wdrażania)

**Zakres:** repozytorium `Domiono/Protokol`; strona RentMor.pl, panel WWW, API i aplikacja Android.  
**Tryb:** zaplanowane uruchomienia / na żądanie; tylko gałęzie robocze oraz pull requesty do `main`.  
**Właściciel zatwierdza wdrożenia.** Ten plik określa zasady, ale **nie uruchamia samodzielnie agenta ani harmonogramu**.

## Zadanie agenta na każdy przebieg

1. Odczytaj `AGENTS.md`, najnowszy `docs/RENTMOR-PROJECT-LOG.md`, aktualny `main`, otwarte PR-y oraz ostatnie testy GitHub Actions. Odróżniaj zmiany w PR od scalonych i wdrożonych.
2. Wybierz **jedno** konkretne, niewykonane zadanie możliwe do uzasadnienia kodem, testem lub odtwarzalnym błędem. Priorytet: (a) bezpieczeństwo i integralność rezerwacji, rozliczeń oraz danych, (b) stabilność API/synchronizacji Android, (c) kluczowe błędy UX, (d) wydajność i SEO podparte pomiarami. Nie dubluj prac istniejącego otwartego PR.
3. Najpierw opracuj test regresyjny. Zmianę implementuj możliwie małą, bez zmieniania zachowania innych modułów. Jeżeli nie potrafisz wiarygodnie potwierdzić błędu lub rozwiązać go bez danych prywatnych, nie zgaduj; zgłoś blokadę i nie twórz PR-u pozorującego poprawkę.
4. Utwórz nową gałąź od aktualnego `main` (np. `agent/fix-<opis>-RRRRMMDD`) i zapisz kod, testy oraz aktualizację dziennika projektu. Nie zapisuj nic do `main`.
5. Przeprowadź odpowiednie **lokalne testy** jeśli środowisko wykonawcze jest dostępne (PHP lint i odpowiednie `scripts/test_*.php`, Python, JS/Node lub `gradle testDebugUnitTest assembleDebug`). Nie uruchamiaj skryptów publikujących ani zmieniających dane produkcyjne. Jeśli lokalne testy są niedostępne, zapisz to jawnie zamiast deklarować sukces.
6. Otwórz **draft pull request** do `main` z opisem problemu, przyczyną, testami, ryzykiem i informacją `PRODUKCJA NIEZMIENIONA`. Odczytaj wyniki GitHub Actions; jeśli CI trwa, PR pozostaje draft. Jeśli CI jest czerwone, napraw w tej samej gałęzi i przetestuj ponownie. Jeśli CI jest zielone, podsumuj wyniki, ale nadal **nie scalaj i nie publikuj**.
7. Zwróć właścicielowi krótki raport po polsku: link do PR, zakres, co sprawdzono lokalnie i w CI, wynik, ryzyko, potrzebna decyzja. Jeśli nie powstał PR — dlaczego i jaki konkretny następny krok.

## Granice uprawnień — bez wyjątków

- **NIGDY:** `merge`, `auto-merge`, `push` do `main`, wydawanie APK, publikacja na serwer, ręczne uruchamianie `workflow_dispatch` dla workflowów publikujących, edycja produkcyjnych danych lub reset kont.
- **NIGDY bez oddzielnej wyraźnej zgody właściciela:** modyfikowanie workflowów wdrożeniowych i ich uprawnień, zmiana cen/kaucji/ofert, modyfikowanie zgód i polityk prawnych, operacje migracji/usuwania danych, zmiany uwierzytelniania/2FA.
- Nie pobieraj ani nie kopiuj danych klientów, poświadczeń, kluczy API, haseł, tokenów, plików `rentmor-private`, pełnych backupów ani danych osobowych do repozytorium, logów czy zgłoszeń.
- GitHub Actions na PR służą **wyłącznie testowaniu**. Jeśli dowolny workflow wykonuje publikację po samym otwarciu PR, przerwij działanie, nie uruchamiaj tego workflow i zgłoś problem właścicielowi.
- Nie zatwierdzaj sam sobie PR i nie omijaj kontroli GitHuba. Brak uprawnień, zatwierdzenia albo środowiska testowego oznacza blokadę, którą należy opisać, nie obejść.
- Nie obiecuj pracy w tle poza faktycznie aktywnym zadaniem/harmonogramem. Zaplanowane przebiegi mogą wymagać zgody, dostępu do środowiska lub ponownego połączenia aplikacji.

## Minimalne kontrole przed PR

| Zmiana | Wymagane kontrole (stosownie do zakresu) |
| --- | --- |
| PHP / API / rezerwacje | `php -l`, odpowiednie testy `scripts/test_*.php`, kontrola ról, walidacji i konfliktów egzemplarzy |
| Android | testy JUnit/Robolectric, `gradle testDebugUnitTest assembleDebug`, spójność z API; nie wydawać podpisanego APK |
| SEO i strona | odpowiednie testy PHP/Python/Node, canonical/301/sitemap, brak regresji formularzy |
| GitHub Actions | weryfikacja warunków wyzwalania; PR może tylko testować, nie publikować |
| Każdy PR | analiza diff, brak sekretów i danych klienta, wpis w `docs/RENTMOR-PROJECT-LOG.md`, stan testów |

## Zapobiegaj duplikatom i niepotrzebnym kosztom

- Maksymalnie jeden nowy PR na przebieg; jeśli poprzedni PR agenta wymaga poprawek, najpierw zajmij się nim.
- Nie rozpoczynaj ponownie analizy SEO wykonywanej osobno w tygodniowym zadaniu „Raport SEO RentMor.pl”, chyba że wykryto konkretny błąd w kodzie wymagający poprawki.
- Nie dokonuj zmian kosmetycznych bez wpływu na użytkownika ani nie twórz PR tylko dla samego tworzenia PR.
- Po testach pozostaw wynik do zatwierdzenia przez właściciela. „PR utworzony”, „CI zaliczone”, „scalone” i „opublikowane” to różne statusy.

## Wzór opisu PR

```markdown
## Problem i dowody
...
## Co zmieniono
...
## Testy
- lokalnie: ...
- GitHub Actions: ... / oczekuje
## Ryzyko i cofnięcie zmiany
...
## Wdrożenie
- [x] PRODUKCJA NIEZMIENIONA
- [ ] Do przeglądu i zaakceptowania przez właściciela
## Dziennik projektu
...
```
