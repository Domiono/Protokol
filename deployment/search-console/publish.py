"""Narrow, owner-requested panel publication and private progress synchronization.

Only source and the two project documents are accessed. No customer data, saved
Google credentials, tokens, analytics cache, Android package or rental inventory.
"""
import base64
import hashlib
import json
import logging
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import urllib.request
import urllib.error
import uuid
import paramiko

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
from patch_search_console import patch
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
DOCS={'CONTINUE-RENTMOR.md':'f659ae2adc71024959484dc8f607723e8f9b8725',
      'RENTMOR-PROJECT-LOG.md':'559415febf2b2e23f45d22848b9b01e2481acc87'}
logging.getLogger('paramiko').addHandler(logging.NullHandler())
logging.getLogger('paramiko').propagate=False

def sha(b):return hashlib.sha256(b).hexdigest()
def blob_sha(b):return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def read(s,path):
    info=s.lstat(path)
    if not stat.S_ISREG(info.st_mode) or info.st_size>2_000_000:raise ValueError('Unsafe source')
    with s.open(path,'rb')as fp:data=fp.read(2_000_001)
    if len(data)>2_000_000:raise ValueError('Oversized source')
    return data,info.st_mode&0o777
def optional(s,path):
    try:return read(s,path)
    except FileNotFoundError:return None,0o644
def put(s,path,data,mode):
    # Stage even public PHP in a private0700 directory: no readable .tmp source URL.
    temp='rentmor-control-private/gsc-upload-'+uuid.uuid4().hex+'.tmp'
    try:
        # SFTP is encrypted; private backup data never passes through a public file.
        with s.open(temp,'wb')as fp:fp.write(data)
        s.chmod(temp,mode)
        if read(s,temp)!=(data,mode):raise RuntimeError('Upload readback')
        s.posix_rename(temp,path)
        if read(s,path)!=(data,mode):raise RuntimeError('Final readback')
    finally:
        try:s.remove(temp)
        except FileNotFoundError:pass
def http(path,expected):
    try:
        with urllib.request.urlopen('https://rentmor.pl/'+path,timeout=30)as response:
            status=response.status
            if status==200:
                body=response.read(500000)
                if b'<?php' in body or b'Admin RentMor' not in body:raise RuntimeError('Login page check')
    except urllib.error.HTTPError as e:status=e.code
    if status!=expected:raise RuntimeError('Unauthenticated HTTP check')

def main():
    t=paramiko.Transport((os.environ['FTP_SERVER'],22222));s=None;held=False
    lock='rentmor-control-private/search-console-publish.lock'
    try:
        t.start_client(timeout=25)
        if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in PINS:raise ValueError('Host key')
        t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
        s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(40)
        for directory in ['rentmor-control-private','rentmor-control-private/progress']:
            info=s.lstat(directory)
            if not stat.S_ISDIR(info.st_mode)or info.st_mode&0o077:raise ValueError('Private directory required')
        s.mkdir(lock,mode=0o700);held=True
        changes=[]
        # Verify both private progress copies against the inspected main before continuing.
        for name,expected in DOCS.items():
            path='rentmor-control-private/progress/'+name;old,mode=read(s,path)
            if blob_sha(old)!=expected or mode&0o077:raise ValueError('Private progress differs: '+name)
            changes.append((path,old,(ROOT/'docs'/name).read_bytes(),0o600))
        helper_changes=[]
        for name in ['search_console.php','search_console_admin.php']:
            path='public_html/'+name;old,mode=optional(s,path);new=(ROOT/'server'/name).read_bytes()
            if old is not None and old!=new:raise ValueError('Unexpected existing helper')
            helper_changes.append((path,old,new,0o644))
        old,mode=read(s,'public_html/admin.php')
        new=patch(old.decode('utf-8')).encode('utf-8')
        # Lint CURRENT production candidate, preserving all newer integrations.
        with tempfile.TemporaryDirectory()as tmp:
            candidate=Path(tmp)/'admin.php';candidate.write_bytes(new)
            subprocess.run(['php','-l',str(candidate)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        changes=helper_changes+[('public_html/admin.php',old,new,mode)]+changes
        backup='rentmor-control-private/search-console-'+uuid.uuid4().hex;s.mkdir(backup,mode=0o700)
        for i,(path,before,after,mode)in enumerate(changes):
            if before is not None:put(s,backup+'/'+str(i),before,0o600)
        written=[]
        try:
            for path,before,after,mode in changes:
                if optional(s,path)[0]!=before:raise RuntimeError('Concurrent edit')
                written.append((path,before,after,mode));put(s,path,after,mode)
            http('admin.php',200)
            http('search_console.php',404)
            http('search_console_admin.php',404)
            http('admin.php?tab=google&gsc_callback=1',403)
            for path,before,after,mode in changes:
                if read(s,path)!=(after,mode):raise RuntimeError('Final verification')
                print('SOURCE_OR_PROGRESS_SHA256_OK',Path(path).name,sha(after))
        except Exception:
            for path,before,after,mode in reversed(written):
                current=optional(s,path)[0]
                if current==before:continue
                if current!=after:raise RuntimeError('Concurrent edit prevents rollback')from None
                if before is None:s.remove(path)
                else:put(s,path,before,mode)
            raise RuntimeError('Publication failed; originals restored')from None
        print('SEARCH_CONSOLE_PUBLISHED_CURRENT_ADMIN_PATCHED_TWO_PROGRESS_FILES_VERIFIED')
        print('GOOGLE_OAUTH_OWNER_SETUP_REQUIRED_NO_GOOGLE_OR_CUSTOMER_RECORDS_READ')
    finally:
        if s:
            if held:s.rmdir(lock)
            s.close()
        t.close()

if __name__=='__main__':
    try:main()
    except Exception as e:print('SEARCH_CONSOLE_PUBLICATION_STOPPED_'+type(e).__name__);raise SystemExit(1)
