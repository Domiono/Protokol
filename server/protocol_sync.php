<?php
// RentMor protocol sync API. Upload beside protocol.php.
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');

function rm_json_response(int $status, array $payload): never {
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function rm_read_json_file(string $path, array $fallback): array {
    if (!is_file($path)) return $fallback;
    $raw = file_get_contents($path);
    $data = is_string($raw) ? json_decode($raw, true) : null;
    return is_array($data) ? $data : $fallback;
}
function rm_write_json_atomic(string $path, array $data): bool {
    $json = json_encode(array_values($data), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($json === false) return false;
    $tmp = $path . '.' . bin2hex(random_bytes(6)) . '.tmp';
    if (file_put_contents($tmp, $json, LOCK_EX) === false) return false;
    return rename($tmp, $path);
}
function rm_device_role(string $file): string {
    $token = (string)($_COOKIE['rm_protocol_device'] ?? '');
    if ($token === '') return '';
    $data = rm_read_json_file($file, ['devices'=>[]]);
    foreach (($data['devices'] ?? []) as $device) {
        if (!empty($device['active']) && !empty($device['token_hash']) &&
            hash_equals((string)$device['token_hash'], hash('sha256', $token))) {
            return (($device['role'] ?? '') === 'assistant') ? 'assistant' : 'admin';
        }
    }
    return '';
}
function rm_text(array $input, string $key, int $max = 4000): string {
    $value = trim((string)($input[$key] ?? ''));
    return mb_substr($value, 0, $max, 'UTF-8');
}

$role = rm_device_role(__DIR__ . '/protocol_devices.json');
if ($role === '') rm_json_response(401, ['ok'=>false, 'error'=>'Urządzenie nieaktywne. Połącz się z internetem i aktywuj je ponownie.']);

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
if ($method === 'GET') {
    $protocols = rm_read_json_file(__DIR__ . '/protocols.json', []);
    $inventory = rm_read_json_file(__DIR__ . '/data.json', []);
    $equipment = [];
    foreach ($inventory as $product) {
        $name = trim((string)($product['name'] ?? ''));
        if ($name !== '') $equipment[$name] = true;
    }
    rm_json_response(200, [
        'ok'=>true, 'role'=>$role, 'server_time'=>date(DATE_ATOM),
        'protocols'=>array_values($protocols), 'equipment'=>array_keys($equipment)
    ]);
}
if ($method !== 'POST') rm_json_response(405, ['ok'=>false, 'error'=>'Niedozwolona metoda.']);

$raw = file_get_contents('php://input');
$body = is_string($raw) ? json_decode($raw, true) : null;
if (!is_array($body)) rm_json_response(400, ['ok'=>false, 'error'=>'Nieprawidłowy JSON.']);
$operations = $body['operations'] ?? [];
if (!is_array($operations) || count($operations) > 100) rm_json_response(400, ['ok'=>false, 'error'=>'Nieprawidłowa lista operacji.']);

$file = __DIR__ . '/protocols.json';
$fp = fopen($file, 'c+');
if (!$fp) rm_json_response(500, ['ok'=>false, 'error'=>'Nie można otworzyć bazy protokołów.']);
if (!flock($fp, LOCK_EX)) { fclose($fp); rm_json_response(503, ['ok'=>false, 'error'=>'Baza jest chwilowo zajęta.']); }
$rawDb = stream_get_contents($fp);
$protocols = json_decode((string)$rawDb, true);
if (!is_array($protocols)) $protocols = [];
$results = [];

foreach ($operations as $op) {
    if (!is_array($op)) { $results[]=['ok'=>false,'error'=>'Nieprawidłowa operacja']; continue; }
    $opId = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['op_id'] ?? ''));
    $action = (string)($op['action'] ?? '');
    if ($opId === '' || strlen($opId) > 80) { $results[]=['ok'=>false,'error'=>'Brak poprawnego identyfikatora operacji']; continue; }

    // Idempotency: replaying the same create/return is safe.
    $already = false;
    foreach ($protocols as $saved) {
        if (($saved['sync_op_id'] ?? '') === $opId || ($saved['return_sync_op_id'] ?? '') === $opId) { $already = true; break; }
    }
    if ($already) { $results[]=['op_id'=>$opId,'ok'=>true,'duplicate'=>true]; continue; }

    if ($action === 'create') {
        $client = rm_text($op, 'client', 200);
        $equipment = rm_text($op, 'equipment', 200);
        if ($client === '' || $equipment === '') { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Wymagane są dane klienta i sprzętu']; continue; }
        $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['id'] ?? ''));
        if ($id === '' || strlen($id) > 80) $id = bin2hex(random_bytes(8));
        $exists = false;
        foreach ($protocols as $saved) if (($saved['id'] ?? '') === $id) { $exists = true; break; }
        if ($exists) { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Identyfikator protokołu już istnieje']; continue; }
        $number = preg_replace('/[^A-Z0-9-]/', '', strtoupper(rm_text($op, 'number', 40)));
        if ($number === '') $number = 'RM-' . date('Ymd') . '-' . strtoupper(substr($id, 0, 4));
        $protocols[] = [
            'id'=>$id, 'number'=>$number, 'status'=>'wydany', 'created_at'=>date('Y-m-d H:i:s'),
            'client'=>$client, 'phone'=>rm_text($op,'phone',50), 'equipment'=>$equipment,
            'rental_date'=>rm_text($op,'rental_date',40), 'condition'=>rm_text($op,'condition',2000),
            'accessories'=>rm_text($op,'accessories',2000), 'notes'=>rm_text($op,'notes',2000),
            'return_date'=>'', 'return_condition'=>'', 'return_notes'=>'', 'sync_op_id'=>$opId
        ];
        $results[]=['op_id'=>$opId,'ok'=>true,'id'=>$id,'number'=>$number];
    } elseif ($action === 'return') {
        $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['id'] ?? ''));
        $found = false; $conflict = false;
        foreach ($protocols as &$item) {
            if (($item['id'] ?? '') !== $id) continue;
            $found = true;
            if (($item['status'] ?? '') === 'zwrócony') { $conflict = true; break; }
            $item['status'] = 'zwrócony';
            $item['return_date'] = rm_text($op,'return_date',40);
            $item['return_condition'] = rm_text($op,'return_condition',2000);
            $item['return_notes'] = rm_text($op,'return_notes',2000);
            $item['return_sync_op_id'] = $opId;
            break;
        }
        unset($item);
        if (!$found) $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nie znaleziono protokołu na serwerze. Najpierw zsynchronizuj wydanie.'];
        elseif ($conflict) $results[]=['op_id'=>$opId,'ok'=>false,'conflict'=>true,'error'=>'Ten protokół został już zwrócony na innym urządzeniu.'];
        else $results[]=['op_id'=>$opId,'ok'=>true,'id'=>$id];
    } else {
        $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nieobsługiwana operacja'];
    }
}
rewind($fp);
$encoded = json_encode(array_values($protocols), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
$writeOk = $encoded !== false && ftruncate($fp, 0) && fwrite($fp, $encoded) !== false && fflush($fp);
flock($fp, LOCK_UN);
fclose($fp);
if (!$writeOk) rm_json_response(500, ['ok'=>false,'error'=>'Nie udało się zapisać danych. Operacje należy ponowić.']);
rm_json_response(200, ['ok'=>true,'role'=>$role,'results'=>$results]);
