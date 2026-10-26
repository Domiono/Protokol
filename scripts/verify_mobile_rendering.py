"""Require exact compiled critical CSS and responsive image roles on live pages."""
from urllib.request import Request,urlopen
from html.parser import HTMLParser
import re,hashlib
class Page(HTMLParser):
 def __init__(self):super().__init__();self.links=[];self.images=[]
 def handle_starttag(self,t,a):
  if t=='link':self.links.append(dict(a))
  if t=='img':self.images.append(dict(a))
def get(path):
 with urlopen(Request('https://rentmor.pl'+path,headers={'Cache-Control':'no-cache','User-Agent':'RentMorMobileRenderingCheck/1.0'}),timeout=35) as r:
  assert r.status==200;return r.read().decode()
def main():
 for path in ['/','/szlifierka-do-betonu-i-posadzek-250mm-wynajem-krakow/']:
  html=get(path+'?mobile_render_check=1');p=Page();p.feed(html)
  css=[a for a in p.links if re.fullmatch('/assets/rentmor-tailwind-[a-f0-9]{12}.css',a.get('href',''))]
  assert len(css)==1 and css[0].get('media')=='print'
  text=get(css[0]['href'])
  assert '<style data-rm-critical="1">'+text+'\n' in html
  assert hashlib.sha256(text.encode()).hexdigest()[:12] in css[0]['href']
  if path=='/':
   photos=[a for a in p.images if a.get('data-rm-image-kind')=='card'];assert len(photos)>=20
   assert all('min(' in a.get('sizes','') and a.get('srcset') for a in photos)
   assert 'data-rm-next' not in html
  else:
   assert any(a.get('data-rm-image-kind')=='detail' for a in p.images)
   assert any(a.get('data-rm-image-kind')=='thumb' and a.get('sizes')=='78px' for a in p.images)
  print('PASS exact inline utility CSS, nonblocking fallback and responsive image roles',path)
if __name__=='__main__':main()
