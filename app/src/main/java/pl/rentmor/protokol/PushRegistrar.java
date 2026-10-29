package pl.rentmor.protokol;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

/** Registers the Firebase device token against the authenticated RentMor device role. */
public final class PushRegistrar {
    private PushRegistrar(){}
    private static final String TAG="RentMorPush";
    private static final String LAST="registered_fcm_token";
    private static final String ROLE="registered_fcm_role";
    private static final String TIME="registered_fcm_time";
    private static final String STATUS="fcm_registration_status";
    /** Polling still refreshes inbox data; Firebase owns alerts for registered devices. */
    public static boolean hasRegisteredToken(Context context) {
        SharedPreferences p=context.getSharedPreferences("rentmor",0);
        return "admin".equals(p.getString("role",""))
            && "admin".equals(p.getString(ROLE,""))
            && !p.getString(LAST,"").isEmpty()
            && System.currentTimeMillis()-p.getLong(TIME,0)<90L*24L*60L*60L*1000L;
    }
    public static String status(Context context) {
        if(!configured(context))return "Firebase nie jest skonfigurowany w tej wersji aplikacji.";
        return context.getSharedPreferences("rentmor",0)
            .getString(STATUS,"Trwa sprawdzanie rejestracji Firebase.");
    }
    public static void retry(Context context) {
        context.getSharedPreferences("rentmor",0).edit().remove(TIME).apply();
        refresh(context);
    }
    private static final long REFRESH_MS=24L*60L*60L*1000L;

    public static boolean configured(Context context) {
        try{return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();}
        catch(Exception ignored){return false;}
    }
    /** Call after a successful server sync (never rely on client role alone for security). */
    public static void refresh(Context input) {
        Context context=input.getApplicationContext();
        if(!configured(context)){
            context.getSharedPreferences("rentmor",0).edit().putString(STATUS,"Brak konfiguracji Firebase w APK.").apply();
            return;
        }
        SharedPreferences p=context.getSharedPreferences("rentmor",0);
        String cookie=SecurePrefs.get(context,"cookie","");
        if(cookie.isEmpty())return;
        String role=p.getString("role","");
        if(!"admin".equals(role)) {
            String previous=p.getString(LAST,"");
            if(!previous.isEmpty())send(context,previous,false);
            return;
        }
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->{
                if(token!=null&&!token.isEmpty())send(context,token,true);
                else p.edit().putString(STATUS,"Firebase nie zwrócił identyfikatora telefonu.").apply();
            }).addOnFailureListener(e->{
                p.edit().putString(STATUS,"Nie udało się uzyskać identyfikatora Firebase.").apply();
                Log.w(TAG,"Firebase token not available; polling fallback active.");
            });
        }catch(Exception ex){
            p.edit().putString(STATUS,"Firebase nie jest gotowy w telefonie.").apply();
            Log.w(TAG,"Firebase not ready; polling fallback active.");
        }
    }
    public static void refreshedToken(Context context,String token){
        if(token!=null&&!token.isEmpty())send(context.getApplicationContext(),token,true);
    }
    private static void send(Context context,String token,boolean enable){
        SharedPreferences p=context.getSharedPreferences("rentmor",0);
        String cookie=SecurePrefs.get(context,"cookie","");
        if(cookie.isEmpty())return;
        String role=p.getString("role","");
        boolean admin="admin".equals(role);
        String old=p.getString(LAST,"");
        if(enable && !admin)enable=false;
        if(enable && admin && token.equals(old) && "admin".equals(p.getString(ROLE,""))
            && System.currentTimeMillis()-p.getLong(TIME,0)<REFRESH_MS){
            p.edit().putString(STATUS,"Telefon został zarejestrowany w Firebase.").apply();
            return;
        }
        final boolean active=enable;
        p.edit().putString(STATUS,"Łączenie telefonu z Firebase…").apply();
        new Thread(()->{
            try {
                SyncManager sync=new SyncManager(context,new LocalStore(context));
                JSONObjectResult result=new JSONObjectResult(sync.updatePushToken(token,active));
                // Server is authoritative about which devices are admins.
                if(result.ok){
                    if(active && result.enabled) p.edit().putString(LAST,token).putString(ROLE,"admin")
                        .putLong(TIME,System.currentTimeMillis())
                        .putString(STATUS,"Telefon został zarejestrowany w Firebase.").apply();
                    else p.edit().remove(LAST).remove(ROLE).remove(TIME)
                        .putString(STATUS,"Rejestracja push nie jest aktywna dla tego urządzenia.").apply();
                }else p.edit().putString(STATUS,"Serwer nie zaakceptował rejestracji Firebase.").apply();
            }catch(Exception ex){
                p.edit().putString(STATUS,"Błąd rejestracji w serwerze. Sprawdź połączenie lub uprawnienia.").apply();
                Log.w(TAG,"Could not register push token; polling fallback active.");
            }
        },"rentmor-fcm-register").start();
    }
    private static final class JSONObjectResult {
        final boolean ok,enabled;
        JSONObjectResult(org.json.JSONObject o){ok=o.optBoolean("ok");enabled=o.optBoolean("push_enabled");}
    }
}
