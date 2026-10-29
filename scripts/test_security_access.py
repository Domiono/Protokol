"""Exercise real PHP access boundaries using disposable fixtures only."""
import hashlib, http.cookiejar, json, os, re, shutil, socket, subprocess, tempfile, time
from pathlib import Path
from urllib.request import Request, urlopen, build_opener, HTTPCookieProcessor
from urllib.error import HTTPError, URLError
from patch_private_data_access import patch, RULES
ROOT = Path(__file__).resolve().parents[1]
old = '# BEGIN RentMor private-data access v1\nold rules\n# END RentMor private-data access v1\n\nRewriteEngine On\n'
assert patch(patch(old)) == patch(old)
assert 'old rules' not in patch(old) and patch(old).endswith('RewriteEngine On\n')
patterns = [re.compile(s) for s in re.findall(r'<FilesMatch "([^"]+)"', RULES)]
for name in ['costs.json','costs.json.bak','reservations.json~','stats.json.old','rental_assets.php','rental_assets.php.abc.php','phone_events_data.php','customers.json.abc.tmp']:
    assert any(p.search(name) for p in patterns), name
for name in ['manifest.json','index.php','protocol_sync.php','image.webp']:
    assert not any(p.search(name) for p in patterns), name

with tempfile.TemporaryDirectory() as td:
    root=Path(td); shutil.copytree(ROOT/'server',root,dirs_exist_ok=True)
    devices=[]
    for role in ['admin','assistant','unknown','missing','disabled']:
        entry={'active':role!='disabled','token_hash':hashlib.sha256(role.encode()).hexdigest()}
        if role!='missing': entry['role']='admin' if role=='disabled' else role
        devices.append(entry)
    (root/'protocol_devices.json').write_text(json.dumps({'devices':devices}))
    rental={'id':'r1','phone':'500600700','equipment':'Puzzi','text':'Rental','month':'2030-01','start_date':'2030-01-01','end_date':'2030-01-02','settled':True,'income':321,'loss':12,'loss_note':'private loss','settled_at':'2030-01-02','daily_rate':80,'paid':20,'deposit':200,'settlement':{'extra':77}}
    for name,data in [('reservations',[rental]),('protocols',[]),('customers',[]),('costs',[{'id':'c','amount':999}]),('stats',{'total_views':27}),('messages',[])]:
        (root/(name+'.json')).write_text(json.dumps(data))
    (root/'rental_assets.php').write_text('<?php exit; ?>\n'+json.dumps([{'id':'a','repairs':[{'id':'fix','cost':81}]}]))
    (root/'seed.php').write_text("<?php session_start(); $_SESSION['logged_in']=true; $_SESSION['admin_authenticated_at']=time(); $_SESSION['admin_last_activity']=time()-(isset($_GET['expired'])?1900:0); echo 'ok';")
    (root/'session_probe.php').write_text("<?php require 'admin_safety.php'; rm_admin_start_session(); echo json_encode(['logged'=>!empty($_SESSION['logged_in'])]);")
    with socket.socket() as sock: sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]
    base=f'http://127.0.0.1:{port}'
    server=subprocess.Popen([os.environ.get('PHP_BINARY','php'),'-n','-d',f'session.save_path={root}','-S',f'127.0.0.1:{port}','-t',td],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    def request(role=None,op=None,header=True,path='/protocol_sync.php',client=None):
        headers={'Content-Type':'application/json'}
        if role: headers['Cookie']='rm_protocol_device='+role
        if header: headers['X-RentMor-Sync']='1'
        data=json.dumps({'operations':[dict(op,op_id='test-operation')]}).encode() if op else None
        try: response=(client.open if client else urlopen)(Request(base+path,data=data,headers=headers))
        except HTTPError as exc: response=exc
        with response: return response.status,response.headers,response.read()
    try:
        for _ in range(60):
            try: request();break
            except URLError:time.sleep(.05)
        for role in [None,'unknown','missing','disabled','forged']:
            code,_,body=request(role);assert code==401,(role,code)
            assert 'income' not in body.decode()
        code,_,body=request('admin');assert code==200
        assert json.loads(body)['reservations'][0]['income']==321
        code,_,body=request('assistant');assert code==200
        data=json.loads(body)
        assert data['expenses']==[] and data['site_stats']==[] and data['site_products']==[]
        for item in data['reservations']+data['customers'][0]['reservations']:
            for key in ['income','loss','loss_note','settled_at','daily_rate','paid','deposit','settlement']:assert key not in item,key
        assert 'cost' not in data['assets'][0]['repairs'][0]
        for view in ['messages','push_status','notebook']:
            assert request('assistant',path='/protocol_sync.php?view='+view)[0]==403
        before=(root/'reservations.json').read_bytes()
        for action in ['reservation_settle','reservation_delete','reservation_save','expense_save','expense_delete','asset_save','product_save']:
            code,_,body=request('assistant',dict(rental,action=action,income=0,loss=0,equipment='Changed'))
            assert code==200 and not json.loads(body)['results'][0]['ok'],action
        assert (root/'reservations.json').read_bytes()==before
        assert request('admin',{'action':'reservation_delete','id':'r1'},header=False)[0]==403
        assert (root/'reservations.json').read_bytes()==before
        # The authorized admin path remains available.
        code,_,body=request('admin',{'action':'reservation_settle','id':'r1','income':400,'loss':0})
        assert code==200 and json.loads(body)['results'][0]['ok']
        assert json.loads((root/'reservations.json').read_text())[0]['income']==400
        client=build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()))
        code,headers,_=request(path='/session_probe.php',client=client)
        cookie=headers.get('Set-Cookie','')
        assert 'HttpOnly' in cookie and 'SameSite=Lax' in cookie
        assert 'no-store' in headers['Cache-Control'] and headers['X-Frame-Options']=='DENY'
        assert "frame-ancestors 'none'" in headers['Content-Security-Policy']
        request(path='/seed.php',client=client)
        assert json.loads(request(path='/session_probe.php',client=client)[2])['logged']
        request(path='/seed.php?expired=1',client=client)
        assert not json.loads(request(path='/session_probe.php',client=client)[2])['logged']
    finally: server.terminate();server.wait(timeout=5)
print('PASS: authorization, private finance, denied writes, CSRF header, session expiry and private-file rules.')
