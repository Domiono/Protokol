<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
function rm_phone_reply(int $status): never { http_response_code($status); echo json_encode(['ok'=>$status===200]); exit; }
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') rm_phone_reply(405);
$origin = (string)($_SERVER['HTTP_ORIGIN'] ?? '');
if (($origin !== '' && !in_array(strtolower((string)parse_url($origin, PHP_URL_HOST)), ['rentmor.pl','www.rentmor.pl'], true)) || ($_SERVER['HTTP_SEC_FETCH_SITE'] ?? '') === 'cross-site') rm_phone_reply(403);
require_once __DIR__ . '/traffic_filter.php';
require_once __DIR__ . '/phone_events.php';
session_start();
if (rm_stats_is_internal() || rm_stats_is_automated()) rm_phone_reply(200);
$action = $_POST['action'] ?? '';
$id = $_POST['source_id'] ?? '';
if (!is_string($id) || strlen($id)>100 || !in_array($action, ['reveal','call'], true)) rm_phone_reply(400);
$source = 'Strona główna';
if ($id !== 'home') {
    $products = json_decode((string)@file_get_contents(__DIR__ . '/data.json'), true);
    $source = null;
    foreach (is_array($products) ? $products : [] as $product) {
        if ((string)($product['id'] ?? '') === $id) { $source = (string)($product['name'] ?? $id); break; }
    }
    if ($source === null) rm_phone_reply(400);
}
// Rate limit only pathological bursts; ordinary repeated clicks remain distinct events.
$minute = (int)floor(time()/60);
if (($_SESSION['rm_phone_minute'] ?? 0) !== $minute) { $_SESSION['rm_phone_minute']=$minute; $_SESSION['rm_phone_count']=0; }
if (($_SESSION['rm_phone_count'] ?? 0)>=120) rm_phone_reply(429);
$_SESSION['rm_phone_count'] = ($_SESSION['rm_phone_count'] ?? 0)+1;
session_write_close();
$now = new DateTimeImmutable('now', new DateTimeZone('Europe/Warsaw'));
rm_phone_reply(rm_phone_events_append(['at'=>$now->format('Y-m-d H:i:s'), 'action'=>$action, 'source_id'=>$id, 'source'=>$source]) ? 200 : 503);
