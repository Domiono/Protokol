#!/usr/bin/env python3
"""Run the SSH/PHP transaction locally against disposable source-only fixtures."""
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from patch_search_console import HOOKS,patch

ROOT=Path(__file__).resolve().parents[1]
def encode(b):return base64.b64encode(b).decode()
def blob(b):return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()

class DeployTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
        self.web=self.root/'public_html';self.web.mkdir()
        self.private=self.root/'rentmor-control-private';self.private.mkdir(mode=0o700)
        (self.private/'progress').mkdir(mode=0o700)
        self.admin=(ROOT/'server/admin.php').read_text()
        for _,insertion,_ in HOOKS:self.admin=self.admin.replace(insertion,'')
        self.admin+='\n<?php /* newer Mortii integration fixture must survive */ ?>\n'
        (self.web/'admin.php').write_text(self.admin)
        self.payload={'mode':'publish','hooks':HOOKS,'helpers':{},'docs':{},'admin_before_sha':hashlib.sha256(self.admin.encode()).hexdigest()}
        for name in ['search_console.php','search_console_admin.php']:
            self.payload['helpers'][name]=encode((ROOT/'server'/name).read_bytes())
        for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
            old=b'Original fixture notes\n';p=self.private/'progress'/name;p.write_bytes(old);p.chmod(0o600)
            self.payload['docs'][name]={'before_blob':blob(old),'content':encode(b'Updated fixture notes\n')}
    def tearDown(self):self.tmp.cleanup()
    def run_remote(self,payload):
        source=(ROOT/'deployment/search-console/remote.php').read_text().replace('__GSC_PAYLOAD__',encode(json.dumps(payload).encode()))
        return subprocess.run(['php'],input=source,text=True,cwd=self.root,capture_output=True)
    def test_publish_and_rollback(self):
        r=self.run_remote(self.payload);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        result=json.loads(next(l.removeprefix('GSC_SSH_RESULT ')for l in r.stdout.splitlines()if l.startswith('GSC_SSH_RESULT ')))
        self.assertEqual((self.web/'admin.php').read_text(),patch(self.admin))
        for name in self.payload['helpers']:self.assertEqual((self.web/name).stat().st_mode&0o777,0o644)
        for name in self.payload['docs']:
            self.assertEqual((self.private/'progress'/name).read_bytes(),b'Updated fixture notes\n')
            self.assertEqual((self.private/'progress'/name).stat().st_mode&0o777,0o600)
        r=self.run_remote({'mode':'rollback','backup':result['backup']});self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertEqual((self.web/'admin.php').read_text(),self.admin)
        self.assertFalse((self.web/'search_console.php').exists())
    def test_refuse_newer_notes(self):
        (self.private/'progress'/'CONTINUE-RENTMOR.md').write_bytes(b'Concurrent owner notes\n')
        r=self.run_remote(self.payload);self.assertNotEqual(r.returncode,0)
        self.assertIn('progress-baseline',r.stdout)
        self.assertEqual((self.web/'admin.php').read_text(),self.admin)
        self.assertFalse((self.web/'search_console.php').exists())
    def test_refuse_changed_panel(self):
        (self.web/'admin.php').write_text(self.admin+'newer source')
        r=self.run_remote(self.payload);self.assertNotEqual(r.returncode,0)
        self.assertIn('source-preflight',r.stdout)
        self.assertFalse((self.web/'search_console.php').exists())
    def test_rollback_refuses_concurrent_change(self):
        r=self.run_remote(self.payload);self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        result=json.loads(next(l.removeprefix('GSC_SSH_RESULT ')for l in r.stdout.splitlines()if l.startswith('GSC_SSH_RESULT ')))
        (self.web/'admin.php').write_text('Owner newer source')
        r=self.run_remote({'mode':'rollback','backup':result['backup']})
        self.assertNotEqual(r.returncode,0)
        self.assertEqual((self.web/'admin.php').read_text(),'Owner newer source')

if __name__=='__main__':unittest.main()
