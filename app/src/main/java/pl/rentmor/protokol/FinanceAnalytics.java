package pl.rentmor.protokol;

import org.json.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.math.*;

/** Amounts in grosz; settlement date, not booking month, determines revenue. */
public final class FinanceAnalytics {
    public static class Bucket {
        public LocalDate start,end;public long income,loss,cost,investment;public int rentals;
        Bucket(LocalDate start,LocalDate end){this.start=start;this.end=end;}
        public long costs(){return loss+cost+investment;}
        public long result(){return income-costs();}
    }
    public static class Report extends Bucket {
        public int undated,invalid,pending;public List<Bucket> buckets=new ArrayList<>();
        public Map<String,long[]> equipment=new LinkedHashMap<>();
        Report(LocalDate start,LocalDate end){super(start,end);}
    }
    public static LocalDate date(String s){try{return LocalDate.parse(s.length()>10?s.substring(0,10):s);}catch(Exception e){return null;}}
    private static Long amount(JSONObject o,String key){
        try{if(!o.has(key)||o.isNull(key))return null;BigDecimal n=new BigDecimal(o.getString(key).replace(',','.'));if(n.signum()<0)return null;return n.movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact();}catch(Exception e){return null;}
    }
    public static LocalDate bucketStart(LocalDate d,int grain){return grain==1?d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)):grain==2?d.withDayOfMonth(1):d;}
    public static Report calculate(JSONArray rentals,JSONArray expenses,LocalDate from,LocalDate to,int grain){
        if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>3660)throw new IllegalArgumentException("Wybierz okres do 10 lat");
        Report r=new Report(from,to);Map<LocalDate,Bucket> map=new LinkedHashMap<>();
        for(LocalDate d=bucketStart(from,grain);!d.isAfter(to);){LocalDate next=grain==1?d.plusWeeks(1):grain==2?d.plusMonths(1):d.plusDays(1);Bucket b=new Bucket(d.isBefore(from)?from:d,next.minusDays(1).isAfter(to)?to:next.minusDays(1));map.put(d,b);r.buckets.add(b);d=next;}
        for(int i=0;i<rentals.length();i++){
            JSONObject o=rentals.optJSONObject(i);if(o==null)continue;
            if(!o.optBoolean("settled")){LocalDate planned=date(o.optString("start_date"));if(planned!=null&&!planned.isBefore(from)&&!planned.isAfter(to))r.pending++;continue;}
            LocalDate d=date(o.optString("settled_at"));if(d==null){r.undated++;continue;}if(d.isBefore(from)||d.isAfter(to))continue;
            Long inc=amount(o,"income"),loss=amount(o,"loss");if(inc==null||loss==null){r.invalid++;continue;}
            Bucket b=map.get(bucketStart(d,grain));r.income+=inc;r.loss+=loss;r.rentals++;b.income+=inc;b.loss+=loss;b.rentals++;
            String name=o.optString("equipment").trim();if(name.isEmpty())name="Nieprzypisany sprzęt";long[] sums=r.equipment.get(name);if(sums==null){sums=new long[3];r.equipment.put(name,sums);}sums[0]+=inc;sums[1]+=loss;sums[2]++;
        }
        for(int i=0;i<expenses.length();i++){
            JSONObject o=expenses.optJSONObject(i);if(o==null)continue;LocalDate d=date(o.optString("date"));if(d==null){r.undated++;continue;}if(d.isBefore(from)||d.isAfter(to))continue;
            Long value=amount(o,"amount");if(value==null){r.invalid++;continue;}Bucket b=map.get(bucketStart(d,grain));if("investment".equals(o.optString("type"))){r.investment+=value;b.investment+=value;}else{r.cost+=value;b.cost+=value;}
        }
        return r;
    }
}
