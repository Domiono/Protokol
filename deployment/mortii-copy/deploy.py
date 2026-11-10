"""Narrow, manually invoked SFTP transaction. Never prints remote contents or errors."""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent
ALLOWED = {'mortii.js', 'public_metadata.php', 'rental_categories.php', 'polityka-prywatnosci.php'}
PINS = {'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk', 'GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}

def digest(data):
    return hashlib.sha256(data).hexdigest()

def patch(data, entry):
    if digest(data) != entry['sha256']:
        raise ValueError('Unrecognized source version')
    text = data.decode('utf-8')
    for old, new in entry['replacements']:
        if text.count(old) != 1:
            raise ValueError('Ambiguous patch')
        text = text.replace(old, new)
    return text.encode('utf-8')

def read_remote(sftp, path):
    metadata = sftp.lstat(path)
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > 2_000_000:
        raise ValueError('Not an allowed regular file')
    with sftp.open(path, 'rb') as stream:
        data = stream.read(2_000_001)
    if len(data) > 2_000_000:
        raise ValueError('Oversized source')
    return data, metadata.st_mode & 0o777

def write_remote(sftp, path, data, mode):
    with sftp.open(path, 'wb') as stream:
        stream.write(data)
    sftp.chmod(path, mode)
    if read_remote(sftp, path)[0] != data:
        raise RuntimeError('Readback failed')

def validate(name, data):
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder, name)
        path.write_bytes(data)
        command = ['node', '--check', str(path)] if name.endswith('.js') else ['php', '-l', str(path)]
        subprocess.run(command, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)

def transaction(sftp, entries, apply=False, validator=validate, health=None):
    if len(entries) != 4 or {e['name'] for e in entries} != ALLOWED:
        raise ValueError('Invalid file allowlist')
    snapshot, candidate, modes = {}, {}, {}
    for entry in entries:
        name = entry['name']
        snapshot[name], modes[name] = read_remote(sftp, 'public_html/' + name)
        candidate[name] = patch(snapshot[name], entry)
        validator(name, candidate[name])
    if not apply:
        return 'preflight passed; no remote writes'
    backup = 'rentmor-control-private'
    if not stat.S_ISDIR(sftp.lstat(backup).st_mode) or sftp.lstat(backup).st_mode & 0o077:
        raise ValueError('Backup directory must be private (0700)')
    backup += '/mortii-copy-' + uuid.uuid4().hex
    sftp.mkdir(backup, mode=0o700)
    for name in snapshot:
        write_remote(sftp, backup + '/' + name, snapshot[name], 0o600)
    changed = []
    try:
        # JavaScript first, then cache references. All files have preflighted.
        for name in candidate:
            path = 'public_html/' + name
            if read_remote(sftp, path)[0] != snapshot[name]:
                raise ValueError('Concurrent edit')
            changed.append(name)  # includes a possible partial write
            write_remote(sftp, path, candidate[name], modes[name])
        for name in candidate:
            if read_remote(sftp, 'public_html/' + name)[0] != candidate[name]:
                raise RuntimeError('Final readback failed')
        if health:
            health(candidate)
    except Exception:
        failures = []
        for name in reversed(changed):
            try:
                write_remote(sftp, 'public_html/' + name, snapshot[name], modes[name])
            except Exception:
                failures.append(name)
        if failures:
            raise RuntimeError('Rollback incomplete; private backup retained') from None
        raise RuntimeError('Deployment failed; previous files restored') from None
    return 'deployed; readback and public checks passed; private backup retained'

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def health(candidate):
    opener = urllib.request.build_opener(NoRedirect)
    with opener.open('https://rentmor.pl/mortii.js?v=3', timeout=30) as response:
        if response.read(2_000_001) != candidate['mortii.js']:
            raise RuntimeError('Public JavaScript differs')
    for path in ['/', '/sprzet-budowlany/', '/polityka-prywatnosci.php']:
        with opener.open('https://rentmor.pl' + path, timeout=30) as response:
            body = response.read(4_000_001)
            if response.status != 200 or b'mortii.js?v=3' not in body:
                raise RuntimeError('Public hook check failed')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if args.apply and os.environ.get('CONFIRM_DEPLOY') != 'MORTII-COPY-ONLY':
        raise ValueError('Explicit narrow deployment confirmation required')
    import paramiko
    host, user, password = [os.environ[k] for k in ['FTP_SERVER', 'FTP_USERNAME', 'FTP_PASSWORD']]
    if '://' in host or '/' in host:
        raise ValueError('Host must be a hostname')
    transport = paramiko.Transport((host, 22222))
    try:
        transport.start_client(timeout=25)
        key = transport.get_remote_server_key()
        fingerprint = base64.b64encode(hashlib.sha256(key.asbytes()).digest()).decode().rstrip('=')
        if fingerprint not in PINS:
            raise ValueError('Host key differs; credentials not sent')
        transport.auth_password(user, password, fallback=False)
        sftp = paramiko.SFTPClient.from_transport(transport)
        sftp.get_channel().settimeout(30)
        try:
            print(transaction(sftp, json.loads((ROOT/'manifest.json').read_text()), args.apply, health=health))
        finally:
            sftp.close()
    finally:
        transport.close()

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Exception messages from libraries can include hostnames or source paths.
        print('Deployment stopped: ' + type(error).__name__ + '. No credentials/source logged.', file=sys.stderr)
        sys.exit(1)
