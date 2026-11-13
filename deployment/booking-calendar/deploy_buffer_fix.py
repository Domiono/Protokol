#!/usr/bin/env python3
"""One-time owner-authorized return-time fix. No customer records are read or written."""
import base64
import hashlib
import json
import os
import stat
import subprocess
import tempfile
import urllib.parse
import urllib.request
import uuid
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import paramiko

ROOT = Path(__file__).resolve().parents[2]
PINS = {'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk',
        'GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
# Exact Git blob hashes of 12/13 November reviewed public source versions.
EXPECTED = {
    'booking_schedule.php': 'bddefb0c866fd113f7a5d043d88f84332b04ecb2',
    'mortii.js': 'fc856b6a93a7a27aaa273965fdcc8d4a39aea6f9',
}
HOOKS = ('public_metadata.php', 'category.php',
         'polityka-prywatnosci.php', 'rental_categories.php')


def blob_hash(raw):
    return hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()


def read(sftp, path):
    mode = sftp.lstat(path)
    if not stat.S_ISREG(mode.st_mode) or mode.st_size > 1_000_000:
        raise ValueError('Unapproved file type or size')
    with sftp.open(path, 'rb') as file:
        raw = file.read(1_000_001)
    if len(raw) > 1_000_000:
        raise ValueError('Oversized source')
    return raw


def write(sftp, path, raw, mode):
    with sftp.open(path, 'wb') as file:
        file.write(raw)
    sftp.chmod(path, mode)
    if read(sftp, path) != raw:
        raise RuntimeError('Source readback does not match')


def get(path):
    request = urllib.request.Request(
        'https://rentmor.pl/' + path,
        headers={'Cache-Control': 'no-cache', 'Accept': 'application/json'})
    with urllib.request.urlopen(request, timeout=35) as response:
        if response.status != 200:
            raise RuntimeError('Public smoke check returned non-200')
        return response.read(1_000_001)


def checked_php(contents, name):
    with tempfile.TemporaryDirectory(prefix='rm-booking-buffer-') as folder:
        path = Path(folder) / name
        path.write_bytes(contents)
        subprocess.run(['php', '-l', str(path)], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def sync_progress(sftp, private):
    progress = private + '/progress'
    try:
        sftp.mkdir(progress, mode=0o700)
    except IOError:
        pass
    sftp.chmod(progress, 0o700)
    for filename in ('RENTMOR-PROJECT-LOG.md', 'CONTINUE-RENTMOR.md'):
        raw = (ROOT / 'docs' / filename).read_bytes()
        target = progress + '/' + filename
        write(sftp, target, raw, 0o600)
        if sftp.lstat(target).st_mode & 0o777 != 0o600:
            raise RuntimeError('Progress file permissions')
    print('TWO_PROGRESS_DOCUMENTS_SHA256_0600_VERIFIED')


def main():
    expected_sources = {
        'booking_schedule.php': (ROOT / 'server' / 'booking_schedule.php').read_bytes(),
        'mortii.js': (ROOT / 'server' / 'mortii.js').read_bytes(),
    }
    checked_php(expected_sources['booking_schedule.php'], 'booking_schedule.php')
    subprocess.run(['node', '--check', str(ROOT / 'server' / 'mortii.js')],
                   check=True, stdout=subprocess.DEVNULL)
    transport = paramiko.Transport((os.environ['FTP_SERVER'], 22222))
    try:
        transport.start_client(timeout=25)
        pin = base64.b64encode(
            hashlib.sha256(transport.get_remote_server_key().asbytes()).digest()
        ).decode().rstrip('=')
        if pin not in PINS:
            raise ValueError('Hosting key pin mismatch')
        transport.auth_password(os.environ['FTP_USERNAME'],
                                os.environ['FTP_PASSWORD'], fallback=False)
        sftp = paramiko.SFTPClient.from_transport(transport)
        sftp.get_channel().settimeout(35)

        private = 'rentmor-control-private'
        st = sftp.lstat(private)
        if not stat.S_ISDIR(st.st_mode) or st.st_mode & 0o077:
            raise ValueError('Private backup folder is missing or exposed')

        changes = []
        for name, new_data in expected_sources.items():
            old_data = read(sftp, 'public_html/' + name)
            if blob_hash(old_data) != EXPECTED[name]:
                raise ValueError('Production source differs from reviewed baseline: ' + name)
            changes.append((name, old_data, new_data))

        for name in HOOKS:
            old = read(sftp, 'public_html/' + name)
            text = old.decode('utf-8')
            if text.count('mortii.js?v=7') != 1:
                raise ValueError('Unexpected cache hook version or duplicate: ' + name)
            new = text.replace('mortii.js?v=7', 'mortii.js?v=8').encode('utf-8')
            checked_php(new, name)
            changes.append((name, old, new))
        print('ALL_SIX_SOURCES_PREFLIGHT_OK')

        folder = private + '/booking-buffer-' + uuid.uuid4().hex
        sftp.mkdir(folder, mode=0o700)
        sftp.chmod(folder, 0o700)
        for name, old, new in changes:
            write(sftp, folder + '/' + name + '.before', old, 0o600)
            write(sftp, folder + '/' + name + '.next', new, 0o600)
        print('PRIVATE_BACKUP_AND_STAGING_OK')

        applied = []
        try:
            for name, old, new in changes:
                target = 'public_html/' + name
                if read(sftp, target) != old:
                    raise ValueError('Concurrent source edit blocks publication: ' + name)
                applied.append((name, old, new))
                write(sftp, target, new, 0o644)
            print('SIX_SOURCE_FILES_READBACK_OK')

            raw_js = get('mortii.js?bufferfix=' + uuid.uuid4().hex)
            if raw_js != expected_sources['mortii.js']:
                raise RuntimeError('Public JS differs from published bytes')
            homepage = get('?bufferfix=' + uuid.uuid4().hex)
            if b'mortii.js?v=8' not in homepage:
                raise RuntimeError('Homepage does not load version 8 of calendar JS')

            # Availability API returns derived, public slots only; no personal data.
            bootstrap = json.loads(get('mortii_api.php'))
            products = bootstrap.get('products', [])
            if not products:
                raise RuntimeError('Public catalogue unavailable')
            month = datetime.now(ZoneInfo('Europe/Warsaw')).strftime('%Y-%m')
            query = urllib.parse.urlencode({'equipment': products[0]['name'],
                                            'month': month, 'rental_days': '1'})
            public_calendar = json.loads(get('booking_calendar.php?' + query))
            days = public_calendar.get('days', [])
            if public_calendar.get('month') != month or not (28 <= len(days) <= 31):
                raise RuntimeError('Unexpected calendar result')
            if not all(set(d) == {'date', 'status', 'slots'} for d in days):
                raise RuntimeError('Unexpected private field in calendar')
            print('PUBLIC_JS_CACHE_AND_CALENDAR_CHECK_OK')
        except Exception as error:
            print('PUBLICATION_CHECK_FAILED_' + type(error).__name__)
            for name, old, new in reversed(applied):
                target = 'public_html/' + name
                current = read(sftp, target)
                if current != old and current != new:
                    raise RuntimeError('Concurrent edit or partial write; manual recovery needed: ' + name) from error
                if current != old:
                    write(sftp, target, old, 0o644)
            print('ORIGINAL_FILES_RESTORED')
            raise RuntimeError('Publication failed and was rolled back') from None
        print('BOOKING_BUFFER_FIX_PUBLISHED_VERIFIED')
        sync_progress(sftp, private)
    finally:
        transport.close()


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('BOOKING_BUFFER_DEPLOY_STOPPED_' + type(error).__name__)
        raise SystemExit(1)
