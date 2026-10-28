#!/usr/bin/env python3
"""Offline regression checks for new protection and backup functions."""
import tempfile
import json
import tarfile
from datetime import date
from pathlib import Path
from patch_private_data_access import patch
from backup_photos import archive_photos, verify_photos
from backup_retention import expired
from backup_rentmor import REQUIRED, validate

original = "# RewriteEngine On\nRewriteRule ^test$ index.php [L]\n"
safe = patch(original)
assert patch(safe)==safe
assert "Require all denied" in safe and safe.endswith(original)
for broken in ("# BEGIN RentMor private-data access v1",safe+safe):
    try: patch(broken)
    except ValueError: pass
    else: raise AssertionError("Invalid Apache rule marker accepted")

with tempfile.TemporaryDirectory() as td:
    root=Path(td)
    uploads=root/"uploads"
    uploads.mkdir()
    (uploads/"one.jpg").write_bytes(b"IMAGE_ONE")
    (uploads/"another.webp").write_bytes(b"IMAGE_TWO")
    archive=root/"photos.tar.gz"
    assert archive_photos(uploads,archive)==2
    verify_photos(archive)
    with tarfile.open(archive,"r:gz") as tar:
        saved={m.name:tar.extractfile(m).read() for m in tar.getmembers()}
    assert saved["uploads/one.jpg"]==b"IMAGE_ONE"
    assert saved["uploads/another.webp"]==b"IMAGE_TWO"
    for filename in REQUIRED:
        (root/filename).write_text("[]")
    (root/"rental_equipment_names.php").write_text("<?php http_response_code(404); exit; ?>\n"+json.dumps(["Internal only"]))
    assert "rental_equipment_names.php" in validate(root)
    (root/"rental_equipment_names.php").write_text("<?php exit; ?>\n{bad")
    try:validate(root)
    except ValueError: pass
    else:raise AssertionError("Damaged equipment list accepted by backup")

names=["2026-09-01-run-40712345678/","2026-09-02-run-40712345679/","2026-09-03-run-40712345680/","2026-10-28-run-40712345681/","2026-08-20/","../bad"]
deleted=expired(names,"data",today=date(2026,10,28))
assert deleted==["2026-09-01-run-40712345678","2026-09-02-run-40712345679"],deleted
photos=["2026-09-01-run-40712345678.tar.gz","2026-09-02-run-40712345679.tar.gz","2026-10-20-run-40712345680.tar.gz"]
assert expired(photos,"photos",today=date(2026,10,28))==["2026-09-01-run-40712345678.tar.gz"]
print("Private access rules, photo integrity, registry backup and retention tests passed.")
