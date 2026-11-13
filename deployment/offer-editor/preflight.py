"""Only source hashes; never read reservations, customers, credentials or data.json."""
import base64,hashlib,json,os,stat
from pathlib import Path
import paramiko
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  pin=base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
  if pin not in PINS:raise ValueError('Host key mismatch')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30)
  base=json.loads(Path('deployment/offer-editor/baseline.json').read_text())
  ok=True
  for name,expected in base.items():
   path='public_html/'+name;meta=s.lstat(path)
   if not stat.S_ISREG(meta.st_mode) or meta.st_size>300000:raise ValueError('Unexpected source')
   with s.open(path,'rb') as f:raw=f.read(300001)
   actual=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
   print('EDITOR_BASELINE '+name+' '+actual+' '+('MATCH'if actual==expected else'MISMATCH'))
   ok=ok and actual==expected
  for name in ['offer_content.php','offer_content_form.php']:
   try:s.lstat('public_html/'+name)
   except FileNotFoundError:print('EDITOR_NEW_FILE_ABSENT '+name);continue
   raise ValueError('New file already exists')
  if not ok:raise ValueError('Hosting source differs; do not publish')
  print('EDITOR_ALL_HOSTING_BASELINES_MATCH')
 finally:t.close()
if __name__=='__main__':
 try:main()
 except Exception as e:print('EDITOR_PREFLIGHT_STOPPED_'+type(e).__name__);raise SystemExit(1)
