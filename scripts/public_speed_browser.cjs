// Read-only public smoke tests: no contact forms, reservations or admin actions.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox']});
 for(const width of [390,1365]){
  const page=await browser.newPage({viewport:{width,height:900},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('https://rentmor.pl/',{waitUntil:'networkidle'});
  const cards=page.locator('[data-rm-static="1"]');assert(await cards.count()>=20);
  assert.equal(await page.locator('[data-rm-next]').count(),0);
  assert.equal(await page.locator('[data-rm-gallery]').count(),0);
  assert(await cards.evaluateAll(cs=>cs.every(c=>c.querySelectorAll('img').length===1)));
  const first=cards.first();await first.scrollIntoViewIfNeeded();
  await first.locator('img').evaluate(im=>im.decode());
  const src=await first.locator('img').getAttribute('src');
  await page.waitForTimeout(5000);
  assert.equal(await first.locator('img').getAttribute('src'),src);
  await page.screenshot({path:`reports/home-${width}.png`});
  const search=page.locator('#searchInput');await search.fill('Puzzi');await search.press('i');await search.press('Backspace');
  assert(await page.getByRole('heading',{name:'Odkurzacz piorący Karcher Puzzi 10/1',exact:true}).isVisible());
  assert.equal(await page.getByRole('heading',{name:'Bruzdownica Stalco',exact:true}).isVisible(),false);
  await search.fill('');await search.press('Backspace');
  await page.getByRole('button',{name:/ogrodnicze/i}).click();
  assert.equal(await page.getByRole('heading',{name:'Bruzdownica Stalco',exact:true}).isVisible(),false);
  await page.getByRole('button',{name:/^wszystkie$/i}).click();
  const href=await first.locator('a').getAttribute('href');await page.goto('https://rentmor.pl'+href,{waitUntil:'networkidle'});
  const gallery=page.locator('.rm-detail-gallery');assert.equal(await gallery.count(),1);
  await gallery.locator('[data-rm-next]').click();
  await gallery.locator('.rm-active img').evaluate(im=>im.decode());
  await gallery.locator('[data-rm-zoom]').click();
  await page.locator('.rm-gallery-lightbox img').evaluate(im=>im.decode());
  await page.screenshot({path:`reports/detail-${width}.png`});
  await page.locator('.rm-gallery-lightbox-close').click();
  assert.equal(await page.locator('.rm-gallery-lightbox').count(),0);
  assert.deepEqual(errors,[]);console.log('PASS browser',width,'static cards, search, filters, detail navigation and zoom');
  await page.close();
 }
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
