# AI — Wiedza, Android 1.7.27 / 42

PR148. Nowy ekran administratora zapisuje wiedzę dla klientów osobno od dotychczasowej prywatnej wiedzy czatu WWW. Publiczny Mortii odczytuje wyłącznie notatkę świadomie opublikowaną przez administratora. Zapis jest online i nie wywołuje modelu AI. Pracownicy mają odmowę GET/POST po stronie serwera.

Walidacja42825160844 SUCCESS:31kontroli PHP,10testów Robolectric/HTTP/UI oraz podpisany assembleRelease. Testowano wyłącznie fikcyjne dane w katalogu tymczasowym. Nie wywoływano płatnegoAI ani rzeczywistych zgłoszeń.

Właściciel zatwierdził jednorazowy wyjątek GitHub/SFTP. Manifest dotyczy dokładnych poprzednich SHA produkcji, nie służy do ponownego wdrażania na zmieniony serwer. Podpis nowegoAPK musi być zgodny z dotychczasowym, poprzednia wersja41, nowa42. Backupy prywatne, odczyt zwrotny źródeł/APK/dokumentów, kontrola niezalogowanegoAPI401 i publicznegoAPK. Tymczasowe aktywne workflow należy usunąć po zakończeniu, bez wyłączania globalnej kontroli test-only.

Postępy i końcowy status publikacji: docs/CONTINUE-RENTMOR.md oraz docs/RENTMOR-PROJECT-LOG.md. Nie kopiować całego hostingu ani danych użytkowników do repozytorium.
