package pl.rentmor.protokol;

import android.content.Context;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.text.SimpleDateFormat;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class ReservationMonthRolloverTest {
    private JSONObject rental(String id,String month,boolean issued)throws Exception {
        return new JSONObject().put("id",id).put("month",month).put("done",issued)
            .put("equipment",id).put("start_date",month+"-20").put("end_date",month+"-21");
    }
    private Set<String> ids(JSONArray rows)throws Exception {
        Set<String> result=new HashSet<>();
        for(int i=0;i<rows.length();i++)assertTrue("Duplicate reservation",result.add(rows.getJSONObject(i).getString("id")));
        return result;
    }
    @Test public void issuedRentalsSurviveMonthAndYearChangesWithoutChangingFinancialMonths()throws Exception {
        LocalStore store=new LocalStore(RuntimeEnvironment.getApplication());
        JSONObject overdue=rental("overdue","2026-10",true);
        store.saveReservation(overdue);
        store.saveReservation(rental("current","2026-11",false));
        store.saveReservation(rental("old-pending","2026-10",false));
        store.saveReservation(rental("settled","2026-10",true).put("settled",true));
        store.saveReservation(rental("returned","2026-10",true).put("returned",true));
        assertEquals(new HashSet<>(Arrays.asList("overdue","current")),ids(store.operationalReservations("2026-11")));
        assertEquals(Collections.singleton("overdue"),ids(store.operationalReservations("2027-01")));
        assertEquals(Collections.singleton("current"),ids(store.reservations("2026-11")));
        assertEquals(4,ids(store.operationalReservations("2026-10")).size());
        overdue.put("settled",true);store.saveReservation(overdue);
        assertEquals(Collections.singleton("current"),ids(store.operationalReservations("2026-11")));
        assertEquals(5,store.allReservations().length());
        store.close();
    }
    @Test public void widgetKeepsOutstandingRentalsAndRefreshesAfterReturnWithRoleProtection()throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("rentmor",0).edit().putString("role","admin").commit();
        LocalStore store=new LocalStore(context);
        Calendar now=Calendar.getInstance();
        String current=new SimpleDateFormat("yyyy-MM",Locale.US).format(now.getTime());
        now.add(Calendar.MONTH,-1);
        String previous=new SimpleDateFormat("yyyy-MM",Locale.US).format(now.getTime());
        JSONObject issued=rental("issued",previous,true);
        store.saveReservation(issued);
        store.saveReservation(rental("pending",current,false));
        store.saveReservation(rental("closed",previous,true).put("settled",true));
        ReservationsWidgetService.Factory widget=new ReservationsWidgetService.Factory(context);
        widget.onDataSetChanged();
        assertEquals(2,widget.rows.size());
        assertEquals("issued",widget.rows.get(0).getString("id"));
        issued.put("returned",true);store.saveReservation(issued);widget.onDataSetChanged();
        assertEquals(1,widget.rows.size());assertEquals("pending",widget.rows.get(0).getString("id"));
        context.getSharedPreferences("rentmor",0).edit().putString("role","assistant").commit();
        widget.onDataSetChanged();assertTrue(widget.rows.isEmpty());
        store.close();
    }
}
