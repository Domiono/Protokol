#!/usr/bin/env python3
"""Update only the approved WT20i public offer description."""
import copy
import json
from pathlib import Path
import sys

PRODUCT_ID = "69385f95de01c"

def patch(items):
    if not isinstance(items, list):
        raise ValueError("Expected product array")
    result = copy.deepcopy(items)
    matches = [x for x in result if str(x.get("id", "")) == PRODUCT_ID]
    if len(matches) != 1 or "wt20i" not in matches[0].get("name", "").lower():
        raise ValueError("Expected exactly one known WT20i product")
    product = matches[0]
    desc = product["desc"]
    for old, new in [
        ("227 bar (3290 PSI)", "210 bar (3045 PSI)"),
        ("Pompa GRACO 395", "Pompa typu GRACO 395"),
    ]:
        if old in desc:
            if desc.count(old) != 1:
                raise ValueError("Ambiguous WT20i description")
            desc = desc.replace(old, new)
        elif new not in desc:
            raise ValueError("WT20i description changed; review before applying")
    product["desc"] = desc
    return result

if __name__ == "__main__":
    source, output = map(Path, sys.argv[1:3])
    raw = source.read_bytes()
    items = json.loads(raw)
    updated = patch(items)
    output.write_bytes(raw if updated == items else
                       (json.dumps(updated, ensure_ascii=False, indent=4) + "\n").encode("utf-8"))
    print("WT20i offer validated; " + ("unchanged." if updated == items else "two approved corrections prepared."))
