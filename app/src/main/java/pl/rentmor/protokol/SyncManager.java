package pl.rentmor.protokol;

import android.content.*;
import org.json.*;
import java.net.*;
import java.io.*;

public class SyncManager {
    private static final String API="https://rentmor.pl/protocol_sync.php";
    private final Context context; private final LocalStore store;
    public SyncManager(Context c, LocalStore s){context=c;store=s;}
    private String cookie(){return context.getSharedPreferences("rentmor",0).getString("cookie","");}
    private JSONObject request(String method, JSONObject body) throws Exception {
        return request(API,method,body);
    }
    // Package-private for local HTTP integration tests; never expose the session cookie.
    JSONObject request(String endpoint,String method,JSONObject body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(20000);
        c.setRequestProperty("Accept","application/json");
        String ck=cookie();if(ck.isEmpty())throw new IOException("Brak aktywacji urządzenia. Otwórz aktywację online.");
        try{
            c.setRequestProperty("Cookie",ck);
            if(body!=null){
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                c.setRequestProperty("X-RentMor-Sync","1");
                try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}
            }
            int code=c.getResponseCode();
            if(code>=300&&code<400)
                throw new IOException("Serwer przekierował synchronizację (HTTP "+code+"). Sprawdź połączenie z RentMor.pl i ponów synchronizację.");
            InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            if(in==null)throw new IOException("Serwer RentMor.pl nie zwrócił danych (HTTP "+code+").");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream stream=in){
                byte[] bytes=new byte[8192];int count;
                while((count=stream.read(bytes))!=-1)out.write(bytes,0,count);
            }
            String raw=out.toString("UTF-8");
            return decodeResponse(code,c.getContentType(),raw);
        }finally{c.disconnect();}
    }
    /** Readable, privacy-safe errors: never show a server HTML page or an activation cookie. */
    static JSONObject decodeResponse(int code,String contentType,String payload) throws IOException {
        if(code>=300&&code<400)
            throw new IOException("Serwer przekierował synchronizację (HTTP "+code+"). Sprawdź połączenie z RentMor.pl i spróbuj ponownie.");
        String raw=payload==null?"":payload.trim();
        if(raw.isEmpty())throw new IOException("Serwer RentMor.pl zwrócił pustą odpowiedź (HTTP "+code+").");
        JSONObject result;
        try{result=new JSONObject(raw);}
        catch(JSONException wrongFormat){
            boolean html=raw.startsWith("<")||(contentType!=null&&
                contentType.toLowerCase(java.util.Locale.ROOT).contains("text/html"));
            throw new IOException(html?
                "Serwer RentMor.pl zwrócił stronę HTML zamiast danych synchronizacji (HTTP "+code+"). Spróbuj ponownie; jeśli błąd wraca, sprawdź hosting.":
                "Serwer RentMor.pl zwrócił nieprawidłowe dane (HTTP "+code+"). Spróbuj ponownie.",wrongFormat);
        }
        if(code<200||code>=300)throw new IOException(result.optString("error","Błąd serwera "+code));
        if(!result.optBoolean("ok",false))throw new IOException(result.optString("error","Serwer nie potwierdził synchronizacji."));
        return result;
    }
    /** Only the logged-in administrator can view their own Firebase registration health. */
    public JSONObject fetchPushStatus() throws Exception {
        return request(API+"?view=push_status","GET",null);
    }
    public JSONObject updatePushToken(String token,boolean enabled) throws Exception {
        if(token==null)throw new IllegalArgumentException("Token is missing");
        JSONObject data=new JSONObject();data.put("token",token);data.put("enabled",enabled);
        return request(API+"?view=push_token","POST",data);
    }
    public JSONArray fetchMessages() throws Exception {
        JSONObject response=request(API+"?view=messages","GET",null);
        if(!"admin".equals(response.optString("role")))
            throw new SecurityException("Brak uprawnień do odczytu wiadomości.");
        JSONArray items=response.optJSONArray("messages");
        if(items==null)throw new IOException("Brak poprawnej listy wiadomości.");
        return items;
    }
    public synchronized JSONObject sync() throws Exception {
        JSONArray pending=store.queued();
        if(pending.length()>0){
            JSONObject body=new JSONObject(); JSONArray ops=new JSONArray();
            for(int i=0;i<pending.length();i++) ops.put(pending.getJSONObject(i));
            body.put("operations",ops);
            JSONObject response=request("POST",body);JSONArray results=response.optJSONArray("results");
            if(results==null)throw new IOException("Serwer nie potwierdził zapisania operacji. Ponów synchronizację.");
            for(int i=0;i<results.length();i++){
                JSONObject r=results.getJSONObject(i);
                if(r.optBoolean("ok")) store.clearQueueItem(r.optString("op_id"));
                else throw new IOException(r.optString("error","Nie udało się zsynchronizować operacji"));
            }
        }
        JSONObject server=request("GET",null);
        store.replaceServerProtocols(server.optJSONArray("protocols")==null?new JSONArray():server.optJSONArray("protocols"));
        store.replaceServerCustomers(server.optJSONArray("customers")==null?new JSONArray():server.optJSONArray("customers"));
        store.replaceServerReservations(server.optJSONArray("reservations")==null?new JSONArray():server.optJSONArray("reservations"));
        store.replaceServerExpenses(server.optJSONArray("expenses")==null?new JSONArray():server.optJSONArray("expenses"));
        if(server.has("assets"))store.replaceAssets(server.getJSONArray("assets"));
        ReservationsWidgetProvider.refresh(context);
        store.setEquipment(server.optJSONArray("equipment")==null?new JSONArray():server.optJSONArray("equipment"));
        context.getSharedPreferences("rentmor",0).edit().putString("site_products",server.optJSONArray("site_products")==null?"[]":server.optJSONArray("site_products").toString()).putString("site_stats",server.optJSONObject("site_stats")==null?"{}":server.optJSONObject("site_stats").toString()).apply();
        context.getSharedPreferences("rentmor",0).edit().putString("role",server.optString("role")).apply();
        if(!"admin".equals(server.optString("role")))store.clearMessages();
        PushRegistrar.refresh(context);
        return server;
    }
}