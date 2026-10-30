#!/usr/bin/env python3
"""Cache-bust one public header wordmark image without touching other PHP content."""
import re
import sys
from pathlib import Path

VERSION = 'rm-20261030-wordmark-v2'
URL = '/rentmor-logo.png?v=' + VERSION
IMG = re.compile(r'<img\b[^>]*>', re.I | re.S)
SRC = re.compile(r'\bsrc\s*=\s*(["\x27])(?P<url>[^"\x27]+)\1', re.I)

def patch(source: str) -> str:
    body = re.search(r'<body\b', source, re.I)
    if not body:
        raise ValueError('No body in hosting template')
    main = re.search(r'<main\b|<h1\b', source[body.end():], re.I)
    limit = body.end() + main.start() if main else min(len(source), body.end() + 16000)
    found = 0

    def refresh(match):
        nonlocal found
        tag = match.group()
        attr = SRC.search(tag)
        if not attr:
            return tag
        old = attr.group('url').split('?', 1)[0]
        is_logo = old == '/rentmor-logo.png' or ('rentmor-logo' in old.lower() and 'favicon' not in old.lower())
        if not is_logo:
            return tag
        found += 1
        updated = tag[:attr.start('url')] + URL + tag[attr.end('url'):]
        # A single PNG source prevents a legacy optimizer from selecting old WebP.
        updated = re.sub(r'\s+(?:srcset|sizes)\s*=\s*(["\x27]).*?\1', '', updated, flags=re.I | re.S)
        suffix = '/>' if updated.endswith('/>') else '>'
        return updated[:-len(suffix)] + ' srcset="' + URL + ' 1x"' + suffix

    updated = IMG.sub(refresh, source[:limit])
    if found != 1:
        raise ValueError(f'Expected exactly one RentMor header logo image, found {found}; refusing update')
    return updated + source[limit:]

if __name__ == '__main__':
    path = Path(sys.argv[1])
    original = path.read_text(encoding='utf-8')
    updated = patch(original)
    if updated != original:
        path.write_text(updated, encoding='utf-8')
    print('Public header logo cache URL updated safely')
