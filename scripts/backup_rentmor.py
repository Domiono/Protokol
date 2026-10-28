#!/usr/bin/env python3
"""Private, verified RentMor SFTP snapshots. Never uploads customer data to GitHub."""
import argparse
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import tempfile

FILES = (
    "data.json", "reservations.json", "customers.json", "costs.json",
    "protocols.json", "messages.json", "stats.json", "terms.json",
    "protocol_devices.json", "rental_assets.php", "rental_addons.json",
)
ESSENTIAL = ("data.json", "reservations.json")
REMOTE = "rentmor-private/backups"
SAFE_NAME = re.compile(r"^rentmor-\d{8}T\d{6}Z(?:-[0-9]{1,12})?\.tar\.gz$")
KEEP_DAYS = 21


def sha(data):
    return hashlib.sha256(data).hexdigest()


def sftp(commands, *, capture=False):
    # No credentials in argv or logs. Host key verification is configured by
    # scripts/secure_sftp_connection.sh and passed as SFTP_CONNECT_SETUP.
    env = os.environ
    for name in ("FTP_SERVER", "FTP_USERNAME", "LFTP_PASSWORD", "SFTP_CONNECT_SETUP"):
        if not env.get(name):
            raise RuntimeError("Brak konfiguracji " + name)
    cmd = ["lftp", "--env-password", "-u", env["FTP_USERNAME"],
           "sftp://" + env["FTP_SERVER"] + ":22222",
           "-e", env["SFTP_CONNECT_SETUP"] + "; " + "; ".join(commands) + "; bye"]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=180, check=False)
    if result.returncode:
        raise RuntimeError("SFTP nie powiodło się (bez publikowania danych).")
    return result.stdout if capture else None


def make_snapshot():
    now = dt.datetime.now(dt.timezone.utc)
    stamp = now.strftime("%Y%m%dT%H%M%SZ")
    run = os.environ.get("GITHUB_RUN_ID", "")
    name = "rentmor-" + stamp + ("-" + run if run.isdigit() else "") + ".tar.gz"
    with tempfile.TemporaryDirectory(prefix="rentmor-backup-") as tmp:
        root = Path(tmp)
        # Missing optional files are normal on a new installation.
        commands = ["set cmd:fail-exit no"]
        for fn in FILES:
            commands.append(f"get public_html/{fn} -o {root / fn}")
        commands.append("set cmd:fail-exit yes")
        sftp(commands)
        for fn in ESSENTIAL:
            if not (root / fn).is_file():
                raise RuntimeError("Brak krytycznego pliku do kopii: " + fn)
        payload = {}
        for fn in FILES:
            path = root / fn
            if not path.is_file():
                continue
            data = path.read_bytes()
            if fn.endswith(".json"):
                try:
                    json.loads(data)
                except (ValueError, UnicodeDecodeError):
                    raise RuntimeError("Nieprawidłowy JSON; kopia nie zostanie zatwierdzona: " + fn)
            payload[fn] = data
        if len(payload) < len(ESSENTIAL):
            raise RuntimeError("Kopia nie zawiera wymaganych plików.")
        manifest = {
            "created_utc": now.isoformat(),
            "files": {fn: {"sha256": sha(data), "bytes": len(data)}
                      for fn, data in payload.items()},
        }
        archive = root / name
        with tarfile.open(archive, "w:gz") as tar:
            for fn, data in sorted(payload.items()):
                info = tarfile.TarInfo(fn)
                info.size = len(data)
                info.mode = 0o600
                tar.addfile(info, io.BytesIO(data))
            packed = json.dumps(manifest, sort_keys=True).encode()
            info = tarfile.TarInfo("manifest.json")
            info.size = len(packed)
            info.mode = 0o600
            tar.addfile(info, io.BytesIO(packed))
        # Only mkdir is best-effort; put/get must succeed and the readback
        # must be byte-for-byte identical before counting as a saved backup.
        remote_file = REMOTE + "/" + name
        readback = root / "readback.tar.gz"
        sftp(["set cmd:fail-exit no", f"mkdir -p {REMOTE}",
              "set cmd:fail-exit yes",
              f"put {archive} -o {remote_file}",
              f"get {remote_file} -o {readback}"])
        if sha(archive.read_bytes()) != sha(readback.read_bytes()):
            raise RuntimeError("Weryfikacja kopii na serwerze nie powiodła się.")
        print(f"Backup verified: {name}, files={len(payload)}; outside public_html")
        # Retention: remove only exact snapshot names older than 21 days.
        listing = sftp([f"cls -1 {REMOTE}"], capture=True) or ""
        cutoff = (now - dt.timedelta(days=KEEP_DAYS)).date()
        obsolete = []
        for line in listing.splitlines():
            fn = line.strip().split("/")[-1]
            if not SAFE_NAME.fullmatch(fn):
                continue
            day = dt.datetime.strptime(fn[8:16], "%Y%m%d").date()
            if day < cutoff:
                obsolete.append(fn)
        if obsolete:
            sftp([f"rm {REMOTE}/{fn}" for fn in obsolete])
            print(f"Removed expired private snapshots: {len(obsolete)}")


def stage_restore(snapshot, file_name):
    if not SAFE_NAME.fullmatch(snapshot):
        raise RuntimeError("Nieprawidłowa nazwa kopii.")
    if file_name not in FILES:
        raise RuntimeError("Nieznany plik do przywrócenia.")
    with tempfile.TemporaryDirectory(prefix="rentmor-restore-") as tmp:
        local = Path(tmp)
        archive = local / snapshot
        sftp([f"get {REMOTE}/{snapshot} -o {archive}"])
        with tarfile.open(archive, "r:gz") as tar:
            members = tar.getmembers()
            expected = set(FILES) | {"manifest.json"}
            if any(not m.isfile() or m.name not in expected or m.size > 150_000_000 for m in members):
                raise RuntimeError("Nieprawidłowa struktura archiwum.")
            manifest = json.load(tar.extractfile("manifest.json"))
            record = manifest["files"].get(file_name)
            if not record:
                raise RuntimeError("Tego pliku nie ma w wybranej kopii.")
            data = tar.extractfile(file_name).read()
            if sha(data) != record["sha256"] or len(data) != record["bytes"]:
                raise RuntimeError("Niepoprawna suma kontrolna kopii.")
            if file_name.endswith(".json"):
                json.loads(data)
        prepared = local / file_name
        prepared.write_bytes(data)
        # SAFETY: Do not change a live file while the site/app can write to it.
        # Stage outside public_html; operator can restore after suspending writes.
        staging = "rentmor-private/restore-ready"
        remote = staging + "/" + file_name
        checked = local / "checked"
        sftp(["set cmd:fail-exit no", f"mkdir -p {staging}",
              "set cmd:fail-exit yes", f"put {prepared} -o {remote}",
              f"get {remote} -o {checked}"])
        if sha(checked.read_bytes()) != sha(data):
            raise RuntimeError("Weryfikacja przygotowanego przywrócenia nie powiodła się.")
        print("Verified restore candidate staged at " + remote +
              ". No production files were changed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage-restore", metavar="SNAPSHOT")
    parser.add_argument("--file", choices=FILES)
    args = parser.parse_args()
    if bool(args.stage_restore) != bool(args.file):
        parser.error("--stage-restore and --file must be used together")
    if args.stage_restore:
        stage_restore(args.stage_restore, args.file)
    else:
        make_snapshot()
