"""Owner-authorized, exact-version deployment of AI knowledge API and signed APK."""
import base64,hashlib,json,logging,os,stat,subprocess,tempfile,uuid,urllib.request
from pathlib import Path
import paramiko
ROOT=Path(__file__).resolve().parents[2]
ALLOWED={'protocol_sync.php','mortii_api.php','ai_chat_service.php','ai_app_knowledge.php'}
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
logging.getLogger('paramiko').addHandler(logging.NullHandler());logging.getLogger('paramiko').propagate=False
def sha(b):return hashlib.sha256(b).hexdigest()
def read(s,path,limit=2000000):
 m=s.lstat(path)
 if not stat.S_ISREG(m.st_mode) or m.st_size>limit:raise ValueError('Unsafe file')
 with s.open(path,'rb')as f:b=f.read(limit+1)
 if len(b)>limit:raise ValueError('Oversized file')
 return b,m.st_mode&0o777
def atomic(s,path,b,mode):
 temp=path+'.knowledge-'+uuid.uuid4().hex+'.tmp'
 try:
  with s.open(temp,'wb')as f:f.write(b)
  s.chmod(temp,mode)
  if read(s,temp,max(2000000,len(b)))[0]!=b:raise RuntimeError('Upload verification')
  s.posix_rename(temp,path)
  if read(s,path,max(2000000,len(b)))[0]!=b:raise RuntimeError('Readback')
 finally:
  try:s.remove(temp)
  except FileNotFoundError:pass
def signature(path):
 sdk=Path(os.environ['ANDROID_HOME']);signer=sorted(sdk.glob('build-tools/*/apksigner'))[-1]
 out=subprocess.check_output([str(signer),'verify','--print-certs',str(path)],text=True,stderr=subprocess.DEVNULL)
 return [line for line in out.splitlines()if 'certificate SHA-256 digest:'in line]
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222));s=None
 try:
  t.start_client(timeout=25)
  if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in PINS:raise ValueError('Host key')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(45)
  rows=json.loads((ROOT/'deployment/ai-knowledge/manifest.json').read_text())
  if len(rows)!=4 or {r['name']for r in rows}!=ALLOWED:raise ValueError('Allowlist')
  changes=[]
  for row in rows:
   name=row['name'];path='public_html/'+name
   try:old,mode=read(s,path)
   except FileNotFoundError:old,mode=None,0o644
   if (sha(old)if old is not None else None)!=row['before']:raise ValueError('Source changed: '+name)
   new=(ROOT/row['source']).read_bytes()
   if sha(new)!=row['after']:raise ValueError('Candidate hash')
   subprocess.run(['php','-l',str(ROOT/row['source'])],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
   changes.append((path,old,new,mode))
  newapk=Path('signed/app-release.apk').read_bytes();oldapk,apkmode=read(s,'public_html/RentMor.apk',100000000)
  with tempfile.TemporaryDirectory()as tmp:
   oldpath=Path(tmp)/'previous.apk';oldpath.write_bytes(oldapk)
   newpath=Path('signed/app-release.apk');oldsign=signature(oldpath);newsign=signature(newpath)
   if not oldsign or oldsign!=newsign:raise ValueError('Signing certificate differs')
   aapt=sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/aapt'))[-1]
   oldinfo=subprocess.check_output([str(aapt),'dump','badging',str(oldpath)],text=True,stderr=subprocess.DEVNULL).splitlines()[0]
   newinfo=subprocess.check_output([str(aapt),'dump','badging',str(newpath)],text=True,stderr=subprocess.DEVNULL).splitlines()[0]
   if "name='pl.rentmor.protokol'"not in newinfo or "versionCode='42'"not in newinfo or "versionName='1.7.27'"not in newinfo:raise ValueError('Wrong new version')
   if "versionCode='41'"not in oldinfo:raise ValueError('Published APK changed')
  changes.append(('public_html/RentMor.apk',oldapk,newapk,apkmode))
  # Documentation uses exact preflight hashes too, preserving concurrent work.
  docmanifest=json.loads((ROOT/'deployment/ai-knowledge/progress-manifest.json').read_text())
  if set(docmanifest)!={'CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md'}:raise ValueError('Document allowlist')
  for name,expected in docmanifest.items():
   path='rentmor-control-private/progress/'+name;old,mode=read(s,path)
   if sha(old)!=expected or mode&0o077:raise ValueError('Progress changed')
   changes.append((path,old,(ROOT/'docs'/name).read_bytes(),0o600))
  for path in ['rentmor-control-private','rentmor-control-private/progress']:
   m=s.lstat(path)
   if not stat.S_ISDIR(m.st_mode)or m.st_mode&0o077:raise ValueError('Private directory permissions')
  backup='rentmor-control-private/ai-knowledge-'+uuid.uuid4().hex;s.mkdir(backup,mode=0o700)
  for i,(path,old,new,mode)in enumerate(changes):
   if old is not None:atomic(s,backup+'/'+str(i),old,0o600)
  written=[]
  try:
   for path,old,new,mode in changes:
    try:current=read(s,path,100000000)[0]
    except FileNotFoundError:current=None
    if current!=old:raise ValueError('Concurrent edit')
    written.append((path,old,new,mode));atomic(s,path,new,mode)
   for path,old,new,mode in changes:
    if read(s,path,100000000)[0]!=new:raise RuntimeError('Final verification')
   # GET only. Never invoke AI, create a booking, or use a customer/device token.
   for endpoint in ['protocol_sync.php?view=ai_knowledge&audience=public','protocol_sync.php?view=ai_knowledge&audience=private']:
    try:urllib.request.urlopen('https://rentmor.pl/'+endpoint,timeout=30);raise RuntimeError('Unauthenticated request allowed')
    except urllib.error.HTTPError as e:
     data=json.loads(e.read(10000))
     if e.code!=401 or data.get('ok')is not False or 'knowledge'in data:raise RuntimeError('Unauthenticated endpoint check')
   with urllib.request.urlopen('https://rentmor.pl/RentMor.apk?knowledge=42',timeout=90)as response:
    if sha(response.read(100000001))!=sha(newapk):raise RuntimeError('Public installer differs')
  except Exception:
   for path,old,new,mode in reversed(written):
    current=read(s,path,100000000)[0]
    if current!=new:raise RuntimeError('Concurrent update blocks rollback')from None
    if old is None:s.remove(path)
    else:atomic(s,path,old,mode)
   raise RuntimeError('Deployment failed; originals restored')from None
  print('AI_KNOWLEDGE_DEPLOYED_READBACK_OK APK_1.7.27_42 SAME_SIGNER PUBLIC_APK_SHA256='+sha(newapk))
  print('UNAUTHENTICATED_PUBLIC_AND_PRIVATE_API_DENIED DOCS_READBACK_SHA256_OK')
 finally:
  if s:s.close()
  t.close()
if __name__=='__main__':
 try:main()
 except Exception as e:print('AI_KNOWLEDGE_DEPLOY_STOPPED_'+type(e).__name__);raise SystemExit(1)
