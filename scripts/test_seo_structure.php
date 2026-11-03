<?php
require __DIR__.'/../server/seo_structure.php';
function check($ok, $message) { if (!$ok) throw new RuntimeException($message); }
$home = '<html><head><link rel="canonical" href="https://rentmor.pl/"></head><body><nav><div><a href="/" aria-label="RentMor.pl — strona główna"><img src="/rentmor-logo.png" alt="RentMor.pl"></a><a href="tel:733891281">Telefon</a></div></nav><h1>Katalog</h1><div id="products">KEEP CARDS</div><section><h2>Wynajem sprzętu budowlanego i ogrodniczego w Krakowie</h2><p>Szukasz sprzętu</p></section><section id="poradniki">old guide</section><section id="wynajem-sprzetu-krakow">old local</section><footer>KEEP FOOTER</footer></body></html>';
if (isset($argv[1])) $home = file_get_contents($argv[1]);
$out = rentmor_seo_structure($home);
check(str_contains($out, 'data-rm-home-structure="v1"'), 'Known live home sections replaced');
check(substr_count($out, 'id="poradniki"') === 1, 'One guides section');
check(substr_count($out, 'id="wynajem-sprzetu-krakow"') === 1, 'One rental section');
check(!str_contains($out, 'Szukasz sprzętu do remontu'), 'Removed duplicate introduction');
check(rentmor_seo_structure($out) === $out, 'Idempotent HTML');
foreach (rm_categories() as $slug=>$cat) check(str_contains($out, 'href="/'.$slug.'/"'), 'Category linked');
check(substr_count($out, 'id="rm-category-menu"') === 1, 'One header menu');
check(!str_contains($out, 'class="rm-seo-links"'), 'No duplicate lower category navigation');
check(strpos($out, 'id="rm-category-menu"') < strpos($out, '<h1>'), 'Menu above content');
check(str_contains($out, 'href="tel:733891281"'), 'Phone preserved');
$schema = ['@context'=>'https://schema.org','@type'=>'Product','name'=>'Test </script>','offers'=>['@type'=>'Offer','url'=>'https://rentmor.pl/test/','price'=>'130','priceCurrency'=>'PLN','availability'=>'https://schema.org/InStock']];
$product = '<html><head><link rel="canonical" href="https://rentmor.pl/test/"><script type="application/ld+json">'.rm_category_json($schema).'</script></head><body><h1>Test</h1><p>130 zł / doba</p><form action="/">FORM</form></body></html>';
$rendered = rentmor_seo_structure($product);
preg_match('~<script type="application/ld\+json">(.*?)</script>~s', $rendered, $m);
$node = json_decode($m[1], true, 512, JSON_THROW_ON_ERROR);
check($node['offers']['price'] === '130', 'Price unchanged');
check($node['offers']['priceSpecification']['referenceQuantity']['unitCode'] === 'DAY', 'One day');
check($node['offers']['businessFunction'] === 'http://purl.org/goodrelations/v1#LeaseOut', 'Rental');
check(!isset($node['offers']['availability']), 'No unverified stock assertion');
check(str_contains($rendered, '<form action="/">FORM</form>'), 'Form unchanged');
check(str_contains($rendered, 'data-rm-rental-terms="v1"'), 'Visible terms');
check(!str_contains($rendered, 'id="poradniki"'), 'No home content on product');
check(rentmor_seo_structure('{"ok":true}') === '{"ok":true}', 'Non HTML unchanged');
$items = rm_category_items([['name'=>'Puzzi','cat'=>'czyszczace','price'=>50],['name'=>'Młot','category'=>'budowlane','price'=>60],['name'=>'Puzzi','cat'=>'czyszczace'],['name'=>'Nowy sprzęt','cat'=>'czyszczące']], 'czyszczace');
check(count($items) === 2 && isset($items['puzzi'], $items['nowy-sprzet']), 'Category matching and deduplication');
check(rm_related_price(['price'=>'<script>']) === '', 'Invalid price excluded');
check(str_contains(rm_category_escape('<script>'), '&lt;script&gt;'), 'Escaping');
// Unknown templates must keep their original content rather than remove selected pieces.
$unknown = str_replace('Szukasz sprzętu', 'Changed layout', $home);
if (!isset($argv[1])) check(str_contains(rentmor_seo_structure($unknown), 'old local'), 'Unknown layout retained');
echo "PASS: homepage consolidation, categories, schema semantics, price preservation, escaping, forms, idempotence\n";
