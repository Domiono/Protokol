const fs=require('fs'),vm=require('vm'),assert=require('assert');
const listeners={},sent=[];
const context={document:{visibilityState:'hidden',readyState:'complete',addEventListener:(type,fn)=>{listeners[type]=fn;}},window:{setInterval:()=>{}},location:{search:''},URLSearchParams,fetch:(url,options)=>{sent.push({url,options});return Promise.resolve();}};
vm.runInNewContext(fs.readFileSync('server/online.js','utf8'),context);
function click(attrs,text='Zadzwoń'){listeners.click({target:{closest:()=>({getAttribute:name=>attrs[name]||null,textContent:text})}});return sent.at(-1);}
let event=click({href:'tel:733891281',onclick:"trackCall('p1')"});
assert.equal(event.options.body.get('source_id'),'p1');assert.equal(event.options.body.get('action'),'call');assert.equal(event.options.keepalive,true);
event=click({href:'tel:733891281',onclick:"trackCall('top_nav')"});assert.equal(event.options.body.get('source_id'),'home');
context.location.search='?id=p2';event=click({href:'tel:733891281'});assert.equal(event.options.body.get('source_id'),'p2');
event=click({'data-product-id':'p3'},'Pokaż numer');assert.equal(event.options.body.get('source_id'),'p3');assert.equal(event.options.body.get('action'),'reveal');
const before=sent.length;click({href:'/kontakt'},'Kontakt');assert.equal(sent.length,before);
console.log('PASS: equipment, home and detail attribution, reveal, keepalive, unrelated clicks');
