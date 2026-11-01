<?php
/** Public HTML metadata only: no catalogue writes, requests or private data. */
function rentmor_public_metadata(string $html): string {
    if (!preg_match('~<head\b[^>]*>.*?</head>~is', $html, $match)) return $html;
    $head = $match[0];
    if (strpos($head, 'rentmor-sharing-v1') !== false) return $html;
    $titles = [
        'Szlifierka do betonu MSW-FG-250 250 mm – wynajem Kraków | RentMor' => 'Szlifierka MSW-FG-250 do betonu – wynajem Kraków | RentMor',
        'Szlifierka kątowa Bosch 230 mm 2200 W – wynajem Kraków | RentMor' => 'Szlifierka Bosch 230 mm – wynajem Kraków | RentMor',
        'Szlifierka kątowa DeWalt 125 mm 1700 W – wynajem Kraków | RentMor' => 'Szlifierka DeWalt 125 mm – wynajem Kraków | RentMor',
        'Przecinarka do glazury Norton Clipper TR202 – wynajem Kraków | RentMor' => 'Przecinarka Norton TR202 – wynajem Kraków | RentMor',
        'Odkurzacz przemysłowy Bosch GAS 25 L SFC – wynajem Kraków | RentMor' => 'Odkurzacz Bosch GAS 25 L SFC – wynajem Kraków | RentMor',
    ];
    $escape = static fn($s) => htmlspecialchars($s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    $head = preg_replace_callback('~(<title\b[^>]*>)(.*?)(</title>)~is', static function ($m) use ($titles, $escape) {
        $old = trim(html_entity_decode($m[2], ENT_QUOTES | ENT_HTML5, 'UTF-8'));
        return isset($titles[$old]) ? $m[1] . $escape($titles[$old]) . $m[3] : $m[0];
    }, $head);
    $head = preg_replace_callback('~<meta\b[^>]*>~is', static function ($m) use ($titles, $escape) {
        if (!preg_match('~property\s*=\s*[\x22\x27]og:title[\x22\x27]~i', $m[0])) return $m[0];
        return preg_replace_callback('~(content\s*=\s*)([\x22\x27])(.*?)\2~is', static function ($a) use ($titles, $escape) {
            $old = html_entity_decode($a[3], ENT_QUOTES | ENT_HTML5, 'UTF-8');
            return isset($titles[$old]) ? $a[1] . $a[2] . $escape($titles[$old]) . $a[2] : $a[0];
        }, $m[0]);
    }, $head);
    $image = 'https://rentmor.pl/assets/rm-20261030-512.png';
    $alt = 'RentMor.pl – wypożyczalnia sprzętu w Krakowie';
    // Select only the main product picture, never a related-offer thumbnail.
    if (preg_match_all('~<img\b[^>]*>~is', $html, $images)) {
        foreach ($images[0] as $tag) {
            if (!preg_match('~data-rm-image-kind=[\x22\x27]detail[\x22\x27]~', $tag)) continue;
            if (preg_match('~\bsrc=[\x22\x27](/uploads/[a-zA-Z0-9._%/-]+\.(?:png|jpe?g|webp))[\x22\x27]~i', $tag, $src)) {
                $image = 'https://rentmor.pl' . $src[1];
                if (preg_match('~\balt=([\x22\x27])(.*?)\1~s', $tag, $a)) $alt = html_entity_decode($a[2], ENT_QUOTES | ENT_HTML5, 'UTF-8');
            }
            break;
        }
    }
    $extra = "\n<!-- rentmor-sharing-v1 -->\n";
    if (!preg_match('~property=[\x22\x27]og:image[\x22\x27]~i', $head)) {
        $extra .= '<meta property="og:image" content="' . $escape($image) . '">' . "\n";
        $extra .= '<meta property="og:image:alt" content="' . $escape($alt) . '">' . "\n";
    }
    // The terms page previously had no Open Graph metadata at all.
    if (!preg_match('~property=[\x22\x27]og:type[\x22\x27]~i', $head)) {
        $extra .= '<meta property="og:type" content="website">' . "\n";
        $extra .= '<meta property="og:title" content="Zasady wynajmu sprzętu | RentMor.pl">' . "\n";
        $extra .= '<meta property="og:url" content="https://rentmor.pl/zasady.php">' . "\n";
        $extra .= '<meta property="og:description" content="Zasady wynajmu sprzętu w RentMor.pl, Kraków Czyżyny.">' . "\n";
    }
    $head = str_replace('</head>', $extra . '</head>', $head);
    return substr_replace($html, $head, strpos($html, $match[0]), strlen($match[0]));
}
