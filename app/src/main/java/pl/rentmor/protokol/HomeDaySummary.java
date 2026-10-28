package pl.rentmor.protokol;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;

/** Same reservation-based definition on the home screen and in the day plan. */
final class HomeDaySummary {
    static int[] count(JSONArray reservations, LocalDate day) {
        int[] result = new int[3];
        String today = day.toString();
        for (int i = 0; i < reservations.length(); i++) {
            JSONObject r = reservations.optJSONObject(i);
            if (r == null || r.optBoolean("settled") || r.optBoolean("returned")) continue;
            boolean issued = r.optBoolean("done");
            String end = r.optString("end_date");
            if (!issued && today.equals(r.optString("start_date"))) result[0]++;
            if (issued && today.equals(end)) result[1]++;
            if (issued && !end.isEmpty() && end.compareTo(today) < 0) result[2]++;
        }
        return result;
    }
}
