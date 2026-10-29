#!/usr/bin/env python3
"""Migrate an existing live bcrypt hash to a secret location without printing it."""
import json, pathlib, re, sys
live, existing, candidate = map(pathlib.Path,sys.argv[1:])
if existing.is_file():
    config=json.loads(existing.read_text())
else:
    match=re.search(r"\$hasz_hasla\s*=\s*'(\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53})';",live.read_text())
    if not match:raise SystemExit("Cannot migrate existing admin hash, aborting deploy")
    config={"password_hash":match.group(1),"totp_secret":""}
if not isinstance(config,dict) or not re.fullmatch(r"\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}",config.get("password_hash","")) or not isinstance(config.get("totp_secret"),str) or (config["totp_secret"] and not re.fullmatch(r"[A-Z2-7]{32}",config["totp_secret"])):
    raise SystemExit("Invalid existing secret config, refusing deploy")
candidate.write_text(json.dumps(config,separators=(',',':')))
candidate.chmod(0o600)
print("Private admin config validated (secrets redacted).")
