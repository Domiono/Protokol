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
 private void checkLayout(Activity a){View v=a.findViewById(android.R.id.content);v.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));v.layout(0,0,1080,1920);assertTrue(((ViewGroup)v).getChildCount()>0);}
}
