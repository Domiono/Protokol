package pl.rentmor.protokol;

import android.app.Activity;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.IOException;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class SyncQueueRecoveryTest {
    private LocalStore store;

    @Before public void setUp() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        store=new LocalStore(activity);
        store.getWritableDatabase().delete("queue",null,null);
        store.getWritableDatabase().delete("assets",null,null);
    }
    @After public void tearDown(){store.close();}

    @Test public void duplicateRenameIsRejectedLocally()throws Exception{
        store.saveAsset(new JSONObject().put("id","a").put("equipment","Puzzi").put("label","Puzzi 01"));
        store.saveAsset(new JSONObject().put("id","b").put("equipment","Puzzi").put("label","Puzzi 02"));
        assertTrue(store.isAssetLabelTaken(" puzzi 02 ","a"));
        assertFalse(store.isAssetLabelTaken("Puzzi 01","a"));
        assertFalse(store.isAssetLabelTaken("Puzzi 03","a"));
    }

    @Test public void repairAllPendingSavesForCopyButPreserveHistoryAndOtherChanges()throws Exception{
        JSONObject copy=new JSONObject().put("id","a").put("equipment","Puzzi")
            .put("label","Duplicated").put("in_service",true)
            .put("repairs",new JSONArray().put(new JSONObject().put("id","repair1").put("note","Filtr")));
        store.saveAsset(copy);
        store.saveAsset(new JSONObject().put("id","b").put("equipment","Puzzi").put("label","Puzzi 02"));
        store.enqueue("asset_save",new JSONObject(copy.toString()));
        store.enqueue("asset_save",new JSONObject(copy.toString()).put("service_every",5));
        store.enqueue("reservation_save",new JSONObject().put("id","reservation1"));
        JSONArray before=store.queued();
        String first=before.getJSONObject(0).getString("op_id");
        String second=before.getJSONObject(1).getString("op_id");
        String reservation=before.getJSONObject(2).getString("op_id");

        try{store.repairQueuedAssetLabel(first,"Puzzi 02");fail("duplicate");}
        catch(IllegalArgumentException ex){assertTrue(ex.getMessage().contains("używana"));}
        assertEquals(3,store.pendingCount());
        assertEquals(2,store.repairQueuedAssetLabel(first,"Puzzi 03"));
        JSONArray after=store.queued();
        assertEquals(first,after.getJSONObject(0).getString("op_id"));
        assertEquals(second,after.getJSONObject(1).getString("op_id"));
        assertEquals(reservation,after.getJSONObject(2).getString("op_id"));
        assertEquals("Puzzi 03",after.getJSONObject(0).getString("label"));
        assertEquals("Puzzi 03",after.getJSONObject(1).getString("label"));
        assertEquals(5,after.getJSONObject(1).getInt("service_every"));
        assertTrue(store.assets().getJSONObject(0).getBoolean("in_service"));
        assertEquals(1,store.assets().getJSONObject(0).getJSONArray("repairs").length());
        assertEquals("Puzzi 03",store.assets().getJSONObject(0).getString("label"));
        assertEquals(3,store.pendingCount());
    }

    @Test public void processAllServerResultsAndOnlyKeepTheRejectedItem()throws Exception{
        store.enqueue("asset_save",new JSONObject().put("id","a").put("label","Duplicated"));
        store.enqueue("reservation_save",new JSONObject().put("id","r"));
        store.enqueue("expense_save",new JSONObject().put("id","e"));
        JSONArray pending=store.queued();
        JSONArray results=new JSONArray()
            .put(new JSONObject().put("op_id",pending.getJSONObject(0).getString("op_id")).put("ok",false).put("error","Zajęta nazwa"))
            .put(new JSONObject().put("op_id",pending.getJSONObject(1).getString("op_id")).put("ok",true))
            .put(new JSONObject().put("op_id",pending.getJSONObject(2).getString("op_id")).put("ok",true));
        try{SyncManager.applyAcknowledgements(store,pending,results);fail("expected rejection");}
        catch(IOException ex){assertEquals("Zajęta nazwa",ex.getMessage());}
        assertEquals(1,store.pendingCount());
        assertEquals("asset_save",store.queued().getJSONObject(0).getString("action"));
    }
}
