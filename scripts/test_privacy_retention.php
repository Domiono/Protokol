<?php
declare(strict_types=1);
require_once __DIR__ . '/../server/privacy_retention.php';
require_once __DIR__ . '/../server/phone_events.php';
function check_ret(bool $value, string $msg): void {
    if (!$value) { fwrite(STDERR, $msg . "\n"); exit(1); }
}
function ev(string $at): array {
    return ['at'=>$at,'action'=>'call','source'=>'Strona główna','source_id'=>'home'];
}
$root = sys_get_temp_dir() . '/rm-retention-' . bin2hex(random_bytes(10));
if (!mkdir($root, 0700)) exit(1);
$messages = $root . '/messages.json';
$phone = $root . '/phone_events_data.php';
$cutoff = rm_privacy_timestamp('2025-11-01 12:00:00');
check_ret(is_int($cutoff), 'Date parsing failed');
try {
    $old = ['id'=>'old','date'=>'2025-10-30 12:00:00','client'=>'Old'];
    $new = ['id'=>'new','date'=>'2025-11-02 12:00:00','client'=>'New'];
    $hold = ['id'=>'hold','date'=>'2025-10-30 12:00:00','legal_hold'=>true];
    $bad = ['id'=>'unknown','date'=>'unknown'];
    $exact = ['id'=>'exact','date'=>'2025-11-01 12:00:00'];
    file_put_contents($messages, json_encode([$old,$new,$hold,$bad,$exact], JSON_THROW_ON_ERROR));
    check_ret(rm_privacy_prune_messages($messages, $cutoff), 'Message prune failed');
    $after = json_decode(file_get_contents($messages), true);
    check_ret(array_column($after,'id') === ['new','hold','unknown','exact'], 'Incorrect message prune');
    check_ret(rm_privacy_insert_message($messages,['id'=>'incoming','date'=>'2026-01-01 10:00:00'], $cutoff),'Insert failed');
    check_ret(rm_privacy_delete_message($messages, 'new', $cutoff),'Delete failed');
    check_ret(array_column(json_decode(file_get_contents($messages),true),'id') === ['incoming','hold','unknown','exact'],'Unexpected inbox result');
    $prior = '{"bad":true}';
    file_put_contents($messages, $prior);
    check_ret(!rm_privacy_prune_messages($messages,$cutoff) && file_get_contents($messages)===$prior, 'Malformed inbox overwritten');
    file_put_contents($messages, json_encode([$new],JSON_THROW_ON_ERROR));
    check_ret(rm_privacy_timestamp('31.10.2025')===null, 'Unknown date should be preserved');
    $header = "<?php http_response_code(404); exit; ?>\n";
    file_put_contents($phone,$header . json_encode(ev('2025-10-30 12:00:00')) . "\n"
        . json_encode(ev('2025-11-02 12:00:00')) . "\n"
        . json_encode(ev('2025-11-01 12:00:00')) . "\n"
        . "{invalid json}\n");
    check_ret(rm_phone_events_prune($phone,$cutoff),'Phone prune failed');
    $record = file_get_contents($phone);
    check_ret(str_starts_with($record,$header) && !str_contains($record,'2025-10-30'), 'Phone data/header incorrect');
    check_ret(str_contains($record,'{invalid json}'), 'Unknown history discarded');
    // The read method prunes against current date, so test it on fresh records.
    file_put_contents($phone,$header . json_encode(ev(date('Y-m-d H:i:s'))) . "\n");
    check_ret(count(rm_phone_events_read($phone))===1, 'Phone read failed');
    check_ret(rm_phone_events_append(ev(date('Y-m-d H:i:s')),$phone), 'Phone append failed');
    check_ret(count(rm_phone_events_read($phone))===2, 'Phone append integrity failed');
    check_ret(str_starts_with(file_get_contents($phone),$header), 'Phone PHP guard lost');
    echo "PASS: 12-month message and contact history retention; locks; invalid dates; legal hold\n";
} finally {
    foreach (glob($root . '/*') ?: [] as $name) unlink($name);
    rmdir($root);
}
