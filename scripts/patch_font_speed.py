import json,re,sys
from pathlib import Path

def patch(s,m):
    # Keep existing asynchronous stylesheet activation and noscript fallbacks.
    s=re.sub(r'https://cdnjs\.cloudflare\.com/ajax/libs/font-awesome/6\.4\.0/css/all\.min\.css',m['css'],s)
    s=re.sub(r'https://fonts\.googleapis\.com/css2\?[^"\x27<>]+',m['css'],s)
    s=re.sub(r'/assets/rentmor-type-[a-f0-9]{12}\.css',m['css'],s)
    # Replace only URLs in logo tags; no product images or PHP expressions.
    def logo(x):
        t=x.group()
        if '/rentmor-logo.png' not in t and '/assets/rentmor-wordmark-' not in t:return t
        return re.sub(r'(?:/rentmor-logo\.png(?:\?[^"\s<>]+)?|/assets/rentmor-wordmark-[a-f0-9]{12}\.webp)',m['logo'],t)
    s=re.sub(r'<img\b[^>]*>',logo,s)
    # Move the existing mobile hero resource hint before other style resources.
    p=re.search(r'<\?php if \(empty\(\$detail_view\)\): \?>\s*<link rel="preload" as="image"[^>]+>\s*<\?php endif; \?>',s)
    if p:
        hint=p.group();s=s[:p.start()]+s[p.end():];s=re.sub(r'<head>\s*',lambda _: '<head>\n'+hint+'\n',s,count=1)
    assert m['css'] in s and m['logo'] in s
    return s
if __name__=='__main__':
    p=Path(sys.argv[1]);m=json.loads(Path(sys.argv[2]).read_text());s=p.read_text();u=patch(s,m);assert patch(u,m)==u;p.write_text(u)
