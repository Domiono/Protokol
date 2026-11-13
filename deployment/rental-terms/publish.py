"""Publish only authorized rental wording, retaining current catalogue fields."""
import base64,hashlib,importlib.util,json,os,stat,subprocess,sys,tempfile,urllib.request,uuid
from pathlib import Path
from html.parser import HTMLParser
from catalog_patch import catalog,terms,PERIOD
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('calendar_deploy',ROOT/'deployment/booking-calendar/deploy_calendar.py')
common=importlib.util.module_from_spec(spec);spec.loader.exec_module(common)
class Text(HTMLParser):
 def __init__(self):super().__init__();self.parts=[];self.hidden=0
 def handle_starttag(self,tag,attrs):
  if tag in ('script','style'):self.hidden+=1
 def handle_endtag(self,tag):
  if tag in ('script','style'):self.hidden=max(0,self.hidden-1)
 def handle_data(self,data):
  if not self.hidden:self.parts.append(data)
def public(path):
 req=urllib.request.Request('https://rentmor.pl/'+path+('&' if '?' in path else '?')+'terms_check='+uuid.uuid4().hex,headers={'Cache-Control':'no-cache'})
 with urllib.request.urlopen(req,timeout=35) as r:
  if r.status!=200:raise RuntimeError('Public status')
  data=r.read(2000001).decode('utf-8')
 parser=Text();parser.feed(data)
 return data,' '.join(' '.join(parser.parts).split())
def verify():
 _,home=public('')
 if PERIOD not in home or 'nie świadczymy usługi dowozu' not in home:raise RuntimeError('Homepage wording not public')
 raw,grinder=public('szlifierka-do-betonu-i-posadzek-250mm-wynajem-krakow/')
 for value in [PERIOD,'40 zł za każde rozpoczęte 0,5 mm','sama szlifierka 130 zł/doba; z odkurzaczem 160 zł/doba','rezerwacji przy sprzęcie']:
  if value not in grinder:raise RuntimeError('Grinder wording not public')
 if 'data-rm-grinder-price-summary="v1"' not in raw or 'mortii.js?v=7' not in raw:raise RuntimeError('Expected public hooks missing')
 _,spray=public('agregat-malarski-wabrotech-wt20i-wynajem-krakow-malowanie-natryskowe/')
 for value in ['wszystkie farby wodnorozpuszczalne','Nie nadaje się do farb strukturalnych.',PERIOD]:
  if value not in spray:raise RuntimeError('Spray wording not public')
 _,rules=public('zasady.php')
 for value in [PERIOD,'Nie świadczymy usługi dowozu','rezerwacja wymaga potwierdzenia przez RentMor']:
  if value not in rules:raise RuntimeError('Rental rules not public')
 print('PUBLIC_HOME_GRINDER_SPRAY_RULES_CONFIRMED')
def main():
 import paramiko
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  pin=base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
  if pin not in common.PINS:raise ValueError('Host key mismatch')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30)
  private='rentmor-control-private'
  meta=s.lstat(private)
  if not stat.S_ISDIR(meta.st_mode) or meta.st_mode&0o077:raise ValueError('Private directory required')
  for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
   prior=common.read(s,private+'/progress/'+name)
   print('PRIOR_PROGRESS_SHA256 '+name+' '+common.sha(prior))
  if os.environ.get('RENTAL_TERMS_PROGRESS_ONLY')=='1':
   sync_progress(s);return
  manifest=json.loads((ROOT/'deployment/rental-terms/manifest.json').read_text())
  changes=[]
  for name,guard in manifest.items():
   old=common.read(s,'public_html/'+name);new=(ROOT/'server'/name).read_bytes()
   if common.sha(old)!=guard['expected_sha256'] or common.sha(new)!=guard['sha256']:raise ValueError('Source changed '+name)
   subprocess.run(['php','-l',str(ROOT/'server'/name)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
   changes.append((name,old,new))
  for name,patch in [('data.json',catalog),('terms.json',terms)]:
   old=common.read(s,'public_html/'+name);new=patch(old)
   if old!=new:changes.append((name,old,new))
  folder=private+'/rental-terms-'+uuid.uuid4().hex;s.mkdir(folder,mode=0o700)
  for name,old,new in changes:
   common.write(s,folder+'/'+name+'.before',old,0o600)
  print('SOURCE_JSON_PREFLIGHT_AND_PRIVATE_BACKUP_OK')
  applied=[]
  try:
   for name,old,new in changes:
    target='public_html/'+name
    if common.read(s,target)!=old:raise ValueError('Concurrent modification')
    mode=stat.S_IMODE(s.lstat(target).st_mode)
    applied.append((name,old,new,mode))
    common.write(s,target,new,mode)
   print('ALL_FILES_SHA256_READBACK_OK')
   verify()
  except Exception:
   for name,old,new,mode in reversed(applied):
    if common.read(s,'public_html/'+name)!=new:raise RuntimeError('Concurrent change blocks rollback')
    common.write(s,'public_html/'+name,old,mode)
   print('ORIGINAL_FILES_RESTORED_SHA256_VERIFIED')
   raise
  print('RENTAL_TERMS_PUBLISHED_BACKUP_READBACK_PUBLIC_OK')
  sync_progress(s)
 finally:t.close()
def sync_progress(s):
 target='rentmor-control-private/progress'
 s.chmod(target,0o700)
 for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
  data=(ROOT/'docs'/name).read_bytes()
  common.write(s,target+'/'+name,data,0o600)
  if stat.S_IMODE(s.lstat(target+'/'+name).st_mode)!=0o600:raise RuntimeError('Progress mode')
 print('TWO_PROGRESS_DOCUMENTS_SHA256_0600_VERIFIED')
if __name__=='__main__':
 try:main()
 except Exception as e:print('RENTAL_TERMS_STOPPED_'+type(e).__name__+' '+str(e)[:120]);sys.exit(1)
