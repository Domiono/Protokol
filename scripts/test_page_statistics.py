#!/usr/bin/env python3
"""Exercise actual standalone-page rendering, consent, tokens and confirmation."""
import tempfile, shutil, subprocess, socket, time, http.client, re, json
from pathlib import Path
from urllib.parse import urlencode
ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as tmp:
    root=Path(tmp)
    for name in ['pageview.php','browser_stats.php','traffic_filter.php','page_statistics.php','public_metadata.php']:
        shutil.copy(ROOT/'server'/name,root/name)
    for name,body in [('guide.php','<script src="/online.js?v=1" defer></script>'),('terms.php','')]:
        (root/name).write_text("<?php require 'page_statistics.php'; rm_stats_public_page('/"+name+"'); ?><html><body>Public page"+body+"</body></html>")
    (root/'missing.php').write_text("<?php require 'page_statistics.php'; rm_stats_public_page('/missing.php'); http_response_code(404); ?><html><body>Not found</body></html>")
    # Simulates a future article: only the existing shared template is included.
    (root/'poradnik-nowy-przyszly-sprzet.php').write_text("<?php require 'public_metadata.php'; ob_start('rentmor_public_metadata'); ?><html><head><title>Nowy poradnik</title></head><body>New article</body></html>")
    (root/'admin-test.php').write_text("<?php require 'public_metadata.php'; ?><html><body>Not a public guide</body></html>")
    sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close()
    proc=subprocess.Popen(['php','-d',f'session.save_path={root}','-S',f'127.0.0.1:{port}','-t',tmp],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    def request(path, cookie='', consent='allow', token=None):
        conn=http.client.HTTPConnection('127.0.0.1',port,timeout=10)
        headers={'User-Agent':'Mozilla/5.0 Chrome/130 Safari/537.36','Cookie':cookie+'; rm_stats_consent='+consent,'Content-Type':'application/x-www-form-urlencoded'}
        conn.request('POST' if token else 'GET',path,urlencode({'token':token}) if token else None,headers)
        response=conn.getresponse();body=response.read().decode();session=response.getheader('Set-Cookie','').split(';')[0];code=response.status;conn.close()
        return code,body,session
    try:
        for _ in range(100):
            try:request('/guide.php',consent='deny');break
            except OSError:time.sleep(.05)
        assert 'data-view-token' not in request('/guide.php',consent='deny')[1]
        assert 'data-view-token' not in request('/missing.php')[1]
        cookie=''
        for name in ['guide.php','terms.php']:
            code,html,newcookie=request('/'+name+'?secret=never-store',cookie)
            cookie=newcookie or cookie
            assert code==200 and html.count('src="/online.js')==1
            token=re.search(r'data-view-token="([a-f0-9]{48})"',html).group(1)
            assert request('/pageview.php',cookie,token=token)[0]==200
        assert 'data-view-token' not in request('/admin-test.php',cookie)[1]
        code,html,newcookie=request('/poradnik-nowy-przyszly-sprzet.php?utm_source=test',cookie)
        assert code==200 and html.count('data-view-token=')==1
        token=re.search(r'data-view-token="([a-f0-9]{48})"',html).group(1)
        assert request('/pageview.php',cookie,token=token)[0]==200
        data=json.loads((root/'stats.json').read_text())
        assert data['total_views']==3 and data['unique_visitors']==1
        assert data['page_views']=={'/guide.php':1,'/terms.php':1,'/poradnik-nowy-przyszly-sprzet.php':1}
        assert 'never-store' not in json.dumps(data)
        print('PASS: standalone pages, distinct counters, reused script, missing-script injection, consent and 404 exclusion')
    finally:proc.terminate();proc.wait()
