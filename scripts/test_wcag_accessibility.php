<?php
require_once __DIR__ . '/../server/seo_structure.php';
function wcag_check($truthy, string $message): void {
    if (!$truthy) throw new RuntimeException('WCAG check failed: ' . $message);
}
$home = <<<'HTML'
<!doctype html><html lang="pl"><head><link rel="canonical" href="https://rentmor.pl/"></head>
<body><h1>Wypożyczalnia Kraków</h1>
<div class="max-w-4xl mx-auto px-4 -mt-8 relative z-10 mb-10">
<div class="relative"><input type="text" id="searchInput" oninput="searchProducts()" placeholder="Czego szukasz?"></div>
<div><button onclick="filterProducts('all', this)" class="filter-btn filter-active">Wszystkie</button>
<button onclick="filterProducts('budowlane', this)" class="filter-btn">🏗️ Budowlane</button>
<button onclick="filterProducts('ogrodnicze', this)" class="filter-btn">🌱 Ogrodnicze</button>
<button onclick="filterProducts('czyszczace', this)" class="filter-btn">🧹 Czyszczące</button></div></div>
<div class="product-item"><h3>Wypożycz szlifierkę</h3></div>
<section><h2>Gdzie jesteśmy?</h2><iframe title="Mapa dojazdu RentMor Kraków"></iframe></section>
<footer><a href="/polityka-prywatnosci.php">Polityka prywatności</a> · <a href="#ustawienia-cookies" data-rm-cookie-settings>Ustawienia cookies</a></footer>
</body></html>
HTML;
$rendered = rentmor_seo_structure($home);
wcag_check(str_contains($rendered, '<label for="searchInput" class="rm-sr-only">Szukaj sprzętu do wynajęcia</label>'), 'Search has a programmatic label');
wcag_check(substr_count($rendered, 'id="rentmor-catalogue-heading"') === 1, 'H2 gives product H3 headings context');
wcag_check(strpos($rendered, 'id="rentmor-catalogue-heading"') < strpos($rendered, '<h3>'), 'H2 before product titles');
wcag_check(substr_count($rendered, 'aria-pressed="true"') === 1, 'One active filter initially');
wcag_check(substr_count($rendered, 'aria-pressed="false"') === 3, 'Three inactive filters initially');
wcag_check(str_contains($rendered, 'aria-label="Filtr: sprzęt budowlany"'), 'Emoji filter has text accessible name');
wcag_check(str_contains($rendered, 'aria-label="Filtr: sprzęt ogrodniczy"'), 'Garden filter has text accessible name');
wcag_check(str_contains($rendered, 'aria-label="Filtr: wszystkie urządzenia"'), 'All filter has text accessible name');
wcag_check(str_contains($rendered, '<button type="button" class="rm-cookie-settings-button" data-rm-cookie-settings>Ustawienia cookies</button>'), 'Cookie action is a button');
wcag_check(!str_contains($rendered, 'href="#ustawienia-cookies"'), 'No dead fragment link');
wcag_check(str_contains($rendered, '<iframe title="Mapa dojazdu RentMor Kraków">'), 'Already accessible map unchanged');
wcag_check(str_contains($rendered, 'oninput="searchProducts()"'), 'Existing search handler preserved');
wcag_check(str_contains($rendered, "filterProducts('ogrodnicze', this)"), 'Existing filter handler preserved');
wcag_check(str_contains($rendered, '<html lang="pl">'), 'Page language preserved');
wcag_check(rentmor_seo_structure($rendered) === $rendered, 'HTML pipeline idempotent');
wcag_check(rm_accessibility_html($rendered, true) === $rendered, 'Accessibility changes idempotent');
$offer = str_replace('href="https://rentmor.pl/"', 'href="https://rentmor.pl/test/"', $home);
$offerResult = rentmor_seo_structure($offer);
wcag_check(!str_contains($offerResult, 'rentmor-catalogue-heading'), 'No catalogue heading added to offers');
wcag_check(str_contains($offerResult, 'rm-cookie-settings-button'), 'Offer has accessible cookies action');
wcag_check(str_contains($offerResult, '<iframe title="Mapa dojazdu RentMor Kraków">'), 'Offer map unchanged');
wcag_check(rm_accessibility_html('No HTML', true) === 'No HTML', 'Non-HTML preserved');
echo "PASS: WCAG labelled search, headings, filter state, cookie button, original handlers, map and language\n";
