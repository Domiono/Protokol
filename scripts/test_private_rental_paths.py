#!/usr/bin/env python3
"""Test marker-based routing with disposable PHP directories only."""
from pathlib import Path
import subprocess, tempfile, shutil
source=Path(__file__).resolve().parents[1]/'server/private_rental_files.php'
with tempfile.TemporaryDirectory() as tmp:
    root=Path(tmp);public=root/'public_html';public.mkdir()
    shutil.copyfile(source,public/'private_rental_files.php')
    private=root/'rentmor-private'/'rental-data';private.mkdir(parents=True)
    script=public/'test.php'
    script.write_text("<?php require __DIR__.'/private_rental_files.php'; echo json_encode(['path'=>rm_rental_file('costs.json'),'migrated'=>rm_rental_migrated(),'maintenance'=>rm_rental_maintenance()]);")
    def run():
        import json
        return json.loads(subprocess.check_output(['php',str(script)],text=True))
    a=run();assert a['path']==str(public/'costs.json') and not a['migrated']
    (private/'maintenance.flag').write_text('yes')
    assert run()['maintenance']
    (private/'active.flag').write_text('verified')
    b=run();assert b['migrated'] and b['path']==str(private/'costs.json')
    (private/'active.flag').unlink()
    assert run()['path']==str(public/'costs.json')
print('PASS: private storage gating and safe fallback before migration')
