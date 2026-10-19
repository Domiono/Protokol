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
        menuCard(page,"☷","Rezerwacje","Lista odbiorów i rozliczenia najmu",()->reservationPage(),0xffe9f5ef);
        menuCard(page,"▥","Statystyki najmu","Przychody, straty i wynik według sprzętu",()->reservationStatsPage(),0xfffff3e5);
        if("admin".equals(getSharedPreferences("rentmor",0).getString("role",""))) menuCard(page," zł ","Pozostałe koszty","Paliwo, naprawy, reklama i zakupy sprzętu",()->expensePage(),0xfffcefe8);
        menuCard(page,"⟳","Synchronizuj teraz","Wyślij zapisane protokoły na serwer",()->syncNow(),0xffe8f1fb);
        menuCard(page,"＋","Nowy protokół wydania","Utwórz protokół i dodaj zdjęcia",()->newIssue(),0xffe9f5ef);
        menuCard(page,"▤","Lista protokołów","Przeglądaj dokumenty i zapisuj PDF",()->protocolList(),0xfffff3e5);
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
        ImageView ico=new ImageView(this);ico.setImageResource(menuIcon(title));ico.setColorFilter(0xff174a78);ico.setScaleType(ImageView.ScaleType.CENTER_INSIDE);ico.setPadding(14,14,14,14);ico.setBackground(roundBg(tint,16));card.addView(ico,new LinearLayout.LayoutParams(52,52));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);labels.setPadding(14,0,0,0);TextView t=new TextView(this);t.setText(title);t.setTextSize(16);t.setTextColor(0xff172b43);t.setTypeface(null,1);labels.addView(t);TextView sub=new TextView(this);sub.setText(subtitle);sub.setTextSize(12);sub.setTextColor(0xff718096);sub.setPadding(0,4,0,0);labels.addView(sub);card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextSize(28);arrow.setTextColor(0xff94a3b8);card.addView(arrow);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=12;parent.addView(card,cp);card.setOnClickListener(v->action.run());
    }
    private int menuIcon(String title){
        if(title.contains("Rezerwacje"))return android.R.drawable.ic_menu_my_calendar;
        if(title.contains("Statystyki"))return android.R.drawable.ic_menu_sort_by_size;
        if(title.contains("koszty"))return android.R.drawable.ic_menu_info_details;
        if(title.contains("Synchronizuj"))return android.R.drawable.ic_popup_sync;
        if(title.contains("Nowy protokół"))return android.R.drawable.ic_input_add;
        if(title.contains("Lista protokołów"))return android.R.drawable.ic_menu_agenda;
        if(title.contains("zwrot"))return android.R.drawable.ic_menu_revert;
        if(title.contains("Aktywuj"))return android.R.drawable.ic_menu_preferences;
        if(title.contains("Pobierz"))return android.R.drawable.stat_sys_download_done;
        return android.R.drawable.ic_menu_view;
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
        root.removeAllViews(); root.setOrientation(1); root.setPadding(18,16,18,18); root.setBackgroundColor(0xfff3f6fb);
        TextView heading=new TextView(this); heading.setText("REZERWACJE"); heading.setTextSize(25); heading.setTypeface(null,1); heading.setTextColor(0xff123b68); root.addView(heading);
        Calendar today=Calendar.getInstance();
        final String[] month={String.format(Locale.US,"%04d-%02d",today.get(Calendar.YEAR),today.get(Calendar.MONTH)+1)};
        TextView monthLabel=new TextView(this); monthLabel.setTextSize(16); monthLabel.setTextColor(0xff526174); monthLabel.setPadding(0,8,0,8); root.addView(monthLabel);
        LinearLayout monthBar=new LinearLayout(this); monthBar.setGravity(Gravity.CENTER_VERTICAL);
        Button previous=new Button(this); previous.setText("‹"); Button selectMonth=new Button(this); selectMonth.setText("Wybierz miesiąc"); Button next=new Button(this); next.setText("›");
        monthBar.addView(previous); monthBar.addView(selectMonth,new LinearLayout.LayoutParams(0,-2,1)); monthBar.addView(next); root.addView(monthBar);

        final Calendar[] reservationStart={ (Calendar)today.clone() },reservationEnd={ (Calendar)today.clone() };
        LinearLayout dateRange=new LinearLayout(this);dateRange.setGravity(Gravity.CENTER_VERTICAL);
        Button startDateButton=new Button(this);Button endDateButton=new Button(this);
        Runnable updateDateButtons=()->{SimpleDateFormat df=new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault());startDateButton.setText("OD  "+df.format(reservationStart[0].getTime()));endDateButton.setText("DO  "+df.format(reservationEnd[0].getTime()));};
        dateRange.addView(startDateButton,new LinearLayout.LayoutParams(0,-2,1));dateRange.addView(endDateButton,new LinearLayout.LayoutParams(0,-2,1));root.addView(dateRange);
        startDateButton.setOnClickListener(v->{Calendar c=(Calendar)reservationStart[0].clone();new android.app.DatePickerDialog(this,(view,y,m,d)->{reservationStart[0].set(y,m,d);if(reservationEnd[0].before(reservationStart[0]))reservationEnd[0]=(Calendar)reservationStart[0].clone();updateDateButtons.run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});
        endDateButton.setOnClickListener(v->{Calendar c=(Calendar)reservationEnd[0].clone();new android.app.DatePickerDialog(this,(view,y,m,d)->{Calendar chosen=(Calendar)reservationEnd[0].clone();chosen.set(y,m,d);if(chosen.before(reservationStart[0])){toast("Data zakończenia nie może być wcześniejsza niż początek.");return;}reservationEnd[0]=chosen;updateDateButtons.run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});
        updateDateButtons.run();
        Spinner equipment=new Spinner(this); root.addView(equipment);
        Runnable fillEquipment=()->{
            JSONArray a=store.equipment(); ArrayList<String> names=new ArrayList<>(); names.add("— Wybierz sprzęt —");
            for(int i=0;i<a.length();i++){String n=a.optString(i).trim();if(!n.isEmpty())names.add(n);}
            ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);equipment.setAdapter(ad);
        };
        fillEquipment.run();
        addButton("＋ Dodaj nowy sprzęt do wspólnej listy",()->{
            if(!"admin".equals(getSharedPreferences("rentmor",0).getString("role",""))){toast("Nowy sprzęt może dodawać tylko administrator.");return;}
            EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Nazwa sprzętu");
            new AlertDialog.Builder(this).setTitle("Dodaj sprzęt").setMessage("Po zapisaniu pojawi się na wspólnej liście RentMor.").setView(name)
                .setNegativeButton("Anuluj",null).setPositiveButton("Dodaj",(d,w)->{
                    String value=name.getText().toString().trim().replaceAll("\\s+"," ");
                    if(value.isEmpty()){toast("Wpisz nazwę sprzętu.");return;}
                    try{JSONObject op=new JSONObject();op.put("id",UUID.randomUUID().toString());op.put("name",value);store.enqueue("equipment_add",op);
                        if(isOnline())new Thread(()->{try{sync.sync();runOnUiThread(()->{fillEquipment.run();int pos=((ArrayAdapter)equipment.getAdapter()).getPosition(value);if(pos>=0)equipment.setSelection(pos);toast("Dodano sprzęt do wspólnej listy.");});}catch(Exception ex){runOnUiThread(()->toast("Nie udało się dodać sprzętu: "+ex.getMessage()));}}).start();
                        else toast("Zapisano do wysłania po odzyskaniu internetu.");
                    }catch(Exception ex){toast("Nie udało się dodać sprzętu.");}
                }).show();
        });
        EditText time=new EditText(this);time.setSingleLine(true);time.setHint("Godzina (np. 18:00)");time.setInputType(2);root.addView(time);
        EditText phone=new EditText(this);phone.setSingleLine(true);phone.setHint("Telefon");phone.setInputType(3);root.addView(phone);
        TextView hint=new TextView(this);hint.setText("Ustaw datę OD–DO. Rezerwacja może obejmować kilka dni.");hint.setTextSize(12);hint.setTextColor(0xff718096);root.addView(hint);

        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);list.setPadding(0,10,0,8);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        final Runnable[] render=new Runnable[1];
        render[0]=()->{
            Calendar selected=parseReservationMonth(month[0]);monthLabel.setText(new SimpleDateFormat("LLLL yyyy",new Locale("pl","PL")).format(selected.getTime()).toUpperCase(new Locale("pl","PL")));list.removeAllViews();
            JSONArray data=store.reservations(month[0]);ArrayList<JSONObject> items=new ArrayList<>();for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null)items.add(item);}
            Collections.sort(items,(a,b)->{int da=reservationDay(a.optString("text")),db=reservationDay(b.optString("text"));if(da!=db)return Integer.compare(da,db);return Integer.compare(reservationHour(a.optString("text")),reservationHour(b.optString("text")));});
            int shown=0;
            for(JSONObject item:items){if(item.optBoolean("settled"))continue;shown++;
                boolean done=item.optBoolean("done");
                LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(14,13,14,13);row.setBackground(roundBg(Color.WHITE,16));row.setElevation(2);
                LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
                CheckBox check=new CheckBox(this);check.setChecked(done);check.setText("");head.addView(check,new LinearLayout.LayoutParams(34,-2));
                TextView dayNumber=new TextView(this);dayNumber.setText(reservationDateRangeShort(item));dayNumber.setTextSize(25);dayNumber.setTypeface(null,1);dayNumber.setTextColor(0xff123b68);dayNumber.setGravity(Gravity.CENTER);dayNumber.setBackground(roundBg(0xffe8f0fa,10));LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(52,52);dp.rightMargin=11;head.addView(dayNumber,dp);
                LinearLayout dayCol=new LinearLayout(this);dayCol.setOrientation(1);dayCol.setGravity(Gravity.CENTER_VERTICAL);
                TextView dayCaption=new TextView(this);dayCaption.setText(reservationMonthRangeLabel(item));dayCaption.setTextSize(12);dayCaption.setTextColor(0xff64748b);dayCol.addView(dayCaption);
                TextView dayLabel=new TextView(this);dayLabel.setText("OKRES REZERWACJI");dayLabel.setTextSize(10);dayLabel.setTypeface(null,1);dayLabel.setTextColor(0xff123b68);dayCol.addView(dayLabel);head.addView(dayCol,new LinearLayout.LayoutParams(0,-2,1));
                TextView state=new TextView(this);state.setText(done?"WYDANE":"DO WYDANIA");state.setTextSize(10);state.setTypeface(null,1);state.setTextColor(done?0xff166534:0xff925400);state.setPadding(9,7,9,7);state.setBackground(roundBg(done?0xffe7f5e9:0xfffff1d6,8));head.addView(state);row.addView(head);
                String dateLabel=reservationDateRangeLabel(item);
                LinearLayout dateChips=new LinearLayout(this);dateChips.setGravity(Gravity.CENTER_VERTICAL);dateChips.setPadding(0,10,0,0);
                String[] dateParts=dateLabel.split("\\s+•\\s+");
                if(dateParts.length==2){
                    TextView fromChip=new TextView(this);fromChip.setText(dateParts[0]);fromChip.setTextSize(12);fromChip.setTypeface(null,1);fromChip.setTextColor(0xff087e8b);fromChip.setPadding(10,8,10,8);fromChip.setBackground(roundBg(0xffe3f6f5,9));
                    TextView toChip=new TextView(this);toChip.setText(dateParts[1]);toChip.setTextSize(12);toChip.setTypeface(null,1);toChip.setTextColor(0xff245b91);toChip.setPadding(10,8,10,8);toChip.setBackground(roundBg(0xffe9f1fb,9));
                    dateChips.addView(fromChip);LinearLayout.LayoutParams toParams=new LinearLayout.LayoutParams(-2,-2);toParams.leftMargin=7;dateChips.addView(toChip,toParams);
                }else{TextView legacyDate=new TextView(this);legacyDate.setText(dateLabel);legacyDate.setTextSize(12);legacyDate.setTypeface(null,1);legacyDate.setTextColor(0xff526174);legacyDate.setPadding(0,8,0,0);dateChips.addView(legacyDate);}
                row.addView(dateChips);
                TextView equipmentTitle=new TextView(this);equipmentTitle.setText(reservationEquipment(item));equipmentTitle.setTextSize(18);equipmentTitle.setTypeface(null,1);equipmentTitle.setTextColor(done?0xff64748b:0xff172b43);equipmentTitle.setPadding(0,10,0,10);row.addView(equipmentTitle);
                LinearLayout details=new LinearLayout(this);details.setGravity(Gravity.CENTER_VERTICAL);details.setPadding(0,0,0,10);
                TextView timeBadge=new TextView(this);String timeText=reservationTimeForItem(item);timeBadge.setText("◷  "+(timeText.isEmpty()?"GODZINA NIEUSTALONA":timeText));timeBadge.setTextSize(13);timeBadge.setTypeface(null,1);timeBadge.setTextColor(0xff7c4a03);timeBadge.setPadding(10,8,10,8);timeBadge.setBackground(roundBg(0xfffff3df,9));details.addView(timeBadge);row.addView(details);
                String ph=reservationPhoneForItem(item);if(!ph.isEmpty()){LinearLayout phoneLine=new LinearLayout(this);phoneLine.setGravity(Gravity.CENTER_VERTICAL);phoneLine.setPadding(0,0,0,11);TextView phoneLabel=new TextView(this);phoneLabel.setText("TELEFON   ");phoneLabel.setTextSize(10);phoneLabel.setTypeface(null,1);phoneLabel.setTextColor(0xff64748b);phoneLine.addView(phoneLabel);TextView phoneText=new TextView(this);phoneText.setText(ph);phoneText.setTextSize(17);phoneText.setTypeface(null,1);phoneText.setTextColor(0xff123b68);phoneLine.addView(phoneText);row.addView(phoneLine);}
                LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);Button edit=new Button(this);edit.setText("EDYTUJ");actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
                Button settle=new Button(this);settle.setText("ROZLICZ");actions.addView(settle,new LinearLayout.LayoutParams(0,-2,1));settle.setEnabled("admin".equals(getSharedPreferences("rentmor",0).getString("role","")));row.addView(actions);
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=11;list.addView(row,rp);
                check.setOnCheckedChangeListener((button,checked)->{try{item.put("done",checked);store.saveReservation(item);store.enqueue("reservation_save",item);ReservationsWidgetProvider.refresh(this);if(isOnline())syncNow();render[0].run();}catch(Exception ex){toast("Nie udało się zapisać zmiany.");}});
                edit.setOnClickListener(v->{
                    LinearLayout f=new LinearLayout(this);f.setOrientation(1);EditText e=new EditText(this);e.setSingleLine(true);e.setText(item.optString("text"));f.addView(e);
                    new AlertDialog.Builder(this).setTitle("Edytuj krótki opis rezerwacji").setView(f).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(d,w)->{String val=e.getText().toString().trim();if(val.isEmpty()){toast("Wpis nie może być pusty.");return;}try{item.put("text",val);store.saveReservation(item);store.enqueue("reservation_save",item);render[0].run();if(isOnline())syncNow();}catch(Exception ex){toast("Nie udało się zapisać wpisu.");}}).show();
                });
                settle.setOnClickListener(v->{
                    EditText income=new EditText(this);income.setHint("Zarobek z najmu (zł)");income.setInputType(8194);EditText loss=new EditText(this);loss.setHint("Strata / koszt (zł, np. 0)");loss.setInputType(8194);EditText note=new EditText(this);note.setHint("Za co strata? (opcjonalnie)");
                    LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(8,4,8,4);form.addView(income);form.addView(loss);form.addView(note);
                    new AlertDialog.Builder(this).setTitle("Rozlicz najem").setMessage(item.optString("text")).setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz rozliczenie",(d,w)->{try{String iv=income.getText().toString().trim().replace(',','.');String lv=loss.getText().toString().trim().replace(',','.');double earned=iv.isEmpty()?0:Double.parseDouble(iv),cost=lv.isEmpty()?0:Double.parseDouble(lv);if(earned<0||cost<0){toast("Kwoty nie mogą być ujemne.");return;}item.put("settled",true);item.put("income",earned);item.put("loss",cost);item.put("loss_note",note.getText().toString().trim());item.put("settled_at",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()));store.saveReservation(item);store.enqueue("reservation_settle",item);render[0].run();if(isOnline())syncNow();}catch(Exception ex){toast("Wpisz prawidłowe kwoty.");}}).show();
                });
            }
            if(shown==0){TextView empty=new TextView(this);empty.setText("Brak rezerwacji w tym miesiącu.");empty.setTextColor(0xff718096);empty.setPadding(8,20,8,20);list.addView(empty);}
        };
        previous.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);c.add(Calendar.MONTH,-1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(c.getTime());render[0].run();});
        next.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);c.add(Calendar.MONTH,1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(c.getTime());render[0].run();});
        selectMonth.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);new DatePickerDialog(this,(d,y,m,dd)->{month[0]=String.format(Locale.US,"%04d-%02d",y,m+1);render[0].run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),1).show();});
        addButton("＋ DODAJ REZERWACJĘ",()->{
            String tm=time.getText().toString().trim(),ph=phone.getText().toString().trim();
            if(equipment.getSelectedItemPosition()<=0){toast("Wybierz sprzęt z listy.");return;}
            if(!tm.isEmpty()&&!tm.matches("\\d{1,2}(:[0-5]\\d)?")){toast("Podaj godzinę, np. 18:00.");return;}
            String eq=String.valueOf(equipment.getSelectedItem());String shortTime=tm.isEmpty()?"":(tm.contains(":")?tm:tm+":00");
            SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd",Locale.US);String from=iso.format(reservationStart[0].getTime()),to=iso.format(reservationEnd[0].getTime());
            String value=from+" – "+to+(shortTime.isEmpty()?"":" "+shortTime)+" • "+eq+(ph.isEmpty()?"":" • "+ph);
            try{JSONObject item=new JSONObject();item.put("id",UUID.randomUUID().toString());item.put("month",new SimpleDateFormat("yyyy-MM",Locale.US).format(reservationStart[0].getTime()));item.put("text",value);item.put("equipment",eq);item.put("day",reservationStart[0].get(Calendar.DAY_OF_MONTH));item.put("start_date",from);item.put("end_date",to);item.put("end_day",reservationEnd[0].get(Calendar.DAY_OF_MONTH));item.put("end_month",new SimpleDateFormat("yyyy-MM",Locale.US).format(reservationEnd[0].getTime()));item.put("time",shortTime);item.put("phone",ph);item.put("done",false);store.saveReservation(item);store.enqueue("reservation_save",item);phone.setText("");time.setText("");render[0].run();if(isOnline())syncNow();}
            catch(Exception ex){toast("Nie udało się dodać rezerwacji.");}
        });
        addButton("WRÓĆ",()->showHome());render[0].run();
    }

    private void expensePage() {
        if(!"admin".equals(getSharedPreferences("rentmor",0).getString("role",""))){toast("Ta funkcja jest dostępna tylko dla administratora.");showHome();return;}
        root.removeAllViews();root.setOrientation(1);root.setPadding(18,16,18,18);root.setBackgroundColor(0xfff3f6fb);
        TextView heading=new TextView(this);heading.setText("POZOSTAŁE KOSZTY");heading.setTextSize(24);heading.setTypeface(null,1);heading.setTextColor(0xff123b68);root.addView(heading);
        Calendar now=Calendar.getInstance();final String[] month={String.format(Locale.US,"%04d-%02d",now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1)};
        TextView monthLabel=new TextView(this);monthLabel.setTextSize(15);monthLabel.setTextColor(0xff526174);monthLabel.setPadding(0,8,0,8);root.addView(monthLabel);
        LinearLayout bar=new LinearLayout(this);Button prev=new Button(this);prev.setText("‹");Button pick=new Button(this);pick.setText("Wybierz miesiąc");Button next=new Button(this);next.setText("›");bar.addView(prev);bar.addView(pick,new LinearLayout.LayoutParams(0,-2,1));bar.addView(next);root.addView(bar);
        EditText name=field("Nazwa kosztu, np. paliwo, naprawa, reklama");EditText amount=field("Kwota (zł)");amount.setInputType(8194);
        EditText date=dateField("Data poniesienia kosztu",new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date()));
        Spinner type=new Spinner(this);ArrayAdapter<String> ta=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Koszt bieżący","Zakup sprzętu / inwestycja"});ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);type.setAdapter(ta);root.addView(type);
        EditText note=field("Opis / uwagi (opcjonalnie)");
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(1);list.setPadding(0,10,0,10);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        final Runnable[] render=new Runnable[1];render[0]=()->{Calendar c=parseReservationMonth(month[0]);monthLabel.setText(new SimpleDateFormat("LLLL yyyy",new Locale("pl","PL")).format(c.getTime()).toUpperCase(new Locale("pl","PL")));list.removeAllViews();
            JSONArray arr=store.expenses(month[0]);double total=0,inv=0;
            for(int i=0;i<arr.length();i++){JSONObject e=arr.optJSONObject(i);if(e==null)continue;double v=e.optDouble("amount");if("investment".equals(e.optString("type")))inv+=v;else total+=v;
                LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(13,11,13,11);card.setBackground(roundBg(Color.WHITE,12));
                TextView t=new TextView(this);t.setText(e.optString("name")+"  •  "+String.format(Locale.US,"%.2f zł",v));t.setTextSize(15);t.setTypeface(null,1);t.setTextColor(0xff172b43);card.addView(t);
                TextView d=new TextView(this);d.setText(e.optString("date")+"  •  "+("investment".equals(e.optString("type"))?"Zakup sprzętu / inwestycja":"Koszt bieżący")+(e.optString("note").isEmpty()?"":"\n"+e.optString("note")));d.setTextSize(12);d.setTextColor(0xff718096);card.addView(d);
                LinearLayout actions=new LinearLayout(this);Button edit=new Button(this);edit.setText("Edytuj");Button del=new Button(this);del.setText("Usuń");actions.addView(edit);actions.addView(del);card.addView(actions);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=7;list.addView(card,lp);
                edit.setOnClickListener(view->editExpense(e,()->render[0].run()));del.setOnClickListener(view->new AlertDialog.Builder(this).setTitle("Usunąć koszt?").setMessage(e.optString("name")).setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(di,w)->{try{JSONObject op=new JSONObject();op.put("id",e.optString("id"));store.enqueue("expense_delete",op);store.deleteExpense(e.optString("id"));render[0].run();if(isOnline())syncNow();}catch(Exception ex){toast("Nie udało się usunąć kosztu.");}}).show());
            }
            addStatCard(list,"KOSZTY BIEŻĄCE",String.format(Locale.US,"%.2f zł",total),0xffb45309);addStatCard(list,"ZAKUPY SPRZĘTU",String.format(Locale.US,"%.2f zł",inv),0xffb45309);
        };
        addButton("ZAPISZ KOSZT",()->{try{String nm=name.getText().toString().trim();String av=amount.getText().toString().trim().replace(',','.');if(nm.isEmpty()||av.isEmpty())throw new Exception();double val=Double.parseDouble(av);if(val<=0)throw new Exception();
            Date dt=new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).parse(date.getText().toString());String iso=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(dt);String mo=iso.substring(0,7);
            JSONObject e=new JSONObject();e.put("id",UUID.randomUUID().toString());e.put("date",iso);e.put("month",mo);e.put("name",nm);e.put("amount",val);e.put("type",type.getSelectedItemPosition()==1?"investment":"expense");e.put("note",note.getText().toString().trim());
            store.saveExpense(e);store.enqueue("expense_save",e);month[0]=mo;name.setText("");amount.setText("");note.setText("");render[0].run();if(isOnline())syncNow();toast("Koszt zapisany.");
        }catch(Exception ex){toast("Sprawdź nazwę, datę i kwotę kosztu.");}});
        prev.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);c.add(Calendar.MONTH,-1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(c.getTime());render[0].run();});
        next.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);c.add(Calendar.MONTH,1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(c.getTime());render[0].run();});
        pick.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);new DatePickerDialog(this,(d,y,m,day)->{month[0]=String.format(Locale.US,"%04d-%02d",y,m+1);render[0].run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),1).show();});
        addButton("WRÓĆ",()->showHome());render[0].run();
    }
    private void editExpense(JSONObject old,Runnable refresh) {
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);EditText n=new EditText(this);n.setHint("Nazwa kosztu");n.setText(old.optString("name"));EditText a=new EditText(this);a.setHint("Kwota zł");a.setInputType(8194);a.setText(String.valueOf(old.optDouble("amount")));EditText d=new EditText(this);d.setHint("Data RRRR-MM-DD");d.setText(old.optString("date"));EditText note=new EditText(this);note.setHint("Opis");note.setText(old.optString("note"));Spinner typ=new Spinner(this);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Koszt bieżący","Zakup sprzętu / inwestycja"});typ.setAdapter(ad);typ.setSelection("investment".equals(old.optString("type"))?1:0);form.addView(n);form.addView(a);form.addView(d);form.addView(typ);form.addView(note);
        new AlertDialog.Builder(this).setTitle("Edytuj koszt").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(di,w)->{try{String nm=n.getText().toString().trim(),ds=d.getText().toString().trim(),av=a.getText().toString().trim().replace(',','.');double val=Double.parseDouble(av);if(nm.isEmpty()||val<=0||!ds.matches("\\d{4}-\\d{2}-\\d{2}"))throw new Exception();old.put("name",nm);old.put("date",ds);old.put("month",ds.substring(0,7));old.put("amount",val);old.put("type",typ.getSelectedItemPosition()==1?"investment":"expense");old.put("note",note.getText().toString().trim());store.saveExpense(old);store.enqueue("expense_save",old);if(isOnline())syncNow();refresh.run();}catch(Exception ex){toast("Nieprawidłowe dane kosztu.");}}).show();
    }

    private void reservationStatsPage() {
        root.removeAllViews(); root.setOrientation(1); root.setPadding(18,16,18,18); root.setBackgroundColor(0xfff3f6fb);
        TextView heading=new TextView(this); heading.setText("STATYSTYKI NAJMU"); heading.setTextSize(24); heading.setTypeface(null,1); heading.setTextColor(0xff123b68); root.addView(heading);
        Calendar now=Calendar.getInstance(); final String[] month={String.format(Locale.US,"%04d-%02d",now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1)};
        TextView monthLabel=new TextView(this); monthLabel.setTextSize(16); monthLabel.setTextColor(0xff526174); monthLabel.setPadding(0,8,0,8); root.addView(monthLabel);
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        Button prev=new Button(this); prev.setText("‹"); Button pick=new Button(this); pick.setText("Wybierz miesiąc"); Button next=new Button(this);
        next.setText("›"); bar.addView(prev); bar.addView(pick,new LinearLayout.LayoutParams(0,-2,1)); bar.addView(next); root.addView(bar);
        ScrollView scroll=new ScrollView(this); LinearLayout content=new LinearLayout(this); content.setOrientation(1); content.setPadding(0,10,0,10); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()->{
            Calendar selected=parseReservationMonth(month[0]); monthLabel.setText(new SimpleDateFormat("LLLL yyyy",new Locale("pl","PL")).format(selected.getTime()).toUpperCase(new Locale("pl","PL")));
            content.removeAllViews(); JSONArray data=store.reservations(month[0]); JSONArray costData=store.expenses(month[0]); double income=0,loss=0,otherCosts=0,investments=0; int count=0;
            for(int ci=0;ci<costData.length();ci++){JSONObject ce=costData.optJSONObject(ci);if(ce==null)continue;if("investment".equals(ce.optString("type")))investments+=ce.optDouble("amount");else otherCosts+=ce.optDouble("amount");}
            LinkedHashMap<String,double[]> byEquipment=new LinkedHashMap<>(); ArrayList<JSONObject> history=new ArrayList<>();
            for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i); if(item==null||!item.optBoolean("settled"))continue;
                double inc=item.optDouble("income"), los=item.optDouble("loss"); income+=inc; loss+=los; count++; history.add(item);
                String name=item.optString("text").replaceFirst("^\\s*\\d{1,2}\\s+","").replaceFirst("\\s+\\d{1,2}\\s*/.*$","").trim();
                if(name.isEmpty())name="Pozostałe / nierozpoznane";
                double[] sums=byEquipment.get(name); if(sums==null){sums=new double[4];byEquipment.put(name,sums);} sums[0]++;sums[1]+=inc;sums[2]+=los;sums[3]+=inc-los;
            }
            addStatCard(content,"PRZYCHÓD",String.format(Locale.US,"%.2f zł",income),0xff172b43);
            addStatCard(content,"STRATY Z NAJMU",String.format(Locale.US,"%.2f zł",loss),0xffb42318);
            addStatCard(content,"KOSZTY BIEŻĄCE",String.format(Locale.US,"%.2f zł",otherCosts),0xffb45309);
            addStatCard(content,"ZAKUPY SPRZĘTU / INWESTYCJE",String.format(Locale.US,"%.2f zł",investments),0xffb45309);
            double finalResult=income-loss-otherCosts-investments;
            addStatCard(content,"WYNIK PO WSZYSTKICH KOSZTACH",String.format(Locale.US,"%.2f zł",finalResult),finalResult<0?0xffb42318:0xff18794e);
            TextView countText=new TextView(this); countText.setText("Rozliczone najmy: "+count); countText.setTextColor(0xff526174); countText.setPadding(4,6,4,16); content.addView(countText);
            TextView sub=new TextView(this); sub.setText("WYNIK WEDŁUG SPRZĘTU"); sub.setTextSize(16); sub.setTypeface(null,1); sub.setTextColor(0xff123b68); sub.setPadding(2,12,2,8); content.addView(sub);
            if(byEquipment.isEmpty()){TextView empty=new TextView(this);empty.setText("Brak rozliczeń w tym miesiącu.");empty.setTextColor(0xff718096);content.addView(empty);}
            ArrayList<String> names=new ArrayList<>(byEquipment.keySet()); Collections.sort(names,(x,y)->Double.compare(byEquipment.get(y)[3],byEquipment.get(x)[3]));
            for(String name:names){double[] v=byEquipment.get(name);TextView line=new TextView(this);line.setText(name+"\nNajmy: "+(int)v[0]+"  •  Przychód: "+String.format(Locale.US,"%.2f",v[1])+" zł\nStraty: "+String.format(Locale.US,"%.2f",v[2])+" zł  •  Wynik: "+String.format(Locale.US,"%.2f",v[3])+" zł");line.setTextSize(14);line.setTextColor(0xff26384d);line.setPadding(14,12,14,12);line.setBackground(roundBg(Color.WHITE,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=7;content.addView(line,lp);}
            TextView hist=new TextView(this);hist.setText("HISTORIA ROZLICZEŃ ("+history.size()+")");hist.setTextSize(16);hist.setTypeface(null,1);hist.setTextColor(0xff123b68);hist.setPadding(2,18,2,8);content.addView(hist);
            Collections.reverse(history);
            for(JSONObject item:history){double inc=item.optDouble("income"),los=item.optDouble("loss");TextView line=new TextView(this);line.setText(item.optString("text")+"\nPrzychód: "+String.format(Locale.US,"%.2f",inc)+" zł  •  Strata: "+String.format(Locale.US,"%.2f",los)+" zł\nWynik: "+String.format(Locale.US,"%.2f",inc-los)+" zł"+(item.optString("loss_note").isEmpty()?"":"  •  "+item.optString("loss_note")));line.setTextSize(13);line.setTextColor(0xff26384d);line.setPadding(14,12,14,12);line.setBackground(roundBg(Color.WHITE,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=7;content.addView(line,lp);}
        };
        prev.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);c.add(Calendar.MONTH,-1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(c.getTime());render.run();});
        next.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);c.add(Calendar.MONTH,1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(c.getTime());render.run();});
        pick.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);new DatePickerDialog(this,(d,y,m,day)->{month[0]=String.format(Locale.US,"%04d-%02d",y,m+1);render.run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),1).show();});
        addButton("WRÓĆ",()->showHome()); render.run();
    }
    private void addStatCard(LinearLayout parent,String label,String value,int color) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(16,12,16,12);card.setBackground(roundBg(Color.WHITE,14));
        TextView l=new TextView(this);l.setText(label);l.setTextSize(11);l.setTypeface(null,1);l.setTextColor(0xff718096);card.addView(l);
        TextView v=new TextView(this);v.setText(value);v.setTextSize(22);v.setTypeface(null,1);v.setTextColor(color);v.setPadding(0,4,0,0);card.addView(v);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=8;parent.addView(card,lp);
    }

    private String reservationEquipment(JSONObject item) {
        String value=item.optString("equipment","").trim();if(!value.isEmpty())return cleanReservationEquipment(value);
        String raw=item.optString("text","");String[] parts=raw.split("•");
        if(parts.length>=2)return cleanReservationEquipment(parts[1].trim());
        java.util.regex.Matcher legacy=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+(.+?)\\s+\\d{1,2}\\s*/\\s*.+$").matcher(raw);
        if(legacy.find())return cleanReservationEquipment(legacy.group(1));
        return cleanReservationEquipment(raw);
    }
    private String cleanReservationEquipment(String value) {
        return value.replaceFirst("(?i)\\s+-\\s+wynajem.*$","").replaceFirst("\\s+\\|.*$","").trim();
    }
    private String reservationTimeForItem(JSONObject item) {
        String value=item.optString("time","").trim();if(!value.isEmpty())return value;
        String raw=item.optString("text","");java.util.regex.Matcher modern=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+(\\d{1,2}:\\d{2})").matcher(raw);
        if(modern.find())return modern.group(1);
        java.util.regex.Matcher legacy=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+.+?\\s+(\\d{1,2})\\s*/").matcher(raw);
        return legacy.find()?String.format(Locale.US,"%02d:00",Integer.parseInt(legacy.group(1))):"";
    }
    private String reservationPhoneForItem(JSONObject item) {
        String value=item.optString("phone","").trim();if(!value.isEmpty())return value;
        String raw=item.optString("text","");String[] parts=raw.split("•");
        if(parts.length>=3)return parts[parts.length-1].trim();
        java.util.regex.Matcher legacy=java.util.regex.Pattern.compile("/\\s*(.+)$").matcher(raw);
        return legacy.find()?legacy.group(1).trim():"";
    }
    private String reservationDateRangeLabel(JSONObject item) {
        String from=item.optString("start_date","").trim(),to=item.optString("end_date","").trim();
        if(!from.matches("\\d{4}-\\d{2}-\\d{2}")) {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*[–-]\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(item.optString("text",""));
            if(m.find()){from=m.group(1);to=m.group(2);}
        }
        if(!from.matches("\\d{4}-\\d{2}-\\d{2}")) return "OKRES REZERWACJI: "+reservationDateRangeShort(item);
        if(!to.matches("\\d{4}-\\d{2}-\\d{2}")) to=from;
        String a=from.substring(8,10)+"."+from.substring(5,7)+"."+from.substring(0,4);
        String b=to.substring(8,10)+"."+to.substring(5,7)+"."+to.substring(0,4);
        return "OD: "+a+"    •    DO: "+b;
    }
    private String reservationDateRangeShort(JSONObject item) {
        String from=item.optString("start_date","").trim(),to=item.optString("end_date","").trim();
        if(from.matches("\\d{4}-\\d{2}-\\d{2}")){String a=from.substring(8,10);String b=to.matches("\\d{4}-\\d{2}-\\d{2}")?to.substring(8,10):a;return a.equals(b)?a:a+"–"+b;}
        int legacyDay=reservationDay(item.optString("text"));return legacyDay>=1&&legacyDay<=31?String.valueOf(legacyDay):String.valueOf(item.optInt("day",1));
    }
    private String reservationMonthRangeLabel(JSONObject item) {
        String from=item.optString("start_date",""),to=item.optString("end_date","");
        if(from.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try{SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd",Locale.US),out=new SimpleDateFormat("LLL",new Locale("pl","PL"));Date a=in.parse(from);Date b=to.matches("\\d{4}-\\d{2}-\\d{2}")?in.parse(to):a;return out.format(a).toUpperCase(new Locale("pl","PL"))+(new SimpleDateFormat("yyyy-MM",Locale.US).format(a).equals(new SimpleDateFormat("yyyy-MM",Locale.US).format(b))?"":" – "+out.format(b).toUpperCase(new Locale("pl","PL")));}
            catch(Exception ignored){}
        }
        return reservationMonthLabel(item);
    }
    private String reservationDayNumber(JSONObject item) {
        int day=item.optInt("day",reservationDay(item.optString("text")));return day>=1&&day<=31?String.valueOf(day):"—";
    }
    private String reservationMonthLabel(JSONObject item) {
        return new SimpleDateFormat("LLLL",new Locale("pl","PL")).format(parseReservationMonth(item.optString("month",new SimpleDateFormat("yyyy-MM",Locale.US).format(new Date()))).getTime()).toUpperCase(new Locale("pl","PL"));
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