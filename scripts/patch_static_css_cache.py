#!/usr/bin/env python3
"""Add long-lived browser caching only for content-hashed static public CSS."""
from pathlib import Path
import sys

MARKER="# BEGIN RentMor static Tailwind CSS cache v1"
BLOCK="""
# BEGIN RentMor static Tailwind CSS cache v1
<IfModule mod_headers.c>
    <FilesMatch "^rentmor-tailwind-[a-f0-9]{12}\\.css$">
        Header set Cache-Control "public, max-age=31536000, immutable"
    </FilesMatch>
</IfModule>
# END RentMor static Tailwind CSS cache v1
"""
def patch(text):
    if text.count(MARKER)>1:raise ValueError("Duplicate Tailwind CSS cache marker")
    return text if MARKER in text else text.rstrip()+"\n"+BLOCK

if __name__=="__main__":
    p=Path(sys.argv[1]); old=p.read_text(encoding="utf-8")
    new=patch(old)
    if new!=old:p.write_text(new,encoding="utf-8")
    print("Static Tailwind CSS immutable cache rule prepared.")
