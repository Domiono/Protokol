#!/usr/bin/env python3
"""Read-only inventory of *public* site resources, safe to run in CI."""
from html.parser import HTMLParser
from urllib.request import Request, urlopen
from urllib.parse import urljoin, urlparse
from collections import Counter
import json

URLS = ["https://rentmor.pl/", "https://rentmor.pl/odkurzacz-pioracy-karcher-puzzi-10-1/"]

class Audit(HTMLParser):
    def __init__(self):
        super().__init__()
        self.images = []; self.scripts=[]; self.styles=[]; self.picture=0
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "img": self.images.append(a)
        if tag == "script": self.scripts.append(a.get("src","[inline]"))
        if tag == "link" and a.get("rel") == "stylesheet": self.styles.append(a.get("href"))
        if tag == "picture": self.picture+=1

def run():
    for url in URLS:
        with urlopen(Request(url,headers={"User-Agent":"RentMorPerformanceAudit/1.0"}), timeout=30) as r:
            html=r.read().decode("utf-8", errors="replace")
        audit=Audit(); audit.feed(html)
        print(json.dumps({"page":url,"html_bytes":len(html.encode()),
          "images":len(audit.images), "pictures":audit.picture,
          "lazy":sum(a.get("loading")=="lazy" for a in audit.images),
          "dimensions":sum("width" in a and "height" in a for a in audit.images),
          "srcsets":sum("srcset" in a for a in audit.images),
          "image_src_sample":[{k:a.get(k) for k in ("src","loading","fetchpriority","width","height","class") if k in a}
                    for a in audit.images[:5]],
          "script_sources":audit.scripts[:15],"stylesheet_sources":audit.styles[:15],
          "tailwind_cdn":"cdn.tailwindcss.com" in html},ensure_ascii=False))
        if url.endswith("/"):
            for raw in list(dict.fromkeys(a.get("src") for a in audit.images if a.get("src")))[:12]:
                u=urljoin(url,raw)
                if urlparse(u).hostname!="rentmor.pl":continue
                try:
                    with urlopen(Request(u,method="HEAD",headers={"User-Agent":"RentMorPerformanceAudit/1.0"}),timeout=12) as r:
                        print(json.dumps({"asset":urlparse(u).path,"bytes":r.headers.get("content-length"),"type":r.headers.get("content-type")}))
                except Exception as ex:
                    print(json.dumps({"asset":urlparse(u).path,"error":type(ex).__name__}))

if __name__=="__main__": run()
