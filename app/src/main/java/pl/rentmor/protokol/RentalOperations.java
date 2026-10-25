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
    void button(LinearLayout box,String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());box.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    void screen(String title){LinearLayout outer=new LinearLayout(activity);outer.setOrientation(1);outer.setPadding(dp(14),dp(30),dp(14),dp(20));outer.setBackgroundColor(0xfff4f6fa);button(outer,"‹ Panel główny",host::home);ScrollView scroll=new ScrollView(activity);page=new LinearLayout(activity);page.setOrientation(1);scroll.addView(page);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));activity.setContentView(outer);text(page,title,26,0xff17263e);}
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
            if(r.optString("asset_id").isEmpty())missing++;
            if(!r.optBoolean("done")&&today.equals(r.optString("start_date")))issue++;
            if(r.optBoolean("done")&&today.equals(r.optString("end_date")))returns++;
            if(r.optBoolean("done")&&!r.optString("end_date").isEmpty()&&r.optString("end_date").compareTo(today)<0)late++;
        }
        text(page,"Wydania: "+issue+"   •   Zwroty: "+returns+"   •   Po terminie: "+late,16,0xff245b91);
        if(admin)button(page,"Wiadomości • nieprzeczytane: "+store.unreadMessagesCount(),host::inbox);
        button(page,"Kalendarz dostępności i egzemplarze",()->calendar(LocalDate.now()));
        button(page,"Wszystkie aktywne najmy • uzupełnij egzemplarze ("+missing+")",this::active);
        boolean any=false;
        for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r==null||r.optBoolean("settled")||r.optBoolean("returned"))continue;
            String end=r.optString("end_date"),start=r.optString("start_date");
            if((!r.optBoolean("done")&&today.equals(start))||(r.optBoolean("done")&&!end.isEmpty()&&end.compareTo(today)<=0)){rentalCard(r);any=true;}
        }
        if(!any)text(page,"Brak wydań i zwrotów na dziś.",16,0xff526174);
        for(int i=0;i<store.assets().length();i++){JSONObject a=store.assets().optJSONObject(i);if(a!=null&&serviceDue(a))button(page,"Przegląd: "+a.optString("label"),()->assetDetails(a));}
    }
    public void active(){screen("Aktywne najmy");JSONArray all=store.allReservations();for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(r!=null&&!r.optBoolean("settled"))rentalCard(r);}button(page,"Dzisiaj",this::today);}
    void rentalCard(JSONObject r){LinearLayout c=card(page,r.optString("equipment",r.optString("text")));text(c,r.optString("start_date")+" → "+r.optString("end_date")+"  "+r.optString("time"),14,0xff245b91);text(c,r.optString("customer_name",r.optString("client"))+" • "+r.optString("phone"),14,0xff526174);text(c,r.optBoolean("returned")?"Zwrócony — oczekuje na rozliczenie":r.optBoolean("done")?"Wydany":"Do wydania",14,0xff925400);
        text(c,"Egzemplarz: "+r.optString("asset_label","nieprzypisany"),14,0xff526174);
        String phone=r.optString("phone");if(phone.matches("[+0-9 ()-]{7,}"))button(c,"Zadzwoń",()->{try{activity.startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",phone.replaceAll("[^+0-9]",""),null)));}catch(Exception e){error(e);}});
        button(c,"Egzemplarz i warunki najmu",()->rentalForm(r));button(c,"Protokół wydania / zwrotu",()->open(r));
        if(!r.optBoolean("returned"))button(c,"Przedłuż najem",()->extend(r));
        if(admin)button(c,"Rozlicz najem",()->settlement(r));
    }
    public void calendar(LocalDate start){screen("Dostępność egzemplarzy");text(page,"Rezerwacje blokują cały dzień odbioru i zwrotu. Brak przypisanej sztuki oznacza termin do sprawdzenia.",13,0xff526174);
        button(page,"‹ Poprzednie 7 dni",()->calendar(start.minusDays(7)));button(page,start+" • następne 7 dni ›",()->calendar(start.plusDays(7)));
        if(admin)button(page,"＋ Dodaj egzemplarz",()->assetForm(null));
        JSONArray assets=store.assets(),rentals=store.allReservations();if(assets.length()==0)text(page,"Dodaj rzeczywiste egzemplarze, np. Puzzi 01, Puzzi 02. Następnie przypisz je do aktywnych rezerwacji.",16,0xff526174);
        for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a==null)continue;LinearLayout c=card(page,a.optString("label")+" • "+a.optString("equipment"));
            for(int d=0;d<7;d++){String day=start.plusDays(d).toString();JSONObject probe=new JSONObject();try{probe.put("asset_id",a.optString("id"));probe.put("equipment",a.optString("equipment"));probe.put("start_date",day);probe.put("end_date",day);}catch(Exception ignored){}String reason=RentalRules.conflict(probe,rentals,assets);if(reason.isEmpty())for(int j=0;j<rentals.length();j++){JSONObject legacy=rentals.optJSONObject(j);if(legacy!=null&&!legacy.optBoolean("settled")&&!legacy.optBoolean("returned")&&legacy.optString("asset_id").isEmpty()&&a.optString("equipment").equals(legacy.optString("equipment"))){try{if(RentalRules.overlaps(day,day,legacy.optString("start_date"),legacy.optString("end_date")))reason="Sprawdź — rezerwacja modelu bez przypisanej sztuki";}catch(Exception e){reason="Sprawdź starsze rezerwacje tego modelu";}}}text(c,day+"  •  "+(reason.isEmpty()?"Wolny":reason),13,reason.isEmpty()?0xff166534:0xff925400);}
            button(c,"Historia i serwis",()->assetDetails(a));
        }
    }
    long rentalsCount(JSONObject a){long count=0;JSONArray rs=store.allReservations();for(int i=0;i<rs.length();i++){JSONObject r=rs.optJSONObject(i);if(r!=null&&a.optString("id").equals(r.optString("asset_id"))&&(r.optBoolean("done")||r.optBoolean("settled")))count++;}return count;}
    boolean serviceDue(JSONObject a){String day=a.optString("next_service");return (!day.isEmpty()&&day.compareTo(LocalDate.now().toString())<=0)||(a.optLong("service_every")>0&&rentalsCount(a)-a.optLong("service_at_count")>=a.optLong("service_every"));}
    void assetDetails(JSONObject a){screen(a.optString("label"));text(page,a.optString("equipment")+(a.optBoolean("in_service")?" • W SERWISIE":" • gotowy do najmu"),16,0xff245b91);text(page,"Wynajmy: "+rentalsCount(a)+(serviceDue(a)?" • PRZEGLĄD DO WYKONANIA":""),16,0xff925400);
        BigDecimal income=BigDecimal.ZERO;JSONArray rs=store.allReservations();for(int i=0;i<rs.length();i++){JSONObject r=rs.optJSONObject(i);if(r!=null&&a.optString("id").equals(r.optString("asset_id"))){if(r.optBoolean("settled"))income=income.add(RentalRules.money(r.optString("income","0")));text(page,r.optString("start_date")+" → "+r.optString("end_date")+" • "+r.optString("customer_name"),14,0xff526174);}}
        if(admin){text(page,"Przychód rozliczony: "+income+" zł",18,0xff166534);button(page,"Edytuj / ustaw serwis",()->assetForm(a));button(page,"Dodaj wykonaną naprawę / przegląd",()->repair(a));}
        JSONArray repairs=a.optJSONArray("repairs");if(repairs!=null)for(int i=0;i<repairs.length();i++){JSONObject r=repairs.optJSONObject(i);if(r!=null)text(page,r.optString("date")+" • "+r.optString("note")+(admin?" • "+r.optString("cost")+" zł":""),14,0xff526174);}
    }
    void assetForm(JSONObject old){if(!admin)return;screen(old==null?"Nowy egzemplarz":"Ustawienia egzemplarza");JSONArray eq=store.equipment();ArrayList<String> names=new ArrayList<>();for(int i=0;i<eq.length();i++)names.add(eq.optString(i));if(names.isEmpty()){text(page,"Najpierw odśwież listę sprzętu.",16,0xff925400);return;}Spinner model=new Spinner(activity);model.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,names));page.addView(model);if(old!=null){model.setSelection(Math.max(0,names.indexOf(old.optString("equipment"))));model.setEnabled(false);}
        EditText label=field(page,"Numer / nazwa egzemplarza",old==null?"":old.optString("label"),false);CheckBox service=new CheckBox(activity);service.setText("Wyłącz z wynajmu — w serwisie");service.setChecked(old!=null&&old.optBoolean("in_service"));page.addView(service);
        EditText next=field(page,"Następny przegląd (opcjonalnie)",old==null?"":old.optString("next_service"),false);date(next);button(page,"Wyczyść datę przeglądu",()->next.setText(""));EditText every=field(page,"Przegląd co ile wynajmów (0 = wyłączony)",old==null?"0":old.optString("service_every","0"),true);
        button(page,"Zapisz egzemplarz",()->{try{String name=label.getText().toString().trim();if(name.isEmpty())throw new IllegalArgumentException("Wpisz numer egzemplarza");JSONObject a=old==null?new JSONObject():copy(old);a.put("id",old==null?UUID.randomUUID().toString():old.optString("id"));a.put("equipment",model.getSelectedItem().toString());a.put("label",name);a.put("in_service",service.isChecked());a.put("next_service",next.getText().toString());int n=Integer.parseInt(every.getText().toString());if(n<0||n>10000)throw new IllegalArgumentException("Nieprawidłowy interwał");a.put("service_every",n);store.enqueue("asset_save",a);store.saveAsset(a);host.sync();assetDetails(a);}catch(Exception e){error(e);}});
    }
    void repair(JSONObject old){screen("Wykonany serwis • "+old.optString("label"));EditText note=field(page,"Co wykonano?","",false),cost=field(page,"Koszt naprawy (zł)","0",true),next=field(page,"Następny przegląd (opcjonalnie)","",false);date(next);text(page,"Koszt jest zapisywany również w pozostałych kosztach. Nie dodawaj go drugi raz ręcznie.",13,0xff526174);
        button(page,"Zapisz naprawę",()->{try{if(note.getText().toString().trim().isEmpty())throw new IllegalArgumentException("Opisz wykonane prace");JSONObject a=copy(old),r=new JSONObject();String id=UUID.randomUUID().toString();r.put("id",id);r.put("date",LocalDate.now().toString());r.put("note",note.getText().toString());r.put("cost",RentalRules.money(cost.getText().toString()).toPlainString());JSONArray repairs=a.optJSONArray("repairs");if(repairs==null)repairs=new JSONArray();repairs.put(r);a.put("repairs",repairs);a.put("service_at_count",rentalsCount(a));a.put("next_service",next.getText().toString());a.put("in_service",false);store.enqueue("asset_save",a);store.saveAsset(a);
            JSONObject expense=new JSONObject();expense.put("id",id);expense.put("date",r.getString("date"));expense.put("month",r.getString("date").substring(0,7));expense.put("name","Serwis: "+a.optString("label"));expense.put("amount",r.getString("cost"));expense.put("type","expense");expense.put("note",r.getString("note"));store.enqueue("expense_save",expense);store.saveExpense(expense);host.sync();assetDetails(a);}catch(Exception e){error(e);}});
    }
    void rentalForm(JSONObject old){screen("Egzemplarz i warunki najmu");ArrayList<JSONObject> items=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();labels.add("Bez przypisania");items.add(null);JSONArray assets=store.assets();int selected=0;for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a!=null&&a.optString("equipment").equals(old.optString("equipment"))){items.add(a);labels.add(a.optString("label")+(a.optBoolean("in_service")?" • serwis":""));if(a.optString("id").equals(old.optString("asset_id")))selected=items.size()-1;}}
        text(page,"Przypisz także pozostałe rezerwacje tego modelu. Terminy bez numeru egzemplarza wymagają ręcznego sprawdzenia.",13,0xff925400);
        Spinner pick=new Spinner(activity);pick.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,labels));pick.setSelection(selected);if(old.optBoolean("done")&&!old.optString("asset_id").isEmpty())pick.setEnabled(false);page.addView(pick);
        EditText rate=field(page,"Stawka za dobę (zł)",old.optString("daily_rate","0"),true),paid=field(page,"Wpłata na poczet najmu (bez kaucji)",old.optString("paid","0"),true),deposit=field(page,"Pobrana kaucja (zł)",old.optString("deposit","0"),true);rate.setEnabled(admin);paid.setEnabled(admin);deposit.setEnabled(admin);
        button(page,"Zapisz",()->{try{JSONObject r=copy(old),a=items.get(pick.getSelectedItemPosition());r.put("asset_id",a==null?"":a.optString("id"));r.put("asset_label",a==null?"nieprzypisany":a.optString("label"));if(admin){r.put("daily_rate",RentalRules.money(rate.getText().toString()).toPlainString());r.put("paid",RentalRules.money(paid.getText().toString()).toPlainString());r.put("deposit",RentalRules.money(deposit.getText().toString()).toPlainString());}save(r);active();}catch(Exception e){error(e);}});
    }
    void extend(JSONObject old){screen("Przedłuż najem");text(page,old.optString("equipment")+" • obecny zwrot: "+old.optString("end_date"),16,0xff526174);EditText end=field(page,"Nowy termin zwrotu",old.optString("end_date"),false);date(end);
        button(page,"Sprawdź termin i przedłuż",()->{try{LocalDate to=LocalDate.parse(end.getText());if(!to.isAfter(LocalDate.parse(old.optString("end_date"))))throw new IllegalArgumentException("Wybierz późniejszy termin");JSONObject r=copy(old);r.put("end_date",to.toString());r.put("end_month",to.toString().substring(0,7));r.put("end_day",to.getDayOfMonth());r.put("text",r.optString("start_date")+" – "+to+" • "+r.optString("equipment")+" • "+r.optString("phone"));if(r.optString("asset_id").isEmpty())throw new IllegalArgumentException("Najpierw przypisz egzemplarz, aby sprawdzić dostępność");String conflict=RentalRules.conflict(r,store.allReservations(),store.assets());if(!conflict.isEmpty())throw new IllegalArgumentException(conflict);BigDecimal total=RentalRules.money(r.optString("daily_rate","0")).multiply(BigDecimal.valueOf(RentalRules.days(r.optString("start_date"),to.toString())));new AlertDialog.Builder(activity).setTitle("Potwierdź przedłużenie").setMessage("Nowy zwrot: "+to+(admin?"\nSzacowany najem: "+total+" zł (bez materiałów i rabatów)":"")+"\nTermin zostanie potwierdzony podczas synchronizacji.").setNegativeButton("Anuluj",null).setPositiveButton("Przedłuż",(d,w)->{try{save(r);active();}catch(Exception e){error(e);}}).show();}catch(Exception e){error(e);}});
    }
    public void settlement(JSONObject old){if(!admin)return;screen("Rozliczenie najmu");EditText end=field(page,"Faktyczna data zwrotu",old.optString("actual_return",LocalDate.now().toString()),false);date(end);text(page,"Doby: różnica dat, minimum 1. Sprawdź ustalenia godzinowe; liczbę dób możesz skorygować przed zatwierdzeniem.",13,0xff526174);
        String suggested="1";try{suggested=""+RentalRules.days(old.optString("start_date"),end.getText().toString());}catch(Exception ignored){}
        EditText days=field(page,"Liczba dób",suggested,true);button(page,"Przelicz doby z dat",()->{try{days.setText(""+RentalRules.days(old.optString("start_date"),end.getText().toString()));}catch(Exception e){error(e);}});
        EditText rate=field(page,"Stawka za dobę",old.optString("daily_rate","0"),true),extra=field(page,"Materiały / zużycie tarczy / inne dopłaty","0",true),discount=field(page,"Rabat","0",true),paid=field(page,"Już zapłacono za najem (bez kaucji)",old.optString("paid","0"),true),deposit=field(page,"Pobrana kaucja",old.optString("deposit","0"),true),retain=field(page,"Potrącenie z kaucji na należność","0",true),loss=field(page,"Koszt / strata wypożyczalni","0",true),note=field(page,"Opis materiałów, zużycia, potrącenia lub straty","",false);
        button(page,"Podsumuj i zatwierdź",()->{try{LocalDate.parse(end.getText());RentalRules.days(old.optString("start_date"),end.getText().toString());int n=Integer.parseInt(days.getText().toString());if(n<1||n>10000)throw new IllegalArgumentException("Sprawdź liczbę dób");BigDecimal rent=RentalRules.money(rate.getText().toString()).multiply(BigDecimal.valueOf(n));BigDecimal total=rent.add(RentalRules.money(extra.getText().toString())).subtract(RentalRules.money(discount.getText().toString()));BigDecimal p=RentalRules.money(paid.getText().toString()),dep=RentalRules.money(deposit.getText().toString()),keep=RentalRules.money(retain.getText().toString()),cost=RentalRules.money(loss.getText().toString());if(total.signum()<0||keep.compareTo(dep)>0||keep.compareTo(total.subtract(p).max(BigDecimal.ZERO))>0)throw new IllegalArgumentException("Rabat lub potrącenie przekracza należność / kaucję");BigDecimal due=total.subtract(p).subtract(keep),refund=dep.subtract(keep);String summary="Należność: "+total+" zł\n"+(due.signum()<0?"Nadpłata do zwrotu: "+due.abs():"Do dopłaty: "+due)+" zł\nKaucja do oddania: "+refund+" zł\nZatwierdź po przyjęciu sprzętu i rozliczeniu płatności.";
            JSONObject r=copy(old);r.put("settled",true);r.put("returned",true);r.put("actual_return",end.getText().toString());r.put("income",total.toPlainString());r.put("loss",cost.toPlainString());r.put("loss_note",note.getText().toString());r.put("settled_at",LocalDate.now()+" 12:00:00");JSONObject details=new JSONObject();details.put("days",n);details.put("rate",rate.getText().toString());details.put("extra",extra.getText().toString());details.put("discount",discount.getText().toString());details.put("paid",p.toPlainString());details.put("deposit",dep.toPlainString());details.put("retained",keep.toPlainString());details.put("due",due.toPlainString());details.put("deposit_refund",refund.toPlainString());r.put("settlement",details);
            new AlertDialog.Builder(activity).setTitle("Potwierdź rozliczenie").setMessage(summary).setNegativeButton("Popraw",null).setPositiveButton("Sprzęt i płatność rozliczone",(d,w)->{store.saveRentalOperation(r,"reservation_settle");host.sync();active();}).show();
        }catch(Exception e){error(e);}});
    }
}
