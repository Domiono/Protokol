// Exercise the actual public HTML with the candidate helper before publication.
// Never submits reservations, accepts cookies or sends phone tracking events.
const {chromium}=require('playwright');
const {execFileSync}=require('node:child_process');
const fs=require('node:fs');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox']});
 for(const width of [390,1365]){
  const page=await browser.newPage({viewport:{width,height:900}});
  const requests=[];page.on('request',r=>requests.push(r.url()));
  if(process.env.RM_ASSETS_PREVIEW==='1'){
   await page.route('https://rentmor.pl/**',async route=>{
    if(!route.request().isNavigationRequest())return route.continue();
    const response=await route.fetch();
    const body=execFileSync('php',['-r','require "server/seo_structure.php"; echo rm_light_public_assets(stream_get_contents(STDIN));'],{input:await response.text(),encoding:'utf8'});
    await route.fulfill({response,body});
   });
  }
  for(const path of ['/', '/odkurzacz-pioracy-karcher-puzzi-10-1/']){
   requests.length=0;
   await page.goto('https://rentmor.pl'+path,{waitUntil:'networkidle',timeout:60000});
   assert(!requests.some(u=>u.includes('font-awesome')), 'Icon font must not download');
   assert(await page.locator('a[href^="tel:"] svg.rm-inline-icon').count()>0);
   assert(await page.getByRole('link',{name:'Zadzwoń do RentMor',exact:true}).isVisible());
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   for(const icon of await page.locator('svg.rm-inline-icon').all()){
    if(!await icon.isVisible())continue;
    const box=await icon.boundingBox();assert(box.width>0&&box.height>0);
   }
   if(path==='/'){
    const cards=await page.locator('.product-item').count();assert(cards>10);
    await page.locator('#searchInput').fill('Puzzi');
    assert(await page.locator('.product-item:visible').count()>0);
    assert(await page.locator('.product-item:visible').count()<cards);
    await page.locator('#searchInput').fill('zzzz-no-equipment');
    assert(await page.getByText('Brak wyników',{exact:true}).isVisible());
    await page.getByRole('button',{name:'Wyczyść filtry',exact:true}).click();
    assert.equal(await page.locator('.product-item:visible').count(),cards);
    await page.evaluate(()=>window.scrollTo(0,0));
   }else{
    assert(await page.locator('[data-rm-image-kind="detail"]').evaluate(img=>img.complete&&img.naturalWidth>0));
    assert(await page.locator('form').count()>0);
   }
   fs.mkdirSync('reports',{recursive:true});
   await page.screenshot({path:`reports/light-assets-${path==='/'?'home':'offer'}-${width}.png`});
  }
  await page.close();
 }
 await browser.close();
 console.log('PASS: no icon font requests, visible SVG, phone labels, search/reset, images, forms, mobile/desktop');
})().catch(e=>{console.error(e);process.exit(1)});
