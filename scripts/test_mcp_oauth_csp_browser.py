#!/usr/bin/env python3
"""Chromium regression: signed owner form POST -> 302 to ChatGPT callback."""
from __future__ import annotations
import base64, hashlib, json, os, shutil, socket, subprocess, sys, tempfile, time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from playwright.sync_api import sync_playwright

PASSWORD='SyntheticOnlyOwnerPassword#2026'
CALLBACK='https://chatgpt.com/connector_platform_oauth_redirect'

def http(url, payload=None, headers=None):
    if isinstance(payload,dict):
        payload=urlencode(payload).encode()
    req=Request(url,data=payload,headers=headers or {},method='POST' if payload is not None else 'GET')
    try: resp=urlopen(req,timeout=6)
    except HTTPError as e: resp=e
    return resp.status,dict(resp.headers),resp.read()

def scenario(browser, core_source, oauth_source, change_source, expected_redirect):
    with tempfile.TemporaryDirectory(prefix='rm-csp-browser-') as temp:
        root=Path(temp); pub=root/'public_html'/'rentmor-control-mcp'; private=root/'rentmor-control-private'
        pub.mkdir(parents=True);private.mkdir()
        shutil.copyfile(core_source,pub/'oauth_core.php')
        shutil.copyfile(oauth_source,pub/'oauth.php')
        shutil.copyfile(change_source,pub/'change-password.php')
        pw_hash=subprocess.check_output(
            ['php','-r','echo password_hash($argv[1], PASSWORD_DEFAULT);',PASSWORD],text=True)
        (private/'owner.json').write_text(json.dumps({'password_hash':pw_hash}))
        (private/'state.json').write_text(json.dumps({'pair_until':int(time.time()+300)}))
        with socket.socket() as sock:
            sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
        base=f'http://127.0.0.1:{port}/rentmor-control-mcp'
        env=os.environ|{'RM_MCP_TEST_BASE_URL':base}
        proc=subprocess.Popen(['php','-S',f'127.0.0.1:{port}','-t',str(root/'public_html')],
                               env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:
            for _ in range(50):
                try: status,_,_=http(base+'/oauth.php?op=metadata');break
                except URLError:time.sleep(.1)
            assert status==200, 'Synthetic OAuth server failed to start'
            payload=json.dumps({'redirect_uris':[CALLBACK],'token_endpoint_auth_method':'none'}).encode()
            status,_,raw=http(base+'/oauth.php?op=register',payload,{'Content-Type':'application/json'})
            assert status==201, ('OAuth registration',status)
            client=json.loads(raw)['client_id']
            verifier='V'*43
            challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b'=').decode()
            params={'response_type':'code','client_id':client,'redirect_uri':CALLBACK,
                    'code_challenge':challenge,'code_challenge_method':'S256','scope':'source:read',
                    'state':'TEST_BROWSER_STATE_NO_SECRETS','resource':base+'/index.php'}
            auth_url=base+'/oauth.php?op=authorize&'+urlencode(params)
            status,headers,_=http(auth_url)
            assert status==200,'OAuth form GET rejected'
            csp=headers.get('Content-Security-Policy','')
            assert "form-action 'self'" in csp
            assert ('https://chatgpt.com' in csp)==expected_redirect,('CSP callback',csp)
            if expected_redirect:
                status,other,_=http(base+'/change-password.php')
                assert status==200
                assert "https://chatgpt.com" not in other.get('Content-Security-Policy',''), 'Other forms must remain self-only'
            page=browser.new_page()
            callback_seen=[]
            csp_violations=[]
            def intercept(route):
                callback_seen.append(True)
                route.fulfill(status=200,content_type='text/html',body='<html><body>Fake ChatGPT callback reached</body></html>')
            page.route('https://chatgpt.com/**',intercept)
            page.on('console', lambda msg: csp_violations.append(True) if 'form-action' in msg.text else None)
            page.goto(auth_url)
            page.locator('input[name="password"]').fill(PASSWORD)
            page.locator('button[type="submit"]').click()
            page.wait_for_timeout(1450)
            redirect=bool(callback_seen) and page.url.startswith(CALLBACK)
            assert redirect==expected_redirect,('Unexpected OAuth browser navigation',redirect,expected_redirect, bool(csp_violations))
            state=json.loads((private/'state.json').read_text())
            assert len(state.get('codes',{}))==1, 'OAuth code should have been issued even if browser blocked redirect'
            if not expected_redirect:
                assert csp_violations, 'Original CSP did not visibly block cross-origin 302'
            print('PASS: browser '+('follows approved ChatGPT redirect' if expected_redirect else 'reproduces silent CSP redirect block'))
            page.close()
        finally:
            proc.terminate()
            try: proc.communicate(timeout=3)
            except subprocess.TimeoutExpired:
                proc.kill();proc.communicate()

if __name__=='__main__':
    if len(sys.argv)!=5:raise SystemExit('usage: test_mcp_oauth_csp_browser.py original-core patched-core oauth.php change-password.php')
    with sync_playwright() as playwright:
        binary=shutil.which('google-chrome') or shutil.which('chromium')
        args=['--no-sandbox']
        browser=playwright.chromium.launch(headless=True,executable_path=binary,args=args)
        try:
            scenario(browser,*map(Path,(sys.argv[1],sys.argv[3],sys.argv[4])),False)
            scenario(browser,*map(Path,(sys.argv[2],sys.argv[3],sys.argv[4])),True)
        finally:
            browser.close()
    print('PASS: Chromium OAuth CSP regression and self-only password-change form verified on isolated test state.')
