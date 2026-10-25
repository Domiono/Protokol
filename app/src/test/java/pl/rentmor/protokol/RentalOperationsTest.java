package pl.rentmor.protokol;
import android.app.Activity;
import android.view.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.time.LocalDate;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class RentalOperationsTest {
 @Test public void operationalScreensRenderAndPendingEditsSurviveRefresh()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).setup().get();
  LocalStore store=new LocalStore(a);
  JSONObject asset=new JSONObject().put("id","a").put("equipment","Puzzi").put("label","Puzzi 01");store.saveAsset(asset);
  JSONObject r=new JSONObject().put("id","r").put("equipment","Puzzi").put("asset_id","a").put("month","2030-10").put("start_date","2030-10-25").put("end_date","2030-10-27");store.saveReservation(r);
  RentalOperations ops=new RentalOperations(a,store,new RentalOperations.Host(){public void home(){}public void issue(JSONObject r){}public void protocol(JSONObject p){}public void inbox(){}public void sync(){}},true);
  ops.today();checkLayout(a);ops.active();checkLayout(a);ops.calendar(LocalDate.of(2030,10,25));checkLayout(a);ops.assetDetails(asset);checkLayout(a);ops.rentalForm(r);checkLayout(a);ops.extend(r);checkLayout(a);ops.settlement(r);checkLayout(a);
  JSONObject change=new JSONObject(r.toString()).put("end_date","2030-10-29");store.saveRentalOperation(change,"reservation_save");store.replaceServerReservations(new JSONArray().put(r));assertEquals("2030-10-29",store.allReservations().getJSONObject(0).getString("end_date"));
  change.put("end_date","2030-10-30");store.saveRentalOperation(change,"reservation_save");assertEquals(1,store.pendingCount());
  store.close();
 }
 @Test public void quickServiceTogglePreservesAssetHistoryAndWarnsAboutExistingBookings()throws Exception{
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  LocalStore store=new LocalStore(activity);
  JSONObject asset=new JSONObject().put("id","vac1").put("equipment","Odkurzacz przemysłowy")
      .put("label","Odkurzacz 01").put("asset_kind","vacuum")
      .put("service_every",5).put("next_service","2030-12-30")
      .put("repairs",new JSONArray().put(new JSONObject().put("id","repair1").put("note","Filtr")));
  store.saveAsset(asset);
  String day=LocalDate.now().plusDays(2).toString();
  store.saveReservation(new JSONObject().put("id","set1").put("equipment","Żyrafa + odkurzacz")
      .put("asset_id","g1").put("vacuum_asset_id","vac1")
      .put("start_date",day).put("end_date",day).put("month",day.substring(0,7)));
  final int[] sync={0};
  RentalOperations ops=new RentalOperations(activity,store,host(sync),true);
  LocalDate week=LocalDate.now();
  ops.calendar(week);
  assertNotNull(findText(activity.findViewById(android.R.id.content),"Awaria / wyłącz"));
  assertNotNull(findText(activity.findViewById(android.R.id.content),"Edytuj"));
  assertNotNull(findText(activity.findViewById(android.R.id.content),"Usuń egzemplarz"));
  ops.confirmServiceChange(asset,week);
  android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertNotNull(dialog);assertTrue(((android.widget.TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("1 aktywnych"));
  assertFalse(store.assets().getJSONObject(0).optBoolean("in_service"));
  dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
  org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  JSONObject disabled=store.assets().getJSONObject(0);
  assertTrue(disabled.optBoolean("in_service"));assertEquals("vacuum",disabled.getString("asset_kind"));
  assertEquals(1,disabled.getJSONArray("repairs").length());
  assertEquals(1,sync[0]);assertEquals(1,store.pendingCount());
  assertNotNull(findText(activity.findViewById(android.R.id.content),"AWARIA / SERWIS"));
  assertNotNull(findText(activity.findViewById(android.R.id.content),"Przywróć do wynajmu"));
  store.replaceAssets(new JSONArray().put(asset));
  assertTrue(store.assets().getJSONObject(0).optBoolean("in_service"));
  ops.confirmServiceChange(disabled,week);
  assertFalse(store.assets().getJSONObject(0).optBoolean("in_service"));
  assertEquals(2,sync[0]);assertEquals(1,store.allReservations().length());
  store.close();
 }
 @Test public void deleteUnusedAssetAndKeepDeletionAcrossServerRefresh()throws Exception{
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  LocalStore store=new LocalStore(activity);
  JSONObject unused=new JSONObject().put("id","never-used").put("equipment","Puzzi")
      .put("label","Puzzi 04").put("asset_kind","standard");
  store.saveAsset(unused);
  final int[] sync={0};RentalOperations ops=new RentalOperations(activity,store,host(sync),true);
  ops.calendar(LocalDate.now());
  ops.confirmAssetDelete(unused,LocalDate.now());
  android.app.AlertDialog dlg=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertNotNull(dlg);assertTrue(((android.widget.TextView)dlg.findViewById(android.R.id.message)).getText().toString().contains("trwałe usunięcie"));
  assertEquals(1,store.assets().length());
  dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
  org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  assertEquals(0,store.assets().length());assertEquals(1,store.pendingCount());assertEquals(1,sync[0]);
  store.replaceAssets(new JSONArray().put(unused));
  assertEquals(0,store.assets().length()); // Pending deletion must not resurrect locally.
  assertEquals("asset_delete",store.queued().getJSONObject(0).getString("action"));
  store.close();
 }
 @Test public void usedCopiesCannotBeRemovedEvenAfterSettlementOrWhenOnlyInProtocol()throws Exception{
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  LocalStore store=new LocalStore(activity);
  JSONObject asset=new JSONObject().put("id","used").put("equipment","Odkurzacz przemysłowy").put("label","Odkurzacz 01");
  store.saveAsset(asset);
  store.saveReservation(new JSONObject().put("id","historical").put("equipment","Żyrafa + odkurzacz")
      .put("vacuum_asset_id","used").put("settled",true));
  RentalOperations ops=new RentalOperations(activity,store,host(new int[1]),true);
  ops.confirmAssetDelete(asset,LocalDate.now());
  android.app.AlertDialog dlg=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertNotNull(dlg);assertTrue(((android.widget.TextView)dlg.findViewById(android.R.id.message)).getText().toString().contains("historię"));
  assertEquals(1,store.assets().length());
  assertEquals(0,store.pendingCount());
  JSONObject fresh=new JSONObject().put("id","protocol-unit").put("equipment","Puzzi");
  assertFalse(RentalRules.deletionBlocker(fresh,new JSONArray(),
      new JSONArray().put(new JSONObject().put("asset_id","protocol-unit"))).isEmpty());
  JSONObject repaired=new JSONObject().put("id","repaired").put("repairs",new JSONArray().put(new JSONObject()));
  assertFalse(RentalRules.deletionBlocker(repaired,new JSONArray(),new JSONArray()).isEmpty());
  store.close();
 }
 @Test public void assistantCannotOperateQuickActions()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).setup().get();
  LocalStore store=new LocalStore(a);
  JSONObject asset=new JSONObject().put("id","a").put("equipment","Puzzi").put("label","Puzzi 01");
  store.saveAsset(asset);
  RentalOperations ops=new RentalOperations(a,store,host(new int[1]),false);
  ops.calendar(LocalDate.now());
  assertNull(findText(a.findViewById(android.R.id.content),"Usuń egzemplarz"));
  assertNull(findText(a.findViewById(android.R.id.content),"Awaria / wyłącz"));
  ops.confirmAssetDelete(asset,LocalDate.now());
  ops.confirmServiceChange(asset,LocalDate.now());
  assertEquals(1,store.assets().length());assertEquals(0,store.pendingCount());
  store.close();
 }
 private RentalOperations.Host host(int[] sync){
  return new RentalOperations.Host(){public void home(){}public void issue(JSONObject r){}
   public void protocol(JSONObject p){}public void inbox(){}public void sync(){sync[0]++;}};
 }
 private View findText(View v,String needle){
  if(v instanceof android.widget.TextView&&((android.widget.TextView)v).getText().toString().contains(needle))return v;
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){
   View found=findText(g.getChildAt(i),needle);if(found!=null)return found;
  }}
  return null;
 }
 private void checkLayout(Activity a){View v=a.findViewById(android.R.id.content);v.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));v.layout(0,0,1080,1920);assertTrue(((ViewGroup)v).getChildCount()>0);}
}
