package pl.rentmor.protokol;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class NotebookStoreTest {
    private NotebookStore store;
    @Before public void setup(){RuntimeEnvironment.getApplication().deleteDatabase("rentmor_notebook.db");store=new NotebookStore(RuntimeEnvironment.getApplication());}
    @After public void cleanup(){store.close();RuntimeEnvironment.getApplication().deleteDatabase("rentmor_notebook.db");}
    @Test public void savedNotesSurviveReopeningAndCanBeCompletedEditedRestoredAndDeleted(){
        String id=store.save(null,"  Kupić worki\ndo odkurzacza  ","Zakupy");
        store.save(null,"Wydrukować umowy","Umowy");
        store.close();store=new NotebookStore(RuntimeEnvironment.getApplication());
        assertEquals(2,store.pendingCount());
        assertEquals("Kupić worki\ndo odkurzacza",store.entries("Zakupy",false).get(0).text);
        store.setDone(id,true);
        assertEquals(1,store.pendingCount());
        assertEquals(0,store.entries("Zakupy",false).size());
        store.save(id,"Kupić 10 worków","Inne");
        assertEquals(id,store.entries("Inne",true).get(0).id);
        assertTrue(store.entries("Inne",true).get(0).done);
        store.setDone(id,false);
        assertEquals(2,store.pendingCount());
        store.delete(id);
        assertEquals(1,store.pendingCount());
        assertEquals(0,store.entries("Inne",false).size());
        assertEquals(1,store.entries("Umowy",false).size());
    }
    @Test public void invalidInputsAndMissingEditsNeverReplaceExistingNotes(){
        store.save(null,"Wydrukować umowy","Umowy");
        for(String value:new String[]{" ",new String(new char[2001]).replace('\0','a')}){
            try{store.save(null,value,"Inne");fail();}catch(IllegalArgumentException expected){}
        }
        try{store.save(null,"Treść","Nieznana");fail();}catch(IllegalArgumentException expected){}
        try{store.save("missing","Treść","Inne");fail();}catch(IllegalStateException expected){}
        assertEquals(1,store.pendingCount());
        assertEquals("Wydrukować umowy",store.entries(null,false).get(0).text);
    }
}
