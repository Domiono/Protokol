#!/usr/bin/env python3
"""Regression: live PHP auth wins over older SFTP snapshots; no production credentials."""
import hashlib
import http.server
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT=Path(__file__).resolve().parents[1]
TEMPLATE=(ROOT/'scripts/admin_auth_runtime_installer.php.template').read_text()
TOKEN='only-local-regression-test-token'
def bcrypt(value):
    return subprocess.check_output(
        ['php','-r','echo password_hash($argv[1],PASSWORD_BCRYPT);',value],
        text=True
    ).strip()
def req(url,body,authorized=True):
    headers={'Content-Type':'application/json'}
    if authorized: headers['X-RentMor-Probe']=TOKEN
    request=Request(url,data=json.dumps(body).encode(),headers=headers,method='POST')
    try: reply=urlopen(request,timeout=5)
    except HTTPError as e: reply=e
    with reply: return reply.status,reply.read()

with tempfile.TemporaryDirectory() as tmp:
    root=Path(tmp)
    public=root/'public_html'
    private=root/'rentmor-private'
    public.mkdir();private.mkdir()
    (public/'probe.php').write_text(
        TEMPLATE.replace('__AUTH_HASH__',hashlib.sha256(TOKEN.encode()).hexdigest())
    )
    path=private/'admin-security.json'
    current={'password_hash':bcrypt('new-admin-credential'),'totp_secret':'A'*32}
    stale={'password_hash':bcrypt('old-sftp-snapshot'),'totp_secret':'B'*32}
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0))
        port=sock.getsockname()[1]
    server=subprocess.Popen(
        ['php','-S',f'127.0.0.1:{port}','-t',str(public)],
        stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL
    )
    url=f'http://127.0.0.1:{port}/probe.php'
    try:
        for _ in range(75):
            try:
                status,_=req(url,stale,authorized=False)
                if status==404:break
            except URLError:
                time.sleep(.03)
        else:raise AssertionError('PHP fixture server did not start')
        path.write_text(json.dumps(current))
        code,body=req(url,stale)
        assert code==200 and json.loads(body)['ok']
        assert json.loads(path.read_text())==current,'Stale SFTP snapshot replaced valid credentials'
        code,body=req(url,{})
        assert code==200 and json.loads(path.read_text())==current
        path.write_text('{}')
        code,_=req(url,stale)
        assert code==503 and path.read_text()=='{}','Invalid existing secrets overwritten'
        path.unlink()
        code,body=req(url,stale)
        assert code==200 and json.loads(body)['ok']
        assert json.loads(path.read_text())==stale,'Initial bootstrap failed'
        path.unlink()
        code,_=req(url,{})
        assert code==503 and not path.exists(),'Missing credentials accepted'
    finally:
        server.terminate()
        server.wait(timeout=5)
print('PASS: existing PHP auth preserved, stale snapshot ignored, corrupt auth rejected, bootstrapping validated')
