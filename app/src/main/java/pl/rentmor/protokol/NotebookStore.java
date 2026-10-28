package pl.rentmor.protokol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Offline-first notes with durable per-change pending IDs and server revisions. */
final class NotebookStore extends SQLiteOpenHelper {
    static final String[] CATEGORIES={"Zakupy","Umowy","Inne"};
    static final int MAX_LENGTH=2000;
    static final class Entry {
        final String id,text,category;
        final boolean done,conflict,remoteDeleted;
        final String remoteText,remoteCategory;
        Entry(String id,String text,String category,boolean done){this(id,text,category,done,false,"","",false);}
        Entry(String id,String text,String category,boolean done,boolean conflict,String remoteText,String remoteCategory,boolean remoteDeleted){
            this.id=id;this.text=text;this.category=category;this.done=done;this.conflict=conflict;
            this.remoteText=remoteText;this.remoteCategory=remoteCategory;this.remoteDeleted=remoteDeleted;
        }
    }
    NotebookStore(Context context){super(context.getApplicationContext(),"rentmor_notebook.db",null,2);}
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes(id TEXT PRIMARY KEY, text TEXT NOT NULL, category TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1, conflict INTEGER NOT NULL DEFAULT 0, server_revision INTEGER NOT NULL DEFAULT 0, op_id TEXT NOT NULL DEFAULT '', remote_text TEXT NOT NULL DEFAULT '', remote_category TEXT NOT NULL DEFAULT '', remote_deleted INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2){
            db.execSQL("ALTER TABLE notes ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE notes ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE notes ADD COLUMN conflict INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE notes ADD COLUMN server_revision INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE notes ADD COLUMN op_id TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE notes ADD COLUMN remote_text TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE notes ADD COLUMN remote_category TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE notes ADD COLUMN remote_deleted INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE notes SET op_id=lower(hex(randomblob(4)))||'-'||lower(hex(randomblob(2)))||'-4'||substr(lower(hex(randomblob(2))),2)||'-a'||substr(lower(hex(randomblob(2))),2)||'-'||lower(hex(randomblob(6))) WHERE op_id=''");
        }
    }
    private static boolean known(String category){
        for(String c:CATEGORIES)if(c.equals(category))return true;
        return false;
    }
    private static void markEdited(ContentValues values) {
        values.put("dirty",1);values.put("conflict",0);
        values.put("op_id",UUID.randomUUID().toString());
        values.put("remote_text","");values.put("remote_category","");values.put("remote_deleted",0);
    }
    synchronized String save(String id,String text,String category){
        text=text==null?"":text.trim();
        if(text.isEmpty()||text.length()>MAX_LENGTH)throw new IllegalArgumentException("Wpisz od 1 do 2000 znaków.");
        if(!known(category))throw new IllegalArgumentException("Wybierz kategorię.");
        ContentValues values=new ContentValues();values.put("text",text);values.put("category",category);values.put("deleted",0);
        markEdited(values);
        if(id==null){
            id=UUID.randomUUID().toString();values.put("id",id);values.put("created",System.currentTimeMillis());
            getWritableDatabase().insertOrThrow("notes",null,values);
        }else if(getWritableDatabase().update("notes",values,"id=?",new String[]{id})!=1){
            throw new IllegalStateException("Ten wpis już nie istnieje.");
        }
        return id;
    }
    synchronized void setDone(String id,boolean done){
        ContentValues values=new ContentValues();values.put("done",done?1:0);
        markEdited(values);
        if(getWritableDatabase().update("notes",values,"id=? AND deleted=0",new String[]{id})!=1)
            throw new IllegalStateException("Ten wpis już nie istnieje.");
    }
    synchronized void delete(String id){
        ContentValues values=new ContentValues();values.put("deleted",1);
        markEdited(values);
        if(getWritableDatabase().update("notes",values,"id=? AND deleted=0",new String[]{id})!=1)
            throw new IllegalStateException("Ten wpis już nie istnieje.");
    }
    synchronized int pendingCount(){
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM notes WHERE done=0 AND deleted=0",null)){
            cursor.moveToFirst();return cursor.getInt(0);
        }
    }
    synchronized int conflicts(){
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM notes WHERE conflict=1",null)){
            cursor.moveToFirst();return cursor.getInt(0);
        }
    }
    synchronized List<Entry> entries(String category,boolean done){
        List<Entry> result=new ArrayList<>();
        String selection="deleted=0 AND done=?"+(category==null?"":" AND category=?");
        String[] args=category==null?new String[]{done?"1":"0"}:new String[]{done?"1":"0",category};
        try(Cursor cursor=getReadableDatabase().query("notes",new String[]{"id","text","category","done","conflict","remote_text","remote_category","remote_deleted"},selection,args,null,null,"created DESC, rowid DESC")){
            while(cursor.moveToNext())result.add(new Entry(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getInt(3)==1,cursor.getInt(4)==1,cursor.getString(5),cursor.getString(6),cursor.getInt(7)==1));
        }
        return result;
    }
    synchronized JSONArray pending() throws Exception {
        JSONArray result=new JSONArray();
        try(Cursor cursor=getReadableDatabase().query("notes",new String[]{"id","op_id","text","category","done","deleted","server_revision"},
                "dirty=1 AND conflict=0",null,null,null,"created ASC","100")){
            while(cursor.moveToNext()){
                JSONObject obj=new JSONObject();
                obj.put("id",cursor.getString(0));obj.put("op_id",cursor.getString(1));
                obj.put("text",cursor.getString(2));obj.put("category",cursor.getString(3));
                obj.put("done",cursor.getInt(4)==1);obj.put("deleted",cursor.getInt(5)==1);
                obj.put("base_revision",cursor.getLong(6));
                result.put(obj);
            }
        }
        return result;
    }
    synchronized void acknowledgements(JSONArray operations,JSONArray results) throws Exception {
        if(operations.length()!=results.length())throw new IllegalStateException("Brak potwierdzeń notatnika");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            for(int i=0;i<results.length();i++){
                JSONObject result=results.getJSONObject(i),op=operations.getJSONObject(i);
                if(!op.optString("op_id").equals(result.optString("op_id")))throw new IllegalStateException("Niepoprawne potwierdzenie notatki");
                ContentValues update=new ContentValues();
                update.put("server_revision",result.getLong("revision"));
                if(result.optBoolean("ok")) {update.put("dirty",0);update.put("conflict",0);update.put("remote_text","");update.put("remote_category","");update.put("remote_deleted",0);}
                else if(result.optBoolean("conflict")){update.put("conflict",1);}
                else throw new IllegalStateException("Serwer odrzucił zmianę notatki");
                db.update("notes",update,"id=? AND op_id=?",new String[]{op.getString("id"),op.getString("op_id")});
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized void mergeRemote(JSONArray remote) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            for(int i=0;i<remote.length();i++){
                JSONObject item=remote.getJSONObject(i);
                String id=item.getString("id"),category=item.getString("category"),body=item.getString("text");
                if(!id.matches("[a-f0-9-]{36}") || !known(category) || body.length()>MAX_LENGTH)throw new IllegalStateException("Niepoprawna notatka na serwerze");
                long revision=item.getLong("revision");
                boolean found=false,dirty=false;long previous=0;
                try(Cursor cur=db.query("notes",new String[]{"dirty","server_revision"},"id=?",new String[]{id},null,null,null)){
                    if(cur.moveToFirst()){found=true;dirty=cur.getInt(0)==1;previous=cur.getLong(1);}
                }
                ContentValues values=new ContentValues();
                values.put("server_revision",revision);
                if(found && dirty) {
                    if(previous!=revision){
                        values.put("conflict",1);
                        values.put("remote_text",body);values.put("remote_category",category);
                        values.put("remote_deleted",item.getBoolean("deleted")?1:0);
                    }
                    db.update("notes",values,"id=?",new String[]{id});
                    continue;
                }
                values.put("text",body);values.put("category",category);
                values.put("done",item.getBoolean("done")?1:0);values.put("deleted",item.getBoolean("deleted")?1:0);
                values.put("dirty",0);values.put("conflict",0);values.put("op_id","");
                values.put("remote_text","");values.put("remote_category","");values.put("remote_deleted",0);
                if(found)db.update("notes",values,"id=?",new String[]{id});
                else{values.put("id",id);values.put("created",System.currentTimeMillis());db.insertOrThrow("notes",null,values);}
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
