<?php
declare(strict_types=1);
require_once __DIR__ . '/rental_inventory.php';

function rm_admin_valid_time(string $time): bool {
    return preg_match('/^(?:[01]?\d|2[0-3]):[0-5]\d$/D', $time) === 1;
}

// Called while reservations.json.lock is held, exactly like the sync API.
function rm_admin_assign_reservation(array &$item, array $reservations, ?array $assets = null): string {
    if (!rm_valid_reservation_dates($item)) return 'Sprawdź daty rezerwacji.';
    if (!empty($item['settled']) || !empty($item['returned'])) return '';
    $assets = $assets ?? rm_read_assets();
    if (empty($item['asset_id'])) {
        $chosen = rm_auto_pick_asset($item, $reservations, $assets);
        if ($chosen['error'] !== '') return $chosen['error'];
        $item['asset_id'] = $chosen['asset']['id'] ?? '';
        $item['asset_label'] = $chosen['asset']['label'] ?? '';
        $item['vacuum_asset_id'] = $chosen['vacuum']['id'] ?? '';
        $item['vacuum_asset_label'] = $chosen['vacuum']['label'] ?? '';
    }
    return rm_rental_conflict($item, $reservations, $assets);
}

function rm_admin_storage_error(): never {
    http_response_code(503);
    exit('Nie udało się zapisać danych. Odśwież panel i spróbuj ponownie.');
}
// The Android API locks <data>.lock and atomically renames <data>.
// Admin MUST lock the same sidecar (locking the replaced data inode is unsafe).
function rm_admin_inventory_open(string $path) {
    $fp = @fopen($path . '.lock', 'c+');
    if (!$fp || !@flock($fp, LOCK_EX)) rm_admin_storage_error();
    return $fp;
}
function rm_admin_inventory_path($fp): string {
    $meta = stream_get_meta_data($fp);
    $uri = (string)($meta['uri'] ?? '');
    if (!str_ends_with($uri, '.lock')) rm_admin_storage_error();
    return substr($uri, 0, -5);
}
function rm_admin_read_json_list(string $path): array {
    if (!file_exists($path)) return [];
    if (!is_file($path)) rm_admin_storage_error();
    $raw = @file_get_contents($path);
    if (!is_string($raw) || $raw === '') rm_admin_storage_error();
    $data = json_decode($raw, true);
    if (!is_array($data) || !array_is_list($data)) rm_admin_storage_error();
    return $data;
}
function rm_admin_inventory_read($fp): array {
    return rm_admin_read_json_list(rm_admin_inventory_path($fp));
}
function rm_admin_inventory_write($fp, array $data): void {
    rm_admin_write_json_file(rm_admin_inventory_path($fp), array_values($data));
}

/**
 * IP-based limiter independent of PHP sessions, so a fresh cookie cannot
 * reset the counter. Ignore client-supplied forwarding headers.
 */
function rm_admin_check_password(string $password, string $hash): array {
    $dir = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR) . '/rentmor-admin-login-' . substr(hash('sha256', __DIR__), 0, 20);
    if (!is_dir($dir) && !@mkdir($dir, 0700, true) && !is_dir($dir)) rm_admin_storage_error();
    $ip = (string)($_SERVER['REMOTE_ADDR'] ?? 'unknown');
    $path = $dir . '/' . hash('sha256', $ip) . '.json';
    $fp = @fopen($path, 'c+');
    if (!$fp || !@flock($fp, LOCK_EX)) rm_admin_storage_error();
    @chmod($path, 0600);
    try {
        rewind($fp);
        $state = json_decode((string)stream_get_contents($fp), true);
        if (!is_array($state)) $state = [];
        $now = time();
        $first = (int)($state['first'] ?? 0);
        $failures = (int)($state['failures'] ?? 0);
        $blocked = (int)($state['blocked'] ?? 0);
        if ($first <= 0 || $first > $now || $first + 900 <= $now) {
            $first = $now; $failures = 0; $blocked = 0;
        }
        if ($blocked > $now) return ['ok' => false, 'retry_after' => $blocked - $now];
        $ok = password_verify($password, $hash);
        if ($ok) {
            $first = $now; $failures = 0; $blocked = 0;
        } else {
            ++$failures;
            if ($failures >= 5) $blocked = $now + 900;
        }
        $json = json_encode(['first'=>$first, 'failures'=>$failures, 'blocked'=>$blocked], JSON_THROW_ON_ERROR);
        rewind($fp);
        if (fwrite($fp, $json) !== strlen($json) || !ftruncate($fp, strlen($json)) || !fflush($fp)) rm_admin_storage_error();
        return ['ok' => $ok, 'retry_after' => max(0, $blocked - $now)];
    } finally {
        flock($fp, LOCK_UN);
        fclose($fp);
    }
}

