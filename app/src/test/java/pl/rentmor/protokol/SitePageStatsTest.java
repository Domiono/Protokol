package pl.rentmor.protokol;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.json.*;
import java.util.*;
import java.time.Instant;
import android.widget.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class SitePageStatsTest {
    @Test public void rangeUsesWarsawMidnightAndDoesNotMixLifetimeCounts() throws Exception {
        JSONObject s=new JSONObject("{\"product_views\":{\"p1\":999},\"page_days\":{\"2026-11-10\":{\"/\":100},\"2026-11-11\":{\"/\":2},\"2026-11-16\":{\"product:p1\":4},\"2026-11-17\":{\"/\":3},\"2026-11-18\":{\"/\":200}}}");
        Map<String,Long> counts=SitePageStats.counts(s,7,Date.from(Instant.parse("2026-11-16T23:30:00Z")));
        assertEquals(Long.valueOf(5),counts.get("/"));assertEquals(Long.valueOf(4),counts.get("product:p1"));
        assertEquals(Long.valueOf(105),SitePageStats.counts(s,30,Date.from(Instant.parse("2026-11-16T23:30:00Z"))).get("/"));
    }
    @Test public void oldServerAndDeletedOffersRemainExplicit() throws Exception {
        assertTrue(SitePageStats.counts(new JSONObject("{\"product_views\":{\"x\":40}}"),7,new Date()).isEmpty());
        assertEquals("Strona główna",SitePageStats.name("/",new JSONArray()));
        assertEquals("Oferta x (poza katalogiem)",SitePageStats.name("product:x",new JSONArray()));
        assertEquals("Agregat",SitePageStats.name("product:x",new JSONArray("[{\"id\":\"x\",\"name\":\"Agregat\"}]")));
    }
    @Test public void historyShowsAllOffersBeyondFormerTopFive() throws Exception {
        JSONObject old=new JSONObject();for(int i=1;i<=8;i++)old.put("p"+i,i);
        JSONObject s=new JSONObject().put("product_views",old);
        LinearLayout parent=new LinearLayout(RuntimeEnvironment.getApplication());
        SitePageStats.render(parent.getContext(),parent,s,new JSONArray(),7,new Date());
        Button toggle=null;for(int i=0;i<parent.getChildCount();i++)if(parent.getChildAt(i) instanceof Button)toggle=(Button)parent.getChildAt(i);
        assertNotNull(toggle);toggle.performClick();
        LinearLayout history=(LinearLayout)parent.getChildAt(parent.getChildCount()-1);
        assertEquals(android.view.View.VISIBLE,history.getVisibility());
        assertEquals(9,history.getChildCount()); // explanatory text + all eight offers
    }
}
