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
    if (!$isHome) return $html;

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
            if (!str_contains($tag, 'aria-label=')) {
                $attrs .= ' aria-label="' . $labels[$name] . '"';
            }
            return $attrs === '' ? $tag : substr($tag, 0, -1) . $attrs . '>';
        }, $html);
    }
    return $html;
}
