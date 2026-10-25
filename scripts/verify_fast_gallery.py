#!/usr/bin/env python3
"""Ensure logo/favicon and carousel deferral work on actual public HTML."""
from html.parser import HTMLParser
from urllib.request import Request,urlopen
from urllib.parse import urljoin
import re
from io import BytesIO
BASE="https://rentmor.pl"
class ImgParser(HTMLParser):
    def __init__(self):super().__init__();self.images=[];self.links=[];self.slides=[];self.current_slide=None
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=="link":self.links.append(a)
        if tag=="div" and "rm-gallery-slide" in (a.get("class") or "").split():
            self.slides.append({"active":"rm-active" in (a.get("class") or "").split(),"image":None})
        if tag=="img":
            self.images.append(a)
            if self.slides and self.slides[-1]["image"] is None:self.slides[-1]["image"]=a

def get(path):
    with urlopen(Request(urljoin(BASE,path),headers={"User-Agent":"RentMorFastGalleryVerification/1.0",
                 "Cache-Control":"no-cache"}),timeout=30) as r:
        assert r.status==200
        return r.read(500000)
def main():
    home=get("/?deferred_gallery_verification=1").decode("utf-8","replace")
    assert "/rentmor-gallery.js?v=3" in home and "cdn.tailwindcss.com" not in home
    p=ImgParser();p.feed(home)
    active=[s for s in p.slides if s["active"] and s["image"]]
    inactive=[s for s in p.slides if not s["active"] and s["image"]]
    assert len(active)>=15 and len(inactive)>=15,(len(active),len(inactive))
    assert all(s["image"].get("src") for s in active)
    assert all(not s["image"].get("src") and s["image"].get("data-rm-src") for s in inactive)
    assert all(not s["image"].get("srcset") for s in inactive)
    assert all(s["image"].get("data-rm-srcset") or s["image"].get("data-rm-src") for s in inactive)
    icons=[x.get("href","") for x in p.links if x.get("rel","").lower()=="icon"]
    hashed=[v for v in icons if re.fullmatch(r"/assets/rentmor-favicon-[0-9a-f]{12}\.png",v)]
    assert len(hashed)==1,icons
    favicon=get(hashed[0])
    assert favicon.startswith(b"\x89PNG\r\n\x1a\n") and 100<len(favicon)<30000
    js=get("/rentmor-gallery.js?v=3").decode("utf-8")
    assert "hydrateSlide(slide)" in js and "image.src = slideImage.src" in js
    print("PASS: %d active images available, %d inactive deferred; optimized favicon %d bytes; gallery hydration v3 present."%
          (len(active),len(inactive),len(favicon)))
    detail=get("/odkurzacz-pioracy-karcher-puzzi-10-1/").decode("utf-8","replace")
    assert "/rentmor-gallery.js?v=3" in detail
    print("PASS: product page uses optimized public gallery assets.")

if __name__=="__main__":main()
