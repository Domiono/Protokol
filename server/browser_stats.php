<?php
declare(strict_types=1);
require_once __DIR__ . '/traffic_filter.php';

// Mint a one-use page token. A GET never increments the statistics itself.
function rm_stats_prepare_pageview(string $productId, string $source): string {
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET' || http_response_code() === 404 || rm_stats_is_internal() || rm_stats_is_automated()) return '';
    $dest = $_SERVER['HTTP_SEC_FETCH_DEST'] ?? '';
    if ($dest !== '' && !in_array($dest, ['document','iframe'], true)) return '';
    if (strtolower((string)($_SERVER['HTTP_X_REQUESTED_WITH'] ?? '')) === 'xmlhttprequest') return '';
    $pending = $_SESSION['rm_view_tokens'] ?? [];
    if (!is_array($pending)) $pending = [];
    $now = time();
    foreach ($pending as $key=>$value) if (($value['at'] ?? 0) < $now-600) unset($pending[$key]);
    $token = bin2hex(random_bytes(24));
    $pending[$token] = ['at'=>$now,'product'=>$productId,'source'=>$source];
    $_SESSION['rm_view_tokens'] = array_slice($pending,-30,null,true);
    return $token;
}

function rm_stats_confirm_pageview(string $token, ?string $path = null): int {
    if (rm_stats_is_internal() || rm_stats_is_automated()) return 200;
    $pending = $_SESSION['rm_view_tokens'][$token] ?? null;
    if (!is_array($pending) || ($pending['at'] ?? 0)<time()-600) return 400;
    $key = 'page:' . (string)$pending['product']; // Ignore UTM and alternate URLs for the same offer.
    $recent = $_SESSION['rm_browser_recent'] ?? [];
    if (!is_array($recent)) $recent = [];
    $now = time();
    foreach ($recent as $page=>$at) if (!is_int($at) || $at<=$now-30) unset($recent[$page]);
    if (isset($recent[$key])) { unset($_SESSION['rm_view_tokens'][$token]); return 200; }
    $fp = @fopen($path ?? __DIR__ . '/stats.json','c+');
    if (!$fp) return 503;
    if (!flock($fp,LOCK_EX)) { fclose($fp); return 503; }
    $original = stream_get_contents($fp);
    $stats = $original === '' ? [] : json_decode($original,true);
    if (!is_array($stats)) { flock($fp,LOCK_UN); fclose($fp); return 503; }
    $date = new DateTimeImmutable('now',new DateTimeZone('Europe/Warsaw'));
    $today = $date->format('Y-m-d');
    $stats['total_views'] = (int)($stats['total_views'] ?? 0)+1;
    $stats['days'][$today] = (int)($stats['days'][$today] ?? 0)+1;
    $stats['browser_filter_since'] = $stats['browser_filter_since'] ?? $date->format(DATE_ATOM);
    if (empty($_SESSION['v_rm'])) {
        $stats['unique_visitors'] = (int)($stats['unique_visitors'] ?? 0)+1;
        $source = (string)$pending['source'];
        if (!in_array($source,['google_bing_search','social','olx','direct','other_ext','internal'],true)) $source='other_ext';
        $stats['sources'][$today][$source] = (int)($stats['sources'][$today][$source] ?? 0)+1;
    }
    $product = (string)$pending['product'];
    if ($product !== '') $stats['product_views'][$product] = (int)($stats['product_views'][$product] ?? 0)+1;
    $json = json_encode($stats,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
    rewind($fp);
    $ok = $json !== false && fwrite($fp,$json) === strlen($json) && ftruncate($fp,strlen($json)) && fflush($fp);
    if (!$ok) { rewind($fp); fwrite($fp,$original); ftruncate($fp,strlen($original)); fflush($fp); }
    flock($fp,LOCK_UN); fclose($fp);
    if (!$ok) return 503;
    $_SESSION['v_rm'] = true;
    $recent[$key] = $now;
    $_SESSION['rm_browser_recent'] = array_slice($recent,-100,null,true);
    unset($_SESSION['rm_view_tokens'][$token]);
    return 200;
}
