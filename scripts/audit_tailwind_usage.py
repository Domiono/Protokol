#!/usr/bin/env python3
"""Report safe, non-secret facts needed before replacing public Tailwind CDN."""
from pathlib import Path
import re, sys, json
s=Path(sys.argv[1]).read_text(encoding="utf-8")
cdn=list(re.finditer(r"<script\b[^>]*\bsrc\s*=\s*['\"]https://cdn\.tailwindcss\.com(?:/[^'\"]*)?['\"][^>]*>\s*</script>",s,re.I))
tailwind_config=re.findall(r"tailwind\s*\.\s*config\s*=",s)
nested_configs = re.findall(r"(?:theme|plugins|safelist)\s*:",s)
contains = {needle: needle in s for needle in ["classList.","className","class=\"","class='","dark:","@apply","tailwind.config","cdn.tailwindcss.com"]}
contains["interpolated_classes"] = bool(re.search(r"(?:bg|text|border)-[$][{]",s))
print(json.dumps({"cdn_script_tags":len(cdn),"tailwind_config_assignments":len(tailwind_config),
                   "config_keywords":len(nested_configs),"php_bytes":len(s.encode()),
                   "markers":contains},ensure_ascii=False))
if len(cdn)!=1: raise SystemExit("Expected exactly one Tailwind CDN script on live public index; manual review required.")
if tailwind_config: raise SystemExit("Custom runtime tailwind.config exists; inspect/port configuration before static build.")
