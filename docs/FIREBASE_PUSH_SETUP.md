# RentMor — konfiguracja powiadomień push FCM

Ta integracja dodaje **data-only** powiadomienia Firebase Cloud Messaging. W samym push
przesyłamy wyłącznie `type=contact_message` i identyfikator wiadomości. Imię, numer,
treść i szczegóły wiadomości pozostają na serwerze RentMor, a Android pobiera je z
uwierzytelnionego API. Tylko urządzenia z rolą `admin` mogą rejestrować tokeny.

## Włączenie (bez umieszczania kluczy w repozytorium)

1. Wejdź na https://console.firebase.google.com/ i utwórz projekt, np. „RentMor”.
2. Dodaj aplikację **Android** o identyfikatorze pakietu **`pl.rentmor.protokol`**.
   Nie zmieniaj identyfikatora — aktualizacje muszą zachować dane dotychczasowej aplikacji.
3. Pobierz `google-services.json` z ustawień projektu.
4. W GitHubie repozytorium `Domiono/Protokol` otwórz
   **Settings → Secrets and variables → Actions → New repository secret**.
   Dodaj `RENTMOR_FIREBASE_ANDROID_JSON` — pełną zawartość pliku `google-services.json`.
5. W Firebase → Ustawienia projektu → **Service accounts** wygeneruj nowy prywatny klucz
   konta usługi JSON. Jest **ściśle poufny**: nie wysyłaj go przez chat i nie umieszczaj
   w public_html ani w repozytorium. Dodaj jego pełną zawartość jako sekret
   `RENTMOR_FCM_SERVICE_ACCOUNT_JSON` w GitHub Actions.
6. W Google Cloud sprawdź, że **Firebase Cloud Messaging API** jest włączone
   dla tego samego projektu; konto usługi musi mieć uprawnienie wysyłania FCM.
7. Uruchom w GitHub Actions kolejno:
   - `Deploy RentMor API` — zapisuje klucz wyłącznie do katalogu **`rentmor-private`
     poza `public_html`**, a nie do WWW.
   - `Build APK` — wstrzykuje konfigurację Androida dopiero podczas kompilacji
     i publikuje najnowszy instalator.
8. Zainstaluj nowy APK **na obecnej aplikacji**, nie usuwaj jej danych.
   Otwórz ją jako administrator, przyznaj powiadomienia i uruchom synchronizację.
   Wyślij testową wiadomość przez formularz RentMor.pl.

## Weryfikacja

- Jeśli nie ustawiono sekretów, APK nadal się kompiluje i zachowuje
  dotychczasową skrzynkę + sprawdzanie w tle (to **nie** jest aktywny push).
- W logach `Build APK` szukaj **Validated Firebase Android application configuration.**
- W logach `Deploy RentMor API` sprawdź, czy krok instalacji klucza wykonał
  przesłanie pliku (zamiast komunikatu o brakującym kluczu).
- Żadnych tokenów, treści wiadomości ani klucza prywatnego nie kopiuj do zgłoszeń
  ani zrzutów ekranu.
- Pierwsze uruchomienie rejestruje token urządzenia po autoryzowanej synchronizacji.
  Od tego momentu każda **nowa** wiadomość wysłana z formularza wywołuje push.
- Google Play services i dostęp do Internetu są wymagane. Android może opóźniać
  dostarczenie lub blokować powiadomienia przez ustawienia systemowe;
  FCM nie gwarantuje dostarczenia w określonej liczbie sekund.

## Gdzie przechowujemy dane

- `rentmor-private/fcm-service-account.json` — prywatny klucz konta usługi, poza WWW.
- `rentmor-private/fcm-tokens.json` — tokeny urządzeń związane wyłącznie z aktywnymi
  uprawnieniami administratora; plik poza WWW.
- `public_html/messages.json` — dotychczasowa baza wiadomości strony;
  ta integracja nie zmienia formatu wiadomości.
- `public_html/push_bridge.php` — funkcje wysyłki i rejestracji; nie ujawnia kluczy.

Jeśli hosting blokuje dostęp PHP do folderu ponad `public_html` (open_basedir),
trzeba nadać mu uprawnienia do folderu `rentmor-private` w ustawieniach hostingu.
Nie przenoś klucza do `public_html` na skróty.

## Co się dzieje przy braku push

Dotychczasowy `MessageMonitor` nadal sprawdza wiadomości okresowo przez
autoryzowane API i zachowuje historię. Jeśli serwer Firebase jest niedostępny,
wiadomość z formularza nadal zapisuje się w RentMor.pl i działają dotychczasowe
powiadomienia e-mail / Telegram.
