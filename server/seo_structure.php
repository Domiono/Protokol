<?php
declare(strict_types=1);
require_once __DIR__ . '/rental_categories.php';
require_once __DIR__ . '/rentmor_accessibility.php';

/** Replace the small public icon set instead of downloading the entire icon font.
 * Unknown icons retain Font Awesome, so editorial additions never disappear.
 * Inline scripts are deliberately excluded from rewriting.
 */
function rm_light_public_assets(string $html): string {
    $icons = [
        'fa-phone' => '<path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 2 .7 2.9a2 2 0 0 1-.5 2.1L8 10a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.5c.9.3 1.9.6 2.9.7a2 2 0 0 1 1.7 2z"/>',
        'fa-location-dot' => '<path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0z"/><circle cx="12" cy="10" r="2.5"/>',
        'fa-search' => '<circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/>',
        'fa-circle-info' => '<circle cx="12" cy="12" r="10"/><path d="M12 11v6m0-10v.1"/>',
        'fa-expand' => '<path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5"/>',
    ];
    $parts = preg_split('~(<script\b[^>]*>.*?</script>)~is', $html, -1, PREG_SPLIT_DELIM_CAPTURE);
    if (!is_array($parts)) return $html;
    $remaining = false;
    foreach ($parts as $index => $part) {
        if ($index % 2 !== 0) continue;
        $parts[$index] = preg_replace_callback('~<i\b([^>]*)>\s*</i>~is', static function ($m) use ($icons) {
            if (!preg_match('~\bclass="([^"]*)"~', $m[1], $classes)) return $m[0];
            $tokens = preg_split('/\s+/', trim($classes[1]));
            $names = array_values(array_intersect(array_keys($icons), $tokens));
            // Preserve unusual labelled/interactive elements rather than drop attributes.
            $rest = preg_replace('~\s*(?:class|aria-hidden)="[^"]*"~', '', $m[1]);
            if (count($names) !== 1 || trim($rest) !== '') return $m[0];
            $classes = array_filter($tokens, static fn($v) => !str_starts_with($v, 'fa-') && $v !== 'fas' && $v !== 'fa');
            return '<svg class="rm-inline-icon ' . htmlspecialchars(implode(' ', $classes), ENT_QUOTES, 'UTF-8')
                . '" width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">'
                . $icons[$names[0]] . '</svg>';
        }, $part);
        if (preg_match('~class=["\x27][^"\x27]*\b(?:fa[srb]?|fa-[\w-]+)\b~', $parts[$index])) $remaining = true;
        // Below-the-fold catalogue previews must not compete with the hero/detail LCP.
        $parts[$index] = preg_replace_callback('~<img\b(?=[^>]*data-rm-image-kind="card")[^>]*>~is', static function ($m) {
            if (preg_match('~\bfetchpriority\s*=~i', $m[0])) return $m[0];
            return substr_replace($m[0], ' fetchpriority="low"', -1, 0);
        }, $parts[$index]);
    }
    $html = implode('', $parts);
    if (!$remaining) {
        $html = preg_replace('~<link\b[^>]*href="https://cdnjs\.cloudflare\.com/ajax/libs/font-awesome/6\.4\.0/css/all\.min\.css"[^>]*>~i', '', $html);
        $html = preg_replace('~<noscript>\s*</noscript>~i', '', $html);
    }
    if (str_contains($html, 'class="rm-inline-icon ') && !str_contains($html, 'id="rm-inline-icons-css"')) {
        $html = str_replace('</head>', '<style id="rm-inline-icons-css">.rm-inline-icon{display:inline-block;vertical-align:-.125em;flex-shrink:0}</style></head>', $html);
    }
    return $html;
}

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

/** Preload only a product's first, already-optimized gallery photo.
 * Matches the existing srcset selection without downloading original JPG/PNG.
 * Do not preload additional slides, because they are hydrated on demand.
 */
