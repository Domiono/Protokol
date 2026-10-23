package pl.rentmor.protokol;

import android.appwidget.*;
import android.content.*;
import android.widget.*;
import android.view.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;

public class ReservationsWidgetService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent i) { return new Factory(getApplicationContext()); }

    static class Factory implements RemoteViewsFactory {
        final Context c;
        final ArrayList<JSONObject> rows = new ArrayList<>();
        boolean admin;

        Factory(Context c) { this.c = c; }
        public void onCreate() { load(); }
        public void onDataSetChanged() { load(); }
        public void onDestroy() {}

        void load() {
            rows.clear();
            admin = "admin".equals(c.getSharedPreferences("rentmor", 0).getString("role", ""));
            if (!admin) return;
            Calendar n = Calendar.getInstance();
            String month = String.format(Locale.US, "%04d-%02d", n.get(Calendar.YEAR), n.get(Calendar.MONTH) + 1);
            JSONArray a = new LocalStore(c).reservations(month);
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null && !x.optBoolean("settled")) rows.add(x);
            }
            Collections.sort(rows, (x, y) -> {
                int issued = Integer.compare(y.optBoolean("done") ? 1 : 0, x.optBoolean("done") ? 1 : 0);
                if (issued != 0) return issued;
                int date = sortDate(x).compareTo(sortDate(y));
                if (date != 0) return date;
                return sortTime(x).compareTo(sortTime(y));
            });
        }

        public int getCount() { return admin ? Math.max(1, rows.size()) : 1; }

        public RemoteViews getViewAt(int p) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.reservations_widget_row);
            if (!admin) {
                v.setTextViewText(R.id.widget_item_text, "Widżet dostępny dla administratora");
                v.setViewVisibility(R.id.widget_equipment_name, View.GONE);
                v.setViewVisibility(R.id.widget_details_text, View.GONE);
                hideActions(v);
                return v;
            }
            if (rows.isEmpty()) {
                v.setTextViewText(R.id.widget_item_text, "Brak aktywnych rezerwacji w tym miesiącu");
                v.setViewVisibility(R.id.widget_equipment_name, View.GONE);
                v.setViewVisibility(R.id.widget_details_text, View.GONE);
                hideActions(v);
                return v;
            }

            JSONObject x = rows.get(p);
            boolean done = x.optBoolean("done");
            String raw = x.optString("text", "");
            String[] parts = raw.split("•");
            String eq = x.optString("equipment", "").trim();
            if (eq.isEmpty() && parts.length >= 2) eq = parts[1].trim();
            if (eq.isEmpty()) eq = raw;
            eq = eq.replaceFirst("(?i)\\s+-\\s+wynajem.*$", "").replaceFirst("\\s+\\|.*$", "").trim();

            String time = x.optString("time", "").trim();
            if (time.isEmpty()) {
                Matcher m = Pattern.compile("(\\d{1,2}:\\d{2})").matcher(raw);
                if (m.find()) time = m.group(1);
            }
            String phone = x.optString("phone", "").trim();
            if (phone.isEmpty() && parts.length >= 3) phone = parts[parts.length - 1].trim();

            String customer = x.optString("customer_name", x.optString("client", "")).trim();
            String header = (done ? "✓ WYDANE" : "● DO WYDANIA") + "   ·   " + dateRange(x, raw);
            String details = time.isEmpty() ? "Godzina nieustalona" : "◷ " + time;
            if (!customer.isEmpty()) details += (details.isEmpty() ? "" : "   •   ") + customer;
            if (!phone.isEmpty()) details += (details.isEmpty() ? "" : "   •   ") + (isPhoneNumber(phone) ? "TEL. " : "OLX: ") + phone;
            v.setTextViewText(R.id.widget_item_text, header);
            v.setTextViewText(R.id.widget_equipment_name, eq);
            setAction(v, R.id.widget_card, x, "open", phone);
            v.setInt(R.id.widget_equipment_name, "setBackgroundColor", equipmentColor(eq));
            v.setTextViewText(R.id.widget_details_text, details);
            v.setViewVisibility(R.id.widget_details_text, details.isEmpty() ? View.GONE : View.VISIBLE);
            v.setTextColor(R.id.widget_item_text, done ? 0xff176b55 : 0xff203a57);
            v.setInt(R.id.widget_card, "setBackgroundResource", done ? R.drawable.widget_row_issued : R.drawable.widget_row_pending);

            setAction(v, R.id.widget_action_status, x, "toggle", phone);
            v.setTextViewText(R.id.widget_action_status, done ? "COFNIJ" : "WYDANO");
            boolean validPhone = isPhoneNumber(phone);
            v.setViewVisibility(R.id.widget_action_sms, validPhone ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.widget_action_call, validPhone ? View.VISIBLE : View.GONE);
            if (validPhone) {
                setAction(v, R.id.widget_action_sms, x, "sms", phone);
                setAction(v, R.id.widget_action_call, x, "call", phone);
            }
            return v;
        }

        int equipmentColor(String name) {
            String s = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if (s.contains("puzzi") || s.contains("odkurzacz") || s.contains("karcher")) return 0xff3b82b8;
            if (s.contains("szlifier") || s.contains("żyrafa") || s.contains("zyrafa")) return 0xffd58a32;
            if (s.contains("agregat malarski") || s.contains("wagner") || s.contains("wąbrotech") || s.contains("wabrotech")) return 0xff8b70b5;
            if (s.contains("glebogryz") || s.contains("kosi") || s.contains("wykasz") || s.contains("stiga")) return 0xff57966a;
            if (s.contains("rębak") || s.contains("rebak")) return 0xff9b7653;
            if (s.contains("zagęsz") || s.contains("zagesz")) return 0xffc2a23b;
            if (s.contains("bruzdown")) return 0xffbd6262;
            if (s.contains("osusz")) return 0xff4d9c9a;
            return 0xff8293a6;
        }

        void hideActions(RemoteViews v) {
            v.setViewVisibility(R.id.widget_actions, View.GONE);
        }

        void setAction(RemoteViews v, int viewId, JSONObject x, String action, String phone) {
            Intent fill = new Intent();
            fill.putExtra("action", action);
            fill.putExtra("reservation_id", x.optString("id"));
            fill.putExtra("phone", phone);
            v.setOnClickFillInIntent(viewId, fill);
        }

        boolean isPhoneNumber(String value) {
            String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
            return digits.length() >= 7 && digits.length() <= 15;
        }

        public RemoteViews getLoadingView() { return null; }
        public int getViewTypeCount() { return 1; }
        public long getItemId(int p) { return p; }
        public boolean hasStableIds() { return true; }

        static String sortDate(JSONObject x) {
            String d = x.optString("start_date", "").trim();
            if (d.matches("\\d{4}-\\d{2}-\\d{2}")) return d;
            Matcher m = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(x.optString("text", ""));
            return m.find() ? m.group(1) : "9999-99-99";
        }

        static String sortTime(JSONObject x) {
            String t = x.optString("time", "").trim();
            if (t.isEmpty()) {
                Matcher m = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(x.optString("text", ""));
                if (m.find()) t = String.format(Locale.US, "%02d:%s", Integer.parseInt(m.group(1)), m.group(2));
            }
            Matcher m = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(t);
            return m.find() ? String.format(Locale.US, "%02d:%s", Integer.parseInt(m.group(1)), m.group(2)) : "99:99";
        }

        static String dateRange(JSONObject x, String raw) {
            String a = x.optString("start_date", "").trim(), b = x.optString("end_date", "").trim();
            if (!a.matches("\\d{4}-\\d{2}-\\d{2}")) {
                Matcher m = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*[–-]\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(raw);
                if (m.find()) { a = m.group(1); b = m.group(2); }
            }
            if (!a.matches("\\d{4}-\\d{2}-\\d{2}")) {
                int day = x.optInt("day", 0);
                String month = x.optString("month", "");
                if (day >= 1 && day <= 31 && month.matches("\\d{4}-\\d{2}"))
                    return String.format(Locale.US, "%02d.%s", day, month.substring(5, 7));
                return "TERMIN NIEUSTALONY";
            }
            if (!b.matches("\\d{4}-\\d{2}-\\d{2}")) b = a;
            return "OD: " + a.substring(8, 10) + "." + a.substring(5, 7) + " • DO: " + b.substring(8, 10) + "." + b.substring(5, 7);
        }
    }
}