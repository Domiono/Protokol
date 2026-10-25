#!/usr/bin/env python3
"""Build a compact Tailwind v3 stylesheet from the latest hosting PHP + public rendering.

Usage: python3 scripts/build_public_tailwind.py LIVE_INDEX REPO_DIR OUTPUT_DIR TAILWIND_BIN
The pinned Tailwind v3 CLI dependency is installed by CI, never used in visitors' browsers.
"""
import hashlib
import html
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.request import Request, urlopen
from urllib.parse import urlparse
from xml.etree import ElementTree

from patch_static_tailwind import CDN, STATIC

BASE = "https://rentmor.pl"
REQUIRED = [
    ".flex{", ".grid{", ".font-bold{", ".items-center{", ".text-gray-700{",
    ".rounded-2xl{", ".lg\\:grid-cols-3{",
]
class Classes(HTMLParser):
    def __init__(self):
        super().__init__()
        self.values = set()
    def handle_starttag(self, tag, attrs):
        for key, val in attrs:
            if key == "class" and val:
                self.values.update(str(val).split())

def get(url):
    req=Request(url,headers={"User-Agent":"RentMorTailwindStaticBuild/1.0","Cache-Control":"no-cache"})
    with urlopen(req,timeout=25) as response:
        if response.status!=200: raise RuntimeError("Unexpected public status")
        return response.read(2_000_000).decode("utf-8","replace")

def observe_public_classes(destination: Path):
    parser=Classes()
    url_candidates=[BASE+"/"]
    try:
        xml=ElementTree.fromstring(get(BASE+"/sitemap.xml"))
        products=[]
        for element in xml.findall("{*}url/{*}loc"):
            if not element.text: continue
            u=element.text
            p=urlparse(u)
            if p.netloc=="rentmor.pl" and p.scheme=="https" and p.path.endswith("/"):
                products.append(u)
        url_candidates += products[:40]
    except Exception as error:
        print("Sitemap unavailable; using homepage and template classes:", type(error).__name__)
    ok=0
    for url in list(dict.fromkeys(url_candidates)):
        try:
            page=get(url)
            parser.feed(page)
            ok+=1
        except Exception as exc:
            print("Public class collection skipped a page:",type(exc).__name__)
    if not ok:
        raise RuntimeError("No public page could be sampled before CSS generation")
    escaped=[html.escape(v,quote=True) for v in sorted(parser.values) if "\n" not in v and len(v)<150]
    destination.write_text('\n'.join('<span class="'+c+'"></span>' for c in escaped),encoding="utf-8")
    return ok, len(escaped)

def build(index: Path, repo: Path, output: Path, cli: Path):
    output.mkdir(parents=True,exist_ok=True)
    contents=index.read_text(encoding="utf-8")
    if len(CDN.findall(contents))+len(STATIC.findall(contents))!=1:
        raise ValueError("Could not find exactly one Tailwind source in PHP")
    observed=output/"observed-classes.html"
    pages, classes=observe_public_classes(observed)
    # Helpers and JS may contain classes not rendered in the current catalogue.
    globs=[
      str(index),
      str(observed),
      str(repo/"server"/"**"/"*.php"),
      str(repo/"server"/"**"/"*.js"),
      str(repo/"scripts"/"**"/"*.py")
    ]
    configuration=output/"tailwind.config.cjs"
    configuration.write_text("module.exports = "+json.dumps({"content":globs,"theme":{"extend":{}},"plugins":[]},ensure_ascii=False)+";\n",encoding="utf-8")
    input_css=output/"tailwind-input.css"
    input_css.write_text("@tailwind base;\n@tailwind components;\n@tailwind utilities;\n",encoding="utf-8")
    target=output/"rentmor-tailwind-output.css"
    subprocess.run([str(cli),"-i",str(input_css),"-o",str(target),"-c",str(configuration),"--minify"],
                   check=True,cwd=repo,timeout=100)
    data=target.read_bytes()
    if not (8000 <= len(data) <= 240000):
        raise ValueError(f"Unexpected generated stylesheet size: {len(data)} bytes")
    css=data.decode("utf-8")
    required=[".flex{",".grid{",".font-bold{",".items-center{",".text-gray-700{"]
    if not all(rule in css for rule in required):
        raise ValueError("Missing essential Tailwind utility selectors")
    # Tailwind v3 responsive selectors must survive minification.
    if "\\:hover" not in css and "\\:grid" not in css and "\\:text" not in css:
        raise ValueError("No responsive or variant utility classes compiled")
    digest=hashlib.sha256(data).hexdigest()[:12]
    name=f"rentmor-tailwind-{digest}.css"
    final=output/name
    target.replace(final)
    print(json.dumps({"public_pages_sampled":pages,"unique_classes_seen":classes,
                     "css_bytes":len(data),"filename":name},ensure_ascii=False))
    return final

if __name__=="__main__":
    if len(sys.argv)!=5:
        raise SystemExit("Usage: build_public_tailwind.py LIVE_INDEX REPO_DIR OUTPUT_DIR TAILWIND_BIN")
    build(*map(Path,sys.argv[1:]))
