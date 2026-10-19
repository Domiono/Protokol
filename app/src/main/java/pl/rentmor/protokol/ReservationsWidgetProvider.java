package pl.rentmor.protokol;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.*;
import android.view.View;
import android.widget.RemoteViews;
import org.json.*;
import java.util.*;
public class ReservationsWidgetProvider extends AppWidgetProvider {
 public static void refresh(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);ComponentName n=new ComponentName(c,ReservationsWidgetProvider.class);int[] ids=m.getAppWidgetIds(n);if(ids.length>0)new ReservationsWidgetProvider().onUpdate(c,m,ids);}
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
  String role=c.getSharedPreferences("rentmor",0).getString("role","");
  LocalStore store=new LocalStore(c);Calendar now=Calendar.getInstance();String month=String.format(Locale.US,"%04d-%02d",now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1);
  JSONArray a=store.reservations(month);ArrayList<JSONObject> rows=new ArrayList<>();
  for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&!x.optBoolean("settled"))rows.add(x);}
  Collections.sort(rows,(x,y)->{int dx=day(x.optString("text")),dy=day(y.optString("text"));if(dx!=dy)return Integer.compare(dx,dy);return Integer.compare(hour(x.optString("text")),hour(y.optString("text")));});
  int[] rowIds={R.id.widget_row_1,R.id.widget_row_2,R.id.widget_row_3,R.id.widget_row_4,R.id.widget_row_5,R.id.widget_row_6,R.id.widget_row_7,R.id.widget_row_8,R.id.widget_row_9,R.id.widget_row_10};
  for(int id:ids){
   RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.reservations_widget);
   v.setTextViewText(R.id.widget_title,"RENTMOR • REZERWACJE");
   for(int i=0;i<rowIds.length;i++)v.setViewVisibility(rowIds[i],View.GONE);
   if(!"admin".equals(role)){
    v.setViewVisibility(R.id.widget_row_1,View.VISIBLE);
    v.setTextViewText(R.id.widget_row_1,"Widżet dostępny dla administratora");
    v.setTextColor(R.id.widget_row_1,0xff53677f);
    v.setInt(R.id.widget_row_1,"setBackgroundResource",R.drawable.widget_row_pending);
   }else if(rows.isEmpty()){
    v.setViewVisibility(R.id.widget_row_1,View.VISIBLE);
    v.setTextViewText(R.id.widget_row_1,"Brak aktywnych rezerwacji w tym miesiącu");
    v.setTextColor(R.id.widget_row_1,0xff64748b);
    v.setInt(R.id.widget_row_1,"setBackgroundResource",R.drawable.widget_row_pending);
   }else{
    for(int i=0;i<Math.min(rowIds.length,rows.size());i++){
     JSONObject item=rows.get(i);boolean done=item.optBoolean("done");
     int rid=rowIds[i];v.setViewVisibility(rid,View.VISIBLE);
     String raw=item.optString("text","");String[] parts=raw.split("•");
     String equipment=item.optString("equipment","").trim();
     if(equipment.isEmpty()&&parts.length>=2)equipment=parts[1].trim();
     if(equipment.isEmpty()){java.util.regex.Matcher em=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+(.+?)\\s+\\d{1,2}\\s*/\\s*.+$").matcher(raw);equipment=em.find()?em.group(1):raw;}
     equipment=equipment.replaceFirst("(?i)\\s+-\\s+wynajem.*$","").replaceFirst("\\s+\\|.*$","").trim();
     String time=item.optString("time","").trim();if(time.isEmpty()){java.util.regex.Matcher tm=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+(\\d{1,2}:\\d{2})").matcher(raw);if(tm.find())time=tm.group(1);else{java.util.regex.Matcher lm=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+.+?\\s+(\\d{1,2})\\s*/").matcher(raw);if(lm.find())time=String.format(Locale.US,"%02d:00",Integer.parseInt(lm.group(1)));}}
     String phone=item.optString("phone","").trim();if(phone.isEmpty()){if(parts.length>=3)phone=parts[parts.length-1].trim();else{java.util.regex.Matcher pm=java.util.regex.Pattern.compile("/\\s*(.+)$").matcher(raw);if(pm.find())phone=pm.group(1).trim();}}
     String dates=dateRange(item,raw);
     String details=time.isEmpty()?"":"GODZ. "+time;
     String display=(done?"✓ WYDANE":"● DO WYDANIA")+"\n"+dates+"\n"+equipment+(details.isEmpty()?"":"\n"+details)+(phone.isEmpty()?"":"\nTEL. "+phone);
     v.setTextViewText(rid,display);
     v.setTextColor(rid,done?0xff176b55:0xff203a57);
     v.setInt(rid,"setBackgroundResource",done?R.drawable.widget_row_issued:R.drawable.widget_row_pending);
    }
   }
   v.setTextViewText(R.id.widget_footer,"Otwórz aplikację  ›");
   Intent open=new Intent(c,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
   PendingIntent pi=PendingIntent.getActivity(c,41,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   v.setOnClickPendingIntent(R.id.widget_root,pi);m.updateAppWidget(id,v);
  }
 }
 private static String dateRange(JSONObject item,String raw){
  String from=item.optString("start_date","").trim(),to=item.optString("end_date","").trim();
  if(!from.matches("\\d{4}-\\d{2}-\\d{2}")){
   java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*[–-]\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(raw);
   if(m.find()){from=m.group(1);to=m.group(2);}
  }
  if(!from.matches("\\d{4}-\\d{2}-\\d{2}")){int d=item.optInt("day",day(raw));return "DZIEŃ "+d;}
  if(!to.matches("\\d{4}-\\d{2}-\\d{2}"))to=from;
  String a=from.substring(8,10)+"."+from.substring(5,7)+"."+from.substring(0,4);
  String b=to.substring(8,10)+"."+to.substring(5,7)+"."+to.substring(0,4);
  return "OD: "+a+" • DO: "+b;
 }
 private static int day(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*(\\d{1,2})").matcher(s);return m.find()?Integer.parseInt(m.group(1)):99;}
 private static int hour(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+.*?\\s+(\\d{1,2})\\s*/").matcher(s);return m.find()?Integer.parseInt(m.group(1)):99;}
}