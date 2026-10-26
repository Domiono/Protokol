package pl.rentmor.protokol;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.json.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class RentalRulesTest {
 @Test public void boundariesAndMonthChanges(){assertTrue(RentalRules.overlaps("2026-10-30","2026-11-02","2026-11-02","2026-11-04"));assertFalse(RentalRules.overlaps("2026-10-30","2026-11-02","2026-11-03","2026-11-04"));assertEquals(1,RentalRules.days("2026-10-25","2026-10-25"));assertEquals(2,RentalRules.days("2026-10-24","2026-10-26"));}
 @Test public void moneyUsesDecimal(){assertEquals("123.45",RentalRules.money("123,45").toPlainString());}
 @Test(expected=IllegalArgumentException.class) public void negativeMoneyRejected(){RentalRules.money("-1");}
 @Test public void automaticAssignmentChoosesFreeCopyAndStopsOverbooking() throws Exception {
  JSONArray copies=new JSONArray()
    .put(new JSONObject().put("id","a").put("label","Puzzi 01").put("equipment","Puzzi"))
    .put(new JSONObject().put("id","b").put("label","Puzzi 02").put("equipment","Puzzi"));
  JSONObject existing=new JSONObject().put("id","first").put("equipment","Puzzi").put("asset_id","a")
    .put("start_date","2030-10-25").put("end_date","2030-10-27");
  JSONArray bookings=new JSONArray().put(existing);
  JSONObject next=new JSONObject().put("id","next").put("equipment","Puzzi")
    .put("start_date","2030-10-26").put("end_date","2030-10-28");
  assertEquals("",RentalRules.autoAssign(next,bookings,copies));assertEquals("b",next.getString("asset_id"));
  bookings.put(new JSONObject(next.toString()).put("id","another"));
  JSONObject full=new JSONObject(next.toString()).put("id","full");
  assertFalse(RentalRules.autoAssign(full,bookings,copies).isEmpty());assertEquals("",full.getString("asset_id"));
  JSONArray older=new JSONArray().put(new JSONObject().put("id","old").put("equipment","Puzzi")
    .put("start_date","2030-10-25").put("end_date","2030-10-27"));
  assertFalse(RentalRules.autoAssign(full,older,copies).isEmpty());
  assertEquals("",RentalRules.autoAssign(full,new JSONArray(),new JSONArray()));assertEquals("",full.getString("asset_id"));
 }
 @Test public void copiesAndService()throws Exception{
 JSONObject a=new JSONObject("{id:'one',equipment:'Puzzi',in_service:false}");JSONArray assets=new JSONArray().put(a);
 JSONObject first=new JSONObject("{id:'r1',asset_id:'one',equipment:'Puzzi',start_date:'2030-10-25',end_date:'2030-10-27'}");
 JSONObject second=new JSONObject(first.toString()).put("id","r2");
 assertFalse(RentalRules.conflict(second,new JSONArray().put(first),assets).isEmpty());
 assertEquals("",RentalRules.conflict(first,new JSONArray().put(first),assets));
 a.put("in_service",true);assertFalse(RentalRules.conflict(first,new JSONArray(),assets).isEmpty());
 }

 @Test public void giraffesAndIndustrialVacuumsSharePhysicalStockAcrossOfferNames()throws Exception{
  JSONArray assets=new JSONArray();
  for(int n=1;n<=3;n++){
   assets.put(new JSONObject().put("id","g"+n).put("label","Żyrafa "+n).put("equipment","Szlifierka żyrafa Stanley").put("asset_kind","giraffe"));
   assets.put(new JSONObject().put("id","v"+n).put("label","Odkurzacz "+n).put("equipment","Odkurzacz przemysłowy Bosch").put("asset_kind","vacuum"));
  }
  JSONArray bookings=new JSONArray();
  JSONObject soloVac=new JSONObject().put("id","v-book").put("equipment","Odkurzacz przemysłowy Metabo")
      .put("start_date","2030-10-25").put("end_date","2030-10-28");
  assertEquals("",RentalRules.autoAssign(soloVac,bookings,assets));assertEquals("v1",soloVac.getString("asset_id"));bookings.put(soloVac);
  JSONObject first=new JSONObject().put("id","set1").put("equipment","Żyrafa + odkurzacz")
      .put("start_date","2030-10-26").put("end_date","2030-10-27");
  assertTrue(RentalRules.needsVacuum(first));
  assertEquals("",RentalRules.autoAssign(first,bookings,assets));
  assertEquals("g1",first.getString("asset_id"));assertEquals("v2",first.getString("vacuum_asset_id"));bookings.put(first);
  JSONObject second=new JSONObject().put("id","set2").put("equipment","Żyrafa Stanley")
      .put("needs_vacuum",true).put("start_date","2030-10-26").put("end_date","2030-10-27");
  assertEquals("",RentalRules.autoAssign(second,bookings,assets));
  assertEquals("g2",second.getString("asset_id"));assertEquals("v3",second.getString("vacuum_asset_id"));bookings.put(second);
  JSONObject third=new JSONObject().put("id","set3").put("equipment","Żyrafa + odkurzacz")
      .put("start_date","2030-10-27").put("end_date","2030-10-28");
  assertFalse(RentalRules.autoAssign(third,bookings,assets).isEmpty());
  assertEquals("",third.getString("asset_id"));assertEquals("",third.getString("vacuum_asset_id"));
  JSONObject standaloneGiraffe=new JSONObject().put("id","g-solo").put("equipment","Szlifierka żyrafa Stanley")
      .put("needs_vacuum",false).put("start_date","2030-10-27").put("end_date","2030-10-28");
  assertEquals("",RentalRules.autoAssign(standaloneGiraffe,bookings,assets));
  assertEquals("g3",standaloneGiraffe.getString("asset_id"));
  assertEquals("",standaloneGiraffe.getString("vacuum_asset_id"));
  JSONObject duplicate=new JSONObject(standaloneGiraffe.toString()).put("id","new-v").put("equipment","Odkurzacz przemysłowy");
  duplicate.put("asset_id","v2");
  assertFalse(RentalRules.conflict(duplicate,bookings,assets).isEmpty());
  soloVac.put("returned",true);
  assertEquals("",RentalRules.autoAssign(third,bookings,assets));
  assertEquals("v1",third.getString("vacuum_asset_id"));
 }
 @Test public void editingTimePreservesHistoricAssetButNotEquipmentChanges()throws Exception {
  JSONObject copy=new JSONObject().put("id","wt2").put("equipment","Agregat Wabrotech WT20");
  JSONArray assets=new JSONArray().put(copy);
  JSONObject saved=new JSONObject().put("id","rent-old").put("equipment","Agregat Malarski Wabrotech wt20i")
      .put("asset_id","wt2").put("start_date","2030-10-26").put("end_date","2030-10-27").put("time","10:00").put("done",true);
  JSONArray reservations=new JSONArray().put(saved);
  JSONObject edited=new JSONObject(saved.toString()).put("time","13:10");
  assertEquals("",RentalRules.conflict(edited,reservations,assets));
  assertEquals("Agregat Malarski Wabrotech wt20i",
      RentalRules.equipmentAfterEdit(saved,"Agregat Malarski Wabrotech wt20i","Agregat Malarski Wabrotech wt20i"));
  JSONObject named=new JSONObject(saved.toString()).put("equipment","Inny agregat");
  assertFalse(RentalRules.conflict(named,reservations,assets).isEmpty());
  JSONObject newBooking=new JSONObject(saved.toString()).put("id","new");
  assertFalse(RentalRules.conflict(newBooking,reservations,assets).isEmpty());
  JSONObject conflicting=new JSONObject(saved.toString()).put("id","other").put("equipment","Agregat Wabrotech WT20");
  assertFalse(RentalRules.conflict(conflicting,reservations,assets).isEmpty());
  copy.put("in_service",true);
  assertFalse(RentalRules.conflict(edited,reservations,assets).isEmpty());
 }
 @Test public void legacySetWithoutVacuumBlocksUnsafeBookingsUntilReconciled()throws Exception{
  JSONArray assets=new JSONArray().put(new JSONObject().put("id","g1").put("equipment","Żyrafa"))
      .put(new JSONObject().put("id","v1").put("equipment","Odkurzacz przemysłowy"))
      .put(new JSONObject().put("id","v2").put("equipment","Odkurzacz przemysłowy"));
  JSONObject legacy=new JSONObject().put("id","old").put("equipment","Żyrafa + odkurzacz")
      .put("asset_id","g1").put("start_date","2030-10-25").put("end_date","2030-10-27");
  JSONArray bookings=new JSONArray().put(legacy);
  JSONObject vacuum=new JSONObject().put("id","solo").put("equipment","Odkurzacz przemysłowy")
      .put("start_date","2030-10-26").put("end_date","2030-10-27");
  assertFalse(RentalRules.autoAssign(vacuum,bookings,assets).isEmpty());
  legacy.put("vacuum_asset_id","v1");
  assertEquals("",RentalRules.autoAssign(vacuum,bookings,assets));
  assertEquals("v2",vacuum.getString("asset_id"));
  JSONArray onlyGiraffe=new JSONArray().put(assets.getJSONObject(0));
  JSONObject set=new JSONObject().put("id","set").put("equipment","Żyrafa + odkurzacz")
      .put("start_date","2030-11-01").put("end_date","2030-11-02");
  assertFalse(RentalRules.autoAssign(set,new JSONArray(),onlyGiraffe).isEmpty());
 }
}
