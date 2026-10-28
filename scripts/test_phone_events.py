#!/usr/bin/env python3
"""Exercise the real HTTP collector against an isolated temporary dataset."""
import concurrent.futures
import http.client
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
from urllib.parse import urlencode

ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as directory:
    root=Path(directory)
    for name in ['phone_event.php','phone_events.php','traffic_filter.php']:
        shutil.copy(ROOT/'server'/name,root/name)
    (root/'data.json').write_text(json.dumps([{'id':'p1','name':'Kärcher Puzzi'}]))
    (root/'read.php').write_text("<?php require 'phone_events.php'; echo json_encode(rm_phone_events_read());")
    sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close()
    server=subprocess.Popen(['php','-d',f'session.save_path={root}','-S',f'127.0.0.1:{port}','-t',str(root)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    def request(path='/phone_event.php',method='POST',fields=None,extra=None):
        connection=http.client.HTTPConnection('127.0.0.1',port,timeout=10)
        headers={'User-Agent':'Mozilla/5.0','Content-Type':'application/x-www-form-urlencoded','Origin':'https://rentmor.pl'}
        headers.update(extra or {})
        connection.request(method,path,urlencode(fields or {'source_id':'home','action':'call'}),headers)
        response=connection.getresponse();status=response.status;body=response.read();connection.close();return status,body
    try:
        for _ in range(100):
            try: request(method='GET');break
            except OSError: time.sleep(.05)
        assert request(method='GET')[0]==405
        assert request(fields={'source_id':'missing','action':'call'})[0]==400
        assert request(fields={'source_id':'home','action':'copy'})[0]==400
        assert request(extra={'Origin':'https://evil.example'})[0]==403
        assert request(extra={'Cookie':'rm_stats_exclude=1'})[0]==200
        assert request(extra={'User-Agent':'Googlebot'})[0]==200
        assert json.loads(request('/read.php','GET')[1])==[]
        assert request()[0]==200
        assert request(fields={'source_id':'p1','action':'reveal'})[0]==200
        # Each separate ordinary click is retained, including rapid repeats.
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            assert all(code==200 for code,_ in pool.map(lambda _:request(),range(20)))
        events=json.loads(request('/read.php','GET')[1]);assert len(events)==22
        assert events[0]['source']=='Strona główna'
        assert events[1]['source']=='Kärcher Puzzi' and events[1]['action']=='reveal'
        assert all(set(e)=={'at','source_id','source','action'} for e in events)
        assert request('/phone_events_data.php','GET')[0]==404
        assert request('/phone_events_data.php','GET')[1]==b''
        print('PASS: validation, owner/bot exclusion, separate clicks, product attribution, private history')
    finally:
        server.terminate();server.wait()
