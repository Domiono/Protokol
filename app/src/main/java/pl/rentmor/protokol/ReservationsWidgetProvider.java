package pl.rentmor.protokol;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.*;
import android.widget.RemoteViews;
import org.json.*;
import java.util.*;
import java.text.*;
public class ReservationsWidgetProvider extends AppWidgetProvider {
 public static void refresh(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);ComponentName n=new ComponentName(c,ReservationsWidgetProvider.class);int[] ids=m.getAppWidgetIds(n);if(ids.length>0)new ReservationsWidgetProvider().onUpdate(c,m,ids);}
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
  String role=c.getSharedPreferences("rentmor",0).getString("role","");
  LocalStore store=new LocalStore(c);Calendar now=Calendar.getInstance();String month=String.format(Locale.US,"%04d-%02d",now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1);
  JSONArray a=store.reservations(month);ArrayList<JSONObject> rows=new ArrayList<>();
  for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&!x.optBoolean("settled"))rows.add(x);}
  Collections.sort(rows,(x,y)->{int dx=day(x.optString("text")),dy=day(y.optString("text"));if(dx!=dy)return Integer.compare(dx,dy);return Integer.compare(hour(x.optString("text")),hour(y.optString("text")));});
  StringBuilder text=new StringBuilder();
  if(!"admin".equals(role))text.append("Widżet dostępny dla administratora");
  else if(rows.isEmpty())text.append("Brak aktywnych rezerwacji w tym miesiącu");
  else {for(int i=0;i<Math.min(4,rows.size());i++){if(i>0)text.append("\n");text.append("• ").append(rows.get(i).optString("text"));}}
  for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.reservations_widget);v.setTextViewText(R.id.widget_title,"RENTMOR • REZERWACJE");v.setTextViewText(R.id.widget_rows,text.toString());v.setTextViewText(R.id.widget_footer,"Otwórz aplikację  ›");
   Intent open=new Intent(c,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(c,41,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);v.setOnClickPendingIntent(R.id.widget_root,pi);m.updateAppWidget(id,v);}
 }
 private static int day(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*(\\d{1,2})").matcher(s);return m.find()?Integer.parseInt(m.group(1)):99;}
 private static int hour(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*\\d{1,2}\\s+.*?\\s+(\\d{1,2})\\s*/").matcher(s);return m.find()?Integer.parseInt(m.group(1)):99;}
}