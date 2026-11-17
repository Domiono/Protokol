package pl.rentmor.protokol;

import android.content.Context;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** Aggregated page counts only; no visitor identifiers or browsing histories. */
final class SitePageStats {
    static final TimeZone ZONE = TimeZone.getTimeZone("Europe/Warsaw");
    static SortedMap<String,Long> counts(JSONObject stats, int days, Date now) {
        TreeMap<String,Long> result = new TreeMap<>();
        JSONObject daily = stats.optJSONObject("page_days");
        if (daily == null) return result;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(ZONE);
        Calendar cal = Calendar.getInstance(ZONE); cal.setTime(now);
        String end = format.format(cal.getTime()); cal.add(Calendar.DAY_OF_YEAR, -(days-1));
        String start = format.format(cal.getTime());
        Iterator<String> dates = daily.keys();
        while (dates.hasNext()) {
            String day = dates.next();
            if (day.compareTo(start)<0 || day.compareTo(end)>0) continue;
            JSONObject pages = daily.optJSONObject(day); if (pages == null) continue;
            Iterator<String> keys = pages.keys();
            while (keys.hasNext()) {
                String key = keys.next(); long value = pages.optLong(key,0);
                if (value>0) result.put(key,result.getOrDefault(key,0L)+value);
            }
        }
        return result;
    }
    static String name(String key, JSONArray products) {
        if (key.startsWith("product:")) {
            String id = key.substring(8);
            for (int n=0;n<products.length();n++) {
                JSONObject p=products.optJSONObject(n);
                if (p!=null && id.equals(p.optString("id"))) return p.optString("name",id);
            }
            return "Oferta "+id+" (poza katalogiem)";
        }
        switch (key) {
            case "/": return "Strona główna";
            case "/sprzet-budowlany/": return "Kategoria: sprzęt budowlany";
            case "/sprzet-ogrodniczy/": return "Kategoria: sprzęt ogrodniczy";
            case "/sprzet-czyszczacy/": return "Kategoria: sprzęt czyszczący";
            case "/zasady.php": return "Zasady wynajmu";
            case "/polityka-prywatnosci.php": return "Polityka prywatności";
            case "/poradnik-jaki-papier-scierny-do-zyrafy.php": return "Poradnik: papier do żyrafy";
            case "/poradnik-pranie-tapicerki-karcher-puzzi.php": return "Poradnik: pranie tapicerki";
            case "/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php": return "Poradnik: malowanie agregatem";
            default: return key;
        }
    }
    private static void text(Context context, LinearLayout parent, String value, boolean bold) {
        TextView v = new TextView(context);v.setText(value);v.setTextSize(bold?15:13);
        v.setTextColor(bold?0xff172235:0xff526174);v.setPadding(4,12,4,8);
        if (bold) v.setTypeface(null,1);parent.addView(v);
    }
    private static void rows(Context c, LinearLayout parent, Map<String,Long> values, JSONArray products) {
        ArrayList<String> keys = new ArrayList<>(values.keySet());
        keys.sort((a,b)->{int order=Long.compare(values.get(b),values.get(a));return order!=0?order:a.compareTo(b);});
        for (String key:keys) {
            LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(12,4,12,8);row.setBackgroundColor(Color.WHITE);
            text(c,row,name(key,products),false);
            text(c,row,values.get(key)+" odsłon",true);parent.addView(row);
        }
    }
    static void render(Context c, LinearLayout parent, JSONObject stats, JSONArray products, int days, Date now) {
        text(c,parent,"ODWIEDZANE PODSTRONY • "+days+" DNI",true);
        String since=stats.optString("page_tracking_since","");
        if (since.isEmpty()) text(c,parent,"Nie ma jeszcze szczegółowych danych podstron. Pojawią się po aktualizacji licznika i pierwszych odsłonach. Poniżej historia ofert.",false);
        else {
            text(c,parent,"Pomiar od "+since.substring(0,Math.min(10,since.length()))+". Odsłony, nie liczba osób. Zakres zmienisz przy wykresie.",false);
            Map<String,Long> values=counts(stats,days,now);
            if (values.isEmpty()) text(c,parent,"Brak zarejestrowanych odsłon podstron w tym okresie.",false);
            else rows(c,parent,values,products);
        }
        android.widget.Button history=new android.widget.Button(c);history.setText("POKAŻ WSZYSTKIE OFERTY — CAŁA HISTORIA");parent.addView(history);
        LinearLayout legacy=new LinearLayout(c);legacy.setOrientation(LinearLayout.VERTICAL);legacy.setVisibility(android.view.View.GONE);parent.addView(legacy);
        JSONObject old=stats.optJSONObject("product_views");Map<String,Long> values=new TreeMap<>();
        if(old!=null){Iterator<String> keys=old.keys();while(keys.hasNext()){String key=keys.next();long count=old.optLong(key);if(count>0)values.put("product:"+key,count);}}
        text(c,legacy,"Dane historyczne ofert nie mają podziału na dni. Nie należy ich dodawać do wyników powyżej.",false);
        if(values.isEmpty())text(c,legacy,"Brak historycznych odsłon ofert.",false);else rows(c,legacy,values,products);
        history.setOnClickListener(v->{boolean show=legacy.getVisibility()!=android.view.View.VISIBLE;legacy.setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);history.setText(show?"UKRYJ HISTORIĘ OFERT":"POKAŻ WSZYSTKIE OFERTY — CAŁA HISTORIA");});
    }
}
