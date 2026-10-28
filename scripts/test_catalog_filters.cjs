const assert = require('node:assert/strict');
const vm = require('node:vm');
const {execFileSync} = require('node:child_process');
const source = execFileSync('python3', ['-c', `import sys; sys.path.insert(0,'scripts'); from patch_catalog_filters import JS, patch
fixture='onkeyup="searchProducts()"\\n        function searchProducts() {}\\n        function filterProducts(cat, btn) {}\\n        function filterItems(search, cat) {}'
patched=patch(fixture)
assert patch(patched)==patched
assert 'oninput="searchProducts()"' in patched
old=fixture.replace('onkeyup','oninput').replace('        function searchProducts() {}\\n        function filterProducts(cat, btn) {}\\n', '''        // RentMor combined catalogue filters v1
        let selectedCategory = 'all';
        function searchProducts() {}
        function filterProducts(cat, btn) {}
''')
assert patch(patch(old))==patch(old)
assert 'Brak wyników' in patch(old)
try: patch('unknown')
except ValueError: pass
else: raise AssertionError('Must reject unknown source')
print(JS)`], {encoding:'utf8'});
const classes = () => ({list:new Set(),toggle(s,on){if(on)this.list.add(s);else this.list.delete(s)},contains(s){return this.list.has(s)}});
const cards = ['Puzzi','Żyrafa'].map((name,i)=>({name,cat:i?'budowlane':'czyszczace',style:{display:''},classList:classes(),hidden:false}));
const buttons = ['all','ogrodnicze'].map((category)=>({classList:classes(),attributes:{},getAttribute:(k)=>k==='onclick'?"filterProducts('"+category+"', this)":'',setAttribute(k,v){this.attributes[k]=v;}}));
const input = {value:'',focus(){this.focused=true}};
let empty;
const grid = {appendChild(node){empty=node;}};
for(const c of cards)c.parentNode=grid;
const doc = {
  getElementById(id){if(id==='searchInput')return input;if(id==='rentmor-empty-catalogue')return empty;return null},
  querySelectorAll(sel){if(sel==='.product-card')return cards;if(sel==='.filter-btn')return buttons;return []},
  createElement(){return {style:{},setAttribute(){},querySelector(){return {addEventListener(k,fn){this.click=fn;}}},parentNode:grid};}
};
let result;
const context=vm.createContext({document:doc,filterItems:(search,cat)=>{
 result={search,cat};
 for(const card of cards)card.style.display=(card.name.toLowerCase().includes(search) && (cat==='all'||cat===card.cat))?'':'none';
}});
vm.runInContext(source,context);
input.value='puzzi';context.searchProducts();assert.deepEqual(result,{search:'puzzi',cat:'all'});
assert.equal(empty.style.display,'none');
context.filterProducts('ogrodnicze',buttons[1]);
assert.equal(empty.style.display,'block');
assert.equal(empty.id,'rentmor-empty-catalogue');
assert.equal(empty.attributes,undefined);
context.clearSearchFilters();
assert.equal(input.value,'');
assert.equal(input.focused,true);
assert.equal(empty.style.display,'none');
assert.deepEqual(result,{search:'',cat:'all'});
assert.equal(buttons[0].attributes['aria-pressed'],'true');
console.log('Catalogue empty state, clear filters, combined filters and upgrade tests passed.');
