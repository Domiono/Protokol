#!/usr/bin/env python3
"""Update only icon metadata; preserve all visible logo/image markup."""
from pathlib import Path
import re, sys
VERSION = 'rm-20261030'
START = '<!-- rentmor-brand-icons-v1 -->'
END = '<!-- /rentmor-brand-icons-v1 -->'
def patch(text):
    text = re.sub(re.escape(START)+r'.*?'+re.escape(END)+r'\s*', '', text, flags=re.S)
    def icon(tag):
        if not re.search(r'\brel\s*=\s*[\"\x27]icon[\"\x27]', tag, re.I):
            return tag
        tag = re.sub(r'\s+sizes\s*=\s*[\"\x27][^\"\x27]*[\"\x27]', '', tag, flags=re.I)
        return tag.replace('rel="icon"', 'rel="icon" sizes="64x64"')
    text = re.sub(r'<link\b[^>]*>', lambda m: icon(m[0]), text, flags=re.I)
    if re.search(r'<link\b[^>]*rel\s*=\s*[\"\x27](manifest|apple-touch-icon)[\"\x27]', text, re.I):
        raise ValueError('Existing manifest/Apple icon requires explicit reconciliation')
    block = f'''{START}
<link rel="apple-touch-icon" sizes="180x180" href="/assets/{VERSION}-180.png">
<link rel="manifest" href="/assets/{VERSION}.webmanifest">
<link rel="shortcut icon" href="/assets/{VERSION}.ico">
{END}
'''
    if text.lower().count('</head>') != 1:
        raise ValueError('Expected one head')
    return re.sub(r'</head>', lambda m: block+m[0], text, count=1, flags=re.I)
if __name__ == '__main__':
    path = Path(sys.argv[1]); path.write_text(patch(path.read_text()))
