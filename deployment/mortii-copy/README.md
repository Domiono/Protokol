## Aktualizacja 10.11.2026: opublikowano

PR #144: preflight42766482713 i wdrożenie42766581986 zakończone powodzeniem. Tymczasowy aktywny workflow usunięty po wdrożeniu; pozostał nieaktywny szablon. Poniższe instrukcje są opisem przygotowanego procesu, a nie wezwaniem do ponownego wdrożenia. Manifest wskazuje wersje sprzed publikacji, więc ponowny apply zatrzyma się na SHA.

# Mortii: jednorazowe wdrożenie tekstów formularza

Status 10.11.2026: przygotowane i przetestowane syntetycznie; NIE uruchomiono SFTP ani publikacji. Szablon workflow jest nieaktywny (`workflow.yml.example`). Bieżące workflow test-only i ich zabezpieczenia pozostają bez zmian.

Zakres: wyłącznie `mortii.js` (usunięcie zaznaczonych przez właściciela tekstów) i trzy istniejące pliki integracji PHP (tylko zmiana `mortii.js?v=2` na `mortii.js?v=3`, ponieważ JS jest cache'owany przez tydzień). Bez zmian w buforze 90 minut, regułach dostępności, danych klientów, konfiguracji AI, OAuth i SMS. To NIE rozwiązuje otwartego problemu odrzucania terminu.

## Przygotowanie do uruchomienia

1. Skonfigurować/zweryfikować w GitHub Actions sekrety `FTP_SERVER`, `FTP_USERNAME`, `FTP_PASSWORD` bez wpisywania wartości do repo ani czatu. Aktualne narzędzia połączenia nie pozwalają potwierdzić ich istnienia. Potrzebne SFTP na porcie 22222 i istniejący katalog `rentmor-control-private` z uprawnieniami 0700. Używane są dwa wcześniej zweryfikowane odciski kluczy hosta z istniejącego skryptu projektu; inny klucz zatrzyma połączenie PRZED wysłaniem hasła.
2. W osobnej, jawnie wdrożeniowej gałęzi skopiować template do `.github/workflows/manual-mortii-copy.yml`. Obecna polityka test-only będzie świadomie zgłaszać taki aktywny workflow jako naruszenie. Nie wyłączać globalnego skanera ani nie dodawać ogólnego wyjątku; aktywację uzgodnić jako konkretny wyjątek do wcześniejszej decyzji test-only. Samo dodanie tego pakietu niczego nie uruchamia.
3. Uruchomić ręcznie workflow z `mode=check`. Wszystkie cztery źródła muszą zgadzać się z manifestem SHA256, mieć jednoznaczne fragmenty i przejść parser PHP/Node. Check nie zapisuje niczego na hostingu i nie tworzy backupu. Workflow wymaga dostępnego PHP/Node na runnerze; brak parsera jest błędem, nie pominięciem testu.
4. Po sprawdzeniu wyniku wybrać ręcznie `mode=apply`, confirmation `MORTII-COPY-ONLY`. Brak automatycznego wdrażania po push/PR/schedule. Szablon nie wysyła artefaktów ani logów źródeł na GitHub.
5. Apply tworzy prywatną losową kopię całego zestawu, sprawdza jej odczyt i prawa 0600, porównuje ponownie każdy plik przed zapisem, wgrywa najpierw JS, następnie odnośniki cache. Zapis bezpośredni zgodny z dotychczasowym hostingiem: nie jest to atomowa transakcja serwera. Przy błędzie zapisu/odczytu lub publicznych kontroli próbuje przywrócić cały zmieniony zestaw; awaria połączenia może uniemożliwić rollback i wymaga ręcznego odzyskania z prywatnej kopii. Przy równoległych zmianach poza Actions należy wstrzymać inne wdrożenia.
6. Po sukcesie sprawdzić formularz w przeglądarce, odczyt HTTP i SHA plików, a następnie usunąć jednorazowy workflow. Nie przywracać całej starej gałęzi ani nie nadpisywać aktualnego hostingu źródłami z main: Mortii powstał później.

## Testy

`python3 deployment/mortii-copy/test_deploy.py`: 8 testów offline: check bez zapisu, niezgodność SHA, symlink, ścieżki spoza listy, częściowy zapis, awaria publicznej kontroli, prywatność kopii, udane wdrożenie z kopią. Parsery i patch sprawdzone dodatkowo na lokalnych kopiach czterech plików. Brak testu prawdziwego połączenia SFTP; dane logowania nie były odczytywane.
