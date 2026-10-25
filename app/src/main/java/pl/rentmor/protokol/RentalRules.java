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
    /**
     * Choose an available physical copy. A legacy booking without a copy
     * makes the inventory ambiguous, so it must be assigned manually first.
     * With no registered copies the reservation stays explicitly unassigned.
     */
    public static String autoAssign(JSONObject candidate, JSONArray reservations, JSONArray assets) throws JSONException {
        String equipment=candidate.optString("equipment");
        java.util.ArrayList<JSONObject> copies=new java.util.ArrayList<>();
        for(int i=0;i<assets.length();i++){
            JSONObject a=assets.optJSONObject(i);
            if(a!=null&&equipment.equals(a.optString("equipment")))copies.add(a);
        }
        candidate.put("asset_id","");candidate.put("asset_label","");
        if(copies.isEmpty())return "";
        String start=candidate.optString("start_date"),end=candidate.optString("end_date");
        try{overlaps(start,end,start,end);}catch(Exception e){return "Sprawdź daty rezerwacji.";}
        for(int i=0;i<reservations.length();i++){
            JSONObject r=reservations.optJSONObject(i);
            if(r==null||candidate.optString("id").equals(r.optString("id"))||r.optBoolean("settled")||r.optBoolean("returned")
                ||!equipment.equals(r.optString("equipment"))||!r.optString("asset_id").isEmpty())continue;
            try{
                if((r.optBoolean("done")&&!r.optString("end_date").isEmpty()&&r.optString("end_date").compareTo(LocalDate.now().toString())<0)
                    ||overlaps(start,end,r.optString("start_date"),r.optString("end_date")))
                    return "Najpierw przypisz egzemplarz do wcześniejszej rezerwacji tego sprzętu.";
            }catch(Exception e){return "Sprawdź daty wcześniejszej rezerwacji bez przypisanego egzemplarza.";}
        }
        copies.sort((a,b)->a.optString("label").compareToIgnoreCase(b.optString("label")));
        for(JSONObject a:copies){
            candidate.put("asset_id",a.optString("id"));
            candidate.put("asset_label",a.optString("label"));
            if(conflict(candidate,reservations,assets).isEmpty())return "";
        }
        candidate.put("asset_id","");candidate.put("asset_label","");
        return "Brak wolnego egzemplarza w wybranym terminie. Zmień daty.";
    }
    public static String conflict(JSONObject candidate,JSONArray reservations,JSONArray assets) {
        String asset=candidate.optString("asset_id");
        if(asset.isEmpty()||candidate.optBoolean("settled")||candidate.optBoolean("returned"))return "";
        JSONObject found=null;
        for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a!=null&&asset.equals(a.optString("id")))found=a;}
        if(found==null)return "Nie znaleziono egzemplarza.";
        if(!candidate.optString("equipment").equals(found.optString("equipment")))return "Egzemplarz należy do innego modelu.";
        if(found.optBoolean("in_service"))return "Egzemplarz jest w serwisie.";
        try {
            for(int i=0;i<reservations.length();i++){
                JSONObject r=reservations.optJSONObject(i);
                if(r==null||r.optString("id").equals(candidate.optString("id"))||r.optBoolean("settled")||r.optBoolean("returned"))continue;
                // Calendar separately flags legacy reservations without an assigned copy.
                boolean same=asset.equals(r.optString("asset_id"));
                if(same&&overlaps(candidate.optString("start_date"),candidate.optString("end_date"),r.optString("start_date"),r.optString("end_date")))
                    return r.optString("asset_id").isEmpty()?"Rezerwacja tego modelu nie ma przypisanego egzemplarza. Najpierw ją uzupełnij.":"Ten egzemplarz ma już rezerwację w tym terminie.";
                if(same&&r.optBoolean("done")&&r.optString("end_date").compareTo(LocalDate.now().toString())<0)return "Egzemplarz jest po terminie zwrotu.";
            }
        }catch(Exception e){return "Sprawdź daty rezerwacji tego modelu.";}
        return "";
    }
}
