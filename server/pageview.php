<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
function rm_view_reply(int $code): never { http_response_code($code); echo json_encode(['ok'=>$code===200]); exit; }
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') rm_view_reply(405);
$origin = (string)($_SERVER['HTTP_ORIGIN'] ?? '');
if (($origin !== '' && !in_array(strtolower((string)parse_url($origin,PHP_URL_HOST)),['rentmor.pl','www.rentmor.pl'],true)) || ($_SERVER['HTTP_SEC_FETCH_SITE'] ?? '') === 'cross-site') rm_view_reply(403);
$token = $_POST['token'] ?? '';
if (!is_string($token) || !preg_match('/^[a-f0-9]{48}$/D',$token)) rm_view_reply(400);
require_once __DIR__ . '/browser_stats.php';
if (!session_start()) rm_view_reply(503);
$code = rm_stats_confirm_pageview($token);
session_write_close();
rm_view_reply($code);
