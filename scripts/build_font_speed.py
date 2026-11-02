"""Prepare versioned local fonts and a compact copy of the current wordmark."""
import hashlib,io,json,re,sys
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.parse import quote
from fontTools.ttLib import TTFont
from fontTools import subset
from PIL import Image
OUT=Path(sys.argv[1]);OUT.mkdir(parents=True,exist_ok=True)
def get(url):
    return urlopen(Request(url,headers={'User-Agent':'Mozilla/5.0 Chrome/130.0.0.0 Safari/537.36'}),timeout=45).read()
def asset(prefix,data,ext):
    name=prefix+'-'+hashlib.sha256(data).hexdigest()[:12]+'.'+ext
    (OUT/name).write_bytes(data);return '/assets/'+name
# Include public templates, JS and all current sitemap pages so no used glyph is lost.
sources='\n'.join(p.read_text() for p in Path('server').glob('*.php'))
sources+='\n'.join(p.read_text() for p in Path('server').glob('*.js'))
sitemap=get('https://rentmor.pl/sitemap.xml').decode()
from concurrent.futures import ThreadPoolExecutor
urls=re.findall(r'<loc>(.*?)</loc>',sitemap)
assert all(u.startswith('https://rentmor.pl/') for u in urls)
with ThreadPoolExecutor(max_workers=6) as pool:
    sources+=''.join(b.decode() for b in pool.map(get,urls))
fa=get('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css').decode()
names=set(re.findall(r'fa-[a-z0-9-]+',sources));points=set()
for selector,code in re.findall(r'([^{}]+)\{content:"\\([a-f0-9]+)"\}',fa):
    if names.intersection(re.findall(r'fa-[a-z0-9-]+',selector)):points.add(int(code,16))
assert len(points)>=3
font=TTFont(io.BytesIO(get('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/webfonts/fa-solid-900.woff2')))
opts=subset.Options();opts.flavor='woff2';ss=subset.Subsetter(options=opts);ss.populate(unicodes=points);ss.subset(font)
b=io.BytesIO();font.flavor='woff2';font.save(b);icon_url=asset('rentmor-icons',b.getvalue(),'woff2')
fa=re.sub(r'@font-face\{[^}]*\}','',fa)
fa+='\n@font-face{font-family:"Font Awesome 6 Free";font-style:normal;font-weight:900;font-display:block;src:url("'+icon_url+'") format("woff2");}'
# Latin alphabet, Polish letters, typography and currency stay available for dynamic offers.
chars=''.join(chr(x) for x in range(32,383))+'–—…„”’€✓'
url='https://fonts.googleapis.com/css2?family=Roboto:wght@300..900&display=swap&text='+quote(chars)
css=get(url).decode()
for remote in set(re.findall(r'url\((https://[^)]+)\)',css)):
    css=css.replace(remote,asset('rentmor-roboto',get(remote),'woff2'))
css_url=asset('rentmor-type',(css+'\n'+fa).encode(),'css')
logo=Image.open(io.BytesIO(get('https://rentmor.pl/rentmor-logo.png?v=rm-20261030-wordmark-v2'))).convert('RGBA')
logo.thumbnail((672,672),Image.Resampling.LANCZOS);b=io.BytesIO();logo.save(b,format='WEBP',quality=95,method=6)
logo_url=asset('rentmor-wordmark',b.getvalue(),'webp')
(OUT/'manifest.json').write_text(json.dumps({'css':css_url,'logo':logo_url,'icons':icon_url}))
print('Font glyphs:',len(points),'Asset bytes:',sum(p.stat().st_size for p in OUT.iterdir()))
