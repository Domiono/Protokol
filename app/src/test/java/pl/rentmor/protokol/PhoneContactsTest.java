package pl.rentmor.protokol;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.robolectric.Robolectric;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class PhoneContactsTest {
    private String text(View view) {
        String result=view instanceof TextView?((TextView)view).getText().toString()+"\n":"";
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)result+=text(((ViewGroup)view).getChildAt(i));
        return result;
    }
    @Test public void summaryUsesHistoryNotLegacyCounterAndMatchesList() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout root=new LinearLayout(activity);
        Calendar now=Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));now.clear();now.set(2026,Calendar.OCTOBER,29,12,0);
        String[] dates=PhoneContacts.defaultDates(now.getTime());
        assertArrayEquals(new String[]{"2026-09-30","2026-10-29"},dates);
        JSONArray events=new JSONArray()
            .put(event("2026-10-29 10:00:00","call","szlifierka"))
            .put(event("2026-10-29 11:00:00","call","szlifierka"))
            .put(event("2026-09-30 00:00:00","reveal","puzzi"))
            .put(event("2026-09-29 23:59:59","call","home"))
            .put(event("2026-10-30 00:00:00","call","home"));
        JSONObject stats=new JSONObject().put("total_phone_clicks",1).put("phone_events",events);
        PhoneContacts.summary(activity,root,stats,now.getTime());
        assertTrue(text(root).contains("Zadzwoń: 2   •   Pokaż numer: 1"));
        assertArrayEquals(new int[]{2,1},PhoneContacts.counts(PhoneContacts.filtered(events,dates[0],dates[1],"","")));
        assertArrayEquals(new int[]{0,1},PhoneContacts.counts(PhoneContacts.filtered(events,dates[0],dates[1],"puzzi","")));
        root.removeAllViews();stats.put("phone_events",new JSONArray());
        PhoneContacts.summary(activity,root,stats,now.getTime());assertTrue(text(root).contains("Zadzwoń: 0   •   Pokaż numer: 0"));
        root.removeAllViews();stats.remove("phone_events");
        PhoneContacts.summary(activity,root,stats,now.getTime());assertTrue(text(root).contains("Zadzwoń: —"));assertFalse(text(root).contains("Zadzwoń: 1"));
    }
    @Test public void datesUseWarsawEvenWhenDeviceIsInAnotherTimezone() throws Exception {
        TimeZone previous=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            java.text.SimpleDateFormat utc=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US);utc.setTimeZone(TimeZone.getTimeZone("UTC"));
            assertArrayEquals(new String[]{"2026-09-30","2026-10-29"},PhoneContacts.defaultDates(utc.parse("2026-10-28 23:30:00")));
        } finally {TimeZone.setDefault(previous);}
    }
    private JSONObject event(String at,String action,String source) throws Exception {return new JSONObject().put("at",at).put("action",action).put("source_id",source);}
    @Test public void filtersInclusiveDatesAndSortsNewestFirst() throws Exception {
        JSONArray events=new JSONArray().put(event("2026-10-27 23:59:59","call","home")).put(event("2026-10-28 00:00:00","call","home")).put(event("2026-10-28 23:59:59","reveal","p1")).put(event("2026-10-29 00:00:00","call","p1"));
        ArrayList<JSONObject> rows=PhoneContacts.filtered(events,"2026-10-28","2026-10-28","","");
        assertEquals(2,rows.size());assertEquals("reveal",rows.get(0).getString("action"));
        assertEquals(1,PhoneContacts.filtered(events,"2026-10-28","2026-10-28","home","call").size());
        assertEquals(0,PhoneContacts.filtered(events,"2026-10-28","2026-10-28","home","reveal").size());
    }
    @Test public void preservesRepeatedClicksAndHandlesNoData() throws Exception {
        JSONObject click=event("2026-10-28 08:00:00","call","p1");
        assertEquals(2,PhoneContacts.filtered(new JSONArray().put(click).put(click),"2026-10-01","2026-10-31","","").size());
        assertTrue(PhoneContacts.filtered(new JSONArray(),"2026-10-01","2026-10-31","","").isEmpty());
    }
}

