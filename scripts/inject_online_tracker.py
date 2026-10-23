#!/usr/bin/env python3
"""Add the RentMor online heartbeat script to the latest FTP-downloaded homepage.
Only inject one safe external JS tag. Never replace live site content with a stale copy.
"""
import pathlib
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: inject_online_tracker.py /path/to/live-index.php")

path = pathlib.Path(sys.argv[1])
contents = path.read_text(encoding="utf-8")
if "<?php" not in contents or "stats.json" not in contents:
    raise SystemExit("Unexpected index.php; refusing to overwrite live site.")

tag = '<script src="/online.js" defer></script>'
if tag in contents:
    print("Live index already has the online heartbeat. No changes.")
    raise SystemExit(0)

matches = list(re.finditer(r"</body\s*>", contents, flags=re.IGNORECASE))
if len(matches) != 1:
    raise SystemExit("Expected exactly one closing body tag. Refusing to patch.")

offset = matches[0].start()
contents = contents[:offset] + "    " + tag + "\n" + contents[offset:]
path.write_text(contents, encoding="utf-8")
print("Injected online tracker in the latest live homepage.")
