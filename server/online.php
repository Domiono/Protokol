<?php
// RentMor online-presence heartbeat. Counts active browser sessions, not page views.
// Visitor IP addresses, URLs and user-agent strings are not persisted.
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('X-Content-Type-Options: nosniff');

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    http_response_code(405);
    header('Allow: POST');
    echo '{"ok":false}';
    exit;
}
$origin = (string)($_SERVER['HTTP_ORIGIN'] ?? '');
if ($origin !== '' && !in_array(strtolower((string)parse_url($origin, PHP_URL_HOST)), ['rentmor.pl','www.rentmor.pl'], true)) {
    http_response_code(403);
    echo '{"ok":false}';
    exit;
}
if (($_SERVER['HTTP_SEC_FETCH_SITE'] ?? '') === 'cross-site') {
    http_response_code(403);
    echo '{"ok":false}';
    exit;
}
$ua = strtolower((string)($_SERVER['HTTP_USER_AGENT'] ?? ''));
if (preg_match('/bot|crawler|spider|headless|preview|lighthouse/', $ua)) {
    echo '{"ok":true}';
    exit;
}
session_start();
$sid = session_id();
session_write_close();
if ($sid === '') {
    http_response_code(500);
    echo '{"ok":false}';
    exit;
}

$path = __DIR__ . '/online_sessions.json';
$fp = @fopen($path, 'c+');
if ($fp === false) {
    http_response_code(503);
    echo '{"ok":false}';
    exit;
}
if (!flock($fp, LOCK_EX)) {
    fclose($fp);
    http_response_code(503);
    echo '{"ok":false}';
    exit;
}
$raw = stream_get_contents($fp);
$sessions = json_decode((string)$raw, true);
if (!is_array($sessions)) $sessions = [];
$now = time();
// The app shows sessions with a heartbeat in the past 95 seconds.
foreach ($sessions as $key => $last) {
    if (!is_int($last) && !is_numeric($last) || (int)$last < $now - 95 || (int)$last > $now + 60) {
        unset($sessions[$key]);
    }
}
$key = hash('sha256', $sid);
$sessions[$key] = $now;
$json = json_encode($sessions, JSON_UNESCAPED_SLASHES);
rewind($fp);
$success = $json !== false && ftruncate($fp, 0) && fwrite($fp, $json) !== false && fflush($fp);
flock($fp, LOCK_UN);
fclose($fp);
if (!$success) {
    http_response_code(503);
    echo '{"ok":false}';
    exit;
}
echo '{"ok":true}';
