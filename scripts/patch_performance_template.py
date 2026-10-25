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
    if MARKER in content:
        # Force a new JS URL because v2 is already cached on visitors' browsers.
        return content.replace('/rentmor-gallery.js?v=2', '/rentmor-gallery.js?v=3')
    match = list(re.finditer(r"<!doctype\s+html\s*>", content, flags=re.I))
    if len(match) != 1 or "<?php" not in content:
        raise ValueError("Unexpected public homepage DOCTYPE layout")
    pos = match[0].start()
    updated = content[:pos] + INCLUDE + content[pos:]
    # Bust the gallery JS URL once so full-resolution lightbox behavior takes effect.
    return updated.replace('/rentmor-gallery.js?v=1', '/rentmor-gallery.js?v=3').replace('/rentmor-gallery.js?v=2', '/rentmor-gallery.js?v=3')

if __name__ == "__main__":
    f = Path(sys.argv[1])
    original = f.read_text(encoding="utf-8")
    updated = patch(original)
    if updated != original: f.write_text(updated,encoding="utf-8")
    print("Responsive images HTML middleware prepared.")
