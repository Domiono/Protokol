# Wiedza AI według sprzętu

PR150, Android1.7.29/code44, publikacja42834469996 SUCCESS. Dotychczasowy knowledge.json pozostaje notatką „Zasady ogólne”. Nie przepisywano istniejącej wiedzy. Administrator sam przenosi odpowiednie fragmenty do wybranego sprzętu.

Publiczne i prywatne notatki pozostają oddzielone. Katalog sprzętu pochodzi z nazw ofert; każda notatka ma katalog equipment-knowledge/SHA256(nazwa), etykietę i własny knowledge.json/lock/previous. Katalogi0700, pliki0600; żadnej ścieżki od użytkownika. Zapis jednej notatki nie zmienia rewizji innych. Stare nazwy po zmianie/usunięciu oferty pozostają dostępne; nie mapować ich automatycznie na nową ofertę.

rm_ai_knowledge_read domyślnie odczytuje również notatki sprzętów na potrzeby czatu WWW i Mortii. API edytora zwraca tylko wybraną notatkę i listę nazw. Kontekst dobiera do dwóch notatek według dopasowania katalogu do pytania; publiczny Mortii bierze pod uwagę ostatnie cztery wiadomości, żeby zachować temat. Limit kontekstu80000bajtów; najpierw usuwa opisy katalogowe, a następnie dodatkowe notatki, jeśli rozmiar przekracza limit. Nigdy nie dołącza prywatnej wiedzy do publicznego czatu.

Walidacja:31dotychczasowych+15nowychkontroli PHP,4HTTPpobierania,15Android/UI/HTTP, podpisana kompilacja. Brak płatnegoAI lub prawdziwych zgłoszeń. Publish jest historycznym procesem z dokładnymi SHA poprzednich plików: nie uruchamiać ponownie na zmienionej produkcji. Kolejne wydanie musi aktualizować APK i app-release.json łącznie. Postępy w docs/CONTINUE-RENTMOR.md oraz docs/RENTMOR-PROJECT-LOG.md.
