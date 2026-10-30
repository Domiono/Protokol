import ast,hashlib,json,shutil,subprocess,tempfile,unittest
from pathlib import Path
from patch_mobile_rendering import patch
from prepare_rentmor_images import convert,candidates
ROOT=Path(__file__).resolve().parents[1]
class MobileRendering(unittest.TestCase):
 def test_css_exact_and_patch_idempotent(self):
  tree=ast.parse((ROOT/'scripts/patch_public_gallery.py').read_text())
  blocks={n.targets[0].id:ast.literal_eval(n.value) for n in tree.body if isinstance(n,ast.Assign) and isinstance(n.targets[0],ast.Name) and n.targets[0].id in ['new_card','new_detail']}
  css='/*'+'x'*9000+'*/.flex{display:flex}';digest=hashlib.sha256(css.encode()).hexdigest()[:12]
  gallery=(ROOT/'server/rentmor-gallery.css').read_text()
  head=f'''<head><link rel="stylesheet" href="/assets/rentmor-tailwind-{digest}.css"><link rel="stylesheet" href="/rentmor-gallery.css?v=1"></head>'''
  source="<?php require_once __DIR__ . '/performance_images.php'; ?>"+head+blocks['new_card']+blocks['new_detail']
  result=patch(source,css,gallery)
  self.assertEqual(patch(result,css,gallery),result)
  self.assertIn('<style data-rm-critical="1">'+css+'\n'+gallery+'</style>',result)
  self.assertEqual(result.count('media="print"'),2)
  for role in ['card','detail','thumb']:self.assertEqual(result.count('data-rm-image-kind="'+role+'"'),1)
  self.assertIn('/performance_images_v2.php',result)
  # Contents remain usable even if JS is disabled: no dependency on onload.
  self.assertLess(result.index('.flex{display:flex}'),result.index('media="print"'))
  with self.assertRaises(ValueError):patch(source,css+' ',gallery)
  with self.assertRaises(ValueError):patch(source,css+'</style>',gallery)
 def test_php_correct_role_sizes_and_originals(self):
  if not shutil.which('php'):self.skipTest('PHP runs in CI')
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);shutil.copy(ROOT/'server/performance_images_v2.php',root/'performance_images_v2.php')
   a={'width':1000,'height':2000,**{k:{'url':f'/uploads/rentmor-webp/photo-{k}.webp','width':w} for k,w in [('thumb',160),('small',320),('card',640),('detail',1000)]}}
   (root/'rentmor-image-manifest.json').write_text(json.dumps({'photo.jpg':a}))
   for role in ['card','thumb','detail']:
    html=f'<img data-rm-image-kind="{role}" src="/uploads/photo.jpg" alt="Test" loading="lazy">'
    php='require '+json.dumps(str(root/'performance_images_v2.php'))+';echo rentmor_optimize_public_html('+json.dumps(html)+');'
    out=subprocess.run(['php','-r',php],check=True,text=True,capture_output=True).stdout
    self.assertIn('src="/uploads/photo.jpg"',out)
    self.assertIn('width="1000" height="2000"',out)
    self.assertIn('160w',out);self.assertIn('320w',out);self.assertIn('640w',out);self.assertIn('1000w',out)
    if role=='card':self.assertIn('96px',out);self.assertIn('108px',out)
    if role=='thumb':self.assertIn('sizes="78px"',out)
    if role=='detail':self.assertIn('100vw, 900px',out)
 def test_wordmark_skips_historical_webp_srcset(self):
  if not shutil.which('php'):self.skipTest('PHP runs in CI')
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);shutil.copy(ROOT/'server/performance_images_v2.php',root/'performance_images_v2.php')
   legacy={'width':520,'height':110,'small':{'url':'/assets/rentmor-logo-old-small.webp','width':260},
           'detail':{'url':'/assets/rentmor-logo-old-detail.webp','width':520}}
   photo={'width':800,'height':500,'card':{'url':'/uploads/rentmor-webp/photo-card.webp','width':640}}
   (root/'rentmor-image-manifest.json').write_text(json.dumps({'rentmor-logo.png':legacy,'photo.jpg':photo}))
   icon='<img src="/rentmor-logo.png?v=rm-20261030-wordmark-v2" alt="RentMor.pl">'
   html='<nav>'+icon+'</nav><img src="/uploads/photo.jpg">'
   code='require '+json.dumps(str(root/'performance_images_v2.php'))+';echo rentmor_optimize_public_html('+json.dumps(html)+');'
   out=subprocess.run(['php','-r',code],check=True,text=True,capture_output=True).stdout
   self.assertIn(icon,out)
   self.assertNotIn('rentmor-logo-old',out)
   self.assertIn('/uploads/rentmor-webp/photo-card.webp',out)
 def test_new_small_variants_preserve_published_assets(self):
  try:from PIL import Image
  except ImportError:self.skipTest('Pillow required')
  with tempfile.TemporaryDirectory() as d:
   p=Path(d);(p/'src').mkdir()
   Image.new('RGB',(1200,1800),'blue').save(p/'src/photo.jpg',quality=96)
   old={'photo.jpg':{'width':1200,'height':1800,'card':{'width':640,'url':'/uploads/rentmor-webp/existing-card.webp'}}}
   (p/'old.json').write_text(json.dumps(old));(p/'data.json').write_text(json.dumps([{'images':['photo.jpg']}]))
   original=(p/'src/photo.jpg').read_bytes()
   convert(p/'data.json',p/'old.json',p/'src',p/'out',p/'new.json',p/'upload.txt')
   result=json.loads((p/'new.json').read_text())
   self.assertEqual(result['photo.jpg']['card'],old['photo.jpg']['card'])
   self.assertEqual(result['photo.jpg']['small']['width'],320)
   self.assertEqual(result['photo.jpg']['thumb']['width'],160)
   self.assertEqual(candidates(['photo.jpg'],result),[])
   self.assertEqual((p/'src/photo.jpg').read_bytes(),original)
if __name__=='__main__':unittest.main()
