# Mortii – oprawa graficzna czatu (10.11.2026)

**Status: przygotowane, bez publikacji na rentmor.pl.**

W katalogu `assets/` są cztery pliki publiczne: dwa WebP maskotki, plik CSS i plik JS. Grafiki mają służyć jako dymek otwierający czat oraz awatar w nagłówku i odpowiedziach asystenta. Warstwa JS jest dodatkiem do już działającego `mortii.js`; nie zmienia API, treści rozmów, rezerwacji, płatności, powiadomień ani SMS.

Docelowo opublikować cztery pliki w `public_html/assets/` oraz za istniejącym znacznikiem `<script src="/mortii.js?v=5" defer></script>` dodać jeden znacznik `<script src="/assets/rentmor-ai-mortii-visual.js?v=1" defer></script>` w integracjach `public_metadata.php`, `rental_categories.php`, `polityka-prywatnosci.php`. JS sam dołącza CSS. Nie używać starszych kopii `mortii.js` ani całego kodu `main` jako źródła dla produkcji.

**Walidacja:** sprawdzić aktualne SHA256 i dokładne wystąpienie znaczników w trzech plikach PHP, potem backup, aktualizacja i odczyt zwrotny. Test składni nowego JS: `node --check` – PASS lokalnie. Po wdrożeniu sprawdzić cztery zasoby HTTP, stronę główną/produkt/kategorię/politykę, kliknięcie launchera, jego znikanie po otwarciu, zamknięcie i aktualizację awatarów w historii; bez wysyłania prawdziwej rezerwacji ani płatnego pytania AI.

Repozytorium pozostaje test-only; bez automatycznych wdrożeń, sekretów i danych klientów.