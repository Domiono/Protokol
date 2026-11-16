package pl.rentmor.protokol;

import android.app.Activity;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class MultiEquipmentReservationTest {
    JSONObject booking()throws Exception{return new JSONObject().put("id","group").put("month","2035-01").put("text","fixture")
        .put("equipment","Bruzdownica").put("start_date","2035-01-10").put("end_date","2035-01-12")
        .put("additional_items",new JSONArray().put(new JSONObject().put("line_id","vac").put("equipment","Odkurzacz przemysłowy").put("daily_rate","30")));
    }
    JSONArray assets()throws Exception{return new JSONArray()
        .put(new JSONObject().put("id","w").put("equipment","Bruzdownica").put("label","Wall 1"))
        .put(new JSONObject().put("id","v").put("equipment","Odkurzacz przemysłowy").put("label","Vac 1"));}
    @Test public void allDevicesBlockAndReturnTogether()throws Exception{
        JSONObject r=booking();JSONArray assets=assets();assertEquals("",RentalRules.autoAssign(r,new JSONArray(),assets));
        assertEquals("w",r.getString("asset_id"));assertEquals("v",r.getJSONArray("additional_items").getJSONObject(0).getString("asset_id"));
        assertEquals("",RentalRules.conflict(r,new JSONArray().put(r),assets));
        JSONObject standalone=new JSONObject(r.toString()).put("id","solo").put("equipment","Odkurzacz przemysłowy").put("asset_id","v");standalone.remove("additional_items");
        assertFalse(RentalRules.conflict(standalone,new JSONArray().put(r),assets).isEmpty());
        assertFalse(RentalRules.assetAvailability(assets.getJSONObject(1),"2035-01-11",new JSONArray().put(r)).isEmpty());
        assertFalse(RentalRules.deletionBlocker(assets.getJSONObject(1),new JSONArray().put(r),new JSONArray()).isEmpty());
        r.put("returned",true);assertEquals("",RentalRules.conflict(standalone,new JSONArray().put(r),assets));
    }
    @Test public void failureDoesNotPartiallyAssignAndDuplicateCopiesFail()throws Exception{
        JSONObject r=booking();JSONArray assets=assets();assets.getJSONObject(1).put("in_service",true);
        assertFalse(RentalRules.autoAssign(r,new JSONArray(),assets).isEmpty());assertFalse(r.has("asset_id"));
        assets.getJSONObject(1).put("in_service",false);assertEquals("",RentalRules.autoAssign(r,new JSONArray(),assets));
        JSONObject extra=r.getJSONArray("additional_items").getJSONObject(0);extra.put("equipment","Bruzdownica").put("asset_id","w");
        assertFalse(RentalRules.conflict(r,new JSONArray(),assets).isEmpty());
    }
    @Test public void arbitraryLinesSurviveLocalQueueAndOneSettlement()throws Exception{
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();a.deleteDatabase("rentmor_local.db");LocalStore store=new LocalStore(a);
        JSONArray assets=assets();for(int i=0;i<assets.length();i++)store.saveAsset(assets.getJSONObject(i));
        JSONObject r=booking();RentalItems.updateRate(r,"80");assertEquals("110.00",r.getString("daily_rate"));
        assertEquals("",RentalRules.autoAssign(r,new JSONArray(),assets));store.saveRentalOperation(r,"reservation_save");
        assertEquals(1,store.allReservations().length());assertEquals(1,store.queued().length());
        assertTrue(RentalItems.hasExtras(store.allReservations().getJSONObject(0)));
        store.clearQueueItem(store.queued().getJSONObject(0).getString("op_id"));
        store.queueConfirmedIssue(store.allReservations().getJSONObject(0));
        assertTrue(store.allReservations().getJSONObject(0).getBoolean("done"));
        JSONObject saved=store.allReservations().getJSONObject(0);saved.put("settled",true).put("income","220").put("loss","0");store.saveRentalOperation(saved,"reservation_settle");
        assertEquals(1,store.allReservations().length());assertEquals(2,RentalItems.lines(saved).length());store.close();a.finish();
    }
    @Test public void editorLoadsLinesAndLocksIssuedEquipment()throws Exception{
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();LinearLayout form=new LinearLayout(a);form.setOrientation(1);
        JSONObject r=booking();RentalRules.autoAssign(r,new JSONArray(),assets());r.put("done",true);
        AdditionalEquipmentEditor editor=new AdditionalEquipmentEditor(a,form,new JSONArray().put("Bruzdownica").put("Odkurzacz przemysłowy"),assets(),r,true);
        assertEquals("vac",editor.value().getJSONObject(0).getString("line_id"));
        assertEquals("v",editor.value().getJSONObject(0).getString("asset_id"));
        Button add=(Button)form.getChildAt(form.getChildCount()-1);assertFalse(add.isEnabled());a.finish();
    }
    @Test public void duplicateManualSelectionsFailBeforeAnyLocalAssignment()throws Exception{
        JSONObject r=booking();JSONObject line=r.getJSONArray("additional_items").getJSONObject(0);
        line.put("equipment","Bruzdownica").put("asset_id","w");
        r.getJSONArray("additional_items").put(new JSONObject(line.toString()).put("line_id","duplicate"));
        String original=r.toString();
        JSONArray stock=assets().put(new JSONObject().put("id","w2").put("equipment","Bruzdownica").put("label","Wall 2"));
        assertFalse(RentalRules.autoAssign(r,new JSONArray(),stock).isEmpty());assertEquals(original,r.toString());
    }
    @Test public void groupedModelNameRetainsAllEquipmentWithMarketingSuffixes()throws Exception{
        JSONObject r=booking();r.put("equipment","Bruzdownica - wynajem Kraków");
        r.getJSONArray("additional_items").getJSONObject(0).put("equipment","Odkurzacz - wynajem Kraków");
        assertEquals("Bruzdownica + Odkurzacz",EquipmentAnalytics.name(r));
    }
}
