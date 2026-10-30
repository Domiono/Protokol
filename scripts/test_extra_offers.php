<?php
declare(strict_types=1);

require_once __DIR__ . '/../server/rental_offer_details.php';
require_once __DIR__ . '/../server/rental_offer_details_extra.php';

// Exact public catalogue names seen on rentmor.pl on 2026-10-30.
$names = [
    'wiertnica-glebowa' => 'Wiertnica glebowa, świder',
    'bosch-gws-2200' => 'Szlifierka kątowa 230mm Bosch Professional GWS 2200',
    'dewalt-dws520ktr' => 'Zagłębiarka DeWalt DWS520KTR + SZYNA 1,5 m, Pilarka tarczowa',
    'stiga-src-775' => 'Glebogryzarka Stiga SRC 775 RG',
    'cedrus-rb02-pro' => 'Rębak Cedrus RB02 PRO, rozdrabniacz do gałęzi',
    'energotytan-ph32uth' => 'Zaciskarka, praska do rur pex wynajem systemu U, TH',
    'norton-tr202' => 'Przecinarka do glazury Norton Clipper TR202. Przecinarka do płytek na mokro',
    'cedrus-wr01-h' => 'Wertykulator Cedrus WR01-H',
    'dewalt-dwe4357' => 'Szlifierka kątowa 125mm DeWALT DWE4357, szlifierka do betonu',
    'odkurzacz-przemyslowy' => 'Odkurzacz przemysłowy Bosch GAS 25 L SFC/ Metabo ASR 25L SC',
    'dewalt-d25810k' => 'Młot udarowy dewalt D25810K. 7.1J',
    'dewalt-d25911k' => 'Młot udarowy Dewalt D25911K-QS 27J',
    'ulsonix-ozonator' => 'Ozonator 20000 mg/h ULSONIX',
    'bosch-gcm-305' => 'Ukośnica, Piła ukosowa, pilarka. Wynajem Kraków',
    'karcher-hd720' => 'Myjka ciśnieniowa Karcher HD 7/20 G Classic',
];

$extra = rentmor_extra_offers();
if (count($extra) !== 15) { fwrite(STDERR, "Expected exactly 15 extra offers\n"); exit(1); }
foreach ($names as $key => $name) {
    if (!isset($extra[$key])) { fwrite(STDERR, "Missing SEO offer: $key\n"); exit(2); }
    if (rentmor_extra_offer_key(strtolower($name)) !== $key) {
        fwrite(STDERR, "Wrong SEO matching: $key\n"); exit(3);
    }
    $offer = $extra[$key];
    if (empty($offer['title']) || empty($offer['intro']) || count($offer['questions'] ?? []) < 3) {
        fwrite(STDERR, "Incomplete editorial copy for $key\n"); exit(4);
    }
    $rendered = rentmor_offer_details(['id' => 'new-' . $key, 'name' => $name]);
    if (!str_contains($rendered, htmlspecialchars($offer['title'], ENT_QUOTES, 'UTF-8'))
        || substr_count($rendered, '<h3') < 4
        || !str_contains($rendered, '/zasady.php')
        || !str_contains($rendered, 'data-rentmor-offer-details')) {
        fwrite(STDERR, "SEO section not rendered correctly for $key\n"); exit(5);
    }
}
if (rentmor_offer_details(['id' => 'unrelated', 'name' => 'Nieznany model']) !== '') {
    fwrite(STDERR, "Unrelated product must not acquire guessed SEO copy\n"); exit(6);
}
if (!str_contains(rentmor_offer_details(['id' => '69388418830b8', 'name' => 'Odkurzacz piorący Karcher Puzzi 10/1']), 'Puzzi')) {
    fwrite(STDERR, "Original editorial offers must be preserved\n"); exit(7);
}
echo "15 additional SEO sections verified; previous curated offers preserved.\n";