/** Store canonical nonnegative decimal values; do not trust HTML number controls. */
function rm_admin_money($value, bool $positive, string $label): string {
    if (!is_string($value) && !is_int($value) && !is_float($value)) rm_admin_reject('Nieprawidłowa kwota: ' . $label . '.');
    $raw = trim((string)$value);
    if (!preg_match('/^(?:0|[1-9][0-9]{0,5})(?:[.,][0-9]{1,2})?$/D', $raw)) rm_admin_reject('Nieprawidłowa kwota: ' . $label . '.');
    $normalized = str_replace(',', '.', $raw);
    if ($positive && (float)$normalized <= 0) rm_admin_reject($label . ' musi być większa od zera.');
    return rtrim(rtrim(number_format((float)$normalized, 2, '.', ''), '0'), '.');
}

function rm_admin_reject(string $message): never {
    http_response_code(422);
    header('Content-Type: text/plain; charset=UTF-8');
    exit($message . ' Wróć do formularza i popraw dane.');
}

/** Validate actual uploaded image content, not merely the filename suffix. */
function rm_admin_validate_photos(array $files): array {
    if (!isset($files['name'])) return [];
    if (!is_array($files['name']) || !is_array($files['error'] ?? null)
        || !is_array($files['tmp_name'] ?? null) || !is_array($files['size'] ?? null)) {
        rm_admin_reject('Nieprawidłowy formularz zdjęć.');
    }
    $types = ['jpg'=>'image/jpeg', 'jpeg'=>'image/jpeg', 'png'=>'image/png', 'webp'=>'image/webp', 'gif'=>'image/gif'];
    $valid = [];
    $finfo = class_exists('finfo') ? new finfo(FILEINFO_MIME_TYPE) : null;
    foreach ($files['name'] as $i => $name) {
        $error = $files['error'][$i] ?? UPLOAD_ERR_NO_FILE;
        if ($error === UPLOAD_ERR_NO_FILE) continue;
        if ($error !== UPLOAD_ERR_OK || !is_string($name)) rm_admin_reject('Nie udało się przesłać zdjęcia.');
        $size = $files['size'][$i] ?? 0;
        $tmp = $files['tmp_name'][$i] ?? '';
        $extension = strtolower(pathinfo($name, PATHINFO_EXTENSION));
        if (!isset($types[$extension]) || !is_int($size) || $size <= 0 || $size > 8 * 1024 * 1024
            || !is_string($tmp) || !is_uploaded_file($tmp)) {
            rm_admin_reject('Zdjęcie musi być plikiem JPG, PNG, WebP lub GIF o rozmiarze do 8 MB.');
        }
        $info = @getimagesize($tmp);
        if (!$info || ($info['mime'] ?? '') !== $types[$extension] || ($finfo !== null && $finfo->file($tmp) !== $types[$extension])
            || ($info[0] ?? 0) < 1 || ($info[1] ?? 0) < 1
            || $info[0] > 8000 || $info[1] > 8000 || $info[0] * $info[1] > 24000000) {
            rm_admin_reject('Nieprawidłowy obraz lub za duża rozdzielczość (maks. 24 MP).');
        }
        $valid[] = ['tmp' => $tmp, 'extension' => $extension];
        if (count($valid) > 12) rm_admin_reject('Można dodać maksymalnie 12 zdjęć naraz.');
    }
    return $valid;
}

/** Refuse to report success if the encoded JSON cannot be atomically replaced. */
function rm_admin_write_json_file(string $path, array $data): void {
    $json = json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($json === false) rm_admin_storage_error();
    $tmp = $path . '.' . bin2hex(random_bytes(8)) . '.tmp';
    if (@file_put_contents($tmp, $json, LOCK_EX) !== strlen($json) || !@rename($tmp, $path)) {
        @unlink($tmp);
        rm_admin_storage_error();
    }
}
