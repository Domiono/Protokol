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
function rm_admin_inventory_open(string $path) {
    $fp = @fopen($path, 'c+');
    if (!$fp || !flock($fp, LOCK_EX)) rm_admin_storage_error();
    return $fp;
}
function rm_admin_inventory_read($fp): array {
    rewind($fp);
    $raw = stream_get_contents($fp);
    if ($raw === '') return [];
    $data = is_string($raw) ? json_decode($raw, true) : null;
    if (!is_array($data)) rm_admin_storage_error();
    return $data;
}
function rm_admin_inventory_write($fp, array $data): void {
    $json = json_encode(array_values($data), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($json === false) rm_admin_storage_error();
    rewind($fp);
    $original = stream_get_contents($fp);
    rewind($fp);
    if (fwrite($fp, $json) !== strlen($json) || !ftruncate($fp, strlen($json)) || !fflush($fp)) {
        if (is_string($original)) { rewind($fp); fwrite($fp, $original); ftruncate($fp, strlen($original)); fflush($fp); }
        rm_admin_storage_error();
    }
}
