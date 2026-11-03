#!/usr/bin/env python3
"""Read-only audit of public RentMor HTML relevant to the owner's WCAG report.
Print only limited public element metadata; never submit forms or read admin endpoints.
"""
from html.parser import HTMLParser
from urllib.request import Request, urlopen
import re

class Scan(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tags=[]
        self.headings=[]
        self.h=None
    def handle_starttag(self, tag, attrs):
        a=dict(attrs)
        if tag in ('html','main','nav','header','footer','input','button','summary','iframe','label') or (tag in ('a','div','section') and any(k in a for k in ('data-rm-cookie-settings','id','role','aria-label','aria-labelledby','aria-live','aria-hidden'))):
            if tag=='button' and 'filter-btn' not in a.get('class','') and 'data-rm-cookie-settings' not in a: return
            if tag=='a' and 'data-rm-cookie-settings' not in a: return
            if tag=='div' and not (a.get('id','') in ('map','mapa','map-container','products','products-grid') or 'map' in a.get('class','').lower() or 'map' in a.get('id','').lower()): return
            self.tags.append((tag,{k:v[:130] if isinstance(v,str) else v for k,v in a.items() if k in ('id','role','type','class','href','lang','placeholder','aria-label','aria-labelledby','aria-pressed','aria-current','aria-hidden','data-rm-cookie-settings','onclick','alt')}, self.getpos()[0]))
        if tag in ('h1','h2','h3','h4'):
            self.h=(tag,'',self.getpos()[0])
    def handle_data(self, data):
        if self.h:self.h=(self.h[0],self.h[1]+data,self.h[2])
    def handle_endtag(self,tag):
        if self.h and self.h[0]==tag:
            self.headings.append((self.h[0],self.h[1][:95].strip(),self.h[2]))
            self.h=None

for path in ('/','/odkurzacz-pioracy-karcher-puzzi-10-1/','/sprzet-budowlany/'):
    print('WCAG_PUBLIC_PATH',path)
    req=Request('https://rentmor.pl'+path, headers={'User-Agent':'RentMorPublicWcagProbe/1.0','Cache-Control':'no-cache'})
    with urlopen(req,timeout=35) as r:
        html=r.read().decode('utf-8','replace')
        print('STATUS',r.status,'HTML_CHARS',len(html))
    scan=Scan();scan.feed(html)
    print('PUBLIC_ELEMENTS',scan.tags[:55])
    print('PUBLIC_HEADINGS',scan.headings[:16])
    print('HAS_MAP_LINK',bool(re.search(r'href="https?://[^"]*maps',html)))
    print('COOKIE_CTA',re.findall(r'<(?:a|button)\\b[^>]*data-rm-cookie-settings[^>]*>',html)[:2])
    print('SEARCH_TAG',re.findall(r'<input\\b[^>]*id="searchInput"[^>]*>',html)[:1])
    print('FILTER_TAGS',re.findall(r'<button\\b[^>]*class="[^"]*filter-btn[^"]*"[^>]*>',html)[:5])
    for term in ('id="searchInput"', 'class="filter-btn', 'Gdzie jesteśmy?', 'data-rm-cookie-settings', '<iframe', 'id="products"', 'id="catalog'):
        idx=html.find(term,html.lower().find('<body'))
        if idx>=0:
            print('BODY_SNIPPET',term,repr(html[max(html.lower().find('<body'),idx-330):idx+680].replace('\\n',' ')[:1010]))
    print('MAP_SNIPPETS', [html[max(0,m.start()-180):m.end()+230].replace('\\n',' ') for m in list(re.finditer('Gdzie jesteśmy|mapa-dojazdu|iframe',html[html.lower().find('<body'):],re.I))[-3:]])


# The crawl provider flagged directory browsing. Distinguish a real Apache
# listing from a 200-page rewrite; do not print filenames or private content.
from urllib.error import HTTPError
for folder in ('/uploads/', '/uploads/rentmor-webp/', '/assets/', '/.rentmor-backups/'):
    req = Request('https://rentmor.pl' + folder,
        headers={'User-Agent':'RentMorPublicDirectoryAudit/1.0','Cache-Control':'no-cache'})
    try:
        with urlopen(req, timeout=25) as response:
            snippet = response.read(45000).decode('utf-8','replace')
            is_listing = bool(re.search(r'<title>\s*Index of\b|<h1>\s*Index of\b|Directory listing for', snippet, re.I))
            print('PUBLIC_DIRECTORY_STATUS',folder,response.status,'listing_detected',is_listing)
    except HTTPError as exc:
        print('PUBLIC_DIRECTORY_STATUS',folder,exc.code,'listing_detected',False)
