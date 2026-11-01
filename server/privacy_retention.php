<?php
/** Twelve calendar months for CONTACT MESSAGES ONLY.
 * Never touch rentals, customers, settlements or protocols.
 * All message writers must share the same sidecar lock to avoid lost updates.
 */
declare(strict_types=1);
if (isset($_SERVER['SCRIPT_FILENAME']) && realpath((string)$_SERVER['SCRIPT_FILENAME']) === __FILE__) {
    http_response_code(404); exit;
}
function rm_privacy_cutoff(?DateTimeImmutable $now = null): int {
    $now = $now ?? new DateTimeImmutable('now', new DateTimeZone('Europe/Warsaw'));
    return $now->setTimezone(new DateTimeZone('Europe/Warsaw'))->modify('-12 months')->getTimestamp();
}
function rm_privacy_timestamp($input): ?int {
    if (!is_string($input) || trim($input) === '') return null;
    $value = trim($input);
    $timezone = new DateTimeZone('Europe/Warsaw');
    foreach (['!Y-m-d H:i:s', '!Y-m-d H:i', '!Y-m-d'] as $pattern) {
        $time = DateTimeImmutable::createFromFormat($pattern, $value, $timezone);
        if ($time !== false && $time->format(substr($pattern,1)) === $value) return $time->getTimestamp();
    }
    // Do not delete data whose historical date format is unknown.
    return null;
}
function rm_privacy_should_keep_message(array $entry, int $cutoff): bool {
    // Active dispute/claim is a reason to retain the necessary correspondence.
    if (!empty($entry['retention_hold']) || !empty($entry['legal_hold'])) return true;
    $when = rm_privacy_timestamp($entry['date'] ?? null);
    return $when === null || $when >= $cutoff;
}
function rm_privacy_messages_modify(string $file, callable $transform, ?int $cutoff = null): bool {
    $lock = @fopen($file . '.lock', 'c+');
    if (!$lock) return false;
    if (!@flock($lock, LOCK_EX)) { fclose($lock); return false; }
    try {
        if (file_exists($file)) {
            $raw = @file_get_contents($file);
            if (!is_string($raw) || $raw === '') return false;
            $current = json_decode($raw, true);
            if (!is_array($current) || !array_is_list($current)) return false;
        } else {
            $current = [];
        }
        $retained = [];
        foreach ($current as $item) {
            if (!is_array($item)) return false; // do not rewrite malformed storage
            if (rm_privacy_should_keep_message($item, $cutoff ?? rm_privacy_cutoff())) $retained[] = $item;
        }
        $next = $transform($retained);
        if (!is_array($next) || !array_is_list($next)) return false;
        if ($next === $current) return true;
        $json = json_encode($next, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        if (!is_string($json)) return false;
        $tmp = $file . '.' . bin2hex(random_bytes(8)) . '.tmp';
        if (@file_put_contents($tmp, $json, LOCK_EX) !== strlen($json) || !@rename($tmp, $file)) {
            @unlink($tmp);
            return false;
        }
        return true;
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
}
function rm_privacy_prune_messages(string $file, ?int $cutoff = null): bool {
    if (!is_file($file)) return true;
    return rm_privacy_messages_modify($file, static fn(array $current): array => $current, $cutoff);
}
function rm_privacy_insert_message(string $file, array $new, ?int $cutoff = null): bool {
    return rm_privacy_messages_modify($file, static function(array $current) use ($new): array {
        array_unshift($current, $new);
        return $current;
    }, $cutoff);
}
function rm_privacy_delete_message(string $file, string $id, ?int $cutoff = null): bool {
    return rm_privacy_messages_modify($file, static function(array $current) use ($id): array {
        return array_values(array_filter($current, static fn(array $entry): bool => (string)($entry['id'] ?? '') !== $id));
    }, $cutoff);
}
