#!/usr/bin/env python3
"""Read-only public health checks; never authenticate or mutate production."""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "https://rentmor.pl"
CHECKS = [
    ("/", 200, "html"),
    ("/robots.txt", 200, "text"),
    ("/sitemap.php", 200, "xml"),
    ("/protocol_sync.php", 401, "auth"),
]

def check(path: str, expected: int, mode: str, base: str = BASE) -> None:
    req = urllib.request.Request(base + path, headers={
        "User-Agent": "RentMorHealthMonitor/1.0",
        "Cache-Control": "no-cache",
        "X-RentMor-Monitor": "1",
    })
    try:
        response = urllib.request.urlopen(req, timeout=18)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        code = response.status
        body = response.read(300000).decode("utf-8", "replace")
        content_type = response.headers.get("Content-Type", "")
    if code != expected:
        raise RuntimeError(f"{path}: HTTP {code}, expected {expected}")
    if mode == "html" and ("rentmor" not in body.lower() or "<html" not in body.lower()):
        raise RuntimeError(f"{path}: expected site HTML absent")
    if mode == "xml" and ("<urlset" not in body and "<sitemapindex" not in body):
        raise RuntimeError(f"{path}: sitemap structure not found")
    if mode == "text" and "user-agent:" not in body.lower():
        raise RuntimeError(f"{path}: robots.txt missing user-agent")
    if mode == "auth":
        try:
            data = json.loads(body)
        except ValueError as exc:
            raise RuntimeError(f"{path}: expected JSON auth refusal") from exc
        if data.get("ok") is not False or "json" not in content_type.lower():
            raise RuntimeError(f"{path}: unauthenticated API not safely rejected")
    print(f"OK {path} HTTP {code}")

def run(checks=CHECKS, base=BASE):
    failures = []
    for path, expected, mode in checks:
        for attempt in range(2):
            try:
                check(path, expected, mode, base)
                break
            except (RuntimeError, urllib.error.URLError, TimeoutError) as exc:
                if attempt == 1:
                    failures.append(f"{path}: {type(exc).__name__}: {str(exc)}")
                else:
                    time.sleep(3)
    if failures:
        raise SystemExit("RentMor health check failed: " + "; ".join(failures))

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--self-test":
        assert CHECKS[-1] == ("/protocol_sync.php", 401, "auth")
        assert len(set(path for path, _, _ in CHECKS)) == len(CHECKS)
        print("Health monitor configuration tests passed.")
    else:
        run()
