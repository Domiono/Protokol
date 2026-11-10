package pl.rentmor.protokol;

import android.app.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class SearchConsolePageTest {
    static class FakeSync extends SyncManager {
        volatile int reads=0,writes=0,lastDays=0;volatile boolean fail=false,wrongRole=false,missing=false;
        volatile CountDownLatch gate;
        FakeSync(Activity a){super(a,null);}
        JSONObject response(int days)throws Exception{
            if(gate!=null)gate.await(2,TimeUnit.SECONDS);
            JSONObject metric=new JSONObject().put("clicks",5).put("impressions",100).put("ctr",5).put("position",7);
            JSONObject query=new JSONObject().put("query","żyrafa wynajem Kraków").put("current",metric).put("previous",metric);
            JSONObject equipment=new JSONObject().put("id","1").put("name","Żyrafa").put("current",metric).put("previous",metric).put("queries",new JSONArray().put(query));
            return new JSONObject().put("ok",true).put("role",wrongRole?"assistant":"admin").put("days",days).put("connected",!missing).put("has_report",!missing)
                .put("ranges",new JSONObject().put("current",new JSONArray().put("2026-10-11").put("2026-11-07")).put("previous",new JSONArray().put("2026-09-13").put("2026-10-10")))
                .put("fetched_at","2026-11-10T10:00:00Z").put("equipment",new JSONArray().put(equipment));
        }
        @Override public JSONObject fetchSearchConsole(int days)throws Exception{reads++;lastDays=days;return response(days);}
        @Override public JSONObject refreshSearchConsole(int days)throws Exception{writes++;lastDays=days;if(fail)throw new java.io.IOException("Google niedostępne");return response(days);}
    }
    Activity activity;LinearLayout root;
    @Before public void setup(){activity=Robolectric.buildActivity(Activity.class).setup().get();activity.getSharedPreferences("rentmor",0).edit().putString("role","admin").commit();root=new LinearLayout(activity);activity.setContentView(root);}
    @After public void end(){activity.finish();}
    View named(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=named(((ViewGroup)v).getChildAt(i),text);if(f!=null)return f;}return null;}
    boolean contains(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(contains(((ViewGroup)v).getChildAt(i),text))return true;return false;}
    void settle()throws Exception{for(int i=0;i<35;i++){Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();}}
    @Test public void assistantNeverOpensOrCallsServer(){activity.getSharedPreferences("rentmor",0).edit().putString("role","assistant").commit();FakeSync s=new FakeSync(activity);boolean[] back={false};new SearchConsolePage(activity,root,s,()->back[0]=true).show();assertTrue(back[0]);assertEquals(0,s.reads);assertEquals(0,s.writes);assertNull(named(root,"Google / SEO"));}
    @Test public void nativeReportAndPeriodSelection()throws Exception{
        FakeSync s=new FakeSync(activity);new SearchConsolePage(activity,root,s,()->{}).show();settle();
        assertNotNull(named(root,"żyrafa wynajem Kraków"));assertTrue(contains(root,"Kliknięcia: 5"));assertEquals(28,s.lastDays);
        named(root,"Okres: 28 dni ▼").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
        AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();d.getListView().performItemClick(d.getListView().getChildAt(2),2,2);settle();
        assertEquals(90,s.lastDays);assertEquals(2,s.reads);assertEquals(0,s.writes);
    }
    @Test public void refreshFailureRetainsPreviousReport()throws Exception{
        FakeSync s=new FakeSync(activity);new SearchConsolePage(activity,root,s,()->{}).show();settle();s.fail=true;
        named(root,"Pobierz dane z Google").performClick();settle();
        assertEquals(1,s.writes);assertNotNull(named(root,"żyrafa wynajem Kraków"));assertTrue(contains(root,"poprzedni raport"));
    }
    @Test public void noReportShowsSetupInsteadOfZeroTraffic()throws Exception{
        FakeSync s=new FakeSync(activity);s.missing=true;new SearchConsolePage(activity,root,s,()->{}).show();settle();
        assertTrue(contains(root,"Najpierw połącz konto Google"));assertFalse(named(root,"Pobierz dane z Google").isEnabled());assertFalse(contains(root,"Kliknięcia: 0"));
    }
    @Test public void lateResponseAfterBackIsIgnored()throws Exception{
        FakeSync s=new FakeSync(activity);s.gate=new CountDownLatch(1);SearchConsolePage p=new SearchConsolePage(activity,root,s,()->root.removeAllViews());p.show();p.back();s.gate.countDown();settle();
        assertEquals(0,root.getChildCount());
    }
    @Test public void roleRevokedDuringRequestClearsView()throws Exception{
        FakeSync s=new FakeSync(activity);s.gate=new CountDownLatch(1);boolean[] back={false};new SearchConsolePage(activity,root,s,()->{back[0]=true;root.removeAllViews();}).show();
        activity.getSharedPreferences("rentmor",0).edit().putString("role","assistant").commit();s.gate.countDown();settle();
        assertTrue(back[0]);assertEquals(0,root.getChildCount());
    }
    @Test public void responseAdminRoleRequired()throws Exception{
        FakeSync s=new FakeSync(activity);s.wrongRole=true;new SearchConsolePage(activity,root,s,()->{}).show();settle();
        assertTrue(contains(root,"Nieprawidłowa odpowiedź"));assertNull(named(root,"żyrafa wynajem Kraków"));
    }
    static class RequestSync extends SyncManager {
        int calls;String endpoint,method;JSONObject body;
        RequestSync(Activity a){super(a,null);}
        @Override JSONObject request(String endpoint,String method,JSONObject body)throws Exception{calls++;this.endpoint=endpoint;this.method=method;this.body=body;return new JSONObject().put("role","admin");}
    }
    @Test public void clientUsesActivatedDeviceApiAndNeverReplaysRefresh()throws Exception{
        RequestSync s=new RequestSync(activity);s.fetchSearchConsole(7);assertTrue(s.endpoint.endsWith("?view=search_console&days=7"));assertEquals("GET",s.method);
        s.refreshSearchConsole(90);assertEquals("POST",s.method);assertEquals("refresh",s.body.getString("action"));assertEquals(90,s.body.getInt("days"));assertFalse(SyncManager.shouldRetryRead("POST",503,1,3));
        int before=s.calls;activity.getSharedPreferences("rentmor",0).edit().putString("role","assistant").commit();
        try{s.fetchSearchConsole(28);fail();}catch(SecurityException expected){}assertEquals(before,s.calls);
    }
}
