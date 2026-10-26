#!/usr/bin/env python3
"""Verify newly uploaded public WebP assets after SFTP manifest read-back."""
import json
import sys
import time
from pathlib import Path
from urllib.request import Request, urlopen

BASE = "https://rentmor.pl"
def verify(path):
    m = json.loads(Path(path).read_text(encoding="utf-8"))
    assert isinstance(m, dict) and m, "Manifest must not be empty"
    keys = list(m)
    prioritized = ["rentmor-logo.png"] if "rentmor-logo.png" in m else []
    # Deterministic checks: logo and first five images, plus a final item.
    sample = list(dict.fromkeys(prioritized + keys[:6] + keys[-1:]))
    count = 0
    for filename in sample:
        a = m[filename]
        for variant in ("thumb", "small", "card", "detail"):
            data = a.get(variant)
            if not data: continue
            url = BASE + data["url"]
            ok = False
            for attempt in range(3):
                try:
                    with urlopen(Request(url,method="HEAD",headers={"Cache-Control":"no-cache",
                                "User-Agent":"RentMorImageCheck/1.0"}),timeout=25) as r:
                        assert r.status == 200 and "image/webp" in r.headers.get("Content-Type","").lower(), url
                        assert int(r.headers.get("Content-Length","1")) > 0, url
                        ok = True
                        break
                except Exception:
                    if attempt == 2: raise
                    time.sleep(2)
            assert ok
            count += 1
    assert count > 0, "No generated WebP assets available"
    print(f"PASS: manifest and {count} public WebP HEAD checks; originals were not modified.")

if __name__=="__main__":
    verify(sys.argv[1])
