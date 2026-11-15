package pl.rentmor.protokol;
import android.app.Activity;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class ReservationRecoveryTest {
 private LocalStore store(){return new LocalStore(Robolectric.buildActivity(Activity.class).setup().get());}
 private JSONObject booking(String id)throws Exception{return new JSONObject().put("id",id).put("equipment","Test equipment").put("asset_id","local").put("asset_label","Local").put("end_date","2030-12-04");}
 @Test public void recoveryPreservesDatesOtherOperationsAndQueueIdentity()throws Exception{
  LocalStore s=store();s.saveRentalOperation(booking("one"),"reservation_save");s.saveRentalOperation(booking("two"),"reservation_save");
  JSONObject op=s.queued().getJSONObject(0);String untouched=s.queued().getJSONObject(1).toString();
  JSONObject remote=booking("one").put("asset_id","server").put("asset_label","Server").put("done",true).put("end_date","2030-12-02").put("vacuum_asset_id","vac").put("vacuum_asset_label","Vacuum");
  s.repairReservationCopies(op,remote);
  JSONObject fixed=s.queued().getJSONObject(0);
  assertEquals(2,s.pendingCount());assertEquals(op.getString("op_id"),fixed.getString("op_id"));assertEquals("server",fixed.getString("asset_id"));assertEquals("vac",fixed.getString("vacuum_asset_id"));assertTrue(fixed.getBoolean("done"));assertEquals("2030-12-04",fixed.getString("end_date"));assertEquals(untouched,s.queued().getJSONObject(1).toString());
  s.close();
 }
 @Test public void finishedOrMismatchedReservationCannotBeRepaired()throws Exception{
  LocalStore s=store();s.saveRentalOperation(booking("one"),"reservation_save");JSONObject op=s.queued().getJSONObject(0);
  for(JSONObject remote:new JSONObject[]{booking("one").put("settled",true),booking("one").put("returned",true),booking("other"),booking("one").put("equipment","Other")}){
   try{s.repairReservationCopies(op,remote);fail("must reject");}catch(IllegalArgumentException expected){}
   assertEquals(op.toString(),s.queued().getJSONObject(0).toString());
  }s.close();
 }
 @Test public void staleDialogCannotReplaceNewerQueuedEdit()throws Exception{
  LocalStore s=store();s.saveRentalOperation(booking("one"),"reservation_save");JSONObject op=s.queued().getJSONObject(0);
  s.saveRentalOperation(booking("one").put("end_date","2030-12-09"),"reservation_save");
  try{s.repairReservationCopies(op,booking("one"));fail("must reject");}catch(IllegalStateException expected){}
  assertEquals("2030-12-09",s.queued().getJSONObject(0).getString("end_date"));s.close();
 }
}