function rm_product_lcp_preload(string $html, array $manifest): string {
    if (str_contains($html, 'data-rm-lcp-preload="v1"')) return $html;
    if (preg_match_all('~<img\b(?=[^>]*\bdata-rm-image-kind="detail")[^>]*>~is', $html, $images) !== 1) return $html;
    $tag = $images[0][0];
    $sources = [];
    if (preg_match('~\bsrcset\s*=\s*(["\x27])(.*?)\1~is', $tag, $match)) {
        // HTML optimizer may already have populated srcset, depending on buffer order.
        $sources = array_map('trim', explode(',', html_entity_decode($match[2], ENT_QUOTES | ENT_HTML5, 'UTF-8')));
    } elseif (preg_match('~\bsrc\s*=\s*(["\x27])(.*?)\1~is', $tag, $match)) {
        $path = parse_url(html_entity_decode($match[2], ENT_QUOTES | ENT_HTML5, 'UTF-8'), PHP_URL_PATH);
        if (!is_string($path)) return $html;
        $asset = null;
        if (preg_match('~^/uploads/([A-Za-z0-9_.-]+\.(?:jpe?g|png|webp))$~i', $path, $source)) {
            $asset = $manifest[$source[1]] ?? null;
        } elseif (preg_match('~^/uploads/rentmor-webp/[A-Za-z0-9_.-]+\.webp$~', $path)) {
            // Some page templates render the already optimized public WebP as src.
            // Reverse-match a *declared* variant in the safe manifest, never a free URL.
            foreach ($manifest as $candidate) {
                if (!is_array($candidate)) continue;
                foreach (['card', 'detail'] as $kind) {
                    if (($candidate[$kind]['url'] ?? null) === $path) {
                        $asset = $candidate;
                        break 2;
                    }
                }
            }
        }
        if (!is_array($asset)) return $html;
        foreach (['thumb', 'small', 'card', 'detail'] as $kind) {
            $variant = $asset[$kind] ?? null;
            if (!is_array($variant)) continue;
            $url = $variant['url'] ?? null;
            $width = $variant['width'] ?? null;
            if (!is_string($url) || !preg_match('~^/uploads/rentmor-webp/[A-Za-z0-9_.-]+\.webp$~', $url)
                || !is_numeric($width) || (int)$width < 1 || (int)$width > 5000) continue;
            $sources[] = $url . ' ' . (int)$width . 'w';
        }
    }
    if (count($sources) < 2) return $html;
    foreach ($sources as $source) {
        if (!preg_match('~^/uploads/rentmor-webp/[A-Za-z0-9_.-]+\.webp [1-9][0-9]{0,3}w$~', $source)) return $html;
    }
    if (preg_match_all('~<head\b[^>]*>~i', $html) !== 1) return $html;
    $link = '<link rel="preload" as="image" imagesrcset="'
        . htmlspecialchars(implode(', ', $sources), ENT_QUOTES, 'UTF-8')
        . '" imagesizes="(max-width: 768px) 100vw, 900px" fetchpriority="high" data-rm-lcp-preload="v1">';
    return (string)preg_replace_callback('~<head\b[^>]*>~i',
        static fn($m) => $m[0] . "\n" . $link, $html, 1);
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
        // Match any existing preload for this exact image, preserving media
        // and fetchpriority regardless of HTML attribute order or other attributes.
        $preloadPattern = '~<link\b(?=[^>]*\brel=["\x27]preload["\x27])'
            . '(?=[^>]*\bhref=["\x27]' . preg_quote($lighterHero, '~') . '["\x27])[^>]*>~i';
        if (preg_match_all($preloadPattern, $html, $preloads) === 1
            && preg_match_all('~<head\b[^>]*>~i', $html) === 1) {
            $preload = $preloads[0][0];
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
        $description = 'Wypożyczalnia sprzętu budowlanego, ogrodniczego i czyszczącego w Krakowie, Czyżynach. Sprawdź ceny wynajmu i zadzwoń, aby ustalić termin.';
        $html = preg_replace('~<meta\b(?=[^>]*\bname="description")[^>]*>~i', '<meta name="description" content="' . $description . '">', $html, 1);
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
        $manifestFile = __DIR__ . '/rentmor-image-manifest.json';
        $manifestRaw = is_file($manifestFile) ? @file_get_contents($manifestFile) : false;
        $imageManifest = is_string($manifestRaw) ? json_decode($manifestRaw, true) : null;
        if (is_array($imageManifest)) $html = rm_product_lcp_preload($html, $imageManifest);
    }
    $html = rm_add_header_category_menu($html);
    $html = rm_accessibility_html($html, $isHome);
    $html = rm_light_public_assets($html);
    // This small public stylesheet is render-blocking as a separate request.
    // Inline it after the already-rendered critical styles on home and offers.
    // Category pages keep using the external, cached stylesheet.
    $css = @file_get_contents(__DIR__ . '/seo-structure.css');
    $style = is_string($css) && strlen($css) > 500 && strlen($css) < 16000
        && stripos($css, '</style') === false
        ? '<style id="rm-public-accessibility-css">' . $css . '</style>'
        : '<link rel="stylesheet" href="/seo-structure.css?v=20261103-google-quality">';
    return str_replace('</head>', '<!-- rentmor-seo-structure-v1 -->' . $style . '</head>', $html);
}
