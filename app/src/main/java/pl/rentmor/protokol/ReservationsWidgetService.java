package pl.rentmor.protokol;
import android.appwidget.*;import android.content.*;import android.widget.*;import android.view.*;import org.json.*;import java.util.*;import java.util.regex.*;
public class ReservationsWidgetService extends RemoteViewsService {
 @Override public RemoteViewsFactory onGetViewFactory(Intent i){return new Factory(getApplicationContext());}
 static class Factory implements RemoteViewsFactory {
  final Context c;final ArrayList<JSONObject> rows=new ArrayList<>();boolean admin;
  Factory(Context c){this.c=c;} public void onCreate(){load();}public void onDataSetChanged(){load();}public void onDestroy(){}
  void load(){rows.clear();admin="admin".equals(c.getSharedPreferences("rentmor",0).getString("role",""));if(!admin)return;
   Calendar n=Calendar.getInstance();String month=String.format(Locale.US,"%04d-%02d",n.get(Calendar.YEAR),n.get(Calendar.MONTH)+1);
   JSONArray a=new LocalStore(c).reservations(month);for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&!x.optBoolean("settled"))rows.add(x);}
   Collections.sort(rows,(x,y)->{int d=Integer.compare(day(x.optString("text")),day(y.optString("text")));return d!=0?d:Integer.compare(hour(x.optString("text")),hour(y.optString("text")));});
  }
  public int getCount(){return admin?Math.max(1,rows.size()):1;}
  public RemoteViews getViewAt(int p){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.reservations_widget_row);
   if(!admin){v.setTextViewText(R.id.widget_item_text,"Widżet dostępny dla administratora");return v;}
   if(rows.isEmpty()){v.setTextViewText(R.id.widget_item_text,"Brak aktywnych rezerwacji w tym miesiącu");return v;}
   JSONObject x=rows.get(p);boolean done=x.optBoolean("done");String raw=x.optString("text","");String[] parts=raw.split("•");
   String eq=x.optString("equipment","").trim();if(eq.isEmpty()&&parts.length>=2)eq=parts[1].trim();if(eq.isEmpty())eq=raw;
   eq=eq.replaceFirst("(?i)\\s+-\\s+wynajem.*$","").replaceFirst("\\s+\\|.*$","").trim();
   String time=x.optString("time","").trim();if(time.isEmpty()){Matcher m=Pattern.compile("(\\d{1,2}:\\d{2})").matcher(raw);if(m.find())time=m.group(1);}
   String phone=x.optString("phone","").trim();if(phone.isEmpty()&&parts.length>=3)phone=parts[parts.length-1].trim();
   String txt=(done?"✓ WYDANE":"● DO WYDANIA")+"   "+dateRange(x,raw)+"\n"+eq+(time.isEmpty()?"":"   •   "+time)+(phone.isEmpty()?"":"\nTEL. "+phone);
   v.setTextViewText(R.id.widget_item_text,txt);v.setTextColor(R.id.widget_item_text,done?0xff176b55:0xff203a57);
   v.setInt(R.id.widget_item,"setBackgroundResource",done?R.drawable.widget_row_issued:R.drawable.widget_row_pending);return v;
  }
  public RemoteViews getLoadingView(){return null;}public int getViewTypeCount(){return 1;}public long getItemId(int p){return p;}public boolean hasStableIds(){return true;}
  static int day(String s){Matcher m=Pattern.compile("^\\s*(\\d{1,2})").matcher(s);return m.find()?Integer.parseInt(m.group(1)):99;}
  static int hour(String s){Matcher m=Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(s);return m.find()?Integer.parseInt(m.group(1)):99;}
  static String dateRange(JSONObject x,String raw){String a=x.optString("start_date","").trim(),b=x.optString("end_date","").trim();
   if(!a.matches("\\d{4}-\\d{2}-\\d{2}")){Matcher m=Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*[–-]\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(raw);if(m.find()){a=m.group(1);b=m.group(2);}}
   if(!a.matches("\\d{4}-\\d{2}-\\d{2}"))return "DZIEŃ "+x.optInt("day",day(raw));if(!b.matches("\\d{4}-\\d{2}-\\d{2}"))b=a;
   return "OD: "+a.substring(8,10)+"."+a.substring(5,7)+" • DO: "+b.substring(8,10)+"."+b.substring(5,7);
  }
 }
}