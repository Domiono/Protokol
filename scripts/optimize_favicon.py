#!/usr/bin/env python3
"""Generate tiny stable content-hashed 64x64 favicon from original public PNG.

Do not modify the source image. Only the public index.php href is changed.
"""
from pathlib import Path
from PIL import Image, ImageOps
import hashlib, re, sys

OLD="/rentmor-kask-favicon.png"
def build(origin:Path, index:Path, outdir:Path)->Path:
    source=origin.read_bytes()
    assert len(source)>500, "Unexpected favicon source"
    with Image.open(origin) as source_image:
        source_image=ImageOps.exif_transpose(source_image)
        source_image.thumbnail((64,64),Image.Resampling.LANCZOS)
        tile=Image.new("RGBA",(64,64),(0,0,0,0))
        if source_image.mode!="RGBA":source_image=source_image.convert("RGBA")
        tile.alpha_composite(source_image,((64-source_image.width)//2,(64-source_image.height)//2))
        outdir.mkdir(parents=True,exist_ok=True)
        temp=outdir/"favicon.png"
        tile.save(temp,format="PNG",optimize=True)
    blob=temp.read_bytes()
    if not (100<len(blob)<30000):
        raise ValueError("Optimized favicon unexpectedly large or invalid")
    name="rentmor-favicon-"+hashlib.sha256(blob).hexdigest()[:12]+".png"
    path=outdir/name
    temp.replace(path)
    template=index.read_text(encoding="utf-8")
    # Exact public icon path, regardless of attribute ordering.
    previous=re.compile(r'(?<=["\\x27])/rentmor-kask-favicon\.png(?=["\\x27])')
    static=re.compile(r'/assets/rentmor-favicon-[0-9a-f]{12}\.png')
    old=previous.findall(template)
    upgraded=static.findall(template)
    if len(old)+len(upgraded)!=1:
        raise ValueError("Expected precisely one existing homepage favicon")
    if old:
        changed=previous.sub("/assets/"+name,template,count=1)
    else:
        changed=static.sub("/assets/"+name,template,count=1)
    index.write_text(changed,encoding="utf-8")
    print(f"Favicon optimized from {len(source)} to {len(blob)} bytes: {name}")
    return path

if __name__=="__main__":
    if len(sys.argv)!=4:raise SystemExit("Usage: optimize_favicon.py ORIGINAL_PNG LIVE_INDEX OUT_DIR")
    build(*map(Path,sys.argv[1:]))
