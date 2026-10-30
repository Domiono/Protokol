#!/usr/bin/env python3
"""Verify the actual server-rendered public header avoids old WebP srcset."""
from html.parser import HTMLParser
from pathlib import Path
import sys

URL = '/rentmor-logo.png?v=rm-20261030-wordmark-v2'

class Header(HTMLParser):
    def __init__(self):
        super().__init__()
        self.images = []
    def handle_starttag(self, tag, attrs):
        if tag == 'img':
            self.images.append(dict(attrs))

def verify(html):
    parser=Header()
    parser.feed(html)
    logos=[im for im in parser.images if '/rentmor-logo.png' in im.get('src','')]
    assert len(logos)==1, f'Expected exactly one new header logo, found {len(logos)}'
    logo=logos[0]
    assert logo['src']==URL, f'Incorrect logo src: {logo.get("src")}'
    assert logo.get('srcset')==URL+' 1x', f'Old optimized srcset still overrides logo: {logo.get("srcset")}'
    assert 'old' not in str(logo.get('srcset')) and 'webp' not in str(logo.get('srcset'))
    print('PASS public RentMor header uses approved RM PNG without old WebP candidates')

if __name__=='__main__':
    if len(sys.argv)!=2: raise SystemExit('Usage: verify_public_wordmark.py PUBLIC_HTML_FILE')
    verify(Path(sys.argv[1]).read_text(encoding='utf-8'))
