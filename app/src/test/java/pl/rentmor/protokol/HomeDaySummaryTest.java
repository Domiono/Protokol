package pl.rentmor.protokol;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class HomeDaySummaryTest {
    private JSONObject rental(String start,String end,boolean issued) throws Exception {
        return new JSONObject().put("start_date",start).put("end_date",end).put("done",issued);
    }
    @Test public void countsOnlyOutstandingWorkAndSeparatesLateReturns() throws Exception {
        JSONArray rows=new JSONArray()
            .put(rental("2026-10-28","2026-10-29",false))
            .put(rental("2026-10-28","2026-10-28",true))
            .put(rental("2026-10-25","2026-10-27",true))
            .put(rental("2026-10-25","2026-10-27",false))
            .put(rental("2026-10-25","",true))
            .put(rental("2026-10-28","2026-10-28",true).put("returned",true))
            .put(rental("2026-10-28","2026-10-28",false).put("settled",true))
            .put(JSONObject.NULL);
        assertArrayEquals(new int[]{1,1,1},HomeDaySummary.count(rows,LocalDate.of(2026,10,28)));
        assertArrayEquals(new int[]{0,0,2},HomeDaySummary.count(rows,LocalDate.of(2026,10,29)));
    }
    @Test public void emptyDayHasZeroCounts() {
        assertArrayEquals(new int[]{0,0,0},HomeDaySummary.count(new JSONArray(),LocalDate.of(2026,10,28)));
    }
}
