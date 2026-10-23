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
    private WebView activation; private boolean showingApp=false; private final ArrayList<Bitmap> issuePhotos=new ArrayList<>(); private final ArrayList<Bitmap> productPhotos=new ArrayList<>(); private final ArrayList<String> productSelectedPhotoUris=new ArrayList<>(); private int existingProductPhotoCount=0; private int productPhotoSession=0; private boolean productPhotosLoading=false; private LinearLayout productExistingPhotoContainer,productNewPhotoContainer; private Button productPhotoPickButton; private TextView productPhotoCount; private TextView photoCount; private SignatureView signatureView; private final AtomicBoolean syncRunning=new AtomicBoolean(false); private ConnectivityManager.NetworkCallback networkCallback;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(20,29,45));getWindow().setNavigationBarColor(Color.rgb(20,29,45));store=new LocalStore(this);sync=new SyncManager(this,store);registerNetworkSync();
        String saved=getSharedPreferences("rentmor",0).getString("cookie","");
        if(!saved.isEmpty()){showHome();openWidgetDestination();} else showActivation();
    }
    private void openWidgetDestination(){
        if(getIntent()!=null && getIntent().getBooleanExtra("open_reservations",false)){
            getIntent().removeExtra("open_reservations");
            if(root!=null) root.postDelayed(()->reservationPage(),180);
        }
    }
    private void showActivation(){
        activation=new WebView(this); activation.getSettings().setJavaScriptEnabled(true);activation.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        activation.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){
            String c=CookieManager.getInstance().getCookie("https://rentmor.pl");
            if(c!=null&&c.contains("rm_protocol_device=")){getSharedPreferences("rentmor",0).edit().putString("cookie",c).apply();activation.postDelayed(()->{showHome();openWidgetDestination();},500);}
        }});
        setContentView(activation); activation.loadUrl(HOME);
    }
    private LinearLayout homeToolsList;
    private void showHome(){
        if(siteLiveHandler!=null && siteLiveRefresh!=null)siteLiveHandler.removeCallbacks(siteLiveRefresh);
        showingApp=true;
        LinearLayout screen=new LinearLayout(this);
        screen.setOrientation(1);screen.setBackgroundColor(0xfff4f6fa);
        final int inL=dp(13),inT=dp(10),inR=dp(13),inB=dp(16);
        screen.setPadding(inL,inT,inR,inB);
        screen.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(inL+insets.getSystemWindowInsetLeft(),inT+insets.getSystemWindowInsetTop(),
                inR+insets.getSystemWindowInsetRight(),inB+insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);
        root=page;homePage=page;adminDownloadAdded=false;homeToolsList=null;
        page.setPadding(0,dp(3),0,dp(12));
        scroll.addView(page);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);
        hero.setPadding(dp(19),dp(18),dp(19),dp(17));
        hero.setBackground(roundBg(0xff17263e,18));
        TextView eyebrow=new TextView(this);
        eyebrow.setText("RENTMOR  /  PANEL ZARZĄDZANIA");
        eyebrow.setTextSize(10);eyebrow.setLetterSpacing(.15f);
        eyebrow.setTextColor(0xfffacc15);eyebrow.setTypeface(null,1);hero.addView(eyebrow);
        TextView title=new TextView(this);title.setText("Pro RentMor");
        title.setTextSize(28);title.setTypeface(null,1);title.setTextColor(Color.WHITE);
        title.setPadding(0,dp(8),0,dp(7));hero.addView(title);
        TextView heroSubtitle=new TextView(this);heroSubtitle.setText("Twoja wypożyczalnia w jednym miejscu");
        heroSubtitle.setTextSize(12);heroSubtitle.setTextColor(0xffd4deea);hero.addView(heroSubtitle);
        LinearLayout.LayoutParams heroParams=new LinearLayout.LayoutParams(-1,-2);
        heroParams.bottomMargin=dp(11);page.addView(hero,heroParams);

        LinearLayout statusCard=new LinearLayout(this);statusCard.setOrientation(0);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);statusCard.setPadding(dp(13),dp(12),dp(13),dp(12));
        statusCard.setBackground(roundBg(Color.WHITE,14));
        ImageView statusIcon=new ImageView(this);statusIcon.setImageResource(android.R.drawable.ic_popup_sync);
        statusIcon.setColorFilter(0xff35578d);statusIcon.setPadding(dp(8),dp(8),dp(8),dp(8));
        statusIcon.setBackground(roundBg(0xffe8f0fd,12));
        statusCard.addView(statusIcon,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout statusContent=new LinearLayout(this);statusContent.setOrientation(1);
        statusContent.setPadding(dp(11),0,0,0);
        TextView statusHeader=new TextView(this);statusHeader.setText("STATUS DANYCH");
        statusHeader.setTextSize(11);statusHeader.setTypeface(null,1);
        statusHeader.setTextColor(0xff526174);statusContent.addView(statusHeader);
        status=new TextView(this);
        status.setText("W telefonie: "+store.allProtocols().length()+"   •   Do wysłania: "+store.pendingCount());
        status.setTextSize(13);status.setTextColor(0xff172235);status.setPadding(0,dp(3),0,0);
        statusContent.addView(status);statusCard.addView(statusContent,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-1,-2);
        statusParams.bottomMargin=dp(8);page.addView(statusCard,statusParams);

        homeSectionTitle(page,"SZYBKI DOSTĘP","Najczęściej używane funkcje");
        LinearLayout quickRow1=homeQuickRow(page);
        homeQuickCard(quickRow1,android.R.drawable.ic_menu_my_calendar,"Rezerwacje","Wydania i rozliczenia",
            0xffffe8a2,()->reservationPage());
        homeQuickCard(quickRow1,android.R.drawable.ic_input_add,"Nowy protokół","Dodaj wydanie",
            0xffe4efff,()->newIssue());
        LinearLayout quickRow2=homeQuickRow(page);
        homeQuickCard(quickRow2,android.R.drawable.ic_menu_myplaces,"Klienci","Kontakty i notatki",
            0xffeaf2fc,()->customerPage());
        if("admin".equals(getSharedPreferences("rentmor",0).getString("role",""))){
            homeQuickCard(quickRow2,android.R.drawable.ic_menu_view,"Strona","Oferta i statystyki",
                0xffffecc0,()->sitePage());
        }else {
            homeQuickCard(quickRow2,android.R.drawable.ic_menu_agenda,"Protokoły","Wszystkie dokumenty",
                0xffedf0f5,()->protocolList());
        }

        homeSectionTitle(page,"WYNAJEM","Dokumenty i obsługa sprzętu");
        homeMenuItem(page,android.R.drawable.ic_menu_revert,"Obsłuż zwrot sprzętu",
            "Potwierdź zwrot i stan sprzętu",0xffe4efff,()->returnList());
        homeMenuItem(page,android.R.drawable.ic_menu_agenda,"Lista protokołów",
            "Przeglądaj i pobieraj dokumenty PDF",0xffedf1fa,()->protocolList());

        homeSectionTitle(page,"FINANSE I ANALITYKA","Wyniki Twojej wypożyczalni");
        homeMenuItem(page,android.R.drawable.ic_menu_sort_by_size,"Statystyki najmu",
            "Przychody, straty i wynik sprzętu",0xffffe8ac,()->reservationStatsPage());
        if("admin".equals(getSharedPreferences("rentmor",0).getString("role",""))){
            homeMenuItem(page,android.R.drawable.ic_menu_info_details,"Pozostałe koszty",
                "Naprawy, paliwo, reklama i zakupy",0xffedf0f5,()->expensePage());
        }

        homeSectionTitle(page,"NARZĘDZIA","Ustawienia i dostęp");
        homeToolsList=new LinearLayout(this);homeToolsList.setOrientation(1);page.addView(homeToolsList);
        homeMenuItem(homeToolsList,android.R.drawable.ic_popup_sync,"Synchronizuj teraz",
            "Prześlij dane na serwer",0xffe6f4ec,()->syncNow());
        homeMenuItem(homeToolsList,android.R.drawable.ic_menu_preferences,"Aktywuj / zmień urządzenie",
            "Skonfiguruj dostęp telefonu",0xffedf0f5,()->resetActivation());
        addAdminDownloadCard();
        screen.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        setContentView(screen);
        if(isOnline())syncNow();
    }
    private void homeSectionTitle(LinearLayout parent,String title,String caption){
        LinearLayout section=new LinearLayout(this);section.setOrientation(1);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(15);lp.bottomMargin=dp(9);parent.addView(section,lp);
        TextView h=new TextView(this);h.setText(title);h.setTextSize(12);
        h.setLetterSpacing(.08f);h.setTypeface(null,1);h.setTextColor(0xff172235);section.addView(h);
        TextView sub=new TextView(this);sub.setText(caption);sub.setTextSize(11);
        sub.setTextColor(0xff78869b);sub.setPadding(0,dp(2),0,0);section.addView(sub);
    }
    private LinearLayout homeQuickRow(LinearLayout parent){
        LinearLayout row=new LinearLayout(this);row.setOrientation(0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(7);parent.addView(row,lp);return row;
    }
    private void homeQuickCard(LinearLayout row,int icon,String title,String subtitle,int accent,Runnable action){
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);
        card.setPadding(dp(13),dp(12),dp(11),dp(12));card.setBackground(roundBg(Color.WHITE,15));
        card.setMinimumHeight(dp(135));card.setElevation(dp(1));
        ImageView picture=new ImageView(this);picture.setImageResource(icon);picture.setColorFilter(0xff172235);
        picture.setPadding(dp(9),dp(9),dp(9),dp(9));picture.setBackground(roundBg(accent,11));
        card.addView(picture,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView name=new TextView(this);name.setText(title);name.setTypeface(null,1);
        name.setTextSize(15);name.setTextColor(0xff172235);name.setPadding(0,dp(10),0,0);
        card.addView(name);
        TextView info=new TextView(this);info.setText(subtitle);info.setTextSize(11);
        info.setTextColor(0xff697386);info.setPadding(0,dp(3),0,0);card.addView(info);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(dp(3),0,dp(3),0);row.addView(card,lp);
        card.setOnClickListener(v->action.run());card.setClickable(true);
    }
    private void homeMenuItem(LinearLayout parent,int icon,String title,String subtitle,int accent,Runnable action){
        LinearLayout card=new LinearLayout(this);card.setOrientation(0);card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13),dp(12),dp(11),dp(12));card.setBackground(roundBg(Color.WHITE,14));
        card.setElevation(dp(1));
        ImageView image=new ImageView(this);image.setImageResource(icon);image.setColorFilter(0xff172235);
        image.setPadding(dp(9),dp(9),dp(9),dp(9));image.setBackground(roundBg(accent,11));
        card.addView(image,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(11),0,0,0);
        TextView heading=new TextView(this);heading.setText(title);heading.setTypeface(null,1);
        heading.setTextColor(0xff172235);heading.setTextSize(15);info.addView(heading);
        TextView sub=new TextView(this);sub.setText(subtitle);sub.setTextColor(0xff697386);
        sub.setTextSize(11);sub.setPadding(0,dp(3),0,0);info.addView(sub);
        card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextColor(0xff94a3b8);
        arrow.setTextSize(24);card.addView(arrow);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(7);lp.leftMargin=dp(3);lp.rightMargin=dp(3);
        parent.addView(card,lp);card.setOnClickListener(v->action.run());
    }
    private void addAdminDownloadCard(){
        if(adminDownloadAdded||homeToolsList==null)return;
        if(!"admin".equals(getSharedPreferences("rentmor",0).getString("role","")))return;
        adminDownloadAdded=true;
        homeMenuItem(homeToolsList,android.R.drawable.stat_sys_download_done,"Pobierz aplikację",
            "Instalator na kolejny telefon lub tablet",0xffffe8ac,()->{
                new AlertDialog.Builder(this).setTitle("Pobierz Pro RentMor")
                    .setMessage("Otworzyć najnowszy instalator APK?")
                    .setNegativeButton("Anuluj",null)
                    .setPositiveButton("Pobierz",(d,w)->{
                        try{
                            Intent i=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(
                                "https://github.com/Domiono/Protokol/releases/latest/download/RentMor.apk"));
                            startActivity(i);
                        }catch(Exception e){toast("Nie można otworzyć pobierania: "+e.getMessage());}
                    }).show();
            });
    }
    private android.graphics.drawable.GradientDrawable roundBg(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private void brandButton(Button b,boolean primary){
        b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,1);
        b.setTextColor(0xff172235);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(primary?0xffffcc18:0xffedf0f5);bg.setCornerRadius(12f);
        bg.setStroke(1,primary?0xfff0b900:0xffdce1e9);
        b.setBackground(bg);b.setElevation(2f);
        if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);
        b.setPadding(14,10,14,10);
    }
    private void compactReservationButton(Button b,boolean primary){
        b.setAllCaps(false);b.setSingleLine(true);b.setTextSize(12);b.setTypeface(null,1);
        b.setTextColor(0xff172235);b.setGravity(Gravity.CENTER);b.setIncludeFontPadding(false);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(primary?0xffffcc18:0xffedf0f5);bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1),primary?0xfff0b900:0xffdce1e9);b.setBackground(bg);
        b.setPadding(dp(6),dp(7),dp(6),dp(7));b.setMinHeight(dp(45));b.setMinimumHeight(dp(45));
        b.setMinWidth(0);b.setMinimumWidth(0);
        if(android.os.Build.VERSION.SDK_INT>=21){b.setStateListAnimator(null);b.setElevation(0f);}
    }
    private void menuCard(LinearLayout parent,String symbol,String title,String subtitle,Runnable action,int tint){
        LinearLayout card=new LinearLayout(this);card.setOrientation(0);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(15,14,15,14);card.setBackground(roundBg(0xffffffff,18));card.setElevation(3);
        ImageView ico=new ImageView(this);ico.setImageResource(menuIcon(title));ico.setColorFilter(0xff172235);ico.setScaleType(ImageView.ScaleType.CENTER_INSIDE);ico.setPadding(14,14,14,14);ico.setBackground(roundBg(tint,16));card.addView(ico,new LinearLayout.LayoutParams(52,52));
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
    private void addButton(String text,Runnable action){Button b=new Button(this);b.setText(text);boolean primary=text.contains("DODAJ")||text.contains("ZAPISZ")||text.contains("POTWIERDŹ");brandButton(b,primary);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=4;lp.bottomMargin=5;root.addView(b,lp);}
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

    private void sitePage() {
        root.removeAllViews(); root.setPadding(18,16,18,18);
        TextView h=new TextView(this);h.setText("STRONA RENTMOR");h.setTextSize(25);h.setTypeface(null,1);h.setTextColor(0xff172235);root.addView(h);
        TextView sub=new TextView(this);sub.setText("Zarządzaj publiczną ofertą i sprawdzaj ruch na stronie.");sub.setTextColor(0xff697386);sub.setPadding(0,5,0,14);root.addView(sub);
        menuCard(root,"▥","Statystyki strony","Odsłony, źródła wejść i popularny sprzęt",()->siteStatsPage(),0xfffff1c2);
        menuCard(root,"＋","Dodaj sprzęt","Opublikuj nową ofertę na RentMor.pl",()->productForm(null),0xfffff6d9);
        menuCard(root,"▤","Lista sprzętu","Edytuj lub usuń opublikowane oferty",()->siteProductsPage(),0xffedf0f5);
        addButton("WRÓĆ DO MENU",()->showHome());
    }
    private JSONObject siteStats() {
        try { return new JSONObject(getSharedPreferences("rentmor",0).getString("site_stats","{}")); }
        catch(Exception e) { return new JSONObject(); }
    }
    private JSONArray siteProducts() {
        try { return new JSONArray(getSharedPreferences("rentmor",0).getString("site_products","[]")); }
        catch(Exception e) { return new JSONArray(); }
    }
    private void refreshSiteData(Runnable done) {
        if(!isOnline()){toast("Brak internetu. Pokazuję ostatnio pobrane dane.");done.run();return;}
        new Thread(()->{try{sync.sync();runOnUiThread(done);}catch(Exception e){runOnUiThread(()->{toast("Nie udało się pobrać danych strony: "+e.getMessage());done.run();});}}).start();
    }
    private Handler siteLiveHandler;
    private Runnable siteLiveRefresh;
    private void siteStatsPage() {
        root.removeAllViews(); root.setPadding(14,14,14,18);
        TextView h=new TextView(this);h.setText("STATYSTYKI STRONY");h.setTextSize(23);h.setTypeface(null,1);h.setTextColor(0xff172235);root.addView(h);
        TextView info=new TextView(this);info.setText("Dane z licznika RentMor.pl");info.setTextColor(0xff697386);info.setPadding(0,4,0,10);root.addView(info);
        LinearLayout liveCards=new LinearLayout(this);liveCards.setOrientation(1);root.addView(liveCards);
        final int[] range={7};
        TextView chartTitle=new TextView(this);chartTitle.setText("ODSŁONY DZIEŃ PO DNIU");chartTitle.setTextSize(15);chartTitle.setTypeface(null,1);chartTitle.setTextColor(0xff526174);chartTitle.setPadding(2,16,2,8);root.addView(chartTitle);
        LinearLayout rangeBar=new LinearLayout(this);rangeBar.setOrientation(0);root.addView(rangeBar);
        LinearLayout chartCard=new LinearLayout(this);chartCard.setOrientation(1);chartCard.setPadding(12,12,12,12);chartCard.setBackground(roundBg(Color.WHITE,14));root.addView(chartCard);
        LinearLayout cards=new LinearLayout(this);cards.setOrientation(1);root.addView(cards);
        final View[] chart={null};
        final TextView[] liveLabel={null};
        Runnable render=()->{
            cards.removeAllViews();liveCards.removeAllViews();JSONObject st=siteStats();
            JSONObject days=st.optJSONObject("days");if(days==null)days=new JSONObject();
            String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
            long todayViews=days.optLong(today,0);
            LinearLayout live=new LinearLayout(this);live.setOrientation(1);live.setPadding(14,12,14,12);live.setBackground(roundBg(0xffeaf8ef,13));
            TextView liveHead=new TextView(this);liveHead.setText("DZISIAJ • ODSŁONY");liveHead.setTextSize(12);liveHead.setTypeface(null,1);liveHead.setTextColor(0xff267044);live.addView(liveHead);
            TextView liveValue=new TextView(this);liveValue.setText(String.valueOf(todayViews));liveValue.setTextSize(29);liveValue.setTypeface(null,1);liveValue.setTextColor(0xff14532d);live.addView(liveValue);liveLabel[0]=liveValue;
            TextView liveHint=new TextView(this);liveHint.setText("Automatyczne odświeżanie co 30 sekund");liveHint.setTextSize(11);liveHint.setTextColor(0xff43805b);live.addView(liveHint);liveCards.addView(live);
            LinearLayout presence=new LinearLayout(this);presence.setOrientation(1);presence.setPadding(14,12,14,12);
            presence.setBackground(roundBg(0xffeaf1ff,13));
            TextView presenceTitle=new TextView(this);presenceTitle.setText("OSOBY TERAZ NA STRONIE");
            presenceTitle.setTextSize(12);presenceTitle.setTypeface(null,1);presenceTitle.setTextColor(0xff27549a);presence.addView(presenceTitle);
            TextView presenceValue=new TextView(this);
            presenceValue.setText(st.has("online_visitors")?String.valueOf(st.optInt("online_visitors",0)):"—");
            presenceValue.setTextSize(29);presenceValue.setTypeface(null,1);presenceValue.setTextColor(0xff1c3f75);presence.addView(presenceValue);
            TextView presenceHint=new TextView(this);presenceHint.setText("Aktywne przeglądarki w ciągu ostatnich 95 sekund");
            presenceHint.setTextSize(11);presenceHint.setTextColor(0xff43698e);presence.addView(presenceHint);
            LinearLayout.LayoutParams presenceParams=new LinearLayout.LayoutParams(-1,-2);presenceParams.topMargin=dp(8);
            liveCards.addView(presence,presenceParams);
            addMetric(cards,"Wszystkie odsłony",String.valueOf(st.optLong("total_views",0)));
            addMetric(cards,"Unikalni odwiedzający",String.valueOf(st.optLong("unique_visitors",0)));
            addMetric(cards,"Kliknięcia telefonu",String.valueOf(st.optLong("total_phone_clicks",0)));
            JSONObject sources=st.optJSONObject("sources");long search=0,direct=0,other=0;
            if(sources!=null){Iterator<String> it=sources.keys();while(it.hasNext()){JSONObject day=sources.optJSONObject(it.next());if(day!=null){search+=day.optLong("search_social",0);direct+=day.optLong("direct",0);other+=day.optLong("other_ext",0);}}}
            addMetric(cards,"Wejścia z wyszukiwarki i social",String.valueOf(search));addMetric(cards,"Wejścia bezpośrednie",String.valueOf(direct));addMetric(cards,"Pozostałe źródła",String.valueOf(other));
            TextView popular=new TextView(this);popular.setText("NAJPOPULARNIEJSZY SPRZĘT");popular.setTextSize(14);popular.setTypeface(null,1);popular.setTextColor(0xff526174);popular.setPadding(2,16,2,8);cards.addView(popular);
            JSONObject pv=st.optJSONObject("product_views");if(pv==null||pv.length()==0){TextView empty=new TextView(this);empty.setText("Brak danych o odsłonach produktów.");cards.addView(empty);}else{
                ArrayList<String> ids=new ArrayList<>();Iterator<String> it=pv.keys();while(it.hasNext())ids.add(it.next());Collections.sort(ids,(a,b)->Long.compare(pv.optLong(b),pv.optLong(a)));
                JSONArray products=siteProducts();for(int i=0;i<Math.min(5,ids.size());i++){String id=ids.get(i),name=id;for(int j=0;j<products.length();j++){JSONObject p=products.optJSONObject(j);if(p!=null&&id.equals(p.optString("id"))){name=p.optString("name",id);break;}}addMetric(cards,name,pv.optString(id)+" odsłon");}
            }
            chartCard.removeAllViews();
            final ArrayList<String> labels=new ArrayList<>(), dateKeys=new ArrayList<>();
            final ArrayList<Integer> values=new ArrayList<>();
            Calendar cal=Calendar.getInstance();cal.add(Calendar.DAY_OF_YEAR,-(range[0]-1));
            SimpleDateFormat keyFormat=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
            SimpleDateFormat shortFormat=new SimpleDateFormat("dd.MM",Locale.getDefault());
            for(int i=0;i<range[0];i++){
                String day=keyFormat.format(cal.getTime());dateKeys.add(day);
                labels.add(shortFormat.format(cal.getTime()));
                values.add((int)Math.min(Integer.MAX_VALUE,days.optLong(day,0)));
                cal.add(Calendar.DAY_OF_YEAR,1);
            }
            final int[] selected={Math.max(0,values.size()-1)};
            TextView detail=new TextView(this);
            detail.setTextSize(17);detail.setTypeface(null,1);detail.setTextColor(0xff172235);
            detail.setBackground(roundBg(0xfffff6d9,10));detail.setPadding(dp(12),dp(10),dp(12),dp(10));
            detail.setText(labels.get(selected[0])+"  •  "+values.get(selected[0])+" odsłon");
            chartCard.addView(detail,new LinearLayout.LayoutParams(-1,-2));
            TextView tip=new TextView(this);
            tip.setText("Dotknij słupka, aby zobaczyć datę i liczbę odsłon. Przy 30 i 90 dniach przesuń wykres w bok.");
            tip.setTextSize(11);tip.setTextColor(0xff697386);tip.setPadding(2,dp(8),2,dp(3));
            chartCard.addView(tip);
            android.widget.HorizontalScrollView scroller=new android.widget.HorizontalScrollView(this);
            scroller.setFillViewport(true);scroller.setHorizontalScrollBarEnabled(true);
            chartCard.addView(scroller,new LinearLayout.LayoutParams(-1,dp(235)));
            View graph=new View(this){
                final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas){
                    super.onDraw(canvas);
                    float width=getWidth(),height=getHeight();
                    float left=dp(40),right=dp(8),top=dp(33),bottom=dp(43);
                    float plotWidth=Math.max(1,width-left-right);
                    float plotHeight=Math.max(1,height-top-bottom);
                    int highest=1;for(int v:values)highest=Math.max(highest,v);
                    int axisMax=(int)Math.ceil(highest/10.0)*10;
                    float slot=plotWidth/Math.max(1,values.size());
                    paint.setColor(0xffe3e8f0);paint.setStrokeWidth(dp(1));
                    for(int line=0;line<=2;line++){
                        float y=top+plotHeight*line/2f;
                        canvas.drawLine(left,y,width-right,y,paint);
                        paint.setColor(0xff697386);paint.setTextSize(dp(10));
                        String text=String.valueOf(axisMax-(int)(axisMax*line/2f));
                        canvas.drawText(text,dp(3),y+dp(4),paint);
                        paint.setColor(0xffe3e8f0);
                    }
                    for(int i=0;i<values.size();i++){
                        float center=left+(i+0.5f)*slot;
                        float barWidth=slot*0.60f;
                        float barHeight=plotHeight*values.get(i)/(float)axisMax;
                        float y=top+plotHeight-barHeight;
                        paint.setColor(i==selected[0]?0xffe4a800:0xfffacc15);
                        canvas.drawRoundRect(center-barWidth/2,y,center+barWidth/2,top+plotHeight,dp(4),dp(4),paint);
                        if(values.size()<=7 || i==selected[0]){
                            paint.setColor(0xff172235);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
                            paint.setTextSize(dp(11));
                            String number=String.valueOf(values.get(i));
                            canvas.drawText(number,center-paint.measureText(number)/2f,Math.max(dp(16),y-dp(5)),paint);
                        }
                        if(values.size()<=7 || i%5==0 || i==values.size()-1){
                            paint.setColor(0xff526174);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(dp(11));
                            String day=labels.get(i);
                            canvas.drawText(day,center-paint.measureText(day)/2f,height-dp(13),paint);
                        }
                    }
                }
                @Override public boolean onTouchEvent(android.view.MotionEvent event){
                    if(event.getAction()==android.view.MotionEvent.ACTION_UP){
                        float left=dp(40),right=dp(8);
                        float width=Math.max(1,getWidth()-left-right);
                        int index=(int)((event.getX()-left)/width*values.size());
                        index=Math.max(0,Math.min(values.size()-1,index));
                        selected[0]=index;
                        detail.setText(labels.get(index)+"  •  "+values.get(index)+" odsłon");
                        setContentDescription(dateKeys.get(index)+", "+values.get(index)+" odsłon");
                        invalidate();performClick();return true;
                    }
                    return true;
                }
                @Override public boolean performClick(){super.performClick();return true;}
            };
            graph.setClickable(true);
            int chartWidth=range[0]<=7?dp(315):Math.max(dp(315),range[0]*dp(35)+dp(40));
            scroller.addView(graph,new android.widget.FrameLayout.LayoutParams(chartWidth,dp(230)));
            if(range[0]>7)scroller.post(()->scroller.scrollTo(chartWidth,0));
        };
        final Runnable[] buildRanges=new Runnable[1];buildRanges[0]=()->{rangeBar.removeAllViews();for(int days:new int[]{7,30,90}){Button b=new Button(this);b.setText(days+" DNI");brandButton(b,range[0]==days);b.setTextSize(12);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(2,2,2,8);rangeBar.addView(b,lp);b.setOnClickListener(v->{range[0]=days;buildRanges[0].run();render.run();});}};
        buildRanges[0].run();render.run();refreshSiteData(render);
        if(siteLiveHandler!=null&&siteLiveRefresh!=null)siteLiveHandler.removeCallbacks(siteLiveRefresh);
        siteLiveHandler=new Handler(getMainLooper());siteLiveRefresh=()->{if(!isFinishing()&&root!=null){refreshSiteData(render);siteLiveHandler.postDelayed(siteLiveRefresh,30000);}};
        siteLiveHandler.postDelayed(siteLiveRefresh,30000);
        addButton("⟳ ODŚWIEŻ TERAZ",()->refreshSiteData(render));addButton("WRÓĆ",()->{if(siteLiveHandler!=null&&siteLiveRefresh!=null)siteLiveHandler.removeCallbacks(siteLiveRefresh);sitePage();});
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private void addMetric(LinearLayout parent,String label,String value){
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(14,11,14,11);card.setBackground(roundBg(Color.WHITE,13));
        TextView a=new TextView(this);a.setText(label);a.setTextSize(12);a.setTextColor(0xff697386);card.addView(a);
        TextView b=new TextView(this);b.setText(value);b.setTextSize(20);b.setTypeface(null,1);b.setTextColor(0xff172235);card.addView(b);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=7;parent.addView(card,lp);
    }
    private void siteProductsPage() {
        root.removeAllViews();TextView h=new TextView(this);h.setText("LISTA SPRZĘTU NA STRONIE");h.setTextSize(22);h.setTypeface(null,1);h.setTextColor(0xff172235);root.addView(h);
        LinearLayout list=new LinearLayout(this);list.setOrientation(1);root.addView(list);
        Runnable render=()->{list.removeAllViews();JSONArray a=siteProducts();for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;
            LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(14,12,14,12);row.setBackground(roundBg(Color.WHITE,14));
            TextView name=new TextView(this);name.setText(p.optString("name"));name.setTextSize(16);name.setTypeface(null,1);name.setTextColor(0xff172235);row.addView(name);
            TextView price=new TextView(this);price.setText(p.optString("price","0")+" zł / doba   •   Kaucja: "+p.optString("deposit","0")+" zł");price.setTextColor(0xff697386);row.addView(price);
            LinearLayout actions=new LinearLayout(this);Button edit=new Button(this);edit.setText("EDYTUJ");brandButton(edit,false);Button del=new Button(this);del.setText("USUŃ");brandButton(del,false);actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));actions.addView(del,new LinearLayout.LayoutParams(0,-2,1));row.addView(actions);
            edit.setOnClickListener(v->productForm(p));del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Usunąć ofertę?").setMessage("Produkt zniknie ze strony RentMor.pl.").setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(d,w)->{try{JSONObject op=new JSONObject();op.put("product_id",p.optString("id"));store.enqueue("product_delete",op);if(isOnline())new Thread(()->{try{sync.sync();runOnUiThread(()->{toast("Oferta usunięta ze strony.");siteProductsPage();});}catch(Exception e){runOnUiThread(()->toast("Błąd usuwania: "+e.getMessage()));}}).start();else toast("Brak internetu — usuń ofertę po połączeniu.");}catch(Exception e){toast("Nie udało się przygotować usunięcia.");}}).show());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=8;list.addView(row,lp);
        }if(a.length()==0){TextView empty=new TextView(this);empty.setText("Nie znaleziono ofert. Odśwież dane strony.");list.addView(empty);}};
        refreshSiteData(render);addButton("＋ DODAJ SPRZĘT",()->productForm(null));addButton("⟳ ODŚWIEŻ",()->refreshSiteData(render));addButton("WRÓĆ",()->sitePage());
    }
    private void productForm(JSONObject existing) {
        productPhotoSession++;
        root.removeAllViews();root.setPadding(16,14,16,18);
        for(Bitmap old:productPhotos) if(old!=null&&!old.isRecycled())old.recycle();
        productPhotos.clear();productSelectedPhotoUris.clear();productPhotosLoading=false;
        productPhotoCount=null;productPhotoPickButton=null;productExistingPhotoContainer=null;productNewPhotoContainer=null;
        existingProductPhotoCount=existing==null||existing.optJSONArray("images")==null?0:existing.optJSONArray("images").length();
        boolean editing=existing!=null;
        TextView h=new TextView(this);h.setText(editing?"EDYTUJ OFERTĘ":"DODAJ SPRZĘT NA STRONĘ");h.setTextSize(23);h.setTypeface(null,1);h.setTextColor(0xff172235);root.addView(h);
        TextView intro=new TextView(this);intro.setText("Uzupełnij dane oferty. Pola są podzielone na osobne sekcje, żeby łatwiej było je edytować.");intro.setTextSize(13);intro.setTextColor(0xff697386);intro.setPadding(0,5,0,14);root.addView(intro);
        LinearLayout basic=productSection("1. PODSTAWOWE INFORMACJE","Nazwa, kategoria i opis sprzętu");
        EditText name=productField(basic,"Nazwa sprzętu");Spinner category=new Spinner(this);String[] cats={"budowlane","ogrodnicze","czyszczace"};String[] labels={"Budowlane","Ogrodnicze","Czyszczące"};category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels));addProductLabel(basic,"Kategoria");basic.addView(category,new LinearLayout.LayoutParams(-1,-2));
        EditText desc=productField(basic,"Opis sprzętu");desc.setMinLines(5);desc.setGravity(Gravity.TOP|Gravity.START);
        LinearLayout rental=productSection("2. WARUNKI WYNAJMU","Cena i zabezpieczenie wynajmu");
        EditText price=productField(rental,"Cena za dobę (zł)");price.setInputType(8194);EditText deposit=productField(rental,"Kaucja (zł)");deposit.setInputType(8194);
        LinearLayout seo=productSection("3. SEO I OGŁOSZENIE","Dane pomagające znaleźć ofertę w Google");
        EditText seoTitle=productField(seo,"Tytuł SEO (opcjonalnie)");EditText seoDesc=productField(seo,"Opis SEO (opcjonalnie)");seoDesc.setMinLines(3);seoDesc.setGravity(Gravity.TOP|Gravity.START);EditText olx=productField(seo,"Link do OLX (opcjonalnie)");
        LinearLayout photos=productSection("4. ZDJĘCIA","Galeria sprzętu na RentMor.pl — do 6 zdjęć łącznie.");
        if(editing&&existingProductPhotoCount>0){
            TextView uploaded=new TextView(this);uploaded.setText("ZDJĘCIA JUŻ NA STRONIE ("+existingProductPhotoCount+")");
            uploaded.setTextSize(12);uploaded.setTypeface(null,1);uploaded.setTextColor(0xff526174);
            uploaded.setPadding(0,dp(5),0,dp(8));photos.addView(uploaded);
            android.widget.HorizontalScrollView existingScroll=new android.widget.HorizontalScrollView(this);
            existingScroll.setHorizontalScrollBarEnabled(false);
            productExistingPhotoContainer=new LinearLayout(this);productExistingPhotoContainer.setOrientation(0);
            existingScroll.addView(productExistingPhotoContainer);
            photos.addView(existingScroll,new LinearLayout.LayoutParams(-1,dp(127)));
            showExistingProductPhotos(existing.optJSONArray("images"),productExistingPhotoContainer);
        }
        productPhotoPickButton=new Button(this);productPhotoPickButton.setText("＋ DODAJ ZDJĘCIA");
        brandButton(productPhotoPickButton,false);
        LinearLayout.LayoutParams chooseParams=new LinearLayout.LayoutParams(-1,-2);
        chooseParams.topMargin=dp(9);photos.addView(productPhotoPickButton,chooseParams);
        productPhotoPickButton.setOnClickListener(v->pickProductPhotos());
        productPhotoCount=new TextView(this);productPhotoCount.setTextSize(12);
        productPhotoCount.setTextColor(0xff697386);productPhotoCount.setPadding(0,dp(8),0,dp(6));
        photos.addView(productPhotoCount);
        android.widget.HorizontalScrollView addedScroll=new android.widget.HorizontalScrollView(this);
        addedScroll.setHorizontalScrollBarEnabled(false);
        productNewPhotoContainer=new LinearLayout(this);productNewPhotoContainer.setOrientation(0);
        addedScroll.addView(productNewPhotoContainer);
        photos.addView(addedScroll,new LinearLayout.LayoutParams(-1,-2));
        updateSelectedProductPhotoPreviews();
        if(editing){name.setText(existing.optString("name"));desc.setText(existing.optString("desc"));price.setText(existing.optString("price"));deposit.setText(existing.optString("deposit"));seoTitle.setText(existing.optString("seo_title"));seoDesc.setText(existing.optString("seo_description"));olx.setText(existing.optString("olx"));for(int i=0;i<cats.length;i++)if(cats[i].equals(existing.optString("category")))category.setSelection(i);}
        TextView note=new TextView(this);note.setText("Po zapisaniu oferta zostanie opublikowana bezpośrednio na RentMor.pl.");note.setTextSize(12);note.setTextColor(0xff526174);note.setPadding(4,12,4,8);root.addView(note);
        addButton(editing?"ZAPISZ ZMIANY I OPUBLIKUJ":"OPUBLIKUJ NA RENTMOR.PL",()->{
            if(productPhotosLoading){toast("Trwa wczytywanie zdjęć. Zapisz po ich dodaniu.");return;}
            if(!isOnline()){toast("Do publikacji produktu potrzebny jest internet.");return;}
            String n=name.getText().toString().trim(),pr=price.getText().toString().trim(),de=deposit.getText().toString().trim();if(n.isEmpty()||pr.isEmpty()){toast("Podaj nazwę i cenę za dobę.");return;}
            try{JSONObject op=new JSONObject();if(editing)op.put("product_id",existing.optString("id"));
                op.put("name",n);op.put("desc",desc.getText().toString());op.put("category",cats[category.getSelectedItemPosition()]);op.put("price",pr);op.put("deposit",de.isEmpty()?"0":de);op.put("seo_title",seoTitle.getText().toString());op.put("seo_description",seoDesc.getText().toString());op.put("olx",olx.getText().toString());
                JSONArray photoData=new JSONArray();for(Bitmap bmp:productPhotos){ByteArrayOutputStream out=new ByteArrayOutputStream();Bitmap scaled=bmp;int max=1280;if(bmp.getWidth()>max||bmp.getHeight()>max){float scale=Math.min((float)max/bmp.getWidth(),(float)max/bmp.getHeight());scaled=Bitmap.createScaledBitmap(bmp,Math.max(1,Math.round(bmp.getWidth()*scale)),Math.max(1,Math.round(bmp.getHeight()*scale)),true);}scaled.compress(Bitmap.CompressFormat.JPEG,78,out);photoData.put(Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));if(scaled!=bmp)scaled.recycle();}op.put("photos_base64",photoData);
                store.enqueue("product_save",op);new Thread(()->{try{sync.sync();runOnUiThread(()->{toast("Oferta opublikowana na RentMor.pl.");siteProductsPage();});}catch(Exception e){runOnUiThread(()->toast("Nie udało się opublikować: "+e.getMessage()));}}).start();
            }catch(Exception e){toast("Nie udało się przygotować oferty: "+e.getMessage());}
        });
        addButton("ANULUJ / WRÓĆ",()->sitePage());
    }
    private LinearLayout productSection(String title,String subtitle){
        LinearLayout section=new LinearLayout(this);section.setOrientation(1);section.setPadding(14,12,14,14);section.setBackground(roundBg(Color.WHITE,15));
        TextView h=new TextView(this);h.setText(title);h.setTextSize(14);h.setTypeface(null,1);h.setTextColor(0xff172235);section.addView(h);
        TextView s=new TextView(this);s.setText(subtitle);s.setTextSize(12);s.setTextColor(0xff697386);s.setPadding(0,3,0,10);section.addView(s);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=12;root.addView(section,lp);return section;
    }
    private void addProductLabel(LinearLayout parent,String label){TextView t=new TextView(this);t.setText(label);t.setTextSize(12);t.setTypeface(null,1);t.setTextColor(0xff526174);t.setPadding(0,7,0,2);parent.addView(t);}
    private EditText productField(LinearLayout parent,String hint){addProductLabel(parent,hint);EditText e=new EditText(this);e.setSingleLine(false);e.setTextSize(15);e.setPadding(10,8,10,8);e.setBackground(roundBg(0xfff5f6fa,9));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=6;parent.addView(e,lp);e.setHint(hint);return e;}
    private void pickProductPhotos(){
        if(productPhotosLoading){toast("Trwa wczytywanie zdjęć.");return;}
        int slots=6-existingProductPhotoCount-productPhotos.size();
        if(slots<=0){toast("Galeria ma już 6 zdjęć. Nie można dodać kolejnych.");return;}
        try{
            // Android 13+ uses the system Photo Picker with explicit multi-selection.
            if(android.os.Build.VERSION.SDK_INT>=33){
                Intent picker=new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
                picker.setType("image/*");
                if(slots>1){
                    picker.putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX,
                        Math.min(slots,android.provider.MediaStore.getPickImagesMaxLimit()));
                }
                startActivityForResult(picker,4102);
            }else{
                Intent picker=new Intent(Intent.ACTION_GET_CONTENT);
                picker.setType("image/*");picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
                startActivityForResult(Intent.createChooser(picker,"Wybierz zdjęcia sprzętu"),4102);
            }
        }catch(Exception e){
            try{
                Intent fallback=new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("image/*");fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
                startActivityForResult(Intent.createChooser(fallback,"Wybierz zdjęcia sprzętu"),4102);
            }catch(Exception error){toast("Nie można otworzyć galerii: "+error.getMessage());}
        }
    }

    private Bitmap readProductPhotoThumbnail(android.net.Uri uri) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            if(stream==null)throw new IOException("Brak dostępu do zdjęcia.");
            BitmapFactory.decodeStream(stream,null,bounds);
        }
        int sample=1;int originalSize=Math.max(bounds.outWidth,bounds.outHeight);
        if(originalSize<=0)throw new IOException("Nie można odczytać wymiarów zdjęcia.");
        while(originalSize/sample>1600)sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.RGB_565;
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            if(stream==null)throw new IOException("Brak dostępu do zdjęcia.");
            Bitmap image=BitmapFactory.decodeStream(stream,null,options);
            if(image==null)throw new IOException("Nie można odczytać obrazu.");
            return image;
        }
    }
    private void updateSelectedProductPhotoPreviews(){
        if(productNewPhotoContainer==null||productPhotoCount==null)return;
        productNewPhotoContainer.removeAllViews();
        int remaining=Math.max(0,6-existingProductPhotoCount-productPhotos.size());
        String label="Na stronie: "+existingProductPhotoCount+"  •  Nowe: "+productPhotos.size()+
            "  •  Wolne miejsca: "+remaining;
        if(productPhotosLoading)label+="\nWczytuję zdjęcia...";
        else if(remaining>0)label+="\nMożesz wybrać kilka naraz lub dodawać po jednym.";
        productPhotoCount.setText(label);
        if(productPhotoPickButton!=null){
            productPhotoPickButton.setEnabled(remaining>0&&!productPhotosLoading);
            productPhotoPickButton.setText(remaining>0?"＋ DODAJ ZDJĘCIA ("+remaining+" MIEJSC)":"LIMIT 6 ZDJĘĆ");
        }
        for(int i=0;i<productPhotos.size();i++){
            final int index=i;
            LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);
            tile.setPadding(dp(4),dp(3),dp(4),dp(3));
            tile.setBackground(roundBg(0xfff1f4f8,10));
            ImageView image=new ImageView(this);image.setImageBitmap(productPhotos.get(i));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(image,new LinearLayout.LayoutParams(dp(94),dp(90)));
            Button remove=new Button(this);remove.setText("Usuń");
            remove.setAllCaps(false);remove.setTextSize(11);remove.setMinHeight(0);
            remove.setPadding(0,dp(2),0,dp(2));brandButton(remove,false);
            tile.addView(remove,new LinearLayout.LayoutParams(-1,dp(35)));
            remove.setOnClickListener(v->{
                if(index<productPhotos.size()){
                    productPhotos.remove(index);
                    productSelectedPhotoUris.remove(index);
                    updateSelectedProductPhotoPreviews();
                }
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(102),-2);
            lp.rightMargin=dp(8);productNewPhotoContainer.addView(tile,lp);
        }
    }
    private void showExistingProductPhotos(JSONArray saved,LinearLayout container){
        if(saved==null)return;
        int visible=Math.min(saved.length(),18);
        for(int i=0;i<visible;i++){
            final int number=i+1;
            String filename=saved.optString(i,"");
            String safeName=filename.substring(filename.lastIndexOf('/')+1);
            LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);
            tile.setPadding(dp(4),dp(4),dp(4),dp(4));tile.setBackground(roundBg(0xfff1f4f8,10));
            ImageView thumbnail=new ImageView(this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            thumbnail.setPadding(dp(6),dp(6),dp(6),dp(6));
            tile.addView(thumbnail,new LinearLayout.LayoutParams(dp(94),dp(88)));
            TextView caption=new TextView(this);caption.setText("Zdjęcie "+number);
            caption.setTextSize(11);caption.setTextColor(0xff526174);
            caption.setGravity(Gravity.CENTER);tile.addView(caption);
            LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(dp(102),dp(123));
            tileParams.rightMargin=dp(8);container.addView(tile,tileParams);
            if(!safeName.matches("[A-Za-z0-9._-]{1,180}")||safeName.equals(".")||safeName.equals("..")){
                caption.setText("Brak podglądu");continue;
            }
            // Photos are already public in /uploads. Load compressed thumbnail off the UI thread.
            new Thread(()->{
                Bitmap bitmap=null;
                java.net.HttpURLConnection connection=null;
                try{
                    String encoded=java.net.URLEncoder.encode(safeName,"UTF-8").replace("+","%20");
                    connection=(java.net.HttpURLConnection)new java.net.URL("https://rentmor.pl/uploads/"+encoded).openConnection();
                    connection.setConnectTimeout(6000);connection.setReadTimeout(9000);
                    if(connection.getResponseCode()==200){
                        BitmapFactory.Options settings=new BitmapFactory.Options();
                        settings.inSampleSize=4;settings.inPreferredConfig=Bitmap.Config.RGB_565;
                        try(InputStream stream=connection.getInputStream()){
                            bitmap=BitmapFactory.decodeStream(stream,null,settings);
                        }
                    }
                }catch(Exception ignored){}
                finally{if(connection!=null)connection.disconnect();}
                final Bitmap loaded=bitmap;
                runOnUiThread(()->{
                    if(container!=productExistingPhotoContainer){
                        if(loaded!=null)loaded.recycle();return;
                    }
                    if(loaded!=null){
                        thumbnail.setPadding(0,0,0,0);thumbnail.setImageBitmap(loaded);
                    }else caption.setText("Niedostępne");
                });
            }).start();
        }
        if(saved.length()>visible){
            TextView more=new TextView(this);more.setText("Pozostałe zdjęcia: "+(saved.length()-visible));
            more.setTextSize(12);more.setTextColor(0xff697386);container.addView(more);
        }
    }

    private void reservationPage() {
        root.removeAllViews(); root.setOrientation(1); root.setPadding(dp(13),dp(15),dp(13),dp(22)); root.setBackgroundColor(0xfff5f6fa);
        TextView heading=new TextView(this); heading.setText("REZERWACJE"); heading.setTextSize(25); heading.setTypeface(null,1); heading.setTextColor(0xff172235); root.addView(heading);
        TextView pageHint=new TextView(this);pageHint.setText("Odbiory, terminy i rozliczenia w jednym miejscu");
        pageHint.setTextSize(12);pageHint.setTextColor(0xff697386);
        pageHint.setPadding(0,dp(2),0,dp(10));root.addView(pageHint);
        Calendar today=Calendar.getInstance();
        final String[] month={String.format(Locale.US,"%04d-%02d",today.get(Calendar.YEAR),today.get(Calendar.MONTH)+1)};
        TextView monthLabel=new TextView(this); monthLabel.setTextSize(15);monthLabel.setTypeface(null,1);monthLabel.setTextColor(0xff172235);monthLabel.setPadding(0,dp(5),0,dp(8)); root.addView(monthLabel);
        LinearLayout monthBar=new LinearLayout(this); monthBar.setGravity(Gravity.CENTER_VERTICAL);
        Button previous=new Button(this); previous.setText("‹"); brandButton(previous,false); Button selectMonth=new Button(this); selectMonth.setText("Wybierz miesiąc"); brandButton(selectMonth,false); Button next=new Button(this); next.setText("›"); brandButton(next,false);
        previous.setTextSize(18);next.setTextSize(18);
        previous.setMinWidth(0);next.setMinWidth(0);
        monthBar.addView(previous,new LinearLayout.LayoutParams(dp(49),dp(48)));
        LinearLayout.LayoutParams monthPickParams=new LinearLayout.LayoutParams(0,dp(48),1);
        monthPickParams.leftMargin=dp(5);monthPickParams.rightMargin=dp(5);
        selectMonth.setTextSize(13);
        monthBar.addView(selectMonth,monthPickParams);
        monthBar.addView(next,new LinearLayout.LayoutParams(dp(49),dp(48)));
        root.addView(monthBar);

        Button toggleForm=new Button(this);toggleForm.setText("＋ NOWA REZERWACJA");
        brandButton(toggleForm,true);
        LinearLayout.LayoutParams toggleParams=new LinearLayout.LayoutParams(-1,dp(53));
        toggleParams.topMargin=dp(12);toggleParams.bottomMargin=dp(7);root.addView(toggleForm,toggleParams);
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);
        form.setPadding(dp(14),dp(14),dp(14),dp(14));form.setBackground(roundBg(Color.WHITE,15));
        LinearLayout.LayoutParams formParams=new LinearLayout.LayoutParams(-1,-2);formParams.bottomMargin=dp(10);
        root.addView(form,formParams);form.setVisibility(View.GONE);
        toggleForm.setOnClickListener(v->{
            boolean open=form.getVisibility()!=View.VISIBLE;
            form.setVisibility(open?View.VISIBLE:View.GONE);
            toggleForm.setText(open?"− ZAMKNIJ FORMULARZ":"＋ NOWA REZERWACJA");
            if(open)form.post(()->{form.requestFocus();});
        });
        TextView formHeading=new TextView(this);formHeading.setText("NOWA REZERWACJA");
        formHeading.setTextSize(16);formHeading.setTypeface(null,1);
        formHeading.setTextColor(0xff172235);form.addView(formHeading);
        TextView formHint=new TextView(this);formHint.setText("Wybierz termin, sprzęt i dane kontaktowe klienta.");
        formHint.setTextSize(12);formHint.setTextColor(0xff697386);
        formHint.setPadding(0,dp(3),0,dp(10));form.addView(formHint);
        reservationFormLabel(form,"TERMIN WYNAJMU");

        final Calendar[] reservationStart={ (Calendar)today.clone() },reservationEnd={ (Calendar)today.clone() };
        LinearLayout dateRange=new LinearLayout(this);dateRange.setGravity(Gravity.CENTER_VERTICAL);
        Button startDateButton=new Button(this);Button endDateButton=new Button(this);brandButton(startDateButton,false);brandButton(endDateButton,false);
        Runnable updateDateButtons=()->{SimpleDateFormat df=new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault());startDateButton.setText("OD  "+df.format(reservationStart[0].getTime()));endDateButton.setText("DO  "+df.format(reservationEnd[0].getTime()));};
        startDateButton.setTextSize(12);endDateButton.setTextSize(12);
        LinearLayout.LayoutParams startParams=new LinearLayout.LayoutParams(0,dp(50),1);
        startParams.rightMargin=dp(5);
        dateRange.addView(startDateButton,startParams);
        dateRange.addView(endDateButton,new LinearLayout.LayoutParams(0,dp(50),1));
        form.addView(dateRange);
        startDateButton.setOnClickListener(v->{Calendar c=(Calendar)reservationStart[0].clone();new android.app.DatePickerDialog(this,(view,y,m,d)->{reservationStart[0].set(y,m,d);if(reservationEnd[0].before(reservationStart[0]))reservationEnd[0]=(Calendar)reservationStart[0].clone();updateDateButtons.run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});
        endDateButton.setOnClickListener(v->{Calendar c=(Calendar)reservationEnd[0].clone();new android.app.DatePickerDialog(this,(view,y,m,d)->{Calendar chosen=(Calendar)reservationEnd[0].clone();chosen.set(y,m,d);if(chosen.before(reservationStart[0])){toast("Data zakończenia nie może być wcześniejsza niż początek.");return;}reservationEnd[0]=chosen;updateDateButtons.run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});
        updateDateButtons.run();
        reservationFormLabel(form,"SPRZĘT");
        Spinner equipment=new Spinner(this);
        equipment.setPadding(dp(4),dp(7),dp(4),dp(7));
        equipment.setBackground(roundBg(0xfff3f5f9,9));
        form.addView(equipment,new LinearLayout.LayoutParams(-1,dp(52)));
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
        View addEquipmentAction=root.getChildAt(root.getChildCount()-1);
        root.removeView(addEquipmentAction);
        form.addView(addEquipmentAction,new LinearLayout.LayoutParams(-1,dp(46)));
        reservationFormLabel(form,"GODZINA ODBIORU");
        EditText time=new EditText(this);time.setSingleLine(true);time.setHint("Wybierz godzinę odbioru");
        time.setTextSize(15);time.setPadding(dp(12),dp(10),dp(12),dp(10));
        time.setBackground(roundBg(0xfff3f5f9,9));
        time.setFocusable(false);time.setClickable(true);time.setCursorVisible(false);
        form.addView(time,new LinearLayout.LayoutParams(-1,dp(49)));
        time.setOnClickListener(v->{int hour=18,minute=0;String current=time.getText().toString().trim();if(current.matches("\\d{1,2}:[0-5]\\d")){String[] parts=current.split(":");hour=Integer.parseInt(parts[0]);minute=Integer.parseInt(parts[1]);}new android.app.TimePickerDialog(this,(picker,h,m)->time.setText(String.format(Locale.US,"%02d:%02d",h,m)),hour,minute,true).show();});
        reservationFormLabel(form,"KONTAKT DO KLIENTA");
        AutoCompleteTextView phone=new AutoCompleteTextView(this);
        phone.setSingleLine(true);phone.setHint("Telefon lub nick z OLX");
        phone.setTextSize(15);phone.setPadding(dp(12),dp(10),dp(12),dp(10));
        phone.setBackground(roundBg(0xfff3f5f9,9));
        phone.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ArrayList<String> knownPhones=new ArrayList<>();JSONArray knownCustomers=store.customers();for(int ci=0;ci<knownCustomers.length();ci++){JSONObject cc=knownCustomers.optJSONObject(ci);if(cc!=null&&!cc.optString("phone").isEmpty())knownPhones.add(cc.optString("phone"));}
        ArrayAdapter<String> phoneAdapter=new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,knownPhones);phone.setAdapter(phoneAdapter);phone.setThreshold(1);
        form.addView(phone,new LinearLayout.LayoutParams(-1,dp(49)));
        reservationFormLabel(form,"NAZWA KLIENTA (OPCJONALNIE)");
        EditText customerName=new EditText(this);customerName.setSingleLine(true);
        customerName.setTextSize(15);customerName.setHint("Imię, firma lub nick");
        customerName.setPadding(dp(12),dp(10),dp(12),dp(10));
        customerName.setBackground(roundBg(0xfff3f5f9,9));
        form.addView(customerName,new LinearLayout.LayoutParams(-1,dp(49)));
        phone.setOnItemClickListener((parent,view,position,id)->{String selected=String.valueOf(parent.getItemAtPosition(position));String key=customerPhoneKey(selected);for(int ci=0;ci<knownCustomers.length();ci++){JSONObject cc=knownCustomers.optJSONObject(ci);if(cc!=null&&customerPhoneKey(cc.optString("phone")).equals(key)){customerName.setText(cc.optString("name",""));break;}}});
        TextView hint=new TextView(this);hint.setText("Ustaw datę OD–DO. Rezerwacja może obejmować kilka dni.");hint.setTextSize(11);hint.setTextColor(0xff718096);hint.setPadding(0,dp(10),0,dp(4));form.addView(hint);

        LinearLayout summaryRow=new LinearLayout(this);summaryRow.setOrientation(1);
        LinearLayout.LayoutParams summaryParams=new LinearLayout.LayoutParams(-1,-2);
        summaryParams.topMargin=dp(13);summaryParams.bottomMargin=dp(8);root.addView(summaryRow,summaryParams);
        TextView listHeading=new TextView(this);listHeading.setText("REZERWACJE W MIESIĄCU");
        listHeading.setTextSize(13);listHeading.setTypeface(null,1);listHeading.setTextColor(0xff172235);
        summaryRow.addView(listHeading);
        TextView reservationSummary=new TextView(this);
        reservationSummary.setTextSize(12);reservationSummary.setTextColor(0xff697386);
        reservationSummary.setPadding(0,dp(3),0,0);summaryRow.addView(reservationSummary);
        LinearLayout list=new LinearLayout(this);list.setOrientation(1);
        list.setPadding(dp(2),dp(2),dp(2),dp(10));root.addView(list,new LinearLayout.LayoutParams(-1,-2));
        final Runnable[] render=new Runnable[1];
        render[0]=()->{
            Calendar selected=parseReservationMonth(month[0]);monthLabel.setText(new SimpleDateFormat("LLLL yyyy",new Locale("pl","PL")).format(selected.getTime()).toUpperCase(new Locale("pl","PL")));list.removeAllViews();
            JSONArray data=store.reservations(month[0]);ArrayList<JSONObject> items=new ArrayList<>();for(int i=0;i<data.length();i++){JSONObject item=data.optJSONObject(i);if(item!=null)items.add(item);}
            Collections.sort(items,(a,b)->{int issued=Boolean.compare(b.optBoolean("done"),a.optBoolean("done"));if(issued!=0)return issued;String da=a.optString("start_date","").trim(),db=b.optString("start_date","").trim();if(!da.matches("\\d{4}-\\d{2}-\\d{2}")){java.util.regex.Matcher ma=java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(a.optString("text",""));da=ma.find()?ma.group(1):"9999-99-99";}if(!db.matches("\\d{4}-\\d{2}-\\d{2}")){java.util.regex.Matcher mb=java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(b.optString("text",""));db=mb.find()?mb.group(1):"9999-99-99";}int dateOrder=da.compareTo(db);if(dateOrder!=0)return dateOrder;return reservationTimeForItem(a).compareTo(reservationTimeForItem(b));});
            int active=0,issuedCount=0;
            for(JSONObject reservation:items){
                if(reservation.optBoolean("settled"))continue;
                active++;
                if(reservation.optBoolean("done"))issuedCount++;
            }
            reservationSummary.setText("Aktywne: "+active+"   •   Wydane: "+issuedCount+"   •   Do wydania: "+(active-issuedCount));
            int shown=0;
            for(JSONObject item:items){if(item.optBoolean("settled"))continue;shown++;
                boolean done=item.optBoolean("done");
                LinearLayout row=new LinearLayout(this);row.setOrientation(1);
                row.setPadding(dp(14),dp(13),dp(14),dp(14));
                row.setBackground(roundBg(Color.WHITE,15));row.setElevation(dp(2));
                LinearLayout head=new LinearLayout(this);head.setOrientation(0);
                head.setGravity(Gravity.CENTER_VERTICAL);
                TextView cardLabel=new TextView(this);cardLabel.setText("REZERWACJA  •  "+
                    reservationMonthRangeLabel(item).toUpperCase(new Locale("pl","PL")));
                cardLabel.setTextSize(11);cardLabel.setTypeface(null,1);cardLabel.setTextColor(0xff697386);
                head.addView(cardLabel,new LinearLayout.LayoutParams(0,-2,1));
                TextView state=new TextView(this);state.setText(done?"✓ WYDANE":"DO WYDANIA");
                state.setTextSize(11);state.setTypeface(null,1);
                state.setTextColor(done?0xff166534:0xff925400);
                state.setPadding(dp(9),dp(7),dp(9),dp(7));
                state.setBackground(roundBg(done?0xffe7f5e9:0xfffff1d6,9));
                head.addView(state);
                row.addView(head);
                Button statusAction=new Button(this);
                statusAction.setText(done?"COFNIJ WYDANIE":"OZNACZ WYDANIE");
                compactReservationButton(statusAction,false);
                statusAction.setOnClickListener(v->{
                    try{
                        boolean nextDone=!item.optBoolean("done");item.put("done",nextDone);
                        store.saveReservation(item);store.enqueue("reservation_save",item);
                        ReservationsWidgetProvider.refresh(this);
                        toast(nextDone?"Oznaczono jako wydane.":"Przywrócono status DO WYDANIA.");
                        if(isOnline())syncNow();render[0].run();
                    }catch(Exception ex){toast("Nie udało się zmienić statusu.");}
                });
                String dateLabel=reservationDateRangeLabel(item);
                TextView dateRangeLabel=new TextView(this);
                dateRangeLabel.setText(dateLabel.replace(" • ","\n"));
                dateRangeLabel.setTextSize(13);dateRangeLabel.setTypeface(null,1);
                dateRangeLabel.setTextColor(0xff245b91);dateRangeLabel.setLineSpacing(dp(2),1f);
                dateRangeLabel.setPadding(dp(11),dp(9),dp(11),dp(9));
                dateRangeLabel.setBackground(roundBg(0xffedf4fd,10));
                LinearLayout.LayoutParams dateLp=new LinearLayout.LayoutParams(-1,-2);
                dateLp.topMargin=dp(11);row.addView(dateRangeLabel,dateLp);
                TextView equipmentTitle=new TextView(this);equipmentTitle.setText(reservationEquipment(item));equipmentTitle.setTextSize(17);equipmentTitle.setTypeface(null,1);equipmentTitle.setTextColor(0xff172235);equipmentTitle.setPadding(0,dp(12),0,dp(8));row.addView(equipmentTitle);
                String cardCustomerName=item.optString("customer_name",item.optString("client","")).trim();
                if(!cardCustomerName.isEmpty()){TextView customerText=new TextView(this);customerText.setText("KLIENT:  "+cardCustomerName);customerText.setTextSize(13);customerText.setTextColor(0xff526174);customerText.setPadding(0,0,0,4);row.addView(customerText);}
                LinearLayout details=new LinearLayout(this);details.setGravity(Gravity.CENTER_VERTICAL);details.setPadding(0,0,0,5);
                TextView timeBadge=new TextView(this);String timeText=reservationTimeForItem(item);timeBadge.setText("◷  "+(timeText.isEmpty()?"GODZINA NIEUSTALONA":timeText));timeBadge.setTextSize(12);timeBadge.setTypeface(null,1);timeBadge.setTextColor(0xff7c4a03);timeBadge.setPadding(8,5,8,5);timeBadge.setBackground(roundBg(0xfffff3df,9));details.addView(timeBadge);row.addView(details);
                String ph=reservationPhoneForItem(item);if(!ph.isEmpty()){
                    TextView phoneText=new TextView(this);phoneText.setText((isPhoneNumber(ph)?"TELEFON:  ":"KONTAKT OLX:  ")+ph);phoneText.setTextSize(13);phoneText.setTypeface(null,1);phoneText.setTextColor(0xff172235);phoneText.setPadding(0,0,0,4);row.addView(phoneText);
                }
                LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
                Button edit=new Button(this);edit.setText("EDYTUJ");compactReservationButton(edit,false);
                LinearLayout.LayoutParams editLp=new LinearLayout.LayoutParams(0,dp(48),1);
                editLp.rightMargin=dp(5);actions.addView(edit,editLp);
                Button settle=new Button(this);settle.setText("ROZLICZ");compactReservationButton(settle,true);
                actions.addView(settle,new LinearLayout.LayoutParams(0,dp(48),1));
                settle.setEnabled("admin".equals(getSharedPreferences("rentmor",0).getString("role","")));
                LinearLayout.LayoutParams actionsLp=new LinearLayout.LayoutParams(-1,-2);
                actionsLp.topMargin=dp(10);row.addView(actions,actionsLp);
                if(isPhoneNumber(ph)){
                    LinearLayout contactActions=new LinearLayout(this);contactActions.setGravity(Gravity.CENTER_VERTICAL);
                    Button sms=new Button(this);sms.setText("SMS");compactReservationButton(sms,false);
                    LinearLayout.LayoutParams smsLp=new LinearLayout.LayoutParams(0,dp(48),1);
                    smsLp.rightMargin=dp(5);contactActions.addView(sms,smsLp);
                    Button call=new Button(this);call.setText("ZADZWOŃ");compactReservationButton(call,false);
                    contactActions.addView(call,new LinearLayout.LayoutParams(0,dp(48),1));
                    LinearLayout.LayoutParams contactLp=new LinearLayout.LayoutParams(-1,-2);
                    contactLp.topMargin=dp(6);row.addView(contactActions,contactLp);
                    sms.setOnClickListener(v->{try{android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_SENDTO,android.net.Uri.fromParts("smsto",ph.replaceAll("[^+0-9]",""),null));intent.putExtra("sms_body","Dzień dobry, kontaktuję się w sprawie rezerwacji sprzętu w RentMor.");startActivity(intent);}catch(Exception ex){toast("Nie można otworzyć wiadomości.");}});
                    call.setOnClickListener(v->{try{startActivity(new android.content.Intent(android.content.Intent.ACTION_DIAL,android.net.Uri.fromParts("tel",ph.replaceAll("[^+0-9]",""),null)));}catch(Exception ex){toast("Nie można otworzyć telefonu.");}});
                }
                LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams statusLp=new LinearLayout.LayoutParams(0,dp(45),1);
                statusLp.rightMargin=dp(5);statusRow.addView(statusAction,statusLp);
                Button delete=new Button(this);delete.setText("USUŃ");compactReservationButton(delete,false);
                delete.setTextColor(0xff9d3542);
                android.graphics.drawable.GradientDrawable removeBg=roundBg(0xfffff1f2,10);
                removeBg.setStroke(dp(1),0xfff4c8cb);delete.setBackground(removeBg);
                statusRow.addView(delete,new LinearLayout.LayoutParams(0,dp(45),1));
                LinearLayout.LayoutParams statusRowLp=new LinearLayout.LayoutParams(-1,-2);
                statusRowLp.topMargin=dp(7);row.addView(statusRow,statusRowLp);
                delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Usunąć rezerwację?").setMessage("Rezerwacja zostanie usunięta z aplikacji i po synchronizacji z serwera. Tej operacji nie można cofnąć.").setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(dialog,which)->{try{JSONObject op=new JSONObject();op.put("id",item.optString("id"));store.enqueue("reservation_delete",op);store.deleteReservation(item.optString("id"));ReservationsWidgetProvider.refresh(this);render[0].run();toast("Usunięto rezerwację.");if(isOnline())syncNow();}catch(Exception ex){toast("Nie udało się usunąć rezerwacji.");}}).show());
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(12);list.addView(row,rp);
                edit.setOnClickListener(v->{
                    String raw=item.optString("text","");
                    String fromValue=item.optString("start_date","").trim(),toValue=item.optString("end_date","").trim();
                    java.util.regex.Matcher dateMatch=java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*[–-]\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(raw);
                    if(dateMatch.find()){fromValue=dateMatch.group(1);toValue=dateMatch.group(2);}
                    Calendar fallback=Calendar.getInstance();
                    final Calendar[] editStart={parseReservationDate(fromValue,fallback)};
                    final Calendar[] editEnd={parseReservationDate(toValue,editStart[0])};
                    if(editEnd[0].before(editStart[0]))editEnd[0]=(Calendar)editStart[0].clone();
                    LinearLayout f=new LinearLayout(this);f.setOrientation(1);f.setPadding(8,4,8,4);
                    TextView dateHint=new TextView(this);dateHint.setText("Termin rezerwacji");dateHint.setTextSize(13);dateHint.setTypeface(null,1);dateHint.setTextColor(0xff526174);f.addView(dateHint);
                    LinearLayout dateButtons=new LinearLayout(this);dateButtons.setGravity(Gravity.CENTER_VERTICAL);
                    Button fromButton=new Button(this),toButton=new Button(this);brandButton(fromButton,false);brandButton(toButton,false);
                    Runnable refreshDates=()->{SimpleDateFormat df=new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault());fromButton.setText("OD  "+df.format(editStart[0].getTime()));toButton.setText("DO  "+df.format(editEnd[0].getTime()));};
                    dateButtons.addView(fromButton,new LinearLayout.LayoutParams(0,-2,1));dateButtons.addView(toButton,new LinearLayout.LayoutParams(0,-2,1));f.addView(dateButtons);
                    refreshDates.run();
                    fromButton.setOnClickListener(view->{Calendar c=(Calendar)editStart[0].clone();new DatePickerDialog(this,(picker,y,m,day)->{editStart[0].set(y,m,day);if(editEnd[0].before(editStart[0]))editEnd[0]=(Calendar)editStart[0].clone();refreshDates.run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});
                    toButton.setOnClickListener(view->{Calendar c=(Calendar)editEnd[0].clone();new DatePickerDialog(this,(picker,y,m,day)->{Calendar chosen=(Calendar)editEnd[0].clone();chosen.set(y,m,day);if(chosen.before(editStart[0])){toast("Data zakończenia nie może być wcześniejsza niż początek.");return;}editEnd[0]=chosen;refreshDates.run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});
                    String initialTime=reservationTimeForItem(item);
                    java.util.regex.Matcher textTime=java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s*[–-]\\s*\\d{4}-\\d{2}-\\d{2}\\s+([01]?\\d|2[0-3]):([0-5]\\d)(?:$|\\s|•)").matcher(raw);
                    if(textTime.find())initialTime=String.format(Locale.US,"%02d:%s",Integer.parseInt(textTime.group(1)),textTime.group(2));
                    EditText timeEdit=new EditText(this);timeEdit.setSingleLine(true);timeEdit.setHint("Godzina, np. 08:00");timeEdit.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_TIME);timeEdit.setText(initialTime);f.addView(timeEdit);
                    EditText equipmentEdit=new EditText(this);equipmentEdit.setSingleLine(false);equipmentEdit.setHint("Nazwa sprzętu");equipmentEdit.setText(reservationEquipment(item));f.addView(equipmentEdit);
                    EditText customerNameEdit=new EditText(this);customerNameEdit.setSingleLine(true);customerNameEdit.setHint("Imię i nazwisko / firma klienta");customerNameEdit.setText(item.optString("customer_name",item.optString("client","")));f.addView(customerNameEdit);
                    EditText phoneEdit=new EditText(this);phoneEdit.setSingleLine(true);phoneEdit.setHint("Telefon lub nick z OLX");phoneEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);phoneEdit.setText(reservationPhoneForItem(item));f.addView(phoneEdit);
                    TextView info=new TextView(this);info.setText("Możesz zmienić termin, sprzęt i dane kontaktowe klienta.");info.setTextSize(12);info.setTextColor(0xff718096);info.setPadding(0,6,0,0);f.addView(info);
                    new AlertDialog.Builder(this).setTitle("Edytuj rezerwację").setView(new ScrollView(this){{addView(f);}}).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(d,w)->{
                        try{
                            String entered=timeEdit.getText().toString().trim(),normalizedTime="";
                            if(!entered.isEmpty()){
                                if(!entered.matches("\\d{1,2}(:[0-5]\\d)?")){toast("Podaj godzinę w formacie 08:00.");return;}
                                String[] parts=entered.split(":");int hour=Integer.parseInt(parts[0]),minute=parts.length>1?Integer.parseInt(parts[1]):0;
                                if(hour>23){toast("Godzina musi być w zakresie 00:00–23:59.");return;}
                                normalizedTime=String.format(Locale.US,"%02d:%02d",hour,minute);
                            }
                            SimpleDateFormat iso=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
                            String from=iso.format(editStart[0].getTime()),to=iso.format(editEnd[0].getTime());
                            String equipmentName=equipmentEdit.getText().toString().trim().replaceAll("\\s+"," ");
                            String customerNameValue=customerNameEdit.getText().toString().trim().replaceAll("\\s+"," ");
                            String phoneValue=phoneEdit.getText().toString().trim();
                            if(equipmentName.isEmpty()){toast("Wpisz nazwę sprzętu.");return;}
                            String monthValue=new SimpleDateFormat("yyyy-MM",Locale.US).format(editStart[0].getTime());
                            String value=from+" – "+to+(normalizedTime.isEmpty()?"":" "+normalizedTime)+" • "+equipmentName+(phoneValue.isEmpty()?"":" • "+phoneValue);
                            item.put("month",monthValue);item.put("text",value);item.put("equipment",equipmentName);item.put("day",editStart[0].get(Calendar.DAY_OF_MONTH));
                            item.put("start_date",from);item.put("end_date",to);item.put("end_day",editEnd[0].get(Calendar.DAY_OF_MONTH));
                            item.put("end_month",new SimpleDateFormat("yyyy-MM",Locale.US).format(editEnd[0].getTime()));item.put("time",normalizedTime);item.put("phone",phoneValue);
                            item.put("customer_name",customerNameValue);item.put("client",customerNameValue);
                            store.saveReservation(item);store.enqueue("reservation_save",item);month[0]=monthValue;ReservationsWidgetProvider.refresh(this);render[0].run();if(isOnline())syncNow();
                            toast("Zaktualizowano rezerwację i dane klienta.");
                        }catch(Exception ex){toast("Nie udało się zapisać rezerwacji: "+ex.getMessage());}
                    }).show();
                });
                settle.setOnClickListener(v->{
                    EditText income=new EditText(this);income.setHint("Zarobek z najmu (zł)");income.setInputType(8194);EditText loss=new EditText(this);loss.setHint("Strata / koszt (zł, np. 0)");loss.setInputType(8194);EditText note=new EditText(this);note.setHint("Za co strata? (opcjonalnie)");
                    LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(8,4,8,4);form.addView(income);form.addView(loss);form.addView(note);
                    new AlertDialog.Builder(this).setTitle("Rozlicz najem").setMessage(item.optString("text")).setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz rozliczenie",(d,w)->{try{String iv=income.getText().toString().trim().replace(',','.');String lv=loss.getText().toString().trim().replace(',','.');double earned=iv.isEmpty()?0:Double.parseDouble(iv),cost=lv.isEmpty()?0:Double.parseDouble(lv);if(earned<0||cost<0){toast("Kwoty nie mogą być ujemne.");return;}item.put("settled",true);item.put("income",earned);item.put("loss",cost);item.put("loss_note",note.getText().toString().trim());item.put("settled_at",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()));store.saveReservation(item);store.enqueue("reservation_settle",item);ReservationsWidgetProvider.refresh(this);render[0].run();if(isOnline())syncNow();}catch(Exception ex){toast("Wpisz prawidłowe kwoty.");}}).show();
                });
            }
            if(shown==0){TextView empty=new TextView(this);empty.setText("Brak aktywnych rezerwacji w wybranym miesiącu.");empty.setTextSize(14);empty.setTextColor(0xff718096);empty.setPadding(dp(14),dp(20),dp(14),dp(20));empty.setBackground(roundBg(Color.WHITE,12));list.addView(empty);}
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
            try{JSONObject item=new JSONObject();item.put("id",UUID.randomUUID().toString());item.put("month",new SimpleDateFormat("yyyy-MM",Locale.US).format(reservationStart[0].getTime()));item.put("text",value);item.put("equipment",eq);item.put("day",reservationStart[0].get(Calendar.DAY_OF_MONTH));item.put("start_date",from);item.put("end_date",to);item.put("end_day",reservationEnd[0].get(Calendar.DAY_OF_MONTH));item.put("end_month",new SimpleDateFormat("yyyy-MM",Locale.US).format(reservationEnd[0].getTime()));item.put("time",shortTime);item.put("phone",ph);item.put("customer_name",customerName.getText().toString().trim());item.put("done",false);store.saveReservation(item);store.enqueue("reservation_save",item);ReservationsWidgetProvider.refresh(this);
                phone.setText("");time.setText("");customerName.setText("");
                month[0]=item.optString("month",month[0]);form.setVisibility(View.GONE);
                toggleForm.setText("＋ NOWA REZERWACJA");
                render[0].run();toast("Rezerwacja została dodana.");if(isOnline())syncNow();}
            catch(Exception ex){toast("Nie udało się dodać rezerwacji.");}
        });
        View submitReservation=root.getChildAt(root.getChildCount()-1);
        root.removeView(submitReservation);
        LinearLayout.LayoutParams submitParams=new LinearLayout.LayoutParams(-1,dp(53));
        submitParams.topMargin=dp(12);form.addView(submitReservation,submitParams);
        addButton("WRÓĆ",()->showHome());
        render[0].run();
    }

    private void reservationFormLabel(LinearLayout form,String value){
        TextView label=new TextView(this);label.setText(value);label.setTextSize(11);label.setTypeface(null,1);
        label.setTextColor(0xff526174);label.setPadding(0,dp(11),0,dp(6));form.addView(label);
    }

    private void expensePage() {
        if(!"admin".equals(getSharedPreferences("rentmor",0).getString("role",""))){toast("Ta funkcja jest dostępna tylko dla administratora.");showHome();return;}
        root.removeAllViews();root.setOrientation(1);root.setPadding(18,16,18,18);root.setBackgroundColor(0xfff5f6fa);
        TextView heading=new TextView(this);heading.setText("POZOSTAŁE KOSZTY");heading.setTextSize(24);heading.setTypeface(null,1);heading.setTextColor(0xff172235);root.addView(heading);
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
        root.removeAllViews(); root.setOrientation(1); root.setPadding(18,16,18,18); root.setBackgroundColor(0xfff5f6fa);
        TextView heading=new TextView(this); heading.setText("STATYSTYKI NAJMU"); heading.setTextSize(24); heading.setTypeface(null,1); heading.setTextColor(0xff172235); root.addView(heading);
        Button profitability=new Button(this); profitability.setText("RENTOWNOŚĆ SPRZĘTU"); brandButton(profitability,true); profitability.setOnClickListener(v->equipmentProfitabilityPage()); root.addView(profitability);
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
            TextView sub=new TextView(this); sub.setText("WYNIK WEDŁUG SPRZĘTU"); sub.setTextSize(16); sub.setTypeface(null,1); sub.setTextColor(0xff172235); sub.setPadding(2,12,2,8); content.addView(sub);
            if(byEquipment.isEmpty()){TextView empty=new TextView(this);empty.setText("Brak rozliczeń w tym miesiącu.");empty.setTextColor(0xff718096);content.addView(empty);}
            ArrayList<String> names=new ArrayList<>(byEquipment.keySet()); Collections.sort(names,(x,y)->Double.compare(byEquipment.get(y)[3],byEquipment.get(x)[3]));
            for(String name:names){double[] v=byEquipment.get(name);TextView line=new TextView(this);line.setText(name+"\nNajmy: "+(int)v[0]+"  •  Przychód: "+String.format(Locale.US,"%.2f",v[1])+" zł\nStraty: "+String.format(Locale.US,"%.2f",v[2])+" zł  •  Wynik: "+String.format(Locale.US,"%.2f",v[3])+" zł");line.setTextSize(14);line.setTextColor(0xff26384d);line.setPadding(14,12,14,12);line.setBackground(roundBg(Color.WHITE,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=7;content.addView(line,lp);}
            TextView hist=new TextView(this);hist.setText("HISTORIA ROZLICZEŃ ("+history.size()+")");hist.setTextSize(16);hist.setTypeface(null,1);hist.setTextColor(0xff172235);hist.setPadding(2,18,2,8);content.addView(hist);
            Collections.reverse(history);
            for(JSONObject item:history){double inc=item.optDouble("income"),los=item.optDouble("loss");TextView line=new TextView(this);line.setText(item.optString("text")+"\nPrzychód: "+String.format(Locale.US,"%.2f",inc)+" zł  •  Strata: "+String.format(Locale.US,"%.2f",los)+" zł\nWynik: "+String.format(Locale.US,"%.2f",inc-los)+" zł"+(item.optString("loss_note").isEmpty()?"":"  •  "+item.optString("loss_note")));line.setTextSize(13);line.setTextColor(0xff26384d);line.setPadding(14,12,14,12);line.setBackground(roundBg(Color.WHITE,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=7;content.addView(line,lp);}
        };
        prev.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);c.add(Calendar.MONTH,-1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(c.getTime());render.run();});
        next.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);c.add(Calendar.MONTH,1);month[0]=new SimpleDateFormat("yyyy-MM",Locale.US).format(c.getTime());render.run();});
        pick.setOnClickListener(v->{Calendar c=parseReservationMonth(month[0]);new DatePickerDialog(this,(d,y,m,day)->{month[0]=String.format(Locale.US,"%04d-%02d",y,m+1);render.run();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),1).show();});
        addButton("WRÓĆ",()->showHome()); render.run();
    }
    private void equipmentProfitabilityPage() {
        root.removeAllViews(); root.setOrientation(1); root.setPadding(18,38,18,42); root.setBackgroundColor(0xfff5f6fa);
        TextView h=new TextView(this); h.setText("RENTOWNOŚĆ SPRZĘTU"); h.setTextSize(19); h.setTypeface(null,1); h.setTextColor(0xff172235); root.addView(h);
        TextView hint=new TextView(this); hint.setText("Zestawienie łączy wszystkie sztuki tego samego modelu. Uwzględnia rozliczone rezerwacje."); hint.setTextColor(0xff718096); hint.setTextSize(12); hint.setPadding(0,3,0,8); root.addView(hint);
        Spinner years=new Spinner(this); ArrayList<String> opts=new ArrayList<>(); opts.add("Od początku"); int y=Calendar.getInstance().get(Calendar.YEAR); for(int n=y;n>=2020;n--)opts.add(String.valueOf(n));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,opts); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); years.setAdapter(adapter); years.setBackground(roundBg(Color.WHITE,10)); root.addView(years);
        ScrollView sv=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(1); list.setPadding(0,8,0,16); sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        final Runnable[] draw={null}; draw[0]=()->{
            list.removeAllViews(); int yr=years.getSelectedItemPosition()==0?0:Integer.parseInt(String.valueOf(years.getSelectedItem()));
            LinkedHashMap<String,String> names=new LinkedHashMap<>(); LinkedHashMap<String,double[]> totals=new LinkedHashMap<>();
            Calendar cur=Calendar.getInstance(); cur.set(2015,0,1); Calendar today=Calendar.getInstance(); double all=0;
            while(!cur.after(today)){
                if(yr==0||cur.get(Calendar.YEAR)==yr){
                    String month=new SimpleDateFormat("yyyy-MM",Locale.US).format(cur.getTime()); JSONArray rows=store.reservations(month);
                    for(int k=0;k<rows.length();k++){JSONObject r=rows.optJSONObject(k);if(r==null||!r.optBoolean("settled"))continue;
                        String name=reservationEquipment(r);if(name.isEmpty())name="Pozostałe";String key=name.toLowerCase(new Locale("pl","PL")).replaceAll("\\s+"," ").trim();
                        names.putIfAbsent(key,name);double[] v=totals.get(key);if(v==null){v=new double[4];totals.put(key,v);}
                        v[0]++;v[2]+=r.optDouble("income");v[3]+=r.optDouble("loss");
                        try{SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd",Locale.US);Date a=df.parse(r.optString("start_date")),b=df.parse(r.optString("end_date"));v[1]+=a!=null&&b!=null?Math.max(1,(b.getTime()-a.getTime())/86400000L+1):1;}catch(Exception ex){v[1]++;}
                        all+=r.optDouble("income");
                    }
                } cur.add(Calendar.MONTH,1);
            }
            HashMap<String,double[]> costs=new HashMap<>();
            cur=Calendar.getInstance();cur.set(2015,0,1);
            while(!cur.after(today)){
                if(yr==0||cur.get(Calendar.YEAR)==yr){
                    JSONArray expenses=store.expenses(new SimpleDateFormat("yyyy-MM",Locale.US).format(cur.getTime()));
                    for(int j=0;j<expenses.length();j++){JSONObject e=expenses.optJSONObject(j);if(e==null)continue;String equipment=e.optString("equipment_name","").trim();if(equipment.isEmpty())continue;
                        String key=equipment.toLowerCase(new Locale("pl","PL")).replaceAll("\\s+"," ").trim();double[] v=costs.get(key);if(v==null){v=new double[2];costs.put(key,v);}String note=e.optString("note","").toLowerCase(new Locale("pl","PL"));
                        if(note.contains("zakup"))v[0]+=e.optDouble("amount");else v[1]+=e.optDouble("amount");names.putIfAbsent(key,equipment);totals.putIfAbsent(key,new double[4]);
                    }
                }cur.add(Calendar.MONTH,1);
            }
            addStatCard(list,"PRZYCHÓD ŁĄCZNIE",String.format(Locale.US,"%.2f zł",all),0xff172b43);
            TextView title=new TextView(this);title.setText("WYNIKI WEDŁUG MODELU");title.setTextSize(16);title.setTypeface(null,1);title.setTextColor(0xff172235);title.setPadding(2,12,2,8);list.addView(title);
            ArrayList<String> keys=new ArrayList<>(names.keySet());Collections.sort(keys,(a,b)->Double.compare(totals.get(b)[2],totals.get(a)[2]));
            if(keys.isEmpty()){TextView empty=new TextView(this);empty.setText("Brak rozliczonych wynajmów w tym okresie.");list.addView(empty);}
            for(String key:keys){double[] v=totals.get(key);LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(14,12,14,12);card.setBackground(roundBg(Color.WHITE,12));
                TextView nm=new TextView(this);nm.setText(names.get(key));nm.setTextSize(15);nm.setTypeface(null,1);nm.setTextColor(0xff172b43);card.addView(nm);
                double[] costData=costs.containsKey(key)?costs.get(key):new double[2];double result=v[2]-v[3]-costData[0]-costData[1];
                TextView d=new TextView(this);d.setText("Wynajmy: "+(int)v[0]+"  •  Dni: "+(int)v[1]+"\nPrzychód: "+String.format(Locale.US,"%.2f zł",v[2])+"\nStraty z najmu: "+String.format(Locale.US,"%.2f zł",v[3])+"\nZakup: "+String.format(Locale.US,"%.2f zł",costData[0])+"  •  Naprawy: "+String.format(Locale.US,"%.2f zł",costData[1])+"\nWynik po kosztach: "+String.format(Locale.US,"%.2f zł",result));d.setTextColor(0xff526174);d.setTextSize(13);d.setLineSpacing(2,1f);d.setPadding(0,6,0,8);card.addView(d);
                Button cost=new Button(this);cost.setText("＋ DODAJ KOSZT");brandButton(cost,false);cost.setTextSize(12);cost.setMinHeight(0);cost.setMinimumHeight(0);cost.setPadding(8,2,8,2);cost.setOnClickListener(vw->addEquipmentCost(names.get(key),()->draw[0].run()));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=4;card.addView(cost,cp);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=8;list.addView(card,lp);
            }
        };
        years.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> a,View v,int p,long id){draw[0].run();}public void onNothingSelected(android.widget.AdapterView<?> a){}});
        Button back=new Button(this);back.setText("← WRÓĆ DO STATYSTYK");brandButton(back,false);back.setTextSize(12);back.setMinHeight(44);back.setOnClickListener(v->reservationStatsPage());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=6;root.addView(back,bp); draw[0].run();
    }
    private void addEquipmentCost(String equipment,Runnable refresh) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);Spinner kind=new Spinner(this);kind.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Zakup sprzętu","Naprawa / serwis"}));box.addView(kind);
        EditText amount=new EditText(this);amount.setHint("Kwota w zł");amount.setInputType(8194);box.addView(amount);
        EditText date=new EditText(this);date.setHint("Data RRRR-MM-DD");date.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()));box.addView(date);
        new AlertDialog.Builder(this).setTitle(equipment).setView(box).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(dlg,which)->{try{
            double value=Double.parseDouble(amount.getText().toString().trim().replace(',','.'));String ds=date.getText().toString().trim();if(value<=0||!ds.matches("\\d{4}-\\d{2}-\\d{2}"))throw new Exception();
            JSONObject e=new JSONObject();e.put("id",UUID.randomUUID().toString());e.put("date",ds);e.put("month",ds.substring(0,7));e.put("name",equipment);e.put("amount",value);e.put("type",kind.getSelectedItemPosition()==0?"investment":"expense");e.put("equipment_name",equipment);e.put("note",kind.getSelectedItemPosition()==0?"Zakup modelu":"Naprawa / serwis");
            store.saveExpense(e);store.enqueue("expense_save",e);if(isOnline())syncNow();toast("Zapisano koszt sprzętu.");refresh.run();
        }catch(Exception ex){toast("Sprawdź kwotę i datę.");}}).show();
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
        String raw=item.optString("text","");java.util.regex.Matcher modern=java.util.regex.Pattern.compile("(?:^|\\s)([01]?\\d|2[0-3]):([0-5]\\d)(?:$|\\s|•)").matcher(raw);
        if(modern.find())return String.format(Locale.US,"%02d:%s",Integer.parseInt(modern.group(1)),modern.group(2));
        java.util.regex.Matcher legacy=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+.+?\\s+(\\d{1,2})\\s*/").matcher(raw);
        return legacy.find()?String.format(Locale.US,"%02d:00",Integer.parseInt(legacy.group(1))):"";
    }
    private boolean isPhoneNumber(String value) {
        if(value==null)return false;
        String digits=value.replaceAll("[^0-9]","");
        return digits.length()>=7 && digits.length()<=15 && value.matches("[+0-9() .-]+");
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
    private Calendar parseReservationDate(String value,Calendar fallback) {
        Calendar result=(Calendar)fallback.clone();
        if(value!=null&&value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try{SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setLenient(false);Date parsed=format.parse(value);if(parsed!=null)result.setTime(parsed);}catch(Exception ignored){}
        }
        result.set(Calendar.HOUR_OF_DAY,12);result.set(Calendar.MINUTE,0);result.set(Calendar.SECOND,0);result.set(Calendar.MILLISECOND,0);
        return result;
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

    private String customerPhoneKey(String value) {
        if(value==null)return "";String v=value.trim();if(!v.matches("[+0-9() .\\-]+"))return "";
        String digits=v.replaceAll("[^0-9]","");
        if(digits.length()==11&&digits.startsWith("48"))digits=digits.substring(2);
        if(digits.length()==9)return digits;
        if(digits.length()>=10&&digits.length()<=15)return "+"+digits;
        return "";
    }


    private String customerDate(String raw) {
        if(raw==null) return "";
        raw=raw.trim();
        if(raw.matches("\\d{4}-\\d{2}-\\d{2}.*")) return raw.substring(0,10);
        if(raw.matches("\\d{2}\\.\\d{2}\\.\\d{4}.*")) return raw.substring(6,10)+"-"+raw.substring(3,5)+"-"+raw.substring(0,2);
        return "";
    }
    private String customerLastActivity(JSONObject c) {
        String last="";
        JSONArray reservations=c.optJSONArray("reservations");
        if(reservations!=null) for(int i=0;i<reservations.length();i++) {
            JSONObject r=reservations.optJSONObject(i); if(r==null)continue;
            for(String key:new String[]{"settled_at","end_date","start_date"}) {
                String d=customerDate(r.optString(key));
                if(d.compareTo(last)>0)last=d;
            }
        }
        JSONArray protocols=c.optJSONArray("protocols");
        if(protocols!=null) for(int i=0;i<protocols.length();i++) {
            JSONObject p=protocols.optJSONObject(i);if(p==null)continue;
            for(String key:new String[]{"return_date","rental_date"}) {
                String d=customerDate(p.optString(key));
                if(d.compareTo(last)>0)last=d;
            }
        }
        return last;
    }
    private int customerReservationCount(JSONObject c){
        JSONArray r=c.optJSONArray("reservations");return r==null?0:r.length();
    }
    private int customerProtocolCount(JSONObject c){
        JSONArray p=c.optJSONArray("protocols");return p==null?0:p.length();
    }
    private int customerPaidCount(JSONObject c){
        int count=0;JSONArray reservations=c.optJSONArray("reservations");
        if(reservations!=null)for(int i=0;i<reservations.length();i++){
            JSONObject r=reservations.optJSONObject(i);
            if(r!=null&&r.optBoolean("settled")&&r.has("income")&&!r.isNull("income"))count++;
        }
        return count;
    }
    private double customerTotalRevenue(JSONObject c){
        double total=0;JSONArray reservations=c.optJSONArray("reservations");
        if(reservations!=null)for(int i=0;i<reservations.length();i++){
            JSONObject r=reservations.optJSONObject(i);
            if(r!=null&&r.optBoolean("settled")&&r.has("income")&&!r.isNull("income")){
                double amount=r.optDouble("income",0);
                if(!Double.isNaN(amount)&&!Double.isInfinite(amount)&&amount>=0)total+=amount;
            }
        }
        return total;
    }
    private String customerMoney(double amount){
        return String.format(new Locale("pl","PL"),"%,.2f zł",amount);
    }
    private boolean customerIsAdmin(){
        return "admin".equals(getSharedPreferences("rentmor",0).getString("role",""));
    }
    private String customerTitle(JSONObject c){
        String value=c.optString("name","").trim();
        return value.isEmpty()?"Nieuzupełniona nazwa":value;
    }
    private String customerDisplayDate(String value){
        if(value==null||value.length()<10)return "Brak daty";
        return value.substring(8,10)+"."+value.substring(5,7)+"."+value.substring(0,4);
    }
    private void customerContact(String raw,boolean sms){
        String phone=raw==null?"":raw.replaceAll("[^+0-9]","");
        if(!phone.matches("\\+?\\d{9,15}")){toast("Ten klient nie ma prawidłowego numeru telefonu.");return;}
        try{
            Intent intent=new Intent(sms?Intent.ACTION_SENDTO:Intent.ACTION_DIAL,
                android.net.Uri.parse((sms?"smsto:":"tel:")+phone));
            startActivity(intent);
        }catch(Exception e){toast(sms?"Nie znaleziono aplikacji SMS.":"Nie znaleziono aplikacji telefonu.");}
    }
    private void customerAction(LinearLayout parent,String caption,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(caption);brandButton(b,primary);
        b.setTextSize(11);b.setPadding(dp(4),dp(7),dp(4),dp(7));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(2),0,dp(2),0);
        parent.addView(b,lp);
    }
    private void customerPage() {
        root.removeAllViews();root.setOrientation(1);root.setPadding(14,16,14,18);root.setBackgroundColor(0xfff5f6fa);
        TextView heading=new TextView(this);heading.setText("KLIENCI");heading.setTextSize(25);heading.setTypeface(null,1);heading.setTextColor(0xff172235);root.addView(heading);
        TextView info=new TextView(this);info.setText("Karty klientów, historia wypożyczeń i notatki.");info.setTextSize(13);info.setTextColor(0xff697386);info.setPadding(0,4,0,10);root.addView(info);

        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);
        search.setHint("Szukaj po nazwie, nicku lub telefonie");
        search.setPadding(dp(12),dp(10),dp(12),dp(10));search.setBackground(roundBg(Color.WHITE,12));
        root.addView(search,new LinearLayout.LayoutParams(-1,-2));
        final int[] filter={0};
        LinearLayout filters=new LinearLayout(this);filters.setOrientation(0);
        android.widget.HorizontalScrollView filterScroll=new android.widget.HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);filterScroll.addView(filters);
        LinearLayout.LayoutParams filterLp=new LinearLayout.LayoutParams(-1,-2);filterLp.topMargin=dp(8);root.addView(filterScroll,filterLp);

        LinearLayout sortRow=new LinearLayout(this);sortRow.setOrientation(0);sortRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sortCaption=new TextView(this);sortCaption.setText("Sortuj:");sortCaption.setTextColor(0xff526174);sortCaption.setTextSize(12);sortRow.addView(sortCaption);
        Spinner order=new Spinner(this);
        String[] sorting={"Ostatnia aktywność","Najwięcej rezerwacji","Alfabetycznie"};
        ArrayAdapter<String> orderAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,sorting);
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        order.setAdapter(orderAdapter);sortRow.addView(order,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams sortLp=new LinearLayout.LayoutParams(-1,-2);sortLp.topMargin=dp(3);sortLp.bottomMargin=dp(6);root.addView(sortRow,sortLp);

        TextView counter=new TextView(this);counter.setTextColor(0xff697386);counter.setTextSize(12);counter.setPadding(dp(3),0,0,dp(7));root.addView(counter);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
        LinearLayout list=new LinearLayout(this);list.setOrientation(1);list.setPadding(0,0,0,dp(10));
        sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()->{
            list.removeAllViews();JSONArray all=store.customers();
            String q=search.getText().toString().trim().toLowerCase(new Locale("pl","PL"));
            ArrayList<JSONObject> visible=new ArrayList<>();
            for(int i=0;i<all.length();i++){
                JSONObject customer=all.optJSONObject(i);if(customer==null)continue;
                String name=customer.optString("name").toLowerCase(new Locale("pl","PL"));
                String phone=customer.optString("phone").toLowerCase(new Locale("pl","PL"));
                if(!q.isEmpty()&&!name.contains(q)&&!phone.contains(q))continue;
                if(filter[0]==1&&customerLastActivity(customer).isEmpty())continue;
                if(filter[0]==2&&customerReservationCount(customer)<2)continue;
                if(filter[0]==3&&!customer.optString("name").trim().isEmpty())continue;
                visible.add(customer);
            }
            Collections.sort(visible,(a,b)->{
                int value;
                if(order.getSelectedItemPosition()==1)value=Integer.compare(customerReservationCount(b),customerReservationCount(a));
                else if(order.getSelectedItemPosition()==2)value=customerTitle(a).compareToIgnoreCase(customerTitle(b));
                else value=customerLastActivity(b).compareTo(customerLastActivity(a));
                if(value!=0)return value;
                return customerTitle(a).compareToIgnoreCase(customerTitle(b));
            });
            counter.setText("Klienci: "+visible.size()+" z "+all.length());
            for(JSONObject customer:visible){
                String phone=customer.optString("phone","");
                int reservations=customerReservationCount(customer),protocols=customerProtocolCount(customer);
                LinearLayout card=new LinearLayout(this);card.setOrientation(1);
                card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(roundBg(Color.WHITE,14));
                LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(0);titleRow.setGravity(Gravity.TOP);
                TextView name=new TextView(this);name.setText(customerTitle(customer));name.setTextSize(16);name.setTypeface(null,1);name.setTextColor(0xff172235);
                titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                TextView badge=new TextView(this);badge.setText(reservations+" rez.");
                badge.setTextSize(11);badge.setTypeface(null,1);badge.setTextColor(0xff775300);
                badge.setPadding(dp(8),dp(5),dp(8),dp(5));badge.setBackground(roundBg(0xfffff1be,12));titleRow.addView(badge);
                card.addView(titleRow);
                TextView phoneLabel=new TextView(this);phoneLabel.setText(phone);phoneLabel.setTextSize(14);phoneLabel.setTextColor(0xff526174);phoneLabel.setPadding(0,dp(3),0,dp(4));card.addView(phoneLabel);
                TextView stats=new TextView(this);
                stats.setText("Rezerwacje: "+reservations+"  •  Protokoły: "+protocols);
                stats.setTextSize(12);stats.setTextColor(0xff697386);card.addView(stats);
                String date=customerLastActivity(customer);
                if(!date.isEmpty()){
                    TextView recent=new TextView(this);recent.setText("Ostatni wpis: "+customerDisplayDate(date));
                    recent.setTextSize(12);recent.setTextColor(0xff697386);card.addView(recent);
                }
                if(customerIsAdmin()&&customerPaidCount(customer)>0){
                    TextView paid=new TextView(this);
                    paid.setText("Przychód z rozliczonych najmów: "+customerMoney(customerTotalRevenue(customer)));
                    paid.setTextColor(0xff22744e);paid.setTextSize(12);paid.setPadding(0,dp(3),0,0);card.addView(paid);
                }
                String note=customer.optString("notes","").trim();
                if(!note.isEmpty()){
                    TextView noteView=new TextView(this);noteView.setText("Notatka: "+note.replace('\n',' '));
                    noteView.setTextSize(12);noteView.setTextColor(0xff526174);noteView.setMaxLines(2);
                    noteView.setEllipsize(android.text.TextUtils.TruncateAt.END);noteView.setPadding(0,dp(5),0,0);card.addView(noteView);
                }
                LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(0);
                LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-1,-2);blp.topMargin=dp(10);card.addView(buttons,blp);
                customerAction(buttons,"Zadzwoń",false,()->customerContact(phone,false));
                customerAction(buttons,"SMS",false,()->customerContact(phone,true));
                customerAction(buttons,"Historia",true,()->customerDetail(customer));
                if(customerIsAdmin())customerAction(buttons,"Edytuj",false,()->customerEditDialog(customer,()->renderCustomerPageAfterEdit()));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);list.addView(card,lp);
                card.setOnClickListener(v->customerDetail(customer));
            }
            if(visible.isEmpty()){
                TextView empty=new TextView(this);
                empty.setText(all.length()==0?"Brak klientów. Wybierz Synchronizuj.":"Nie znaleziono klientów pasujących do wyszukiwania lub filtra.");
                empty.setTextSize(14);empty.setTextColor(0xff697386);empty.setPadding(dp(10),dp(16),dp(10),dp(16));list.addView(empty);
            }
        };
        String[] options={"Wszyscy","Z datą aktywności","2+ rezerwacje","Bez nazwy"};
        final Runnable[] drawFilters=new Runnable[1];
        drawFilters[0]=()->{
            filters.removeAllViews();
            for(int i=0;i<options.length;i++){
                final int selected=i;Button b=new Button(this);b.setText(options[i]);brandButton(b,filter[0]==i);
                b.setTextSize(11);b.setSingleLine(true);b.setPadding(dp(12),dp(7),dp(12),dp(7));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(6);filters.addView(b,lp);
                b.setOnClickListener(v->{filter[0]=selected;drawFilters[0].run();render.run();});
            }
        };
        drawFilters[0].run();
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){render.run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        order.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){render.run();}
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        render.run();
        addButton("⟳ SYNCHRONIZUJ",()->{
            if(!isOnline()){toast("Brak internetu. Pokazuję dane z telefonu.");return;}
            new Thread(()->{try{sync.sync();runOnUiThread(()->{render.run();toast("Karty klientów zaktualizowane.");});}
                catch(Exception e){runOnUiThread(()->toast("Błąd synchronizacji: "+e.getMessage()));}}).start();
        });
        addButton("WRÓĆ",()->showHome());
    }
    private void renderCustomerPageAfterEdit(){
        // Reopen the list with freshly saved local data; no server round trip required.
        customerPage();
    }
    private void saveCustomerProfile(JSONObject existing,String newName,String newNotes,Runnable afterLocalSave){
        if(!customerIsAdmin()){toast("Edycja klientów jest dostępna tylko dla administratora.");return;}
        String name=newName==null?"":newName.trim(),notes=newNotes==null?"":newNotes.trim();
        try {
            JSONObject op=new JSONObject();
            op.put("id",existing.optString("phone_key"));
            op.put("phone",existing.optString("phone"));
            op.put("name",name);op.put("notes",notes);
            JSONObject updated=new JSONObject(existing.toString());
            updated.put("name",name);updated.put("notes",notes);
            store.enqueue("customer_save",op);
            store.saveCustomerDraft(updated);
            afterLocalSave.run();
            if(isOnline()){
                new Thread(()->{
                    try{sync.sync();runOnUiThread(()->toast("Dane i notatki klienta zsynchronizowane."));}
                    catch(Exception e){runOnUiThread(()->toast("Zapisano na telefonie. Dane czekają na synchronizację."));}
                }).start();
            } else toast("Zapisano na telefonie. Synchronizacja nastąpi po połączeniu z internetem.");
        }catch(Exception e){toast("Nie udało się zapisać karty klienta: "+e.getMessage());}
    }
    private void customerEditDialog(JSONObject customer,Runnable done){
        LinearLayout content=new LinearLayout(this);content.setOrientation(1);
        content.setPadding(dp(18),dp(10),dp(18),dp(4));
        TextView header=new TextView(this);header.setText("Nazwa klienta lub nick z OLX");
        header.setTextSize(13);header.setTextColor(0xff526174);content.addView(header);
        EditText name=new EditText(this);name.setSingleLine(true);name.setText(customer.optString("name"));name.setTextSize(16);content.addView(name);
        TextView notesLabel=new TextView(this);notesLabel.setText("Notatka o kliencie");notesLabel.setTextSize(13);
        notesLabel.setTextColor(0xff526174);notesLabel.setPadding(0,dp(10),0,0);content.addView(notesLabel);
        EditText notes=new EditText(this);notes.setMinLines(3);notes.setGravity(Gravity.TOP|Gravity.START);
        notes.setText(customer.optString("notes"));notes.setHint("Np. preferuje odbiór wieczorem");
        notes.setTextSize(15);content.addView(notes);
        new AlertDialog.Builder(this).setTitle("Edytuj klienta")
            .setView(content).setNegativeButton("Anuluj",null)
            .setPositiveButton("Zapisz",(dialog,which)->saveCustomerProfile(customer,name.getText().toString(),notes.getText().toString(),done))
            .show();
    }
    private JSONObject findCustomerByKey(String key,JSONObject fallback) {
        JSONArray items=store.customers();
        for(int i=0;i<items.length();i++){
            JSONObject c=items.optJSONObject(i);
            if(c!=null&&key.equals(c.optString("phone_key")))return c;
        }
        return fallback;
    }
    private void customerDetail(JSONObject original) {
        JSONObject customer=findCustomerByKey(original.optString("phone_key"),original);
        root.removeAllViews();root.setOrientation(1);root.setPadding(14,16,14,18);root.setBackgroundColor(0xfff5f6fa);
        TextView title=new TextView(this);title.setText(customerTitle(customer));title.setTextSize(23);
        title.setTypeface(null,1);title.setTextColor(0xff172235);root.addView(title);
        TextView phone=new TextView(this);phone.setText(customer.optString("phone"));
        phone.setTextSize(15);phone.setTextColor(0xff526174);phone.setPadding(0,dp(4),0,dp(12));root.addView(phone);
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(0);
        root.addView(buttons,new LinearLayout.LayoutParams(-1,-2));
        String phoneNumber=customer.optString("phone");
        customerAction(buttons,"Zadzwoń",false,()->customerContact(phoneNumber,false));
        customerAction(buttons,"SMS",false,()->customerContact(phoneNumber,true));
        if(customerIsAdmin())customerAction(buttons,"Edytuj i notatki",true,
            ()->customerEditDialog(customer,()->customerDetail(findCustomerByKey(customer.optString("phone_key"),customer))));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(0,dp(12),0,dp(16));
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout overview=new LinearLayout(this);overview.setOrientation(1);
        overview.setPadding(dp(13),dp(12),dp(13),dp(12));overview.setBackground(roundBg(Color.WHITE,14));
        body.addView(overview);
        detailLine(overview,"Rezerwacje",String.valueOf(customerReservationCount(customer)));
        detailLine(overview,"Protokoły",String.valueOf(customerProtocolCount(customer)));
        String latest=customerLastActivity(customer);
        if(!latest.isEmpty())detailLine(overview,"Ostatni wpis",customerDisplayDate(latest));
        if(customerIsAdmin()){
            if(customerPaidCount(customer)>0){
                detailLine(overview,"Przychód z "+customerPaidCount(customer)+" rozliczonych rezerwacji",
                    customerMoney(customerTotalRevenue(customer)));
            } else detailLine(overview,"Rozliczenia","Brak rozliczonych wynajmów z kwotą");
        }
        String notes=customer.optString("notes","").trim();
        LinearLayout notesBox=new LinearLayout(this);notesBox.setOrientation(1);
        notesBox.setPadding(dp(13),dp(12),dp(13),dp(12));
        notesBox.setBackground(roundBg(0xfffff8e4,13));
        LinearLayout.LayoutParams notesParams=new LinearLayout.LayoutParams(-1,-2);notesParams.topMargin=dp(10);body.addView(notesBox,notesParams);
        TextView notesHeader=new TextView(this);notesHeader.setText("NOTATKI O KLIENCIE");
        notesHeader.setTextColor(0xff7c5c16);notesHeader.setTypeface(null,1);notesHeader.setTextSize(13);notesBox.addView(notesHeader);
        TextView noteText=new TextView(this);
        noteText.setText(notes.isEmpty()?"Brak notatek. Możesz je dodać przez Edytuj i notatki.":notes);
        noteText.setTextColor(0xff424954);noteText.setTextSize(14);noteText.setPadding(0,dp(7),0,0);notesBox.addView(noteText);

        TextView historyHeading=new TextView(this);historyHeading.setText("HISTORIA WYNAJMÓW");
        historyHeading.setTextColor(0xff172235);historyHeading.setTextSize(17);historyHeading.setTypeface(null,1);
        historyHeading.setPadding(0,dp(18),0,dp(9));body.addView(historyHeading);

        ArrayList<JSONObject> history=new ArrayList<>();
        JSONArray reservations=customer.optJSONArray("reservations");
        if(reservations!=null)for(int i=0;i<reservations.length();i++){
            JSONObject r=reservations.optJSONObject(i);if(r==null)continue;
            try{JSONObject item=new JSONObject();item.put("kind","reservation");
                item.put("date",customerDate(r.optString("start_date")));
                item.put("original",r);history.add(item);}catch(Exception ignored){}
        }
        JSONArray protocols=customer.optJSONArray("protocols");
        if(protocols!=null)for(int i=0;i<protocols.length();i++){
            JSONObject p=protocols.optJSONObject(i);if(p==null)continue;
            try{JSONObject item=new JSONObject();item.put("kind","protocol");
                item.put("date",customerDate(p.optString("rental_date")));
                item.put("original",p);history.add(item);}catch(Exception ignored){}
        }
        Collections.sort(history,(a,b)->b.optString("date").compareTo(a.optString("date")));
        if(history.isEmpty()){
            TextView empty=new TextView(this);empty.setText("Brak rezerwacji i protokołów.");
            empty.setTextColor(0xff697386);body.addView(empty);
        }
        for(JSONObject item:history){
            JSONObject entry=item.optJSONObject("original");if(entry==null)continue;
            boolean isReservation="reservation".equals(item.optString("kind"));
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);
            card.setPadding(dp(13),dp(11),dp(13),dp(11));card.setBackground(roundBg(Color.WHITE,12));
            TextView kind=new TextView(this);kind.setText(isReservation?"REZERWACJA":"PROTOKÓŁ");
            kind.setTextSize(11);kind.setTypeface(null,1);kind.setTextColor(0xff526174);card.addView(kind);
            String equipment=entry.optString("equipment","");
            TextView equipmentView=new TextView(this);
            equipmentView.setText(equipment.isEmpty()?(isReservation?entry.optString("text","Rezerwacja"):entry.optString("number","Protokół")):equipment);
            equipmentView.setTextSize(15);equipmentView.setTypeface(null,1);equipmentView.setTextColor(0xff172235);equipmentView.setPadding(0,dp(3),0,dp(4));card.addView(equipmentView);
            TextView details=new TextView(this);
            String when=isReservation?entry.optString("start_date",entry.optString("month")):entry.optString("rental_date");
            String status=isReservation?(entry.optBoolean("settled")?"Rozliczona":entry.optBoolean("done")?"Wydana":"Rezerwacja"):entry.optString("status","Protokół");
            String text=when+"  •  "+status;
            if(isReservation&&customerIsAdmin()&&entry.optBoolean("settled")&&entry.has("income")){
                text+="\nPrzychód: "+customerMoney(entry.optDouble("income",0));
                if(entry.optDouble("loss",0)>0)text+="  •  Strata: "+customerMoney(entry.optDouble("loss",0));
            }
            if(!isReservation)text+="\nDotknij, aby otworzyć protokół";
            details.setText(text);details.setTextSize(12);details.setTextColor(0xff697386);card.addView(details);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);body.addView(card,lp);
            if(!isReservation)card.setOnClickListener(v->showProtocolDetail(entry,
                ()->customerDetail(findCustomerByKey(customer.optString("phone_key"),customer))));
        }
        addButton("WRÓĆ DO KLIENTÓW",()->customerPage());
    }

    private JSONObject findReservation(JSONArray reservations,String id){for(int i=0;i<reservations.length();i++){JSONObject r=reservations.optJSONObject(i);if(r!=null&&id.equals(r.optString("id")))return r;}return null;}
    private void newIssue(){
        issuePhotos.clear(); signatureView=null;
        root.removeAllViews(); TextView h=new TextView(this);h.setText("NOWY PROTOKÓŁ WYDANIA");h.setTextSize(20);h.setTextColor(0xff172235);root.addView(h);
        EditText client=field("Imię i nazwisko / firma klienta");EditText phone=field("Telefon");
        TextView reservationLabel=new TextView(this);reservationLabel.setText("Powiąż z rezerwacją (opcjonalnie)");reservationLabel.setTextColor(0xff526174);reservationLabel.setPadding(0,10,0,2);root.addView(reservationLabel);
        JSONArray availableReservations=store.allReservations();ArrayList<String> reservationLabels=new ArrayList<>();ArrayList<String> reservationIds=new ArrayList<>();reservationLabels.add("— Bez przypisania —");reservationIds.add("");
        for(int ri=0;ri<availableReservations.length();ri++){JSONObject rr=availableReservations.optJSONObject(ri);if(rr==null)continue;String rid=rr.optString("id");if(rid.isEmpty())continue;String label=rr.optString("start_date",rr.optString("month"))+" • "+rr.optString("equipment",rr.optString("text"))+" • "+rr.optString("phone");reservationLabels.add(label);reservationIds.add(rid);}
        Spinner reservationSpinner=new Spinner(this);reservationSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,reservationLabels));root.addView(reservationSpinner,new LinearLayout.LayoutParams(-1,-2));
        reservationSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){if(position>0){JSONObject rr=findReservation(availableReservations,reservationIds.get(position));if(rr!=null){if(client.getText().toString().trim().isEmpty())client.setText(rr.optString("customer_name",rr.optString("client")));if(phone.getText().toString().trim().isEmpty())phone.setText(rr.optString("phone"));}}}public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        // Automatyczne dopasowanie rezerwacji po pełnym, znormalizowanym numerze telefonu.
        Runnable autoMatchReservation=()->{
            String key=customerPhoneKey(phone.getText().toString());int match=-1;
            if(!key.isEmpty())for(int ri=0;ri<availableReservations.length();ri++){
                JSONObject rr=availableReservations.optJSONObject(ri);if(rr==null)continue;
                if(key.equals(customerPhoneKey(rr.optString("phone")))){int candidate=reservationIds.indexOf(rr.optString("id"));if(candidate>0){match=candidate;break;}}
            }
            if(match>0){reservationSpinner.setSelection(match);JSONObject rr=findReservation(availableReservations,reservationIds.get(match));
                if(rr!=null&&client.getText().toString().trim().isEmpty())client.setText(rr.optString("customer_name",rr.optString("client")));
            }else reservationSpinner.setSelection(0);
        };
        phone.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int st,int count,int after){}public void onTextChanged(CharSequence x,int st,int before,int count){autoMatchReservation.run();}public void afterTextChanged(android.text.Editable e){}});
        phone.post(autoMatchReservation);
        TextView equipmentLabel=new TextView(this);equipmentLabel.setText("Sprzęt z aktualnej listy RentMor");equipmentLabel.setTextColor(0xff526174);equipmentLabel.setPadding(0,10,0,2);root.addView(equipmentLabel);
        Spinner equipment=new Spinner(this);populateEquipment(equipment);root.addView(equipment,new LinearLayout.LayoutParams(-1,-2));
        addButton("⟳ Odśwież listę sprzętów",()->refreshEquipment(equipment));
        EditText date=dateField("Data wydania",new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date()));EditText condition=field("Stan sprzętu");EditText accessories=field("Akcesoria");EditText notes=field("Uwagi");
        photoCount=new TextView(this);photoCount.setText("Nie dodano zdjęć.");photoCount.setTextColor(0xff526174);photoCount.setPadding(0,8,0,4);root.addView(photoCount);addButton("＋ Dodaj zdjęcia (można wybrać kilka)",()->pickPhoto());
        signatureView=new SignatureView(this); root.addView(signatureView,new LinearLayout.LayoutParams(-1,180));
        addButton("ZAPISZ PROTOKÓŁ W TELEFONIE",()->{if(client.getText().toString().trim().isEmpty()||equipment.getSelectedItem()==null||equipment.getSelectedItemPosition()==0){toast("Wpisz klienta i sprzęt.");return;}
            try{String id=UUID.randomUUID().toString();JSONObject p=new JSONObject();p.put("id",id);p.put("number","RM-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date()));p.put("client",client.getText());p.put("phone",phone.getText());p.put("reservation_id",reservationIds.get(reservationSpinner.getSelectedItemPosition()));p.put("equipment",equipment.getSelectedItem().toString());p.put("rental_date",date.getText());p.put("condition",condition.getText());p.put("accessories",accessories.getText());p.put("notes",notes.getText());p.put("status","wydany");JSONArray photos=new JSONArray();for(Bitmap photo:issuePhotos)photos.put(bitmapBase64(photo,1920,85));if(photos.length()>0)p.put("photos_base64",photos);if(signatureView!=null)p.put("signature_base64",bitmapBase64(signatureView.image(),900,85));store.enqueue("create",p);makePdf(p);toast("Zapisano lokalnie. PDF znajduje się w Pobrane/RentMor.");showHome();}catch(Exception ex){toast("Błąd zapisu: "+ex.getMessage());}});
        addButton("WRÓĆ",()->showHome());
    }
    private void protocolList(){root.removeAllViews();root.setOrientation(1);root.setPadding(20,20,20,20);root.setBackgroundColor(0xfff5f6fa);
        TextView h=new TextView(this);h.setText("Twoje protokoły");h.setTextSize(25);h.setTypeface(null,1);h.setTextColor(0xff172235);root.addView(h);
        TextView info=new TextView(this);info.setText("Wybierz dokument, aby zobaczyć jego szczegóły i zdjęcia.");info.setTextSize(13);info.setTextColor(0xff697386);info.setPadding(0,5,0,16);root.addView(info);
        ScrollView sc=new ScrollView(this);LinearLayout items=new LinearLayout(this);items.setOrientation(1);sc.addView(items);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        JSONArray a=store.allProtocols();for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;
            LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(16,14,16,14);card.setBackground(roundBg(Color.WHITE,16));card.setElevation(2);
            TextView num=new TextView(this);num.setText(p.optString("number","Protokół"));num.setTextSize(16);num.setTypeface(null,1);num.setTextColor(0xff172b43);card.addView(num);
            TextView desc=new TextView(this);String linkedReservation=p.optString("reservation_id");desc.setText(p.optString("client")+"  •  "+p.optString("equipment")+(linkedReservation.isEmpty()?"":"\n✓ Przypisano do rezerwacji: "+linkedReservation));desc.setTextSize(13);desc.setTextColor(0xff526174);desc.setPadding(0,6,0,5);card.addView(desc);
            TextView st=new TextView(this);st.setText("Status: "+p.optString("status")+"     ›");st.setTextSize(12);st.setTextColor(0xff172235);card.addView(st);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=12;items.addView(card,cp);card.setOnClickListener(v->showProtocolDetail(p));
        }
        if(a.length()==0){TextView empty=new TextView(this);empty.setText("Nie ma jeszcze zapisanych protokołów.");empty.setTextColor(0xff697386);items.addView(empty);}
        addButton("WRÓĆ",()->showHome());
    }
    private void detailLine(LinearLayout box,String label,String value){if(value==null||value.trim().isEmpty())return;TextView t=new TextView(this);t.setText(label+"\\n"+value);t.setTextSize(14);t.setTextColor(0xff24364b);t.setPadding(0,7,0,9);box.addView(t);}
    private void showProtocolDetail(JSONObject p){showProtocolDetail(p,()->protocolList());}
    private void showProtocolDetail(JSONObject p,Runnable backAction){
        root.removeAllViews();root.setOrientation(1);root.setPadding(20,18,20,20);root.setBackgroundColor(0xfff5f6fa);
        ScrollView sc=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(1);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        TextView title=new TextView(this);title.setText(p.optString("number","Protokół"));title.setTextSize(23);title.setTypeface(null,1);title.setTextColor(0xff172235);content.addView(title);
        TextView state=new TextView(this);state.setText("Status: "+p.optString("status"));state.setTextSize(13);state.setTextColor(0xff172235);state.setPadding(0,5,0,14);content.addView(state);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(16,12,16,12);panel.setBackground(roundBg(Color.WHITE,16));content.addView(panel);
        detailLine(panel,"Klient",p.optString("client"));detailLine(panel,"Telefon",p.optString("phone"));detailLine(panel,"Sprzęt",p.optString("equipment"));detailLine(panel,"Data wydania",p.optString("rental_date"));detailLine(panel,"Stan przy wydaniu",p.optString("condition"));detailLine(panel,"Akcesoria",p.optString("accessories"));detailLine(panel,"Uwagi",p.optString("notes"));
        detailLine(panel,"Data zwrotu",p.optString("return_date"));detailLine(panel,"Stan przy zwrocie",p.optString("return_condition"));detailLine(panel,"Uwagi do zwrotu",p.optString("return_notes"));
        JSONArray photos=p.optJSONArray("photos_base64");if((photos==null||photos.length()==0)&&p.optBoolean("photos_expired",false)){TextView expired=new TextView(this);expired.setText("Zdjęcia zostały usunięte z serwera po upływie 90 dni. Kopie zapisane na pierwotnym telefonie pozostają bez zmian.");expired.setTextSize(13);expired.setTextColor(0xff8a5a16);expired.setPadding(0,16,0,8);content.addView(expired);}if(photos!=null&&photos.length()>0){TextView ph=new TextView(this);ph.setText("Zdjęcia ("+photos.length()+")");ph.setTextSize(17);ph.setTypeface(null,1);ph.setTextColor(0xff172235);ph.setPadding(0,18,0,8);content.addView(ph);for(int i=0;i<photos.length();i++){try{byte[] raw=Base64.decode(photos.optString(i),Base64.DEFAULT);Bitmap bm=BitmapFactory.decodeByteArray(raw,0,raw.length);if(bm!=null){final Bitmap photo=bm;final int photoIndex=i+1;final int photoTotal=photos.length();ImageView iv=new ImageView(this);iv.setImageBitmap(photo);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setContentDescription("Dotknij, aby powiększyć zdjęcie "+photoIndex);iv.setOnClickListener(v->showZoomPhoto(photo,"Zdjęcie "+photoIndex+" z "+photoTotal));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.bottomMargin=10;content.addView(iv,ip);}}catch(Exception ignored){}}}
        addButton("OTWÓRZ GOTOWY PROTOKÓŁ (PDF)",()->previewPdf(p));addButton("Zapisz / wygeneruj PDF",()->makePdf(p));
        if("admin".equals(getSharedPreferences("rentmor",0).getString("role",""))) addButton("🗑 USUŃ PROTOKÓŁ",()->confirmDelete(p));
        addButton("WRÓĆ",backAction);
    }

    private void showZoomPhoto(Bitmap bitmap,String label){
        Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout frame=new LinearLayout(this);frame.setOrientation(1);frame.setBackgroundColor(Color.BLACK);
        TextView caption=new TextView(this);caption.setText(label+"   •   Przesuń i użyj dwóch palców, aby powiększyć");caption.setTextColor(Color.WHITE);caption.setTextSize(13);caption.setPadding(14,14,14,14);frame.addView(caption);
        ZoomImageView zoom=new ZoomImageView(this);zoom.setImageBitmap(bitmap);frame.addView(zoom,new LinearLayout.LayoutParams(-1,0,1));
        Button close=new Button(this);close.setText("ZAMKNIJ");close.setOnClickListener(v->dialog.dismiss());frame.addView(close,new LinearLayout.LayoutParams(-1,52));
        dialog.setContentView(frame);dialog.show();
    }
    private class ZoomImageView extends ImageView {
        private final Matrix matrix=new Matrix();private final ScaleGestureDetector detector;private float scale=1f,lastX,lastY;private int mode=0;
        ZoomImageView(Context context){super(context);setScaleType(ScaleType.MATRIX);setBackgroundColor(Color.BLACK);detector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float next=scale*d.getScaleFactor();next=Math.max(1f,Math.min(6f,next));float factor=next/scale;matrix.postScale(factor,factor,d.getFocusX(),d.getFocusY());scale=next;setImageMatrix(matrix);return true;}});}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            detector.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:lastX=e.getX();lastY=e.getY();mode=1;return true;
                case MotionEvent.ACTION_POINTER_DOWN:mode=2;return true;
                case MotionEvent.ACTION_MOVE:
                    if(mode==1&&!detector.isInProgress()&&scale>1f){float dx=e.getX()-lastX,dy=e.getY()-lastY;matrix.postTranslate(dx,dy);setImageMatrix(matrix);}
                    lastX=e.getX();lastY=e.getY();return true;
                case MotionEvent.ACTION_POINTER_UP:mode=1;lastX=e.getX();lastY=e.getY();return true;
                case MotionEvent.ACTION_UP:mode=0;return true;
            }return true;
        }
    }
    private void pickPhoto(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,4101);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==4102 && resultCode==RESULT_OK && data!=null){
            ArrayList<android.net.Uri> selected=new ArrayList<>();
            if(data.getClipData()!=null){
                android.content.ClipData clip=data.getClipData();
                for(int i=0;i<clip.getItemCount();i++)selected.add(clip.getItemAt(i).getUri());
            }
            if(data.getData()!=null&&!selected.contains(data.getData()))selected.add(data.getData());
            int remaining=Math.max(0,6-existingProductPhotoCount-productPhotos.size());
            ArrayList<android.net.Uri> toLoad=new ArrayList<>();
            for(android.net.Uri uri:selected){
                if(toLoad.size()>=remaining)break;
                if(uri!=null&&!productSelectedPhotoUris.contains(uri.toString())&&!toLoad.contains(uri))toLoad.add(uri);
            }
            if(toLoad.isEmpty()){toast("Nie wybrano nowych zdjęć lub osiągnięto limit 6.");return;}
            final int session=productPhotoSession;
            final LinearLayout target=productNewPhotoContainer;
            productPhotosLoading=true;updateSelectedProductPhotoPreviews();
            new Thread(()->{
                ArrayList<Bitmap> loadedImages=new ArrayList<>();
                ArrayList<String> imageKeys=new ArrayList<>();
                for(android.net.Uri uri:toLoad){
                    try{
                        Bitmap bitmap=readProductPhotoThumbnail(uri);
                        loadedImages.add(bitmap);imageKeys.add(uri.toString());
                    }catch(Exception ignored){}
                }
                runOnUiThread(()->{
                    if(session!=productPhotoSession||target!=productNewPhotoContainer){
                        for(Bitmap bitmap:loadedImages)if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
                        return;
                    }
                    for(int i=0;i<loadedImages.size();i++){
                        if(existingProductPhotoCount+productPhotos.size()>=6)break;
                        productPhotos.add(loadedImages.get(i));productSelectedPhotoUris.add(imageKeys.get(i));
                    }
                    productPhotosLoading=false;updateSelectedProductPhotoPreviews();
                    toast(loadedImages.size()>0?"Dodano "+loadedImages.size()+" zdjęć.":"Nie udało się wczytać wybranych zdjęć.");
                });
            }).start();
        }
        if(requestCode==4101 && resultCode==RESULT_OK && data!=null) try {
            int added=0;
            if(data.getClipData()!=null){android.content.ClipData clip=data.getClipData();for(int n=0;n<clip.getItemCount();n++){android.net.Uri uri=clip.getItemAt(n).getUri();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);try(InputStream in=getContentResolver().openInputStream(uri)){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null){issuePhotos.add(b);added++;}}}}
            else if(data.getData()!=null){android.net.Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);try(InputStream in=getContentResolver().openInputStream(uri)){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null){issuePhotos.add(b);added++;}}}
            if(photoCount!=null)photoCount.setText("Dodane zdjęcia: "+issuePhotos.size());
            toast(added>0?"Dodano "+added+" zdjęć.":"Nie udało się wczytać zdjęć.");
        } catch(Exception e){toast("Błąd zdjęć: "+e.getMessage());}
    }
    private void returnList(){
        root.removeAllViews(); TextView h=new TextView(this); h.setText("PROTOKOŁY DO ZWROTU"); h.setTextSize(20); h.setTextColor(0xff172235); root.addView(h);
        JSONArray a=store.allProtocols(); int count=0;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i); if(p==null||!"wydany".equals(p.optString("status")))continue; count++;
            Button b=new Button(this); b.setAllCaps(false); b.setText(p.optString("number")+ " • "+p.optString("client")+" \\n"+p.optString("equipment")); b.setOnClickListener(v->returnForm(p)); root.addView(b);
        }
        if(count==0){TextView t=new TextView(this);t.setText("Brak wydanych protokołów.");root.addView(t);}
        addButton("WRÓĆ",()->showHome());
    }
    private void returnForm(JSONObject original){
        signatureView=null;
        root.removeAllViews(); TextView h=new TextView(this);h.setText("ZWROT: "+original.optString("number"));h.setTextSize(20);h.setTextColor(0xff172235);root.addView(h);
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
            TextView pageLabel=new TextView(this);pageLabel.setGravity(Gravity.CENTER);pageLabel.setTextColor(0xff172235);pageLabel.setTextSize(14);box.addView(pageLabel);
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
