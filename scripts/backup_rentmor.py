#!/usr/bin/env python3
"""Validate a private backup snapshot without printing customer data."""
import json
import sys
from pathlib import Path

REQUIRED = {"data.json", "reservations.json", "protocols.json", "customers.json", "costs.json"}
OPTIONAL = {"messages.json", "terms.json", "protocol_devices.json", "stats.json", "rental_assets.php", "rental_equipment_names.php"}
KNOWN = REQUIRED | OPTIONAL
OBJECTS = {"terms.json", "protocol_devices.json", "stats.json"}
LISTS = {"data.json", "reservations.json", "protocols.json", "customers.json", "costs.json", "messages.json"}

def validate(root: Path) -> list[str]:
    missing = REQUIRED - {p.name for p in root.iterdir() if p.is_file()}
    if missing:
        raise ValueError("Required snapshot files missing: " + ", ".join(sorted(missing)))
    verified = []
    for name in sorted(KNOWN):
        path = root / name
        if not path.is_file():
            continue
        if path.stat().st_size == 0:
            raise ValueError("Empty file: " + name)
        if name in {"rental_assets.php", "rental_equipment_names.php"}:
            text = path.read_text(encoding="utf-8")
            if "\n" not in text or not text.startswith("<?php"):
                raise ValueError("Invalid inventory header")
            payload = text.split("\n", 1)[1]
        else:
            payload = path.read_text(encoding="utf-8")
        data = json.loads(payload)
        if (name in LISTS or name.endswith("_names.php") or name == "rental_assets.php") and not isinstance(data, list):
            raise ValueError("Expected a list: " + name)
        if name in OBJECTS and not isinstance(data, dict):
            raise ValueError("Expected an object: " + name)
        verified.append(name)
    return verified

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: backup_rentmor.py SNAPSHOT_DIRECTORY")
    try:
        files = validate(Path(sys.argv[1]))
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        raise SystemExit("Backup validation failed: " + type(exc).__name__ + ": " + str(exc))
    print("Validated private snapshot (" + str(len(files)) + " files): " + ", ".join(files))
