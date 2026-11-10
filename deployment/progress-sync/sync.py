"""Copy only two public project notes to the existing private hosting directory."""
import base64
import hashlib
import json
import logging
import os
from pathlib import Path
import stat
import sys
import uuid
import paramiko

PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
FILES=['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']
logging.getLogger('paramiko').addHandler(logging.NullHandler())
logging.getLogger('paramiko').propagate=False

def read(sftp,path):
    info=sftp.lstat(path)
    if not stat.S_ISREG(info.st_mode) or info.st_size>2_000_000: raise ValueError('Unsafe file')
    with sftp.open(path,'rb') as fp:return fp.read(2_000_001)

def write(sftp,path,data):
    with sftp.open(path,'wb') as fp:fp.write(data)
    sftp.chmod(path,0o600)
    if read(sftp,path)!=data:raise RuntimeError('Readback failed')

def main():
    root=Path(__file__).resolve().parents[2]
    payload={name:(root/'docs'/name).read_bytes() for name in FILES}
    t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
    try:
        t.start_client(timeout=25)
        fp=base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
        if fp not in PINS:raise ValueError('Key mismatch')
        t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
        s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30)
        parent='rentmor-control-private'
        info=s.lstat(parent)
        if not stat.S_ISDIR(info.st_mode) or info.st_mode&0o077:raise ValueError('Private directory required')
        target=parent+'/progress'
        try:info=s.lstat(target)
        except FileNotFoundError:s.mkdir(target,mode=0o700);info=s.lstat(target)
        if not stat.S_ISDIR(info.st_mode) or info.st_mode&0o077:raise ValueError('Private directory required')
        old={}
        for name in FILES:
            try:old[name]=read(s,target+'/'+name)
            except FileNotFoundError:old[name]=None
        backup=target+'/previous-'+uuid.uuid4().hex
        s.mkdir(backup,mode=0o700)
        for name,data in old.items():
            if data is not None:write(s,backup+'/'+name,data)
        written=[]
        try:
            for name,data in payload.items():
                # Stop if a second writer changed the note after the snapshot.
                try:current=read(s,target+'/'+name)
                except FileNotFoundError:current=None
                if current!=old[name]:raise ValueError('Concurrent update')
                written.append(name);write(s,target+'/'+name,data)
            for name,data in payload.items():
                if read(s,target+'/'+name)!=data:raise RuntimeError('Final verification failed')
        except Exception:
            for name in reversed(written):
                if old[name] is not None:write(s,target+'/'+name,old[name])
            raise
        print('TWO_PROGRESS_FILES_SYNCED_SHA256_AND_PRIVATE_MODES_VERIFIED')
    finally:t.close()
if __name__=='__main__':
    try:main()
    except Exception as e:print('SYNC_STOPPED_'+type(e).__name__);sys.exit(1)
