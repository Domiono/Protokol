<?php
declare(strict_types=1);
require __DIR__ . '/../server/rental_offer_details.php';

function ensure(bool $ok, string $why): void {
    if (!$ok) { fwrite(STDERR, $why . PHP_EOL); exit(1); }
}

// The hosting inventory ID is not hard-coded for the Stalco guide.
$chaser = rentmor_offer_details(['id'=>'future-id', 'name'=>'Bruzdownica Stalco']);
ensure(str_contains($chaser, 'Bruzdownica Stalco – wynajem'), 'Missing Stalco guide');
ensure(str_contains($chaser, 'głębokość cięcia od 5 do 29 mm'), 'Missing verified cutting depth');
ensure(str_contains($chaser, '/odkurzacz-przemyslowy-bosch-gas-25-l-sfc-metabo-asr-25l-sc/'), 'Missing compatible category suggestion');
ensure(str_contains($chaser, 'Sprzęt przydatny do tego zadania'), 'Related equipment section missing');
ensure(str_contains($chaser, 'Dostępność każdego urządzenia'), 'Related equipment availability caveat missing');

$ladder=rentmor_offer_details(['id'=>'6938228c7c4bb','name'=>'Drabina składana Podest Krause Corda']);
ensure(str_contains($ladder,'KRAUSE Corda 4×3'), 'KRAUSE guide missing');
ensure(str_contains($ladder,'150 kg'), 'Missing load limit');
ensure(str_contains($ladder,'4,40 m'), 'Missing work height');
ensure(str_contains($ladder,'/szlifierka-stanley-zyrafa-do-gladzi-gipsu/'), 'Missing contextual ladder link');

$set = rentmor_offer_details(['id'=>'69385df56774a','name'=>'Zestaw Stanley']);
ensure(str_contains($set,'szlifierka-stanley-zyrafa-do-gladzi-gipsu'), 'Existing set link lost');
ensure(substr_count($set, 'szlifierka-stanley-zyrafa-do-gladzi-gipsu') === 1, 'Duplicate existing set link');

$solo = rentmor_offer_details(['id'=>'69384693e2cb9','name'=>'Szlifierka Stanley']);
ensure(str_contains($solo, 'Kiedy wybrać samą żyrafę'), 'Missing solo vs set guidance');
ensure(str_contains($solo, '225 mm'), 'Missing verified disc size');
ensure(str_contains($solo, '/poradnik-jaki-papier-scierny-do-zyrafy.php'), 'Missing contextual paper guide');
ensure(substr_count($solo, '/zestaw-szlifierka-stanley-zyrafa-do-gladzi-i-odkurzacz-bosch-metabo/') === 1, 'Missing or duplicate set link');
ensure(str_contains($solo, '/odkurzacz-przemyslowy-bosch-gas-25-l-sfc-metabo-asr-25l-sc/'), 'Missing dust extractor link');
ensure(!str_contains($set, 'Kiedy wybrać samą żyrafę'), 'Solo FAQ copied to set');

$other = rentmor_offer_details(['id'=>'unknown','name'=>'Bruzdownica innej marki']);
ensure($other === '', 'Content leaked to unrelated product');
$escaped = rentmor_offer_details(['id'=>'<script>alert(1)</script>','name'=>'Stalco Bruzdownica']);
ensure(!str_contains($escaped, 'data-rentmor-offer-details="<script>'), 'Unescaped product ID');
ensure(str_contains($escaped, '&lt;script&gt;'), 'Missing escaped attribute');

echo "Offer details and related links tests passed.\n";
