#!/usr/bin/env python3
"""Optional Telegram alert for failed GitHub health/backup workflows."""
import json
import os
import urllib.request

token = os.getenv("RENTMOR_MONITOR_TELEGRAM_BOT_TOKEN", "")
chat = os.getenv("RENTMOR_MONITOR_TELEGRAM_CHAT_ID", "")
if not token or not chat:
    print("Telegram alert not configured; check GitHub Actions failure notifications.")
    raise SystemExit(0)

run_url = os.getenv("GITHUB_SERVER_URL", "https://github.com") + "/" + os.getenv("GITHUB_REPOSITORY", "Domiono/Protokol") + "/actions/runs/" + os.getenv("GITHUB_RUN_ID", "")
message = "RentMor: alarm monitora lub backupu. Sprawdź szczegóły: " + run_url
payload = json.dumps({"chat_id": chat, "text": message, "disable_web_page_preview": True}).encode()
request = urllib.request.Request(
    "https://api.telegram.org/bot" + token + "/sendMessage",
    data=payload, headers={"Content-Type":"application/json"}, method="POST")
try:
    with urllib.request.urlopen(request, timeout=12) as response:
        if response.status != 200:
            raise RuntimeError("Telegram declined the alert")
    print("Telegram alert submitted.")
except Exception:
    # Never print exception URLs because they contain the Bot API credential.
    print("Telegram alert could not be delivered; inspect GitHub workflow notifications.")
