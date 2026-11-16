package pl.rentmor.protokol;

import org.json.*;
import java.math.BigDecimal;

/** Backward-compatible primary item plus up to 19 additional inventory lines. */
public final class RentalItems {
    private RentalItems(){}
    public static void requireServerSupport(android.content.Context c){
        if(!c.getSharedPreferences("rentmor",0).getBoolean("multi_equipment_reservations",false))
            throw new IllegalArgumentException("Najpierw odśwież synchronizację, aby potwierdzić obsługę wielu sprzętów na serwerze.");
    }
    static final String[] COPY={"asset_id","asset_label","vacuum_asset_id","vacuum_asset_label"};
    public static boolean hasExtras(JSONObject r){if(r==null)return false;JSONArray a=r.optJSONArray("additional_items");return a!=null&&a.length()>0;}
    public static JSONArray lines(JSONObject r)throws JSONException{
        JSONArray out=new JSONArray();JSONObject primary=new JSONObject(r.toString());primary.remove("additional_items");
        primary.put("_booking_id",r.optString("_booking_id",r.optString("id")));out.put(primary);
        JSONArray extras=r.optJSONArray("additional_items");
        if(extras!=null)for(int i=0;i<extras.length();i++){
            JSONObject raw=extras.getJSONObject(i),line=new JSONObject(primary.toString());
            for(String k:new String[]{"equipment","asset_id","asset_label","vacuum_asset_id","vacuum_asset_label","daily_rate"})line.put(k,raw.optString(k));
            line.put("needs_vacuum",raw.optBoolean("needs_vacuum"));line.put("auto_select",raw.optBoolean("auto_select"));
            line.put("id",r.optString("id")+"::"+raw.getString("line_id"));out.put(line);
        }
        return out;
    }
    public static JSONArray flatten(JSONArray rows){
        JSONArray out=new JSONArray();try{for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;JSONArray lines=lines(r);for(int j=0;j<lines.length();j++)out.put(lines.getJSONObject(j));}}
        catch(JSONException e){throw new IllegalArgumentException("Nieprawidłowe pozycje rezerwacji",e);}return out;
    }
    public static boolean sameBooking(JSONObject a,JSONObject b){return a.optString("_booking_id",a.optString("id")).equals(b.optString("_booking_id",b.optString("id")));}
    public static JSONArray without(JSONArray rows,String id){JSONArray out=new JSONArray();JSONArray flat=flatten(rows);for(int i=0;i<flat.length();i++){JSONObject r=flat.optJSONObject(i);if(!id.equals(r.optString("_booking_id",r.optString("id"))))out.put(r);}return out;}
    static void assign(JSONObject parent,int index,JSONObject assigned)throws JSONException{
        JSONObject target=index==0?parent:parent.getJSONArray("additional_items").getJSONObject(index-1);
        for(String k:COPY)target.put(k,assigned.optString(k));
    }
    static void copyAssignments(JSONObject from,JSONObject to)throws JSONException{
        for(String k:COPY)to.put(k,from.optString(k));to.put("additional_items",from.getJSONArray("additional_items"));
    }
    /** Preserve explicit selections while assigning each new extra under sibling occupancy. */
    public static String assignMissing(JSONObject candidate,JSONArray reservations,JSONArray assets)throws JSONException{
        if(!hasExtras(candidate))return "";
        JSONObject working=new JSONObject(candidate.toString());JSONArray lines=lines(working),busy=without(reservations,candidate.optString("id"));
        // Reserve explicit selections first so a new automatic line cannot steal a later selected copy.
        for(int i=0;i<lines.length();i++){JSONObject l=lines.getJSONObject(i);if(!l.optString("asset_id").isEmpty()){
            JSONObject held=new JSONObject(l.toString());held.put("_booking_id","held::"+i);busy.put(held);
        }}
        for(int i=0;i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);
            if(line.optString("asset_id").isEmpty()){
                String why=RentalRules.autoAssign(line,busy,assets);if(!why.isEmpty())return why;
                if(line.optString("asset_id").isEmpty())return "Dodaj fizyczny egzemplarz: "+line.optString("equipment");
                assign(working,i,line);JSONObject held=new JSONObject(line.toString());held.put("_booking_id","assigned::"+i);busy.put(held);
            }
        }
        String conflict=RentalRules.conflict(working,reservations,assets);if(!conflict.isEmpty())return conflict;
        copyAssignments(working,candidate);return "";
    }
    public static void updateRate(JSONObject r,String primary)throws JSONException{
        BigDecimal total=RentalRules.money(primary);r.put("primary_daily_rate",total.toPlainString());
        JSONArray extras=r.optJSONArray("additional_items");
        if(extras!=null)for(int i=0;i<extras.length();i++)total=total.add(RentalRules.money(extras.getJSONObject(i).optString("daily_rate","0")));
        r.put("daily_rate",RentalRules.money(total.toPlainString()).toPlainString());
    }
    public static String summary(JSONObject r,boolean copies){
        StringBuilder text=new StringBuilder();try{JSONArray lines=lines(r);for(int i=0;i<lines.length();i++){
            JSONObject l=lines.getJSONObject(i);if(i>0)text.append("\n＋ ");text.append(l.optString("equipment"));
            if(copies)text.append(" • ").append(l.optString("asset_label").isEmpty()?"do przypisania":l.optString("asset_label"));
            if(RentalRules.needsVacuum(l))text.append(" + ").append(l.optString("vacuum_asset_label","odkurzacz"));
        }}catch(JSONException e){return r.optString("equipment");}return text.toString();
    }
    public static boolean uses(JSONObject r,String id){JSONArray lines=flatten(new JSONArray().put(r));for(int i=0;i<lines.length();i++){JSONObject l=lines.optJSONObject(i);if(id.equals(l.optString("asset_id"))||id.equals(l.optString("vacuum_asset_id")))return true;}return false;}
}
