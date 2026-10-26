"""Keep public first paint independent of the external utility CSS round trip."""
import hashlib,re,sys
from pathlib import Path
from patch_static_tailwind import STATIC
START='<!-- rentmor-critical-css-v1 -->'
END='<!-- /rentmor-critical-css-v1 -->'

def patch(source,css,gallery):
    if '</style' in css.lower() or '</style' in gallery.lower():raise ValueError('Invalid stylesheet content')
    if not 8000<len(css)<45000 or not 1000<len(gallery)<15000:raise ValueError('Unexpected CSS size')
    links=STATIC.findall(source)
    if len(links)!=1:raise ValueError('Expected one public Tailwind link')
    digest=hashlib.sha256(css.encode()).hexdigest()[:12]
    if f'rentmor-tailwind-{digest}.css' not in links[0]:raise ValueError('CSS hash does not match template')
    if START in source:
        if source.count(START)!=1 or source.count(END)!=1:raise ValueError('Ambiguous critical style')
        source=re.sub(re.escape(START)+'.*?'+re.escape(END)+'\n?', '',source,flags=re.S)
    # Keep the hashed cached stylesheet as a nonblocking fallback. Inline content
    # is exactly the compiled public CSS, including responsive rules and preflight.
    old=STATIC.findall(source)[0]
    new=re.sub(r'\s+(?:media|onload)="[^"]*"','',old)
    new=new[:-1]+''' media="print" onload="this.media='all'">'''
    source=source.replace(old,START+'\n<style data-rm-critical="1">'+css+'\n'+gallery+'</style>\n'+END+'\n'+new,1)
    gl='<link rel="stylesheet" href="/rentmor-gallery.css?v=1">'
    gd='''<link rel="stylesheet" href="/rentmor-gallery.css?v=1" media="print" onload="this.media='all'">'''
    if source.count(gl)+source.count(gd)!=1:raise ValueError('Unexpected gallery CSS markup')
    source=source.replace(gl,gd)
    # Each image role is assigned only in the audited PHP gallery sections.
    for anchor,role in [('class="rm-gallery rm-card-gallery"','card'),('class="rm-gallery rm-detail-gallery"','detail'),('class="rm-gallery-thumb ','thumb')]:
        if source.count(anchor)!=1:raise ValueError('Unknown image section: '+role)
        pos=source.index('<img ',source.index(anchor))
        marker='data-rm-image-kind="'+role+'" '
        if source.startswith('<img '+marker,pos):continue
        source=source[:pos]+source[pos:].replace('<img ','<img '+marker,1)
    old="require_once __DIR__ . '/performance_images.php';"
    new="require_once __DIR__ . '/performance_images_v2.php';"
    if source.count(old)+source.count(new)!=1:raise ValueError('Unknown image middleware include')
    return source.replace(old,new)

if __name__=='__main__':
    p=Path(sys.argv[1]);p.write_text(patch(p.read_text(),Path(sys.argv[2]).read_text(),Path(sys.argv[3]).read_text()))
