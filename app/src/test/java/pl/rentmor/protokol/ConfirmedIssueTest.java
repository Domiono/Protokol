package pl.rentmor.protokol;
import android.app.Activity;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class ConfirmedIssueTest {
 private LocalStore store;
 @Before public void setup()throws Exception{
  store=new LocalStore(Robolectric.buildActivity(Activity.class).setup().get());
  store.saveAsset(new JSONObject().put("id","one").put("label","Unit 1").put("equipment","Test machine"));
  store.saveAsset(new JSONObject().put("id","two").put("label","Unit 2").put("equipment","Test machine"));
  store.saveReservation(new JSONObject().put("id","r").put("equipment","Test machine").put("asset_id","one").put("asset_label","Unit 1").put("month","2030-11").put("start_date","2030-11-15").put("end_date","2030-11-16").put("done",false).put("phone","test-contact"));
 }
 @After public void close(){store.close();}
 private JSONObject current()throws Exception{return store.allReservations().getJSONObject(0);}
 @Test public void issuesExactConfirmedCopyAndPreservesContact()throws Exception{
  JSONObject before=current();store.queueConfirmedIssue(before);
  JSONObject op=store.queued().getJSONObject(0);
  assertTrue(op.getBoolean("done"));assertFalse(op.getBoolean("auto_assign"));assertEquals("one",op.getString("asset_id"));assertEquals("test-contact",op.getString("phone"));assertFalse(before.getBoolean("done"));
 }
 @Test public void staleCardCannotRestorePreviousCopy()throws Exception{
  JSONObject old=current();JSONObject fresh=new JSONObject(old.toString()).put("asset_id","two").put("asset_label","Unit 2");store.saveReservation(fresh);
  try{store.queueConfirmedIssue(old);fail();}catch(IllegalStateException expected){}
  assertEquals(0,store.pendingCount());assertEquals("two",current().getString("asset_id"));assertFalse(current().getBoolean("done"));
 }
 @Test public void pendingReturnOrEditMustSyncBeforeIssue()throws Exception{
  JSONObject before=current();store.enqueue("reservation_settle",new JSONObject().put("id","old"));
  try{store.queueConfirmedIssue(before);fail();}catch(IllegalStateException expected){}
  assertEquals(1,store.pendingCount());assertFalse(current().getBoolean("done"));
 }
 @Test public void finishedOrMissingCopyCannotIssue()throws Exception{
  JSONObject before=current();
  for(String flag:new String[]{"done","returned","settled"}){
   store.saveReservation(new JSONObject(before.toString()).put(flag,true));
   try{store.queueConfirmedIssue(current());fail();}catch(IllegalStateException expected){}
   assertEquals(0,store.pendingCount());
  }
  store.saveReservation(new JSONObject(before.toString()).put("asset_id",""));
  try{store.queueConfirmedIssue(current());fail();}catch(IllegalStateException expected){}
  assertEquals(0,store.pendingCount());
 }
 @Test public void serviceConflictDoesNotMutateBooking()throws Exception{
  JSONObject asset=store.assets().getJSONObject(0);asset.put("id","one");asset.put("in_service",true);store.saveAsset(asset);
  try{store.queueConfirmedIssue(current());fail();}catch(IllegalArgumentException expected){}
  assertFalse(current().getBoolean("done"));assertEquals(0,store.pendingCount());
 }
}
