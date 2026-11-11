"""Deploy only the reviewed public stylesheet; preserve all booking logic."""
import base64,hashlib,json,os,stat,urllib.request,uuid
from pathlib import Path
import paramiko
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
BEFORE='98dde0ba4261133cb882491b4acbc96e400fe9ddd00cbcdbba0ce2fd7ad8b402'
def digest(b):return hashlib.sha256(b).hexdigest()
def read(s,p):
 m=s.lstat(p)
 if not stat.S_ISREG(m.st_mode) or m.st_size>500000:raise ValueError('Invalid regular file')
 with s.open(p,'rb') as f:return f.read(500001)
def write(s,p,b,mode):
 with s.open(p,'wb') as f:f.write(b)
 s.chmod(p,mode)
 if read(s,p)!=b:raise RuntimeError('Readback failed')
def main():
 root=Path(__file__).resolve().parents[2]
 candidate=(root/'server/mortii.css').read_bytes()
 if b'.mortii-booking textarea' not in candidate:raise ValueError('Missing textarea style')
 if b'url(' in candidate or b'@import' in candidate:raise ValueError('Unexpected stylesheet import')
 transport=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  transport.start_client(timeout=25)
  pin=base64.b64encode(hashlib.sha256(transport.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
  if pin not in PINS:raise ValueError('Host key mismatch')
  transport.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(transport);s.get_channel().settimeout(30)
  path='public_html/mortii.css';before=read(s,path)
  if digest(before)==digest(candidate):print('CSS_ALREADY_DEPLOYED');return
  if digest(before)!=BEFORE:raise ValueError('Production changed since review')
  private='rentmor-control-private'
  m=s.lstat(private)
  if not stat.S_ISDIR(m.st_mode) or m.st_mode&0o077:raise ValueError('Backup directory not private')
  folder=private+'/booking-css-'+uuid.uuid4().hex;s.mkdir(folder,mode=0o700)
  write(s,folder+'/mortii.css.before',before,0o600)
  write(s,folder+'/mortii.css.stage',candidate,0o644)
  if read(s,path)!=before:raise ValueError('Concurrent change')
  changed=False
  try:
   changed=True;s.posix_rename(folder+'/mortii.css.stage',path)
   if read(s,path)!=candidate:raise RuntimeError('Production readback mismatch')
   request=urllib.request.Request('https://rentmor.pl/mortii.css?booking='+digest(candidate)[:16],headers={'Cache-Control':'no-cache'})
   with urllib.request.urlopen(request,timeout=30) as response:
    if response.status!=200 or response.read(500001)!=candidate:raise RuntimeError('Public CSS differs')
  except Exception:
   if changed:
    write(s,folder+'/mortii.css.rollback',before,0o644)
    s.posix_rename(folder+'/mortii.css.rollback',path)
    if read(s,path)!=before:raise RuntimeError('Rollback readback failed')
   raise RuntimeError('CSS publication failed; original restored') from None
  print('BOOKING_TEXTAREA_CSS_PUBLISHED_SHA256='+digest(candidate))
  progress=private+'/progress'
  try:s.mkdir(progress,mode=0o700)
  except IOError:pass
  s.chmod(progress,0o700)
  for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
   data=(root/'docs'/name).read_bytes()
   write(s,progress+'/'+name+'.css-stage',data,0o600)
   s.posix_rename(progress+'/'+name+'.css-stage',progress+'/'+name)
   if read(s,progress+'/'+name)!=data or s.lstat(progress+'/'+name).st_mode&0o777!=0o600:raise RuntimeError('Progress verification failed')
  print('TWO_PROGRESS_DOCUMENTS_SHA256_0600_VERIFIED')
 finally:transport.close()
if __name__=='__main__':
 try:main()
 except Exception as e:print('CSS_DEPLOYMENT_STOPPED:'+type(e).__name__);raise SystemExit(1)
