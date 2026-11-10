"""Narrow presentation-only deployment; no API, booking data or arbitrary paths."""
import argparse,base64,hashlib,json,logging,os,stat,subprocess,tempfile,urllib.request,uuid
from pathlib import Path
import paramiko
ROOT=Path(__file__).resolve().parent
ALLOWED={'mortii.js','mortii.css','public_metadata.php','category.php','rental_categories.php','polityka-prywatnosci.php'}
IMAGES={'assets/mortii-launcher-59d4e684be12.webp':'launcher.webp','assets/mortii-avatar-00398e07cbdc.webp':'avatar.webp'}
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
logging.getLogger('paramiko').addHandler(logging.NullHandler());logging.getLogger('paramiko').propagate=False

def sha(b):return hashlib.sha256(b).hexdigest()
def read(s,path):
 info=s.lstat(path)
 if not stat.S_ISREG(info.st_mode) or info.st_size>2_000_000:raise ValueError('Unsafe source')
 with s.open(path,'rb')as f:return f.read(2_000_001),info.st_mode&0o777

def write(s,path,data,mode):
 with s.open(path,'wb')as f:f.write(data)
 s.chmod(path,mode)
 if read(s,path)[0]!=data:raise RuntimeError('Readback')

def connect():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222));t.start_client(timeout=25)
 fp=base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
 if fp not in PINS:t.close();raise ValueError('Host key')
 t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
 s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30);return t,s

def restore(s,state):
 for row in reversed(state['changed']):
  current,_=read(s,'public_html/'+row['name'])
  if sha(current)!=row['after']:raise RuntimeError('Concurrent update blocks rollback')
  if row['before'] is not None:
   b,_=read(s,state['backup']+'/'+row['name']);write(s,'public_html/'+row['name'],b,row['mode'])
 print('VISUAL_FILES_RESTORED')

def main():
 p=argparse.ArgumentParser();p.add_argument('--rollback',action='store_true');a=p.parse_args()
 t,s=connect();statefile=Path(os.environ['RUNNER_TEMP'])/'mortii-visual-state.json'
 try:
  if a.rollback:restore(s,json.loads(statefile.read_text()));return
  rows=json.loads((ROOT/'manifest.json').read_text())
  if len(rows)!=len(ALLOWED) or {x['name']for x in rows}!=ALLOWED:raise ValueError('Allowlist')
  changes=[]
  for row in rows:
   name=row['name'];old,mode=read(s,'public_html/'+name)
   if sha(old)!=row['sha256']:raise ValueError('Source version differs: '+name)
   if 'source'in row:new=(ROOT/row['source']).read_bytes()
   else:
    text=old.decode()
    for before,after in row['replacements']:
     if text.count(before)!=1:raise ValueError('Ambiguous patch: '+name)
     text=text.replace(before,after)
    new=text.encode()
   with tempfile.TemporaryDirectory()as tmp:
    path=Path(tmp)/name;path.write_bytes(new)
    cmd=['php','-l',str(path)]if name.endswith('.php')else['node','--check',str(path)]if name.endswith('.js')else None
    if cmd:subprocess.run(cmd,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
   changes.append({'name':name,'old':old,'new':new,'mode':0o644 if name.endswith(('.js','.css'))else mode})
  for name,source in IMAGES.items():
   try:old,mode=read(s,'public_html/'+name)
   except FileNotFoundError:old,mode=None,0o644
   new=(ROOT/source).read_bytes()
   if old is not None and old!=new:raise ValueError('Image collision')
   changes.insert(0,{'name':name,'old':old,'new':new,'mode':0o644})
  # Metadata alone: confirm why old decorator failed; never log its contents.
  for name in ['rentmor-ai-mortii-visual.js','rentmor-ai-mortii-visual.css']:
   try:print('OLD_DECORATOR_MODE',name,oct(s.lstat('public_html/'+name).st_mode&0o777))
   except FileNotFoundError:print('OLD_DECORATOR_MISSING',name)
  parent='rentmor-control-private';info=s.lstat(parent)
  if not stat.S_ISDIR(info.st_mode)or info.st_mode&0o077:raise ValueError('Private backup required')
  backup=parent+'/mortii-visual-'+uuid.uuid4().hex;s.mkdir(backup,mode=0o700);s.mkdir(backup+'/assets',mode=0o700)
  for row in changes:
   if row['old']is not None:write(s,backup+'/'+row['name'],row['old'],0o600)
  state={'backup':backup,'changed':[]}
  attempted=[]
  try:
   for row in changes:
    path='public_html/'+row['name']
    try:current,_=read(s,path)
    except FileNotFoundError:current=None
    if current!=row['old']:raise ValueError('Concurrent update')
    entry={'name':row['name'],'before':sha(row['old'])if row['old']is not None else None,'after':sha(row['new']),'mode':row['mode']}
    # Track original before writing, so partial writes can also be recovered.
    attempted.append(row)
    with s.open(path,'wb')as f:f.write(row['new'])
    s.chmod(path,row['mode'])
    state['changed'].append(entry);statefile.write_text(json.dumps(state))
    if read(s,path)[0]!=row['new']:raise RuntimeError('Written data differs')
   for row in changes:
    if read(s,'public_html/'+row['name'])[0]!=row['new']:raise RuntimeError('Final readback')
  except Exception:
   # Restore all originals already written, including a partly overwritten current file.
   for row in reversed(attempted):
    if row['old']is not None:write(s,'public_html/'+row['name'],row['old'],row['mode'])
   raise
  for row in changes:
   if row['name'].endswith(('.js','.css','.webp')):
    with urllib.request.urlopen('https://rentmor.pl/'+row['name']+'?v=character-20261110',timeout=30)as r:
     if r.read(2_000_001)!=row['new']:raise RuntimeError('Public resource differs')
  print('VISUAL_DEPLOY_BACKUP_READBACK_AND_PUBLIC_RESOURCES_OK')
 finally:s.close();t.close()

if __name__=='__main__':
 try:main()
 except Exception as e:
  # Only fixed local validation messages may be emitted; third-party failures redacted.
  print('VISUAL_DEPLOY_STOPPED_'+type(e).__name__);raise SystemExit(1)
