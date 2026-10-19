package pl.rentmor.protokol;
import android.app.PendingIntent;import android.appwidget.*;import android.content.*;import android.widget.RemoteViews;
public class ReservationsWidgetProvider extends AppWidgetProvider {
 public static void refresh(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);ComponentName n=new ComponentName(c,ReservationsWidgetProvider.class);int[] ids=m.getAppWidgetIds(n);if(ids.length>0){new ReservationsWidgetProvider().onUpdate(c,m,ids);m.notifyAppWidgetViewDataChanged(ids,R.id.widget_list);}}
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.reservations_widget);
 Intent svc=new Intent(c,ReservationsWidgetService.class);svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);svc.setData(android.net.Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));v.setRemoteAdapter(R.id.widget_list,svc);
 Intent open=new Intent(c,MainActivity.class);open.putExtra("open_reservations",true);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(c,41,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 v.setOnClickPendingIntent(R.id.widget_root,pi);v.setTextViewText(R.id.widget_footer,"Przewiń listę • Otwórz aplikację  ›");m.updateAppWidget(id,v);}}
 @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle o){super.onAppWidgetOptionsChanged(c,m,id,o);m.notifyAppWidgetViewDataChanged(id,R.id.widget_list);}
}