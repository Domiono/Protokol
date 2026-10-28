#!/usr/bin/env python3
import json,subprocess,tempfile,shutil,socket,time,http.client
from pathlib import Path
from urllib.parse import urlencode
ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as tmp:
    root=Path(tmp)
    for name in ['pageview.php','browser_stats.php','traffic_filter.php']:
        shutil.copy(ROOT/'server'/name,root/name)
    (root/'page.php').write_text("<?php session_start(); require 'browser_stats.php'; header('Content-Type: application/json'); echo json_encode(['token'=>rm_stats_prepare_pageview((string)($_GET['product']??''),'direct')]);")
    original={'total_views':100,'unique_visitors':40,'total_phone_clicks':9,'days':{'2026-01-01':10},'product_views':{'p1':2}}
    (root/'stats.json').write_text(json.dumps(original))
    s=socket.socket();s.bind(('127.0.0.1',0));port=s.getsockname()[1];s.close()
    server=subprocess.Popen(['php','-d',f'session.save_path={root}','-S',f'127.0.0.1:{port}','-t',tmp],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    def request(path,method='GET',token='',cookie='',headers=None):
        c=http.client.HTTPConnection('127.0.0.1',port,timeout=10)
        h={'User-Agent':'Mozilla/5.0 Chrome/130 Safari/537.36','Cookie':cookie,'Content-Type':'application/x-www-form-urlencoded'};h.update(headers or {})
        c.request(method,path,urlencode({'token':token}) if method=='POST' else None,h);r=c.getresponse();status=r.status;session=r.getheader('Set-Cookie','').split(';')[0];body=r.read();c.close()
        return status,json.loads(body),session
    def count():return json.loads((root/'stats.json').read_text())
    try:
        for _ in range(100):
            try:request('/page.php');break
            except OSError:time.sleep(.05)
        _,page,cookie=request('/page.php');assert page['token'];assert count()==original, 'plain browser-looking HTTP request counted'
        assert request('/pageview.php','POST','a'*48,cookie)[0]==400
        assert request('/pageview.php','POST',page['token'])[0]==400,'token accepted without its session'
        assert request('/pageview.php','POST',page['token'],cookie,{'Origin':'https://evil.example'})[0]==403
        assert count()==original
        assert request('/pageview.php','POST',page['token'],cookie)[0]==200
        assert count()['total_views']==101 and count()['unique_visitors']==41
        assert request('/pageview.php','POST',page['token'],cookie)[0]==400
        _,page,_=request('/page.php?utm_source=other',cookie=cookie)
        assert request('/pageview.php','POST',page['token'],cookie)[0]==200
        assert count()['total_views']==101,'rapid reload/UTM change counted'
        _,page,_=request('/page.php?product=p1',cookie=cookie)
        assert request('/pageview.php','POST',page['token'],cookie)[0]==200
        assert count()['total_views']==102 and count()['unique_visitors']==41 and count()['product_views']['p1']==3
        for headers in [{'User-Agent':'Googlebot'},{'Cookie':'rm_stats_exclude=1'}]:
            assert request('/page.php',headers=headers)[1]['token']==''
        assert request('/page.php',headers={'X-Requested-With':'XMLHttpRequest'})[1]['token']==''
        assert request('/page.php',headers={'Sec-Purpose':'prefetch'})[1]['token']==''
        assert count()['total_phone_clicks']==9 and count()['days']['2026-01-01']==10
        assert 'browser_filter_since' in count()
        # Damaged statistics must never silently reset historical totals.
        _,page,cookie=request('/page.php?product=p2');(root/'stats.json').write_text('broken-json')
        assert request('/pageview.php','POST',page['token'],cookie)[0]==503
        assert (root/'stats.json').read_text()=='broken-json'
        print('PASS: plain GET never counts; session-bound browser confirmation; replay, reload, owner/bot and cross-site exclusion; historic totals preserved')
    finally:server.terminate();server.wait()
