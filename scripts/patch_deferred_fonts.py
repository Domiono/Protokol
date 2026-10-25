#!/usr/bin/env python3
"""Defer only optional icon and Google font stylesheets on the public website.

Patches the current SFTP index.php, never an outdated repository template.
Retains original CSS URLs, core Tailwind CSS, and a no-JS fallback.
"""
from html import unescape
from pathlib import Path
import re
import sys

TARGETS = {
    "icons": "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css",
    "roboto": "https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700;900&display=swap",
}
MARKER = "data-rentmor-deferred-style"
TAG_RE = re.compile(r"<link\b[^>]*>", re.I)
ATTR_RE = re.compile(r'([a-zA-Z_:][-\w:.]*)\s*=\s*(["\'])(.*?)\2', re.S)
TAILWIND_RE = re.compile(
    r'<link\b(?=[^>]*\bhref\s*=\s*["\']/assets/rentmor-tailwind-[0-9a-f]{12}\.css["\'])[^>]*>', re.I
)
PRECONNECT_MARKER = "<!-- rentmor-font-preconnect-v1 -->"

def attrs(tag: str) -> dict[str, str]:
    return {key.lower(): unescape(value) for key, _, value in ATTR_RE.findall(tag)}

def patch(source: str) -> str:
    if len(re.findall(r"</head>", source, re.I)) != 1 or len(TAILWIND_RE.findall(source)) != 1:
        raise ValueError("Expected actual public template with one static Tailwind stylesheet")
    if MARKER in source:
        found = [attrs(tag).get(MARKER, "") for tag in TAG_RE.findall(source) if MARKER in tag]
        if sorted(found) != sorted(TARGETS) or source.count(PRECONNECT_MARKER) != 1:
            raise ValueError("Incomplete previous deferred-font migration")
        for name, url in TARGETS.items():
            fallback = f'<noscript><link rel="stylesheet" href="{url.replace("&", "&amp;")}"></noscript>'
            if fallback not in source:
                raise ValueError("Missing no-JavaScript fallback: " + name)
        return source

    matches: dict[str,list[str]] = {}
    for match in TAG_RE.finditer(source):
        tag = match.group(0)
        a = attrs(tag)
        if a.get("rel", "").lower() == "stylesheet":
            for name, url in TARGETS.items():
                if a.get("href") == url:
                    matches.setdefault(name, []).append(tag)
    if any(len(matches.get(name, [])) != 1 for name in TARGETS):
        raise ValueError("Expected one Google Fonts and one Font Awesome stylesheet; manual review required")

    updated = source
    for name, url in TARGETS.items():
        original = matches[name][0]
        escaped_url = url.replace("&", "&amp;")
        deferred = (
            f'''<link rel="stylesheet" href="{escaped_url}" media="print" '''
            f'''onload="this.media='all'" {MARKER}="{name}">'''
            f'''<noscript><link rel="stylesheet" href="{escaped_url}"></noscript>'''
        )
        if updated.count(original) != 1:
            raise ValueError("Ambiguous stylesheet markup")
        updated = updated.replace(original, deferred, 1)
    connections = (
        PRECONNECT_MARKER + '\n'
        '<link rel="preconnect" href="https://fonts.googleapis.com">\n'
        '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>\n'
        '<link rel="preconnect" href="https://cdnjs.cloudflare.com" crossorigin>\n'
    )
    anchor = TAILWIND_RE.search(updated)
    if anchor is None:
        raise ValueError("Tailwind anchor disappeared")
    return updated[:anchor.start()] + connections + updated[anchor.start():]

if __name__ == "__main__":
    path = Path(sys.argv[1])
    before = path.read_text(encoding="utf-8")
    after = patch(before)
    if before != after:
        path.write_text(after, encoding="utf-8")
    print("Optional public styles load asynchronously; default icon and font fallbacks retained.")
