"""Wire current live contact writer to a lock-safe, 12-month message store.

The site homepage lives on hosting. Patch ONLY the known Firebase save hook,
fail closed on unknown structures; preserve all notifications and form behavior.
"""
from pathlib import Path
import sys

MARKER = "/* rentmor-message-retention-v1 */"
HOOK = "if (save_json_file($msgs_file, $current_msgs)) {"
REPLACEMENT = ("require_once __DIR__ . '/privacy_retention.php';\n"
               "    " + MARKER + "\n"
               "    if (rm_privacy_insert_message($msgs_file, $new_msg)) {")

def patch(source: str) -> str:
    if MARKER in source:
        if source.count(MARKER) != 1 or source.count("rm_privacy_insert_message($msgs_file, $new_msg)") != 1:
            raise ValueError("Invalid existing message retention patch")
        return source
    if source.count("RENTMOR_FCM_AFTER_MESSAGE_SAVE_V1") != 1:
        raise ValueError("Missing unique, tested FCM message-save marker")
    if source.count(HOOK) != 1 or "rm_privacy_insert_message" in source:
        raise ValueError("Unexpected message saving structure")
    pos = source.index(HOOK)
    if abs(pos - source.index("RENTMOR_FCM_AFTER_MESSAGE_SAVE_V1")) > 300:
        raise ValueError("FCM hook moved away from message save")
    return source.replace(HOOK, REPLACEMENT, 1)

if __name__ == '__main__':
    target = Path(sys.argv[1])
    target.write_text(patch(target.read_text(encoding='utf-8')), encoding='utf-8')
    print("Retention-aware live message writer verified.")
