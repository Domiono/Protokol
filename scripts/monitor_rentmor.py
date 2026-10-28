#!/usr/bin/env python3
"""Read-only public smoke tests: never send credentials or mutate production."""
import json
import urllib.error
import urllib.request

BASE = "https://rentmor.pl"
checks = [
    ("/", 200, "rentmor"),
    ("/admin.php", 200, "admin"),
    ("/robots.txt", 200, "user-agent"),
    ("/protocol_sync.php", 401, '"ok":false'),
]
errors = []
for path, expected_code, marker in checks:
    try:
        request = urllib.request.Request(
            BASE + path,
            headers={"User-Agent": "RentMor-HealthCheck/1.0", "Accept": "text/html,application/json,*/*"},
        )
        try:
            response = urllib.request.urlopen(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status = response.status
            data = response.read(200_000).decode("utf-8", errors="replace")
        if status != expected_code or marker not in data.lower():
            errors.append(path + ": unexpected status/content")
        elif path == "/protocol_sync.php":
            try:
                parsed = json.loads(data)
                if parsed.get("ok") is not False:
                    errors.append(path + ": auth guard returned unexpected JSON")
            except (TypeError, ValueError):
                errors.append(path + ": invalid API JSON")
    except (OSError, ValueError) as error:
        errors.append(path + ": network or decoding failure")
    if not errors or not errors[-1].startswith(path):
        print(path + ": OK")
if errors:
    print("FAILED health checks:\n" + "\n".join(errors))
    raise SystemExit(1)
print("Public site and unauthorized API health checks passed.")
