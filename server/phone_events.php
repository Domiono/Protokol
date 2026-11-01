<?php
// Guarded phone-event history. Prune after 12 calendar months without losing concurrent clicks.
declare(strict_types=1);
require_once __DIR__ . '/privacy_retention.php';

/** Called with an exclusive lock on the original file inode.
 * No rename: live appenders and readers use flock() on the same inode.
 * Preserve undated/unknown historical lines rather than guessing.
 */
function rm_phone_events_prune_locked($fp, int $cutoff): bool {
    rewind($fp);
    $original = stream_get_contents($fp);
    if (!is_string($original)) return false;
    if ($original === '') return true;
    $lines = explode("\n", $original);
    $kept = [];
    $removed = false;
    foreach ($lines as $i => $line) {
        if ($line === '' && $i === count($lines) - 1) continue;
        if ($i === 0 && str_starts_with($line, '<?php ')) {
            $kept[] = $line;
            continue;
        }
        $entry = json_decode($line, true);
        $when = is_array($entry) ? rm_privacy_timestamp($entry['at'] ?? null) : null;
        if ($when !== null && $when < $cutoff) {
            $removed = true;
            continue;
        }
        $kept[] = $line;
    }
    if (!$removed) return true;
    $updated = implode("\n", $kept) . "\n";
    rewind($fp);
    $ok = fwrite($fp, $updated) === strlen($updated)
        && ftruncate($fp, strlen($updated)) && fflush($fp);
    if (!$ok) {
        // Best effort restore; fail closed rather than append to a damaged store.
        rewind($fp);
        fwrite($fp, $original);
        ftruncate($fp, strlen($original));
        fflush($fp);
    }
    return $ok;
}
function rm_phone_events_prune(?string $path = null, ?int $cutoff = null): bool {
    $file = $path ?? __DIR__ . '/phone_events_data.php';
    if (!is_file($file)) return true;
    $fp = @fopen($file, 'c+');
    if (!$fp) return false;
    if (!flock($fp, LOCK_EX)) { fclose($fp); return false; }
    try { return rm_phone_events_prune_locked($fp, $cutoff ?? rm_privacy_cutoff()); }
    finally { flock($fp, LOCK_UN); fclose($fp); }
}
function rm_phone_events_read(?string $path = null): array {
    $file = $path ?? __DIR__ . '/phone_events_data.php';
    if (!is_file($file)) return [];
    $fp = @fopen($file, 'r+');
    if (!$fp) return [];
    if (!flock($fp, LOCK_EX)) { fclose($fp); return []; }
    try {
        if (!rm_phone_events_prune_locked($fp, rm_privacy_cutoff())) return [];
        rewind($fp);
        $events = [];
        while (($line = fgets($fp)) !== false) {
            $event = json_decode($line, true);
            if (is_array($event) && isset($event['at'], $event['action'], $event['source_id'], $event['source'])) $events[] = $event;
        }
        return $events;
    } finally { flock($fp, LOCK_UN); fclose($fp); }
}
function rm_phone_events_append(array $event, ?string $path = null): bool {
    $line = json_encode($event, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($line === false) return false;
    $fp = @fopen($path ?? __DIR__ . '/phone_events_data.php', 'c+');
    if (!$fp) return false;
    if (!flock($fp, LOCK_EX)) { fclose($fp); return false; }
    try {
        if (!rm_phone_events_prune_locked($fp, rm_privacy_cutoff())) return false;
        fseek($fp, 0, SEEK_END);
        $start = ftell($fp);
        if ($start === false) return false;
        $data = ($start === 0 ? "<?php http_response_code(404); exit; ?>\n" : '') . $line . "\n";
        $ok = fwrite($fp, $data) === strlen($data) && fflush($fp);
        if (!$ok) { ftruncate($fp, $start); fflush($fp); }
        return $ok;
    } finally { flock($fp, LOCK_UN); fclose($fp); }
}
