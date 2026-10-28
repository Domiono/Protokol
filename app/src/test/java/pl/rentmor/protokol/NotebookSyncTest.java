package pl.rentmor.protokol;

import android.app.Activity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=29)
public class NotebookSyncTest {
    @Test public void offlineChangesSyncAndDeleteWithoutResurrection() throws Exception {
        Activity context=Robolectric.buildActivity(Activity.class).setup().get();
        context.deleteDatabase("rentmor_notebook.db");
        NotebookStore notes=new NotebookStore(context);
        String id=notes.save(null,"Kup papier","Zakupy");
        JSONArray pending=notes.pending();
        assertEquals(1,pending.length());
        assertEquals(0,pending.getJSONObject(0).getInt("base_revision"));
        JSONArray ack=new JSONArray().put(new JSONObject()
            .put("op_id",pending.getJSONObject(0).getString("op_id"))
            .put("ok",true).put("revision",1));
        notes.acknowledgements(pending,ack);
        assertEquals(0,notes.pending().length());
        notes.delete(id);
        assertEquals(0,notes.pendingCount());
        JSONArray deletion=notes.pending();
        assertEquals(1,deletion.length());
        assertTrue(deletion.getJSONObject(0).getBoolean("deleted"));
        notes.acknowledgements(deletion,new JSONArray().put(new JSONObject()
            .put("op_id",deletion.getJSONObject(0).getString("op_id"))
            .put("ok",true).put("revision",2)));
        notes.mergeRemote(new JSONArray().put(new JSONObject().put("id",id)
            .put("text","Kup papier").put("category","Zakupy").put("done",false)
            .put("deleted",true).put("revision",2)));
        assertEquals(0,notes.entries(null,false).size());
        notes.close();context.deleteDatabase("rentmor_notebook.db");
    }

    @Test public void editingDuringSyncPreservesTheNewerLocalChange() throws Exception {
        Activity context=Robolectric.buildActivity(Activity.class).setup().get();
        context.deleteDatabase("rentmor_notebook.db");
        NotebookStore notes=new NotebookStore(context);
        String id=notes.save(null,"Zakup A","Zakupy");
        JSONArray posted=notes.pending();
        notes.save(id,"Zakup A i B","Zakupy");
        notes.acknowledgements(posted,new JSONArray().put(new JSONObject()
            .put("op_id",posted.getJSONObject(0).getString("op_id"))
            .put("ok",true).put("revision",1)));
        assertEquals(1,notes.pending().length());
        assertEquals(1,notes.pending().getJSONObject(0).getInt("base_revision"));
        notes.mergeRemote(new JSONArray().put(new JSONObject().put("id",id).put("text","Zakup A")
            .put("category","Zakupy").put("done",false).put("deleted",false).put("revision",1)));
        assertEquals(0,notes.conflicts());
        assertEquals("Zakup A i B",notes.entries(null,false).get(0).text);
        notes.close();context.deleteDatabase("rentmor_notebook.db");
    }

    @Test public void detectsConflictsInsteadOfOverwritingOfflineEdits() throws Exception {
        Activity context=Robolectric.buildActivity(Activity.class).setup().get();
        NotebookStore notes=new NotebookStore(context);
        String id="44444444-4444-4444-a444-444444444444";
        notes.mergeRemote(new JSONArray().put(new JSONObject().put("id",id)
            .put("text","Zamów worki").put("category","Zakupy").put("done",false)
            .put("deleted",false).put("revision",4)));
        notes.save(id,"Zamów inne worki","Zakupy");
        notes.mergeRemote(new JSONArray().put(new JSONObject().put("id",id)
            .put("text","Zamów filtry").put("category","Zakupy").put("done",false)
            .put("deleted",false).put("revision",5)));
        assertEquals(1,notes.conflicts());
        assertEquals(0,notes.pending().length());
        assertEquals("Zamów inne worki",notes.entries(null,false).get(0).text);
        notes.save(id,"Zamów inne worki (potwierdzone)","Zakupy");
        assertEquals(0,notes.conflicts());
        assertEquals(5,notes.pending().getJSONObject(0).getInt("base_revision"));
        notes.close();
    }
}
