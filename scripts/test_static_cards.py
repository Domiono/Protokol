import ast,struct,zlib,tempfile,unittest,sys,shutil,subprocess
from pathlib import Path
from patch_static_cards import patch, NEW, STATIC
ROOT=Path(__file__).resolve().parents[1]
class Cards(unittest.TestCase):
 def test_only_home_cards_change(self):
  tree=ast.parse((ROOT/'scripts/patch_public_gallery.py').read_text())
  blocks={n.targets[0].id:ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and isinstance(n.targets[0],ast.Name) and n.targets[0].id in ['new_card','new_detail','new_image']}
  source=blocks['new_image']+blocks['new_card']+blocks['new_detail']
  result=patch(source)
  self.assertEqual(patch(result),result)
  self.assertIn(blocks['new_detail'],result)
  self.assertIn(NEW,result);self.assertIn(STATIC,result)
  self.assertEqual(result.count('data-rm-autoplay="true"'),1)
  with self.assertRaises(ValueError):patch(source+blocks['new_card'])
  with self.assertRaises(ValueError):patch(result.replace(STATIC,'broken'))
 def test_php_render_preserves_detail_photos_and_links(self):
  if not shutil.which('php'):self.skipTest('PHP is checked in CI')
  tree=ast.parse((ROOT/'scripts/patch_public_gallery.py').read_text())
  blocks={n.targets[0].id:ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and isinstance(n.targets[0],ast.Name) and n.targets[0].id in ['new_card','new_detail','new_image']}
  fixture="<?php function e($s){return htmlspecialchars($s,ENT_QUOTES);} function product_url($p){return '/product/';} $p=['images'=>['one.jpg','two.jpg','three.jpg']]; $product=$p; $product['name']='Test'; $product_name='Test'; "
  source=fixture+blocks['new_image']+'?>'+blocks['new_card']+'<!-- detail -->'+blocks['new_detail']
  result=subprocess.run(['php'],input=patch(source),text=True,capture_output=True,check=True).stdout
  card,detail=result.split('<!-- detail -->')
  self.assertEqual(card.count('<img '),1)
  self.assertIn('/uploads/one.jpg',card)
  self.assertNotIn('two.jpg',card)
  self.assertNotIn('<button',card)
  self.assertIn('href="/product/"',card)
  self.assertEqual(detail.count('data-rm-slide'),3)
  self.assertIn('data-rm-next',detail)
  self.assertEqual(detail.count('data-rm-thumb'),3)
 def test_png_only_exif_recovery(self):
  try:
   from PIL import Image
   from png_metadata import open_image
  except ImportError:self.skipTest('Pillow not installed')
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'photo.png';Image.new('RGB',(64,64),'red').save(p)
   original=p.read_bytes();chunk=struct.pack('>I',4)+b'eXIf'+b'bad!'+b'\0'*4
   p.write_bytes(original[:33]+chunk+original[33:]);damaged=p.read_bytes()
   with open_image(p) as im:self.assertEqual(im.getpixel((0,0)),(255,0,0))
   self.assertEqual(p.read_bytes(),damaged)
   data=bytearray(damaged);idx=data.index(b'IDAT');data[idx+5]^=1;p.write_bytes(data)
   with self.assertRaises((ValueError,OSError)):open_image(p)
if __name__=='__main__':unittest.main()
