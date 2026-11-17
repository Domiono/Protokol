"""Publish tested editor PHP and a same-signer APK with guarded writes and rollback."""
import base64,hashlib,json,os,stat,subprocess,tempfile,urllib.request,urllib.error,uuid,zipfile,io
from pathlib import Path
import paramiko
from preflight import PINS
ROOT=Path(__file__).resolve().parents[2]
def sha(b):return hashlib.sha256(b).hexdigest()
def blob(b):return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def read(s,p):
 m=s.lstat(p)
 if not stat.S_ISREG(m.st_mode) or m.st_size>100000000:raise ValueError('Unsafe file')
 with s.open(p,'rb')as f:
  if m.st_size>1000000:f.prefetch(m.st_size,max_concurrent_requests=32)
  b=f.read(m.st_size+1)
 if len(b)!=m.st_size:raise ValueError('Concurrent read')
 return b
def put(s,p,b,mode):
 with s.open(p,'wb')as f:f.set_pipelined(True);f.write(b)
 s.chmod(p,mode)
 if read(s,p)!=b:raise RuntimeError('Readback')
def patch_admin(old):
 text=old.decode()
 for before,after in json.loads((ROOT/'deployment/offer-editor/admin-patch.json').read_text()):
  if text.count(before)!=1:raise ValueError('Admin patch context differs')
  text=text.replace(before,after)
 return text.encode()
def public(path):
 req=urllib.request.Request('https://rentmor.pl/'+path+('&'if'?'in path else'?')+'offer_editor='+uuid.uuid4().hex,headers={'Cache-Control':'no-cache'})
 with urllib.request.urlopen(req,timeout=90)as r:return r.read(100000001),r.headers
def certify(old,newpath):
 signer=sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/apksigner'))[-1]
 def cert(p):return [l for l in subprocess.check_output([str(signer),'verify','--print-certs',str(p)],text=True,stderr=subprocess.DEVNULL).splitlines()if'certificate SHA-256 digest:'in l]
 with tempfile.TemporaryDirectory()as temp:
  p=Path(temp)/'old.apk';p.write_bytes(old)
  if not cert(p)or cert(p)!=cert(newpath):raise ValueError('APK signer differs')
 aapt=sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/aapt'))[-1]
 info=subprocess.check_output([str(aapt),'dump','badging',str(newpath)],text=True,stderr=subprocess.DEVNULL).splitlines()[0]
 if any(v not in info for v in ["name='pl.rentmor.protokol'","versionCode='47'","versionName='1.7.32'"]):raise ValueError('Wrong APK version')
 z=zipfile.ZipFile(newpath);dex=b''.join(z.read(n)for n in z.namelist()if n.endswith('.dex'))
 if b'OfferContentEditor'not in dex or b'AiKnowledgePage'not in dex or b'SearchConsolePage'in dex:raise ValueError('APK feature checks')
def verify_public(apk):
 for p in ['download-app.php?release=47','RentMor.apk?release=47']:
  data,h=public(p)
  if sha(data)!=sha(apk):raise RuntimeError('Public APK differs')
  if p.startswith('download-app')and('no-store'not in h.get('Cache-Control','')or'RentMor-1.7.32.apk'not in h.get('Content-Disposition','')):raise RuntimeError('Download metadata')
 for p in ['', 'szlifierka-do-betonu-i-posadzek-250mm-wynajem-krakow/','agregat-malarski-wabrotech-wt20i-wynajem-krakow-malowanie-natryskowe/','zasady.php','admin.php']:
  data,_=public(p)
  if b'Fatal error' in data or b'mortii.js?v=7'not in data and p in ['', 'szlifierka-do-betonu-i-posadzek-250mm-wynajem-krakow/']:raise RuntimeError('Public page failed')
  if p.startswith('szlifierka-') and any(v.encode()not in data for v in ['40 zł za każde rozpoczęte 0,5 mm','160 zł/doba','MSW-FG-250']):raise RuntimeError('Public descriptions changed')
 try:public('protocol_sync.php')
 except urllib.error.HTTPError as e:
  if e.code!=401 or json.loads(e.read(10000)).get('ok')is not False:raise RuntimeError('API auth')
 else:raise RuntimeError('API auth missing')
 print('EDITOR_PUBLIC_PAGES_APK_BYTES_HEADERS_AND_API_AUTH_VERIFIED')
