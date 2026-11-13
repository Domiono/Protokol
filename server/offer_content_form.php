<?php
if (empty($is_logged)) { http_response_code(404); exit; }
// Included inside the authenticated inventory form; all submitted fields use its CSRF token.
$rm_content=rm_offer_content($edit_item??[]);
$rm_e=static fn($s)=>htmlspecialchars((string)$s,ENT_QUOTES,'UTF-8');
?>
<fieldset class="bg-slate-50 p-4 rounded-2xl border border-slate-200">
<legend class="font-black">Pełna treść oferty</legend>
<p class="text-sm mb-4">Te same treści edytujesz w aplikacji. Puste pole usuwa daną treść z oferty. Linki prowadzą do stron RentMor.pl.</p>
<input type="hidden" name="offer_revision" value="<?= $rm_e(rm_offer_revision($edit_item??[])) ?>">
<?php foreach(rm_offer_fields() as $key=>$label): ?>
<label class="block mb-4"><?= $rm_e($label) ?>
<textarea class="block w-full p-3 border rounded-xl" rows="<?= in_array($key,['intro','booking_text','rental_note','price_note'],true)?4:2 ?>" name="offer_content[<?= $rm_e($key) ?>]" maxlength="<?= in_array($key,['intro','booking_text'],true)?16000:4000 ?>"><?= $rm_e($rm_content[$key]) ?></textarea>
</label>
<?php endforeach; ?>
<?php foreach(['questions'=>['Pytania i odpowiedzi','question'=>'Pytanie','answer'=>'Odpowiedź'], 'related'=>['Linki do powiązanego sprzętu','url'=>'Adres strony sprzętu','label'=>'Nazwa linku']] as $key=>$labels): ?>
<h3 class="font-bold mt-4"><?= $rm_e($labels[0]) ?></h3>
<div data-offer-rows="<?= $key ?>">
<?php foreach($rm_content[$key] as $i=>$row): ?>
<div class="p-3 mb-3 border rounded-xl" data-offer-row>
<?php foreach(array_slice($labels,1,null,true) as $column=>$label): ?>
<label class="block mb-2"><?= $rm_e($label) ?><textarea class="block w-full p-2 border rounded-xl" rows="<?= $column==='answer'?4:2 ?>" name="offer_content[<?= $key ?>][<?= $i ?>][<?= $column ?>]"><?= $rm_e($row[$column]) ?></textarea></label>
<?php endforeach; ?>
<button type="button" data-offer-remove class="p-2 border rounded-xl">Usuń pozycję</button>
</div>
<?php endforeach; ?>
</div>
<button type="button" data-offer-add="<?= $key ?>" class="p-2 border rounded-xl">Dodaj <?= $key==='questions'?'pytanie':'link' ?></button>
<?php endforeach; ?>
</fieldset>
<script>
(()=>{
const script=document.currentScript,fieldset=script.previousElementSibling;
const spec={questions:{question:'Pytanie',answer:'Odpowiedź'},related:{url:'Adres strony sprzętu',label:'Nazwa linku'}};
function renumber(container,key){[...container.children].forEach((row,i)=>row.querySelectorAll('textarea').forEach(field=>field.name=`offer_content[${key}][${i}][${field.dataset.column||field.name.match(/\[([^\]]+)\]$/)[1]}]`));}
fieldset.addEventListener('click',event=>{
const remove=event.target.closest('[data-offer-remove]');
if(remove){const container=remove.parentElement.parentElement,key=container.dataset.offerRows;remove.parentElement.remove();renumber(container,key);return;}
const add=event.target.closest('[data-offer-add]');if(!add)return;
const key=add.dataset.offerAdd,container=fieldset.querySelector(`[data-offer-rows="${key}"]`);
if(container.children.length>=40){alert('Maksymalnie 40 pozycji.');return;}
const row=document.createElement('div');row.className='p-3 mb-3 border rounded-xl';row.dataset.offerRow='';
for(const [column,text] of Object.entries(spec[key])){const label=document.createElement('label');label.className='block mb-2';label.textContent=text;const field=document.createElement('textarea');field.dataset.column=column;field.rows=column==='answer'?4:2;field.className='block w-full p-2 border rounded-xl';label.append(field);row.append(label);}
const button=document.createElement('button');button.type='button';button.textContent='Usuń pozycję';button.dataset.offerRemove='';button.className='p-2 border rounded-xl';row.append(button);container.append(row);renumber(container,key);
});
})();
</script>
