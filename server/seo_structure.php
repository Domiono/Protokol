<?php
declare(strict_types=1);
require_once __DIR__ . '/rental_categories.php';
require_once __DIR__ . '/rentmor_accessibility.php';

function rm_home_sections(): string {
    return '<section id="wynajem-sprzetu-krakow" class="rm-seo-section" data-rm-home-structure="v1">'
        . '<h2>Wypożycz sprzęt w Krakowie, Czyżynach</h2>'
        . '<p>RentMor to lokalna wypożyczalnia sprzętu w Krakowie, w dzielnicy Czyżyny. Wynajmujemy urządzenia do remontu, prac ogrodowych i sprzątania osobom prywatnym oraz firmom. Zobacz <a href="/sprzet-budowlany/">sprzęt budowlany na wynajem</a>, porównaj ceny dobowe i wybierz model odpowiedni do pracy.</p>'
        . '<h3>Jak wypożyczyć sprzęt?</h3><ol><li>Wybierz urządzenie i sprawdź cenę dobową, kaucję oraz dodatki.</li><li>Zadzwoń i ustal termin odbioru oraz zwrotu.</li><li>Przy odbiorze podpisz umowę i wpłać kaucję. Transport zapewniasz we własnym zakresie.</li></ol>'
        . '<p>Odbiór osobisty w Krakowie przy ul. Woźniców, po wcześniejszym uzgodnieniu terminu. Dokładny adres przekazujemy przy rezerwacji. Nie trzeba prowadzić firmy, aby wynająć sprzęt; obsługujemy również wykonawców.</p>'
        . '<p><a href="tel:+48733891281">Zadzwoń: 733 891 281</a> · <a href="/zasady.php">Zasady wynajmu</a></p></section>'
        . '<section id="poradniki" class="rm-seo-section"><h2>Poradniki przed rozpoczęciem pracy</h2><div class="rm-seo-grid">'
        . '<article class="rm-seo-card"><h3><a href="/poradnik-jaki-papier-scierny-do-zyrafy.php">Jaki papier ścierny do żyrafy?</a></h3><p>Dobór gradacji i najczęstsze błędy przy szlifowaniu gładzi.</p></article>'
        . '<article class="rm-seo-card"><h3><a href="/poradnik-pranie-tapicerki-karcher-puzzi.php">Jak prać tapicerkę Puzzi 10/1?</a></h3><p>Przygotowanie tapicerki, pranie i suszenie.</p></article>'
        . '<article class="rm-seo-card"><h3><a href="/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php">Jak malować agregatem i dobrać dyszę?</a></h3><p>Dysze 511, 517 i 521, przygotowanie urządzenia i czyszczenie.</p></article>'
        . '</div></section>';
}

/** Optimizes only already-approved images on the public homepage; preserves the artwork. */
function rm_speed_up_home(string $html): string {
    $oldHero = '/assets/rentmor-blueprint-mobile-20261102.webp';
    $currentHero = '/assets/rentmor-blueprint-mobile-20261103.webp';
    $lighterHero = '/assets/rentmor-blueprint-mobile-20261103-lite.webp';
    // The preload and the CSS background MUST always point to the same image.
    // Convert either known previous production variant; unknown markup is left alone.
    if (str_contains($html, 'data-rentmor-blueprint="v1"')) {
        foreach ([$oldHero, $currentHero] as $previous) {
            if (substr_count($html, $previous) >= 2) {
                $html = str_replace($previous, $lighterHero, $html);
            }
        }
        $preload = '<link rel="preload" as="image" href="' . $lighterHero
            . '" media="(max-width: 899px)" fetchpriority="high">';
        // Start the network request immediately when the head is parsed,
        // before the large inline stylesheet. Keep just one preload.
        if (substr_count($html, $preload) === 1 &&
            preg_match_all('~<head\b[^>]*>~i', $html) === 1) {
            $html = str_replace($preload, '', $html);
            $html = preg_replace_callback('~<head\b[^>]*>~i',
                static fn($match) => $match[0] . "\n" . $preload, $html, 1);
        }
    }
    $oldLogo = '/rentmor-logo.png?v=rm-20261030-wordmark-v2';
    $newLogo = '/assets/rentmor-logo-mobile-20261103.webp';
    if (str_contains($html, 'src="' . $oldLogo . '"')) {
        $html = str_replace('src="' . $oldLogo . '"', 'src="' . $newLogo . '"', $html);
        $html = str_replace('srcset="' . $oldLogo . ' 1x"', 'srcset="' . $newLogo . ' 1x"', $html);
    }
    return $html;
}

