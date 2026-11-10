"""Exact-hash synchronization of two non-sensitive progress documents, private on host."""
import base64,hashlib,json,logging,os,stat,uuid
from pathlib import Path
import paramiko
from deploy import atomic,read,sha,PINS,ROOT
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222));s=None
 try:
  t.start_client(timeout=25)
  if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in PINS:raise ValueError('Host key')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False);s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30)
  for p in ['rentmor-control-private','rentmor-control-private/progress']:
   m=s.lstat(p)
   if not stat.S_ISDIR(m.st_mode)or m.st_mode&0o077:raise ValueError('Private directory')
  expected=json.loads((ROOT/'deployment/ai-knowledge/progress-manifest.json').read_text())
  if set(expected)!={'CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md'}:raise ValueError('Allowlist')
  changes=[]
  for name,h in expected.items():
   p='rentmor-control-private/progress/'+name;old,mode=read(s,p)
   if sha(old)!=h or mode&0o077:raise ValueError('Progress changed')
   changes.append((p,old,(ROOT/'docs'/name).read_bytes()))
  backup='rentmor-control-private/knowledge-progress-'+uuid.uuid4().hex;s.mkdir(backup,mode=0o700)
  for i,(p,old,new)in enumerate(changes):atomic(s,backup+'/'+str(i),old,0o600)
  done=[]
  try:
   for p,old,new in changes:
    if read(s,p)[0]!=old:raise ValueError('Concurrent edit')
    done.append((p,old,new));atomic(s,p,new,0o600)
  except Exception:
   for p,old,new in reversed(done):
    current=read(s,p)[0]
    if current==old:continue
    if current!=new:raise RuntimeError('Concurrent update prevents rollback')
    atomic(s,p,old,0o600)
   raise
  for p,old,new in changes:
   b,mode=read(s,p)
   if b!=new or mode!=0o600:raise RuntimeError('Readback')
   print('PROGRESS_SYNC_SHA256_OK',Path(p).name,sha(b))
 finally:
  if s:s.close()
  t.close()
if __name__=='__main__':
 try:main()
 except Exception as e:print('PROGRESS_SYNC_STOPPED_'+type(e).__name__);raise SystemExit(1)
