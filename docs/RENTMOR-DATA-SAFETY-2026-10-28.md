# RentMor: integralność danych, kopie zapasowe i monitoring

## Wspólny zapis

- Panel `admin.php` i Android `protocol_sync.php` blokują ten sam plik `data.json.lock` podczas odczytu i zapisu. Dane JSON są podmieniane atomowo.
- Uszkodzony istniejący JSON skutkuje błędem 503 i odmową zapisu — **nie jest** zastępowany pustą tablicą. Dotyczy to ofert, rezerwacji, klientów, kosztów, wiadomości i egzemplarzy. Testy używają wyłącznie tymczasowych danych.
- W przypadku 503 nie wykonuj prób „naprawy” przez wyczyszczenie danych; najpierw odzyskaj poprawną kopię.

## Zdjęcia oferty

W panelu **Sprzęt → Edytuj** są miniatury z przyciskami przesuwania w lewo/prawo. Pierwsze zdjęcie jest zdjęciem głównym. Checkbox „Usuń zdjęcie” zaznacza zdjęcie do usunięcia po zapisaniu formularza. Plik jest usuwany dopiero po pomyślnym zapisie i tylko jeśli nie jest używany w innej ofercie. Dodane w tym samym formularzu zdjęcia trafiają na koniec galerii.

## Prywatna kopia danych

Workflow `.github/workflows/backup-rentmor.yml` uruchamia się codziennie o 02:15 UTC (03:15/04:15 czasu polskiego), a dodatkowo na zmianę konfiguracji i ręcznie.

1. Pobiera zasoby po SFTP do nietrwałego katalogu uruchomienia GitHub Actions, bez publikowania jako artefakt czy commit.
2. Sprawdza poprawność JSON; gdy brakuje wymaganych danych albo są uszkodzone, backup kończy się błędem.
3. Umieszcza sprawdzone pliki w `rentmor-private/backups/RRRR-MM-DD/` **poza `public_html`**, pobiera je ponownie i porównuje z oryginałem.
4. Wymaga istniejących sekretów SFTP `FTP_SERVER`, `FTP_USERNAME`, `FTP_PASSWORD`. Nie zawiera danych klienta w logach.

**Uwaga:** Pierwszy rzeczywisty backup należy potwierdzić statusem workflow. Przechowywanie wielu codziennych kopii zwiększa wykorzystanie miejsca dyskowego — retencję (np. 30 dni) trzeba wdrożyć osobno po sprawdzeniu uprawnień do usuwania na hostingu. Nie przeprowadzamy automatycznego usuwania archiwum bez potwierdzonego przywracania. Kopia obejmuje JSON i stan egzemplarzy, nie obejmuje pełnych plików zdjęć z katalogu `uploads/`.

**Przywracanie:** Najpierw pobierz i sprawdź kopię, wykonaj zapas aktualnego stanu, wyłącz zapisy na czas przywrócenia, podmień pliki z zachowaniem uprawnień i uruchom test synchronizacji. Nie przywracaj wyłącznie rezerwacji, gdy zmieniły się identyfikatory egzemplarzy.

## Monitoring

Workflow `.github/workflows/monitor-rentmor.yml` uruchamia się co godzinę. Sprawdza bez logowania: stronę główną, robots.txt, sitemapę i poprawną odmowę 401 dla niezalogowanego API aplikacji. Powtarza odczyt po pierwszej chwilowej awarii.

Wynik jest dostępny w **GitHub → Actions**; alert o błędzie zależy od włączonych powiadomień GitHub dla właściciela. Dodatkowo można skonfigurować Telegram, ustawiając jako sekrety repozytorium `RENTMOR_MONITOR_TELEGRAM_BOT_TOKEN` i `RENTMOR_MONITOR_TELEGRAM_CHAT_ID`. **Bez tych dwóch sekretów powiadomienia Telegram nie są wysyłane.** Nie podawaj ich w rozmowie ani w repozytorium.

Monitoring nie sprawdza stanu zalogowanej aplikacji ani powodzenia każdej operacji zapisu; wymagałoby to osobnego bezpiecznego testowego konta.
