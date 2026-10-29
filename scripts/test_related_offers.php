<?php
declare(strict_types=1);
require_once __DIR__ . '/../server/related_offers.php';

function check_related(bool $condition, string $message): void {
    if (!$condition) { fwrite(STDERR, "FAIL: $message\n"); exit(1); }
}
$catalogue = [
    ['id'=>'f','name'=>'Kompaktowy rębak spalinowy Forest Master FM6DD 6KM','category'=>'ogrodnicze','price'=>85],
    ['id'=>'c','name'=>'Rębak Cedrus RB02 PRO rozdrabniacz do gałęzi','category'=>'ogrodnicze','price'=>200],
    ['id'=>'t','name'=>'Glebogryzarka Stiga SRC 775 RG','category'=>'ogrodnicze','price'=>90],
    ['id'=>'m','name'=>'Szlifierka do betonu i posadzek 250mm - Wynajem Kraków','category'=>'budowlane','price'=>'130 zł'],
    ['id'=>'v','name'=>'Odkurzacz przemysłowy Bosch GAS 25 L SFC Metabo ASR 25L SC','category'=>'budowlane','price'=>50, 'images'=>['vacuum.webp']],
    ['id'=>'a','name'=>'Szlifierka kątowa 125mm DeWALT DWE4357','category'=>'budowlane','price'=>65],
    ['id'=>'s','name'=>'Szlifierka Stanley żyrafa do gładzi gipsu','category'=>'budowlane','price'=>40],
    ['id'=>'z','name'=>'Zestaw szlifierka Stanley żyrafa do gładzi i odkurzacz Bosch Metabo','category'=>'budowlane','price'=>80],
    ['id'=>'x','name'=>'<script>alert(1)</script>','category'=>'budowlane','price'=>45,'images'=>['../../evil.php']],
];

$forest = rm_related_pick($catalogue[0], $catalogue);
check_related(count($forest) === 2, 'Forest Master should link existing garden tools only');
check_related($forest[0]['id'] === 'c', 'Forest Master should prefer Cedrus');
check_related(!in_array('f', array_column($forest, 'id'), true), 'Do not link current offer');
$msw = rm_related_pick($catalogue[3], $catalogue);
check_related(count($msw) === 3, 'Limit recommendations to 3');
check_related($msw[0]['id'] === 'v' && $msw[1]['id'] === 'a', 'MSW must prefer vacuum then angle grinder');
$solo = rm_related_pick($catalogue[6], $catalogue);
check_related($solo[0]['id'] === 'z', 'Single Stanley should recommend complete Stanley set');
$html = rentmor_render_related_offers($catalogue[3], $catalogue);
check_related(str_contains($html, 'data-rentmor-recommendations="v1"'), 'Missing dynamic section');
check_related(substr_count($html, 'data-rentmor-recommendation="1"') === 3, 'Expected three linked cards');
check_related(str_contains($html, '/odkurzacz-przemyslowy-bosch-gas-25-l-sfc-metabo-asr-25l-sc/'), 'Missing live vacuum URL');
check_related(str_contains($html, '130 zł / doba') === false, 'Do not display current product price on recommendations');
check_related(str_contains($html, '50 zł / doba'), 'Candidate rental price missing');
check_related(str_contains($html, '/uploads/vacuum.webp'), 'Candidate image missing');
check_related(!str_contains($html, '<script>'), 'Product names must be escaped');
check_related(rm_related_image($catalogue[8]) === '', 'Unsafe image rejected');
check_related(rm_related_slug('Żyrafa Stanley') === 'zyrafa-stanley', 'Polish transliteration');
check_related(rentmor_render_related_offers($catalogue[0], []) === '', 'No imaginary fallback products');
check_related(rm_related_pick(['name'=>'Bad product'], [['name'=>'Bad product']], 3) === [], 'Single item cannot recommend itself');
$added = ['id'=>'new-1','name'=>'Nowy odkurzacz przemysłowy 2027', 'category'=>'budowlane'];
$newResults = rm_related_pick($catalogue[3], array_merge($catalogue, [$added]));
check_related(in_array('new-1', array_column($newResults, 'id'), true), 'New matching equipment automatically included');
echo "PASS: contextual catalogue ranking, new products, limits, safe links, images and prices.\n";
