"""Small public-link update; no catalogue writes, no APK and no private rental data."""
import base64,hashlib,importlib.util,json,os,stat,subprocess,sys,tempfile,urllib.request,uuid
from pathlib import Path
from html.parser import HTMLParser
import paramiko
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'deployment/offer-editor'))
spec=importlib.util.spec_from_file_location('editor_publish',ROOT/'deployment/offer-editor/publish.py')
common=importlib.util.module_from_spec(spec);spec.loader.exec_module(common)
class Links(HTMLParser):
 def __init__(self):super().__init__();self.urls=[]
 def handle_starttag(self,tag,attrs):
  if tag=='a':self.urls.append(dict(attrs).get('href',''))
def get(path):
 request=urllib.request.Request('https://rentmor.pl/'+path+('&'if'?'in path else'?')+'task_links='+uuid.uuid4().hex,headers={'Cache-Control':'no-cache'})
 with urllib.request.urlopen(request,timeout=40)as response:
  if response.status!=200:raise RuntimeError('Unexpected public status')
  data=response.read(2000001).decode('utf-8')
 if 'Fatal error'in data or 'Warning: require'in data:raise RuntimeError('Public PHP error')
 return data
def verify():
 for path in ['', 'sprzet-budowlany/']:
  page=get(path);parser=Links();parser.feed(page)
  if page.count('data-rm-task-links="v1"')!=1:raise RuntimeError('Task link block missing or duplicate')
  for target in ['agregat-malarski-wabrotech-wt20i-wynajem-krakow-malowanie-natryskowe','szlifierka-do-betonu-i-posadzek-250mm-wynajem-krakow']:
   if '/'+target+'/'not in parser.urls:raise RuntimeError('Offer link missing')
   offer=get(target+'/')
   if '<h1'not in offer or 'mortii.js?v=7'not in offer:raise RuntimeError('Offer unavailable')
 guide=get('poradnik-agregat-malarski-jak-malowac-dobor-dysz.php')
 if guide.count('data-rm-airless-offer-link="v1"')!=1:raise RuntimeError('Early guide link missing')
 if 'Zobacz ofertę wynajmu agregatu WT20i w Krakowie'not in guide:raise RuntimeError('Guide CTA missing')
 if 'data-video-src='not in guide:raise RuntimeError('Existing video privacy gate missing')
 print('TASK_LINKS_HOME_CATEGORY_GUIDE_AND_BOTH_OFFER_TARGETS_VERIFIED')
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in common.PINS:raise ValueError('Host key mismatch')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(40)
  private='rentmor-control-private';m=s.lstat(private)
  if not stat.S_ISDIR(m.st_mode)or m.st_mode&0o077:raise ValueError('Private backup required')
  if os.environ.get('TASK_LINKS_PROGRESS_ONLY')=='1':
   for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
    p=private+'/progress/'+name;data=(ROOT/'docs'/name).read_bytes();common.put(s,p,data,0o600)
    if s.lstat(p).st_mode&0o777!=0o600:raise ValueError('Progress permissions')
   print('TASK_LINKS_TWO_PROGRESS_DOCUMENTS_SHA256_0600_VERIFIED');return
  base=json.loads((ROOT/'deployment/task-links/baseline.json').read_text());changes=[]
  if os.environ.get('TASK_LINKS_PREFLIGHT_ONLY')=='1':
   for name in ['rental_task_links.php','seo_structure.php','category.php','poradnik-agregat-malarski-jak-malowac-dobor-dysz.php']:
    try:old=common.read(s,'public_html/'+name)
    except FileNotFoundError:old=None
    print('TASK_LINKS_LIVE_BLOB '+name+' '+(common.blob(old)if old is not None else 'ABSENT'))
   return
  for name in ['rental_task_links.php','seo_structure.php','category.php','poradnik-agregat-malarski-jak-malowac-dobor-dysz.php']:
   path='public_html/'+name
   try:old=common.read(s,path)
   except FileNotFoundError:old=None
   expected=base.get(name)
   actual=common.blob(old)if old is not None else None
   if actual!=expected:raise ValueError('Source changed '+name+' blob='+str(actual))
   new=(ROOT/'server'/name).read_bytes()
   with tempfile.TemporaryDirectory()as tmp:
    p=Path(tmp)/name;p.write_bytes(new);subprocess.run(['php','-l',str(p)],check=True,stdout=subprocess.DEVNULL)
   changes.append((path,old,new,0o644))
  docs=[]
  for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
   p=private+'/progress/'+name;old=common.read(s,p)
   if s.lstat(p).st_mode&0o777!=0o600:raise ValueError('Progress mode')
   docs.append((p,old,(ROOT/'docs'/name).read_bytes(),0o600))
  folder=private+'/task-links-'+uuid.uuid4().hex;s.mkdir(folder,mode=0o700)
  for p,old,new,mode in changes+docs:
   if old is not None:common.put(s,folder+'/'+Path(p).name+'.before',old,0o600)
  common.apply_transaction(s,changes,docs,verify)
  print('TASK_LINKS_PUBLISHED_SOURCE_READBACK_AND_PRIVATE_BACKUP_VERIFIED')
  print('TASK_LINKS_TWO_PROGRESS_DOCUMENTS_SHA256_0600_VERIFIED')
 finally:t.close()
if __name__=='__main__':
 try:main()
 except Exception as e:print('TASK_LINKS_STOPPED_'+type(e).__name__+':'+str(e));raise SystemExit(1)
