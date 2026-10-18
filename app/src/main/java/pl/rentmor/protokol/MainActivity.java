package pl.rentmor.protokol;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.database.*;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final String HOME="https://rentmor.pl/protocol.php";
    private LocalStore store; private SyncManager sync; private LinearLayout root; private TextView status;
    private WebView activation; private boolean showingApp=false;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(18,59,104));getWindow().setNavigationBarColor(Color.rgb(18,59,104));store=new LocalStore(this);sync=new SyncManager(this,store);
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
    private void showHome(){showingApp=true;root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,18,24,18);root.setBackgroundColor(0xfff1f5f9);
        TextView title=new TextView(this);title.setText("RENTMOR • PROTOKOŁY");title.setTextSize(22);title.setTextColor(0xff123b68);title.setTypeface(null,1);root.addView(title);
        status=new TextView(this);status.setText("Dane lokalne: "+store.allProtocols().length()+" • Oczekujące: "+store.pendingCount());status.setPadding(0,12,0,12);root.addView(status);
        addButton("Synchronizuj teraz",()->syncNow());addButton("Nowy protokół wydania",()->newIssue());addButton("Lista protokołów",()->protocolList());
        addButton("Aktywuj / zmień urządzenie",()->{getSharedPreferences("rentmor",0).edit().remove("cookie").apply();showActivation();});
        setContentView(root); if(isOnline()) syncNow();
    }
    private boolean isOnline(){android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.Network n=cm.getActiveNetwork();return n!=null&&cm.getNetworkCapabilities(n)!=null&&cm.getNetworkCapabilities(n).hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);}
    private void addButton(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    private EditText field(String hint){EditText e=new EditText(this);e.setSingleLine(false);e.setHint(hint);root.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private void syncNow(){if(!isOnline()){status.setText("Brak internetu. Dane pozostają bezpiecznie w telefonie.");return;}status.setText("Synchronizacja…");new Thread(()->{try{sync.sync();runOnUiThread(()->{status.setText("Synchronizacja zakończona. Oczekujące: "+store.pendingCount());});}catch(Exception e){runOnUiThread(()->status.setText("Nie wysłano: "+e.getMessage()+" • Dane zapisane lokalnie."));}}).start();}
    private void newIssue(){
        root.removeAllViews(); TextView h=new TextView(this);h.setText("NOWY PROTOKÓŁ WYDANIA");h.setTextSize(20);h.setTextColor(0xff123b68);root.addView(h);
        EditText client=field("Imię i nazwisko / firma klienta");EditText phone=field("Telefon");EditText equipment=field("Sprzęt");EditText date=field("Data wydania");EditText condition=field("Stan sprzętu");EditText accessories=field("Akcesoria");EditText notes=field("Uwagi");
        addButton("ZAPISZ PROTOKÓŁ W TELEFONIE",()->{if(client.getText().toString().trim().isEmpty()||equipment.getText().toString().trim().isEmpty()){toast("Wpisz klienta i sprzęt.");return;}
            try{String id=UUID.randomUUID().toString();JSONObject p=new JSONObject();p.put("id",id);p.put("number","RM-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date()));p.put("client",client.getText());p.put("phone",phone.getText());p.put("equipment",equipment.getText());p.put("rental_date",date.getText());p.put("condition",condition.getText());p.put("accessories",accessories.getText());p.put("notes",notes.getText());p.put("status","wydany");store.enqueue("create",p);makePdf(p);toast("Zapisano lokalnie. PDF znajduje się w Pobrane/RentMor.");showHome();}catch(Exception ex){toast("Błąd zapisu: "+ex.getMessage());}});
        addButton("WRÓĆ",()->showHome());
    }
    private void protocolList(){root.removeAllViews();TextView h=new TextView(this);h.setText("PROTOKOŁY W TELEFONIE");h.setTextSize(20);h.setTextColor(0xff123b68);root.addView(h);
        JSONArray a=store.allProtocols();for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;TextView t=new TextView(this);t.setText(p.optString("number")+" • "+p.optString("client")+"\n"+p.optString("equipment")+" • "+p.optString("status"));t.setPadding(8,16,8,16);root.addView(t);Button pdf=new Button(this);pdf.setText("Zapisz PDF");pdf.setOnClickListener(v->makePdf(p));root.addView(pdf);}
        addButton("WRÓĆ",()->showHome());
    }
    private void makePdf(JSONObject p){try{PdfDocument doc=new PdfDocument();PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();PdfDocument.Page page=doc.startPage(info);Canvas c=page.getCanvas();Paint paint=new Paint(3);paint.setColor(Color.BLACK);paint.setTextSize(20);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));c.drawText("RENTMOR – PROTOKÓŁ WYDANIA",40,55,paint);paint.setTextSize(13);paint.setTypeface(Typeface.DEFAULT);
        String[][] rows={{"Numer",p.optString("number")},{"Data",p.optString("rental_date")},{"Klient",p.optString("client")},{"Telefon",p.optString("phone")},{"Sprzęt",p.optString("equipment")},{"Stan sprzętu",p.optString("condition")},{"Akcesoria",p.optString("accessories")},{"Uwagi",p.optString("notes")}};
        int y=100;for(String[] row:rows){c.drawText(row[0]+": "+row[1],40,y,paint);y+=32;}c.drawText("Podpis klienta: ______________________________",40,720,paint);doc.finishPage(page);
        File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"");dir.mkdirs();File f=new File(dir,p.optString("number","protokol").replaceAll("[^A-Za-z0-9_-]","_")+".pdf");try(FileOutputStream out=new FileOutputStream(f)){doc.writeTo(out);}doc.close();
        android.content.ContentValues v=new android.content.ContentValues();v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,f.getName());v.put(android.provider.MediaStore.Downloads.MIME_TYPE,"application/pdf");v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/RentMor");android.net.Uri u=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u!=null)try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}
        toast("PDF zapisany w Pobrane/RentMor.");
    }catch(Exception e){toast("Nie udało się utworzyć PDF: "+e.getMessage());}}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    @Override public void onBackPressed(){if(showingApp)showHome();else super.onBackPressed();}
}