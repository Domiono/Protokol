const fs=require('fs'),vm=require('vm'),assert=require('assert');
const listeners={},sent=[];
const context={document:{querySelector:()=>null,querySelectorAll:()=>[],visibilityState:'hidden',readyState:'complete',addEventListener:(type,fn)=>{listeners[type]=fn;}},window:{setInterval:()=>{}},location:{search:''},URLSearchParams,fetch:(url,options)=>{sent.push({url,options});return Promise.resolve();}};
vm.runInNewContext(fs.readFileSync('server/online.js','utf8'),context);
function click(attrs,text='Zadzwoń'){listeners.click({target:{closest:()=>({getAttribute:name=>attrs[name]||null,textContent:text})}});return sent.at(-1);}
let event=click({href:'tel:733891281',onclick:"trackCall('p1')"});
assert.equal(event.options.body.get('source_id'),'p1');assert.equal(event.options.body.get('action'),'call');assert.equal(event.options.keepalive,true);
event=click({href:'tel:733891281',onclick:"trackCall('top_nav')"});assert.equal(event.options.body.get('source_id'),'home');
context.location.search='?id=p2';event=click({href:'tel:733891281'});assert.equal(event.options.body.get('source_id'),'p2');
event=click({'data-product-id':'p3'},'Pokaż numer');assert.equal(event.options.body.get('source_id'),'p3');assert.equal(event.options.body.get('action'),'reveal');
const before=sent.length;click({href:'/kontakt'},'Kontakt');assert.equal(sent.length,before);
console.log('PASS: equipment, home and detail attribution, reveal, keepalive, unrelated clicks');

context.location.search='';context.document.querySelector=selector=>selector==='.rm-detail-gallery'?{}:null;context.document.querySelectorAll=()=>[{getAttribute:()=>"trackCall('p4')"}];
event=click({href:'tel:733891281',onclick:"trackCall('top_nav')"});assert.equal(event.options.body.get('source_id'),'p4');
console.log('PASS: header click on SEO product URL');

// The rendered page wins over stale query parameters and unrelated recommendation links.
context.location.search='?id=wrong';
context.document.querySelector=selector=>selector==='script[data-phone-source]'?{getAttribute:()=> 'puzzi'}:null;
context.document.querySelectorAll=()=>[{getAttribute:()=>"trackCall('szlifierka')"}];
event=click({href:'tel:733891281',onclick:"trackCall('top_nav')"});
assert.equal(event.options.body.get('source_id'),'puzzi');
event=click({},'Pokaż numer');
assert.equal(event.options.body.get('source_id'),'puzzi');assert.equal(event.options.body.get('action'),'reveal');
context.document.querySelector=selector=>selector==='script[data-phone-source]'?{getAttribute:()=> 'home'}:null;
event=click({href:'tel:733891281',onclick:"trackCall('top_nav')"});assert.equal(event.options.body.get('source_id'),'home');
event=click({href:'tel:733891281',onclick:"trackCall('puzzi')"});assert.equal(event.options.body.get('source_id'),'puzzi');
const count=sent.length;click({href:'tel:733891281',onclick:"trackCall('puzzi')"});assert.equal(sent.length,count+1);
console.log('PASS: authoritative page identity, home catalogue product, one history event per click');

