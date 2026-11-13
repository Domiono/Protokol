<?php
declare(strict_types=1);
require_once __DIR__ . '/rental_categories.php';
require_once __DIR__ . '/rental_task_links.php';
$path = parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_PATH);
$slug = trim((string)$path, '/');
$category = rm_categories()[$slug] ?? null;
if (!$category) { http_response_code(404); header('Content-Type: text/plain; charset=UTF-8'); echo 'Nie znaleziono kategorii.'; exit; }
if ($path !== '/'.$slug.'/') { header('Location: https://rentmor.pl/'.$slug.'/', true, 301); exit; }
$raw = @file_get_contents(__DIR__ . '/data.json');
$catalogue = $raw === false ? null : json_decode($raw, true);
if (!is_array($catalogue)) { http_response_code(503); header('Retry-After: 300'); echo 'Katalog jest chwilowo niedostępny. Zadzwoń: 733 891 281.'; exit; }
$items = rm_category_items($catalogue, $category['key']);
// Category cards reuse the same versioned WebP manifest as product pages.
// A missing or partial manifest falls back to existing original images.
$manifestRaw = @file_get_contents(__DIR__ . '/rentmor-image-manifest.json');
$manifestParsed = is_string($manifestRaw) ? json_decode($manifestRaw, true) : null;
$imageManifest = is_array($manifestParsed) ? $manifestParsed : [];
$url = 'https://rentmor.pl/'.$slug.'/';
$list = []; $position = 0;
foreach ($items as $productSlug=>$item) $list[] = ['@type'=>'ListItem', 'position'=>++$position, 'url'=>'https://rentmor.pl/'.$productSlug.'/', 'name'=>$item['name']];
header('Content-Type: text/html; charset=UTF-8');
header('X-Content-Type-Options: nosniff');
$e = 'rm_category_escape';
?>
<!doctype html>
<html lang="pl"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><?= $e($category['title']) ?></title><meta name="description" content="<?= $e($category['description']) ?>">
<link rel="canonical" href="<?= $url ?>"><meta name="robots" content="index, follow">
<link rel="icon" href="/assets/rm-20261030-64.png"><link rel="stylesheet" href="/seo-structure.css?v=20261103-unified-header">
<meta property="og:type" content="website"><meta property="og:title" content="<?= $e($category['title']) ?>"><meta property="og:description" content="<?= $e($category['description']) ?>"><meta property="og:url" content="<?= $url ?>"><meta property="og:image" content="https://rentmor.pl/assets/rm-20261030-512.png">
<script type="application/ld+json"><?= rm_category_json(['@context'=>'https://schema.org','@type'=>'CollectionPage','name'=>$category['heading'],'url'=>$url,'mainEntity'=>['@type'=>'ItemList','numberOfItems'=>count($list),'itemListElement'=>$list]]) ?></script>
<script type="application/ld+json"><?= rm_category_json(['@context'=>'https://schema.org','@type'=>'BreadcrumbList','itemListElement'=>[['@type'=>'ListItem','position'=>1,'name'=>'RentMor','item'=>'https://rentmor.pl/'],['@type'=>'ListItem','position'=>2,'name'=>$category['name'],'item'=>$url]]]) ?></script>
</head><body class="rm-category-page">
<a class="rm-skip-link" href="#rentmor-category-main">Przejdź do treści</a>
<?= rm_public_site_header() ?>
<main id="rentmor-category-main" tabindex="-1"><nav aria-label="Okruszki"><a href="/">Strona główna</a> / <?= $e($category['name']) ?></nav>
<h1><?= $e($category['heading']) ?></h1><p><?= $e($category['intro']) ?></p>
<?php if ($category['key']==='budowlane') echo rm_task_equipment_links($catalogue); ?>
<?php $featuredSetSlug = 'zestaw-szlifierka-stanley-zyrafa-do-gladzi-i-odkurzacz-bosch-metabo'; if (isset($items[$featuredSetSlug])): ?>
<p class="rm-seo-section">Szukasz zestawu do szlifowania gładzi? <a href="/<?= $e($featuredSetSlug) ?>/">Wynajem żyrafy z odkurzaczem w Krakowie</a> — zobacz wyposażenie, zdjęcia i cenę zestawu.</p>
<?php endif; ?>
<section class="rm-seo-section" aria-labelledby="category-offers"><h2 id="category-offers">Urządzenia i ceny wynajmu</h2>
<p>Dostępność na wybrany termin potwierdzamy przy rezerwacji. Kaucję i ewentualne koszty dodatków sprawdzisz w szczegółach oferty.</p>
<?php if (!$items): ?><p>Obecnie nie ma urządzeń w tej kategorii. <a href="/">Zobacz cały katalog</a>.</p><?php endif; ?>
<div class="rm-seo-grid">
<?php foreach ($items as $productSlug=>$item): $rawImage = rm_related_image($item); [$image, $srcset] = rm_category_responsive_image($rawImage, $imageManifest); $price = rm_related_price($item); ?>
<article class="rm-seo-card"><a href="/<?= $e($productSlug) ?>/">
<?php if ($image !== ''): ?><img src="<?= $e($image) ?>" alt="<?= $e($item['name']) ?>" width="300" height="180" loading="lazy" decoding="async"<?php if ($srcset !== ''): ?> srcset="<?= $e($srcset) ?>" sizes="(max-width: 600px) calc(100vw - 72px), (max-width: 1120px) 48vw, 310px"<?php endif; ?>><?php endif; ?>
<h3><?= $e($item['name']) ?></h3></a>
<?php if ($price !== ''): ?><p class="rm-seo-price"><?= $e($price) ?></p><?php endif; ?>
<a href="/<?= $e($productSlug) ?>/">Wynajem: <?= $e($item['name']) ?></a></article>
<?php endforeach; ?></div></section>
<section class="rm-seo-section"><h2>Jak dobrać sprzęt?</h2><p><?= $e($category['advice']) ?></p>
<h2>Wynajem sprzętu dla osób prywatnych i firm</h2><p>W RentMor w Krakowie można wynająć sprzęt zarówno do jednorazowego remontu, jak i prac realizowanych przez wykonawcę. Cena podana przy każdym urządzeniu to stawka podstawowa za dobę. Osprzęt, opłaty dodatkowe i kaucję sprawdzisz w szczegółach konkretnej oferty.</p>
<h3>Czy sprzęt można odebrać tego samego dnia?</h3><p>Termin zależy od dostępności urządzenia. Skontaktuj się z nami przed przyjazdem — potwierdzimy możliwość odbioru i godzinę. Nie prowadzimy odbiorów bez wcześniejszego ustalenia terminu.</p>
<h2>Odbiór w Krakowie, Czyżynach</h2><p>Odbiór osobisty przy ul. Woźniców po ustaleniu terminu. Dokładny adres podajemy przy rezerwacji. Transport zapewniasz we własnym zakresie. Przy odbiorze podpisujesz umowę i wpłacasz kaucję wskazaną w ofercie.</p><p><a href="tel:+48733891281">Zadzwoń i ustal termin: 733 891 281</a> · <a href="/zasady.php">Zasady wynajmu</a></p></section>
</main><footer>RentMor.pl — Kraków Czyżyny · <a href="/">Wszystkie urządzenia</a> · <a href="/polityka-prywatnosci.php">Polityka prywatności</a> · <button type="button" class="rm-cookie-settings-button" data-rm-cookie-settings>Ustawienia cookies</button></footer>
<script src="/cookie-consent.js" defer></script></body></html>

