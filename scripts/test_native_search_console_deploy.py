#!/usr/bin/env python3
"""Exercise release transaction with fake APK bytes, source and notes only."""
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[1]
def enc(b):return base64.b64encode(b).decode()
def sha(b):return hashlib.sha256(b).hexdigest()
def blob(b):return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()

class NativeDeployTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.web=self.root/'public_html';self.web.mkdir()
        self.private=self.root/'rentmor-control-private';self.private.mkdir(mode=0o700);(self.private/'progress').mkdir(mode=0o700)
        self.old=b'old fake apk';self.new=b'new fake apk';(self.web/'RentMor.apk').write_bytes(self.old)
        (self.web/'app-release.json').write_text(json.dumps({'version_code':44,'sha256':sha(self.old)}))
        self.protocol=b"<?php\nif (($_GET['view'] ?? '') === 'ai_knowledge') { /* existing fixture integration */ }\n"
        (self.web/'protocol_sync.php').write_bytes(self.protocol);core=b'<?php // fixture shared core';(self.web/'search_console.php').write_bytes(core)
        self.staged='rentmor-control-private/native-gsc-stage-'+'a'*24+'.apk';(self.root/self.staged).write_bytes(self.new);(self.root/self.staged).chmod(0o600)
        helper=(ROOT/'server/search_console_app.php').read_bytes()
        self.payload={'mode':'publish','docs':{},'protocol_before_blob':blob(self.protocol),'core_sha256':sha(core),'helper':enc(helper),
            'hook':["if (($_GET['view'] ?? '') === 'ai_knowledge') {","if (($_GET['view'] ?? '') === 'search_console') { rm_gsc_app_handle($role,$method); }\n"],
            'staged_apk':self.staged,'apk_before_sha256':sha(self.old),'apk_sha256':sha(self.new),'apk_size':len(self.new)}
        for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
            p=self.private/'progress'/name;p.write_bytes(b'old notes');p.chmod(0o600);self.payload['docs'][name]={'before_blob':blob(b'old notes'),'content':enc(b'new notes')}
    def tearDown(self):self.tmp.cleanup()
    def remote(self,payload):
        source=(ROOT/'deployment/android-search-console/remote.php').read_text().replace('__NATIVE_GSC_PAYLOAD__',enc(json.dumps(payload).encode()))
        return subprocess.run(['php'],input=source,text=True,cwd=self.root,capture_output=True)
    def receipt(self,result):return json.loads(next(s.removeprefix('NATIVE_GSC_RESULT ')for s in result.stdout.splitlines()if s.startswith('NATIVE_GSC_RESULT ')))
    def test_release_and_rollback_preserve_all_previous_files(self):
        r=self.remote(self.payload);self.assertEqual(r.returncode,0,r.stdout+r.stderr);receipt=self.receipt(r)
        self.assertEqual((self.web/'RentMor.apk').read_bytes(),self.new)
        self.assertEqual(json.loads((self.web/'app-release.json').read_text())['version_code'],45)
        self.assertIn('existing fixture integration',(self.web/'protocol_sync.php').read_text())
        self.assertIn('rm_gsc_app_handle',(self.web/'protocol_sync.php').read_text())
        self.assertEqual((self.web/'search_console_app.php').stat().st_mode&0o777,0o644)
        self.assertEqual((self.private/'progress'/'CONTINUE-RENTMOR.md').read_bytes(),b'new notes')
        self.assertFalse((self.root/self.staged).exists())
        r=self.remote({'mode':'rollback','backup':receipt['backup']});self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertEqual((self.web/'RentMor.apk').read_bytes(),self.old);self.assertEqual((self.web/'protocol_sync.php').read_bytes(),self.protocol)
        self.assertFalse((self.web/'search_console_app.php').exists())
    def test_newer_api_aborts_before_writes(self):
        (self.web/'protocol_sync.php').write_bytes(self.protocol+b'newer owner change')
        r=self.remote(self.payload);self.assertNotEqual(r.returncode,0);self.assertIn('protocol-baseline',r.stdout)
        self.assertEqual((self.web/'RentMor.apk').read_bytes(),self.old);self.assertFalse((self.web/'search_console_app.php').exists())
    def test_newer_apk_aborts_before_source_writes(self):
        (self.web/'RentMor.apk').write_bytes(b'owner newer APK')
        r=self.remote(self.payload);self.assertNotEqual(r.returncode,0);self.assertIn('apk-preflight',r.stdout)
        self.assertFalse((self.web/'search_console_app.php').exists())
    def test_progress_only_never_reads_apk_or_protocol(self):
        (self.web/'RentMor.apk').unlink();(self.web/'protocol_sync.php').unlink();self.payload['progress_only']=True
        r=self.remote(self.payload);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertEqual(set(self.receipt(r)['sha256']),{'CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md'})

if __name__=='__main__':unittest.main()
