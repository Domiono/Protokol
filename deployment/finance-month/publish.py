"""Publish reviewed source patches and same-signer APK; never read customer storage."""
import argparse,base64,hashlib,json,os,stat,subprocess,tempfile,uuid
from pathlib import Path
import importlib.util
import paramiko
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('guarded_apk',ROOT/'deployment/reservation-recovery/publish.py')
common=importlib.util.module_from_spec(spec);spec.loader.exec_module(common)
sha=common.sha
OLD_APK='d21eafd79268dea65dcc3396186cc4f57a415b90cf831e050f36c021f8bf05c5'
def certify(old,path):
 signer=sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/apksigner'))[-1]
 def certificate(p):return [line for line in subprocess.check_output([str(signer),'verify','--print-certs',str(p)],text=True,stderr=subprocess.DEVNULL).splitlines()if'certificate SHA-256 digest:'in line]
 with tempfile.TemporaryDirectory()as temp:
  previous=Path(temp)/'old.apk';previous.write_bytes(old)
  if not certificate(previous)or certificate(previous)!=certificate(path):raise ValueError('Signer mismatch')
 aapt=sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/aapt'))[-1]
 info=subprocess.check_output([str(aapt),'dump','badging',str(path)],text=True).splitlines()[0]
 if any(value not in info for value in ["name='pl.rentmor.protokol'","versionCode='52'","versionName='1.7.37'"]):raise ValueError('Wrong APK version')
 import zipfile
 with zipfile.ZipFile(path)as z:dex=b''.join(z.read(n)for n in z.namelist()if n.endswith('.dex'))
 if any(value not in dex for value in [b'AdditionalEquipmentEditor',b'additional_items',b'queueConfirmedIssue',b'OfferContentEditor',b'AiKnowledgePage']):raise ValueError('Missing APK feature')
 print('FINANCE_APK_VERSION_FEATURES_AND_SAME_SIGNER_VERIFIED')

def public_checks(apk):
 for path in ['download-app.php?release=52','RentMor.apk?release=52']:
  raw,headers=common.public(path)
  if sha(raw)!=sha(apk):raise ValueError('Public APK mismatch')
  if path.startswith('download-app')and('no-store'not in headers.get('Cache-Control','')or'RentMor-1.7.37.apk'not in headers.get('Content-Disposition','')):raise ValueError('Download metadata')
 raw,_=common.public('')
 if b'<html'not in raw.lower():raise ValueError('Homepage smoke check')
 import urllib.request,urllib.error
 try:
  urllib.request.urlopen('https://rentmor.pl/protocol_sync.php',timeout=30)
 except urllib.error.HTTPError as e:
  if e.code!=401:raise ValueError('API authorization smoke check')
 else:raise ValueError('API unexpectedly public')
 print('FINANCE_PUBLIC_APK_HEADERS_HOME_AND_API_AUTH_VERIFIED')

def main(progress_only=False):
 manifest=json.loads((ROOT/'deployment/finance-month/baseline.json').read_text())
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in common.PINS:raise ValueError('Host key mismatch')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(60)
  private='rentmor-control-private'
  for directory in [private,private+'/progress']:
   meta=s.lstat(directory)
   if not stat.S_ISDIR(meta.st_mode)or meta.st_mode&0o777!=0o700:raise ValueError('Private directory permissions')
  docs=[]
  for name,expected in manifest['_progress'].items():
   path=private+'/progress/'+name;old=common.read(s,path)
   if sha(old)!=expected or s.lstat(path).st_mode&0o777!=0o600:raise ValueError('Private progress changed')
   docs.append((path,old,(ROOT/'docs'/name).read_bytes(),0o600))
  changes=[]
  if not progress_only:
   apkpath=ROOT/'app/build/outputs/apk/release/app-release.apk';apk=apkpath.read_bytes()
   old=common.read(s,'public_html/RentMor.apk')
   if sha(old)!=OLD_APK:raise ValueError('Installed APK changed')
   certify(old,apkpath)
   oldmeta=common.read(s,'public_html/app-release.json');meta=json.loads(oldmeta)
   if meta.get('version')!='1.7.36'or meta.get('version_code')!=51:raise ValueError('Release metadata changed')
   newmeta=json.dumps({'version':'1.7.37','version_code':52,'sha256':sha(apk),'size':len(apk)},indent=2).encode()+b'\n'
   changes.extend([('public_html/RentMor.apk',old,apk,0o644),('public_html/app-release.json',oldmeta,newmeta,0o644)])
  backup=private+'/finance-month-'+uuid.uuid4().hex;s.mkdir(backup,mode=0o700)
  for path,old,new,mode in changes+docs:
   if old is not None:common.put(s,backup+'/'+Path(path).name+'.before',old,0o600)
  print('FINANCE_EXACT_SOURCE_AND_PROGRESS_PREFLIGHT_PRIVATE_BACKUP_VERIFIED')
  common.apply_transaction(s,changes,docs,(lambda:None)if progress_only else lambda:public_checks(apk))
  for path,old,new,mode in changes+docs:
   if common.read(s,path)!=new or s.lstat(path).st_mode&0o777!=mode:raise RuntimeError('Final readback or permissions')
  print('FINANCE_ALL_FILES_READBACK_AND_TWO_PROGRESS_DOCUMENTS_SHA256_0600_VERIFIED')
  if not progress_only:print('FINANCE_PUBLISHED_VERSION_1.7.37_CODE52_SHA256='+sha(apk))
 finally:t.close()
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--progress-only',action='store_true');args=parser.parse_args()
 try:main(args.progress_only)
 except Exception as e:print('FINANCE_PUBLICATION_STOPPED_'+type(e).__name__+':'+str(e));raise SystemExit(1)
