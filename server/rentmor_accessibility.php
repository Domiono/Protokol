<?php
declare(strict_types=1);

/** Small, idempotent WCAG improvements to public HTML only. No form, stats or rental writes. */
function rm_accessibility_html(string $html, bool $isHome): string {
    $oldSettings = '<a href="#ustawienia-cookies" data-rm-cookie-settings>Ustawienia cookies</a>';
    if (substr_count($html, $oldSettings) === 1) {
        // Opening settings is an action, not a navigation to a nonexistent #fragment.
        $html = str_replace($oldSettings,
            '<button type="button" class="rm-cookie-settings-button" data-rm-cookie-settings>Ustawienia cookies</button>',
            $html);
    }
    // Use the already-published smaller wordmark also on offer pages.
    // The original image stays on the server as an unmodified fallback.
    $html = preg_replace_callback('~<img\b[^>]*\balt="RentMor\.pl"[^>]*>~i',
        static function (array $match): string {
            $tag = $match[0];
            $tag = str_replace('/rentmor-logo.png?v=rm-20261030-wordmark-v2',
                '/assets/rentmor-logo-mobile-20261103.webp', $tag);
            if (!preg_match('~\bwidth\s*=~i', $tag)) $tag = substr_replace($tag, ' width="224"', -1, 0);
            if (!preg_match('~\bheight\s*=~i', $tag)) $tag = substr_replace($tag, ' height="48"', -1, 0);
            return $tag;
        }, $html, 1);

    // Give keyboard users a visible-on-focus route around the header.
    if (!str_contains($html, 'class="rm-skip-link"')) {
        $target = $isHome && str_contains($html, 'id="searchInput"') ? 'searchInput' : '';
        if ($target === '' && preg_match('~<h1\b[^>]*>~i', $html, $heading) === 1) {
            if (preg_match('~\bid=["\x27]([^"\x27]+)["\x27]~i', $heading[0], $idMatch)) {
                $target = $idMatch[1];
            } else {
                $target = 'rentmor-main-heading';
                $html = preg_replace_callback('~<h1\b[^>]*>~i',
                    static fn($m) => substr_replace($m[0], ' id="rentmor-main-heading" tabindex="-1"', -1, 0),
                    $html, 1);
            }
        }
        if ($target !== '') {
            $html = preg_replace_callback('~<body\b[^>]*>~i',
                static fn($m) => $m[0] . '<a class="rm-skip-link" href="#' . htmlspecialchars($target, ENT_QUOTES, 'UTF-8') . '">Przejdź do treści</a>',
                $html, 1);
        }
    }

    if (!$isHome) {
        // The detail description belongs under H1 as H2, and equipment
        // included in the kit is a subordinate H3 (not a skipped H4).
        if (str_contains($html, 'rentmor-product-description')) {
            $html = preg_replace_callback('~<h3\b([^>]*)>((?:(?!</h3>).)*?Opis:\s*)</h3>~is',
                static fn($m) => '<h2' . $m[1] . '>' . $m[2] . '</h2>', $html, 1);
            $html = preg_replace_callback('~<h4\b([^>]*)>((?:(?!</h4>).)*?W zestawie:\s*)</h4>~is',
                static fn($m) => '<h3' . $m[1] . '>' . $m[2] . '</h3>', $html, 1);
        }
        return $html;
    }

    // Explicit label is useful even when the input placeholder disappears on typing.
    $search = '~<input\b[^>]*\bid="searchInput"[^>]*>~is';
    if (preg_match_all($search, $html, $inputs) === 1
        && !str_contains($inputs[0][0], 'aria-label=')
        && !str_contains($html, 'for="searchInput"')) {
        $html = preg_replace_callback($search, static fn($m) =>
            '<label for="searchInput" class="rm-sr-only">Szukaj sprzętu do wynajęcia</label>' . $m[0],
            $html, 1);
    }

    // The page has an H1 then a grid of H3 product titles; provide their H2 context.
    $searchPanel = '<div class="max-w-4xl mx-auto px-4 -mt-8 relative z-10 mb-10">';
    if (substr_count($html, $searchPanel) === 1 && !str_contains($html, 'id="rentmor-catalogue-heading"')) {
        $html = str_replace($searchPanel,
            '<h2 class="rm-sr-only" id="rentmor-catalogue-heading">Sprzęt dostępny do wynajęcia</h2>' . "\n" . $searchPanel,
            $html);
    }

    // The existing JS updates aria-pressed after a click; set the initial state in HTML too.
    // aria-label also removes decorative emoji from the accessible name, keeping sighted text.
    $buttonPattern = '~<button\b(?=[^>]*\bclass="[^"]*\bfilter-btn\b)[^>]*>~is';
    $labels = [
        'all' => 'Filtr: wszystkie urządzenia',
        'budowlane' => 'Filtr: sprzęt budowlany',
        'ogrodnicze' => 'Filtr: sprzęt ogrodniczy',
        'czyszczace' => 'Filtr: sprzęt czyszczący',
    ];
    if (preg_match_all($buttonPattern, $html, $buttons) === 4) {
        $html = preg_replace_callback($buttonPattern, static function($match) use ($labels): string {
            $tag = $match[0];
            if (!preg_match('~filterProducts\(\s*\x27([a-z]+)\x27~', $tag, $found)
                || !isset($labels[$found[1]])) return $tag;
            $name = $found[1];
            $attrs = '';
            if (!str_contains($tag, 'aria-pressed=')) {
                $attrs .= ' aria-pressed="' . ($name === 'all' ? 'true' : 'false') . '"';
            }
            return $attrs === '' ? $tag : substr($tag, 0, -1) . $attrs . '>';
        }, $html);
    }
    return $html;
}
