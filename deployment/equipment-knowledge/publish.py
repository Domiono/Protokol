"""Exact-source-version update; never reads or writes saved customer/knowledge records."""
import base64,hashlib,json,os,pathlib,stat,subprocess,sys,tempfile,urllib.request,uuid
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'deployment/apk-download'))
from publish import read,put,sha,PINS
import paramiko
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222));s=None
 try:
  t.start_client(timeout=25)
  if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in PINS:raise ValueError('Host key')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False);s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(40)
  rows=json.loads((ROOT/'deployment/equipment-knowledge/manifest.json').read_text());byname={r['name']:r for r in rows}
  allowed=['ai_equipment_knowledge.php','ai_knowledge_store.php','ai_app_knowledge.php','mortii_api.php']
  if len(rows)!=4 or set(byname)!=set(allowed):raise ValueError('Allowlist')
  changes=[]
  for name in allowed:
   p='public_html/'+name
   try:old,mode=read(s,p)
   except FileNotFoundError:old,mode=None,0o644
   row=byname[name];new=(ROOT/'server'/name).read_bytes()
   if (sha(old)if old is not None else None)!=row['before']or sha(new)!=row['after']:raise ValueError('Source changed: '+name)
   changes.append((p,old,new,mode))
  oldapk,mode=read(s,'public_html/RentMor.apk')
  if sha(oldapk)!='2c292bb74425dde8822ad7cdac8fe22bb60a0539fa56449a87ce1c485085d386':raise ValueError('Published APK changed')
  oldmeta,mm=read(s,'public_html/app-release.json');old=json.loads(oldmeta)
  if old.get('version_code')!=43 or old.get('sha256')!=sha(oldapk):raise ValueError('Release metadata changed')
  apk=ROOT/'app/build/outputs/apk/release/app-release.apk';new=apk.read_bytes()
  signer=sorted(pathlib.Path(os.environ['ANDROID_HOME']).glob('build-tools/*/apksigner'))[-1]
  def cert(p):return [l for l in subprocess.check_output([str(signer),'verify','--print-certs',str(p)],text=True,stderr=subprocess.DEVNULL).splitlines()if 'certificate SHA-256 digest:'in l]
  with tempfile.TemporaryDirectory()as tmp:
   prev=pathlib.Path(tmp)/'previous.apk';prev.write_bytes(oldapk)
   if not cert(prev)or cert(prev)!=cert(apk):raise ValueError('Signing certificate differs')
  aapt=sorted(pathlib.Path(os.environ['ANDROID_HOME']).glob('build-tools/*/aapt'))[-1];info=subprocess.check_output([str(aapt),'dump','badging',str(apk)],text=True,stderr=subprocess.DEVNULL).splitlines()[0]
  if "versionCode='44'"not in info or "versionName='1.7.29'"not in info or "name='pl.rentmor.protokol'"not in info:raise ValueError('Wrong APK')
  metadata=json.dumps({'version':'1.7.29','version_code':44,'size':len(new),'sha256':sha(new)},indent=2).encode()+b'\n'
  changes.extend([('public_html/RentMor.apk',oldapk,new,mode),('public_html/app-release.json',oldmeta,metadata,mm)])
  baseline={'CONTINUE-RENTMOR.md':'60b217801727f29aa0ba92ee4579e98d5f9c69030824e06d35c994a0611b01b9','RENTMOR-PROJECT-LOG.md':'4b013df552071006577b08e43cf4f7e053c810a06e30e9aa3e2b0810609f2c08'}
  for n,h in baseline.items():
   b,m=read(s,'rentmor-control-private/progress/'+n)
   if sha(b)!=h or m&0o077:raise ValueError('Private progress differs')
  m=s.lstat('rentmor-control-private')
  if not stat.S_ISDIR(m.st_mode)or m.st_mode&0o077:raise ValueError('Private backup required')
  backup='rentmor-control-private/equipment-knowledge-'+uuid.uuid4().hex;s.mkdir(backup,mode=0o700)
  for i,(p,before,after,m)in enumerate(changes):
   if before is not None:put(s,backup+'/'+str(i),before,0o600)
  written=[]
  try:
   for p,before,after,m in changes:
    try:current=read(s,p)[0]
    except FileNotFoundError:current=None
    if current!=before:raise ValueError('Concurrent edit')
    written.append((p,before,after,m));put(s,p,after,m)
   with urllib.request.urlopen('https://rentmor.pl/download-app.php?release=44',timeout=90)as r:
    if sha(r.read(100000001))!=sha(new)or 'RentMor-1.7.29.apk'not in r.headers.get('Content-Disposition','')or 'no-store'not in r.headers.get('Cache-Control',''):raise ValueError('Public download differs')
   try:urllib.request.urlopen('https://rentmor.pl/protocol_sync.php?view=ai_knowledge&equipment=fixture',timeout=20);raise ValueError('Unauthenticated endpoint allowed')
   except urllib.error.HTTPError as e:
    if e.code!=401 or json.loads(e.read()).get('ok')is not False:raise ValueError('Unauthenticated endpoint')
  except Exception:
   for p,before,after,m in reversed(written):
    try:current=read(s,p)[0]
    except FileNotFoundError:current=None
    if current==before:continue
    if current!=after:raise RuntimeError('Concurrent edit blocks rollback')from None
    if before is None:s.remove(p)
    else:put(s,p,before,m)
   raise RuntimeError('Publication failed; originals restored')from None
  print('EQUIPMENT_KNOWLEDGE_PUBLISHED VERSION_1.7.29_CODE_44 SAME_SIGNER SHA256='+sha(new))
  print('SIX_FILES_READBACK_PUBLIC_APK_AND_UNAUTHENTICATED_API_OK NO_KNOWLEDGE_DATA_CHANGED')
 finally:
  if s:s.close()
  t.close()
if __name__=='__main__':
 try:main()
 except Exception as e:print('EQUIPMENT_KNOWLEDGE_STOPPED_'+type(e).__name__);raise SystemExit(1)
