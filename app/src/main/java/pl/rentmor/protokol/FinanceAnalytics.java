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
        public int undated,invalid,pending,addonsUnknown;public long addonPowder,addonPaper,addonDisc,addonOther,addonCustom,addonUnclassified;
        public Map<String,Long> addonCustomByName=new LinkedHashMap<>();
        public long addonTotal(){return addonPowder+addonPaper+addonDisc+addonOther+addonCustom+addonUnclassified;}
        public List<Bucket> buckets=new ArrayList<>();
        public List<JSONObject> records=new ArrayList<>();
        public Map<String,long[]> equipment=new LinkedHashMap<>();
        Report(LocalDate start,LocalDate end){super(start,end);}
    }
    public static LocalDate date(String s){try{return LocalDate.parse(s.length()>10?s.substring(0,10):s);}catch(Exception e){return null;}}
    static Long amount(JSONObject o,String key){
        try{if(!o.has(key)||o.isNull(key))return null;BigDecimal n=new BigDecimal(o.getString(key).replace(',','.'));if(n.signum()<0)return null;return n.movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact();}catch(Exception e){return null;}
    }
    /** Addons are already part of settled income. Never add them to revenue again. */
    private static void countAddons(Report r,JSONObject rental){
        JSONObject details=rental.optJSONObject("settlement");
        if(details==null){r.addonsUnknown++;return;}
        Long extra=amount(details,"extra");
        if(extra==null){r.addonsUnknown++;return;}
        String[] keys={"add_powder","add_paper","add_disc","add_other"};
        long[] values=new long[keys.length];
        boolean classified=true;
        long check=0;
        for(int i=0;i<keys.length;i++){
            Long value=amount(details,keys[i]);
            if(value==null){classified=false;break;}
            values[i]=value;
            if(check>Long.MAX_VALUE-value){classified=false;break;}
            check+=value;
        }
        Map<String,Long> customAmounts=new LinkedHashMap<>();
        if(details.has("add_custom")){
            JSONArray items=details.optJSONArray("add_custom");
            if(items==null||items.length()>20)classified=false;
            if(classified)for(int i=0;i<items.length();i++){
                JSONObject item=items.optJSONObject(i);
                if(item==null){classified=false;break;}
                String name=item.optString("name").trim().replaceAll("\\s+"," ");
                Long amount=amount(item,"amount");
                if(name.isEmpty()||name.length()>100||amount==null||amount<=0||check>Long.MAX_VALUE-amount){classified=false;break;}
                check+=amount;
                String key=name;
                for(String saved:customAmounts.keySet())if(saved.equalsIgnoreCase(name)){key=saved;break;}
                customAmounts.put(key,customAmounts.getOrDefault(key,0L)+amount);
            }
        }
        if(classified&&check==extra){
            r.addonPowder+=values[0];r.addonPaper+=values[1];
            r.addonDisc+=values[2];r.addonOther+=values[3];
            for(Map.Entry<String,Long> e:customAmounts.entrySet()){
                String key=e.getKey();
                for(String saved:r.addonCustomByName.keySet())if(saved.equalsIgnoreCase(key)){key=saved;break;}
                r.addonCustomByName.put(key,r.addonCustomByName.getOrDefault(key,0L)+e.getValue());
                r.addonCustom+=e.getValue();
            }
        }else{
            r.addonUnclassified+=extra;
        }
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
            r.records.add(o);Bucket b=map.get(bucketStart(d,grain));r.income+=inc;r.loss+=loss;r.rentals++;b.income+=inc;b.loss+=loss;b.rentals++;countAddons(r,o);
            String name=o.optString("equipment").trim();if(name.isEmpty())name="Nieprzypisany sprzęt";long[] sums=r.equipment.get(name);if(sums==null){sums=new long[3];r.equipment.put(name,sums);}sums[0]+=inc;sums[1]+=loss;sums[2]++;
        }
        for(int i=0;i<expenses.length();i++){
            JSONObject o=expenses.optJSONObject(i);if(o==null)continue;LocalDate d=date(o.optString("date"));if(d==null){r.undated++;continue;}if(d.isBefore(from)||d.isAfter(to))continue;
            Long value=amount(o,"amount");if(value==null){r.invalid++;continue;}Bucket b=map.get(bucketStart(d,grain));if("investment".equals(o.optString("type"))){r.investment+=value;b.investment+=value;}else{r.cost+=value;b.cost+=value;}
        }
        return r;
    }
}
