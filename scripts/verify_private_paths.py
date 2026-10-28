#!/usr/bin/env python3
"""Verify private endpoints cannot be read over HTTP. Never inspect response bodies."""
import argparse
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

PATHS = [
    "/data.json", "/reservations.json", "/customers.json",
    "/protocols.json", "/costs.json", "/messages.json",
    "/stats.json", "/protocol_devices.json", "/online_sessions.json",
    "/data.json.lock", "/reservations.json.lock",
    "/rental_equipment_names.php",
    "/rental_equipment_names.php.lock",
]
ALLOWED = {401, 403, 404, 410}

def status(url: str, method: str) -> int:
    req = Request(url, method=method, headers={"User-Agent":"RentMorPrivatePathAudit/1.0"})
    try:
        response = urlopen(req, timeout=16)
    except HTTPError as err:
        response = err
    with response:
        # Intentionally never call read() on private records.
        return response.status

def verify(base: str) -> None:
    bad = []
    for path in PATHS:
        for method in ("HEAD", "GET"):
            code = status(base + path, method)
            if code not in ALLOWED:
                bad.append(f"{path} {method} returned HTTP {code}")
    if bad:
        raise SystemExit("Private file access must be denied: " + "; ".join(bad))
    print("Private rental data, locks and registry are blocked from public HTTP access.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="https://rentmor.pl")
    args = parser.parse_args()
    verify(args.base.rstrip("/"))
