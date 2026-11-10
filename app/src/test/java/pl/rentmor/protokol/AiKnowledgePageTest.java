package pl.rentmor.protokol;
import android.app.*;
import android.view.*;
import android.widget.*;
import android.os.Looper;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class AiKnowledgePageTest {
 static class FakeSync extends SyncManager {
  volatile int reads=0,writes=0;volatile boolean fail=false;String draft="";
  FakeSync(Activity a){super(a,null);}
  JSONObject note(String kind,String text)throws Exception{return new JSONObject().put("ok",true).put("role","admin").put("audience",kind).put("knowledge",new JSONObject().put("text",text).put("revision",new String(new char[64]).replace('\0','a')).put("updated_at",JSONObject.NULL));}
  @Override public JSONObject fetchAiKnowledge(String kind)throws Exception{reads++;return note(kind,kind.equals("public")?"Publiczna zasada":"Prywatna zasada");}
  @Override public JSONObject saveAiKnowledge(String kind,String text,String rev)throws Exception{writes++;draft=text;if(fail)throw new java.io.IOException("Konflikt — pobierz aktualną wiedzę.");return note(kind,text);}
 }
 View named(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=named(((ViewGroup)v).getChildAt(i),s);if(r!=null)return r;}return null;}
 EditText input(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText r=input(((ViewGroup)v).getChildAt(i));if(r!=null)return r;}return null;}
 void settle()throws Exception{for(int i=0;i<30;i++){Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();}}
 @Test public void employeeNeverLoadsEditorOrCallsServer(){Activity a=Robolectric.buildActivity(Activity.class).setup().get();a.getSharedPreferences("rentmor",0).edit().putString("role","assistant").commit();LinearLayout r=new LinearLayout(a);FakeSync s=new FakeSync(a);boolean[] home={false};new AiKnowledgePage(a,r,s,()->home[0]=true).show();assertTrue(home[0]);assertNull(input(r));assertEquals(0,s.reads);a.finish();}
 @Test public void publicPublicationConflictKeepsDraftAndSwitchNeedsConfirmation()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).setup().get();a.getSharedPreferences("rentmor",0).edit().putString("role","admin").commit();LinearLayout r=new LinearLayout(a);a.setContentView(r);FakeSync s=new FakeSync(a);AiKnowledgePage p=new AiKnowledgePage(a,r,s,()->{});p.show();settle();
  assertEquals("Publiczna zasada",input(r).getText().toString());input(r).setText("Nowa zasada");named(r,"Zapisz i udostępnij Mortii").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,s.writes);
  AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle();assertEquals(1,s.writes);assertEquals("Nowa zasada",s.draft);
  s.fail=true;input(r).setText("Nie utracić tekstu");named(r,"Zapisz i udostępnij Mortii").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle();assertEquals("Nie utracić tekstu",input(r).getText().toString());assertEquals(2,s.writes);
  named(r,"Otwórz wiedzę prywatną").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();d=ShadowAlertDialog.getLatestAlertDialog();assertEquals(1,s.reads);d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals("Nie utracić tekstu",input(r).getText().toString());
  input(r).setText(new String(new char[6001]).replace('\0','ą'));assertFalse(named(r,"Zapisz i udostępnij Mortii").isEnabled());a.finish();
 }
 @Test public void responseRoleIsRequired()throws Exception{try{SyncManager.requireAiAdmin(new JSONObject().put("role","assistant"));fail();}catch(SecurityException expected){}assertNotNull(SyncManager.requireAiAdmin(new JSONObject().put("role","admin")));}
}
