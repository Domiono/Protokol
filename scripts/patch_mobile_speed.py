"""Narrow, idempotent changes to the current hosting-owned public template."""
import re
import sys
from pathlib import Path

ASSET = '/assets/rentmor-blueprint-mobile-20261102.webp'
MARKER = 'rentmor-mobile-speed-v1'
STYLE = f'''<!-- {MARKER} -->
<?php if (empty($detail_view)): ?>
<link rel="preload" as="image" href="{ASSET}" media="(max-width: 899px)" fetchpriority="high">
<?php endif; ?>
<style id="{MARKER}">
@media (max-width: 899px) {{
 header[data-rentmor-blueprint="v1"] {{
  background-image: linear-gradient(90deg,rgba(2,15,36,.10),rgba(2,15,36,.16),rgba(2,15,36,.10)),url("{ASSET}") !important;
 }}
}}
</style>
<!-- /{MARKER} -->
'''
CACHE = '''
# BEGIN RentMor public static cache v2
<IfModule mod_headers.c>
 <FilesMatch "\\.(webp|png|jpg|jpeg|gif|ico|woff2?|css|js)$">
  Header set Cache-Control "public, max-age=604800"
 </FilesMatch>
 <FilesMatch "^rentmor-blueprint-(mobile-20261102|final-20261030)\\.webp$">
  Header set Cache-Control "public, max-age=31536000, immutable"
 </FilesMatch>
</IfModule>
# END RentMor public static cache v2
'''
def patch(source):
    if source.count('</head>') != 1 or 'data-rentmor-blueprint="v1"' not in source:
        raise ValueError('Unknown public template; refusing to patch')
    found = 0
    def phone(m):
        nonlocal found
        tag = m.group()
        if 'top_nav' not in tag: return tag
        if not re.search(r'href=[\"\x27]tel:',tag): raise ValueError('Unexpected phone link')
        found += 1
        if 'aria-label=' not in tag: tag = tag[:-1] + ' aria-label="Zadzwoń do RentMor">'
        return tag
    source = re.sub(r'<a\b[^>]*>',phone,source,flags=re.S)
    if found != 1: raise ValueError(f'Expected one top phone link, got {found}')
    source = re.sub(r'(<i\b)([^>]*class="[^"]*fa-phone[^"]*"[^>]*)(>)',
        lambda m: m.group() if 'aria-hidden=' in m.group() else m[1]+m[2]+' aria-hidden="true"'+m[3],source)
    if f'<!-- {MARKER} -->' not in source:
        source = source.replace('</head>', STYLE+'</head>')
    return source

def cache(source):
    return source if '# BEGIN RentMor public static cache v2' in source else source.rstrip()+'\n'+CACHE

if __name__ == '__main__':
    p=Path(sys.argv[1]); f=cache if p.name=='.htaccess' else patch
    p.write_text(f(p.read_text()))
