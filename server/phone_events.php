<?php
// Private append-only history. The PHP guard prevents public downloads of the data.
declare(strict_types=1);
function rm_phone_events_read(?string $path = null): array {
    $fp = @fopen($path ?? __DIR__ . '/phone_events_data.php', 'r');
    if (!$fp) return [];
    if (!flock($fp, LOCK_SH)) { fclose($fp); return []; }
    $events = [];
    while (($line = fgets($fp)) !== false) {
        $event = json_decode($line, true);
        if (is_array($event) && isset($event['at'], $event['action'], $event['source_id'], $event['source'])) $events[] = $event;
    }
    flock($fp, LOCK_UN); fclose($fp);
    return $events;
}
function rm_phone_events_append(array $event, ?string $path = null): bool {
    $line = json_encode($event, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($line === false) return false;
    $fp = @fopen($path ?? __DIR__ . '/phone_events_data.php', 'c+');
    if (!$fp) return false;
    if (!flock($fp, LOCK_EX)) { fclose($fp); return false; }
    fseek($fp, 0, SEEK_END);
    $start = ftell($fp);
    $data = ($start === 0 ? "<?php http_response_code(404); exit; ?>\n" : '') . $line . "\n";
    $ok = fwrite($fp, $data) === strlen($data) && fflush($fp);
    if (!$ok) { ftruncate($fp, $start); fflush($fp); }
    flock($fp, LOCK_UN); fclose($fp);
    return $ok;
}
