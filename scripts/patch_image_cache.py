#!/usr/bin/env python3
"""Cache only content-hashed WebP derivative filenames, preserve all other rules."""
import sys
from pathlib import Path

MARKER = "# BEGIN RentMor optimized image cache v1"
BLOCK = """
# BEGIN RentMor optimized image cache v1
<IfModule mod_headers.c>
    <FilesMatch "^[a-zA-Z0-9_.-]+-[a-f0-9]{12}-(card|detail)\\.webp$">
        Header set Cache-Control "public, max-age=31536000, immutable"
    </FilesMatch>
</IfModule>
# END RentMor optimized image cache v1
"""

def patch(text):
    if text.count(MARKER)>1: raise ValueError("Duplicate cache markers")
    return text if MARKER in text else text.rstrip()+"\n"+BLOCK

if __name__=="__main__":
    file=Path(sys.argv[1])
    original=file.read_text(encoding="utf-8")
    changed=patch(original)
    if changed!=original: file.write_text(changed,encoding="utf-8")
    print("Versioned WebP caching rules prepared.")
