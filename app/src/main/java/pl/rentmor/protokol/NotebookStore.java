package pl.rentmor.protokol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Device-local notebook; deliberately independent of rental sync snapshots. */
final class NotebookStore extends SQLiteOpenHelper {
    static final String[] CATEGORIES={"Zakupy","Umowy","Inne"};
    static final int MAX_LENGTH=2000;
    static final class Entry {
        final String id,text,category;
        final boolean done;
        Entry(String id,String text,String category,boolean done){
            this.id=id;this.text=text;this.category=category;this.done=done;
        }
    }
    NotebookStore(Context context){super(context.getApplicationContext(),"rentmor_notebook.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE notes(id TEXT PRIMARY KEY, text TEXT NOT NULL, category TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    synchronized String save(String id,String text,String category){
        text=text==null?"":text.trim();
        if(text.isEmpty()||text.length()>MAX_LENGTH)throw new IllegalArgumentException("Wpisz od 1 do 2000 znaków.");
        boolean known=false;for(String item:CATEGORIES)if(item.equals(category))known=true;
        if(!known)throw new IllegalArgumentException("Wybierz kategorię.");
        ContentValues values=new ContentValues();values.put("text",text);values.put("category",category);
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
        if(getWritableDatabase().update("notes",values,"id=?",new String[]{id})!=1)
            throw new IllegalStateException("Ten wpis już nie istnieje.");
    }
    synchronized void delete(String id){getWritableDatabase().delete("notes","id=?",new String[]{id});}
    synchronized int pendingCount(){
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM notes WHERE done=0",null)){
            cursor.moveToFirst();return cursor.getInt(0);
        }
    }
    synchronized List<Entry> entries(String category,boolean done){
        List<Entry> result=new ArrayList<>();
        String selection="done=?"+(category==null?"":" AND category=?");
        String[] args=category==null?new String[]{done?"1":"0"}:new String[]{done?"1":"0",category};
        try(Cursor cursor=getReadableDatabase().query("notes",new String[]{"id","text","category","done"},selection,args,null,null,"created DESC, rowid DESC")){
            while(cursor.moveToNext())result.add(new Entry(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getInt(3)==1));
        }
        return result;
    }
}