/** Only rendered public HTML: never modifies product data, sessions or reservations. */
function rentmor_seo_structure(string $html): string {
    if (!str_contains($html, '</head>') || str_contains($html, '<!-- rentmor-seo-structure-v1 -->')) return $html;
    $isHome = (bool)preg_match('~<link\b[^>]*rel=["\x27]canonical["\x27][^>]*href=["\x27]https://rentmor\.pl/["\x27]~i', $html);
    if ($isHome) {
        $html = rm_speed_up_home($html);
        // Replace only the three known sections. On template drift leave all content intact.
        $section = '~<section\b[^>]*>(?:(?!<section\b|</section>).)*?</section>~is';
        $counts = ['intro'=>0, 'guide'=>0, 'local'=>0];
        $updated = preg_replace_callback($section, static function ($m) use (&$counts) {
            if (preg_match('~<h2\b[^>]*>\s*Wynajem sprzętu budowlanego i ogrodniczego w Krakowie\s*</h2>~u', $m[0]) && str_contains($m[0], 'Szukasz sprzętu')) {
                ++$counts['intro']; return rm_home_sections();
            }
            if (str_contains($m[0], 'id="poradniki"')) { ++$counts['guide']; return ''; }
            if (str_contains($m[0], 'id="wynajem-sprzetu-krakow"')) { ++$counts['local']; return ''; }
            return $m[0];
        }, $html);
        if ($counts === ['intro'=>1,'guide'=>1,'local'=>1] && is_string($updated)) $html = $updated;
    }
    $changedProduct = false;
    $html = preg_replace_callback('~(<script\b[^>]*type=["\x27]application/ld\+json["\x27][^>]*>)(.*?)(</script>)~is', static function ($m) use (&$changedProduct) {
        $node = json_decode($m[2], true);
        if (!is_array($node) || ($node['@type'] ?? '') !== 'Product') return $m[0];
        $offer = $node['offers'] ?? null;
        if (!is_array($offer) || ($offer['@type'] ?? '') !== 'Offer'
            || !str_starts_with((string)($offer['url'] ?? ''), 'https://rentmor.pl/')
            || !is_numeric($offer['price'] ?? null) || (float)$offer['price'] <= 0
            || ($offer['priceCurrency'] ?? '') !== 'PLN') return $m[0];
        $offer['businessFunction'] = 'http://purl.org/goodrelations/v1#LeaseOut';
        $offer['priceSpecification'] = ['@type'=>'UnitPriceSpecification', 'price'=>$offer['price'],
            'priceCurrency'=>'PLN', 'unitCode'=>'DAY', 'unitText'=>'doba',
            'referenceQuantity'=>['@type'=>'QuantitativeValue','value'=>1,'unitCode'=>'DAY']];
        // The catalogue is not a date-specific stock feed. Do not promise availability.
        unset($offer['availability']);
        $offer['description'] = 'Cena podstawowa wynajmu za jedną dobę. Kaucja i ewentualne dodatki zgodnie z opisem oferty. Dostępność na termin potwierdzamy przy rezerwacji.';
        $node['offers'] = $offer;
        $changedProduct = true;
        return $m[1] . rm_category_json($node) . $m[3];
    }, $html);
    if ($changedProduct) {
        $html = preg_replace('~(</h1>)~i', '$1<p class="rm-seo-muted" data-rm-rental-terms="v1">Cena podstawowa za dobę wynajmu. Kaucja i ewentualne dodatki zgodnie z opisem. Dostępność na termin potwierdzamy przy rezerwacji.</p>', $html, 1);
    }
    $html = rm_add_header_category_menu($html);
    $html = rm_accessibility_html($html, $isHome);
    return str_replace('</head>', '<!-- rentmor-seo-structure-v1 --><link rel="stylesheet" href="/seo-structure.css?v=20261103-wcag"></head>', $html);
}
