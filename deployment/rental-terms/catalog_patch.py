"""Change only approved public catalogue descriptions and rental terms strings."""
import json,re
PERIOD='Doba wynajmu trwa 24 godziny od ustalonej godziny odbioru, np. od 16:00 do 16:00 następnego dnia.'
STRINGS=re.compile(r'"(?:[^"\\]|\\.)*"')
def replace_string(raw,old,new):
 if old==new:return raw
 matches=[m for m in STRINGS.finditer(raw) if json.loads(m.group())==old]
 if len(matches)!=1:raise ValueError('Expected unique public JSON string')
 m=matches[0];return raw[:m.start()]+json.dumps(new,ensure_ascii=False)+raw[m.end():]
def catalog(raw):
 text=raw.decode('utf-8');items=json.loads(text)
 if not isinstance(items,list):raise ValueError('Catalogue shape')
 grinders=[p for p in items if 'szlifierka do betonu' in str(p.get('name','')).lower() and '250' in str(p.get('name',''))]
 sprays=[p for p in items if str(p.get('id',''))=='69385f95de01c' and 'wt20i' in str(p.get('name','')).lower()]
 if len(grinders)!=1 or len(sprays)!=1:raise ValueError('Catalogue targets changed')
 for p in grinders:
  desc=p['desc'];new=desc
  old='40 zł za zużycie 0,5 mm.';desired='40 zł za każde rozpoczęte 0,5 mm zużycia tarczy do betonu.'
  if old in new:
   if new.count(old)!=1:raise ValueError('Ambiguous wear charge')
   new=new.replace(old,desired)
  elif desired not in new:raise ValueError('Wear description changed')
  text=replace_string(text,desc,new)
 for p in sprays:
  desc=p['desc'];new=desc
  old='Obsługuje wszystkie farby wodne';desired='Obsługuje wszystkie farby wodnorozpuszczalne'
  if old in new:new=new.replace(old,desired)
  elif desired not in new:raise ValueError('Paint wording changed')
  if 'Nie nadaje się do farb strukturalnych.' not in new:raise ValueError('Structural exclusion missing')
  text=replace_string(text,desc,new)
 result=text.encode('utf-8');updated=json.loads(result)
 # No product fields other than these two descriptions may change.
 allowed={str(p['id']) for p in grinders+sprays}
 for a,b in zip(items,updated):
  a=dict(a);b=dict(b)
  if str(a.get('id')) in allowed:a.pop('desc');b.pop('desc')
  if a!=b:raise ValueError('Unexpected catalogue mutation')
 return result
def terms(raw):
 text=raw.decode('utf-8');data=json.loads(text)
 if not isinstance(data,dict) or not isinstance(data.get('content'),str):raise ValueError('Terms shape')
 old=data['content'];new=old
 pairs=[
 ('Rezerwacji można dokonać telefonicznie lub przez dostępne kanały kontaktu.','Rezerwacji można dokonać telefonicznie lub przez formularz rezerwacji przy wybranym sprzęcie. Wysłanie formularza jest zgłoszeniem; rezerwacja wymaga potwierdzenia przez RentMor.'),
 ('Odbiór i zwrot sprzętu odbywają się osobiście, w tym samym, wcześniej uzgodnionym miejscu w Krakowie.','Odbiór i zwrot sprzętu odbywają się osobiście, w tym samym, wcześniej uzgodnionym miejscu w Krakowie. Nie świadczymy usługi dowozu — transport organizuje Najemca.'),
 ('Cena oraz okres najmu są ustalane przed wydaniem sprzętu i wpisywane do umowy.','Cena oraz okres najmu są ustalane przed wydaniem sprzętu i wpisywane do umowy. '+PERIOD)]
 for before,after in pairs:
  if after in new:continue
  if new.count(before)!=1:raise ValueError('Terms changed; manual review required')
  new=new.replace(before,after)
 return replace_string(text,old,new).encode('utf-8')
