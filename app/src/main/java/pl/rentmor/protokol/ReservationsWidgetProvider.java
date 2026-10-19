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
  int[] rowIds={R.id.widget_row_1,R.id.widget_row_2,R.id.widget_row_3,R.id.widget_row_4};
  for(int id:ids){
   RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.reservations_widget);
   v.setTextViewText(R.id.widget_title,"RENTMOR • REZERWACJE");
   for(int i=0;i<rowIds.length;i++)v.setViewVisibility(rowIds[i],View.GONE);
   if(!"admin".equals(role)){
    v.setViewVisibility(R.id.widget_row_1,View.VISIBLE);
    v.setTextViewText(R.id.widget_row_1,"Widżet dostępny dla administratora");
    v.setTextColor(R.id.widget_row_1,0xff64748b);
    v.setInt(R.id.widget_row_1,"setBackgroundColor",0xfff1f5f9);
   }else if(rows.isEmpty()){
    v.setViewVisibility(R.id.widget_row_1,View.VISIBLE);
    v.setTextViewText(R.id.widget_row_1,"Brak aktywnych rezerwacji w tym miesiącu");
    v.setTextColor(R.id.widget_row_1,0xff64748b);
    v.setInt(R.id.widget_row_1,"setBackgroundColor",0xfff1f5f9);
   }else{
    for(int i=0;i<Math.min(rowIds.length,rows.size());i++){
     JSONObject item=rows.get(i);boolean done=item.optBoolean("done");
     int rid=rowIds[i];v.setViewVisibility(rid,View.VISIBLE);
     String raw=item.optString("text","");String[] parts=raw.split("•");
     String equipment=item.optString("equipment","").trim();
     if(equipment.isEmpty()&&parts.length>=2){equipment=parts[1].trim().replaceFirst("(?i)\\s+-\\s+wynajem.*$","").replaceFirst("\\s+\\|.*$","").trim();}
     if(equipment.isEmpty())equipment=raw;
     String time=item.optString("time","");if(time.isEmpty()){java.util.regex.Matcher tm=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+(\\d{1,2}:\\d{2})").matcher(raw);if(tm.find())time=tm.group(1);}
     String phone=item.optString("phone","");if(phone.isEmpty()&&parts.length>=3)phone=parts[parts.length-1].trim();
     int day=item.optInt("day",day(raw));String details=day+(time.isEmpty()?"":" • "+time);
     String display=(done?"✓ WYDANE":"○ DO WYDANIA")+"\\n"+equipment+"\\n"+details+(phone.isEmpty()?"":" • "+phone);
     v.setTextViewText(rid,display);
     v.setTextColor(rid,done?0xff166534:0xff26384d);
     v.setInt(rid,"setBackgroundColor",done?0xffe8f5e9:0xfff1f5f9);
    }
   }
   v.setTextViewText(R.id.widget_footer,"Otwórz aplikację  ›");
   Intent open=new Intent(c,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
   PendingIntent pi=PendingIntent.getActivity(c,41,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   v.setOnClickPendingIntent(R.id.widget_root,pi);m.updateAppWidget(id,v);
  }
 }
 private static int day(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*(\\d{1,2})").matcher(s);return m.find()?Integer.parseInt(m.group(1)):99;}
 private static int hour(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+.*?\\s+(\\d{1,2})\\s*/").matcher(s);return m.find()?Integer.parseInt(m.group(1)):99;}
}