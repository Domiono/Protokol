#!/usr/bin/env python3
"""Offline tests for backup filename and restore integrity rules."""
import importlib.util
import io
import json
from pathlib import Path
import tarfile
from unittest import mock

spec = importlib.util.spec_from_file_location("rentmor_backup", Path(__file__).parent / "backup_rentmor.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

assert module.SAFE_NAME.fullmatch("rentmor-20261028T153000Z.tar.gz")
assert not module.SAFE_NAME.fullmatch("../rentmor-20261028T153000Z.tar.gz")
assert not module.SAFE_NAME.fullmatch("rentmor-20261028T153000Z.php")
assert module.sha(b"data") == "3a6eb0790f39ac87c94f3856b2dd2c5d110e6811602261a9a923d3bb23adc8b7"

snapshot = "rentmor-20261028T153000Z.tar.gz"
remote = {}
def fake_sftp(commands, capture=False):
    for command in commands:
        if command.startswith("get "):
            parts = command.split(" -o ")
            if parts[0][4:] in remote:
                Path(parts[1]).write_bytes(remote[parts[0][4:]])
        if command.startswith("put "):
            parts = command.split(" -o ")
            remote[parts[1]] = Path(parts[0][4:]).read_bytes()

def bundle(records):
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w:gz") as tar:
        for name, data in records.items():
            info = tarfile.TarInfo(name)
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
    return output.getvalue()

reservation = b'[{"id":"test"}]'
manifest = json.dumps({"files":{"reservations.json":{"sha256":module.sha(reservation),"bytes":len(reservation)}}}).encode()
remote[module.REMOTE+"/"+snapshot] = bundle({"reservations.json":reservation,"manifest.json":manifest})
with mock.patch.object(module, "sftp", side_effect=fake_sftp):
    module.stage_restore(snapshot, "reservations.json")
    assert remote["rentmor-private/restore-ready/reservations.json"] == reservation
    remote[module.REMOTE+"/"+snapshot] = bundle({"reservations.json":b'[]',"manifest.json":manifest})
    try:
        module.stage_restore(snapshot, "reservations.json")
    except RuntimeError:
        pass
    else:
        raise AssertionError("Modified payload accepted")
print("Backup path and checksum checks passed.")
