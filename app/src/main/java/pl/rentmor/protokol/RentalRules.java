package pl.rentmor.protokol;

import org.json.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Date-only reservations occupy both endpoint days; no implicit hourly billing. */
public final class RentalRules {
    public static boolean overlaps(String a,String b,String c,String d) {
        LocalDate from=LocalDate.parse(a),to=LocalDate.parse(b),other=LocalDate.parse(c),end=LocalDate.parse(d);
        if(to.isBefore(from)||end.isBefore(other))throw new IllegalArgumentException("Nieprawidłowy termin");
        return !to.isBefore(other)&&!end.isBefore(from);
    }
    public static long days(String a,String b) {
        long n=ChronoUnit.DAYS.between(LocalDate.parse(a),LocalDate.parse(b));
        if(n<0)throw new IllegalArgumentException("Zwrot przed wydaniem");
        return Math.max(1,n);
    }
    public static BigDecimal money(String value) {
        BigDecimal n=new BigDecimal(value.trim().isEmpty()?"0":value.trim().replace(',','.'));
        if(n.signum()<0||n.compareTo(new BigDecimal("10000000"))>0)throw new IllegalArgumentException("Nieprawidłowa kwota");
        return n.setScale(2,RoundingMode.HALF_UP);
    }

    /** Two shared inventory groups. A labelled asset can be offered under several product names. */
    public static String kind(String name){
        String n=java.text.Normalizer.normalize(name==null?"":name,java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","").replace('ł','l').replace('Ł','l')
            .toLowerCase(java.util.Locale.ROOT);
        if(n.contains("zyrafa"))return "giraffe";
        if(n.contains("odkurzacz")&&!n.contains("puzzi")&&!n.contains("piorac")&&!n.contains("ekstrakcyj"))return "vacuum";
        return "standard";
    }
    public static String assetKind(JSONObject a){
        String type=a.optString("asset_kind");
        return ("standard".equals(type)||"giraffe".equals(type)||"vacuum".equals(type))?type:kind(a.optString("equipment"));
    }
    public static boolean needsVacuum(JSONObject r){
        if(!"giraffe".equals(kind(r.optString("equipment"))))return false;
        if(r.has("needs_vacuum"))return r.optBoolean("needs_vacuum");
        return kind(r.optString("equipment")).equals("giraffe")&&
            java.text.Normalizer.normalize(r.optString("equipment"),java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT).contains("odkurzacz");
    }
    public static boolean covers(JSONObject r,String role){
        if("vacuum".equals(role))return "vacuum".equals(kind(r.optString("equipment")))||needsVacuum(r);
        if("giraffe".equals(role))return "giraffe".equals(kind(r.optString("equipment")));
        return false;
    }
    private static boolean matches(JSONObject candidate,JSONObject asset,String role){
        if(!"standard".equals(role))return role.equals(assetKind(asset));
        return candidate.optString("equipment").equals(asset.optString("equipment"));
    }
    private static boolean reserved(JSONObject r,String copyId){
        return copyId.equals(r.optString("asset_id"))||copyId.equals(r.optString("vacuum_asset_id"));
    }
    private static boolean active(JSONObject r){return !r.optBoolean("settled")&&!r.optBoolean("returned");}
    private static boolean busy(JSONObject candidate,JSONObject r) {
        if(r.optBoolean("done")&&!r.optString("end_date").isEmpty()
            &&r.optString("end_date").compareTo(LocalDate.now().toString())<0)return true;
        return overlaps(candidate.optString("start_date"),candidate.optString("end_date"),
            r.optString("start_date"),r.optString("end_date"));
    }
    private static String checkCopy(JSONObject candidate,JSONObject copy,String role,JSONArray reservations){
        if(copy==null)return "Nie znaleziono egzemplarza.";
        if(!matches(candidate,copy,role))return "Wybrany egzemplarz należy do innej grupy sprzętu.";
        if(copy.optBoolean("in_service"))return "Egzemplarz jest w serwisie.";
        for(int i=0;i<reservations.length();i++){
            JSONObject r=reservations.optJSONObject(i);
            if(r==null||candidate.optString("id").equals(r.optString("id"))||!active(r))continue;
            String rid=copy.optString("id");
            // Old bundles missing their vacuum are treated as unresolved inventory, not as a free copy.
            boolean ambiguous=!"standard".equals(role)&&covers(r,role)&&
                ("vacuum".equals(role)?r.optString("vacuum_asset_id").isEmpty()&&
                    (!"vacuum".equals(kind(r.optString("equipment")))||r.optString("asset_id").isEmpty())
                  :r.optString("asset_id").isEmpty());
            try {
                if((reserved(r,rid)||ambiguous)&&busy(candidate,r))
                    return ambiguous?"Wcześniejsza rezerwacja tej grupy nie ma przypisanego egzemplarza. Uzupełnij ją najpierw.":
                        "Egzemplarz jest już zajęty w tym terminie lub po terminie zwrotu.";
            }catch(Exception ex){if(reserved(r,rid)||ambiguous)return "Sprawdź daty starszej rezerwacji sprzętu.";}
        }
        return "";
    }
    private static java.util.ArrayList<JSONObject> copies(JSONObject candidate,JSONArray assets,String role){
        java.util.ArrayList<JSONObject> list=new java.util.ArrayList<>();
        for(int i=0;i<assets.length();i++){
            JSONObject a=assets.optJSONObject(i);
            if(a!=null&&matches(candidate,a,role))list.add(a);
        }
        list.sort((a,b)->a.optString("label").compareToIgnoreCase(b.optString("label")));
        return list;
    }
    private static JSONObject byId(JSONArray assets,String id){
        for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a!=null&&id.equals(a.optString("id")))return a;}
        return null;
    }
    /**
     * Allocate both physical devices of a set or one item for a standalone booking.
     * The server repeats this decision under the reservations lock.
     */
    public static String autoAssign(JSONObject candidate,JSONArray reservations,JSONArray assets)throws JSONException{
        String role=kind(candidate.optString("equipment"));
        boolean needs=needsVacuum(candidate);
        candidate.put("asset_id","");candidate.put("asset_label","");
        candidate.put("vacuum_asset_id","");candidate.put("vacuum_asset_label","");
        try{overlaps(candidate.optString("start_date"),candidate.optString("end_date"),candidate.optString("start_date"),candidate.optString("end_date"));}
        catch(Exception e){return "Sprawdź daty rezerwacji.";}
        java.util.ArrayList<JSONObject> mains=copies(candidate,assets,role);
        if(mains.isEmpty()){
            return "standard".equals(role)&&!needs?"":"Dodaj egzemplarze żyraf i odkurzaczy w Dostępność przed rezerwacją.";
        }
        JSONObject first=null;
        for(JSONObject a:mains)if(checkCopy(candidate,a,role,reservations).isEmpty()){first=a;break;}
        if(first==null)return "Brak wolnego egzemplarza "+("vacuum".equals(role)?"odkurzacza":"sprzętu")+" w wybranym terminie.";
        if(needs){
            JSONObject second=null;
            for(JSONObject a:copies(candidate,assets,"vacuum"))
                if(!a.optString("id").equals(first.optString("id"))&&checkCopy(candidate,a,"vacuum",reservations).isEmpty()){second=a;break;}
            if(second==null)return "Brak wolnego odkurzacza przemysłowego do zestawu. Sprawdź rezerwacje i przypisania.";
            candidate.put("vacuum_asset_id",second.optString("id"));
            candidate.put("vacuum_asset_label",second.optString("label"));
        }
        candidate.put("asset_id",first.optString("id"));candidate.put("asset_label",first.optString("label"));
        return "";
    }
    public static String conflict(JSONObject candidate,JSONArray reservations,JSONArray assets){
        if(candidate.optBoolean("settled")||candidate.optBoolean("returned"))return "";
        String id=candidate.optString("asset_id");
        String role=kind(candidate.optString("equipment"));
        boolean needs=needsVacuum(candidate);
        if(id.isEmpty())return needs?"Przypisz żyrafę oraz odkurzacz do zestawu.":"";
        try{overlaps(candidate.optString("start_date"),candidate.optString("end_date"),candidate.optString("start_date"),candidate.optString("end_date"));}
        catch(Exception e){return "Sprawdź daty rezerwacji.";}
        String main=checkCopy(candidate,byId(assets,id),role,reservations);
        if(!main.isEmpty())return main;
        String secondary=candidate.optString("vacuum_asset_id");
        if(!needs){
            if(!secondary.isEmpty())return "Rezerwacja bez zestawu nie może blokować dodatkowego odkurzacza.";
            return "";
        }
        if(secondary.isEmpty())return "Przypisz odkurzacz przemysłowy do zestawu.";
        if(secondary.equals(id))return "Zestaw wymaga dwóch różnych urządzeń.";
        return checkCopy(candidate,byId(assets,secondary),"vacuum",reservations);
    }
}
