package pl.rentmor.protokol;

import android.app.*;
import android.app.DatePickerDialog;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.net.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.database.*;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import android.util.Base64;

public class MainActivity extends Activity {
    private static final String HOME="https://rentmor.pl/protocol.php";
    private LocalStore store; private SyncManager sync; private LinearLayout root; private LinearLayout homePage; private boolean adminDownloadAdded=false; private TextView status;
    private WebView activation; private boolean showingApp=false; private final ArrayList<Bitmap> issuePhotos=new ArrayList<>(); private TextView photoCount; private SignatureView signatureView; private final AtomicBoolean syncRunning=new AtomicBoolean(false); private ConnectivityManager.NetworkCallback networkCallback;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(18,59,104));getWindow().setNavigationBarColor(Color.rgb(18,59,104));store=new LocalStore(this);sync=new SyncManager(this,store);registerNetworkSync();
        String saved=getSharedPreferences("rentmor",0).getString("cookie","");
        if(!saved.isEmpty()) showHome(); else showActivation();
    }
    private void showActivation(){
        activation=new WebView(this); activation.getSettings().setJavaScriptEnabled(true);activation.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        activation.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){
            String c=CookieManager.getInstance().getCookie("https://rentmor.pl");
            if(c!=null&&c.contains("rm_protocol_device=")){getSharedPreferences("rentmor",0).edit().putString("cookie",c).apply();activation.postDelayed(()->showHome(),500);}
        }});
        setContentView(activation); activation.loadUrl(HOME);
    }
    private void showHome(){showingApp=true;root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,22,20,24);root.setBackgroundColor(0xfff3f6fb);
        ScrollView scroll=new ScrollView(this); LinearLayout page=new LinearLayout(this);homePage=page;adminDownloadAdded=false;page.setOrientation(1);scroll.addView(page);
        TextView eyebrow=new TextView(this);eyebrow.setText("RENTMOR");eyebrow.setTextSize(13);eyebrow.setLetterSpacing(.18f);eyebrow.setTextColor(0xff64748b);eyebrow.setTypeface(null,1);page.addView(eyebrow);
        TextView title=new TextView(this);title.setText("Protokoły");title.setTextSize(30);title.setTextColor(0xff123b68);title.setTypeface(null,1);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.bottomMargin=14;page.addView(title,tp);
        LinearLayout statCard=new LinearLayout(this);statCard.setOrientation(1);statCard.setPadding(18,16,18,16);statCard.setBackground(roundBg(0xffffffff,18));TextView stTitle=new TextView(this);stTitle.setText("STATUS DANYCH");stTitle.setTextSize(11);stTitle.setTextColor(0xff64748b);stTitle.setTypeface(null,1);statCard.addView(stTitle);
        status=new TextView(this);status.setText("W telefonie: "+store.allProtocols().length()+"   •   Do wysłania: "+store.pendingCount());status.setTextSize(14);status.setTextColor(0xff26384d);status.setPadding(0,8,0,0);statCard.addView(status);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.bottomMargin=20;page.addView(statCard,sp);
        menuCard(page,"⟳","Synchronizuj teraz","Wyślij zapisane protokoły na serwer",()->syncNow(),0xffe8f1fb);
        menuCard(page,"＋","Nowy protokół wydania","Utwórz protokół i dodaj zdjęcia",()->newIssue(),0xffe9f5ef);
        menuCard(page,"▤","Lista protokołów","Przeglądaj dokumenty i zapisuj PDF",()->protocolList(),0xfffff3e5);
        menuCard(page,"☷","Rezerwacje","Prosta lista odbiorów – jak w notatniku",()->reservationPage(),0xffe9f5ef);
        menuCard(page,"↩","Obsłuż zwrot sprzętu","Zarejestruj stan sprzętu przy zwrocie",()->returnList(),0xfff1edfa);
        menuCard(page,"⚙","Aktywuj / zmień urządzenie","Ustaw dostęp tego telefonu",()->resetActivation(),0xffeef1f5);
        addAdminDownloadCard();
        root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(root); if(isOnline()) syncNow();
    }
    private void addAdminDownloadCard(){
        if(adminDownloadAdded || homePage==null) return;
        if(!"admin".equals(getSharedPreferences("rentmor",0).getString("role",""))) return;
        adminDownloadAdded=true;
        menuCard(homePage,"⬇","Pobierz aplikację","Instalator APK na kolejne urządzenie",()->{
            new AlertDialog.Builder(this).setTitle("Pobierz aplikację RentMor")
                .setMessage("Otworzyć najnowszy instalator APK? Link będzie prowadził do aktualnej wersji aplikacji.")
                .setNegativeButton("Anuluj",null)
                .setPositiveButton("Pobierz",(d,w)->{
                    try{Intent i=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/Domiono/Protokol/releases/latest/download/RentMor.apk"));startActivity(i);}
                    catch(Exception e){toast("Nie można otworzyć pobierania: "+e.getMessage());}
                }).show();
        },0xffe8f1fb);
    }
    private android.graphics.drawable.GradientDrawable roundBg(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private void menuCard(LinearLayout parent,String symbol,String title,String subtitle,Runnable action,int tint){
        LinearLayout card=new LinearLayout(this);card.setOrientation(0);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(15,14,15,14);card.setBackground(roundBg(0xffffffff,18));card.setElevation(3);
        TextView ico=new TextView(this);ico.setText(symbol);ico.setTextSize(25);ico.setTextColor(0xff123b68);ico.setGravity(Gravity.CENTER);ico.setBackground(roundBg(tint,14));card.addView(ico,new LinearLayout.LayoutParams(52,52));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);labels.setPadding(14,0,0,0);TextView t=new TextView(this);t.setText(title);t.setTextSize(16);t.setTextColor(0xff172b43);t.setTypeface(null,1);labels.addView(t);TextView sub=new TextView(this);sub.setText(subtitle);sub.setTextSize(12);sub.setTextColor(0xff718096);sub.setPadding(0,4,0,0);labels.addView(sub);card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextSize(28);arrow.setTextColor(0xff94a3b8);card.addView(arrow);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=12;parent.addView(card,cp);card.setOnClickListener(v->action.run());
    }
    private void resetActivation(){
        showingApp=false;
        getSharedPreferences("rentmor",0).edit().remove("cookie").apply();
        CookieManager cm=CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.removeAllCookies(removed->{cm.flush();runOnUiThread(()->showActivation());});
    }
    private boolean isOnline(){android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.Network n=cm.getActiveNetwork();return n!=null&&cm.getNetworkCapabilities(n)!=null&&cm.getNetworkCapabilities(n).hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);}
    private void addButton(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    private EditText field(String hint){EditText e=new EditText(this);e.setSingleLine(false);e.setHint(hint);root.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private EditText dateField(String hint,String initial){
        EditText e=field(hint);e.setText(initial);e.setFocusable(false);e.setClickable(true);e.setOnClickListener(v->showDatePicker(e));return e;
    }
    private void showDatePicker(EditText target){
        Calendar cal=Calendar.getInstance();String current=target.getText().toString();
        try{Date d=new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).parse(current);if(d!=null)cal.setTime(d);}catch(Exception ignored){}
        new DatePickerDialog(this,(view,year,month,day)->target.setText(String.format(Locale.getDefault(),"%02d.%02d.%04d",day,month+1,year)),cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void populateEquipment(Spinner spinner){
        JSONArray a=store.equipment();ArrayList<String> names=new ArrayList<>();names.add("— Wybierz sprzęt —");
        for(int i=0;i<a.length();i++){String name=a.optString(i);if(!name.trim().isEmpty())names.add(name);}
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
    }
    private void refreshEquipment(Spinner spinner){
        if(!isOnline()){toast("Brak internetu. Pokazuję ostatnią pobraną listę sprzętów.");populateEquipment(spinner);return;}
        toast("Aktualizuję listę sprzętów…");
        new Thread(()->{try{JSONObject data=sync.sync();runOnUiThread(()->{populateEquipment(spinner);toast("Lista sprzętów została zaktualizowana.");});}catch(Exception e){runOnUiThread(()->toast("Nie udało się odświeżyć listy: "+e.getMessage()));}}).start();
    }
    private void confirmDelete(JSONObject p){
        new AlertDialog.Builder(this).setTitle("Usunąć protokół?")
            .setMessage("Protokół "+p.optString("number")+" zostanie usunięty z telefonu i serwera. Tej operacji nie można cofnąć.")
            .setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(dialog,which)->{
                try{JSONObject op=new JSONObject();op.put("id",p.optString("id"));store.enqueue("delete",op);store.deleteProtocol(p.optString("id"));toast("Protokół usunięty lokalnie. Usunięcie z serwera zostanie zsynchronizowane.");protocolList();if(isOnline())syncNow();}
                catch(Exception e){toast("Nie udało się usunąć: "+e.getMessage());}
            }).show();
    }
    private void registerNetworkSync(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);networkCallback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(android.net.Network network){runOnUiThread(()->{if(showingApp&&getSharedPreferences("rentmor",0).getString("cookie","").length()>0)syncNow();});}};try{cm.registerDefaultNetworkCallback(networkCallback);}catch(Exception ignored){}}
    @Override protected void onDestroy(){try{if(networkCallback!=null)((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}super.onDestroy();}
    private void syncNow(){if(!isOnline()){if(status!=null)status.setText("Brak internetu. Dane pozostają bezpiecznie w telefonie.");return;}if(!syncRunning.compareAndSet(false,true))return;if(status!=null)status.setText("Synchronizacja…");new Thread(()->{try{sync.sync();runOnUiThread(()->{if(status!=null)status.setText("Synchronizacja zakończona. Oczekujące: "+store.pendingCount());addAdminDownloadCard();});}catch(Exception e){runOnUiThread(()->{if(status!=null)status.setText("Nie wysłano: "+e.getMessage()+" • Dane zapisane lokalnie.");});}finally{syncRunning.set(false);}}).start();}

    private void reservationPage() {
        root.removeAllViews();
        root.setOrientation(1);
        root.setPadding(18,16,18,18);
        root.setBackgroundColor(0xfff3f6fb);
        TextView heading=new TextView(this);
        heading.setText("REZERWACJE"); heading.setTextSize(25); heading.setTypeface(null,1);
        heading.setTextColor(0xff123b68); root.addView(heading);
        Calendar today=Calendar.getInstance();
        final String[] month={String.format(Locale.US,"%04d-%02d",today.get(Calendar.YEAR),today.get(Calendar.MONTH)+1)};
        TextView monthLabel=new TextView(this); monthLabel.setTextSize(16); monthLabel.setTextColor(0xff526174);
        monthLabel.setPadding(0,8,0,8); root.addView(monthLabel);
        LinearLayout monthBar=new LinearLayout(this); monthBar.setGravity(Gravity.CENTER_VERTICAL);
        Button previous=new Button(this); previous.setText("‹");
        Button selectMonth=new Button(this); selectMonth.setText("Wybierz miesiąc");
        Button next=new Button(this); next.setText("›");
        monthBar.addView(previous); monthBar.addView(selectMonth,new LinearLayout.LayoutParams(0,-2,1)); monthBar.addView(next);
        root.addView(monthBar);
        EditText input=new EditText(this); input.setSingleLine(true);
        input.setHint("Np. 18 agregat 18 / 533004489"); root.addView(input);
        TextView hint=new TextView(this); hint.setText("Wpisz lub wklej całą rezerwację – bez formularza.");
        hint.setTextSize(12); hint.setTextColor(0xff718096); root.addView(hint);
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this); list.setOrientation(1); list.setPadding(0,10,0,8);
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        final Runnable[] render=new Runnable[1];
        render[0]=()->{
            Calendar selected=parseReservationMonth(month[0]);
            monthLabel.setText(new SimpleDateFormat("LLLL yyyy",new Locale("pl","PL")).format(selected.getTime()).toUpperCase(new Locale("pl","PL")));
            list.removeAllViews();
            JSONArray data=store.reservations(month[0]);
            ArrayList<JSONObject> items=new ArrayList<>();
            for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null)items.add(item);}
            Collections.sort(items,(a,b)->{
                int da=reservationDay(a.optString("text")), db=reservationDay(b.optString("text"));
                if(da!=db)return Integer.compare(da,db);
                return Integer.compare(reservationHour(a.optString("text")),reservationHour(b.optString("text")));
            });
            if(items.isEmpty()){
                TextView empty=new TextView(this); empty.setText("Brak rezerwacji w tym miesiącu.");
                empty.setTextColor(0xff718096); empty.setPadding(8,20,8,20); list.addView(empty);
            }
            for(JSONObject item:items){
                LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(8,5,4,5); row.setBackground(roundBg(Color.WHITE,12));
                CheckBox check=new CheckBox(this); check.setText(item.optString("text"));
                check.setTextSize(15); check.setTextColor(0xff172b43); check.setChecked(item.optBoolean("done"));
                check.setPaintFlags(check.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                if(item.optBoolean("done"))check.setPaintFlags(check.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                row.addView(check,new LinearLayout.LayoutParams(0,-2,1));
                Button edit=new Button(this); edit.setText("Edytuj"); row.addView(edit);
                Button delete=new Button(this); delete.setText("×"); row.addView(delete);
                LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2); rowParams.bottomMargin=7; list.addView(row,rowParams);
                check.setOnCheckedChangeListener((button,checked)->{
                    try{item.put("done",checked);store.saveReservation(item);store.enqueue("reservation_save",item);if(isOnline())syncNow();render[0].run();}
                    catch(Exception ex){toast("Nie udało się zapisać zmiany.");}
                });
                edit.setOnClickListener(v->{
                    EditText editor=new EditText(this);editor.setSingleLine(true);editor.setText(item.optString("text"));editor.setSelection(editor.getText().length());
                    new AlertDialog.Builder(this).setTitle("Edytuj rezerwację").setView(editor)
                        .setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(dialog,which)->{
                            String value=editor.getText().toString().trim();if(value.isEmpty()){toast("Wpis nie może być pusty.");return;}
                            try{item.put("text",value);store.saveReservation(item);store.enqueue("reservation_save",item);render[0].run();if(isOnline())syncNow();}
                            catch(Exception ex){toast("Nie udało się zapisać wpisu.");}
                        }).show();
                });
                delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Usunąć rezerwację?")
                    .setMessage(item.optString("text")).setNegativeButton("Anuluj",null)
                    .setPositiveButton("Usuń",(dialog,which)->{
                        try{JSONObject op=new JSONObject();op.put("id",item.optString("id"));store.enqueue("reservation_delete",op);store.deleteReservation(item.optString("id"));render[0].run();if(isOnline())syncNow();}
                        catch(Exception ex){toast("Nie udało się usunąć wpisu.");}
                    }).show());
            }
        };
        previous.setOnClickListener(v->{Calendar cal=parseReservationMonth(month[0]);cal.add(Calendar.MONTH,-1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(cal.getTime());render[0].run();});
        next.setOnClickListener(v->{Calendar cal=parseReservationMonth(month[0]);cal.add(Calendar.MONTH,1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(cal.getTime());render[0].run();});
        selectMonth.setOnClickListener(v->{Calendar cal=parseReservationMonth(month[0]);new DatePickerDialog(this,(picker,y,m,d)->{month[0]=String.format(Locale.US,"%04d-%02d",y,m+1);render[0].run();},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),1).show();});
        addButton("＋ DODAJ WPIS",()->{
            String value=input.getText().toString().trim();
            if(value.isEmpty()){toast("Wpisz rezerwację.");return;}
            try{JSONObject item=new JSONObject();item.put("id",UUID.randomUUID().toString());item.put("month",month[0]);item.put("text",value);item.put("done",false);store.saveReservation(item);store.enqueue("reservation_save",item);input.setText("");render[0].run();if(isOnline())syncNow();}
            catch(Exception ex){toast("Nie udało się dodać rezerwacji.");}
        });
        addButton("WRÓĆ",()->showHome());
        render[0].run();
    }
    private Calendar parseReservationMonth(String value) {
        Calendar cal=Calendar.getInstance();
        try{cal.setTime(new SimpleDateFormat("yyyy-MM",Locale.US).parse(value));}catch(Exception ignored){}
        return cal;
    }
    private int reservationDay(String value) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*(\\d{1,2})").matcher(value);
        return m.find()?Integer.parseInt(m.group(1)):99;
    }
    private int reservationHour(String value) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+.*?\\s+(\\d{1,2})\\s*/").matcher(value);
        return m.find()?Integer.parseInt(m.group(1)):99;
    }

    private void newIssue(){
        issuePhotos.clear(); signatureView=null;
        root.removeAllViews(); TextView h=new TextView(this);h.setText("NOWY PROTOKÓŁ WYDANIA");h.setTextSize(20);h.setTextColor(0xff123b68);root.addView(h);
        EditText client=field("Imię i nazwisko / firma klienta");EditText phone=field("Telefon");
        TextView equipmentLabel=new TextView(this);equipmentLabel.setText("Sprzęt z aktualnej listy RentMor");equipmentLabel.setTextColor(0xff526174);equipmentLabel.setPadding(0,10,0,2);root.addView(equipmentLabel);
        Spinner equipment=new Spinner(this);populateEquipment(equipment);root.addView(equipment,new LinearLayout.LayoutParams(-1,-2));
        addButton("⟳ Odśwież listę sprzętów",()->refreshEquipment(equipment));
        EditText date=dateField("Data wydania",new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date()));EditText condition=field("Stan sprzętu");EditText accessories=field("Akcesoria");EditText notes=field("Uwagi");
        photoCount=new TextView(this);photoCount.setText("Nie dodano zdjęć.");photoCount.setTextColor(0xff526174);photoCount.setPadding(0,8,0,4);root.addView(photoCount);addButton("＋ Dodaj zdjęcia (można wybrać kilka)",()->pickPhoto());
        signatureView=new SignatureView(this); root.addView(signatureView,new LinearLayout.LayoutParams(-1,180));
        addButton("ZAPISZ PROTOKÓŁ W TELEFONIE",()->{if(client.getText().toString().trim().isEmpty()||equipment.getSelectedItem()==null||equipment.getSelectedItemPosition()==0){toast("Wpisz klienta i sprzęt.");return;}
            try{String id=UUID.randomUUID().toString();JSONObject p=new JSONObject();p.put("id",id);p.put("number","RM-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date()));p.put("client",client.getText());p.put("phone",phone.getText());p.put("equipment",equipment.getSelectedItem().toString());p.put("rental_date",date.getText());p.put("condition",condition.getText());p.put("accessories",accessories.getText());p.put("notes",notes.getText());p.put("status","wydany");JSONArray photos=new JSONArray();for(Bitmap photo:issuePhotos)photos.put(bitmapBase64(photo,1280,75));if(photos.length()>0)p.put("photos_base64",photos);if(signatureView!=null)p.put("signature_base64",bitmapBase64(signatureView.image(),900,85));store.enqueue("create",p);makePdf(p);toast("Zapisano lokalnie. PDF znajduje się w Pobrane/RentMor.");showHome();}catch(Exception ex){toast("Błąd zapisu: "+ex.getMessage());}});
        addButton("WRÓĆ",()->showHome());
    }
    private void protocolList(){root.removeAllViews();root.setOrientation(1);root.setPadding(20,20,20,20);root.setBackgroundColor(0xfff3f6fb);
        TextView h=new TextView(this);h.setText("Twoje protokoły");h.setTextSize(25);h.setTypeface(null,1);h.setTextColor(0xff123b68);root.addView(h);
        TextView info=new TextView(this);info.setText("Wybierz dokument, aby zobaczyć jego szczegóły i zdjęcia.");info.setTextSize(13);info.setTextColor(0xff64748b);info.setPadding(0,5,0,16);root.addView(info);
        ScrollView sc=new ScrollView(this);LinearLayout items=new LinearLayout(this);items.setOrientation(1);sc.addView(items);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        JSONArray a=store.allProtocols();for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(16,14,16,14);card.setBackground(roundBg(Color.WHITE,16));card.setElevation(2);
            TextView num=new TextView(this);num.setText(p.optString("number","Protokół"));num.setTextSize(16);num.setTypeface(null,1);num.setTextColor(0xff172b43);card.addView(num);
            TextView desc=new TextView(this);desc.setText(p.optString("client")+"  •  "+p.optString("equipment"));desc.setTextSize(13);desc.setTextColor(0xff526174);desc.setPadding(0,6,0,5);card.addView(desc);
            TextView st=new TextView(this);st.setText("Status: "+p.optString("status")+"     ›");st.setTextSize(12);st.setTextColor(0xff2563a6);card.addView(st);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=12;items.addView(card,cp);card.setOnClickListener(v->showProtocolDetail(p));
        }
        if(a.length()==0){TextView empty=new TextView(this);empty.setText("Nie ma jeszcze zapisanych protokołów.");empty.setTextColor(0xff64748b);items.addView(empty);}
        addButton("WRÓĆ",()->showHome());
    }
    private void detailLine(LinearLayout box,String label,String value){if(value==null||value.trim().isEmpty())return;TextView t=new TextView(this);t.setText(label+"\\n"+value);t.setTextSize(14);t.setTextColor(0xff24364b);t.setPadding(0,7,0,9);box.addView(t);}
    private void showProtocolDetail(JSONObject p){
        root.removeAllViews();root.setOrientation(1);root.setPadding(20,18,20,20);root.setBackgroundColor(0xfff3f6fb);
        ScrollView sc=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(1);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        TextView title=new TextView(this);title.setText(p.optString("number","Protokół"));title.setTextSize(23);title.setTypeface(null,1);title.setTextColor(0xff123b68);content.addView(title);
        TextView state=new TextView(this);state.setText("Status: "+p.optString("status"));state.setTextSize(13);state.setTextColor(0xff2563a6);state.setPadding(0,5,0,14);content.addView(state);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(16,12,16,12);panel.setBackground(roundBg(Color.WHITE,16));content.addView(panel);
        detailLine(panel,"Klient",p.optString("client"));detailLine(panel,"Telefon",p.optString("phone"));detailLine(panel,"Sprzęt",p.optString("equipment"));detailLine(panel,"Data wydania",p.optString("rental_date"));detailLine(panel,"Stan przy wydaniu",p.optString("condition"));detailLine(panel,"Akcesoria",p.optString("accessories"));detailLine(panel,"Uwagi",p.optString("notes"));
        detailLine(panel,"Data zwrotu",p.optString("return_date"));detailLine(panel,"Stan przy zwrocie",p.optString("return_condition"));detailLine(panel,"Uwagi do zwrotu",p.optString("return_notes"));
        JSONArray photos=p.optJSONArray("photos_base64");if(photos!=null&&photos.length()>0){TextView ph=new TextView(this);ph.setText("Zdjęcia ("+photos.length()+")");ph.setTextSize(17);ph.setTypeface(null,1);ph.setTextColor(0xff123b68);ph.setPadding(0,18,0,8);content.addView(ph);for(int i=0;i<photos.length();i++){try{byte[] raw=Base64.decode(photos.optString(i),Base64.DEFAULT);Bitmap bm=BitmapFactory.decodeByteArray(raw,0,raw.length);if(bm!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(bm);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.bottomMargin=10;content.addView(iv,ip);}}catch(Exception ignored){}}}
        addButton("OTWÓRZ GOTOWY PROTOKÓŁ (PDF)",()->previewPdf(p));addButton("Zapisz / wygeneruj PDF",()->makePdf(p));
        if("admin".equals(getSharedPreferences("rentmor",0).getString("role",""))) addButton("🗑 USUŃ PROTOKÓŁ",()->confirmDelete(p));
        addButton("WRÓĆ DO LISTY",()->protocolList());
    }

    private void pickPhoto(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,4101);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==4101 && resultCode==RESULT_OK && data!=null) try {
            int added=0;
            if(data.getClipData()!=null){android.content.ClipData clip=data.getClipData();for(int n=0;n<clip.getItemCount();n++){android.net.Uri uri=clip.getItemAt(n).getUri();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);try(InputStream in=getContentResolver().openInputStream(uri)){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null){issuePhotos.add(b);added++;}}}}
            else if(data.getData()!=null){android.net.Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);try(InputStream in=getContentResolver().openInputStream(uri)){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null){issuePhotos.add(b);added++;}}}
            if(photoCount!=null)photoCount.setText("Dodane zdjęcia: "+issuePhotos.size());
            toast(added>0?"Dodano "+added+" zdjęć.":"Nie udało się wczytać zdjęć.");
        } catch(Exception e){toast("Błąd zdjęć: "+e.getMessage());}
    }
    private void returnList(){
        root.removeAllViews(); TextView h=new TextView(this); h.setText("PROTOKOŁY DO ZWROTU"); h.setTextSize(20); h.setTextColor(0xff123b68); root.addView(h);
        JSONArray a=store.allProtocols(); int count=0;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i); if(p==null||!"wydany".equals(p.optString("status")))continue; count++;
            Button b=new Button(this); b.setAllCaps(false); b.setText(p.optString("number")+ " • "+p.optString("client")+" \\n"+p.optString("equipment")); b.setOnClickListener(v->returnForm(p)); root.addView(b);
        }
        if(count==0){TextView t=new TextView(this);t.setText("Brak wydanych protokołów.");root.addView(t);}
        addButton("WRÓĆ",()->showHome());
    }
    private void returnForm(JSONObject original){
        signatureView=null;
        root.removeAllViews(); TextView h=new TextView(this);h.setText("ZWROT: "+original.optString("number"));h.setTextSize(20);h.setTextColor(0xff123b68);root.addView(h);
        EditText date=dateField("Data zwrotu",new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date()));
        EditText condition=field("Stan sprzętu przy zwrocie");EditText notes=field("Uwagi do zwrotu");
        signatureView=new SignatureView(this);root.addView(signatureView,new LinearLayout.LayoutParams(-1,180));
        addButton("ZAPISZ ZWROT I PDF",()->{try{JSONObject p=new JSONObject(original.toString());p.put("status","zwrócony");p.put("return_date",date.getText().toString());p.put("return_condition",condition.getText().toString());p.put("return_notes",notes.getText().toString());if(signatureView!=null)p.put("return_signature_base64",bitmapBase64(signatureView.image(),900,85));store.enqueue("return",p);store.saveProtocol(p);makePdf(p);toast("Zwrot zapisany lokalnie.");showHome();}catch(Exception e){toast("Błąd zwrotu: "+e.getMessage());}});
        addButton("WRÓĆ",()->returnList());
    }
    private class SignatureView extends View {
        private Bitmap bitmap; private Canvas canvas; private Paint pen=new Paint(3); private float lx,ly; private boolean drawing=false;
        SignatureView(Context c){super(c);setBackgroundColor(Color.WHITE);pen.setColor(Color.BLACK);pen.setStrokeWidth(4);pen.setStyle(Paint.Style.STROKE);}
        private void ensure(){if(bitmap==null){bitmap=Bitmap.createBitmap(Math.max(1,getWidth()),Math.max(1,getHeight()),Bitmap.Config.ARGB_8888);canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);}}
        @Override protected void onDraw(Canvas c){super.onDraw(c);ensure();c.drawBitmap(bitmap,0,0,null);Paint p=new Paint(3);p.setColor(0xff888888);p.setTextSize(12);c.drawText("PODPIS KLIENTA – podpisz palcem",8,18,p);}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){ensure();float x=e.getX(),y=e.getY();if(e.getAction()==0){lx=x;ly=y;drawing=true;return true;}if(e.getAction()==2&&drawing){canvas.drawLine(lx,ly,x,y,pen);lx=x;ly=y;invalidate();return true;}if(e.getAction()==1){drawing=false;return true;}return true;}
        Bitmap image(){ensure();return bitmap;}
    }

    private String bitmapBase64(Bitmap source,int maxSide,int quality)throws Exception{Bitmap b=source;float scale=Math.min(1f,(float)maxSide/Math.max(source.getWidth(),source.getHeight()));if(scale<1f)b=Bitmap.createScaledBitmap(source,Math.max(1,(int)(source.getWidth()*scale)),Math.max(1,(int)(source.getHeight()*scale)),true);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,quality,out);if(b!=source)b.recycle();return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);}
    private Bitmap bitmapFrom(JSONObject p,String key){try{String s=p.optString(key,"");if(s.isEmpty())return null;byte[] bytes=Base64.decode(s,Base64.DEFAULT);return BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(Exception e){return null;}}
    private File pdfFile(JSONObject p){File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"");return new File(dir,p.optString("number","protokol").replaceAll("[^A-Za-z0-9_-]","_")+".pdf");}
    private void previewPdf(JSONObject p){
        makePdf(p);
        try{
            File f=pdfFile(p);if(!f.exists())throw new IOException("Nie znaleziono pliku PDF");
            ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer=new PdfRenderer(fd);
            if(renderer.getPageCount()==0){renderer.close();fd.close();throw new IOException("PDF jest pusty");}
            LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(12,8,12,8);
            TextView pageLabel=new TextView(this);pageLabel.setGravity(Gravity.CENTER);pageLabel.setTextColor(0xff123b68);pageLabel.setTextSize(14);box.addView(pageLabel);
            ScrollView scroll=new ScrollView(this);ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);scroll.addView(image);box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);Button prev=new Button(this);prev.setText("‹ Poprzednia");Button next=new Button(this);next.setText("Następna ›");controls.addView(prev);controls.addView(next);box.addView(controls);
            final int[] current={0};
            Runnable render=()->{try(PdfRenderer.Page page=renderer.openPage(current[0])){int w=page.getWidth()*2,h=page.getHeight()*2;Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);image.setImageBitmap(bmp);pageLabel.setText("Strona "+(current[0]+1)+" z "+renderer.getPageCount());prev.setEnabled(current[0]>0);next.setEnabled(current[0]<renderer.getPageCount()-1);}catch(Exception ex){toast("Błąd podglądu PDF: "+ex.getMessage());}};
            prev.setOnClickListener(v->{if(current[0]>0){current[0]--;render.run();}});next.setOnClickListener(v->{if(current[0]<renderer.getPageCount()-1){current[0]++;render.run();}});
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(p.optString("number","Protokół PDF")).setView(box).setPositiveButton("Zamknij",null).create();
            dialog.setOnDismissListener(v->{try{renderer.close();fd.close();}catch(Exception ignored){}});
            render.run();dialog.show();dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.88));
        }catch(Exception e){toast("Nie można otworzyć PDF: "+e.getMessage());}
    }
    private void makePdf(JSONObject p){try{PdfDocument doc=new PdfDocument();PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();PdfDocument.Page page=doc.startPage(info);Canvas c=page.getCanvas();Paint paint=new Paint(3);paint.setColor(Color.BLACK);paint.setTextSize(20);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));c.drawText("RENTMOR – PROTOKÓŁ",40,55,paint);paint.setTextSize(13);paint.setTypeface(Typeface.DEFAULT);
        String[][] rows={{"Numer",p.optString("number")},{"Data wydania",p.optString("rental_date")},{"Klient",p.optString("client")},{"Telefon",p.optString("phone")},{"Sprzęt",p.optString("equipment")},{"Stan przy wydaniu",p.optString("condition")},{"Akcesoria",p.optString("accessories")},{"Uwagi",p.optString("notes")},{"Data zwrotu",p.optString("return_date")},{"Stan przy zwrocie",p.optString("return_condition")},{"Uwagi do zwrotu",p.optString("return_notes")}};
        int y=95;for(String[] row:rows){String value=row[1].replace("\n"," ");if(!value.isEmpty()){c.drawText(row[0]+": "+value,40,y,paint);y+=25;}}Bitmap sign=bitmapFrom(p,(!p.optString("return_date","").isEmpty())?"return_signature_base64":"signature_base64");if(sign!=null){c.drawText("Podpis:",40,690,paint);c.drawBitmap(sign,null,new Rect(40,700,555,810),paint);sign.recycle();}else c.drawText("Podpis: ______________________________",40,730,paint);doc.finishPage(page);
        JSONArray photos=p.optJSONArray("photos_base64");ArrayList<String> encoded=new ArrayList<>();if(photos!=null){for(int i=0;i<photos.length();i++)encoded.add(photos.optString(i));}else if(!p.optString("photo_base64","").isEmpty())encoded.add(p.optString("photo_base64"));
        for(int start=0;start<encoded.size();start+=4){PdfDocument.PageInfo pi=new PdfDocument.PageInfo.Builder(595,842,doc.getPages().size()+1).create();PdfDocument.Page pp=doc.startPage(pi);Canvas pc=pp.getCanvas();Paint ph=new Paint(3);ph.setColor(Color.BLACK);ph.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));ph.setTextSize(19);pc.drawText("RENTMOR – ZDJĘCIA SPRZĘTU",40,48,ph);ph.setTypeface(Typeface.DEFAULT);ph.setTextSize(12);for(int j=0;j<4&&start+j<encoded.size();j++){try{byte[] raw=Base64.decode(encoded.get(start+j),Base64.DEFAULT);Bitmap bm=BitmapFactory.decodeByteArray(raw,0,raw.length);if(bm!=null){int col=j%2,row=j/2;int left=40+col*260,top=75+row*365;pc.drawText("Zdjęcie "+(start+j+1),left,top,ph);Rect dst=new Rect(left,top+12,left+235,top+330);pc.drawBitmap(bm,null,dst,ph);bm.recycle();}}catch(Exception ignored){}}doc.finishPage(pp);}
        File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"");dir.mkdirs();File f=new File(dir,p.optString("number","protokol").replaceAll("[^A-Za-z0-9_-]","_")+".pdf");try(FileOutputStream out=new FileOutputStream(f)){doc.writeTo(out);}doc.close();
        android.content.ContentValues v=new android.content.ContentValues();v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,f.getName());v.put(android.provider.MediaStore.Downloads.MIME_TYPE,"application/pdf");v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/RentMor");android.net.Uri u=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u!=null)try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}
        toast("PDF zapisany w Pobrane/RentMor.");
    }catch(Exception e){toast("Nie udało się utworzyć PDF: "+e.getMessage());}}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    @Override public void onBackPressed(){if(showingApp)showHome();else super.onBackPressed();}
}