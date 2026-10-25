#!/usr/bin/env python3
"""Prepend canonical host rules while preserving all existing hosting rules."""
from pathlib import Path
import sys

MARKER = '# BEGIN RentMor canonical host v1'
RULES = '''# BEGIN RentMor canonical host v1
<IfModule mod_rewrite.c>
RewriteEngine On
# Canonicalize public page reads; do not change POST/API request semantics.
RewriteCond %{REQUEST_METHOD} ^(?:GET|HEAD)$
RewriteCond %{HTTP_HOST} ^www\\.rentmor\\.pl(?::[0-9]+)?$ [NC]
RewriteRule ^ https://rentmor.pl%{REQUEST_URI} [R=301,L,NE]
RewriteCond %{REQUEST_METHOD} ^(?:GET|HEAD)$
RewriteCond %{HTTP_HOST} ^rentmor\\.pl(?::[0-9]+)?$ [NC]
RewriteCond %{HTTPS} !=on
RewriteCond %{SERVER_PORT} !=443
RewriteCond %{HTTP:X-Forwarded-Proto} !^https$ [NC]
RewriteRule ^ https://rentmor.pl%{REQUEST_URI} [R=301,L,NE]
</IfModule>
# END RentMor canonical host v1

'''

def patch(text):
    if MARKER in text:
        if text.count(MARKER) != 1: raise ValueError('Duplicate redirect block')
        return text
    return RULES + text

if __name__ == '__main__':
    p = Path(sys.argv[1]); p.write_text(patch(p.read_text()))
    print('Canonical host rules prepended; existing routing and access rules preserved.')
