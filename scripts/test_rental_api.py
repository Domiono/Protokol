import tempfile,shutil,json,hashlib,subprocess,urllib.request,time,pathlib,os
ROOT=pathlib.Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as td:
 p=pathlib.Path(td);shutil.copy(ROOT/'server/protocol_sync.php',p/'protocol_sync.php')
 shutil.copy(ROOT/'server/phone_events.php',p/'phone_events.php')
 (p/'phone_events_data.php').write_text('<?php exit; ?>\n'+json.dumps({'at':'2026-10-28 09:00:00','action':'call','source_id':'home','source':'Strona główna'})+'\n')
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
  assert len(call()['site_stats']['phone_events'])==1
  assert call(role='assistant')['site_stats']==[]
  asset={'action':'asset_save','id':'a','label':'Puzzi 01','equipment':'Puzzi','in_service':False}
  assert call(asset)['results'][0]['ok']
  # Old bookings may be linked to the same physical unit under a former offer title.
  assert call({'action':'asset_save','id':'legacy-asset','label':'Agregat WT20 #2',
      'equipment':'Wabrotech WT20','in_service':False})['results'][0]['ok']
  legacy={'id':'legacy-rent','month':'2030-10','text':'legacy',
      'equipment':'Agregat Malarski Wabrotech wt20i','asset_id':'legacy-asset',
      'start_date':'2030-10-26','end_date':'2030-10-27','time':'10:00','done':True}
  (p/'reservations.json').write_text(json.dumps([legacy]),encoding='utf-8')
  edited=dict(legacy,action='reservation_save',time='13:10')
  assert call(edited)['results'][0]['ok']
  assert next(x for x in call()['reservations'] if x['id']=='legacy-rent')['time']=='13:10'
  assert not call(dict(edited,equipment='Wabrotech WT20i'))['results'][0]['ok']
  assert not call(dict(edited,id='fake-new'))['results'][0]['ok']
  # A different valid booking cannot take the same copy over its occupied dates.
  assert not call(dict(edited,id='other',equipment='Wabrotech WT20'))['results'][0]['ok']
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
  # Named extras can be created without adding another product category or double-counting.
  named={'days':2,'extra':'45','add_powder':'10','add_paper':'0',
         'add_disc':'0','add_other':'0',
         'add_custom':[{'name':'Worek do odkurzacza','amount':'20'},
                       {'name':'Nowy filtr','amount':'15'}]}
  bad_named=dict(named,extra='40')
  settlement={'action':'reservation_settle','id':'auto1','income':'125','loss':'0',
              'settlement':bad_named,'actual_return':'2030-10-27'}
  assert not call(settlement)['results'][0]['ok']  # Wrong total.
  assert not next(x for x in call()['reservations'] if x['id']=='auto1').get('settled',False)
  bad_empty=dict(named,add_custom=[{'name':'  ','amount':'20'}])
  assert not call(dict(settlement,settlement=bad_empty))['results'][0]['ok']
  bad_negative=dict(named,add_custom=[{'name':'Filtr','amount':'-1'}])
  assert not call(dict(settlement,settlement=bad_negative))['results'][0]['ok']
  bad_long=dict(named,add_custom=[{'name':'a'*101,'amount':'35'}])
  assert not call(dict(settlement,settlement=bad_long))['results'][0]['ok']
  bad_many=dict(named,add_custom=[{'name':'Pozycja '+str(i),'amount':1} for i in range(21)])
  assert not call(dict(settlement,settlement=bad_many))['results'][0]['ok']
  assert call(dict(settlement,settlement=named))['results'][0]['ok']
  stored=next(x for x in call()['reservations'] if x['id']=='auto1')
  assert stored['settled'] and len(stored['settlement']['add_custom'])==2
  assert stored['settlement']['add_custom'][0]['name']=='Worek do odkurzacza'
  assert stored['settlement']['add_custom'][1]['amount']==15
  assert float(stored['settlement']['extra'])==45
  # Three physical giraffes and three industrial vacuums are one common inventory,
  # regardless of whether the offer is a set, standalone Bosch, or standalone Metabo.
  for i in range(1,4):
   assert call({'action':'asset_save','id':'g'+str(i),'equipment':'Szlifierka żyrafa Stanley',
      'label':'Żyrafa '+str(i),'asset_kind':'giraffe'})['results'][0]['ok']
   assert call({'action':'asset_save','id':'v'+str(i),'equipment':'Odkurzacz przemysłowy Bosch',
      'label':'Odkurzacz '+str(i),'asset_kind':'vacuum'})['results'][0]['ok']
  def booking(id,eq,needs=None):
   op={'action':'reservation_save','auto_assign':True,'id':id,'month':'2030-12',
       'text':'test '+id,'equipment':eq,'start_date':'2030-12-01','end_date':'2030-12-03'}
   if needs is not None:op['needs_vacuum']=needs
   return op
  assert call(booking('vac-alone','Odkurzacz przemysłowy Metabo'))['results'][0]['ok']
  solo=next(x for x in call()['reservations'] if x['id']=='vac-alone')
  assert solo['asset_id']=='v1'
  assert call(booking('combo-one','Żyrafa + odkurzacz'))['results'][0]['ok']
  combo=next(x for x in call()['reservations'] if x['id']=='combo-one')
  assert combo['asset_id']=='g1' and combo['vacuum_asset_id']=='v2'
  assert call(booking('combo-two','Żyrafa Stanley',True))['results'][0]['ok']
  combo2=next(x for x in call()['reservations'] if x['id']=='combo-two')
  assert combo2['asset_id']=='g2' and combo2['vacuum_asset_id']=='v3'
  assert not call(booking('combo-three','Żyrafa + odkurzacz'))['results'][0]['ok']
  assert call(booking('giraffe-alone','Żyrafa Stanley',False))['results'][0]['ok']
  alone=next(x for x in call()['reservations'] if x['id']=='giraffe-alone')
  assert alone['asset_id']=='g3' and not alone.get('vacuum_asset_id')
  # Explicit false does not bypass an offer actually named as a kit.
  assert not call(booking('bypass','Żyrafa + odkurzacz',False))['results'][0]['ok']
  assert not call(dict(booking('giraffe-direct','Żyrafa Stanley'),asset_id='g1',auto_assign=False))['results'][0]['ok']
  assert call({'action':'reservation_settle','id':'vac-alone','income':80,'loss':0,
     'settlement':{'days':2},'actual_return':'2030-12-03'})['results'][0]['ok']
  assert call({'action':'reservation_settle','id':'giraffe-alone','income':80,'loss':0,
     'settlement':{'days':2},'actual_return':'2030-12-03'})['results'][0]['ok']
  assert call(booking('combo-three','Żyrafa + odkurzacz'))['results'][0]['ok']
  third=next(x for x in call()['reservations'] if x['id']=='combo-three')
  assert third['vacuum_asset_id']=='v1'
  assert not call(dict(booking('vac-busy','Odkurzacz przemysłowy Bosch'),asset_id='v2',auto_assign=False))['results'][0]['ok']
  # Quick repair toggle persists and prevents new bookings on that physical unit.
  assert call(dict(second_asset,in_service=True))['results'][0]['ok']
  assert next(a for a in call()['assets'] if a['id']=='b')['in_service']
  assert not call(dict(auto_r,id='maintenance-check',start_date='2030-11-01',end_date='2030-11-02'))['results'][0]['ok']
  assert call(dict(second_asset,in_service=False))['results'][0]['ok']
  assert not next(a for a in call()['assets'] if a['id']=='b')['in_service']
  # Unused copies may be deleted; established bookings, including old settlements
  # and a vacuum used as the secondary item of a kit, must remain traceable.
  fresh={'action':'asset_save','id':'unused-copy','equipment':'Puzzi','label':'Puzzi Spare'}
  assert call(fresh)['results'][0]['ok']
  assert not call({'action':'asset_delete','id':'unused-copy'},role='assistant')['results'][0]['ok']
  assert call({'action':'asset_delete','id':'unused-copy'})['results'][0]['ok']
  assert not any(a['id']=='unused-copy' for a in call()['assets'])
  assert call({'action':'asset_delete','id':'unused-copy'})['results'][0]['ok'] # Idempotent on reconnect.
  assert not call({'action':'asset_delete','id':'a'})['results'][0]['ok'] # Already settled r1.
  assert any(a['id']=='a' for a in call()['assets'])
  assert not call({'action':'asset_delete','id':'v2'})['results'][0]['ok'] # Kit secondary.
  repaired={'action':'asset_save','id':'service-copy','equipment':'Puzzi','label':'Puzzi Repaired',
    'repairs':[{'id':'repair-1','date':'2026-10-25','cost':10,'note':'Filtr'}]}
  assert call(repaired)['results'][0]['ok']
  assert not call({'action':'asset_delete','id':'service-copy'})['results'][0]['ok']
  assert any(a['id']=='service-copy' for a in call()['assets'])
  try:urllib.request.urlopen('http://127.0.0.1:18723/rental_assets.php');raise AssertionError('asset storage publicly readable')
  except urllib.error.HTTPError as e:assert e.code==404
  print('Rental API bundle and standalone shared-stock checks passed (isolated test data)')
 finally:server.terminate();server.wait()
