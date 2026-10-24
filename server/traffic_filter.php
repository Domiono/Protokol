<?php
// Statistics-only filtering: never block access to the public site or search indexing.
declare(strict_types=1);

function rm_stats_is_internal(): bool {
    return !empty($_SESSION['logged_in'])
        || ($_COOKIE['rm_stats_exclude'] ?? '') === '1'
        || !empty($_COOKIE['rm_protocol_device']);
}

function rm_stats_remember_owner(): void {
    // This cookie grants no permissions. Keep excluding this browser after logout.
    setcookie('rm_stats_exclude', '1', [
        'expires' => time() + 31536000, 'path' => '/',
        'secure' => !empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off',
        'httponly' => true, 'samesite' => 'Lax',
    ]);
    $_COOKIE['rm_stats_exclude'] = '1';
}

function rm_stats_is_automated(): bool {
    $ua = trim((string)($_SERVER['HTTP_USER_AGENT'] ?? ''));
    if ($ua === '' || preg_match('~bot|crawler|spider|slurp|headless|preview|lighthouse|pagespeed|curl|wget|python|httpclient|http-client|Go-http-client|facebookexternalhit|facebookcatalog|WhatsApp|TelegramBot|uptime|monitor|validator|scrapy|selenium|playwright|puppeteer~i', $ua)) return true;
    $purpose = ($_SERVER['HTTP_SEC_PURPOSE'] ?? '') . ' ' . ($_SERVER['HTTP_PURPOSE'] ?? '');
    return (bool)preg_match('~prefetch|prerender~i', $purpose);
}

function rm_stats_count_pageview(): bool {
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET' || rm_stats_is_internal() || rm_stats_is_automated()) return false;
    if (strtolower((string)($_SERVER['HTTP_X_REQUESTED_WITH'] ?? '')) === 'xmlhttprequest') return false;
    $dest = (string)($_SERVER['HTTP_SEC_FETCH_DEST'] ?? '');
    if ($dest !== '' && !in_array($dest, ['document', 'iframe'], true)) return false;
    // Suppress rapid reloads of the same URL, while counting other offers normally.
    $key = hash('sha256', (string)($_SERVER['REQUEST_URI'] ?? '/'));
    $now = time();
    $recent = $_SESSION['rm_stats_recent'] ?? [];
    if (!is_array($recent)) $recent = [];
    foreach ($recent as $url => $at) {
        if (!is_int($at) || $at <= $now - 30 || $at > $now) unset($recent[$url]);
    }
    if (isset($recent[$key])) return false;
    $recent[$key] = $now;
    $_SESSION['rm_stats_recent'] = array_slice($recent, -100, null, true);
    return true;
}
