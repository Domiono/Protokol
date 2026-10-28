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

$set = rentmor_offer_details(['id'=>'69385df56774a','name'=>'Zestaw Stanley']);
ensure(str_contains($set,'szlifierka-stanley-zyrafa-do-gladzi-gipsu'), 'Existing set link lost');
ensure(substr_count($set, 'szlifierka-stanley-zyrafa-do-gladzi-gipsu') === 1, 'Duplicate existing set link');

$other = rentmor_offer_details(['id'=>'unknown','name'=>'Bruzdownica innej marki']);
ensure($other === '', 'Content leaked to unrelated product');
$escaped = rentmor_offer_details(['id'=>'<script>alert(1)</script>','name'=>'Stalco Bruzdownica']);
ensure(!str_contains($escaped, 'data-rentmor-offer-details="<script>'), 'Unescaped product ID');
ensure(str_contains($escaped, '&lt;script&gt;'), 'Missing escaped attribute');

echo "Offer details and related links tests passed.\n";
