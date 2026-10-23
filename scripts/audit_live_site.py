#!/usr/bin/env python3
"""Audit only public SEO / display structure of a live RentMor FTP snapshot.
Never print full PHP source or credentials.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

folder=Path(sys.argv[1])
print("FILES:", [(p.name,p.stat().st_size) for p in folder.iterdir() if p.is_file()])

ht=folder/".htaccess"
if ht.exists():
    print("\nHTACCESS routing/SEO directives:")
    for n,line in enumerate(ht.read_text("utf-8","replace").splitlines(),1):
        if re.search(r"RewriteRule|RewriteCond|Redirect|sitemap|canonical|robots",line,re.I):
            print(f"L{n}: {line[:260]}")

robots=folder/"robots.txt"
if robots.exists():print("\nROBOTS:\n",robots.read_text("utf-8","replace")[:2500])

for name in ("sitemap.xml","sitemap.php","sitemap_index.xml"):
    path=folder/name
    if not path.exists():continue
    print(f"\n{name} ({path.stat().st_size} bytes):")
    content=path.read_text("utf-8","replace")
    if name.endswith(".xml"):
        try:
            root=ET.fromstring(content)
            urls=[e.text.strip() for e in root.iter() if e.tag.endswith("loc") and e.text]
            print("URLs:",len(urls))
            for url in urls[:65]:print("  ",url)
        except Exception as e:print("Bad XML",str(e)[:250],content[:800])
    else:
        print("CURRENT GENERATOR:\n"+content[:2200])
        lines=content.splitlines()
        for n,line in enumerate(lines,1):
            if re.search(r"xml|sitemap|terms|zasad|regulamin|products|data.json|route|url|lastmod",line,re.I):
                print(f"L{n}: {line[:220]}")

path=folder/"index.php"
if path.exists():
    lines=path.read_text("utf-8","replace").splitlines()
    print("\nINDEX PHP:",len(lines),"lines")
    patterns=[
        r"terms|zasad|regulamin|sitemap|robots",
        r"product.description|product.desc|\\['desc'\\]|\\[\"desc\"\\]|\\.desc\\b",
        r"nl2br|whitespace-pre-line|whitespace-pre-wrap|prose|text-justify|leading-[0-9]|break-words|overflow-wrap",
        r"product.*description|opis.*produktu|Opis sprzętu",
        r"container|max-w-[0-9a-z\\[\\]-]+|grid-cols-|md:col-span|w-full",
        r"@media\s*\(max-width",
    ]
    for idx, pat in enumerate(patterns):
        matches=[i for i,line in enumerate(lines) if re.search(pat,line,re.I)]
        print("PATTERN",idx,pat,"MATCHES",len(matches))
        for i in matches[:32 if idx<=3 else 12]:
            lo=max(0,i-2);hi=min(len(lines),i+3)
            print("  ", " | ".join(f"{j+1}:{lines[j].strip()[:220]}" for j in range(lo,hi) if not re.search(r"token|password|secret|chat_id|hasz_hasla|telegram|botapi|mail_do_pow",lines[j],re.I))[:650])

for name in ("styles.css","style.css","main.css"):
    path=folder/name
    if not path.exists():continue
    print("\nCSS",name,path.stat().st_size)
    lines=path.read_text("utf-8","replace").splitlines()
    for i,line in enumerate(lines):
        if re.search(r"product|desc|opis|prose|description|@media",line,re.I):
            print(" ",i+1,line[:200])

print("\\nFILE LIST (public php/xml/txt/css):")
fileList=folder/"file-list.txt"
if fileList.exists():
    for filename in fileList.read_text("utf-8","replace").splitlines():
        if re.search(r"\\.(php|xml|txt|css)$",filename,re.I):
            print(" ",filename[:150])
if path.exists():
    print("\\nPRODUCT DETAIL TEMPLATE (selected lines 880-1020):")
    lines=path.read_text("utf-8","replace").splitlines()
    for n in range(880,min(len(lines),1021)):
        line=lines[n]
        if not re.search(r"token|password|secret|chat_id|hasz_hasla|telegram",line,re.I):
            print(str(n+1)+": "+line[:260])
