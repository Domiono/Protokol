package pl.rentmor.protokol;
import android.app.Activity;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class OfferContentEditorTest {
 View named(View v,String s){if(v instanceof TextView&&s.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=named(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null;}
 @Test public void existingContentEditableRowsRemovableAndDraftRoundTrips()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).setup().get();LinearLayout root=new LinearLayout(a);
  JSONObject content=new JSONObject().put("title","Poprzedni tytuł").put("questions",new JSONArray().put(new JSONObject().put("question","Pytanie?").put("answer","Odpowiedź"))).put("related",new JSONArray());
  JSONObject source=new JSONObject().put("offer_revision","original-revision").put("offer_content",content);
  OfferContentEditor editor=new OfferContentEditor(a,root,source);int[] changes={0};editor.setOnChange(()->changes[0]++);
  ((EditText)named(root,"Poprzedni tytuł")).setText("Nowy tytuł");assertEquals(1,changes[0]);
  named(root,"DODAJ PYTANIE").performClick();assertEquals(2,editor.snapshot().getJSONObject("offer_content").getJSONArray("questions").length());
  named(root,"USUŃ POZYCJĘ").performClick();assertEquals(1,editor.snapshot().getJSONObject("offer_content").getJSONArray("questions").length());
  named(root,"DODAJ LINK").performClick();assertEquals(1,editor.snapshot().getJSONObject("offer_content").getJSONArray("related").length());
  JSONObject draft=editor.snapshot();LinearLayout restoredRoot=new LinearLayout(a);OfferContentEditor restored=new OfferContentEditor(a,restoredRoot,source);restored.restore(draft);
  JSONObject operation=new JSONObject();restored.applyTo(operation);assertEquals("original-revision",operation.getString("offer_revision"));assertEquals("Nowy tytuł",operation.getJSONObject("offer_content").getString("title"));assertEquals(draft.toString(),restored.snapshot().toString());a.finish();
 }
 @Test public void clearingFieldDoesNotRestoreDefault()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).setup().get();LinearLayout root=new LinearLayout(a);OfferContentEditor e=new OfferContentEditor(a,root,new JSONObject().put("offer_revision","r").put("offer_content",new JSONObject().put("title","Usuń mnie")));
  ((EditText)named(root,"Usuń mnie")).setText("");assertEquals("",e.snapshot().getJSONObject("offer_content").getString("title"));a.finish();
 }
}
