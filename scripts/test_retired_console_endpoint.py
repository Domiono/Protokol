#!/usr/bin/env python3
"""Old clients must receive a small retired response, never the normal sync data."""
import hashlib,json,shutil,socket,subprocess,tempfile,time,urllib.request,urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory()as temp:
 root=Path(temp)/'public_html';root.mkdir()
 for name in ['protocol_sync.php','private_rental_files.php','rental_inventory.php','admin_auth.php','offer_content.php','rental_offer_details.php','rental_offer_details_extra.php']:shutil.copy(ROOT/'server'/name,root/name)
 (root/'protocol_devices.json').write_text(json.dumps({'devices':[{'active':True,'token_hash':hashlib.sha256(('fixture-'+role).encode()).hexdigest(),'role':role}for role in ['admin','assistant']]}))
 with socket.socket()as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
 server=subprocess.Popen(['php','-n','-S',f'127.0.0.1:{port}','-t',str(root)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 try:
  time.sleep(.3);count=0
  for role,method,header,status in [('admin','GET',False,410),('assistant','GET',False,410),('admin','POST',True,410),('admin','POST',False,403),('inactive','GET',False,401)]:
   headers={'Cookie':'rm_protocol_device=fixture-'+role,'Content-Type':'application/json'}
   if header:headers['X-RentMor-Sync']='1'
   request=urllib.request.Request(f'http://127.0.0.1:{port}/protocol_sync.php?view=search_console',headers=headers,data=b'{}'if method=='POST'else None)
   try:urllib.request.urlopen(request,timeout=5);raise AssertionError('Old endpoint succeeded')
   except urllib.error.HTTPError as e:
    body=json.loads(e.read());assert e.code==status,(e.code,status);assert body.get('ok')is False;assert set(body)<= {'ok','error'};count+=1
  print('PASS:',count,'retired endpoint authorization and no-data HTTP checks')
 finally:server.terminate();server.wait(timeout=5)

