#!/usr/bin/env python3
"""Safely add a Firebase notification after a SUCCESSFUL RentMor contact form save.

The public index.php stays server-owned; don't replace it with an old copy.
Fail closed when the form structure changes.
"""
from pathlib import Path
import re
import sys

MARKER = "RENTMOR_FCM_AFTER_MESSAGE_SAVE_V1"
PATTERN = re.compile(r"(?P<indent>^[ \t]*)save_json_file\(\s*\$msgs_file\s*,\s*\$current_msgs\s*\)\s*;", re.M)


def patch(source: str) -> str:
    if MARKER in source:
        if source.count(MARKER) != 1:
            raise ValueError("Unexpected duplicate Firebase hook markers")
        return source
    matches = list(PATTERN.finditer(source))
    if len(matches) != 1:
        raise ValueError("Expected exactly one message-save call; refusing to modify index.php")
    match = matches[0]
    preceding = source[max(0, match.start() - 1800) : match.start()]
    if not re.search(r"\$new_msg\s*=\s*\[", preceding) or not re.search(r"['\"]id['\"]\s*=>", preceding):
        raise ValueError("The message ID field or new-message variable changed")
    indent = match.group("indent")
    hook = (
        indent + "if (save_json_file($msgs_file, $current_msgs)) {\n"
        + indent + "    // " + MARKER + " -- send only after a successfully saved message.\n"
        + indent + "    require_once __DIR__ . '/push_bridge.php';\n"
        + indent + "    rm_push_notify_new_message((string)($new_msg['id'] ?? ''));\n"
        + indent + "}"
    )
    return source[: match.start()] + hook + source[match.end() :]


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_contact_push.py path/to/index.php")
    path = Path(sys.argv[1])
    before = path.read_text(encoding="utf-8")
    after = patch(before)
    if after != before:
        path.write_text(after, encoding="utf-8")
        print("Installed Firebase contact form hook")
    else:
        print("Firebase contact form hook already installed")


if __name__ == "__main__":
    main()
