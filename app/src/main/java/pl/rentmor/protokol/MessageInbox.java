package pl.rentmor.protokol;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.*;
import android.widget.*;
import org.json.*;

import java.util.Locale;

/** Local, offline-readable copy of the administrator's website messages. */
public final class MessageInbox {
    private final Activity activity;
    private final LocalStore store;
    private final LinearLayout root, list;
    private TextView counter,status,pushStatus;
    private int visibleCount=30;
    private static final int NAVY=0xff172b43;
    private static final int MUTED=0xff64748b;
    public MessageInbox(Activity activity,LocalStore store){
        this.activity=activity;this.store=store;
        root=column();root.setPadding(0,0,0,20);
        LinearLayout heading=card(root,0xff172b43);
        text(heading,"WIADOMOŚCI ZE STRONY",12,0xffffcc18,true);
        text(heading,"Zapytania klientów",21,Color.WHITE,true);
        text(heading,"Wiadomości z formularza kontaktowego RentMor.pl",12,0xffd5deea,false);
        LinearLayout bar=card(root,Color.WHITE);
        counter=text(bar,"",14,NAVY,true);
        status=text(bar,"Naciśnij Odśwież, aby pobrać najnowsze zapytania.",12,MUTED,false);
        Button refresh=button(bar,"⟳  Odśwież wiadomości",0xffffcc18);
        refresh.setOnClickListener(v->refresh());
        if(Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            Button settings=button(bar,"Włącz powiadomienia w ustawieniach telefonu",0xffedf2f7);
            settings.setOnClickListener(v->{
                Intent i=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:"+activity.getPackageName()));activity.startActivity(i);
            });
        }
        LinearLayout push=card(root,Color.WHITE);
        text(push,"POWIADOMIENIA PUSH",12,NAVY,true);
        pushStatus=text(push,"Sprawdzanie Firebase…",12,MUTED,false);
        Button checkPush=button(push,"Sprawdź i ponów połączenie Firebase",0xffedf2f7);
        checkPush.setOnClickListener(v->checkPushConnection(true));
        list=column();root.addView(list);
        render();
        refresh();
        checkPushConnection(false);
    }
    private void checkPushConnection(boolean retry){
        android.app.NotificationManager nm=(android.app.NotificationManager)activity.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null&&!nm.areNotificationsEnabled()){
            pushStatus.setText("Powiadomienia Androida są wyłączone. Włącz je w ustawieniach aplikacji.");
            return;
        }
        if(!PushRegistrar.configured(activity)){
            pushStatus.setText("Brak aktywnej konfiguracji Firebase w APK. Zainstaluj najnowszą wersję RentMor.");
            return;
        }
        if(retry)PushRegistrar.retry(activity);
        pushStatus.setText("Sprawdzanie połączenia telefonu z serwerem Firebase…");
        new Thread(()->{
            try {
                // Token registration happens on a separate short-lived thread.
                if(retry)Thread.sleep(1700);
                JSONObject remote=new SyncManager(activity,new LocalStore(activity)).fetchPushStatus();
                String message;
                if(!remote.optBoolean("server_configured"))
                    message="Serwer nie ma jeszcze klucza Firebase.";
                else if(!remote.optBoolean("storage_writable"))
                    message="Serwer nie może zapisać tokenu. Wymaga poprawy uprawnień hostingu.";
                else if(remote.optBoolean("registered"))
                    message="Połączono! Ten telefon jest zarejestrowany do powiadomień push.";
                else
                    message="Telefon nie jest jeszcze zarejestrowany. "+PushRegistrar.status(activity)
                        +" Naciśnij przycisk ponownie.";
                activity.runOnUiThread(()->{if(!activity.isFinishing())pushStatus.setText(message);});
            }catch(Exception ex){
                activity.runOnUiThread(()->{
                    if(!activity.isFinishing())pushStatus.setText(
                        "Nie udało się sprawdzić serwera: "+PushRegistrar.status(activity));
                });
            }
        },"rentmor-push-diagnostic").start();
    }
    public View view(){return root;}
    private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(1);return v;}
    private LinearLayout card(LinearLayout parent,int color){
        LinearLayout c=column();c.setPadding(dp(15),dp(13),dp(15),dp(13));
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(16));c.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);parent.addView(c,lp);return c;
    }
    private TextView text(LinearLayout parent,String str,int size,int color,boolean bold){
        TextView t=new TextView(activity);t.setText(str);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,1);t.setPadding(0,dp(3),0,dp(4));parent.addView(t);return t;
    }
    private Button button(LinearLayout parent,String title,int bg){
        Button b=new Button(activity);b.setText(title);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(NAVY);GradientDrawable shape=new GradientDrawable();shape.setColor(bg);shape.setCornerRadius(dp(11));b.setBackground(shape);
        b.setMinHeight(dp(46));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(7);parent.addView(b,p);return b;
    }
    public void render(){
        JSONArray messages=store.allMessages();
        int unread=store.unreadMessagesCount();
        counter.setText("Nieprzeczytane: "+unread+"    •    Wszystkie: "+messages.length());
        list.removeAllViews();
        if(messages.length()==0){
            LinearLayout empty=card(list,Color.WHITE);
            text(empty,"Brak wiadomości w skrzynce.",15,NAVY,true);
            text(empty,"Po synchronizacji pojawią się tu wiadomości wysłane przez formularz na stronie.",12,MUTED,false);
        }
        for(int i=0;i<Math.min(messages.length(),visibleCount);i++){
            JSONObject message=messages.optJSONObject(i);
            if(message!=null)addMessage(message);
        }
        if(messages.length()>visibleCount){
            Button more=button(list,"Pokaż starsze wiadomości",0xffe9eef5);
            more.setOnClickListener(v->{visibleCount+=30;render();});
        }
    }
    private void addMessage(JSONObject message){
        String id=message.optString("id","");
        boolean unread=!message.optBoolean("is_read");
        LinearLayout c=card(list,Color.WHITE);
        LinearLayout headline=new LinearLayout(activity);headline.setOrientation(LinearLayout.HORIZONTAL);
        headline.setGravity(Gravity.CENTER_VERTICAL);c.addView(headline);
        TextView name=text(headline,(unread?"●  ":"")+message.optString("client","Klient"),16,NAVY,true);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        TextView date=text(headline,message.optString("date"),11,MUTED,false);
        date.setGravity(Gravity.END);
        TextView readStatus=text(c,unread?"● Nieprzeczytane":"✓ Przeczytane",
            12,unread?0xffb77500:0xff288255,true);
        String product=message.optString("product","").trim();
        if(!product.isEmpty())text(c,"Dotyczy: "+product,12,0xffa47300,true);
        String body=message.optString("message","").trim();
        String preview=body.length()>115?body.substring(0,115)+"…":body;
        TextView snippet=text(c,preview.isEmpty()?"(Brak treści)":preview,13,MUTED,false);
        LinearLayout details=column();details.setVisibility(View.GONE);c.addView(details);
        text(details,"TREŚĆ WIADOMOŚCI",11,MUTED,true);
        TextView full=text(details,body,15,NAVY,false);
        full.setTextIsSelectable(true);
        if(!message.optString("booking_request_id").isEmpty()){
            text(details,"REZERWACJA: "+message.optString("booking_status"),13,NAVY,true);
            if("pending".equals(message.optString("booking_status"))){
                Button approve=button(details,"Zatwierdź rezerwację",0xffffcc18);
                approve.setOnClickListener(v->decideBooking(message,"accept"));
                Button decline=button(details,"Odrzuć zgłoszenie",0xffe9eef5);
                decline.setOnClickListener(v->decideBooking(message,"decline"));
            }else if("accepted".equals(message.optString("booking_status"))){
                Button confirmedSms=button(details,"Otwórz SMS z potwierdzeniem",0xffffcc18);
                confirmedSms.setOnClickListener(v->openBookingSms(message.optString("contact"),message.optString("sms_body")));
            }
        }
        String contact=message.optString("contact","").trim();
        if(!contact.isEmpty())text(details,"Kontakt: "+contact,13,NAVY,true);
        if(contact.matches("[+0-9() .\\-]{7,28}") && contact.replaceAll("[^0-9]","").length()>=7){
            LinearLayout actions=new LinearLayout(activity);actions.setOrientation(LinearLayout.HORIZONTAL);details.addView(actions);
            Button call=button(actions,"Zadzwoń",0xffffcc18);
            Button sms=button(actions,"SMS",0xffe9eef5);
            call.setLayoutParams(new LinearLayout.LayoutParams(0,dp(46),1));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.leftMargin=dp(8);sms.setLayoutParams(p);
            call.setOnClickListener(v->activity.startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(contact)))));
            sms.setOnClickListener(v->activity.startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(contact)))));
        } else if(contact.contains("@")){
            Button email=button(details,"Napisz e-mail",0xffffcc18);
            email.setOnClickListener(v->activity.startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+Uri.encode(contact)))));
        }
        TextView disclosure=text(c,unread?"Dotknij, aby przeczytać  ▾":"Dotknij, aby otworzyć  ▾",
            12,0xff64748b,true);
        View.OnClickListener toggle=v->{
            boolean opening=details.getVisibility()!=View.VISIBLE;
            if(Build.VERSION.SDK_INT>=19&&android.animation.ValueAnimator.areAnimatorsEnabled()){
                AutoTransition trans=new AutoTransition();trans.setDuration(190);TransitionManager.beginDelayedTransition(c,trans);
            }
            details.setVisibility(opening?View.VISIBLE:View.GONE);
            disclosure.setText(opening?"Zwiń wiadomość  ▴":"Dotknij, aby przeczytać  ▾");
            if(opening&&!id.isEmpty()){
                store.markMessageRead(id);
                MessageMonitor.messageRead(activity.getApplicationContext(),id);
                name.setText(message.optString("client","Klient"));
                readStatus.setText("✓ Przeczytane");
                readStatus.setTextColor(0xff288255);
                counter.setText("Nieprzeczytane: "+store.unreadMessagesCount()+"    •    Wszystkie: "+store.allMessages().length());
            }
        };
        disclosure.setOnClickListener(toggle);headline.setOnClickListener(toggle);snippet.setOnClickListener(toggle);
    }
    private void openBookingSms(String phone,String body){
        try{
            Intent sms=new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",phone.replaceAll("[^+0-9]",""),null));
            sms.putExtra("sms_body",body);activity.startActivity(sms);
        }catch(Exception e){new android.app.AlertDialog.Builder(activity).setTitle("Nie można otworzyć SMS-a").setMessage(body).setPositiveButton("OK",null).show();}
    }
    private boolean decidingBooking=false;
    private void decideBooking(JSONObject message,String action){
        if(decidingBooking)return;
        final EditText price=new EditText(activity);price.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);price.setText(message.optString("booking_estimate"));
        android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(activity).setTitle("accept".equals(action)?"Potwierdź termin i cenę":"Odrzucić zgłoszenie?")
            .setMessage(message.optString("message")+"\n\nTermin zostanie ponownie sprawdzony na serwerze. SMS nie zostanie wysłany automatycznie.").setNegativeButton("Anuluj",null);
        if("accept".equals(action))dialog.setView(price);
        dialog.setPositiveButton("accept".equals(action)?"Zatwierdzam":"Odrzuć",(d,w)->{
            if(decidingBooking)return;decidingBooking=true;status.setText("Zapisywanie decyzji…");
            final String enteredTotal=price.getText().toString();
            new Thread(()->{
                JSONObject result=null;String error=null;
                try{result=new SyncManager(activity,store).decideMortii(message.optString("booking_request_id"),action,enteredTotal,message.optString("catalogue_revision"));}
                catch(Exception ex){error=ex.getMessage();}
                final JSONObject response=result;final String failure=error;
                activity.runOnUiThread(()->{
                    decidingBooking=false;if(activity.isFinishing()||activity.isDestroyed())return;
                    if(failure!=null){status.setText("Nie potwierdzono decyzji. Odśwież zgłoszenia przed ponowieniem. "+failure);refresh();return;}
                    refresh();
                    if(response!=null&&"accepted".equals(response.optString("status")))new android.app.AlertDialog.Builder(activity).setTitle("Rezerwacja zapisana")
                        .setMessage("Otworzyć SMS z potwierdzeniem? Sprawdź treść i wyślij go z telefonu.").setNegativeButton("Później",null)
                        .setPositiveButton("Otwórz SMS",(sd,sw)->openBookingSms(response.optString("phone"),response.optString("sms_body"))).show();
                });
            },"rentmor-booking-decision").start();
        }).show();
    }
    public void refresh(){
        status.setText("Pobieranie wiadomości…");
        new Thread(()->{
            String error=null;
            try{MessageMonitor.poll(activity.getApplicationContext(),false);}
            catch(Exception ex){error="Nie udało się odświeżyć: "+ex.getMessage();}
            final String result=error;
            activity.runOnUiThread(()->{
                if(activity.isFinishing()||activity.isDestroyed())return;
                status.setText(result==null?"Ostatnie odświeżenie zakończone.":"Brak aktualizacji. "+result);
                render();
            });
        },"rentmor-inbox-refresh").start();
    }
}

