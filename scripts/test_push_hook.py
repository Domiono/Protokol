#!/usr/bin/env python3
import pathlib
import subprocess
import sys
import tempfile
from patch_contact_push import patch, MARKER

SOURCE = """<?php
function save_json_file($name,$data) { return true; }
$new_msg = [
    'id' => uniqid('msg_', true),
    'client'=>'Example',
];
array_unshift($current_msgs, $new_msg);
save_json_file($msgs_file, $current_msgs);
// existing Telegram & mail must remain intact
mail('test@example.org','subj','body');
"""

def test():
    modified=patch(SOURCE)
    assert MARKER in modified
    assert "if (save_json_file($msgs_file, $current_msgs)) {" in modified
    assert "rm_push_notify_new_message((string)($new_msg['id'] ?? ''));" in modified
    assert modified.count("mail('test@example.org'") == 1
    assert patch(modified)==modified, "patch must be idempotent"
    with tempfile.TemporaryDirectory() as folder:
        path=pathlib.Path(folder)/"index.php"
        path.write_text(modified,encoding="utf-8")
        subprocess.run(["php","-l",str(path)],check=True,capture_output=True)
    for invalid in ["<?php save_json_file($other, $items);", "<?php save_json_file($msgs_file,$current_msgs);"]:
        try: patch(invalid)
        except ValueError: pass
        else: raise AssertionError("Changed contact form must be rejected")
    # The background push must carry a system notification, never customer details.
    import json
    payload = subprocess.run(
        ["php", "-r", 'require $argv[1]; echo rm_push_message_payload("TEST_TOKEN", "MSG_TEST");',
         "server/push_bridge.php"],
        capture_output=True, text=True, check=True,
    )
    wire = json.loads(payload.stdout)["message"]
    assert wire["notification"]["title"] == "RentMor — nowa wiadomość"
    assert wire["android"]["priority"] == "high"
    assert wire["android"]["notification"]["channel_id"] == "rentmor_contact_messages"
    assert wire["android"]["notification"]["visibility"] == "PRIVATE"
    assert wire["data"] == {"type": "contact_message", "message_id": "MSG_TEST"}
    assert "TEST_TOKEN" not in wire["notification"]["body"]
    assert "MSG_TEST" not in wire["notification"]["body"]
    print("FCM background notification payload and contact privacy checks passed.")
    print("Contact push hook tests passed.")

if __name__=="__main__":test()
