#!/usr/bin/env python3
"""Create and test a restorable photo archive locally; no files enter GitHub artifacts."""
import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def archive_photos(root: Path, destination: Path) -> int:
    if not root.is_dir():
        raise ValueError("Uploads directory was not downloaded")
    files = sorted(p for p in root.rglob("*") if p.is_file())
    if not files:
        raise ValueError("Uploads directory is empty; refusing a false-complete photo backup")
    if any(p.is_symlink() for p in root.rglob("*")):
        raise ValueError("Symlinks are not allowed in photo backups")
    manifest = {}
    with tarfile.open(destination, "w:gz") as out:
        for path in files:
            relative = path.relative_to(root).as_posix()
            if not relative or relative.startswith("/") or ".." in path.relative_to(root).parts:
                raise ValueError("Unsafe photo path")
            content = path.read_bytes()
            if not content:
                raise ValueError("Empty upload in backup: " + relative)
            manifest[relative] = {"sha256": sha(content), "bytes": len(content)}
            info = tarfile.TarInfo("uploads/" + relative)
            info.size = len(content)
            info.mode = 0o600
            out.addfile(info, io.BytesIO(content))
        contents = json.dumps({"files": manifest}, sort_keys=True).encode()
        info = tarfile.TarInfo("manifest.json")
        info.size = len(contents)
        info.mode = 0o600
        out.addfile(info, io.BytesIO(contents))
    verify_photos(destination)
    return len(manifest)

def verify_photos(path: Path) -> None:
    with tarfile.open(path, "r:gz") as arc:
        members = arc.getmembers()
        if any(not m.isfile() or (not m.name.startswith("uploads/") and m.name!="manifest.json")
               or ".." in Path(m.name).parts for m in members):
            raise ValueError("Unsafe or unexpected archive member")
        record = arc.extractfile("manifest.json")
        if record is None:
            raise ValueError("Missing photo manifest")
        manifest = json.loads(record.read())
        expected = manifest["files"]
        if len(expected) != len(members)-1:
            raise ValueError("Photo manifest count mismatch")
        for item in members:
            if item.name == "manifest.json": continue
            rel = item.name[len("uploads/"):]
            meta = expected.get(rel)
            member = arc.extractfile(item)
            if not meta or not member:
                raise ValueError("Missing photo metadata")
            data = member.read()
            if len(data)!=meta["bytes"] or sha(data)!=meta["sha256"]:
                raise ValueError("Photo integrity mismatch: " + rel)

if __name__ == "__main__":
    if len(sys.argv)==3:
        total=archive_photos(Path(sys.argv[1]),Path(sys.argv[2]))
        print("Verified photo archive created (files=" + str(total) + ")")
    elif len(sys.argv)==2:
        verify_photos(Path(sys.argv[1]))
        print("Photo archive readback verified.")
    else:
        raise SystemExit("Usage: backup_photos.py [SOURCE_DIR] ARCHIVE_PATH")
