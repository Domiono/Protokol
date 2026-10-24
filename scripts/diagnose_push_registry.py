#!/usr/bin/env python3
"""Print anonymous FCM registration health from private server JSON snapshots.

Never output FCM tokens, device hashes, cookies, client data, or secrets.
Use only in an ephemeral, credential-controlled CI runner.
"""
import json
import pathlib
import sys
import time

def load(path):
    try:
        return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None

def evaluate(devices, registry, now=None):
    now = int(time.time() if now is None else now)
    device_list = devices.get("devices", []) if isinstance(devices, dict) else []
    if not isinstance(device_list, list):
        device_list = []
    live = {
        d["token_hash"]
        for d in device_list
        if isinstance(d, dict) and d.get("active")
        and d.get("role") != "assistant"
        and isinstance(d.get("token_hash"), str)
    }
    registrations = registry if isinstance(registry, list) else []
    recent = [r for r in registrations if isinstance(r, dict)
              and isinstance(r.get("updated"), (float, int))
              and r["updated"] >= now - 90 * 86400]
    matching = [r for r in recent if r.get("device_hash") in live
                and isinstance(r.get("token"), str) and bool(r["token"])]
    return {"active_admin_devices": len(live), "all_recent_registrations": len(recent),
            "matching_admin_push_tokens": len(matching)}

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: diagnose_push_registry.py devices.json tokens.json")
    a = evaluate(load(sys.argv[1]), load(sys.argv[2]))
    print("FCM diagnostic (counts only):")
    for key, value in a.items():
        print(f"  {key}: {value}")
    if a["matching_admin_push_tokens"] == 0:
        print("No eligible push token found. Install the current APK and sync as admin.")
    else:
        print("At least one authorized FCM token exists; check delivery path next.")
