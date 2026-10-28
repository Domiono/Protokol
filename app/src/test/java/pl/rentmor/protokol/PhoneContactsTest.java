package pl.rentmor.protokol;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class PhoneContactsTest {
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
