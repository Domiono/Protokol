<?php
declare(strict_types=1);
require_once __DIR__ . '/browser_stats.php';

/** Every guide using the common public template is included, without an article list. */
function rm_stats_auto_public_page(): void {
    $script = (string)($_SERVER['SCRIPT_FILENAME'] ?? '');
    if (realpath(dirname($script)) !== realpath(__DIR__)) return;
    $name = basename($script);
    if (preg_match('/^poradnik-[a-z0-9-]+\.php$/D', $name)) rm_stats_public_page('/'.$name);
}

/** Called before output, only by public page templates with a canonical path. */
function rm_stats_public_page(string $page): void {
    static $registered = false;
    if ($registered) return;
    if (!rm_stats_has_consent() || rm_stats_is_automated()) return;
    if (session_status() !== PHP_SESSION_ACTIVE && !session_start()) return;
    $host = strtolower((string)parse_url((string)($_SERVER['HTTP_REFERER'] ?? ''), PHP_URL_HOST));
    $source = 'other_ext';
    if ($host === '') $source = 'direct';
    elseif (in_array($host, ['rentmor.pl','www.rentmor.pl'], true)) $source = 'internal';
    elseif (preg_match('~(^|\.)(google\.[a-z.]+|bing\.com)$~', $host)) $source = 'google_bing_search';
    elseif (preg_match('~(^|\.)(facebook\.com|instagram\.com|t\.co)$~', $host)) $source = 'social';
    elseif (preg_match('~(^|\.)olx\.pl$~', $host)) $source = 'olx';
    $token = rm_stats_prepare_pageview('', $source, $page);
    if ($token === '') return;
    $registered = true;
    ob_start(static function (string $html) use ($token): string {
        if (http_response_code() >= 300 || stripos($html, '</body>') === false) return $html;
        $attribute = ' data-view-token="'.htmlspecialchars($token, ENT_QUOTES, 'UTF-8').'"';
        // Reuse the existing script to avoid duplicate heartbeats and phone handlers.
        $pattern = '~<script\b[^>]*\bsrc=["\x27]/online\.js(?:\?[^"\x27]*)?["\x27][^>]*>~i';
        if (preg_match($pattern, $html)) {
            return preg_replace_callback($pattern, static fn($m) => substr($m[0],0,-1).$attribute.'>', $html, 1);
        }
        return str_ireplace('</body>', '<script src="/online.js" defer'.$attribute.'></script></body>', $html);
    });
}

function rm_stats_page_name(string $key, array $products): string {
    if (strpos($key, 'product:') === 0) {
        $id = substr($key, 8);
        foreach ($products as $product) if ((string)($product['id'] ?? '') === $id) return (string)($product['name'] ?? $id);
        return 'Oferta '.$id.' (poza katalogiem)';
    }
    $names = ['/' => 'Strona główna', '/zasady.php' => 'Zasady wynajmu', '/polityka-prywatnosci.php' => 'Polityka prywatności'];
    $names += [
        '/sprzet-budowlany/' => 'Kategoria: sprzęt budowlany',
        '/sprzet-ogrodniczy/' => 'Kategoria: sprzęt ogrodniczy',
        '/sprzet-czyszczacy/' => 'Kategoria: sprzęt czyszczący',
        '/poradnik-jaki-papier-scierny-do-zyrafy.php' => 'Poradnik: papier do żyrafy',
        '/poradnik-pranie-tapicerki-karcher-puzzi.php' => 'Poradnik: pranie tapicerki',
        '/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php' => 'Poradnik: malowanie agregatem',
    ];
    if (isset($names[$key])) return $names[$key];
    if (preg_match('~^/poradnik-([a-z0-9-]+)\.php$~D', $key, $m)) return 'Poradnik: '.str_replace('-', ' ', $m[1]);
    return $key;
}
