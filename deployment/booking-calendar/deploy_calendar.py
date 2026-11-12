"""Owner-authorized calendar deployment, strict baseline guards and rollback."""
import base64,hashlib,json,os,stat,subprocess,tempfile,urllib.request,urllib.parse,uuid,shlex
from pathlib import Path
import paramiko
ROOT=Path(__file__).resolve().parents[2]
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
def sha(b):return hashlib.sha256(b).hexdigest()
def read(s,p):
 m=s.lstat(p)
 if not stat.S_ISREG(m.st_mode) or m.st_size>2000000:raise ValueError('Unsafe file')
 with s.open(p,'rb') as f:return f.read(2000001)
def write(s,p,b,mode):
 with s.open(p,'wb') as f:f.write(b)
 s.chmod(p,mode)
 if read(s,p)!=b:raise RuntimeError('Readback')
def atomic_move(t,source,target):
 channel=t.open_session(timeout=30)
 channel.exec_command('mv -f -- '+shlex.quote(source)+' '+shlex.quote(target))
 if channel.recv_exit_status()!=0:raise RuntimeError('Atomic SSH rename failed')
 channel.close()
def get(path):
 req=urllib.request.Request('https://rentmor.pl/'+path,headers={'Cache-Control':'no-cache','Accept':'application/json'})
 with urllib.request.urlopen(req,timeout=40) as r:return r.read(2000001)
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  pin=base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
  if pin not in PINS:raise ValueError('Host key mismatch')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30)
  for name in ['booking_schedule.php','rental_inventory.php']:
   if read(s,'public_html/'+name)!=(ROOT/'deployment/booking-calendar/baseline'/name).read_bytes():raise ValueError('Booking rules changed')
  print('BASELINE_RULES_OK')
  changes=[]
  for name in ['booking_calendar_rules.php','booking_calendar.php','mortii.css','mortii.js']:
   try:old=read(s,'public_html/'+name)
   except FileNotFoundError:old=None
   new=(ROOT/'server'/name).read_bytes()
   if name=='mortii.js' and old!=(ROOT/'deployment/booking-calendar/baseline/mortii.js').read_bytes() and old!=new:raise ValueError('JavaScript changed')
   if name=='mortii.css' and old!=new and (old is None or sha(old)!='98dde0ba4261133cb882491b4acbc96e400fe9ddd00cbcdbba0ce2fd7ad8b402'):raise ValueError('Stylesheet changed')
   if name.startswith('booking_calendar') and old is not None and old!=new:raise ValueError('Endpoint exists')
   changes.append((name,old,new))
  for name in ['public_metadata.php','category.php','polityka-prywatnosci.php','rental_categories.php']:
   old=read(s,'public_html/'+name);text=old.decode()
   for before,after in [('mortii.js?v=6','mortii.js?v=7'),('mortii.css?v=3','mortii.css?v=4')]:
    if text.count(before)!=1:raise ValueError('Unexpected asset hook: '+name)
    text=text.replace(before,after)
   changes.append((name,old,text.encode()))
  print('PREFLIGHT_FILES_OK')
  for name,old,new in changes:
   if name.endswith(('.php','.js')):
    with tempfile.TemporaryDirectory() as tmp:
     p=Path(tmp)/name;p.write_bytes(new)
     subprocess.run(['php','-l',str(p)] if name.endswith('.php') else ['node','--check',str(p)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  private='rentmor-control-private';m=s.lstat(private)
  if not stat.S_ISDIR(m.st_mode) or m.st_mode&0o077:raise ValueError('Private backup required')
  folder=private+'/booking-calendar-'+uuid.uuid4().hex;s.mkdir(folder,mode=0o700)
  for name,old,new in changes:
   if old is not None:write(s,folder+'/'+name+'.before',old,0o600)
   write(s,folder+'/'+name+'.stage',new,0o644)
  print('BACKUP_AND_STAGE_OK')
  applied=[]
  try:
   for name,old,new in changes:
    try:current=read(s,'public_html/'+name)
    except FileNotFoundError:current=None
    if current!=old:raise ValueError('Concurrent modification')
    atomic_move(t,folder+'/'+name+'.stage','public_html/'+name);applied.append((name,old,new))
    if read(s,'public_html/'+name)!=new:raise RuntimeError('Published readback')
   print('PRODUCTION_FILES_READBACK_OK')
   for name in ['mortii.js','mortii.css']:
    if get(name+'?calendar='+uuid.uuid4().hex)!=(ROOT/'server'/name).read_bytes():raise RuntimeError('Public asset differs')
   print('PUBLIC_ASSETS_OK')
   from datetime import datetime
   from zoneinfo import ZoneInfo
   print('CHECKING_BOOTSTRAP')
   boot=json.loads(get('mortii_api.php'))
   product=boot['products'][0]['name']
   month=datetime.now(ZoneInfo('Europe/Warsaw')).strftime('%Y-%m')
   print('CHECKING_AVAILABILITY')
   data=json.loads(get('booking_calendar.php?'+urllib.parse.urlencode({'equipment':product,'month':month,'rental_days':'1'})))
   if data['month']!=month or not 28<=len(data['days'])<=31:raise RuntimeError('Availability response')
   if any(set(d)!={'date','status','slots'} for d in data['days']):raise RuntimeError('Unexpected public fields')
   print('PUBLIC_CALENDAR_OK')
   home=get('?calendar='+uuid.uuid4().hex)
   if b'mortii.js?v=7' not in home or b'mortii.css?v=4' not in home:raise RuntimeError('Homepage cache hooks')
  except Exception as failure:
   print('PUBLICATION_CHECK_FAILED_'+type(failure).__name__)
   for name,old,new in reversed(applied):
    if read(s,'public_html/'+name)!=new:raise RuntimeError('Concurrent change blocks rollback')
    if old is None:s.remove('public_html/'+name)
    else:
     write(s,folder+'/'+name+'.rollback',old,0o644)
     atomic_move(t,folder+'/'+name+'.rollback','public_html/'+name)
     if read(s,'public_html/'+name)!=old:raise RuntimeError('Rollback readback')
   print('ORIGINAL_FILES_RESTORED')
   raise RuntimeError('Publication verification failed; restored') from None
  print('CALENDAR_PUBLISHED_BACKUP_READBACK_PUBLIC_GET_OK')
  progress=private+'/progress'
  try:s.mkdir(progress,mode=0o700)
  except IOError:pass
  s.chmod(progress,0o700)
  for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
   b=(ROOT/'docs'/name).read_bytes();write(s,progress+'/'+name+'.stage',b,0o600);atomic_move(t,progress+'/'+name+'.stage',progress+'/'+name)
   if read(s,progress+'/'+name)!=b or s.lstat(progress+'/'+name).st_mode&0o777!=0o600:raise RuntimeError('Progress readback')
  print('TWO_PROGRESS_DOCUMENTS_SHA256_0600_VERIFIED')
 finally:t.close()
if __name__=='__main__':
 try:main()
 except Exception as e:print('CALENDAR_DEPLOY_STOPPED_'+type(e).__name__);raise SystemExit(1)
