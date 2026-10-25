#!/usr/bin/env python3
"""Enable image optimization only after verified PHP helper + manifest are deployed."""
from pathlib import Path
import re
import sys

MARKER = '<!-- rentmor-responsive-images-v1 -->'
INCLUDE = """<?php
/* <!-- rentmor-responsive-images-v1 --> */
require_once __DIR__ . '/performance_images.php';
rentmor_start_image_optimizer();
?>
"""

def patch(content: str) -> str:
    if content.count(MARKER) > 1: raise ValueError("Duplicate responsive images marker")
    if MARKER in content: return content
    match = list(re.finditer(r"<!doctype\s+html\s*>", content, flags=re.I))
    if len(match) != 1 or "<?php" not in content:
        raise ValueError("Unexpected public homepage DOCTYPE layout")
    pos = match[0].start()
    return content[:pos] + INCLUDE + content[pos:]

if __name__ == "__main__":
    f = Path(sys.argv[1])
    original = f.read_text(encoding="utf-8")
    updated = patch(original)
    if updated != original: f.write_text(updated,encoding="utf-8")
    print("Responsive images HTML middleware prepared.")
