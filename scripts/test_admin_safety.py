"""Exercise actual admin requests against disposable data, never the live site."""
import concurrent.futures
import http.cookiejar
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.request
import urllib.error
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]
PHP = os.environ.get('PHP_BINARY', 'php')
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args): return None

with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    shutil.copytree(ROOT/'server', root, dirs_exist_ok=True)
    (root/'seed.php').write_text("<?php session_start(); $_SESSION['logged_in']=true; $_SESSION['admin_csrf']='test-token'; $_SESSION['reservation_csrf']='booking-token'; echo 'ok';")
    (root/'data.json').write_text('[{"id":"p1","name":"Puzzi","phone_clicks":12,"images":[]}]')
    (root/'reservations.json').write_text('[]')
    (root/'rental_assets.php').write_text('<?php exit; ?>\n'+json.dumps([{'id':'a','equipment':'Puzzi','label':'Puzzi 1'}]))
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
    log = open(root/'server.log', 'w+')
    server = subprocess.Popen([PHP,'-n','-d','post_max_size=20M','-d','upload_max_filesize=16M','-d',f'session.save_path={root}','-S',f'127.0.0.1:{port}','-t',str(root)],stdout=log,stderr=log,cwd=root)
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
    base = f'http://127.0.0.1:{port}'
    def request(path, data=None):
        try:
            response = client.open(base+path, data=urllib.parse.urlencode(data).encode() if data is not None else None)
        except urllib.error.HTTPError as error: response = error
        return response.code, response.read().decode()
    def post(**data): return request('/admin.php',data)[0]
    try:
        for _ in range(50):
            try:
                assert request('/seed.php')[0] == 200; break
            except urllib.error.URLError: time.sleep(.05)
        else: raise AssertionError('Test server did not start')
        for action in ['save_terms','add','update','delete','delete_msg','reset_phones']:
            assert post(action=action,id='p1') == 403, action
            assert post(action=action,id='p1',admin_csrf='wrong') == 403, action
        assert request('/admin.php?reset_phones=1')[0] == 405
        assert json.loads((root/'data.json').read_text())[0]['phone_clicks'] == 12
        assert post(action='reset_phones',admin_csrf='test-token') == 302
        assert json.loads((root/'data.json').read_text())[0]['phone_clicks'] == 0
        booking = dict(action='reservation_add',reservation_csrf='booking-token',equipment='Puzzi',start_date='2030-02-01',end_date='2030-02-02',time='10:00')
        for invalid in [dict(start_date='2030-02-31',end_date='2030-03-02'),dict(time='29:00'),dict(end_date='2030-01-31')]:
            assert post(**dict(booking,**invalid)) == 302
            assert json.loads((root/'reservations.json').read_text()) == []
        assert post(**booking) == 302
        saved = json.loads((root/'reservations.json').read_text()); assert len(saved)==1 and saved[0]['asset_id']=='a'
        assert post(**booking) == 302
        assert len(json.loads((root/'reservations.json').read_text())) == 1
        assert post(**dict(booking,start_date='2030-02-03',end_date='2030-02-04')) == 302
        saved = json.loads((root/'reservations.json').read_text()); assert len(saved)==2
        before = (root/'reservations.json').read_text()
        assert post(**dict(booking,action='reservation_update',reservation_id=saved[1]['id'])) == 302
        assert (root/'reservations.json').read_text() == before, 'Edit must not bypass collisions'
        # A legacy unassigned booking also cannot be issued over occupied dates.
        saved.append(dict(saved[0],id='legacy',asset_id='')); (root/'reservations.json').write_text(json.dumps(saved))
        before=(root/'reservations.json').read_text()
        assert post(action='reservation_toggle',reservation_csrf='booking-token',reservation_id='legacy',reservation_done='1') == 302
        assert (root/'reservations.json').read_text()==before
        # Valid catalogue mutation is accepted; corrupt inventory is not silently reset.
        assert post(action='add',admin_csrf='test-token',name='Drabina',price='40') == 302
        assert len(json.loads((root/'data.json').read_text())) == 2
        # Server-side validation is mandatory even if browser fields are bypassed.
        before = (root/'data.json').read_text()
        for wrong in ['-10', '1e3', 'NaN', '1000000', '10.123', '0']:
            assert post(action='add',admin_csrf='test-token',name='Bad',price=wrong) == 422, wrong
            assert (root/'data.json').read_text() == before
        for wrong in ['-10', 'NaN', '1000000', '0.001']:
            assert post(action='add',admin_csrf='test-token',name='Bad',price='40',deposit=wrong) == 422, wrong
            assert (root/'data.json').read_text() == before
        assert post(action='add',admin_csrf='test-token',name='Comma amount',price='49,50',deposit='200,25') == 302
        data = json.loads((root/'data.json').read_text())
        assert data[0]['price'] == '49.5' and data[0]['deposit'] == '200.25'
        def upload(name, content, mime='image/png'):
            boundary = 'test-boundary-12345'
            fields = dict(action='add',admin_csrf='test-token',name='Upload trial',price='40')
            parts = []
            for key, value in fields.items():
                parts.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="' + key + '"\r\n\r\n' + value + '\r\n').encode())
            parts.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="photos[]"; filename="' + name + '"\r\nContent-Type: ' + mime + '\r\n\r\n').encode()+content+b'\r\n')
            parts.append(('--' + boundary + '--\r\n').encode())
            body = b''.join(parts)
            req = urllib.request.Request(base + '/admin.php', data=body,headers={'Content-Type':'multipart/form-data; boundary='+boundary})
            try: response = client.open(req)
            except urllib.error.HTTPError as err: response=err
            return response.code
        import base64
        png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/I/wAAAAASUVORK5CYII=')
        before = (root/'data.json').read_text()
        assert upload('danger.jpg',b'<?php echo 1; ?>','image/jpeg') == 422
        assert upload('too-large.png',b'X'*(8*1024*1024+1)) == 422
        assert (root/'data.json').read_text() == before
        assert upload('photo.png',png) == 302
        saved = json.loads((root/'data.json').read_text())
        assert len(saved[0]['images']) == 1
        assert (root/'uploads'/saved[0]['images'][0]).is_file()
        # Failed JSON replacement must never redirect with a success message.
        (root/'terms.json').mkdir()
        assert post(action='save_terms',admin_csrf='test-token',terms_content='Test') == 503
        (root/'terms.json').rmdir()
        # Throttling survives a new browser session: it uses REMOTE_ADDR, not cookies.
        for _ in range(4): assert post(login_pass='wrong-password') == 200
        assert post(login_pass='wrong-password') == 429
        assert post(login_pass='wrong-password') == 429
        second_client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
        try: second_client.open(base+'/admin.php',data=urllib.parse.urlencode({'login_pass':'wrong-password'}).encode())
        except urllib.error.HTTPError as err: assert err.code == 429
        else: raise AssertionError('New session bypassed login throttle')
        (root/'data.json').write_text('{broken')
        assert post(action='add',admin_csrf='test-token',name='Test') == 503
        assert (root/'data.json').read_text() == '{broken'
        # Independent processes must serialize read/modify/write, not only their writes.
        (root/'counter.json').write_text('[0]')
        worker = root/'worker.php'
        worker.write_text("<?php require __DIR__.'/admin_safety.php'; for($i=0;$i<30;$i++){$f=rm_admin_inventory_open(__DIR__.'/counter.json');$d=rm_admin_inventory_read($f);usleep(1000);$d[0]++;rm_admin_inventory_write($f,$d);flock($f,LOCK_UN);fclose($f);}")
        workers=[subprocess.Popen([PHP,'-n',str(worker)]) for _ in range(4)]
        assert all(p.wait()==0 for p in workers)
        assert json.loads((root/'counter.json').read_text()) == [120]
        log.flush(); log.seek(0); assert 'Fatal error' not in log.read()
        print('Admin CSRF, date/time, collision, corrupt-data and concurrent-write tests passed.')
    except Exception:
        log.flush(); log.seek(0); print(log.read())
        raise
    finally:
        server.terminate(); server.wait(); log.close()
