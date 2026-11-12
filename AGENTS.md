# Zasady pracy nad RentMor — obowiązujące dla kolejnych zmian

Repozytorium: **Domiono/Protokol**. Projekt obejmuje publiczną stronę RentMor.pl, panel WWW i natywną aplikację Android.

## Dostęp do projektu

Użytkownik pracuje na repozytorium `Domiono/Protokol`. Przy kontynuacji najpierw sprawdź dostępne połączenie GitHub i aktualny kod; nie proś o ponowne przesłanie ZIP-a bez sprawdzenia istniejącego dostępu. Uprawnienia sprawdzaj na bieżąco.

## Najpierw sprawdź historię

1. **Przed audytem, rekomendacją lub zmianą przeczytaj [docs/RENTMOR-PROJECT-LOG.md](docs/RENTMOR-PROJECT-LOG.md).** To główny rejestr wdrożonych funkcji, częściowych realizacji, znanych luk i chronologii prac.
2. Przeczytaj stosowną dokumentację w `docs/`, bieżący kod na `main` oraz najnowsze scalone pull requesty. Karta projektu może być nieaktualna po nowszym PR — kod i potwierdzone wyniki workflow mają pierwszeństwo.
3. **Nie proponuj po raz kolejny wdrożonych funkcji.** Jeśli mechanizm działa tylko częściowo, opisz precyzyjnie, co już jest, a co pozostaje do dokończenia. Nie utożsamiaj pomysłu ze znalezionym błędem.
4. Odróżniaj cztery sytuacje: zaproponowano, wdrożono w kodzie, przeszło testy, opublikowano na produkcji. Bez potwierdzonego wdrożenia nie pisz, że zmiana jest dostępna użytkownikom.

## Po każdej wykonanej zmianie

1. Aktualizuj **w tym samym PR** `docs/RENTMOR-PROJECT-LOG.md`: odpowiedni status funkcji, otwarte luki, datowany wpis dziennika, źródłowy PR/commit, wersję aplikacji i opis testów.
2. Jeżeli numer PR lub wynik publikacji nie są jeszcze znane, zostaw jawne `do potwierdzenia`; po merge zweryfikuj run i uzupełnij wpis osobną małą poprawką dokumentacji, jeżeli trzeba.
3. Przy aktualizacji APK podaj `versionName`/`versionCode`, wynik testów i sprawdzony link do podpisanego instalatora. Przy zmianach PHP/SEO podaj workflow wdrożenia i weryfikację publicznego działania (jeśli była możliwa).
4. Aktualizuj istniejący problem R-xx zamiast otwierać nowy duplikat. Jeśli po zmianie nadal jest luka, zostaw ją jako `CZĘŚCIOWO` i wyjaśnij dlaczego.
5. Nie zapisuj danych klientów, haseł, tokenów, kluczy, cookies, danych identyfikacyjnych ani zawartości prywatnych backupów w dokumentach lub commitach.

## Kluczowe katalogi

- `server/` — witryna, admin, API, synchronizacja i dane publiczne (nie commitować prywatnych danych).
- `app/` — natywna aplikacja Android, testy i numer wersji.
- `.github/workflows/` — testy, wydania APK, wdrożenia, monitoring, kopie.
- `docs/` — szczegółowe raporty i centralny dziennik projektu.

**Cel:** utrzymać ciągłość pracy pomiędzy czatami i uniknąć powtarzania już wykonanych zmian. Instrukcja jest dla asystentów i programistów; nie uruchamia samoczynnych zadań ani zmian na serwerze.

## Trwałe zapisy postępów — decyzja właściciela 10.11.2026

Po każdej zmianie aktualizuj docs/CONTINUE-RENTMOR.md i docs/RENTMOR-PROJECT-LOG.md w repozytorium oraz identyczne kopie na serwerze w rentmor-control-private/progress/. Kopie serwera muszą pozostać prywatne (katalog0700, pliki0600); sprawdź odczyt zwrotny/SHA256. W zapisie podaj stan wdrożenia, testy i następne kroki. Nie deklaruj zapisu na serwerze, jeżeli synchronizacja się nie udała. Repo publiczne: bez danych klientów, sekretów i pełnego adresu odbioru. Przy kontynuacji najpierw odczytaj te dwa pliki. Nie pushuj prywatnej lokalnej historii ani całego hostingu do publicznego repozytorium.

## Wdrożenia przez GitHub — decyzja właściciela 11.11.2026

GitHub służy do testów ORAZ wdrażania zmian na hosting. Właściciel wycofał ograniczenie „GitHub tylko do testów”; nie wymagać ponownego wyjątku dla zwykłych wdrożeń objętych zleceniem. RentMor Control pozostaje alternatywną drogą. Wdrożenie poprzedzają odpowiednie testy, kontrola bieżących SHA, prywatna kopia i przygotowane cofnięcie; po wdrożeniu sprawdź odczyt zwrotny i publiczne działanie. Dane klientów i sekrety pozostają poza publicznym repo/logami, poświadczenia tylko w GitHub Secrets. Workflow wdrożenia nie może uruchamiać kodu z niezweryfikowanych forków ani pull_request_target. Ogranicz zakres plików i zachowuj nowsze zmiany hostingu; samo scalanie PR nie potwierdza publikacji. Dawne wpisy dokumentacji o test-only są historyczne i nie stanowią aktualnego zakazu.

## Tryb autonomicznego agenta — tylko kod, testy i draft PR

Przy zadaniach agenta obowiązuje [docs/RENTMOR-AI-AGENT.md](docs/RENTMOR-AI-AGENT.md). Agent może analizować kod, uruchamiać bezpieczne testy, commitować **wyłącznie na osobnej gałęzi** i tworzyć **draft PR**. Nie wolno scalać, uruchamiać auto-merge, wydawać APK, wdrażać przez GitHub ani RentMor Control, zmieniać danych produkcyjnych lub obchodzić zatwierdzeń. Na mocy decyzji właściciela z 11.11.2026 GitHub może służyć także do wdrożeń wykonywanych przez inne, odrębnie uprawnione procesy. **Ten agent ma jednak wyłącznie uprawnienie do testów i draft PR, bez publikacji.** Sprawdzaj bieżące workflowy i nigdy nie uznawaj ich zielonego statusu za dowód wdrożenia. Jeśli brak środowiska do testów, zgłoś to jawnie. Wymóg synchronizacji dokumentów na prywatny serwer z sekcji powyżej dotyczy wyłącznie oddzielnie zatwierdzonych operacji wdrożeniowych; agent w trybie bez wdrażania nie może go wykonywać.
