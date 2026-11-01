#!/usr/bin/env python3
"""Prove that a caller can still click a tel: link after analytics opt-out."""
from pathlib import Path
import tempfile
import subprocess
from patch_legacy_consent import patch, MARKER, SESSION

fixture = """<?php
session_start();
?>
<!doctype html><html><body>
<a href="tel:+48123456789" onclick="trackCall('test')">Zadzwoń</a>
<script>function trackCall(id) { fetch('/legacy-call.php', {method:'POST'}); }</script>
</body></html>
"""
out = patch(fixture)
assert patch(out) == out
assert out.count(MARKER) == 1 and out.count(SESSION) == 1
assert "if ((\$_COOKIE['rm_stats_consent'] ?? '') === 'allow') session_start();" not in out
assert "if (($_COOKIE['rm_stats_consent'] ?? '') === 'allow') session_start();" in out
assert out.index("if (!") < out.index("fetch('/legacy-call.php'")
assert 'href="tel:+48123456789"' in out
with tempfile.TemporaryDirectory() as folder:
    p=Path(folder)/'index.php'
    p.write_text(out,encoding='utf-8')
    subprocess.run(['php','-l',str(p)],check=True)
for bad in [fixture.replace('session_start();',''), fixture.replace('function trackCall(id)','function different(id)'), fixture.replace("fetch('/legacy-call.php',", "location.href='tel:x'; fetch('/legacy-call.php',")]:
    try: patch(bad)
    except ValueError: pass
    else: raise AssertionError('Unknown live source accepted')
print('PASS: PHP session/statistics gated on consent, tel: links preserved, idempotent/fail closed')
