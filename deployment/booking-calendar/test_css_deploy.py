import base64,hashlib,importlib.util,io,os,stat,tempfile,types,unittest,sys
from pathlib import Path
from unittest.mock import patch
try:import paramiko
except ModuleNotFoundError:sys.modules['paramiko']=types.SimpleNamespace(Transport=None,SFTPClient=types.SimpleNamespace(from_transport=None))
spec=importlib.util.spec_from_file_location('deploy_css',Path(__file__).with_name('deploy_css.py'))
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
class SFTP:
 def __init__(self,old):self.files={'public_html/mortii.css':old};self.modes={'public_html/mortii.css':0o644,'rentmor-control-private':stat.S_IFDIR|0o700};self.writes=0
 def lstat(self,p):
  if p not in self.modes:raise FileNotFoundError()
  mode=self.modes[p];return types.SimpleNamespace(st_mode=mode if stat.S_ISDIR(mode) else stat.S_IFREG|mode,st_size=len(self.files.get(p,b'')))
 def open(self,p,mode):
  s=self
  class Stream(io.BytesIO):
   def close(stream):
    if 'w' in mode:s.files[p]=stream.getvalue();s.modes[p]=0o600;s.writes+=1
    super().close()
  return Stream(self.files.get(p,b''))
 def chmod(self,p,mode):self.modes[p]=(stat.S_IFDIR if stat.S_ISDIR(self.modes[p]) else 0)|mode
 def mkdir(self,p,mode):
  if p in self.modes:raise IOError()
  self.modes[p]=stat.S_IFDIR|mode
 def posix_rename(self,a,b):self.files[b]=self.files.pop(a);self.modes[b]=self.modes.pop(a)
 def get_channel(self):return types.SimpleNamespace(settimeout=lambda _:None)
class Transport:
 def __init__(self,*a):pass
 def start_client(self,**k):pass
 def get_remote_server_key(self):return types.SimpleNamespace(asbytes=lambda:b'fixture-key')
 def auth_password(self,*a,**k):pass
 def close(self):pass
class Tests(unittest.TestCase):
 def run_case(self,old=b'old',health=True):
  folder=tempfile.TemporaryDirectory();root=Path(folder.name);(root/'server').mkdir();(root/'docs').mkdir()
  candidate=b'.mortii-booking textarea{border:1px solid}'
  (root/'server/mortii.css').write_bytes(candidate)
  for n in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:(root/'docs'/n).write_bytes(b'fixture progress')
  s=SFTP(old);real_digest=lambda b:hashlib.sha256(b).hexdigest()
  class Response:
   status=200
   def read(self,*a):return candidate if health else b'wrong bytes'
   def __enter__(self):return self
   def __exit__(self,*a):pass
  pin=base64.b64encode(hashlib.sha256(b'fixture-key').digest()).decode().rstrip('=')
  with patch.object(mod,'__file__',str(root/'deployment/booking-calendar/deploy_css.py')),patch.object(mod,'PINS',{pin}),patch.object(mod,'digest',lambda b:mod.BEFORE if b==b'old' else real_digest(b)),patch.object(mod.paramiko,'Transport',Transport),patch.object(mod.paramiko.SFTPClient,'from_transport',lambda _:s),patch.object(mod.urllib.request,'urlopen',lambda *a,**k:Response()),patch.dict(os.environ,{'FTP_SERVER':'fixture','FTP_USERNAME':'fixture','FTP_PASSWORD':'fixture'}):
   error=None
   try:mod.main()
   except Exception as e:error=e
  folder.cleanup();return s,candidate,error
 def test_publish_and_private_progress(self):
  s,c,e=self.run_case();self.assertIsNone(e);self.assertEqual(s.files['public_html/mortii.css'],c)
  for n in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:self.assertEqual(s.modes['rentmor-control-private/progress/'+n],0o600)
 def test_public_mismatch_rolls_back(self):
  s,c,e=self.run_case(health=False);self.assertIsInstance(e,RuntimeError);self.assertEqual(s.files['public_html/mortii.css'],b'old')
 def test_changed_production_not_written(self):
  s,c,e=self.run_case(old=b'changed');self.assertIsInstance(e,ValueError);self.assertEqual(s.writes,0)
if __name__=='__main__':unittest.main()
