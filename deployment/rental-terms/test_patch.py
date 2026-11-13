import json,unittest
from catalog_patch import catalog,terms,PERIOD
class PatchTests(unittest.TestCase):
 def test_catalog_is_narrow_and_idempotent(self):
  items=[{'id':'g','name':'Szlifierka do betonu i posadzek 250mm','price':130,'desc':'Dodatki. 40 zł za zużycie 0,5 mm.'},{'id':'69385f95de01c','name':'Wabrotech WT20i','desc':'Obsługuje wszystkie farby wodne (lateksowe).\nNie nadaje się do farb strukturalnych.'},{'id':'other','desc':'Pozostaw ten tekst','price':80}]
  raw=json.dumps(items,ensure_ascii=True,indent=4).encode();changed=catalog(raw)
  self.assertEqual(catalog(changed),changed)
  out=json.loads(changed);self.assertEqual(out[2],items[2]);self.assertEqual(out[0]['price'],130)
  self.assertIn('każde rozpoczęte 0,5 mm',out[0]['desc'])
 def test_unknown_catalog_aborts(self):
  with self.assertRaises(ValueError):catalog(b'[]')
 def test_terms_keep_other_fields(self):
  data={'content':'Rezerwacji można dokonać telefonicznie lub przez dostępne kanały kontaktu. Odbiór i zwrot sprzętu odbywają się osobiście, w tym samym, wcześniej uzgodnionym miejscu w Krakowie. Cena oraz okres najmu są ustalane przed wydaniem sprzętu i wpisywane do umowy.','other':{'keep':True}}
  out=terms(json.dumps(data).encode());self.assertEqual(terms(out),out);self.assertEqual(json.loads(out)['other'],data['other']);self.assertIn(PERIOD,json.loads(out)['content'])
if __name__=='__main__':unittest.main()
