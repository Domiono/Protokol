package pl.rentmor.protokol;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

/** Operational screens share the existing reservation and protocol records. */
public final class RentalOperations {
    public interface Host { void home(); void issue(JSONObject r); void protocol(JSONObject p); void inbox(); void sync(); }
    final Activity activity; final LocalStore store; final Host host; final boolean admin;
    LinearLayout page;
    RentalOperations(Activity a,LocalStore s,Host h,boolean admin){activity=a;store=s;host=h;this.admin=admin;}
    int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
    void text(LinearLayout box,String value,int size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));box.addView(t);}
    LinearLayout card(LinearLayout box,String title){LinearLayout c=new LinearLayout(activity);c.setOrientation(1);c.setPadding(dp(14),dp(10),dp(14),dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(16));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);box.addView(c,lp);text(c,title,18,0xff17263e);return c;}
    GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    Button action(String label,boolean primary,Runnable run){
        Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,1);
        b.setTextColor(primary?0xff17263e:0xff34465e);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setStateListAnimator(null);b.setElevation(0);
        GradientDrawable bg=surface(primary?0xffffcd54:0xffeef2f7,12);
        b.setBackgroundTintList(null);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x2017263e),bg,null));
        b.setOnClickListener(v->run.run());return b;
    }
    void button(LinearLayout box,String label,Runnable run){Button b=action(label,label.startsWith("Zapisz")||label.startsWith("Podsumuj"),run);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(5);lp.bottomMargin=dp(5);box.addView(b,lp);}
    void pair(LinearLayout box,String first,Runnable firstAction,String second,Runnable secondAction,boolean primary){
        LinearLayout row=new LinearLayout(activity);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(8);box.addView(row,rp);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(8);row.addView(action(first,primary,firstAction),left);row.addView(action(second,false,secondAction),new LinearLayout.LayoutParams(0,-2,1));
    }
    void badge(LinearLayout box,String value,int color,int fill){TextView t=new TextView(activity);t.setText(value);t.setTextSize(12);t.setTypeface(null,1);t.setTextColor(color);t.setPadding(dp(10),dp(6),dp(10),dp(6));t.setBackground(surface(fill,8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(6);lp.bottomMargin=dp(6);box.addView(t,lp);}
    void metric(LinearLayout row,String label,int value,int color){LinearLayout c=new LinearLayout(activity);c.setOrientation(1);c.setPadding(dp(10),dp(10),dp(8),dp(10));c.setBackground(surface(Color.WHITE,14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),dp(12));row.addView(c,lp);text(c,String.valueOf(value),26,color);text(c,label,12,0xff607086);}
    void screen(String title){
        activity.getWindow().setStatusBarColor(0xfff4f6fa);activity.getWindow().setNavigationBarColor(0xfff4f6fa);
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout outer=new LinearLayout(activity);outer.setOrientation(1);outer.setBackgroundColor(0xfff4f6fa);
        outer.setPadding(dp(16),dp(8),dp(16),dp(12));outer.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(16)+i.getSystemWindowInsetLeft(),dp(8)+i.getSystemWindowInsetTop(),dp(16)+i.getSystemWindowInsetRight(),dp(12)+i.getSystemWindowInsetBottom());return i;});
        pair(outer,"‹ Panel główny",host::home,"↻ Odśwież",host::sync,false);
        ScrollView scroll=new ScrollView(activity);scroll.setClipToPadding(false);scroll.setFillViewport(true);page=new LinearLayout(activity);page.setOrientation(1);page.setPadding(0,dp(12),0,dp(16));scroll.addView(page);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));activity.setContentView(outer);outer.requestApplyInsets();
        text(page,title,28,0xff17263e);if(store.pendingCount()>0)badge(page,"Oczekuje na synchronizację: "+store.pendingCount(),0xff925400,0xfffff0ce);
    }
    void error(Exception e){Toast.makeText(activity,e.getMessage()==null?"Sprawdź dane":e.getMessage(),Toast.LENGTH_LONG).show();}
    JSONObject copy(JSONObject o)throws JSONException{return new JSONObject(o.toString());}
    EditText field(LinearLayout b,String label,String value,boolean money){text(b,label,13,0xff526174);EditText e=new EditText(activity);e.setText(value);e.setSingleLine(true);if(money)e.setInputType(8194);b.addView(e);return e;}
    void date(EditText e){e.setFocusable(false);e.setOnClickListener(v->{LocalDate d;try{d=LocalDate.parse(e.getText());}catch(Exception x){d=LocalDate.now();}new DatePickerDialog(activity,(p,y,m,day)->e.setText(LocalDate.of(y,m+1,day).toString()),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();});}
    void save(JSONObject r)throws Exception {String conflict=RentalRules.conflict(r,store.allReservations(),store.assets());if(!conflict.isEmpty())throw new IllegalArgumentException(conflict);store.saveRentalOperation(r,"reservation_save");host.sync();}
    JSONObject protocolFor(JSONObject r){JSONArray all=store.allProtocols();for(int i=0;i<all.length();i++){JSONObject p=all.optJSONObject(i);if(p!=null&&r.optString("id").equals(p.optString("reservation_id")))return p;}return null;}
    void open(JSONObject r){JSONObject p=protocolFor(r);if(p!=null)host.protocol(p);else host.issue(r);}
    public void today(){
        screen("Dzisiaj");String today=LocalDate.now().toString();JSONArray all=store.allReservations();
        int issue=0,returns=0,late=0,missing=0;
        for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r==null||r.optBoolean("settled")||r.optBoolean("returned"))continue;
            if(r.optString("asset_id").isEmpty()||(RentalRules.needsVacuum(r)&&r.optString("vacuum_asset_id").isEmpty()))missing++;
            if(!r.optBoolean("done")&&today.equals(r.optString("start_date")))issue++;
            if(r.optBoolean("done")&&today.equals(r.optString("end_date")))returns++;
            if(r.optBoolean("done")&&!r.optString("end_date").isEmpty()&&r.optString("end_date").compareTo(today)<0)late++;
        }
        text(page,LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM",new Locale("pl","PL"))),14,0xff607086);
        LinearLayout metrics=new LinearLayout(activity);page.addView(metrics);metric(metrics,"Wydania",issue,0xff245b91);metric(metrics,"Zwroty",returns,0xff16755b);metric(metrics,"Po terminie",late,late>0?0xffb53b3b:0xff607086);
        pair(page,"Dostępność",()->calendar(LocalDate.now()),"Aktywne najmy",this::active,false);
        if(admin)button(page,"Wiadomości  ·  "+store.unreadMessagesCount()+" nowych",host::inbox);
        if(missing>0){final int count=missing;button(page,"Przypisz egzemplarze  ·  "+count+" rezerwacji",this::active);}
        text(page,"Do obsługi dzisiaj",18,0xff17263e);
        boolean any=false;
        for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r==null||r.optBoolean("settled")||r.optBoolean("returned"))continue;
            String end=r.optString("end_date"),start=r.optString("start_date");
            if((!r.optBoolean("done")&&today.equals(start))||(r.optBoolean("done")&&!end.isEmpty()&&end.compareTo(today)<=0)){rentalCard(r);any=true;}
        }
        if(!any)text(page,"Brak wydań i zwrotów na dziś.",16,0xff526174);
        for(int i=0;i<store.assets().length();i++){JSONObject a=store.assets().optJSONObject(i);if(a!=null&&serviceDue(a))button(page,"Przegląd: "+a.optString("label"),()->assetDetails(a));}
    }
    public void active(){screen("Aktywne najmy");JSONArray all=store.allReservations();for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r!=null&&!r.optBoolean("settled"))rentalCard(r);}button(page,"Dzisiaj",this::today);}
    String displayDate(String raw){try{return LocalDate.parse(raw).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));}catch(Exception e){return raw;}}
    void rentalCard(JSONObject r){
        LinearLayout c=card(page,r.optString("equipment",r.optString("text")));
        boolean returned=r.optBoolean("returned"),issued=r.optBoolean("done");String end=r.optString("end_date");boolean late=issued&&!returned&&!end.isEmpty()&&end.compareTo(LocalDate.now().toString())<0;
        badge(c,returned?"Do rozliczenia":late?"Po terminie zwrotu":issued?"Wydany":"Do wydania",late?0xffa52e2e:issued?0xff16654e:0xff925400,late?0xffffe5e5:issued?0xffe4f4ed:0xfffff0ce);
        text(c,displayDate(r.optString("start_date"))+" – "+displayDate(end)+(r.optString("time").isEmpty()?"":"  •  "+r.optString("time")),15,0xff245b91);
        String name=r.optString("customer_name",r.optString("client")).trim(),phone=r.optString("phone").trim();
        if(!name.isEmpty()||!phone.isEmpty())text(c,name+(name.isEmpty()||phone.isEmpty()?"":"  •  ")+phone,14,0xff607086);
        text(c,r.optString("asset_id").isEmpty()?"Egzemplarz: do przypisania":"Egzemplarz: "+r.optString("asset_label"),13,r.optString("asset_id").isEmpty()?0xff925400:0xff607086);
        if(RentalRules.needsVacuum(r))text(c,"Odkurzacz w zestawie: "+(r.optString("vacuum_asset_id").isEmpty()?"DO PRZYPISANIA":r.optString("vacuum_asset_label")),13,r.optString("vacuum_asset_id").isEmpty()?0xff925400:0xff607086);
        boolean canCall=phone.matches("[+0-9 ()-]{7,}");
        if(returned&&admin)pair(c,"Rozlicz najem",()->settlement(r),"Więcej opcji",()->rentalMenu(r),true);
        else pair(c,issued?"Protokół / zwrot":"Protokół wydania",()->open(r),canCall?"Zadzwoń":"Więcej opcji",()->{if(canCall){try{activity.startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone.replaceAll("[^+0-9]",""),null)));}catch(Exception e){error(e);}}else rentalMenu(r);},true);
        if(canCall&&!returned)button(c,"Więcej opcji  ›",()->rentalMenu(r));
    }
    void rentalMenu(JSONObject r){ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
        labels.add("Egzemplarz i warunki najmu");actions.add(()->rentalForm(r));
        if(!r.optBoolean("returned")){labels.add("Przedłuż najem");actions.add(()->extend(r));}
        // Rentals without an issue protocol need a safe way to confirm physical return
        // before finance is settled. Never silently release overdue issued equipment.
        if(r.optBoolean("done")&&!r.optBoolean("returned")&&protocolFor(r)==null){
            labels.add("Potwierdź zwrot bez protokołu");
            actions.add(()->new AlertDialog.Builder(activity)
                .setTitle("Potwierdź fizyczny zwrot sprzętu")
                .setMessage("Użyj tej opcji wyłącznie po odebraniu sprzętu od klienta. "+
                    "W przypadku zestawu zwolnione zostaną żyrafa i odkurzacz. "+
                    "Najem pozostanie do rozliczenia.")
                .setNegativeButton("Anuluj",null)
                .setPositiveButton("Sprzęt wrócił",(d,w)->{
                    try{
                        JSONObject updated=copy(r);
                        updated.put("returned",true);
                        updated.put("actual_return",LocalDate.now().toString());
                        updated.put("auto_assign",false);
                        save(updated);
                        active();
                        Toast.makeText(activity,"Zapisano zwrot. Synchronizacja potwierdzi dostępność.",Toast.LENGTH_LONG).show();
                    }catch(Exception ex){error(ex);}
                }).show());
        }
        if(admin){labels.add("Rozlicz najem");actions.add(()->settlement(r));}
        if(r.optBoolean("returned")){labels.add("Zobacz protokół");actions.add(()->open(r));}
        new AlertDialog.Builder(activity).setTitle("Opcje najmu").setItems(labels.toArray(new String[0]),(d,index)->actions.get(index).run()).setNegativeButton("Zamknij",null).show();
    }
    /** Saves the whole existing asset record, including history and its rental group. */
    void setServiceStatus(JSONObject original,boolean disabled,LocalDate weekStart){
        if(!admin){error(new IllegalArgumentException("Tylko administrator może zmieniać dostępność."));return;}
        try{
            JSONObject fresh=original;
            JSONArray current=store.assets();
            for(int i=0;i<current.length();i++){
                JSONObject saved=current.optJSONObject(i);
                if(saved!=null&&saved.optString("id").equals(original.optString("id"))){fresh=saved;break;}
            }
            JSONObject changed=copy(fresh);
            changed.put("in_service",disabled);
            store.enqueue("asset_save",changed);
            store.saveAsset(changed);
            host.sync();
            calendar(weekStart);
            Toast.makeText(activity,disabled?
                "Egzemplarz wyłączony z wynajmu. Synchronizacja potwierdzi zmianę.":
                "Egzemplarz przywrócony do wynajmu. Synchronizacja potwierdzi zmianę.",
                Toast.LENGTH_LONG).show();
        }catch(Exception e){error(e);}
    }
    void confirmServiceChange(JSONObject asset,LocalDate weekStart){
        if(!admin)return;
        boolean disabled=asset.optBoolean("in_service");
        if(disabled){setServiceStatus(asset,false,weekStart);return;}
        int future=0;
        String today=LocalDate.now().toString();
        JSONArray rentals=store.allReservations();
        for(int i=0;i<rentals.length();i++){
            JSONObject r=rentals.optJSONObject(i);
            if(r==null||r.optBoolean("returned")||r.optBoolean("settled"))continue;
            if(asset.optString("id").equals(r.optString("asset_id"))||
                asset.optString("id").equals(r.optString("vacuum_asset_id"))){
                if(r.optBoolean("done")||r.optString("end_date").compareTo(today)>=0)future++;
            }
        }
        String message="Egzemplarz nie będzie przydzielany do nowych rezerwacji aż do ponownego włączenia.";
        if(future>0)message+="\n\nUwaga: ma "+future+" aktywnych lub nadchodzących rezerwacji. Nie zostaną automatycznie anulowane — sprawdź klientów i terminy.";
        new AlertDialog.Builder(activity)
            .setTitle("Wyłączyć "+asset.optString("label")+"?")
            .setMessage(message)
            .setNegativeButton("Anuluj",null)
            .setPositiveButton("Wyłącz z wynajmu",(dialog,which)->setServiceStatus(asset,true,weekStart))
            .show();
    }
    void confirmAssetDelete(JSONObject asset,LocalDate weekStart){
        if(!admin)return;
        String reason=RentalRules.deletionBlocker(asset,store.allReservations(),store.allProtocols());
        if(!reason.isEmpty()){
            AlertDialog.Builder info=new AlertDialog.Builder(activity)
                .setTitle("Nie można usunąć: "+asset.optString("label"))
                .setMessage(reason+"\n\nAby zachować historię wypożyczeń i napraw, możesz wyłączyć ten egzemplarz z wynajmu.")
                .setNegativeButton("Zamknij",null);
            if(!asset.optBoolean("in_service"))info.setPositiveButton("Wyłącz z wynajmu",
                (d,w)->confirmServiceChange(asset,weekStart));
            info.show();
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle("Usunąć egzemplarz?")
            .setMessage(asset.optString("label")+"\n\nTo trwałe usunięcie z listy sprzętu. Nie będzie można cofnąć tej operacji.")
            .setNegativeButton("Anuluj",null)
            .setPositiveButton("Usuń", (d,w)->{
                try{
                    store.deleteUnusedAsset(asset);
                    host.sync();
                    calendar(weekStart);
                    Toast.makeText(activity,"Egzemplarz usunięty z listy. Synchronizacja potwierdzi usunięcie.",Toast.LENGTH_LONG).show();
                }catch(Exception e){error(e);}
            }).show();
    }
    public void calendar(LocalDate start){
        screen("Dostępność egzemplarzy");
        text(page,"Szybko sprawdź terminy, edytuj egzemplarz lub wyłącz go w razie awarii.",13,0xff526174);
        text(page,start+" — "+start.plusDays(6),15,0xff245b91);
        pair(page,"‹ Poprzednie 7 dni",()->calendar(start.minusDays(7)),
            "Następne 7 dni ›",()->calendar(start.plusDays(7)),false);
        if(!start.equals(LocalDate.now()))button(page,"Przejdź do dzisiaj",()->calendar(LocalDate.now()));
        if(admin)button(page,"＋ Dodaj egzemplarz",()->assetForm(null));
        JSONArray assets=store.assets(),rentals=store.allReservations();
        if(assets.length()==0)text(page,"Dodaj rzeczywiste egzemplarze, np. Puzzi 01, Puzzi 02. Następnie przypisz je do aktywnych rezerwacji.",16,0xff526174);
        for(int i=0;i<assets.length();i++){
            JSONObject a=assets.optJSONObject(i);if(a==null)continue;
            boolean disabled=a.optBoolean("in_service");
            LinearLayout c=card(page,a.optString("label"));
            text(c,a.optString("equipment"),12,0xff64748b);
            if(disabled)badge(c,"AWARIA / SERWIS — WYŁĄCZONY",0xffa52e2e,0xffffe5e5);
            else if(serviceDue(a))badge(c,"ZAPLANUJ PRZEGLĄD",0xff925400,0xfffff0ce);
            for(int d=0;d<7;d++){
                String day=start.plusDays(d).toString();
                String reason=RentalRules.assetAvailability(a,day,rentals);
                String status=disabled?"Niedostępny — awaria / serwis":reason.isEmpty()?"Wolny":reason;
                text(c,day+"  •  "+status,13,disabled?0xffa52e2e:reason.isEmpty()?0xff166534:0xff925400);
            }
            if(admin){
                pair(c,"Edytuj",()->assetForm(a),
                    disabled?"Przywróć do wynajmu":"Awaria / wyłącz",
                    ()->confirmServiceChange(a,start),false);
                pair(c,"Historia i serwis",()->assetDetails(a),
                    "Usuń egzemplarz",()->confirmAssetDelete(a,start),false);
            }else button(c,"Historia i serwis  ›",()->assetDetails(a));
        }
    }
    long rentalsCount(JSONObject a){long count=0;JSONArray rs=store.allReservations();for(int i=0;i<rs.length();i++){JSONObject r=rs.optJSONObject(i);if(r!=null&&(a.optString("id").equals(r.optString("asset_id"))||a.optString("id").equals(r.optString("vacuum_asset_id")))&&(r.optBoolean("done")||r.optBoolean("settled")))count++;}return count;}
    boolean serviceDue(JSONObject a){String day=a.optString("next_service");return (!day.isEmpty()&&day.compareTo(LocalDate.now().toString())<=0)||(a.optLong("service_every")>0&&rentalsCount(a)-a.optLong("service_at_count")>=a.optLong("service_every"));}
    void assetDetails(JSONObject a){screen(a.optString("label"));text(page,a.optString("equipment")+(a.optBoolean("in_service")?" • W SERWISIE":" • gotowy do najmu"),16,0xff245b91);text(page,"Wynajmy: "+rentalsCount(a)+(serviceDue(a)?" • PRZEGLĄD DO WYKONANIA":""),16,0xff925400);
        BigDecimal income=BigDecimal.ZERO;JSONArray rs=store.allReservations();for(int i=0;i<rs.length();i++){JSONObject r=rs.optJSONObject(i);if(r!=null&&(a.optString("id").equals(r.optString("asset_id"))||a.optString("id").equals(r.optString("vacuum_asset_id")))){if(r.optBoolean("settled")&&a.optString("id").equals(r.optString("asset_id")))income=income.add(RentalRules.money(r.optString("income","0")));text(page,r.optString("start_date")+" → "+r.optString("end_date")+" • "+r.optString("customer_name"),14,0xff526174);}}
        if(admin){text(page,"Przychód rozliczony (bez podwójnego liczenia kwoty zestawu): "+income+" zł",16,0xff166534);button(page,"Edytuj / ustaw serwis",()->assetForm(a));button(page,"Dodaj wykonaną naprawę / przegląd",()->repair(a));}
        JSONArray repairs=a.optJSONArray("repairs");if(repairs!=null)for(int i=0;i<repairs.length();i++){JSONObject r=repairs.optJSONObject(i);if(r!=null)text(page,r.optString("date")+" • "+r.optString("note")+(admin?" • "+r.optString("cost")+" zł":""),14,0xff526174);}
    }
    void assetForm(JSONObject old){if(!admin)return;screen(old==null?"Nowy egzemplarz":"Ustawienia egzemplarza");JSONArray eq=store.equipment();ArrayList<String> names=new ArrayList<>();for(int i=0;i<eq.length();i++)names.add(eq.optString(i));if(names.isEmpty()){text(page,"Najpierw odśwież listę sprzętu.",16,0xff925400);return;}Spinner model=new Spinner(activity);model.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,names));page.addView(model);if(old!=null){model.setSelection(Math.max(0,names.indexOf(old.optString("equipment"))));model.setEnabled(false);}
        text(page,"Pula fizycznych egzemplarzy – wybierz właściwą grupę, aby wynajem osobno i w zestawie korzystał z tych samych sztuk.",13,0xff526174);
        String[] groups={"Pozostały sprzęt","Żyrafa","Odkurzacz przemysłowy"};
        Spinner group=new Spinner(activity);group.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,groups));
        String initialKind=old==null?RentalRules.kind(names.get(0)):RentalRules.assetKind(old);
        group.setSelection("giraffe".equals(initialKind)?1:"vacuum".equals(initialKind)?2:0);
        if(old==null)model.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                String value=RentalRules.kind(names.get(position));group.setSelection("giraffe".equals(value)?1:"vacuum".equals(value)?2:0);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        page.addView(group);
        EditText label=field(page,"Numer / nazwa egzemplarza",old==null?"":old.optString("label"),false);CheckBox service=new CheckBox(activity);service.setText("Wyłącz z wynajmu — w serwisie");service.setChecked(old!=null&&old.optBoolean("in_service"));page.addView(service);
        EditText next=field(page,"Następny przegląd (opcjonalnie)",old==null?"":old.optString("next_service"),false);date(next);button(page,"Wyczyść datę przeglądu",()->next.setText(""));EditText every=field(page,"Przegląd co ile wynajmów (0 = wyłączony)",old==null?"0":old.optString("service_every","0"),true);
        button(page,"Zapisz egzemplarz",()->{try{String name=label.getText().toString().trim();if(name.isEmpty())throw new IllegalArgumentException("Wpisz numer egzemplarza");if(name.length()>100)throw new IllegalArgumentException("Nazwa może mieć maksymalnie 100 znaków.");if(store.isAssetLabelTaken(name,old==null?"":old.optString("id")))throw new IllegalArgumentException("Ta nazwa jest już zajęta przez inny egzemplarz.");String serviceDate=next.getText().toString().trim();if(!serviceDate.isEmpty())java.time.LocalDate.parse(serviceDate);JSONObject a=old==null?new JSONObject():copy(old);a.put("id",old==null?UUID.randomUUID().toString():old.optString("id"));a.put("equipment",model.getSelectedItem().toString());a.put("asset_kind",group.getSelectedItemPosition()==1?"giraffe":group.getSelectedItemPosition()==2?"vacuum":"standard");a.put("label",name);a.put("in_service",service.isChecked());a.put("next_service",next.getText().toString());int n=Integer.parseInt(every.getText().toString());if(n<0||n>10000)throw new IllegalArgumentException("Nieprawidłowy interwał");a.put("service_every",n);store.enqueue("asset_save",a);store.saveAsset(a);host.sync();assetDetails(a);}catch(Exception e){error(e);}});
    }
    void repair(JSONObject old){screen("Wykonany serwis • "+old.optString("label"));EditText note=field(page,"Co wykonano?","",false),cost=field(page,"Koszt naprawy (zł)","0",true),next=field(page,"Następny przegląd (opcjonalnie)","",false);date(next);text(page,"Koszt jest zapisywany również w pozostałych kosztach. Nie dodawaj go drugi raz ręcznie.",13,0xff526174);
        button(page,"Zapisz naprawę",()->{try{if(note.getText().toString().trim().isEmpty())throw new IllegalArgumentException("Opisz wykonane prace");JSONObject a=copy(old),r=new JSONObject();String id=UUID.randomUUID().toString();r.put("id",id);r.put("date",LocalDate.now().toString());r.put("note",note.getText().toString());r.put("cost",RentalRules.money(cost.getText().toString()).toPlainString());JSONArray repairs=a.optJSONArray("repairs");if(repairs==null)repairs=new JSONArray();repairs.put(r);a.put("repairs",repairs);a.put("service_at_count",rentalsCount(a));a.put("next_service",next.getText().toString());a.put("in_service",false);store.enqueue("asset_save",a);store.saveAsset(a);
            JSONObject expense=new JSONObject();expense.put("id",id);expense.put("date",r.getString("date"));expense.put("month",r.getString("date").substring(0,7));expense.put("name","Serwis: "+a.optString("label"));expense.put("amount",r.getString("cost"));expense.put("type","expense");expense.put("equipment_name",a.optString("equipment"));expense.put("note",r.getString("note"));
             // The API accepts only positive costs. A free service remains in maintenance history.
             if(RentalRules.money(r.getString("cost")).signum()>0){store.enqueue("expense_save",expense);store.saveExpense(expense);}host.sync();assetDetails(a);}catch(Exception e){error(e);}});
    }
    void rentalForm(JSONObject old){
        screen("Egzemplarze i warunki najmu");
        JSONArray assets=store.assets();
        String role=RentalRules.kind(old.optString("equipment"));
        ArrayList<JSONObject> primary=new ArrayList<>(),vacuums=new ArrayList<>();
        ArrayList<String> primaryNames=new ArrayList<>(),vacNames=new ArrayList<>();
        primary.add(null);primaryNames.add("Bez przypisania");
        vacuums.add(null);vacNames.add("Bez przypisania");
        int selected=0,vacSelected=0;
        for(int i=0;i<assets.length();i++){
            JSONObject a=assets.optJSONObject(i);if(a==null)continue;
            String kind=RentalRules.assetKind(a);
            boolean eligible="standard".equals(role)?old.optString("equipment").equals(a.optString("equipment")):role.equals(kind);
            if(eligible){primary.add(a);primaryNames.add(a.optString("label")+(a.optBoolean("in_service")?" • serwis":""));
                if(a.optString("id").equals(old.optString("asset_id")))selected=primary.size()-1;}
            if("vacuum".equals(kind)){vacuums.add(a);vacNames.add(a.optString("label")+(a.optBoolean("in_service")?" • serwis":""));
                if(a.optString("id").equals(old.optString("vacuum_asset_id")))vacSelected=vacuums.size()-1;}
        }
        text(page,"Egzemplarz główny",14,0xff17263e);
        Spinner pick=new Spinner(activity);
        pick.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,primaryNames));
        pick.setSelection(selected);if(old.optBoolean("done")&&!old.optString("asset_id").isEmpty())pick.setEnabled(false);
        page.addView(pick);
        boolean giraffe="giraffe".equals(role);
        CheckBox withVacuum=new CheckBox(activity);
        withVacuum.setText("Zestaw z odkurzaczem przemysłowym");
        withVacuum.setChecked(RentalRules.needsVacuum(old));
        if(old.optBoolean("done")||RentalRules.bundleOffer(old.optString("equipment")))withVacuum.setEnabled(false);
        if(giraffe)page.addView(withVacuum);
        LinearLayout vacBox=new LinearLayout(activity);vacBox.setOrientation(1);
        text(vacBox,"Odkurzacz do zestawu",14,0xff17263e);
        Spinner vacuumPick=new Spinner(activity);
        vacuumPick.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,vacNames));
        vacuumPick.setSelection(vacSelected);
        if(old.optBoolean("done")&&!old.optString("vacuum_asset_id").isEmpty())vacuumPick.setEnabled(false);
        vacBox.addView(vacuumPick);page.addView(vacBox);
        vacBox.setVisibility(giraffe&&withVacuum.isChecked()?View.VISIBLE:View.GONE);
        withVacuum.setOnCheckedChangeListener((b,checked)->vacBox.setVisibility(checked?View.VISIBLE:View.GONE));
        text(page,"Wybranie zestawu zajmie dwie konkretne sztuki. Rezerwacja osobnego odkurzacza widzi te same zajętości.",13,0xff526174);
        EditText rate=field(page,"Stawka za dobę (zł)",old.optString("daily_rate","0"),true),
            paid=field(page,"Wpłata na poczet najmu (bez kaucji)",old.optString("paid","0"),true),
            deposit=field(page,"Pobrana kaucja (zł)",old.optString("deposit","0"),true);
        rate.setEnabled(admin);paid.setEnabled(admin);deposit.setEnabled(admin);
        button(page,"Zapisz egzemplarze i warunki",()->{try{
            JSONObject r=copy(old),a=primary.get(pick.getSelectedItemPosition());
            boolean set=giraffe&&withVacuum.isChecked();
            if(set&&a==null)throw new IllegalArgumentException("Wybierz konkretną żyrafę do zestawu.");
            r.put("asset_id",a==null?"":a.optString("id"));r.put("asset_label",a==null?"":a.optString("label"));
            r.put("needs_vacuum",set);r.put("auto_assign",false);
            JSONObject vac=set?vacuums.get(vacuumPick.getSelectedItemPosition()):null;
            if(set&&vac==null)throw new IllegalArgumentException("Wybierz odkurzacz do zestawu.");
            r.put("vacuum_asset_id",vac==null?"":vac.optString("id"));
            r.put("vacuum_asset_label",vac==null?"":vac.optString("label"));
            if(admin){r.put("daily_rate",RentalRules.money(rate.getText().toString()).toPlainString());
                r.put("paid",RentalRules.money(paid.getText().toString()).toPlainString());
                r.put("deposit",RentalRules.money(deposit.getText().toString()).toPlainString());}
            save(r);active();
        }catch(Exception e){error(e);}});
    }
    void extend(JSONObject old){screen("Przedłuż najem");text(page,old.optString("equipment")+" • obecny zwrot: "+old.optString("end_date"),16,0xff526174);EditText end=field(page,"Nowy termin zwrotu",old.optString("end_date"),false);date(end);
        button(page,"Sprawdź termin i przedłuż",()->{try{LocalDate to=LocalDate.parse(end.getText());if(!to.isAfter(LocalDate.parse(old.optString("end_date"))))throw new IllegalArgumentException("Wybierz późniejszy termin");JSONObject r=copy(old);r.put("end_date",to.toString());r.put("end_month",to.toString().substring(0,7));r.put("end_day",to.getDayOfMonth());r.put("auto_assign",false);r.put("text",r.optString("start_date")+" – "+to+" • "+r.optString("equipment")+" • "+r.optString("phone"));if(r.optString("asset_id").isEmpty())throw new IllegalArgumentException("Najpierw przypisz egzemplarz, aby sprawdzić dostępność");String conflict=RentalRules.conflict(r,store.allReservations(),store.assets());if(!conflict.isEmpty())throw new IllegalArgumentException(conflict);BigDecimal total=RentalRules.money(r.optString("daily_rate","0")).multiply(BigDecimal.valueOf(RentalRules.days(r.optString("start_date"),to.toString())));new AlertDialog.Builder(activity).setTitle("Potwierdź przedłużenie").setMessage("Nowy zwrot: "+to+(admin?"\nSzacowany najem: "+total+" zł (bez materiałów i rabatów)":"")+"\nTermin zostanie potwierdzony podczas synchronizacji.").setNegativeButton("Anuluj",null).setPositiveButton("Przedłuż",(d,w)->{try{save(r);active();}catch(Exception e){error(e);}}).show();}catch(Exception e){error(e);}});
    }
    public void settlement(JSONObject old){if(!admin)return;screen("Rozliczenie najmu");EditText end=field(page,"Faktyczna data zwrotu",old.optString("actual_return",LocalDate.now().toString()),false);date(end);text(page,"Doby: różnica dat, minimum 1. Sprawdź ustalenia godzinowe; liczbę dób możesz skorygować przed zatwierdzeniem.",13,0xff526174);
        String suggested="1";try{suggested=""+RentalRules.days(old.optString("start_date"),end.getText().toString());}catch(Exception ignored){}
        EditText days=field(page,"Liczba dób",suggested,true);button(page,"Przelicz doby z dat",()->{try{days.setText(""+RentalRules.days(old.optString("start_date"),end.getText().toString()));}catch(Exception e){error(e);}});
        EditText rate=field(page,"Stawka za dobę",old.optString("daily_rate","0"),true);
        // Optional, expandable addon section: no new screen or extra steps for ordinary rentals.
        LinearLayout addon=card(page,"Dodatki do najmu (opcjonalne)");
        text(addon,"Proszek, papier i zużycie tarczy. Kwoty zsumują się automatycznie.",12,0xff64748b);
        LinearLayout addonFields=new LinearLayout(activity);addonFields.setOrientation(1);addonFields.setVisibility(View.GONE);
        addon.addView(addonFields);
        EditText powder=field(addonFields,"Proszek / chemia piorąca (zł)","0",true),
                paper=field(addonFields,"Papier ścierny (zł)","0",true),
                disc=field(addonFields,"Zużycie tarczy (zł)","0",true),
                other=field(addonFields,"Pozostałe materiały i dopłaty (zł)","0",true);
        // New products can be entered as needed; previous names are suggested automatically.
        ArrayList<String> suggestions=new ArrayList<>();
        JSONArray history=store.allReservations();
        for(int i=0;i<history.length();i++){
            JSONObject saved=history.optJSONObject(i);
            JSONArray lines=saved==null?null:saved.optJSONObject("settlement")==null?null:saved.optJSONObject("settlement").optJSONArray("add_custom");
            if(lines==null)continue;
            for(int j=0;j<lines.length();j++){
                JSONObject item=lines.optJSONObject(j);
                String name=item==null?"":item.optString("name").trim();
                if(!name.isEmpty()&&!suggestions.contains(name))suggestions.add(name);
            }
        }
        Collections.sort(suggestions,String.CASE_INSENSITIVE_ORDER);
        LinearLayout customList=new LinearLayout(activity);customList.setOrientation(1);addonFields.addView(customList);
        ArrayList<AutoCompleteTextView> customNames=new ArrayList<>();
        ArrayList<EditText> customPrices=new ArrayList<>();
        button(addonFields,"＋ Dodaj własny produkt lub dopłatę",()->{
            if(customNames.size()>=20){error(new IllegalArgumentException("Maksymalnie 20 dodatków w jednym najmie."));return;}
            LinearLayout entry=card(customList,"Nowy dodatek • "+(customNames.size()+1));
            text(entry,"Nazwa dodatku",13,0xff526174);
            AutoCompleteTextView name=new AutoCompleteTextView(activity);
            name.setSingleLine(true);name.setHint("np. Worek do odkurzacza");
            name.setThreshold(1);
            ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_dropdown_item_1line,suggestions);
            name.setAdapter(adapter);
            entry.addView(name,new LinearLayout.LayoutParams(-1,dp(48)));
            EditText price=field(entry,"Kwota za dodatek (zł)","0",true);
            customNames.add(name);customPrices.add(price);
            button(entry,"Usuń ten dodatek",()->{
                customNames.remove(name);customPrices.remove(price);customList.removeView(entry);
            });
            name.requestFocus();
        });
        Button showAddon=action("＋ Dodaj dodatki",false,()->{});
        addon.addView(showAddon,new LinearLayout.LayoutParams(-1,dp(48)));
        showAddon.setOnClickListener(v->{
            boolean show=addonFields.getVisibility()!=View.VISIBLE;
            addonFields.setVisibility(show?View.VISIBLE:View.GONE);
            showAddon.setText(show?"− Zwiń dodatki":"＋ Dodaj dodatki");
        });
        EditText discount=field(page,"Rabat","0",true),paid=field(page,"Już zapłacono za najem (bez kaucji)",old.optString("paid","0"),true),deposit=field(page,"Pobrana kaucja",old.optString("deposit","0"),true),retain=field(page,"Potrącenie z kaucji na należność","0",true),loss=field(page,"Koszt / strata wypożyczalni","0",true),note=field(page,"Opis dodatkowych ustaleń / strat","",false);
        button(page,"Podsumuj i zatwierdź",()->{try{LocalDate.parse(end.getText());RentalRules.days(old.optString("start_date"),end.getText().toString());int n=Integer.parseInt(days.getText().toString());if(n<1||n>10000)throw new IllegalArgumentException("Sprawdź liczbę dób");BigDecimal rent=RentalRules.money(rate.getText().toString()).multiply(BigDecimal.valueOf(n));BigDecimal powderTotal=RentalRules.money(powder.getText().toString()),paperTotal=RentalRules.money(paper.getText().toString()),discTotal=RentalRules.money(disc.getText().toString()),otherTotal=RentalRules.money(other.getText().toString());
                JSONArray custom=new JSONArray();BigDecimal customTotal=BigDecimal.ZERO;
                for(int i=0;i<customNames.size();i++){
                    String label=customNames.get(i).getText().toString().trim().replaceAll("\\s+"," ");
                    BigDecimal amount=RentalRules.money(customPrices.get(i).getText().toString());
                    if(label.isEmpty()||label.length()>100||amount.signum()<=0)
                        throw new IllegalArgumentException("Każdy własny dodatek musi mieć nazwę i kwotę większą od 0 zł.");
                    JSONObject entry=new JSONObject().put("name",label).put("amount",amount.toPlainString());
                    custom.put(entry);customTotal=customTotal.add(amount);
                }
                BigDecimal extras=powderTotal.add(paperTotal).add(discTotal).add(otherTotal).add(customTotal);
                BigDecimal total=rent.add(extras).subtract(RentalRules.money(discount.getText().toString()));BigDecimal p=RentalRules.money(paid.getText().toString()),dep=RentalRules.money(deposit.getText().toString()),keep=RentalRules.money(retain.getText().toString()),cost=RentalRules.money(loss.getText().toString());if(total.signum()<0||keep.compareTo(dep)>0||keep.compareTo(total.subtract(p).max(BigDecimal.ZERO))>0)throw new IllegalArgumentException("Rabat lub potrącenie przekracza należność / kaucję");BigDecimal due=total.subtract(p).subtract(keep),refund=dep.subtract(keep);String summary="Należność: "+total+" zł\nW tym dodatki (przed rabatem): "+extras+" zł\n"+(due.signum()<0?"Nadpłata do zwrotu: "+due.abs():"Do dopłaty: "+due)+" zł\nKaucja do oddania: "+refund+" zł\nZatwierdź po przyjęciu sprzętu i rozliczeniu płatności.";
            JSONObject r=copy(old);r.put("settled",true);r.put("returned",true);r.put("actual_return",end.getText().toString());r.put("income",total.toPlainString());r.put("loss",cost.toPlainString());r.put("loss_note",note.getText().toString());r.put("settled_at",LocalDate.now()+" 12:00:00");JSONObject details=new JSONObject();details.put("days",n);details.put("rate",rate.getText().toString());details.put("extra",extras.toPlainString());details.put("add_powder",powderTotal.toPlainString());details.put("add_paper",paperTotal.toPlainString());details.put("add_disc",discTotal.toPlainString());details.put("add_other",otherTotal.toPlainString());details.put("add_custom",custom);details.put("discount",discount.getText().toString());details.put("paid",p.toPlainString());details.put("deposit",dep.toPlainString());details.put("retained",keep.toPlainString());details.put("due",due.toPlainString());details.put("deposit_refund",refund.toPlainString());r.put("settlement",details);
            new AlertDialog.Builder(activity).setTitle("Potwierdź rozliczenie").setMessage(summary).setNegativeButton("Popraw",null).setPositiveButton("Sprzęt i płatność rozliczone",(d,w)->{store.saveRentalOperation(r,"reservation_settle");host.sync();active();}).show();
        }catch(Exception e){error(e);}});
    }
}
