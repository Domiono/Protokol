#!/usr/bin/env python3
"""Read-only public RentMor checks: safe statuses, latency and useful CI diagnostics.

Never authenticates, changes data, follows redirects or accesses private URLs.
Scheduled GitHub workflow provides failure notifications; it is NOT a production
log reader, synthetic checkout or an authenticated form tester.
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = "https://rentmor.pl"
TIMEOUT_SECONDS = 18
SLOW_SECONDS = 15
MAX_RESPONSE_BYTES = 300_000
CHECKS = [
    ("/", 200, "html"),
    ("/robots.txt", 200, "text"),
    ("/sitemap.php", 200, "xml"),
    ("/protocol_sync.php", 401, "auth"),
]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def check(path: str, expected: int, mode: str, base: str = BASE,
          opener=None, clock=None) -> dict:
    """One allowlisted GET. Return only public status and timing, never page data."""
    if (base != BASE or path not in {p for p, _, _ in CHECKS} or
            (path, expected, mode) not in CHECKS):
        raise ValueError("Non-allowlisted monitoring target")
    if opener is None:
        opener = urllib.request.build_opener(NoRedirect())
    if clock is None:
        clock = time.monotonic
    request = urllib.request.Request(base + path, headers={
        "User-Agent": "RentMorHealthMonitor/2.0",
        "Cache-Control": "no-cache",
        "X-RentMor-Monitor": "1",
    }, method="GET")
    started = clock()
    try:
        response = opener.open(request, timeout=TIMEOUT_SECONDS)
    except urllib.error.HTTPError as exc:
        response = exc  # 401 is expected for the API.
    with response:
        code = response.status
        body = response.read(MAX_RESPONSE_BYTES).decode("utf-8", "replace")
        content_type = response.headers.get("Content-Type", "")
    duration = round(clock() - started, 3)
    if code != expected:
        raise RuntimeError(f"{path}: HTTP {code} (expected {expected})")
    if duration > SLOW_SECONDS:
        raise RuntimeError(f"{path}: response exceeded {SLOW_SECONDS}s")
    low = body.lower()
    if mode == "html" and ("rentmor" not in low or "<html" not in low):
        raise RuntimeError(f"{path}: expected site HTML absent")
    if mode == "xml" and ("<urlset" not in low and "<sitemapindex" not in low):
        raise RuntimeError(f"{path}: sitemap structure not found")
    if mode == "text" and "user-agent:" not in low:
        raise RuntimeError(f"{path}: robots.txt missing user-agent")
    if mode == "auth":
        try:
            data = json.loads(body)
        except ValueError as exc:
            raise RuntimeError(f"{path}: expected JSON auth refusal") from exc
        if not isinstance(data, dict) or data.get("ok") is not False or "json" not in content_type.lower():
            raise RuntimeError(f"{path}: unauthenticated API not safely rejected")
    return {"path": path, "status": code, "duration_s": duration}


def run(checks=CHECKS, base=BASE, opener=None, clock=None, sleep=None) -> None:
    failures = []
    successes = []
    if sleep is None:
        sleep = time.sleep
    for path, expected, mode in checks:
        for attempt in range(2):
            try:
                result = check(path, expected, mode, base, opener, clock)
                successes.append(result)
                print(f"OK {path} HTTP {result['status']} latency={result['duration_s']:.3f}s")
                break
            except (RuntimeError, urllib.error.URLError, TimeoutError, OSError) as exc:
                # No response bodies, private data or exception URLs in CI output.
                if attempt:
                    message = str(exc) if isinstance(exc, RuntimeError) else type(exc).__name__
                    failures.append(f"{path}: {message}")
                else:
                    sleep(3)
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        lines = ["### RentMor public status (read-only)", "",
                 "| Path | HTTP | Latency |", "| --- | --- | --- |"]
        lines.extend(f"| \`{row['path']}\` | {row['status']} | {row['duration_s']:.3f}s |"
                     for row in successes)
        if failures:
            lines.extend(["", f"**Failed checks:** {len(failures)} (see sanitized job logs)"])
        try:
            with open(summary_path, "a", encoding="utf-8") as destination:
                destination.write("\n".join(lines) + "\n")
        except OSError:
            print("NOTE: GitHub job summary unavailable")
    if failures:
        raise SystemExit("RentMor public health check failed: " + "; ".join(failures))


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--self-test":
        assert CHECKS[-1] == ("/protocol_sync.php", 401, "auth")
        assert len(set(path for path, _, _ in CHECKS)) == len(CHECKS)
        assert BASE.startswith("https://")
        assert SLOW_SECONDS < TIMEOUT_SECONDS
        print("Public monitoring configuration self-test passed")
    else:
        run()
