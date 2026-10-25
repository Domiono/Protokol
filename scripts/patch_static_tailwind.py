#!/usr/bin/env python3
"""Safely switch the *public* index.php from runtime Tailwind v3 CDN to a versioned CSS file.

Accepts the public template in either old CDN state or already patched state.
Never touches admin.php or unrelated assets/scripts. Fails closed on ambiguous markup.
"""
import re
import sys
from pathlib import Path

CDN = re.compile(r'<script\b[^>]*\bsrc\s*=\s*["\']https://cdn\.tailwindcss\.com(?:/[^"\']*)?["\'][^>]*>\s*</script>', re.I)
STATIC = re.compile(r'<link\b[^>]*\brel\s*=\s*["\']stylesheet["\'][^>]*\bhref\s*=\s*["\']/assets/rentmor-tailwind-[a-f0-9]{12}\.css["\'][^>]*>', re.I)
URL = re.compile(r'^/assets/rentmor-tailwind-[a-f0-9]{12}\.css$')
def patch(template: str, css_url: str) -> str:
    if not URL.fullmatch(css_url):
        raise ValueError("Invalid content-addressed CSS URL")
    cdn = CDN.findall(template)
    css = STATIC.findall(template)
    if len(cdn) + len(css) != 1:
        raise ValueError(f"Expected exactly one Tailwind source; CDN={len(cdn)} static={len(css)}")
    if len(re.findall(r'</head>', template, re.I)) != 1:
        raise ValueError("Unknown page template")
    if re.search(r'tailwind\s*\.\s*config\s*=', template):
        raise ValueError("Runtime Tailwind configuration needs manual migration")
    tag = f'<link rel="stylesheet" href="{css_url}">'
    if cdn:
        return CDN.sub(lambda _: tag, template, count=1)
    return STATIC.sub(lambda _: tag, template, count=1)

def main():
    src = Path(sys.argv[1]); url = sys.argv[2]
    before = src.read_text(encoding="utf-8")
    after = patch(before, url)
    if before != after:
        src.write_text(after, encoding="utf-8")
    print(f"Static public Tailwind source: {url}.")

if __name__ == "__main__":
    main()
