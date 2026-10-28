const assert = require('node:assert/strict');
const vm = require('node:vm');
const {execFileSync} = require('node:child_process');
const source = execFileSync('python3', ['-c', `import sys; sys.path.insert(0,'scripts'); from patch_catalog_filters import JS, patch
fixture='onkeyup="searchProducts()"\\n        function searchProducts() {}\\n        function filterProducts(cat, btn) {}\\n        function filterItems(search, cat) {}'
patched=patch(fixture)
assert patch(patched)==patched
assert 'oninput="searchProducts()"' in patched
try: patch('unknown')
except ValueError: pass
else: raise AssertionError('Must reject unknown source')
print(JS)`], {encoding:'utf8'});
const input = {value:'puzzi'};
const buttons = [0,1].map(()=>({active:false, classList:{toggle(){}},setAttribute(k,v){this[k]=v;}}));
let result;
const context=vm.createContext({document:{getElementById:()=>input,querySelectorAll:()=>buttons},filterItems:(search,cat)=>{result={search,cat};}});
vm.runInContext(source,context);
context.filterProducts('ogrodnicze',buttons[1]);
assert.deepEqual(result,{search:'puzzi',cat:'ogrodnicze'});
input.value='  RĘBAK ';context.searchProducts();assert.deepEqual(result,{search:'rębak',cat:'ogrodnicze'});
context.filterProducts('all',buttons[0]);assert.deepEqual(result,{search:'rębak',cat:'all'});
input.value='';context.searchProducts();assert.deepEqual(result,{search:'',cat:'all'});
assert.equal(buttons[0]['aria-pressed'],'true');
console.log('Combined category/search and idempotent template patch tests passed.');
