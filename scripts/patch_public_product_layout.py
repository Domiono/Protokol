#!/usr/bin/env python3
"""Carefully update only the public product detail description markup in current index.php.
Never replace the live index with an old repository copy.
"""
from pathlib import Path
import re
import sys

path=Path(sys.argv[1])
text=path.read_text("utf-8")
if "<?php" not in text or "SZCZEGÓŁY PRODUKTU" not in text or "product['desc']" not in text:
    raise SystemExit("Unexpected product page structure; aborting safe layout patch.")

start=text.index("SZCZEGÓŁY PRODUKTU")
end=text.index('id="kontakt"', start)
before=text[:start]
body=text[start:end]
after=text[end:]

changes=0

def replace_exact(old, new):
    global changes, body
    if old in body:
        if body.count(old) != 1:
            raise SystemExit(f"Ambiguous layout marker (count={body.count(old)}): {old[:55]}")
        body=body.replace(old,new,1)
        changes+=1
    elif new not in body:
        raise SystemExit("Expected product markup not present; refusing to alter source: "+old[:55])

replace_exact('<div class="p-8">', '<div class="p-4 sm:p-8">')
replace_exact(
    '<div class="bg-gray-50 p-8 rounded-3xl border border-gray-100 mb-12">',
    '<div class="bg-gray-50 p-4 sm:p-7 rounded-2xl border border-gray-100 mb-8">'
)
replace_exact(
    '<h3 class="font-bold text-gray-900 border-b pb-4 mb-6 uppercase text-sm tracking-widest flex items-center">',
    '<h3 class="font-bold text-gray-900 border-b pb-3 mb-4 uppercase text-sm tracking-widest flex items-center">'
)
replace_exact(
    '<div class="text-gray-700 leading-relaxed text-lg">',
    '<div class="rentmor-product-description text-gray-700 leading-7 text-base sm:text-lg break-words">'
)
old_description=r"""echo nl2br(
                            e($product['desc'] ?? '')
                        );"""
new_description=r"""require_once __DIR__ . '/product_description.php';
                        echo rentmor_render_description($product['desc'] ?? '');"""
replace_exact(old_description, new_description)

updated=before+body+after
if updated!=text:
    path.write_text(updated,encoding="utf-8")
    print("Updated mobile product description layout and semantic text formatting.")
else:
    print("Product description layout already patched; no changes needed.")
