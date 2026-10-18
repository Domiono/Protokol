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
import java.util.concurrent.atomic.AtomicBoolean;
import android.util.Base64;

public class MainActivity extends Activity {
    private static final String HOME="https://rentmor.pl/protocol.php";
    private LocalStore store; private SyncManager sync; private LinearLayout root; private TextView status;
    private WebView activation; private boolean showingApp=false; private Bitmap issuePhoto; private SignatureView signatureView; private final AtomicBoolean syncRunning=new AtomicBoolean(false); private ConnectivityManager.NetworkCallback networkCallback;
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
    private void showHome(){showingApp=true;root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,18,24,18);root.setBackgroundColor(0xfff1f5f9);
        TextView title=new TextView(this);title.setText("RENTMOR • PROTOKOŁY");title.setTextSize(22);title.setTextColor(0xff123b68);title.setTypeface(null,1);root.addView(title);
        status=new TextView(this);status.setText("Dane lokalne: "+store.allProtocols().length()+" • Oczekujące: "+store.pendingCount());status.setPadding(0,12,0,12);root.addView(status);
        addButton("Synchronizuj teraz",()->syncNow());addButton("Nowy protokół wydania",()->newIssue());addButton("Lista protokołów",()->protocolList());
        addButton("Obsłuż zwrot sprzętu",()->returnList());
        addButton("Aktywuj / zmień urządzenie",()->resetActivation());
        setContentView(root); if(isOnline()) syncNow();
    }
    private void resetActivation(){\n        showingApp=false;\n        getSharedPreferences("rentmor",0).edit().remove("cookie").apply();\n        CookieManager cm=CookieManager.getInstance();\n        cm.setAcceptCookie(true);\n        cm.removeAllCookies(removed->{cm.flush();runOnUiThread(()->showActivation());});\n    }\n    private boolean isOnline(){android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.Network n=cm.getActiveNetwork();return n!=null&&cm.getNetworkCapabilities(n)!=null&&cm.getNetworkCapabilities(n).hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);}
    private void addButton(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    private EditText field(String hint){EditText e=new EditText(this);e.setSingleLine(false);e.setHint(hint);root.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private void registerNetworkSync(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);networkCallback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(android.net.Network network){runOnUiThread(()->{if(showingApp&&getSharedPreferences("rentmor",0).getString("cookie","").length()>0)syncNow();});}};try{cm.registerDefaultNetworkCallback(networkCallback);}catch(Exception ignored){}}
    @Override protected void onDestroy(){try{if(networkCallback!=null)((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}super.onDestroy();}
    private void syncNow(){if(!isOnline()){if(status!=null)status.setText("Brak internetu. Dane pozostają bezpiecznie w telefonie.");return;}if(!syncRunning.compareAndSet(false,true))return;if(status!=null)status.setText("Synchronizacja…");new Thread(()->{try{sync.sync();runOnUiThread(()->{if(status!=null)status.setText("Synchronizacja zakończona. Oczekujące: "+store.pendingCount());});}catch(Exception e){runOnUiThread(()->{if(status!=null)status.setText("Nie wysłano: "+e.getMessage()+" • Dane zapisane lokalnie.");});}finally{syncRunning.set(false);}}).start();}
    private void newIssue(){
        issuePhoto=null; signatureView=null;
        root.removeAllViews(); TextView h=new TextView(this);h.setText("NOWY PROTOKÓŁ WYDANIA");h.setTextSize(20);h.setTextColor(0xff123b68);root.addView(h);
        EditText client=field("Imię i nazwisko / firma klienta");EditText phone=field("Telefon");EditText equipment=field("Sprzęt");EditText date=field("Data wydania");EditText condition=field("Stan sprzętu");EditText accessories=field("Akcesoria");EditText notes=field("Uwagi");
        addButton("Dodaj zdjęcie sprzętu",()->pickPhoto());
        signatureView=new SignatureView(this); root.addView(signatureView,new LinearLayout.LayoutParams(-1,180));
        addButton("ZAPISZ PROTOKÓŁ W TELEFONIE",()->{if(client.getText().toString().trim().isEmpty()||equipment.getText().toString().trim().isEmpty()){toast("Wpisz klienta i sprzęt.");return;}
            try{String id=UUID.randomUUID().toString();JSONObject p=new JSONObject();p.put("id",id);p.put("number","RM-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date()));p.put("client",client.getText());p.put("phone",phone.getText());p.put("equipment",equipment.getText());p.put("rental_date",date.getText());p.put("condition",condition.getText());p.put("accessories",accessories.getText());p.put("notes",notes.getText());p.put("status","wydany");if(issuePhoto!=null)p.put("photo_base64",bitmapBase64(issuePhoto,1280,75));if(signatureView!=null)p.put("signature_base64",bitmapBase64(signatureView.image(),900,85));store.enqueue("create",p);makePdf(p);toast("Zapisano lokalnie. PDF znajduje się w Pobrane/RentMor.");showHome();}catch(Exception ex){toast("Błąd zapisu: "+ex.getMessage());}});
        addButton("WRÓĆ",()->showHome());
    }
    private void protocolList(){root.removeAllViews();TextView h=new TextView(this);h.setText("PROTOKOŁY W TELEFONIE");h.setTextSize(20);h.setTextColor(0xff123b68);root.addView(h);
        JSONArray a=store.allProtocols();for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null)continue;TextView t=new TextView(this);t.setText(p.optString("number")+" • "+p.optString("client")+"\n"+p.optString("equipment")+" • "+p.optString("status"));t.setPadding(8,16,8,16);root.addView(t);Button pdf=new Button(this);pdf.setText("Zapisz PDF");pdf.setOnClickListener(v->makePdf(p));root.addView(pdf);}
        addButton("WRÓĆ",()->showHome());
    }

    private void pickPhoto(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,4101);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==4101 && resultCode==RESULT_OK && data!=null) try {
            getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try(InputStream in=getContentResolver().openInputStream(data.getData())){issuePhoto=BitmapFactory.decodeStream(in);}
            toast("Zdjęcie dodane do protokołu.");
        } catch(Exception e){toast("Błąd zdjęcia: "+e.getMessage());}
    }
    private void returnList(){
        root.removeAllViews(); TextView h=new TextView(this); h.setText("PROTOKOŁY DO ZWROTU"); h.setTextSize(20); h.setTextColor(0xff123b68); root.addView(h);
        JSONArray a=store.allProtocols(); int count=0;
        for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i); if(p==null||!"wydany".equals(p.optString("status")))continue; count++;
            Button b=new Button(this); b.setAllCaps(false); b.setText(p.optString("number")+" • "+p.optString("client")+"\\n"+p.optString("equipment")); b.setOnClickListener(v->returnForm(p)); root.addView(b);
        }
        if(count==0){TextView t=new TextView(this);t.setText("Brak wydanych protokołów.");root.addView(t);}
        addButton("WRÓĆ",()->showHome());
    }
    private void returnForm(JSONObject original){
        signatureView=null;
        root.removeAllViews(); TextView h=new TextView(this);h.setText("ZWROT: "+original.optString("number"));h.setTextSize(20);h.setTextColor(0xff123b68);root.addView(h);
        EditText date=field("Data zwrotu");date.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date()));
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
    private void makePdf(JSONObject p){try{PdfDocument doc=new PdfDocument();PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,1).create();PdfDocument.Page page=doc.startPage(info);Canvas c=page.getCanvas();Paint paint=new Paint(3);paint.setColor(Color.BLACK);paint.setTextSize(20);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));c.drawText("RENTMOR – PROTOKÓŁ",40,55,paint);paint.setTextSize(13);paint.setTypeface(Typeface.DEFAULT);
        String[][] rows={{"Numer",p.optString("number")},{"Data wydania",p.optString("rental_date")},{"Klient",p.optString("client")},{"Telefon",p.optString("phone")},{"Sprzęt",p.optString("equipment")},{"Stan przy wydaniu",p.optString("condition")},{"Akcesoria",p.optString("accessories")},{"Uwagi",p.optString("notes")},{"Data zwrotu",p.optString("return_date")},{"Stan przy zwrocie",p.optString("return_condition")},{"Uwagi do zwrotu",p.optString("return_notes")}};
        int y=95;for(String[] row:rows){String value=row[1].replace("\\n"," ");if(!value.isEmpty()){c.drawText(row[0]+": "+value,40,y,paint);y+=25;}}Bitmap photo=bitmapFrom(p,"photo_base64");if(photo!=null&&y<625){c.drawText("Zdjęcie sprzętu:",40,y+12,paint);Rect dst=new Rect(40,y+20,555,Math.min(635,y+210));c.drawBitmap(photo,null,dst,paint);photo.recycle();y=dst.bottom+12;}Bitmap sign=bitmapFrom(p,(!p.optString("return_date","").isEmpty())?"return_signature_base64":"signature_base64");if(sign!=null){c.drawText("Podpis:",40,690,paint);c.drawBitmap(sign,null,new Rect(40,700,555,810),paint);sign.recycle();}else c.drawText("Podpis: ______________________________",40,730,paint);doc.finishPage(page);
        File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"");dir.mkdirs();File f=new File(dir,p.optString("number","protokol").replaceAll("[^A-Za-z0-9_-]","_")+".pdf");try(FileOutputStream out=new FileOutputStream(f)){doc.writeTo(out);}doc.close();
        android.content.ContentValues v=new android.content.ContentValues();v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,f.getName());v.put(android.provider.MediaStore.Downloads.MIME_TYPE,"application/pdf");v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/RentMor");android.net.Uri u=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u!=null)try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}
        toast("PDF zapisany w Pobrane/RentMor.");
    }catch(Exception e){toast("Nie udało się utworzyć PDF: "+e.getMessage());}}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    @Override public void onBackPressed(){if(showingApp)showHome();else super.onBackPressed();}
}