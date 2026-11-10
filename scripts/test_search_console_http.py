#!/usr/bin/env python3
"""Exercise controller HTTP boundaries on an isolated built-in PHP server."""
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import unittest
import urllib.request
import urllib.error
import urllib.parse
import http.cookiejar

class HTTPTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory()
        cls.web=Path(cls.tmp.name)/'web';cls.web.mkdir()
        root=Path(__file__).resolve().parents[1]
        for name in ['search_console.php','search_console_admin.php']:
            shutil.copy(root/'server'/name,cls.web/name)
        (cls.web/'fixture.php').write_text('''<?php
session_start();
$_SESSION['gsc_csrf']='test-csrf';
require __DIR__.'/search_console_admin.php';
rm_gsc_admin_handle(($_GET['authorized']??'')==='yes');
rm_gsc_admin_render(($_GET['authorized']??'')==='yes',[['id'=>'1','name'=>'Fixture equipment']]);
''')
        with socket.socket()as sock:
            sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        cls.base=f'http://127.0.0.1:{port}/'
        cls.process=subprocess.Popen(['php','-S',f'127.0.0.1:{port}','-t',str(cls.web)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        for _ in range(60):
            try:urllib.request.urlopen(cls.base+'search_console.php',timeout=1)
            except urllib.error.HTTPError:break
            except OSError:time.sleep(.05)
        else:raise RuntimeError('PHP test server unavailable')

    @classmethod
    def tearDownClass(cls):
        cls.process.terminate();cls.process.wait(timeout=5);cls.tmp.cleanup()

    def request(self,path,data=None):
        try:
            with urllib.request.urlopen(self.base+path,data=urllib.parse.urlencode(data).encode()if data is not None else None,timeout=5)as r:
                return r.status,r.read().decode()
        except urllib.error.HTTPError as e:return e.code,e.read().decode()

    def test_direct_helpers(self):
        for name in ['search_console.php','search_console_admin.php']:
            self.assertEqual(self.request(name),(404,''))
    def test_unauthorized_mutations(self):
        private=Path(self.tmp.name)/'rentmor-private'
        before=sorted(p.relative_to(private).as_posix()for p in private.rglob('*'))if private.exists()else []
        for action in ['configure','connect','refresh','disconnect']:
            status,body=self.request('fixture.php',{'gsc_action':action,'gsc_csrf':'test-csrf'})
            self.assertEqual(status,403)
        after=sorted(p.relative_to(private).as_posix()for p in private.rglob('*'))if private.exists()else []
        self.assertEqual(after,before)
    def test_csrf_and_callback(self):
        for path,data in [('fixture.php?authorized=yes',{'gsc_action':'refresh','gsc_csrf':'wrong'}),('fixture.php?gsc_callback=1',None)]:
            self.assertEqual(self.request(path,data)[0],403)
    def test_first_setup_no_fake_data(self):
        status,body=self.request('fixture.php?authorized=yes')
        self.assertEqual(status,200)
        self.assertIn('Brak pobranego raportu',body)
        self.assertIn('Pierwsze połączenie',body)
        self.assertNotIn('fake-token',body)

if __name__=='__main__':unittest.main()
