#!/usr/bin/env python3
"""Read-only live checks after swapping public Tailwind CDN for versioned CSS."""
from html.parser import HTMLParser
from urllib.request import Request, urlopen
from urllib.parse import urlparse
import hashlib
import re

BASE="https://rentmor.pl"
PAGES=[
    "/",
    "/odkurzacz-pioracy-karcher-puzzi-10-1/",
    "/agregat-malarski-wabrotech-wt20i-wynajem-krakow-malowanie-natryskowe/",
    "/zestaw-szlifierka-stanley-zyrafa-do-gladzi-i-odkurzacz-bosch-metabo/"
]
CSS_RE=re.compile(r'/assets/rentmor-tailwind-([a-f0-9]{12})\.css')
class Document(HTMLParser):
    def __init__(self):
        super().__init__()
        self.styles=[]
        self.scripts=[]
        self.classes=set()
        self.feed_count=0
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=="link" and "stylesheet" in (a.get("rel") or "").lower().split():
            self.styles.append(a.get("href",""))
        if tag=="script" and a.get("src"):
            self.scripts.append(a["src"])
        self.classes.update((a.get("class") or "").split())

def request(path):
    url=BASE+path
    req=Request(url,headers={"User-Agent":"RentMorStaticCSSCheck/1.0","Cache-Control":"no-cache"})
    with urlopen(req,timeout=35) as result:
        assert result.status==200, (path,result.status)
        return result.read(1000000),result.headers.get("Content-Type","")

def verify():
    css_links=[]
    for path in PAGES:
        payload,_=request(path+"?static_tailwind_check=1")
        page=payload.decode("utf-8","replace")
        parsed=Document();parsed.feed(page)
        css=[url for url in parsed.styles if CSS_RE.fullmatch(url)]
        assert len(css)==1,(path,"missing or duplicate static CSS",parsed.styles)
        assert "cdn.tailwindcss.com" not in page,(path,"runtime compiler still present")
        assert all("cdn.tailwindcss.com" not in s for s in parsed.scripts)
        assert "rentmor-gallery.js" in page, (path,"gallery JS missing")
        assert "flex" in parsed.classes or "grid" in parsed.classes
        css_links+=css
    assert len(set(css_links))==1,("Different CSS assets on public pages",css_links)
    asset=css_links[0]
    data,media=request(asset)
    assert "text/css" in media.lower(), (asset,media)
    assert 8000 <= len(data) <= 240000,("Unexpected CSS payload length",len(data))
    expected=CSS_RE.fullmatch(asset).group(1)
    actual=hashlib.sha256(data).hexdigest()[:12]
    assert expected==actual,(expected,actual)
    compiled=data.decode("utf-8")
    for rule in [".grid{",".flex{",".font-bold{",".text-gray-700{"]:
        assert rule in compiled,("Missing essential CSS rule",rule)
    print(f"PASS: {len(PAGES)} public pages use the same static Tailwind CSS ({len(data)} bytes); hash, MIME, utilities and gallery verified.")

if __name__=="__main__":
    verify()
