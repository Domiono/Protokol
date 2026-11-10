"""Publish only the verified same-signer APK, public release metadata and download endpoint."""
import base64,hashlib,json,logging,os,pathlib,stat,subprocess,tempfile,urllib.request,uuid
import paramiko,zipfile,io
ROOT=pathlib.Path(__file__).resolve().parents[2]
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
logging.getLogger('paramiko').addHandler(logging.NullHandler());logging.getLogger('paramiko').propagate=False
def sha(b):return hashlib.sha256(b).hexdigest()
def read(s,p):
 m=s.lstat(p)
 if not stat.S_ISREG(m.st_mode)or m.st_size>100000000:raise ValueError('Unsafe source')
 with s.open(p,'rb')as f:
  if m.st_size>1000000:f.prefetch(m.st_size,max_concurrent_requests=32)
  b=f.read(m.st_size+1)
 if len(b)!=m.st_size:raise ValueError('Concurrent source change')
 return b,m.st_mode&0o777
def put(s,p,b,mode):
 tmp=p+'.download-'+uuid.uuid4().hex+'.tmp'
 try:
  with s.open(tmp,'wb')as f:f.set_pipelined(True);f.write(b)
  s.chmod(tmp,mode)
  if read(s,tmp)[0]!=b:raise RuntimeError('Upload differs')
  s.posix_rename(tmp,p)
  if read(s,p)[0]!=b:raise RuntimeError('Readback differs')
 finally:
  try:s.remove(tmp)
  except FileNotFoundError:pass
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222));s=None
 try:
  t.start_client(timeout=25)
  if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in PINS:raise ValueError('Host key')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False);s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(40)
  old,mode=read(s,'public_html/RentMor.apk')
  if sha(old)!='584bb66dade23ecab75a2d77ccac496226c36fd624c6c1f199f81277908fd784':raise ValueError('Published APK changed')
  newpath=ROOT/'app/build/outputs/apk/release/app-release.apk';new=newpath.read_bytes()
  z=zipfile.ZipFile(io.BytesIO(new));dex=b''.join(z.read(n)for n in z.namelist()if n.endswith('.dex'))
  if b'download-app.php'not in dex or 'AI — Wiedza'.encode()not in dex:raise ValueError('Missing application features')
  signer=sorted(pathlib.Path(os.environ['ANDROID_HOME']).glob('build-tools/*/apksigner'))[-1]
  def cert(p):return [l for l in subprocess.check_output([str(signer),'verify','--print-certs',str(p)],text=True,stderr=subprocess.DEVNULL).splitlines()if 'certificate SHA-256 digest:'in l]
  with tempfile.TemporaryDirectory()as temp:
   prev=pathlib.Path(temp)/'old.apk';prev.write_bytes(old)
   if not cert(prev)or cert(prev)!=cert(newpath):raise ValueError('Signature differs')
  aapt=sorted(pathlib.Path(os.environ['ANDROID_HOME']).glob('build-tools/*/aapt'))[-1];info=subprocess.check_output([str(aapt),'dump','badging',str(newpath)],text=True,stderr=subprocess.DEVNULL).splitlines()[0]
  if "name='pl.rentmor.protokol'"not in info or "versionCode='43'"not in info or "versionName='1.7.28'"not in info:raise ValueError('Wrong version')
  meta=json.dumps({'version':'1.7.28','version_code':43,'sha256':sha(new),'size':len(new)},indent=2).encode()+b'\n'
  changes=[('public_html/RentMor.apk',old,new,mode)]
  for name,b in [('app-release.json',meta),('download-app.php',(ROOT/'server/download-app.php').read_bytes())]:
   p='public_html/'+name
   try:read(s,p);raise ValueError('New path exists')
   except FileNotFoundError:pass
   changes.append((p,None,b,0o644))
  # Read and compare the two private progress copies before any production write.
  baseline={'CONTINUE-RENTMOR.md':'28c51e741f9815629893e8609a1032c15cc88b86ccc95130a6daa9d17d395b94','RENTMOR-PROJECT-LOG.md':'3555bda28e6a797e927fdd2c03d9bf4f1b498ffa37daa40523a679ceb0a84a94'}
  for n,h in baseline.items():
   b,m=read(s,'rentmor-control-private/progress/'+n)
   if sha(b)!=h or m&0o077:raise ValueError('Progress changed')
  m=s.lstat('rentmor-control-private')
  if not stat.S_ISDIR(m.st_mode)or m.st_mode&0o077:raise ValueError('Private backup required')
  backup='rentmor-control-private/download-'+uuid.uuid4().hex;s.mkdir(backup,mode=0o700);put(s,backup+'/previous.apk',old,0o600)
  written=[]
  try:
   for p,before,after,m in changes:
    try:current=read(s,p)[0]
    except FileNotFoundError:current=None
    if current!=before:raise ValueError('Concurrent change')
    written.append((p,before,after,m));put(s,p,after,m)
   for path in ['download-app.php?request=release43','RentMor.apk?release43=verified']:
    with urllib.request.urlopen('https://rentmor.pl/'+path,timeout=90)as r:
     if sha(r.read(100000001))!=sha(new):raise ValueError('Public APK differs')
     if path.startswith('download-app')and ('no-store'not in r.headers.get('Cache-Control','')or 'RentMor-1.7.28.apk'not in r.headers.get('Content-Disposition','')):raise ValueError('Download headers')
  except Exception:
   for p,before,after,m in reversed(written):
    try:current=read(s,p)[0]
    except FileNotFoundError:current=None
    if current==before:continue
    if current!=after:raise RuntimeError('Concurrent change blocks rollback')from None
    if before is None:s.remove(p)
    else:put(s,p,before,m)
   raise RuntimeError('Publication failed, originals restored')from None
  print('DOWNLOAD_FIX_PUBLISHED VERSION_1.7.28_CODE_43 SAME_SIGNER SHA256='+sha(new))
  print('PUBLIC_DOWNLOAD_HEADERS_FILENAME_AND_BYTES_OK PRIVATE_BASELINE_OK')
 finally:
  if s:s.close()
  t.close()
if __name__=='__main__':
 try:main()
 except Exception as e:print('DOWNLOAD_FIX_STOPPED_'+type(e).__name__);raise SystemExit(1)
