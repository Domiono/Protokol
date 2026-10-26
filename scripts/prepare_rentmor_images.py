#!/usr/bin/env python3
"""Build new WebP variants without changing rental data or original uploads.

Modes:
 plan data.json existing-manifest-or-dash source-dir lftp-source-command-file
 convert data.json existing-manifest-or-dash source-dir result-dir result-manifest upload-command-file
"""
import hashlib
import json
import re
import sys
from pathlib import Path

SAFE = re.compile(r"^[A-Za-z0-9_.-]+\.(?:jpg|jpeg|png|webp)$", re.I)
MAX_IMAGES = 200
MAX_PIXELS = 45_000_000

def records(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("Expected product list")
    names = []
    for product in data:
        if not isinstance(product, dict): raise ValueError("Invalid product item")
        images = product.get("images", [])
        if not isinstance(images, list): raise ValueError("Invalid product images")
        for raw in images:
            if not isinstance(raw, str) or not SAFE.fullmatch(raw):
                raise ValueError("Unsafe image filename in rental data")
            if raw not in names:
                names.append(raw)
    if len(names) > MAX_IMAGES: raise ValueError("Unexpected number of product images")
    return ["rentmor-logo.png"] + names

def existing(path):
    if path == "-" or not Path(path).is_file():
        return {}
    parsed = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(parsed, dict):
        raise ValueError("Invalid image manifest")
    return parsed

def candidates(data, current):
    return [name for name in data if current.get(name, {}).get("variants_version", 1) < 2]

def lftp_safe(path):
    return str(path).replace("\\", "/")

def plan(data, oldmanifest, source, output):
    required = candidates(records(data), existing(oldmanifest))
    source = Path(source)
    source.mkdir(parents=True, exist_ok=True)
    commands = ["set cmd:fail-exit no"]
    for name in required:
        remote = "public_html/rentmor-logo.png" if name == "rentmor-logo.png" else "public_html/uploads/" + name
        commands.append("get " + remote + " -o " + lftp_safe(source / name))
    commands.append("set cmd:fail-exit yes")
    Path(output).write_text("\n".join(commands)+"\n")
    print(f"Source image plan: {len(required)} new files (up to {MAX_IMAGES} rental photos), existing optimized files retained.")

def convert(data, oldmanifest, source, output, manifestfile, uploadfile):
    try:
        from PIL import Image, ImageOps
        from png_metadata import open_image
    except ImportError as exc:
        raise RuntimeError("Pillow is required for offline image processing") from exc
    Image.MAX_IMAGE_PIXELS = MAX_PIXELS
    source, output = Path(source), Path(output)
    output.mkdir(parents=True, exist_ok=True)
    data_images = records(data)
    old = existing(oldmanifest)
    manifest = {k: old[k] for k in data_images if k in old}
    upload = ["set cmd:fail-exit yes"]
    total_before = 0
    total_after = 0
    done = 0
    for name in candidates(data_images, old):
        origin = source / name
        if not origin.is_file():
            print("Missing source photo (kept original URL):", name)
            continue
        source_bytes = origin.stat().st_size
        if source_bytes > 35_000_000:
            print("Skipping oversized image:", name)
            continue
        try:
            with open_image(origin) as im:
                im = ImageOps.exif_transpose(im)
                im.load()
                width, height = im.size
                if width <= 0 or height <= 0 or width * height > MAX_PIXELS: continue
                if im.mode not in ("RGBA","RGB"):
                    im = im.convert("RGBA" if "A" in im.getbands() or "transparency" in im.info else "RGB")
                fingerprint = hashlib.sha256(origin.read_bytes()).hexdigest()[:12]
                stem = Path(name).stem
                info = dict(old.get(name, {}))
                info.update({"width":width,"height":height,"variants_version":2})
                selections = [("thumb", 160, 78), ("small", 320, 78), ("card", 640, 74), ("detail", 1440, 81)]
                if name == "rentmor-logo.png": selections = [("thumb", 144, 82), ("card", 260, 82), ("detail", 520, 83)]
                for kind, max_width, quality in selections:
                    if kind in info: continue  # Preserve all already published derivatives.
                    dest_width = min(width, max_width)
                    if any(isinstance(v, dict) and v.get("width") == dest_width for v in info.values()):
                        continue
                    target = im.copy()
                    if target.width > dest_width:
                        dest_height = max(1, round(target.height*dest_width/target.width))
                        target = target.resize((dest_width, dest_height), Image.Resampling.LANCZOS)
                    filename = f"{stem}-{fingerprint}-{kind}.webp"
                    dest = output / filename
                    target.save(dest, "WEBP", quality=quality, method=5)
                    if dest.stat().st_size >= source_bytes * .9:
                        dest.unlink()
                        continue
                    url = ("/assets/" if name=="rentmor-logo.png" else "/uploads/rentmor-webp/") + filename
                    info[kind] = {"url":url,"width":target.width}
                    remote = ("public_html/assets/" if name=="rentmor-logo.png" else "public_html/uploads/rentmor-webp/") + filename
                    upload.append("put " + lftp_safe(dest) + " -o " + remote)
                    total_after += dest.stat().st_size
                manifest[name] = info
                total_before += source_bytes
                done += 1
        except (OSError, ValueError, Image.DecompressionBombError) as exc:
            print("Skipping unprocessable image",name,type(exc).__name__)
    Path(manifestfile).write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    Path(uploadfile).write_text("\n".join(upload)+"\n",encoding="utf-8")
    if not manifest:
        raise ValueError("No usable source images or existing manifest; refuse publication")
    print(f"Processed {done} new photos; source bytes {total_before}, published WebP bytes {total_after}; manifest entries {len(manifest)}.")

if __name__ == "__main__":
    if len(sys.argv)<2: raise SystemExit("Mode required")
    if sys.argv[1]=="plan" and len(sys.argv)==6: plan(*sys.argv[2:])
    elif sys.argv[1]=="convert" and len(sys.argv)==8: convert(*sys.argv[2:])
    else: raise SystemExit("Invalid arguments")
