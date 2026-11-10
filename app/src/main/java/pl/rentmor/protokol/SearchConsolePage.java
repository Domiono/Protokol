package pl.rentmor.protokol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.widget.*;
import org.json.*;
import java.util.Locale;

/** Native admin-only view of the same server reports as the WWW panel. */
final class SearchConsolePage {
    static final String SETUP_URL="https://rentmor.pl/admin.php?tab=google";
    private final Activity activity;
    private final LinearLayout root;
    private final SyncManager sync;
    private final Runnable home;
    private LinearLayout page,report;
    private TextView status;
    private Button period,equipment,refresh,reload;
    private JSONObject data;
    private int days=28,generation=0;
    private String selected="";
    private boolean busy=false;
    SearchConsolePage(Activity activity,LinearLayout root,SyncManager sync,Runnable home){this.activity=activity;this.root=root;this.sync=sync;this.home=home;}
    private boolean admin(){return "admin".equals(activity.getSharedPreferences("rentmor",0).getString("role",""));}
    private boolean active(){return !activity.isFinishing()&&!activity.isDestroyed()&&page!=null&&page.isAttachedToWindow();}
    private TextView text(LinearLayout parent,String label,int size){TextView t=new TextView(activity);t.setText(label);t.setTextSize(size);t.setTextColor(0xff17263e);t.setPadding(0,8,0,8);parent.addView(t);return t;}
    private Button button(String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());page.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    void show(){
        if(!admin()){home.run();return;}
        root.removeAllViews();page=new LinearLayout(activity);page.setOrientation(1);root.addView(page,new LinearLayout.LayoutParams(-1,-2));
        button("‹ Panel główny",this::back);
        text(page,"Google / SEO",26).setTypeface(null,Typeface.BOLD);
        text(page,"Search Console — organiczne wyniki Google. Te same raporty co w panelu WWW; dane kończą się trzy dni przed dzisiejszą datą.",14);
        status=text(page,"",15);
        period=button("Okres: 28 dni ▼",()->new AlertDialog.Builder(activity).setTitle("Wybierz okres").setItems(new String[]{"7 dni","28 dni","90 dni"},(d,which)->{
            int next=new int[]{7,28,90}[which];if(next!=days){days=next;selected="";data=null;report.removeAllViews();load(false);}
        }).setNegativeButton("Anuluj",null).show());
        equipment=button("Sprzęt ▼",this::chooseEquipment);
        reload=button("Odśwież widok z serwera",()->load(false));
        refresh=button("Pobierz dane z Google",()->load(true));
        button("Połączenie Google — panel WWW",()->{
            if(!admin()){back();return;}
            try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(SETUP_URL)));}
            catch(android.content.ActivityNotFoundException e){status.setText("Otwórz panel RentMor.pl w przeglądarce, aby połączyć konto Google.");}
        });
        report=new LinearLayout(activity);report.setOrientation(1);page.addView(report,new LinearLayout.LayoutParams(-1,-2));
        load(false);
    }
    void back(){++generation;home.run();}
    private void controls(){
        period.setText("Okres: "+days+" dni ▼");period.setEnabled(!busy);reload.setEnabled(!busy);
        refresh.setEnabled(!busy&&data!=null&&data.optBoolean("connected"));
        equipment.setEnabled(!busy&&data!=null&&data.optBoolean("has_report"));
    }
    private void load(boolean fromGoogle){
        if(!admin()){back();return;}if(busy)return;
        busy=true;final int ticket=++generation,requestedDays=days;
        status.setText(fromGoogle?"Pobieranie z Google… Może potrwać dłuższą chwilę.":"Pobieranie raportu…");controls();
        new Thread(()->{
            try{
                JSONObject response=fromGoogle?sync.refreshSearchConsole(requestedDays):sync.fetchSearchConsole(requestedDays);
                activity.runOnUiThread(()->{
                    if(ticket!=generation||!active())return;
                    busy=false;if(!admin()){data=null;report.removeAllViews();back();return;}
                    try{
                        SyncManager.requireSearchConsoleAdmin(response);
                        if(response.optInt("days")!=requestedDays)throw new IllegalArgumentException();
                        if(response.optBoolean("has_report")&&response.optJSONArray("equipment")==null)throw new IllegalArgumentException();
                        data=response;render();controls();
                    }catch(Exception e){status.setText("Nieprawidłowa odpowiedź Search Console. Zachowano poprzedni widok.");controls();}
                });
            }catch(Exception e){activity.runOnUiThread(()->{
                if(ticket!=generation||!active())return;
                busy=false;if(!admin()){data=null;report.removeAllViews();back();return;}
                status.setText("Nie udało się odświeżyć. "+e.getMessage()+(data!=null&&data.optBoolean("has_report")?"\nNadal wyświetlasz poprzedni raport.":""));controls();
            });}
        },"rentmor-search-console").start();
    }
    private void chooseEquipment(){
        if(!admin()){back();return;}if(busy||data==null)return;
        JSONArray items=data.optJSONArray("equipment");if(items==null)return;
        String[] names=new String[items.length()];for(int i=0;i<names.length;i++)names[i]=items.optJSONObject(i).optString("name");
        new AlertDialog.Builder(activity).setTitle("Wybierz sprzęt").setItems(names,(d,index)->{selected=items.optJSONObject(index).optString("id");render();}).setNegativeButton("Anuluj",null).show();
    }
    private static String number(JSONObject metrics,String key,int decimals){
        if(metrics==null||metrics.isNull(key)||!metrics.has(key))return "—";
        return String.format(new Locale("pl","PL"),"%,."+decimals+"f",metrics.optDouble(key));
    }
    private static String delta(JSONObject current,JSONObject previous,String key,int decimals){
        if(current==null||previous==null||current.isNull(key)||previous.isNull(key)||!current.has(key)||!previous.has(key))return "—";
        double value=current.optDouble(key)-previous.optDouble(key);
        return (value>0?"+":"")+String.format(new Locale("pl","PL"),"%,."+decimals+"f",value);
    }
    private String range(JSONObject ranges,String key){JSONArray a=ranges==null?null:ranges.optJSONArray(key);return a==null?"—":a.optString(0)+" – "+a.optString(1);}
    private void render(){
        report.removeAllViews();
        if(!data.optBoolean("has_report")){
            equipment.setText("Sprzęt ▼");
            status.setText(!data.optBoolean("connected")?"Najpierw połącz konto Google w panelu WWW. W aplikacji nie zapisujemy kluczy ani haseł Google.":"Brak raportu dla tego okresu. Kliknij „Pobierz dane z Google”.");return;
        }
        JSONObject ranges=data.optJSONObject("ranges");
        status.setText("Raport: "+range(ranges,"current")+"\nPorównanie: "+range(ranges,"previous")+"\nPobrano: "+data.optString("fetched_at","—")
            +(data.optBoolean("stale")?"\nStarszy raport — pobierz dane z Google, aby go zaktualizować.":"")
            +(data.optBoolean("truncated")?"\nRaport częściowy: osiągnięto limit pobierania.":""));
        JSONArray items=data.optJSONArray("equipment");JSONObject item=null;
        for(int i=0;i<items.length();i++)if(selected.equals(items.optJSONObject(i).optString("id")))item=items.optJSONObject(i);
        if(item==null&&items.length()>0){item=items.optJSONObject(0);selected=item.optString("id");}
        if(item==null){text(report,"Brak sprzętu w raporcie.",16);return;}
        equipment.setText("Sprzęt: "+item.optString("name")+" ▼");
        text(report,item.optString("name"),21).setTypeface(null,Typeface.BOLD);
        JSONObject current=item.optJSONObject("current"),previous=item.optJSONObject("previous");
        String[] keys={"clicks","impressions","ctr","position"},labels={"Kliknięcia","Wyświetlenia","CTR","Średnia pozycja"};
        for(int i=0;i<keys.length;i++){
            int decimals=i<2?0:2;String suffix=i==2?"%":"";
            text(report,labels[i]+": "+number(current,keys[i],decimals)+suffix+"   (zmiana: "+delta(current,previous,keys[i],decimals)+(i==2?" p.p.":"")+")",18);
        }
        text(report,"Niższa pozycja liczbowa jest lepsza. Zmiany porównują równe okresy. Stare i nowe adresy sprzętu są sumowane.",13);
        text(report,"Frazy wyszukiwania",21).setTypeface(null,Typeface.BOLD);
        JSONArray queries=item.optJSONArray("queries");
        if(queries==null||queries.length()==0)text(report,"Brak ujawnionych fraz dla tego sprzętu w wybranym okresie.",15);
        else for(int i=0;i<Math.min(100,queries.length());i++){
            JSONObject query=queries.optJSONObject(i);if(query==null)continue;
            JSONObject q=query.optJSONObject("current");
            text(report,query.optString("query"),17).setTypeface(null,Typeface.BOLD);
            text(report,"Kliknięcia: "+number(q,"clicks",0)+" ("+delta(q,query.optJSONObject("previous"),"clicks",0)+") · Wyświetlenia: "+number(q,"impressions",0)
                +"\nCTR: "+number(q,"ctr",2)+"% · Pozycja: "+number(q,"position",2),14);
        }
        text(report,"Google ukrywa część zapytań. Suma fraz może być niższa od wyniku sprzętu; brak danych nie oznacza braku indeksacji. Do 100 fraz na sprzęt.",13);
    }
}
