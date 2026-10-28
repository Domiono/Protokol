<?php
if (empty($is_logged)) return;
require_once __DIR__ . '/phone_events.php';
$phoneEvents = array_reverse(rm_phone_events_read());
?>
<section class="bg-white p-6 rounded-3xl shadow-sm mb-8" id="phone-contacts">
<h2 class="font-black text-lg">Kontakty telefoniczne</h2>
<p class="text-sm text-gray-500">Czas polski. Zdarzenia, nie unikalni klienci ani odbyte rozmowy. Historia od uruchomienia funkcji.</p>
<div class="flex flex-wrap gap-4 my-4">
<label>Od <input id="phone-from" type="date" class="border rounded-lg p-2"></label>
<label>Do <input id="phone-to" type="date" class="border rounded-lg p-2"></label>
<label>Źródło <select id="phone-source" class="border rounded-lg p-2"><option value="">Wszystkie źródła</option></select></label>
<label>Działanie <select id="phone-action" class="border rounded-lg p-2"><option value="">Wszystkie działania</option><option value="reveal">Pokaż numer</option><option value="call">Zadzwoń</option></select></label>
</div>
<p id="phone-totals" class="font-bold my-4" aria-live="polite"></p>
<div style="overflow-x:auto"><table class="w-full text-sm"><thead><tr><th class="p-2 text-left">Data i godzina</th><th class="p-2 text-left">Źródło</th><th class="p-2 text-left">Działanie</th></tr></thead><tbody id="phone-rows"></tbody></table></div>
<button type="button" id="phone-more" class="bg-yellow-400 rounded-xl p-3 mt-4">Pokaż kolejne 50</button>
</section>
<script>
(function(){
const events=<?= json_encode($phoneEvents, JSON_HEX_TAG|JSON_HEX_AMP|JSON_HEX_APOS|JSON_HEX_QUOT|JSON_UNESCAPED_UNICODE) ?>;
const from=document.getElementById('phone-from'),to=document.getElementById('phone-to'),source=document.getElementById('phone-source'),action=document.getElementById('phone-action'),rows=document.getElementById('phone-rows'),totals=document.getElementById('phone-totals'),more=document.getElementById('phone-more');
from.value=<?= json_encode((new DateTimeImmutable('now',new DateTimeZone('Europe/Warsaw')))->modify('-29 days')->format('Y-m-d')) ?>;
to.value=<?= json_encode((new DateTimeImmutable('now',new DateTimeZone('Europe/Warsaw')))->format('Y-m-d')) ?>;
const sources=new Map([['home','Strona główna']]);events.forEach(e=>sources.set(e.source_id,e.source));sources.forEach((name,id)=>source.add(new Option(name,id)));
let limit=50;
function render(){
rows.replaceChildren();more.hidden=true;
if(from.value&&to.value&&from.value>to.value){totals.textContent='Data OD nie może być późniejsza niż DO.';return;}
const filtered=events.filter(e=>(!from.value||e.at.slice(0,10)>=from.value)&&(!to.value||e.at.slice(0,10)<=to.value)&&(!source.value||e.source_id===source.value)&&(!action.value||e.action===action.value));
totals.textContent='Pokaż numer: '+filtered.filter(e=>e.action==='reveal').length+' • Zadzwoń: '+filtered.filter(e=>e.action==='call').length+(filtered.length?'':' • Brak zdarzeń dla wybranych filtrów.');
filtered.slice(0,limit).forEach(e=>{const tr=document.createElement('tr');[e.at,e.source,e.action==='call'?'Zadzwoń':'Pokaż numer'].forEach(value=>{const td=document.createElement('td');td.className='p-2 border-b';td.textContent=value;tr.appendChild(td);});rows.appendChild(tr);});more.hidden=filtered.length<=limit;
}
[from,to,source,action].forEach(el=>el.addEventListener('change',()=>{limit=50;render();}));more.addEventListener('click',()=>{limit+=50;render();});render();
})();
</script>
