import argparse,asyncio,json,os,shutil
from pathlib import Path
from playwright.async_api import async_playwright

async def main():
 parser=argparse.ArgumentParser();parser.add_argument('--after',action='store_true');args=parser.parse_args()
 out=Path('mortii-browser-proof');out.mkdir(exist_ok=True)
 async with async_playwright() as p:
  b=await p.chromium.launch(executable_path=shutil.which('google-chrome') or shutil.which('chromium'),headless=True,args=['--no-sandbox'])
  c=await b.new_context(viewport={'width':390,'height':844},is_mobile=True,has_touch=True,device_scale_factor=2,user_agent='Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36')
  results=[]
  urls=['/','/sprzet-budowlany/'] if not args.after else ['/','/sprzet-budowlany/','/szlifierka-katowa-230mm-bosch-professional-gws-2200/','/polityka-prywatnosci.php']
  for i,path in enumerate(urls):
   page=await c.new_page();errors=[];resources=[]
   page.on('pageerror',lambda e:errors.append(str(e)))
   page.on('response',lambda r:resources.append({'url':r.url,'status':r.status}) if 'mortii' in r.url else None)
   await page.goto('https://rentmor.pl'+path,wait_until='domcontentloaded',timeout=60000)
   launch=page.locator('#mortii-launcher');await launch.wait_for();await page.wait_for_timeout(800)
   state=await launch.evaluate('(e)=>({html:e.outerHTML,width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height,round:getComputedStyle(e).borderRadius,images:[...e.querySelectorAll("img")].map(i=>({src:i.src,loaded:i.complete&&i.naturalWidth>0}))})')
   await page.screenshot(path=str(out/f'{"after" if args.after else "before"}-{i}-closed.png'))
   if args.after:
    assert 60<=state['width']<=64 and 60<=state['height']<=64,state
    assert state['images'] and all(x['loaded'] for x in state['images']),state
    assert 'rentmor-ai-mortii-visual' not in await page.content()
    await launch.click();await page.locator('#mortii-chat textarea').wait_for(state='visible')
    assert not await launch.is_visible()
    avatar=page.locator('#mortii-chat .mortii-avatar').first
    await avatar.wait_for();assert await avatar.evaluate('(i)=>i.complete&&i.naturalWidth>0')
    await page.screenshot(path=str(out/f'after-{i}-open.png'))
    await page.get_by_role('button',name='Zamknij',exact=True).click();assert await launch.is_visible()
    assert not errors,errors
    assert all(x['status']==200 for x in resources),resources
   results.append({'path':path,'errors':errors,'resources':resources,'launcher':state})
   await page.close()
  (out/f'{"after" if args.after else "before"}.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
  print(json.dumps(results,ensure_ascii=False));await b.close()
asyncio.run(main())
