const vm=require('vm'),fs=require('fs'),assert=require('assert');
class Element{
 constructor(tag,text='',attrs={}){this.tag=tag;this.textContent=text;this.attrs={...attrs};this.dataset={};this.children=[];this.events={};this.value=attrs.value||'';this.disabled=false;}
 append(...nodes){this.children.push(...nodes);for(const n of nodes)if(n&&typeof n==='object')n.parent=this;}
 after(node){this.parent.append(node);}
 replaceWith(node){const p=this.parent;const i=p.children.indexOf(this);p.children[i]=node;node.parent=p;}
 replaceChildren(...nodes){this.children=[];this.append(...nodes);}
 setAttribute(k,v){this.attrs[k]=v;}
 removeAttribute(k){delete this.attrs[k];}
 addEventListener(k,fn){(this.events[k]??=[]).push(fn);}
 dispatchEvent(e){for(const fn of this.events[e.type]||[])fn(e);}
 querySelectorAll(tag){let found=[];for(const n of this.children)if(n instanceof Element){if(n.tag===tag)found.push(n);found.push(...n.querySelectorAll(tag));}return found;}
}
const context={console,Intl,Date,Map,Number,String,Error,Event:class{constructor(type){this.type=type;}},el:(...a)=>new Element(...a),uid:()=> 'fixture',calls:[]};
vm.createContext(context);vm.runInContext(fs.readFileSync(__dirname+'/../server/mortii.js','utf8').split('function availabilityCalendar')[1].split('\nfunction booking')[0].replace(/^/, 'function availabilityCalendar'),context);
function setup(){const form=new Element('form'),fields={};for(const k of ['start_date','time','rental_days']){fields[k]=new Element('input');const l=new Element('label');l.append(fields[k]);form.append(l);}fields.rental_days.value='1';const submit=new Element('button');form.append(submit);context.availabilityCalendar(fields,form,{name:'fixture'},submit);return {form,fields,submit,calendar:fields.start_date.parent.children[1]};}
const tick=()=>new Promise(r=>setImmediate(r));
(async()=>{
 context.post=async(a,v)=>{context.calls.push(v);const [y,m]=v.month.split('-').map(Number);return {days:Array.from({length:new Date(y,m,0).getDate()},(_,i)=>({date:v.month+'-'+String(i+1).padStart(2,'0'),status:i===19?'busy':'available',slots:i===19?[]:['12:00','12:30']}))};};
 const s=setup();await tick();
 const buttons=s.calendar.querySelectorAll('button').filter(n=>n.dataset.date);
 assert(buttons.find(n=>n.attrs.class.endsWith('busy')).disabled);
 buttons.at(-1).dispatchEvent({type:'click'});assert(s.fields.start_date.value);assert(!s.fields.time.disabled);assert(!s.submit.disabled);
 s.fields.rental_days.value='2';s.fields.rental_days.dispatchEvent({type:'input'});assert(!s.fields.start_date.value);assert(s.submit.disabled);await tick();assert.equal(context.calls.at(-1).rental_days,'2');
 s.fields.rental_days.value='0';s.fields.rental_days.dispatchEvent({type:'input'});await tick();assert(s.submit.disabled);assert(s.calendar.children[3].textContent.includes('od 1 do 365'));
 context.post=async()=>{throw Error('network failure')};s.fields.rental_days.value='3';s.fields.rental_days.dispatchEvent({type:'input'});await tick();assert.equal(s.calendar.children[2].children.length,0);assert.equal(s.calendar.children[3].textContent,'network failure');assert.equal(s.calendar.children[4].hidden,false);
 // An older response must not replace the newer duration's availability.
 let release;context.post=()=>new Promise(r=>release=r);s.fields.rental_days.value='4';s.fields.rental_days.dispatchEvent({type:'input'});const old=release;s.fields.rental_days.value='0';s.fields.rental_days.dispatchEvent({type:'input'});old({days:[]});await tick();assert(s.calendar.children[3].textContent.includes('od 1 do 365'));assert(s.submit.disabled);
 let prevented=false;s.form.dispatchEvent({type:'submit',preventDefault(){prevented=true;},stopImmediatePropagation(){}});assert(prevented);
 console.log('PASS: busy dates, slots, duration refresh, invalid duration, failure, stale response, unchecked submit');
})().catch(e=>{console.error(e);process.exit(1)});
