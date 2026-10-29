#!/usr/bin/env python3
"""Validate five private rental files before switch; never print their contents."""
import json,pathlib,sys
root=pathlib.Path(sys.argv[1])
names=['reservations.json','protocols.json','customers.json','costs.json','rental_assets.php']
for name in names:
    path=root/name
    if not path.is_file() or path.stat().st_size==0:raise SystemExit("Missing private rental file: "+name)
    raw=path.read_text(encoding='utf-8')
    if name.endswith('.php'):
        if not raw.startswith('<?php') or '\n' not in raw:raise SystemExit("Bad private PHP guard")
        raw=raw.split('\n',1)[1]
    data=json.loads(raw)
    if not isinstance(data,list):raise SystemExit("Unexpected list structure: "+name)
print("PASS: five private rental sources are present and structurally valid.")
