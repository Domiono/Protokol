package pl.rentmor.protokol;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class LocalStore extends SQLiteOpenHelper {
    public LocalStore(Context c) { super(c, "rentmor_local.db", null, 7); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE assets(id TEXT PRIMARY KEY, data TEXT NOT NULL)");
        db.execSQL("CREATE TABLE protocols(id TEXT PRIMARY KEY, number TEXT, status TEXT, data TEXT, updated INTEGER)");
        db.execSQL("CREATE TABLE queue(op_id TEXT PRIMARY KEY, action TEXT, payload TEXT, created INTEGER)");
        db.execSQL("CREATE TABLE equipment(name TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE reservations(id TEXT PRIMARY KEY, month TEXT, text TEXT, done INTEGER, data TEXT, updated INTEGER)");
        db.execSQL("CREATE TABLE expenses(id TEXT PRIMARY KEY, month TEXT, date TEXT, name TEXT, amount REAL, type TEXT, note TEXT, data TEXT, updated INTEGER)");
        db.execSQL("CREATE TABLE customers(phone_key TEXT PRIMARY KEY, data TEXT, updated INTEGER)");
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY, data TEXT NOT NULL, is_read INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV) { if(oldV<7) db.execSQL("CREATE TABLE IF NOT EXISTS assets(id TEXT PRIMARY KEY, data TEXT NOT NULL)"); if(oldV<2) db.execSQL("CREATE TABLE IF NOT EXISTS reservations(id TEXT PRIMARY KEY, month TEXT, text TEXT, done INTEGER, updated INTEGER)"); if(oldV<3) db.execSQL("ALTER TABLE reservations ADD COLUMN data TEXT"); if(oldV<4) db.execSQL("CREATE TABLE IF NOT EXISTS expenses(id TEXT PRIMARY KEY, month TEXT, date TEXT, name TEXT, amount REAL, type TEXT, note TEXT, data TEXT, updated INTEGER)"); if(oldV<5) db.execSQL("CREATE TABLE IF NOT EXISTS customers(phone_key TEXT PRIMARY KEY, data TEXT, updated INTEGER)"); if(oldV<6) db.execSQL("CREATE TABLE IF NOT EXISTS messages(id TEXT PRIMARY KEY, data TEXT NOT NULL, is_read INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0)"); }
    public synchronized JSONArray assets() {
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT data FROM assets ORDER BY id",null);
        try{while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}catch(JSONException e){throw new IllegalStateException(e);}finally{c.close();}return a;
    }
    public synchronized void saveAsset(JSONObject a) {
        ContentValues v=new ContentValues();v.put("id",a.optString("id"));v.put("data",a.toString());
        getWritableDatabase().insertWithOnConflict("assets",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    /** Delete a never-used physical copy locally and queue the authenticated server deletion. */
    public synchronized void deleteUnusedAsset(JSONObject asset){
        String id=asset.optString("id");
        String blocker=RentalRules.deletionBlocker(asset,allReservations(),allProtocols());
        if(!blocker.isEmpty())throw new IllegalArgumentException(blocker);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            JSONArray pending=queued();
            for(int i=0;i<pending.length();i++){
                JSONObject op=pending.optJSONObject(i);
                if(op!=null&&"asset_save".equals(op.optString("action"))&&id.equals(op.optString("id")))
                    clearQueueItem(op.optString("op_id"));
            }
            boolean queuedDelete=false;
            for(int i=0;i<pending.length();i++){
                JSONObject op=pending.optJSONObject(i);
                if(op!=null&&"asset_delete".equals(op.optString("action"))&&id.equals(op.optString("id")))queuedDelete=true;
            }
            if(!queuedDelete)enqueue("asset_delete",new JSONObject().put("id",id));
            db.delete("assets","id=?",new String[]{id});
            db.setTransactionSuccessful();
        }catch(JSONException e){throw new IllegalStateException(e);}
        finally{db.endTransaction();}
    }
    /** A duplicate label is rejected before a change gets stuck in the offline queue. */
    public synchronized boolean isAssetLabelTaken(String proposed, String excludingId) {
        String label=proposed==null?"":proposed.trim();
        JSONArray assets=assets();
        for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.optJSONObject(i);
            if(asset!=null&&!asset.optString("id").equals(excludingId)
                    &&asset.optString("label").trim().equalsIgnoreCase(label))return true;
        }
        return false;
    }
    /**
     * Repair an unsent asset rename without dropping unrelated offline operations.
     * Every queued save for this copy is corrected, preserving op_id and its position.
     * The local asset and the queue change in a single SQLite transaction.
     */
    public synchronized int repairQueuedAssetLabel(String operationId,String proposed) {
        String label=proposed==null?"":proposed.trim();
        if(label.isEmpty()||label.length()>100)
            throw new IllegalArgumentException("Nazwa egzemplarza musi mieć od 1 do 100 znaków.");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            JSONArray pending=queued();
            JSONObject target=null;
            for(int i=0;i<pending.length();i++){
                JSONObject item=pending.optJSONObject(i);
                if(item!=null&&operationId.equals(item.optString("op_id"))&&"asset_save".equals(item.optString("action"))) {
                    target=item;break;
                }
            }
            if(target==null)throw new IllegalArgumentException("Ta zmiana nie oczekuje już na synchronizację.");
            String assetId=target.optString("id");
            if(assetId.isEmpty())throw new IllegalArgumentException("Brak identyfikatora egzemplarza.");
            if(isAssetLabelTaken(label,assetId))
                throw new IllegalArgumentException("Taka nazwa jest już używana przez inny egzemplarz.");
            int corrected=0;
            for(int i=0;i<pending.length();i++){
                JSONObject item=pending.optJSONObject(i);
                if(item==null||!"asset_save".equals(item.optString("action"))||!assetId.equals(item.optString("id")))continue;
                item.put("label",label);
                ContentValues payload=new ContentValues();payload.put("payload",item.toString());
                db.update("queue",payload,"op_id=?",new String[]{item.optString("op_id")});
                corrected++;
            }
            Cursor c=db.rawQuery("SELECT data FROM assets WHERE id=?",new String[]{assetId});
            try{
                if(!c.moveToFirst())throw new IllegalArgumentException("Brak lokalnego egzemplarza. Nie zmieniono kolejki.");
                JSONObject asset=new JSONObject(c.getString(0));asset.put("label",label);
                ContentValues data=new ContentValues();data.put("data",asset.toString());
                db.update("assets",data,"id=?",new String[]{assetId});
            }finally{c.close();}
            db.setTransactionSuccessful();
            return corrected;
        }catch(JSONException e){throw new IllegalStateException(e);}
        finally{db.endTransaction();}
    }
    public synchronized void replaceAssets(JSONArray a) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{db.delete("assets",null,null);for(int i=0;i<a.length();i++)saveAsset(a.getJSONObject(i));
            JSONArray pending=queued();for(int i=0;i<pending.length();i++){
                JSONObject op=pending.getJSONObject(i);
                if("asset_save".equals(op.optString("action")))saveAsset(op);
                else if("asset_delete".equals(op.optString("action")))
                    db.delete("assets","id=?",new String[]{op.optString("id")});
            }
            db.setTransactionSuccessful();
        }catch(JSONException e){throw new IllegalStateException(e);}finally{db.endTransaction();}
    }
    public synchronized void saveRentalOperation(JSONObject r,String action) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{if("reservation_save".equals(action)){JSONArray pending=queued();for(int i=0;i<pending.length();i++){JSONObject op=pending.getJSONObject(i);if(action.equals(op.optString("action"))&&r.optString("id").equals(op.optString("id")))clearQueueItem(op.optString("op_id"));}}enqueue(action,new JSONObject(r.toString()));saveReservation(r);db.setTransactionSuccessful();}
        catch(JSONException e){throw new IllegalStateException(e);}finally{db.endTransaction();}
    }
    public synchronized void enqueue(String action, JSONObject payload) {
        try {
            String opId = UUID.randomUUID().toString();
            payload.put("op_id", opId); payload.put("action", action);
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
        SQLiteDatabase db=getWritableDatabase();
        HashSet<String> keepIds=new HashSet<>();
        HashSet<String> pendingCreateIds=new HashSet<>();
        for(int i=0;i<a.length();i++) keepIds.add(a.optJSONObject(i)==null?"":a.optJSONObject(i).optString("id"));
        JSONArray pending=queued();
        for(int i=0;i<pending.length();i++) {
            JSONObject op=pending.optJSONObject(i);
            if(op!=null && "create".equals(op.optString("action"))) {
                String id=op.optString("id");
                if(!id.isEmpty()) pendingCreateIds.add(id);
            }
        }
        keepIds.addAll(pendingCreateIds);
        db.beginTransaction();
        try {
            for(int i=0;i<a.length();i++) {
                JSONObject server=a.getJSONObject(i);
                String id=server.optString("id");
                Cursor oldRow=db.rawQuery("SELECT data FROM protocols WHERE id=?",new String[]{id});
                try {
                    if(oldRow.moveToFirst()) {
                        JSONObject local=new JSONObject(oldRow.getString(0));
                        String[] localOnly={"photo_base64","photos_base64","signature_base64","return_signature_base64","return_photos_base64"};
                        for(String key:localOnly) if(!server.has(key)&&local.has(key)) server.put(key,local.get(key));
                    }
                } finally { oldRow.close(); }
                saveProtocol(server);
            }
            Cursor localRows=db.rawQuery("SELECT id FROM protocols",null);
            try {
                while(localRows.moveToNext()) {
                    String localId=localRows.getString(0);
                    if(!keepIds.contains(localId)) db.delete("protocols","id=?",new String[]{localId});
                }
            } finally { localRows.close(); }
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
    public synchronized void deleteProtocol(String id) {
        SQLiteDatabase db=getWritableDatabase();
        db.delete("protocols","id=?",new String[]{id});
    }

    public synchronized void saveReservation(JSONObject r) {
        ContentValues v=new ContentValues(); v.put("id",r.optString("id")); v.put("month",r.optString("month"));
        v.put("text",r.optString("text")); v.put("done",r.optBoolean("done")?1:0); v.put("data",r.toString()); v.put("updated",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("reservations",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized JSONArray allReservations() {
        JSONArray a=new JSONArray();Cursor c=getReadableDatabase().rawQuery("SELECT data,id,month,text,done FROM reservations ORDER BY updated DESC",null);
        try{while(c.moveToNext()){JSONObject x;try{x=c.isNull(0)?new JSONObject():new JSONObject(c.getString(0));}catch(Exception e){x=new JSONObject();}
            if(!x.has("id"))x.put("id",c.getString(1));if(!x.has("month"))x.put("month",c.getString(2));if(!x.has("text"))x.put("text",c.getString(3));if(!x.has("done"))x.put("done",c.getInt(4)==1);a.put(x);}}
        catch(Exception ignored){}finally{c.close();}return a;
    }
    public synchronized JSONArray reservations(String month) {
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT data,id,month,text,done FROM reservations WHERE month=? ORDER BY updated",new String[]{month});
        try { while(c.moveToNext()) { JSONObject x; try{x=c.isNull(0)?new JSONObject():new JSONObject(c.getString(0));}catch(Exception e){x=new JSONObject();}
            if(!x.has("id"))x.put("id",c.getString(1)); if(!x.has("month"))x.put("month",c.getString(2)); if(!x.has("text"))x.put("text",c.getString(3)); if(!x.has("done"))x.put("done",c.getInt(4)==1); a.put(x); } }
        catch(Exception ignored) {} finally { c.close(); } return a;
    }
    public synchronized void deleteReservation(String id) { getWritableDatabase().delete("reservations","id=?",new String[]{id}); }
    public synchronized void replaceServerReservations(JSONArray a) {
        SQLiteDatabase db=getWritableDatabase(); HashSet<String> keep=new HashSet<>(), pendingDelete=new HashSet<>();
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)keep.add(x.optString("id"));}
        JSONArray pending=queued();
        for(int i=0;i<pending.length();i++){JSONObject op=pending.optJSONObject(i);if(op==null)continue;
            String action=op.optString("action"), id=op.optString("id");
            if("reservation_save".equals(action)||"reservation_settle".equals(action))keep.add(id);
            if("reservation_delete".equals(action))pendingDelete.add(id);
        }
        keep.removeAll(pendingDelete);
        db.beginTransaction();
        try {
            for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&!pendingDelete.contains(x.optString("id")))saveReservation(x);}
            for(String id:pendingDelete)db.delete("reservations","id=?",new String[]{id});
            Cursor rows=db.rawQuery("SELECT id FROM reservations",null);
            try { while(rows.moveToNext()){String id=rows.getString(0);if(!keep.contains(id))db.delete("reservations","id=?",new String[]{id});} }
            finally { rows.close(); }
            for(int i=0;i<pending.length();i++){JSONObject op=pending.optJSONObject(i);if(op!=null&&("reservation_save".equals(op.optString("action"))||"reservation_settle".equals(op.optString("action"))))saveReservation(op);}
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }


    public synchronized void saveExpense(JSONObject e) {
        ContentValues v=new ContentValues(); v.put("id",e.optString("id")); v.put("month",e.optString("month"));
        v.put("date",e.optString("date")); v.put("name",e.optString("name")); v.put("amount",e.optDouble("amount"));
        v.put("type",e.optString("type","expense")); v.put("note",e.optString("note")); v.put("data",e.toString()); v.put("updated",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("expenses",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized JSONArray allExpenses() { return expenses(null); }
    public synchronized JSONArray expenses(String month) {
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT data,id,month,date,name,amount,type,note FROM expenses"+(month==null?"":" WHERE month=?")+" ORDER BY date DESC,updated DESC",month==null?null:new String[]{month});
        try { while(c.moveToNext()) { JSONObject x; try{x=c.isNull(0)?new JSONObject():new JSONObject(c.getString(0));}catch(Exception ex){x=new JSONObject();}
            if(!x.has("id"))x.put("id",c.getString(1)); if(!x.has("month"))x.put("month",c.getString(2));
            if(!x.has("date"))x.put("date",c.getString(3)); if(!x.has("name"))x.put("name",c.getString(4));
            if(!x.has("amount"))x.put("amount",c.getDouble(5)); if(!x.has("type"))x.put("type",c.getString(6));
            if(!x.has("note"))x.put("note",c.getString(7)); a.put(x); } }
        catch(Exception ignored) {} finally { c.close(); } return a;
    }
    public synchronized void deleteExpense(String id) { getWritableDatabase().delete("expenses","id=?",new String[]{id}); }
    public synchronized void replaceServerExpenses(JSONArray a) {
        SQLiteDatabase db=getWritableDatabase(); HashSet<String> keep=new HashSet<>(), pendingDelete=new HashSet<>();
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)keep.add(x.optString("id"));}
        JSONArray pending=queued();
        for(int i=0;i<pending.length();i++){JSONObject op=pending.optJSONObject(i);if(op==null)continue;
            String action=op.optString("action"), id=op.optString("id");
            if("expense_save".equals(action))keep.add(id);
            if("expense_delete".equals(action))pendingDelete.add(id);
        }
        keep.removeAll(pendingDelete); db.beginTransaction();
        try {
            for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&!pendingDelete.contains(x.optString("id")))saveExpense(x);}
            for(String id:pendingDelete)db.delete("expenses","id=?",new String[]{id});
            Cursor rows=db.rawQuery("SELECT id FROM expenses",null);
            try { while(rows.moveToNext()){String id=rows.getString(0);if(!keep.contains(id))db.delete("expenses","id=?",new String[]{id});} } finally { rows.close(); }
            // Preserve pending offline changes when the server snapshot is older.
            for(int i=0;i<pending.length();i++){
                JSONObject op=pending.optJSONObject(i);
                if(op!=null&&"expense_save".equals(op.optString("action")))saveExpense(op);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    /** Keep local read states while replacing the server-backed inbox. */
    public synchronized JSONArray replaceMessages(JSONArray incoming) {
        SQLiteDatabase db=getWritableDatabase();
        JSONArray fresh=new JSONArray();
        HashSet<String> current=new HashSet<>();
        db.beginTransaction();
        try {
            for(int i=0;i<incoming.length();i++) {
                JSONObject item=incoming.optJSONObject(i);
                if(item==null)continue;
                String id=item.optString("id","").trim();
                if(id.isEmpty()||!current.add(id))continue;
                ContentValues initial=new ContentValues();
                initial.put("id",id);initial.put("data",item.toString());
                initial.put("is_read",0);initial.put("position",i);
                long inserted=db.insertWithOnConflict("messages",null,initial,SQLiteDatabase.CONFLICT_IGNORE);
                if(inserted!=-1)fresh.put(item);
                else {
                    ContentValues updated=new ContentValues();
                    updated.put("data",item.toString());updated.put("position",i);
                    db.update("messages",updated,"id=?",new String[]{id});
                }
            }
            Cursor old=db.rawQuery("SELECT id FROM messages",null);
            try{while(old.moveToNext())if(!current.contains(old.getString(0)))
                db.delete("messages","id=?",new String[]{old.getString(0)});
            }finally{old.close();}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return fresh;
    }
    public synchronized JSONArray allMessages() {
        JSONArray items=new JSONArray();
        Cursor c=getReadableDatabase().rawQuery("SELECT data,is_read FROM messages ORDER BY position DESC",null);
        try {
            while(c.moveToNext()) {
                try {JSONObject o=new JSONObject(c.getString(0));
                    o.put("is_read",c.getInt(1)!=0);items.put(o);
                }catch(Exception ignored){}
            }
        }finally{c.close();}
        return items;
    }
    public synchronized int unreadMessagesCount(){
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages WHERE is_read=0",null);
        try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}
    }
    public synchronized void markMessageRead(String id){
        ContentValues v=new ContentValues();v.put("is_read",1);
        getWritableDatabase().update("messages",v,"id=?",new String[]{id});
    }
    public synchronized void clearMessages(){
        getWritableDatabase().delete("messages",null,null);
    }

    public synchronized void saveCustomerDraft(JSONObject customer) {
        String key=customer.optString("phone_key","");
        if(key.isEmpty()) throw new IllegalArgumentException("Brak identyfikatora klienta.");
        ContentValues values=new ContentValues();
        values.put("phone_key",key);
        values.put("data",customer.toString());
        values.put("updated",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("customers",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized void replaceServerCustomers(JSONArray a) {
        SQLiteDatabase db=getWritableDatabase();
        // Keep locally edited names and notes visible until queued operations reach the server.
        HashMap<String,JSONObject> pendingProfiles=new HashMap<>();
        JSONArray pending=queued();
        for(int i=0;i<pending.length();i++){
            JSONObject operation=pending.optJSONObject(i);
            if(operation==null||!"customer_save".equals(operation.optString("action")))continue;
            String key=operation.optString("id","");
            if(!key.isEmpty())pendingProfiles.put(key,operation);
        }
        HashMap<String,JSONObject> previous=new HashMap<>();
        if(!pendingProfiles.isEmpty()){
            Cursor old=db.rawQuery("SELECT phone_key,data FROM customers",null);
            try {
                while(old.moveToNext()){
                    String key=old.getString(0);
                    if(pendingProfiles.containsKey(key)){
                        try{previous.put(key,new JSONObject(old.getString(1)));}catch(Exception ignored){}
                    }
                }
            }finally{old.close();}
        }
        HashSet<String> seen=new HashSet<>();
        db.beginTransaction();
        try {
            db.delete("customers",null,null);
            if(a!=null)for(int i=0;i<a.length();i++) {
                JSONObject source=a.optJSONObject(i);if(source==null)continue;
                String key=source.optString("phone_key","");if(key.isEmpty())continue;
                try {
                    JSONObject updated=new JSONObject(source.toString());
                    JSONObject draft=pendingProfiles.get(key);
                    if(draft!=null){
                        updated.put("name",draft.optString("name",""));
                        updated.put("notes",draft.optString("notes",""));
                    }
                    ContentValues v=new ContentValues();v.put("phone_key",key);v.put("data",updated.toString());
                    v.put("updated",System.currentTimeMillis());
                    db.insertWithOnConflict("customers",null,v,SQLiteDatabase.CONFLICT_REPLACE);
                    seen.add(key);
                }catch(Exception ignored){}
            }
            for(String key:pendingProfiles.keySet()){
                if(seen.contains(key))continue;
                JSONObject draft=pendingProfiles.get(key);
                JSONObject restored=previous.get(key);
                if(restored==null)restored=new JSONObject();
                try{
                    restored.put("phone_key",key);
                    restored.put("phone",draft.optString("phone",""));
                    restored.put("name",draft.optString("name",""));
                    restored.put("notes",draft.optString("notes",""));
                    saveCustomerDraft(restored);
                }catch(Exception ignored){}
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized JSONArray customers() {
        JSONArray a=new JSONArray(); Cursor c=getReadableDatabase().rawQuery("SELECT data FROM customers ORDER BY phone_key",null);
        try { while(c.moveToNext()) a.put(new JSONObject(c.getString(0))); } catch(Exception ignored) {} finally { c.close(); }
        return a;
    }

    public synchronized int pendingCount() {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM queue",null); try { return c.moveToFirst()?c.getInt(0):0; } finally { c.close(); }
    }
}