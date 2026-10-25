import tempfile,shutil,json,hashlib,subprocess,urllib.request,time,pathlib,os
ROOT=pathlib.Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as td:
 p=pathlib.Path(td);shutil.copy(ROOT/'server/protocol_sync.php',p/'protocol_sync.php')
 (p/'protocol_devices.json').write_text(json.dumps({'devices':[{'active':True,'token_hash':hashlib.sha256(b'test-admin').hexdigest(),'role':'admin'},{'active':True,'token_hash':hashlib.sha256(b'test-assistant').hexdigest(),'role':'assistant'}]}))
 server=subprocess.Popen([os.environ.get('PHP_BINARY','php'),'-n','-S','127.0.0.1:18723','-t',td],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 try:
  time.sleep(.3)
  seq=0
  def call(op=None,role='admin'):
   global seq
   seq+=1
   if op:op=dict(op,op_id='op'+str(seq))
   req=urllib.request.Request('http://127.0.0.1:18723/protocol_sync.php',data=json.dumps({'operations':[op]}).encode() if op else None,headers={'Cookie':'rm_protocol_device=test-'+role,'Content-Type':'application/json','X-RentMor-Sync':'1'})
   return json.load(urllib.request.urlopen(req))
  asset={'action':'asset_save','id':'a','label':'Puzzi 01','equipment':'Puzzi','in_service':False}
  assert call(asset)['results'][0]['ok']
  r={'action':'reservation_save','id':'r1','month':'2030-10','text':'test rental','equipment':'Puzzi','asset_id':'a','start_date':'2030-10-25','end_date':'2030-10-27','daily_rate':'80','deposit':'200','paid':'40'}
  assert call(r)['results'][0]['ok']
  assert not call(dict(r,id='r2'))['results'][0]['ok']
  assert call(dict(r,id='r2',start_date='2030-10-28',end_date='2030-10-29'))['results'][0]['ok']
  second_asset={'action':'asset_save','id':'b','label':'Puzzi 02','equipment':'Puzzi','in_service':False}
  assert call(second_asset)['results'][0]['ok']
  auto_r={'action':'reservation_save','auto_assign':True,'id':'auto1','month':'2030-10',
          'text':'autobook','equipment':'Puzzi','start_date':'2030-10-26','end_date':'2030-10-27'}
  assert call(auto_r)['results'][0]['ok']
  assigned=next(x for x in call()['reservations'] if x['id']=='auto1')
  assert assigned['asset_id']=='b' and assigned['asset_label']=='Puzzi 02'
  assert not call(dict(auto_r,id='auto2'))['results'][0]['ok']
  data=call(role='assistant');assert all('daily_rate' not in x and 'deposit' not in x for x in data['reservations'])
  assert not call(asset,role='assistant')['results'][0]['ok']
  assert call(dict(asset,in_service=True))['results'][0]['ok']
  assert not call(dict(r,id='r3',start_date='2030-11-01',end_date='2030-11-02'))['results'][0]['ok']
  breakdown={'days':2,'extra':'30','add_powder':'10','add_paper':'5','add_disc':'12','add_other':'3','deposit_refund':'200'}
  bad=dict(breakdown,add_disc='14')
  assert not call({'action':'reservation_settle','id':'r1','income':'190','loss':'0','settlement':bad,'actual_return':'2030-10-27'})['results'][0]['ok']
  assert not next(x for x in call()['reservations'] if x['id']=='r1').get('settled',False)
  assert call({'action':'reservation_settle','id':'r1','income':'190','loss':'0','settlement':breakdown,'actual_return':'2030-10-27'})['results'][0]['ok']
  saved=next(x for x in call()['reservations'] if x['id']=='r1')
  assert saved['returned'] and saved['settlement']['add_powder']==10 and saved['settlement']['add_disc']==12
  assert call({'action':'reservation_settle','id':'r2','income':'80','loss':'0','settlement':{'days':1,'extra':'10'},'actual_return':'2030-10-29'})['results'][0]['ok']
  assert next(x for x in call()['reservations'] if x['id']=='r2')['settlement']['extra']=='10'
  try:urllib.request.urlopen('http://127.0.0.1:18723/rental_assets.php');raise AssertionError('asset storage publicly readable')
  except urllib.error.HTTPError as e:assert e.code==404
  print('Addon breakdown and legacy settlement API integration checks passed (isolated test data)')
 finally:server.terminate();server.wait()
