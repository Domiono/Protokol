package pl.rentmor.protokol;
import android.app.*;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class AiEquipmentKnowledgeTest extends AiKnowledgePageTest {
 static class EquipmentSync extends FakeSync {
  final Map<String,String> notes=new HashMap<>();String lastEquipment="";
  EquipmentSync(Activity a){super(a);notes.put("","Dotychczasowy tekst");notes.put("Agregat WT20i","Farby wodne");notes.put("Puzzi 10/1","Pranie");}
  JSONObject result(String kind,String equipment)throws Exception{return note(kind,notes.getOrDefault(equipment,"")).put("equipment",equipment).put("equipment_options",new JSONArray().put("Agregat WT20i").put("Puzzi 10/1"));}
  @Override public JSONObject fetchAiKnowledge(String kind,String equipment)throws Exception{reads++;lastEquipment=equipment;return result(kind,equipment);}
  @Override public JSONObject saveAiKnowledge(String kind,String equipment,String text,String revision)throws Exception{writes++;notes.put(equipment,text);return result(kind,equipment);}
 }
 void choose(LinearLayout root,String current,int index)throws Exception{
  named(root,"Sprzęt: "+current+" ▼").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();ListView l=d.getListView();l.performItemClick(l.getAdapter().getView(index,null,l),index,index);settle();
 }
 @Test public void equipmentEditorLoadsAndSavesOnlyChosenNoteAndProtectsDraft()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).setup().get();a.getSharedPreferences("rentmor",0).edit().putString("role","admin").commit();LinearLayout r=new LinearLayout(a);a.setContentView(r);EquipmentSync s=new EquipmentSync(a);new AiKnowledgePage(a,r,s,()->{}).show();settle();
  assertEquals("Dotychczasowy tekst",input(r).getText().toString());choose(r,"Zasady ogólne",1);assertEquals("Farby wodne",input(r).getText().toString());
  input(r).setText("Farby wodne, dysza517");named(r,"Zapisz i udostępnij Mortii").performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle();
  assertEquals("Farby wodne, dysza517",s.notes.get("Agregat WT20i"));assertEquals("Dotychczasowy tekst",s.notes.get(""));assertEquals("Pranie",s.notes.get("Puzzi 10/1"));
  input(r).setText("Niezapisana zmiana");choose(r,"Agregat WT20i",2);AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();settle();assertEquals("Agregat WT20i",s.lastEquipment);assertEquals("Niezapisana zmiana",input(r).getText().toString());a.finish();
 }
}
