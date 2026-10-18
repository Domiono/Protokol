package pl.rentmor.protokol;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class LocalStore extends SQLiteOpenHelper {
    public LocalStore(Context c) { super(c, "rentmor_local.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE protocols(id TEXT PRIMARY KEY, number TEXT, status TEXT, data TEXT, updated INTEGER)");
        db.execSQL("CREATE TABLE queue(op_id TEXT PRIMARY KEY, action TEXT, payload TEXT, created INTEGER)");
        db.execSQL("CREATE TABLE equipment(name TEXT PRIMARY KEY)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV) {}
    public synchronized void enqueue(String action, JSONObject payload) {
        try {
            String opId = UUID.randomUUID().toString();
            payload.put("op_id", opId);
            getWritableDatabase().execSQL("INSERT INTO queue(op_id,action,payload,created) VALUES(?,?,?,?)",
                new Object[]{opId, action, payload.toString(), System.currentTimeMillis()});
            if ("create".equals(action)) saveProtocol(payload);
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    public synchronized void saveProtocol(JSONObject p) {
        try {
            String id=p.optString("id");
            ContentValues v=new ContentValues(); v.put("id",id); v.put("number",p.optString("number"));
            v.put("status",p.optString("status","wydany")); v.put("data",p.toString()); v.put("updated",System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("protocols",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    public synchronized JSONArray queued() {
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM queue ORDER BY created",null);
        try { while(c.moveToNext()) a.put(new JSONObject(c.getString(0))); } catch(Exception ignored) {} finally { c.close(); }
        return a;
    }
    public synchronized void clearQueueItem(String id) { getWritableDatabase().delete("queue","op_id=?",new String[]{id}); }
    public synchronized JSONArray allProtocols() {
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT data FROM protocols ORDER BY updated DESC",null);
        try { while(c.moveToNext()) a.put(new JSONObject(c.getString(0))); } catch(Exception ignored) {} finally { c.close(); }
        return a;
    }
    public synchronized void replaceServerProtocols(JSONArray a) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            for(int i=0;i<a.length();i++) saveProtocol(a.getJSONObject(i));
            db.setTransactionSuccessful();
        } catch(Exception ignored) {} finally { db.endTransaction(); }
    }
    public synchronized void setEquipment(JSONArray a) {
        SQLiteDatabase db=getWritableDatabase(); db.delete("equipment",null,null);
        for(int i=0;i<a.length();i++) db.execSQL("INSERT OR IGNORE INTO equipment(name) VALUES(?)",new Object[]{a.optString(i)});
    }
    public synchronized JSONArray equipment() {
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT name FROM equipment ORDER BY name",null);
        try { while(c.moveToNext()) a.put(c.getString(0)); } finally { c.close(); } return a;
    }
    public synchronized int pendingCount() {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM queue",null); try { return c.moveToFirst()?c.getInt(0):0; } finally { c.close(); }
    }
}