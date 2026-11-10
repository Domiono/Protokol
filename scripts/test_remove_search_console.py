#!/usr/bin/env python3
"""Feature removal transaction using fake APK, source and OAuth configuration."""
import base64,hashlib,json,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def enc(b):return base64.b64encode(b).decode()
def sha(b):return hashlib.sha256(b).hexdigest()
def blob(b):return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
class RemoveTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.web=self.root/'public_html';self.web.mkdir();self.private=self.root/'rentmor-control-private';self.private.mkdir(mode=0o700);(self.private/'progress').mkdir(mode=0o700)
  self.active=self.root/'rentmor-private/search-console';self.active.mkdir(mode=0o700,parents=True);(self.active/'client.json').write_bytes(b'fixture-only-secret');(self.active/'client.json').chmod(0o600)
  baseline=json.loads((ROOT/'deployment/remove-search-console/baseline.json').read_text());self.payload={'mode':'publish','docs':{}}
  for key in ['admin_remove','admin_auth','admin_redirect','api_remove','api_tombstone']:self.payload[key]=baseline[key]
  self.admin=('<?php\n'+baseline['admin_auth']+baseline['admin_remove'][0]+'\n?>\n'+baseline['admin_remove'][1]+baseline['admin_remove'][2]+'<!-- preserve newer Mortii fixture -->').encode()
  self.api=('<?php\n'+baseline['api_remove']+'// preserve newer booking fixture\n').encode()
  (self.web/'admin.php').write_bytes(self.admin);(self.web/'protocol_sync.php').write_bytes(self.api)
  self.payload['source_before']={'admin.php':sha(self.admin),'protocol_sync.php':sha(self.api)};self.payload['helper_blobs']={}
  for name in ['search_console.php','search_console_admin.php','search_console_app.php']:
   b=('<?php // fixture '+name).encode();(self.web/name).write_bytes(b);self.payload['helper_blobs'][name]=blob(b)
  self.old=b'fake old APK';self.new=b'fake removed-feature APK';(self.web/'RentMor.apk').write_bytes(self.old);(self.web/'app-release.json').write_text(json.dumps({'version_code':45,'sha256':sha(self.old)}))
  staged='rentmor-control-private/remove-gsc-stage-'+'a'*24+'.apk';(self.root/staged).write_bytes(self.new);(self.root/staged).chmod(0o600)
  self.payload.update(staged_apk=staged,apk_before_sha256=sha(self.old),apk_sha256=sha(self.new),apk_size=len(self.new))
  for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
   p=self.private/'progress'/name;p.write_bytes(b'old fixture notes');p.chmod(0o600);self.payload['docs'][name]={'before_blob':blob(b'old fixture notes'),'content':enc(b'new fixture notes')}
 def tearDown(self):self.tmp.cleanup()
 def remote(self,payload):
  source=(ROOT/'deployment/remove-search-console/remote.php').read_text().replace('__REMOVE_GSC_PAYLOAD__',enc(json.dumps(payload).encode()))
  return subprocess.run(['php'],input=source,text=True,cwd=self.root,capture_output=True)
 def receipt(self,r):return json.loads(next(s.removeprefix('REMOVE_GSC_RESULT ')for s in r.stdout.splitlines()if s.startswith('REMOVE_GSC_RESULT ')))
 def test_remove_and_restore_are_scoped_to_feature(self):
  r=self.remote(self.payload);self.assertEqual(r.returncode,0,r.stdout+r.stderr);receipt=self.receipt(r)
  self.assertNotIn('rm_gsc_admin_', (self.web/'admin.php').read_text());self.assertIn('preserve newer Mortii fixture',(self.web/'admin.php').read_text())
  self.assertIn('410',(self.web/'protocol_sync.php').read_text());self.assertIn('preserve newer booking fixture',(self.web/'protocol_sync.php').read_text())
  self.assertFalse((self.web/'search_console.php').exists());self.assertFalse(self.active.exists())
  archived=self.root/receipt['backup']/'google-connection-disabled';self.assertEqual((archived/'client.json').read_bytes(),b'fixture-only-secret');self.assertEqual(archived.stat().st_mode&0o777,0o700)
  self.assertEqual((self.web/'RentMor.apk').read_bytes(),self.new);self.assertEqual(json.loads((self.web/'app-release.json').read_text())['version_code'],46)
  r=self.remote({'mode':'rollback','backup':receipt['backup']});self.assertEqual(r.returncode,0,r.stdout+r.stderr)
  self.assertEqual((self.web/'admin.php').read_bytes(),self.admin);self.assertEqual((self.web/'protocol_sync.php').read_bytes(),self.api);self.assertEqual((self.web/'RentMor.apk').read_bytes(),self.old);self.assertTrue((self.active/'client.json').exists())
 def test_concurrent_source_aborts_before_removal(self):
  (self.web/'admin.php').write_bytes(self.admin+b'new owner source');r=self.remote(self.payload);self.assertNotEqual(r.returncode,0);self.assertTrue(self.active.exists());self.assertEqual((self.web/'RentMor.apk').read_bytes(),self.old)
 def test_progress_only_does_not_touch_app_or_connection(self):
  self.payload['progress_only']=True;(self.web/'RentMor.apk').unlink();r=self.remote(self.payload);self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertTrue(self.active.exists());self.assertEqual(set(self.receipt(r)['sha256']),{'CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md'})
 def test_restore_refuses_concurrent_new_connection(self):
  r=self.remote(self.payload);self.assertEqual(r.returncode,0,r.stdout+r.stderr);receipt=self.receipt(r);self.active.mkdir(mode=0o700)
  r=self.remote({'mode':'rollback','backup':receipt['backup']});self.assertNotEqual(r.returncode,0);self.assertEqual((self.web/'RentMor.apk').read_bytes(),self.new)
if __name__=='__main__':unittest.main()