def apply_transaction(s,changes,docs,verify):
 applied=[]
 def apply(row):
  p,old,new,mode=row
  try:current=read(s,p)
  except FileNotFoundError:current=None
  if current!=old:raise ValueError('Concurrent source change')
  try:put(s,p,new,mode)
  except Exception:
   # A short SFTP write may leave a partial file; restore our own attempted write.
   if old is None:
    try:s.remove(p)
    except FileNotFoundError:pass
   else:put(s,p,old,mode)
   raise
  applied.append(row)
 try:
  for row in changes:apply(row)
  verify()
  for row in docs:apply(row)
 except Exception:
  for p,old,new,mode in reversed(applied):
   if read(s,p)!=new:raise RuntimeError('Concurrent modification blocks rollback')from None
   if old is None:s.remove(p)
   else:put(s,p,old,mode)
  print('EDITOR_ORIGINAL_FILES_RESTORED');raise
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in PINS:raise ValueError('Host key')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(60)
  private='rentmor-control-private'
  if not stat.S_ISDIR(s.lstat(private).st_mode)or s.lstat(private).st_mode&0o077:raise ValueError('Private directory')
  changes=[]
  base=json.loads((ROOT/'deployment/offer-editor/baseline.json').read_text())
  for name,expected in base.items():
   old=read(s,'public_html/'+name)
   if blob(old)!=expected:raise ValueError('Source changed '+name)
   if name=='rental_offer_details_extra.php':continue
   new=patch_admin(old)if name=='admin.php'else(ROOT/'server'/name).read_bytes()
   changes.append((name,old,new,0o644))
  for name in ['offer_content.php','offer_content_form.php']:
   try:read(s,'public_html/'+name)
   except FileNotFoundError:pass
   else:raise ValueError('New helper exists')
   changes.insert(0,(name,None,(ROOT/'server'/name).read_bytes(),0o644))
  # Dependencies before their callers, admin form last.
  order={'offer_content.php':0,'offer_content_form.php':1,'rental_offer_details.php':2,'seo_structure.php':3,'protocol_sync.php':4,'admin.php':5}
  changes.sort(key=lambda row:order[row[0]])
  for name,old,new,mode in changes:
   with tempfile.TemporaryDirectory()as tmp:
    p=Path(tmp)/name;p.write_bytes(new);subprocess.run(['php','-l',str(p)],check=True,stdout=subprocess.DEVNULL)
  apkpath=ROOT/'app/build/outputs/apk/release/app-release.apk';apk=apkpath.read_bytes()
  oldapk=read(s,'public_html/RentMor.apk')
  if sha(oldapk)!='85ad13f24ad1dc7d397568f4db175123cd3855c7dedf5f22003134776392e846':raise ValueError('Installed APK changed')
  certify(oldapk,apkpath)
  meta=read(s,'public_html/app-release.json');previous=json.loads(meta)
  if previous.get('version')!='1.7.31' or previous.get('version_code')!=46:raise ValueError('Release metadata changed')
  newmeta=json.dumps({'version':'1.7.32','version_code':47,'sha256':sha(apk),'size':len(apk)},indent=2).encode()+b'\n'
  changes.extend([('RentMor.apk',oldapk,apk,0o644),('app-release.json',meta,newmeta,0o644)])
  # Check current private documents and preserve them; never touch rental/customer data.
  docs=[]
  for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
   p=private+'/progress/'+name;old=read(s,p)
   if s.lstat(p).st_mode&0o777!=0o600:raise ValueError('Progress permissions')
   docs.append((p,old,(ROOT/'docs'/name).read_bytes(),0o600))
  folder=private+'/offer-editor-'+uuid.uuid4().hex;s.mkdir(folder,mode=0o700)
  for name,old,new,mode in changes:
   if old is not None:put(s,folder+'/'+name+'.before',old,0o600)
  for p,old,new,mode in docs:put(s,folder+'/'+Path(p).name+'.before',old,0o600)
  print('EDITOR_PRIVATE_BACKUPS_AND_PREFLIGHT_VERIFIED')
  apply_transaction(s,[('public_html/'+name,old,new,mode)for name,old,new,mode in changes],docs,lambda:verify_public(apk))
  print('EDITOR_TWO_PROGRESS_DOCUMENTS_SHA256_0600_VERIFIED')
  print('EDITOR_PUBLISHED_VERSION_1.7.32_CODE47_SHA256='+sha(apk))
  print('EDITOR_SOURCE_READBACK_ALL_VERIFIED')
 finally:t.close()
if __name__=='__main__':
 try:main()
 except Exception as e:print('EDITOR_PUBLISH_STOPPED_'+type(e).__name__+':'+str(e));raise SystemExit(1)
