package pl.rentmor.protokol;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReservationsWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_WIDGET_ACTION = "pl.rentmor.protokol.WIDGET_RESERVATION_ACTION";

    public static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        ComponentName n = new ComponentName(c, ReservationsWidgetProvider.class);
        int[] ids = m.getAppWidgetIds(n);
        if (ids.length > 0) {
            new ReservationsWidgetProvider().onUpdate(c, m, ids);
            m.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
        }
    }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.reservations_widget);
            Intent svc = new Intent(c, ReservationsWidgetService.class);
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
            v.setRemoteAdapter(R.id.widget_list, svc);

            Intent open = new Intent(c, MainActivity.class);
            open.putExtra("open_reservations", true);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 41, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.widget_root, pi);

            Intent action = new Intent(c, ReservationsWidgetProvider.class);
            action.setAction(ACTION_WIDGET_ACTION);
            PendingIntent actionTemplate = PendingIntent.getBroadcast(c, 42, action,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            v.setPendingIntentTemplate(R.id.widget_list, actionTemplate);

            v.setTextViewText(R.id.widget_footer, "");
            m.updateAppWidget(id, v);
        }
    }

    @Override public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (!ACTION_WIDGET_ACTION.equals(intent.getAction())) return;

        String action = intent.getStringExtra("action");
        String id = intent.getStringExtra("reservation_id");
        if (id == null || id.trim().isEmpty()) return;

        if ("open".equals(action)) {
            Intent open = new Intent(c, MainActivity.class);
            open.putExtra("open_reservations", true);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            c.startActivity(open);
            return;
        }
        LocalStore store = new LocalStore(c);
        if ("toggle".equals(action)) {
            JSONArray all = store.allReservations();
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.optJSONObject(i);
                if (item == null || !id.equals(item.optString("id"))) continue;
                try {
                    boolean next = !item.optBoolean("done");
                    item.put("done", next);
                    store.saveReservation(item);
                    store.enqueue("reservation_save", item);
                    refresh(c);
                    android.widget.Toast.makeText(c, next ? "Rezerwacja oznaczona jako wydana" : "Przywrócono status do wydania",
                            android.widget.Toast.LENGTH_SHORT).show();
                    syncInBackground(c, store);
                } catch (Exception e) {
                    android.widget.Toast.makeText(c, "Nie udało się zmienić statusu", android.widget.Toast.LENGTH_SHORT).show();
                }
                return;
            }
        } else {
            String phone = intent.getStringExtra("phone");
            if (phone == null || phone.trim().isEmpty()) return;
            String number = phone.replaceAll("[^+0-9]", "");
            try {
                if ("call".equals(action)) {
                    Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
                    dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(dial);
                } else if ("sms".equals(action)) {
                    Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null));
                    sms.putExtra("sms_body", "Dzień dobry, kontaktuję się w sprawie rezerwacji sprzętu w RentMor.");
                    sms.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(sms);
                }
            } catch (Exception e) {
                android.widget.Toast.makeText(c, "Nie można otworzyć aplikacji telefonu lub SMS", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void syncInBackground(Context c, LocalStore store) {
        if (SecurePrefs.get(c,"cookie","").isEmpty()) return;
        new Thread(() -> {
            try { new SyncManager(c, store).sync(); }
            catch (Exception ignored) { }
        }).start();
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle o) {
        super.onAppWidgetOptionsChanged(c, m, id, o);
        m.notifyAppWidgetViewDataChanged(id, R.id.widget_list);
    }
}