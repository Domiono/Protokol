const assert=require('assert'),vm=require('vm'),fs=require('fs');
const source=fs.readFileSync('server/online.js','utf8');
function setup({visible=true,webdriver=false,token='test-token'}={}){
  const calls=[],listeners={};
  const document={visibilityState:visible?'visible':'hidden',readyState:'complete',
    querySelector:()=>token?{getAttribute:()=>token}:null,
    addEventListener:(name,fn)=>(listeners[name]??=[]).push(fn)};
  vm.runInNewContext(source,{document,navigator:{webdriver},window:{setInterval:()=>{}},URLSearchParams,fetch:(url,options)=>{calls.push({url,options});return Promise.resolve();}});
  return {document,calls,listeners,views:()=>calls.filter(x=>x.url==='/pageview.php')};
}
let x=setup();assert.equal(x.views().length,1);assert.equal(x.views()[0].options.body.get('token'),'test-token');assert.equal(x.views()[0].options.keepalive,true);
x.listeners.visibilitychange.forEach(f=>f());assert.equal(x.views().length,1,'visibility transition counted twice');
x=setup({visible:false});assert.equal(x.views().length,0);x.document.visibilityState='visible';x.listeners.visibilitychange.forEach(f=>f());assert.equal(x.views().length,1);
assert.equal(setup({webdriver:true}).views().length,0);assert.equal(setup({token:''}).views().length,0);
console.log('PASS: visible browser confirms once; hidden tab waits; automation and tokenless page do not count');
