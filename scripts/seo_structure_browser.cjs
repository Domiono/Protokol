// Read-only navigation checks; never submits a form or starts a phone call.
const {chromium}=require('playwright');
const {execFileSync}=require('node:child_process');
const fs=require('node:fs');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox']});
 fs.mkdirSync('reports',{recursive:true});
 for(const width of [360,390,1365]){
  const page=await browser.newPage({viewport:{width,height:900}});
  if(process.env.RM_MENU_PREVIEW==='1'){
   await page.route('https://rentmor.pl/**',async route=>{
    if(!route.request().isNavigationRequest())return route.continue();
    const response=await route.fetch();let body=await response.text();
    body=execFileSync('php',['-r','require "server/rental_categories.php"; $h=stream_get_contents(STDIN); echo rm_add_header_category_menu(str_replace(rm_category_nav(), "", $h));'],{input:body,encoding:'utf8'});
    await route.fulfill({response,body});
   });
  }
  await page.goto('https://rentmor.pl/',{waitUntil:'networkidle',timeout:60000});
  if(process.env.RM_MENU_PREVIEW==='1')await page.addStyleTag({content:fs.readFileSync('server/seo-structure.css','utf8')});
  const menu=page.locator('#rm-category-menu');
  const trigger=menu.locator('summary');const links=menu.locator('a');
  assert.equal(await menu.count(),1);
  assert.equal(await menu.getAttribute('open'),null);
  assert.equal(await links.first().isVisible(),false);
  const m=await trigger.boundingBox();const logo=await page.getByRole('link',{name:'RentMor.pl — strona główna',exact:true}).boundingBox();
  assert(m.width>=44&&m.height>=44&&m.x<logo.x&&m.y<100);
  await trigger.click();assert(await links.first().isVisible());
  const currentLabels=await links.allTextContents();
  const newLabels=['Sprzęt budowlany','Sprzęt ogrodniczy','Sprzęt czyszczący','Poradniki','Zasady wynajmu'];
  const oldLabels=newLabels.slice(0,3);
  // Pull requests check the currently published site before deployment (possibly old menu).
  // The post-deployment verification must require all five links.
  if(process.env.GITHUB_EVENT_NAME==='pull_request'){
   assert([JSON.stringify(oldLabels),JSON.stringify(newLabels)].includes(JSON.stringify(currentLabels)));
  } else {
   assert.deepEqual(currentLabels,newLabels);
  }
  if(currentLabels.length===5){
   assert.equal(await links.nth(3).getAttribute('href'),' /#poradniki'.trim());
   assert.equal(await links.nth(4).getAttribute('href'),'/zasady.php');
  }
  const box=await menu.locator('.rm-category-dropdown').boundingBox();assert(box.x>=0&&box.x+box.width<=width);
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:`reports/category-menu-${width}.png`});
  await page.keyboard.press('Escape');assert.equal(await menu.getAttribute('open'),null);
  await trigger.press('Enter');assert(await links.first().isVisible());
  await page.keyboard.press('Tab');assert(await links.first().evaluate(el=>el===document.activeElement));
  await page.mouse.click(width-8,450);assert.equal(await menu.getAttribute('open'),null);
  // A contextual in-paragraph link is useful for discovery; only the old bottom category navigation is prohibited.
  assert.equal(await page.locator('.rm-seo-links').count(),0);
  // PR runs check the previous live version (0); after release one contextual link is expected and checked in verify_seo_structure.py.
  assert((await page.locator('#wynajem-sprzetu-krakow p a[href="/sprzet-budowlany/"]').count()) <= 1);
  await trigger.click();await links.first().click();await page.waitForURL('**/sprzet-budowlany/');
  assert(await page.locator('h1').isVisible());
  console.log('PASS category menu: closed initially, position, order, touch, keyboard, Escape, outside click, width and category navigation',width);
  await page.close();
 }
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
