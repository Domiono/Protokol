package pl.rentmor.protokol;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Settlement-based per-model metrics. Every monetary value is in grosz. */
public final class EquipmentAnalytics {
    private EquipmentAnalytics() {}
    public static final class Model {
        public final String name;
        public int rentals, knownDays, missingDays;
        public long income, losses, runningCosts, investments;
        public final TreeMap<YearMonth, long[]> monthly = new TreeMap<>();
        public final List<JSONObject> history = new ArrayList<>();

        Model(String name) { this.name=name; }
        public long result() { return income-losses-runningCosts-investments; }
        public long operatingResult() { return income-losses-runningCosts; }
        public long costs() { return losses+runningCosts+investments; }
        private long[] month(LocalDate d) {
            YearMonth key=YearMonth.from(d);
            long[] v=monthly.get(key);
            if(v==null) { v=new long[3]; monthly.put(key,v); }
            return v; // [income, losses + operating expenses, investments]
        }
    }
    public static final class Snapshot {
        public final Map<String,Model> models=new LinkedHashMap<>();
        public int skipped, unassignedExpenses;
        public long unassignedCost;
        public int settledRentals;
        public long income;
        public List<Model> sorted(int order) {
            List<Model> list=new ArrayList<>(models.values());
            list.sort((a,b)->{
                int n=order==1?Long.compare(b.result(),a.result()):
                      order==2?Integer.compare(b.rentals,a.rentals):
                      Long.compare(b.income,a.income);
                return n!=0?n:a.name.compareToIgnoreCase(b.name);
            });
            return list;
        }
    }
    public static String name(JSONObject rental) {
        String explicit=rental.optString("equipment","").trim();
        if(!explicit.isEmpty())return clean(explicit);
        String raw=rental.optString("text","");
        String[] parts=raw.split("•");
        if(parts.length>=2)return clean(parts[1]);
        java.util.regex.Matcher old=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+(.+?)\\s+\\d{1,2}\\s*/\\s*.+$").matcher(raw);
        return old.find()?clean(old.group(1)):"Nieprzypisany sprzęt";
    }
    private static String clean(String name) {
        String s=name.replaceFirst("(?i)\\s+-\\s+wynajem.*$","").replaceFirst("\\s+\\|.*$","").trim();
        return s.isEmpty()?"Nieprzypisany sprzęt":s;
    }
    private static Model model(Snapshot result, String raw) {
        String label=clean(raw);
        String key=label.replaceAll("\\s+"," ").toLowerCase(new Locale("pl","PL"));
        Model m=result.models.get(key);
        if(m==null) { m=new Model(label); result.models.put(key,m); }
        return m;
    }
    private static boolean inRange(LocalDate date,LocalDate from,LocalDate to) {
        return date!=null&&(from==null||!date.isBefore(from))&&(to==null||!date.isAfter(to));
    }
    public static Snapshot calculate(JSONArray rentals,JSONArray expenses,LocalDate from,LocalDate to) {
        if(from!=null&&to!=null&&to.isBefore(from))throw new IllegalArgumentException("Nieprawidłowy okres");
        Snapshot result=new Snapshot();
        for(int i=0;i<rentals.length();i++) {
            JSONObject r=rentals.optJSONObject(i);
            if(r==null||!r.optBoolean("settled"))continue;
            LocalDate date=FinanceAnalytics.date(r.optString("settled_at"));
            if(date==null) { result.skipped++; continue; }
            if(!inRange(date,from,to))continue;
            Long income=FinanceAnalytics.amount(r,"income"),loss=FinanceAnalytics.amount(r,"loss");
            if(income==null||loss==null) { result.skipped++; continue; }
            Model m=model(result,name(r));
            m.income+=income; m.losses+=loss; m.rentals++;
            m.history.add(r); m.month(date)[0]+=income; m.month(date)[1]+=loss;
            // Prefer billed days approved during return settlement; planned dates are only a fallback.
            JSONObject settlement=r.optJSONObject("settlement");
            int billed=settlement==null?0:settlement.optInt("days",0);
            LocalDate start=FinanceAnalytics.date(r.optString("start_date"));
            LocalDate end=FinanceAnalytics.date(r.optString("actual_return",r.optString("end_date")));
            if(billed>0&&billed<=10000)m.knownDays+=billed;
            else if(start!=null&&end!=null&&!end.isBefore(start)&&ChronoUnit.DAYS.between(start,end)<3660)
                m.knownDays+=(int)Math.max(1,ChronoUnit.DAYS.between(start,end));
            else m.missingDays++;
            result.settledRentals++;result.income+=income;
        }
        for(int i=0;i<expenses.length();i++) {
            JSONObject e=expenses.optJSONObject(i);
            if(e==null)continue;
            LocalDate d=FinanceAnalytics.date(e.optString("date"));
            if(d==null){result.skipped++;continue;}
            if(!inRange(d,from,to))continue;
            Long amount=FinanceAnalytics.amount(e,"amount");
            if(amount==null){result.skipped++;continue;}
            String equipment=e.optString("equipment_name","").trim();
            if(equipment.isEmpty()) {
                result.unassignedExpenses++;
                result.unassignedCost+=amount;
                continue;
            }
            Model m=model(result,equipment);
            if("investment".equals(e.optString("type"))) {
                m.investments+=amount;
                m.month(d)[2]+=amount;
            } else {
                m.runningCosts+=amount;
                m.month(d)[1]+=amount;
            }
        }
        return result;
    }
}
