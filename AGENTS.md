# Zasady pracy nad RentMor — obowiązujące dla kolejnych zmian

Repozytorium: **Domiono/Protokol**. Projekt obejmuje publiczną stronę RentMor.pl, panel WWW i natywną aplikację Android.

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
