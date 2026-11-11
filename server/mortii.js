(()=>{'use strict';if(document.getElementById('mortii-launcher'))return;
const el=(tag,text,attrs={})=>{const n=document.createElement(tag);if(text)n.textContent=text;for(const[k,v]of Object.entries(attrs))n.setAttribute(k,v);return n;};
let bootPromise;async function boot(){if(!bootPromise)bootPromise=fetch('/mortii_api.php',{credentials:'same-origin',headers:{Accept:'application/json'}}).then(async r=>{const d=await r.json();if(!r.ok)throw Error(d.error);return d;}).catch(e=>{bootPromise=null;throw e;});return bootPromise;}
const uid=()=>Array.from(crypto.getRandomValues(new Uint8Array(16)),x=>x.toString(16).padStart(2,'0')).join('');
async function post(action,values,id){if(action==='availability'){const r=await fetch('/booking_calendar.php?'+new URLSearchParams(values),{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});const d=await r.json();if(!r.ok)throw Error(d.error||'Nie udało się sprawdzić dostępności.');return d;}const b=await boot();const r=await fetch('/mortii_api.php',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify({...values,action,csrf:b.csrf,request_id:id})});let d;try{d=await r.json();}catch(e){throw Error('Nie udało się wysłać. Spróbuj ponownie.');}if(!r.ok)throw Error(d.error||'Spróbuj ponownie później.');return d;}
const mortiiArt={launcher:'/assets/mortii-launcher-59d4e684be12.webp',avatar:'/assets/mortii-avatar-00398e07cbdc.webp'};
const mortiiImage=(src,size)=>el('img','',{src,alt:'','aria-hidden':'true',width:String(size),height:String(size),decoding:'async',class:'mortii-avatar'});
const launcher=el('button','',{id:'mortii-launcher',type:'button','aria-label':'Otwórz czat z Mortii',title:'Zapytaj Mortii','aria-expanded':'false','aria-controls':'mortii-chat'});launcher.append(mortiiImage(mortiiArt.launcher,54));
const panel=el('section','',{id:'mortii-chat',role:'dialog','aria-label':'Mortii — pomoc RentMor',hidden:''});const top=el('div','',{class:'mortii-top'});const heading=el('strong','Mortii · RentMor');heading.prepend(mortiiImage(mortiiArt.avatar,32));top.append(heading);const close=el('button','Zamknij',{type:'button'});top.append(close);const messages=el('div','',{class:'mortii-messages','aria-live':'polite'});const notice=el('p','Rozmawiasz z AI. Nie wpisuj danych osobowych — użyj formularza Rezerwuj.');const form=el('form');const label=el('label','Wiadomość do Mortii');const input=el('textarea','',{rows:'2',maxlength:'1500',required:'','aria-label':'Wiadomość do Mortii'});label.append(input);const send=el('button','Wyślij',{type:'submit'});const reset=el('button','Nowa rozmowa',{type:'button'});const error=el('p','',{role:'status',class:'mortii-status'});form.append(label,send,reset,error);panel.append(top,messages,notice,form);document.body.append(launcher,panel);
function assistantBubble(text){const row=el('div','',{class:'mortii-reply'});row.append(mortiiImage(mortiiArt.avatar,30),el('p',text,{class:'mortii-answer'}));return row;}
function render(history){messages.replaceChildren();if(!history.length)messages.append(assistantBubble('Cześć! Jestem Mortii. Pomogę Ci wybrać sprzęt i odpowiem na pytania o wynajem.'));for(const m of history)messages.append(m.role==='user'?el('p',m.content,{class:'mortii-user'}):assistantBubble(m.content));messages.scrollTop=messages.scrollHeight;}
launcher.addEventListener('click',async()=>{panel.hidden=false;launcher.hidden=true;launcher.setAttribute('aria-expanded','true');input.focus();try{render((await boot()).history);}catch(e){error.textContent=e.message;}});function hide(){panel.hidden=true;launcher.hidden=false;launcher.setAttribute('aria-expanded','false');launcher.focus();}close.addEventListener('click',hide);panel.addEventListener('keydown',e=>{if(e.key==='Escape')hide();});
let busy=false,chatId=uid();form.addEventListener('submit',async e=>{e.preventDefault();if(busy)return;busy=true;send.disabled=reset.disabled=true;input.readOnly=true;error.textContent='Mortii odpowiada…';try{const d=await post('chat',{message:input.value},chatId);render(d.history);input.value='';chatId=uid();error.textContent='';}catch(e){error.textContent=e.message;chatId=uid();}finally{busy=false;send.disabled=reset.disabled=false;input.readOnly=false;}});reset.addEventListener('click',async()=>{if(busy)return;busy=true;reset.disabled=send.disabled=true;try{render((await post('reset',{},uid())).history);error.textContent='';}catch(e){error.textContent=e.message;}finally{busy=false;reset.disabled=send.disabled=false;}});
function availabilityCalendar(fields, form, product, submit) {
  const original = fields.start_date;
  original.type = 'text'; original.readOnly = true;
  original.placeholder = 'Wybierz dzień w kalendarzu';
  original.removeAttribute('required');
  const clock = el('select', '', {name:'time', required:'', 'aria-label':'Godzina odbioru'});
  fields.time.replaceWith(clock); fields.time = clock;
  const calendar = el('div','',{class:'mortii-calendar', 'aria-label':'Kalendarz dostępności'});
  const nav = el('div','',{class:'mortii-calendar-nav'});
  const prev = el('button','‹',{type:'button','aria-label':'Poprzedni miesiąc'});
  const next = el('button','›',{type:'button','aria-label':'Następny miesiąc'});
  const title = el('strong'); nav.append(prev,title,next);
  const weekdays = el('div','',{class:'mortii-calendar-grid','aria-hidden':'true'});
  for(const day of ['Pn','Wt','Śr','Cz','Pt','So','Nd']) weekdays.append(el('span',day));
  const grid = el('div','',{class:'mortii-calendar-grid'});
  const message = el('p','',{role:'status','aria-live':'polite',class:'mortii-calendar-status'});
  const retry = el('button','Spróbuj ponownie',{type:'button',hidden:''});
  calendar.append(nav,weekdays,grid,message,retry,el('p','Zielony: dostępny · Czerwony: zajęty · Szary: brak odbioru',{class:'mortii-calendar-legend'}));
  original.after(calendar);
  const today = new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Warsaw',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  const first = today.slice(0,7)+'-01'; let month = first, serial = 0, selected = null, loading = false;
  const iso = d => d.toISOString().slice(0,10);
  function reset(){ original.value=''; selected=null; clock.replaceChildren(el('option','Najpierw wybierz dzień',{value:''})); clock.disabled=true; submit.disabled=true; }
  function choose(day){
    selected=day; original.value=day.date; clock.replaceChildren(el('option','Wybierz godzinę',{value:''}));
    for(const time of day.slots) clock.append(el('option',time,{value:time}));
    clock.disabled=false; submit.disabled=false;
    for(const button of grid.querySelectorAll('button')) button.setAttribute('aria-pressed',String(button.dataset.date===day.date));
    original.dispatchEvent(new Event('input',{bubbles:true}));
    message.textContent='Wybierz godzinę odbioru. Dostępność sprawdzono dla całego najmu.';
  }
  async function load(){
    const version=++serial; loading=true;reset();grid.replaceChildren();retry.hidden=true;calendar.setAttribute('aria-busy','true');
    const date=new Date(month+'T12:00:00Z');title.textContent=new Intl.DateTimeFormat('pl-PL',{month:'long',year:'numeric',timeZone:'UTC'}).format(date);
    prev.disabled=month<=first;
    const maxMonth=iso(new Date(Date.parse(today+'T12:00:00Z')+365*86400000)).slice(0,7)+'-01';next.disabled=month>=maxMonth;
    message.textContent='Sprawdzam dostępne terminy…';
    const days=Number(fields.rental_days.value);
    if(!Number.isInteger(days)||days<1||days>365){loading=false;calendar.removeAttribute('aria-busy');message.textContent='Podaj liczbę dób od 1 do 365.';return;}
    try {
      const data=await post('availability',{equipment:product.name,month:month.slice(0,7),rental_days:String(days)},uid());
      if(version!==serial)return;
      if(!Array.isArray(data.days))throw Error('Nie udało się sprawdzić dostępności.');
      const count=new Date(date.getUTCFullYear(),date.getUTCMonth()+1,0).getDate();
      if(data.days.length!==count)throw Error('Niepełne dane dostępności. Spróbuj ponownie.');
      const byDate=new Map(data.days.map(day=>[day.date,day]));
      for(let i=0;i<(date.getUTCDay()+6)%7;i++)grid.append(el('span'));
      for(let n=1;n<=count;n++){
        const value=month.slice(0,8)+String(n).padStart(2,'0'), day=byDate.get(value);
        if(!day||!Array.isArray(day.slots)||!['available','busy','closed','past'].includes(day.status)||day.slots.some(t=>!/^\d{2}:\d{2}$/.test(t)))throw Error('Nieprawidłowe dane dostępności.');
        const available=day.status==='available'&&day.slots.length>0&&value>=today;
        const label=day.status==='busy'?'zajęty':available?'dostępny':'brak odbioru';
        const button=el('button',String(n),{type:'button',class:'mortii-calendar-day '+(available?'available':day.status==='busy'?'busy':'closed'),'aria-label':value+', '+label,'aria-pressed':'false'});
        button.dataset.date=value;button.disabled=!available;button.addEventListener('click',()=>choose(day));grid.append(button);
      }
      message.textContent='Dostępność dla '+days+' dób. Wybierz dzień odbioru.';
    } catch(e){if(version!==serial)return;grid.replaceChildren();message.textContent=e.message||'Nie udało się sprawdzić dostępności.';retry.hidden=false;}
    finally{if(version===serial){loading=false;calendar.removeAttribute('aria-busy');}}
  }
  function move(delta){const d=new Date(month+'T12:00:00Z');d.setUTCMonth(d.getUTCMonth()+delta);month=iso(d).slice(0,7)+'-01';load();}
  prev.addEventListener('click',()=>move(-1));next.addEventListener('click',()=>move(1));retry.addEventListener('click',load);
  fields.rental_days.addEventListener('input',load);
  form.addEventListener('submit',event=>{if(loading||!selected||!selected.slots.includes(clock.value)){event.preventDefault();event.stopImmediatePropagation();message.textContent='Wybierz dostępny dzień i godzinę odbioru.';}});
  load();
}

function booking(target,name){if(!name||target.querySelector('.mortii-booking'))return;const details=el('details','',{class:'mortii-booking'});details.append(el('summary','Rezerwuj sprzęt'));const holder=el('div');details.append(holder);target.append(details);let ready=false;details.addEventListener('toggle',async()=>{if(!details.open||ready)return;holder.textContent='Wczytuję formularz…';try{const data=await boot();if(data.booking_enabled===false)throw Error('Formularz rezerwacji jest chwilowo niedostępny. Zadzwoń: 733 891 281.');const product=data.products.find(p=>p.name.trim()===name.trim());if(!product){holder.textContent='Otwórz szczegóły sprzętu lub zadzwoń: 733 891 281.';return;}ready=true;holder.replaceChildren();const f=el('form');let requestId=uid();const fields={};
for(const[k,title,type]of[['customer_name','Imię i nazwisko','text'],['phone','Numer telefonu','tel'],['email','E-mail','email'],['start_date','Data odbioru','date'],['time','Godzina odbioru','time'],['rental_days','Liczba dób','number']]){const l=el('label',title);const v=el('input','',{type,name:k,required:'',maxlength:'200'});if(k==='rental_days'){v.min='1';v.max='365';v.value='1';}if(k==='customer_name')v.autocomplete='name';if(k==='phone')v.autocomplete='tel';if(k==='email')v.autocomplete='email';fields[k]=v;l.append(v);f.append(l);}
const noteLabel=el('label','Wiadomość do RentMor (opcjonalnie)');const note=el('textarea','',{name:'customer_message',maxlength:'2000',rows:'3',placeholder:'Dodatkowe informacje lub pytania do rezerwacji'});fields.customer_message=note;noteLabel.append(note);f.append(noteLabel);
const trap=el('input','',{name:'website',tabindex:'-1',autocomplete:'off',class:'mortii-honey','aria-hidden':'true'});f.append(trap);const hours=el('p');const estimate=el('p');const update=()=>{const day=fields.start_date.value;const n=day?((new Date(day+'T12:00:00').getDay()+6)%7+1):0;hours.textContent=day?'Wydawanie tego dnia: '+(data.dates[day]??data.weekly[n]??'Brak wydawania'):'Wybierz dzień, aby zobaczyć godziny wydawania.';const days=Number(fields.rental_days.value);estimate.textContent=`Cena podstawowa: ${product.rate} zł/doba. Kaucja: ${product.deposit} zł. Rabaty i dodatki potwierdzi RentMor. Doba to 24 godziny.`;};fields.start_date.addEventListener('input',update);fields.rental_days.addEventListener('input',update);update();f.append(hours,estimate,el('p','Odbiór przy ul. Woźniców w Krakowie. Dokładny adres otrzymasz SMS-em po potwierdzeniu rezerwacji.'));
const privacy=el('label');const check=el('input','',{type:'checkbox',required:''});privacy.append(check,document.createTextNode(' Zapoznałem/am się z informacją o przetwarzaniu danych w '));const link=el('a','polityce prywatności',{href:'/polityka-prywatnosci.php',target:'_blank',rel:'noopener'});privacy.append(link);f.append(privacy,el('p','Zgłoszenie wymaga potwierdzenia RentMor i nie jest jeszcze rezerwacją sprzętu. Potwierdzenie rezerwacji otrzymasz SMS-em po sprawdzeniu zgłoszenia przez RentMor.'));const submit=el('button','Wyślij zgłoszenie rezerwacji',{type:'submit'});const status=el('p','',{role:'status'});f.append(submit,status);holder.append(f);availabilityCalendar(fields,f,product,submit);f.addEventListener('submit',async e=>{e.preventDefault();if(submit.disabled)return;submit.disabled=true;status.textContent='Sprawdzam termin i wysyłam…';try{const values=Object.fromEntries(Object.entries(fields).map(([k,v])=>[k,v.value]));const d=await post('request',{...values,equipment:product.name,privacy:check.checked?'yes':'',website:trap.value},requestId);status.textContent='Zgłoszenie zapisane. Numer: '+d.id+'. Czeka na potwierdzenie RentMor. '+'Potwierdzenie otrzymasz SMS-em po zatwierdzeniu przez RentMor. Zachowaj numer zgłoszenia.';for(const v of Object.values(fields))v.disabled=true;}catch(e){status.textContent=e.message;submit.disabled=false;}});
}catch(e){holder.textContent=e.message;}});}
for(const card of document.querySelectorAll('.product-item,.rm-seo-card')){if(card.classList.contains('rm-seo-card')&&!card.querySelector('.rm-seo-price'))continue;const heading=card.querySelector('.product-name,h3');if(heading)booking(card,heading.textContent.trim());}
for(const script of document.querySelectorAll('script[type="application/ld+json"]')){try{const p=JSON.parse(script.textContent);if(p['@type']==='Product'&&typeof p.name==='string'){const heading=document.querySelector('h1');if(heading)booking(heading.parentElement,p.name);}}catch(e){}}
})();

