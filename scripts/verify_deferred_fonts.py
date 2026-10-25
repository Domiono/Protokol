#!/usr/bin/env python3
"""Live safeguards for async optional CSS: checks home and one product page."""
from html.parser import HTMLParser
from urllib.request import Request, urlopen
from patch_deferred_fonts import TARGETS, MARKER

BASE="https://rentmor.pl"
PAGES=["/","/odkurzacz-pioracy-karcher-puzzi-10-1/"]
class Head(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links=[]
    def handle_starttag(self,tag,attrs):
        if tag=="link":self.links.append(dict(attrs))
def verify():
    for page in PAGES:
        with urlopen(Request(BASE+page+"?rentmor_font_check=1",headers={
                "User-Agent":"RentMorAsyncFontsAudit/1.0",
                "Cache-Control":"no-cache"}),timeout=25) as reply:
            assert reply.status==200,reply.status
            html=reply.read().decode("utf-8","replace")
        parser=Head();parser.feed(html)
        for name,url in TARGETS.items():
            links=[a for a in parser.links if a.get("href")==url]
            assert len(links)==2,(page,name,"expected async + noscript")
            primary=[a for a in links if a.get(MARKER)==name]
            assert len(primary)==1,(page,name,"async stylesheet missing")
            assert primary[0].get("rel")=="stylesheet"
            assert primary[0].get("media")=="print"
            assert primary[0].get("onload")=="this.media='all'"
            assert '<noscript><link rel="stylesheet"' in html
        assert 'href="https://fonts.gstatic.com" crossorigin' in html
        assert "/assets/rentmor-tailwind-" in html
        assert "rentmor-gallery.js?v=3" in html
        print("PASS: optional icon and font CSS are nonblocking with no-JS fallbacks:",page)
if __name__=="__main__":verify()
