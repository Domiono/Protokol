# RentMor — GitHub wyłącznie do testów (publiczne repozytorium)

Status migracji: 07.11.2026. Cel: **brak dostępu GitHub Actions do AZ.pl**, brak automatycznych wdrożeń, brak sekretów produkcyjnych w zadaniach GitHub.

## Docelowy przepływ

1. Kod jest przygotowywany i weryfikowany w GitHub. Repozytorium jest **PUBLICZNE** — nie umieszczaj tu danych prywatnych, konfiguracji hostingu, haseł, tokenów ani plików klientów.
2. GitHub Actions ma wyłącznie testy lokalne, audyty ogólnodostępnych stron i uprawnienie `contents: read`. Brak SFTP/FTP, sekretów i połączeń administracyjnych z AZ.pl.
3. Po pozytywnych testach poprawki plików z jawnej listy mogą być przygotowane/publikowane przez RentMor Control. MCP wymaga `source:write`, zgodnego SHA256, osobnej akceptacji hasłem właściciela i wykonuje backup każdej zmiany.
4. **Ważne ograniczenie:** obecna wersja RentMor Control 0.6 nie obsługuje wszystkich plików i katalogów (np. panelu admina, API, plików uwierzytelniania, aplikacji Android). Dla nich NIE MA jeszcze niezależnego od GitHuba sposobu publikacji przez MCP. Nie wolno omijać ograniczeń zabezpieczeń. Rozbudowę trzeba przygotować jako odrębny, szczegółowo kontrolowany projekt.
5. Zmiany na produkcji nie są wykonywane podczas testów GitHub.

## Aktywne automatyzacje GitHub

- `ci-public-repository-safety.yml`: odrzuca workflow z dostępem do sekretów, SFTP, uprawnieniami zapisu i mechanizmami publikacji oraz pliki o nazwach sugerujących sekrety.
- `test-rental-operations.yml`: testy składni PHP, reguł rezerwacji, API z danymi testowymi oraz testy jednostkowe Android i debug build; bez wypuszczania APK i bez dostępu do prywatnych API RentMor.
- `audit-rentmor-performance.yml`, `diagnose-pagespeed.yml`, `google-quality-audit.yml`, `public-browser-check.yml`: testowanie ogólnodostępnych zasobów (publiczne HTML/SEO/wydajność), bez uwierzytelniania i bez wdrożeń.
- `monitor-public-rentmor.yml`: cogodzinny test HTTP publicznej strony, robots.txt, sitemap oraz odmowy dostępu do nieautoryzowanego API; bez odczytu prywatnych logów, sekretów i bez powiadomień przez prywatne tokeny.

Workflow publikacji, kopiowania danych prywatnych i komunikacji SFTP z AZ.pl zostały usunięte **z bieżącej gałęzi** przy migracji. Historia Git może nadal zawierać ich poprzednie wersje. Usunięcie plików z aktualnej gałęzi **nie usuwa ich z wcześniejszych publicznych commitów, forków ani pobranych kopii**.

## Dostęp i dane

- Brak plików `.env`, `owner.json`, `state.json`, kluczy podpisywania APK i konfiguracji serwera w repo.
- GitHub Actions nie może używać repozytoryjnych sekretów FTP/SFTP, bo żaden aktywny workflow ich nie pobiera. **Sekrety w ustawieniach GitHub trzeba jednak osobno skasować**; narzędzia połączenia GitHub nie obsługują zmian sekretów konta. Warto też zmienić hasło SFTP w AZ.pl po potwierdzeniu zakończenia migracji, tak by stary login/hasło z GitHuba przestały działać.
- Publiczne repo nie jest mechanizmem ukrywania domeny ani zapobiegania czytaniu kodu. Dla pełnej ochrony kodu należy użyć prywatnego repozytorium lub przechowywać w publicznym tylko syntetyczne testy.
- Testy przez GitHub sprawdzają kod z repozytorium. **Nie weryfikują automatycznie, że dokładnie ten sam kod trafił później na hosting przez RentMor Control.** Przy wdrożeniu należy porównać SHA256/wersję i wynik testów.

## Kopie bezpieczeństwa i aplikacja Android

- Usunięto workflow automatycznej kopii na GitHub (zawierał komunikację z produkcyjnym hostingiem). AZ.pl deklaruje codzienne kopie dla Hostingu Basic, ale trzeba osobno zweryfikować w panelu dostępność i przywracanie. Dla odporności na awarię hostingu wskazana jest też niezależna kopia poza AZ.pl, **bez publicznych artefaktów GitHub Actions**.
- Usunięto automatyczne wydawanie podpisanego APK i publiczną publikację wydania GitHub. Android nadal przechodzi testy i kompilację debug. Przygotowanie i dystrybucja podpisanego APK wymaga osobnej, niepublicznej ścieżki.

## Audyt prywatności: ograniczenia

Przegląd nazw obecnych plików i aktywnych workflow nie jest pełnym skanem historycznych commitów ani dowodem, że wszystkie sekrety w przeszłości były bezpieczne. W przypadku znalezienia historii z kluczem trzeba **rotować** go na serwerze (nie wystarcza usunięcie z repozytorium).

## Czynności ręczne po scaleniu

1. W GitHub: **Settings → Secrets and variables → Actions**. Usuń niepotrzebne sekrety hostingu (np. `FTP_SERVER`, `FTP_USERNAME`, `FTP_PASSWORD`), ale tylko po potwierdzeniu, że nie są używane przez inne repozytoria/workflow.
2. W AZ.pl: zmień stare hasło SFTP i upewnij się, że RentMor Control OAuth działa nadal. MCP nie wymaga sekretów GitHub.
3. Sprawdź w panelu AZ.pl kopie bezpieczeństwa i możliwość przywracania; przechowuj przynajmniej jedną niezależną kopię poza hostingiem.
