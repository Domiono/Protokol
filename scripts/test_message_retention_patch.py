#!/usr/bin/env python3
"""Check idempotent patching of the PHP form after the existing FCM hook."""
import sys
from pathlib import Path
import subprocess
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parent))
from patch_message_retention import patch, MARKER

SOURCE = """<?php
$new_msg = ['id'=>'m','date'=>'2026-11-01 12:00:00'];
array_unshift($current_msgs, $new_msg);
if (save_json_file($msgs_file, $current_msgs)) {
    // RENTMOR_FCM_AFTER_MESSAGE_SAVE_V1 -- send only after a successfully saved message.
    require_once __DIR__ . '/push_bridge.php';
    rm_push_notify_new_message((string)($new_msg['id'] ?? ''));
}
"""
modified = patch(SOURCE)
assert MARKER in modified
assert "rm_privacy_insert_message($msgs_file, $new_msg)" in modified
assert modified.count("rm_push_notify_new_message") == 1
assert modified.count("RENTMOR_FCM_AFTER_MESSAGE_SAVE_V1") == 1
assert patch(modified) == modified
with tempfile.TemporaryDirectory() as td:
    p = Path(td) / "index.php"
    p.write_text(modified, encoding='utf-8')
    subprocess.run(['php', '-l', str(p)], check=True)
for bad in ['<?php save_json_file($msgs_file, $current_msgs);', '<?php if (save_json_file($msgs_file, $current_msgs)) {}']:
    try: patch(bad)
    except ValueError: pass
    else: raise AssertionError('Unsafe template accepted')
print('PASS: existing FCM hook retained; message writes are lock-safe and idempotently patched')
