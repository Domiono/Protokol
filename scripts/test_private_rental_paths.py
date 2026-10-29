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

# Protect standard deployments against accidentally repeating the production cutover.
import re
workflow=(Path(__file__).resolve().parents[1]/'.github/workflows/deploy-rentmor.yml').read_text()
name="Move rental and finance records outside public_html (verified rollback)"
assert workflow.count("- name: "+name)==1, "Missing or duplicated rental cutover step"
gate=re.search(r"(?m)^      - name: Move rental and finance records outside public_html \(verified rollback\)\n(?:        #.*\n)*        if: (.+)$",workflow)
assert gate, "The rental cutover must have a dedicated job-step condition"
condition=gate.group(1)
assert "github.event_name == 'workflow_dispatch'" in condition, "Push or PR must never run migration"
assert "inputs.migrate_private_data == true" in condition, "Manual migration must require explicit opt-in"
assert "steps.config.outputs.enabled == 'true'" in condition
assert "steps.scope.outputs.public_only != 'true'" in condition
manual=re.search(r"(?m)^  workflow_dispatch:\n    inputs:\n      migrate_private_data:\n(?:(?:        .*)\n){1,6}",workflow)
assert manual and re.search(r"(?m)^        default: false$",manual.group(0)), "Manual migration must default to off"
print("PASS: push/PR deployments cannot start rental data cutover; manual migration defaults off")
