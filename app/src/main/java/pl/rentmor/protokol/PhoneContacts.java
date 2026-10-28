package pl.rentmor.protokol;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Read-only contact history; uses the admin-only statistics sync payload. */
final class PhoneContacts {
    static ArrayList<JSONObject> filtered(JSONArray events,String from,String to,String source,String action) {
        ArrayList<JSONObject> result=new ArrayList<>();
        for(int i=0;i<events.length();i++) {
            JSONObject e=events.optJSONObject(i);if(e==null)continue;
            String at=e.optString("at");if(at.length()<10)continue;
            String day=at.substring(0,10);
            if(day.compareTo(from)<0 || day.compareTo(to)>0)continue;
            if(!source.isEmpty()&&!source.equals(e.optString("source_id")))continue;
            if(!action.isEmpty()&&!action.equals(e.optString("action")))continue;
            result.add(e);
        }
        Collections.sort(result,(a,b)->b.optString("at").compareTo(a.optString("at")));
        return result;
    }
    static void show(Activity activity,LinearLayout root,JSONObject stats,Runnable refresh,Runnable back) {
        root.removeAllViews();
        text(activity,root,"Kontakty telefoniczne",23);
        text(activity,root,"Czas polski • zdarzenia, nie unikalni klienci ani odbyte rozmowy. Historia od uruchomienia funkcji.",13);
        JSONArray raw=stats.optJSONArray("phone_events");final JSONArray events=raw==null?new JSONArray():raw;
        Calendar calendar=Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);format.setTimeZone(calendar.getTimeZone());
        final String[] dates={"",format.format(calendar.getTime())};calendar.add(Calendar.DAY_OF_YEAR,-29);dates[0]=format.format(calendar.getTime());
        LinearLayout dateRow=new LinearLayout(activity);root.addView(dateRow);
        Button from=new Button(activity),to=new Button(activity);dateRow.addView(from,new LinearLayout.LayoutParams(0,-2,1));dateRow.addView(to,new LinearLayout.LayoutParams(0,-2,1));
        ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();ids.add("");names.add("Wszystkie źródła");ids.add("home");names.add("Strona główna");
        for(int i=events.length()-1;i>=0;i--){JSONObject e=events.optJSONObject(i);if(e!=null&&!ids.contains(e.optString("source_id"))){ids.add(e.optString("source_id"));names.add(e.optString("source"));}}
        Spinner source=new Spinner(activity),action=new Spinner(activity);
        source.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,names));root.addView(source);
        action.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"Wszystkie działania","Pokaż numer","Zadzwoń"}));root.addView(action);
        LinearLayout list=new LinearLayout(activity);list.setOrientation(1);root.addView(list);
        final int[] limit={50};final Runnable[] render={null};
        render[0]=()->{
            from.setText("Od: "+dates[0]);to.setText("Do: "+dates[1]);list.removeAllViews();
            if(dates[0].compareTo(dates[1])>0){text(activity,list,"Data OD nie może być późniejsza niż DO.",15);return;}
            ArrayList<JSONObject> rows=filtered(events,dates[0],dates[1],ids.get(Math.max(0,source.getSelectedItemPosition())),new String[]{"","reveal","call"}[Math.max(0,action.getSelectedItemPosition())]);
            int calls=0,reveals=0;for(JSONObject e:rows){if("call".equals(e.optString("action")))calls++;else if("reveal".equals(e.optString("action")))reveals++;}
            text(activity,list,"Pokaż numer: "+reveals+"   •   Zadzwoń: "+calls,18);
            if(!stats.has("phone_events"))text(activity,list,"Historia nie została jeszcze pobrana z serwera. Odśwież dane.",14);
            else if(rows.isEmpty())text(activity,list,"Brak zdarzeń dla wybranych filtrów.",15);
            for(int i=0;i<Math.min(limit[0],rows.size());i++){JSONObject e=rows.get(i);text(activity,list,e.optString("at")+"\n"+e.optString("source")+"\n"+("call".equals(e.optString("action"))?"Zadzwoń":"Pokaż numer"),15);}
            if(rows.size()>limit[0]){Button more=new Button(activity);more.setText("Pokaż kolejne 50");list.addView(more);more.setOnClickListener(v->{limit[0]+=50;render[0].run();});}
        };
        for(int i=0;i<2;i++){final int index=i;(i==0?from:to).setOnClickListener(v->{String[] d=dates[index].split("-");new DatePickerDialog(activity,(picker,y,m,day)->{dates[index]=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,day);limit[0]=50;render[0].run();},Integer.parseInt(d[0]),Integer.parseInt(d[1])-1,Integer.parseInt(d[2])).show();});}
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){limit[0]=50;render[0].run();}public void onNothingSelected(AdapterView<?> p){}};
        source.setOnItemSelectedListener(listener);action.setOnItemSelectedListener(listener);
        Button reload=new Button(activity);reload.setText("Odśwież dane");root.addView(reload);reload.setOnClickListener(v->refresh.run());
        Button close=new Button(activity);close.setText("Wróć do statystyk");root.addView(close);close.setOnClickListener(v->back.run());render[0].run();
    }
    private static void text(Activity a,LinearLayout parent,String value,int size){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(0xff172235);int pad=(int)(12*a.getResources().getDisplayMetrics().density);t.setPadding(pad,pad,pad,pad);parent.addView(t);}
}
