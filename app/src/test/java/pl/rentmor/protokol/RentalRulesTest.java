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
 @Test public void copiesAndService()throws Exception{
 JSONObject a=new JSONObject("{id:'one',equipment:'Puzzi',in_service:false}");JSONArray assets=new JSONArray().put(a);
 JSONObject first=new JSONObject("{id:'r1',asset_id:'one',equipment:'Puzzi',start_date:'2030-10-25',end_date:'2030-10-27'}");
 JSONObject second=new JSONObject(first.toString()).put("id","r2");
 assertFalse(RentalRules.conflict(second,new JSONArray().put(first),assets).isEmpty());
 assertEquals("",RentalRules.conflict(first,new JSONArray().put(first),assets));
 a.put("in_service",true);assertFalse(RentalRules.conflict(first,new JSONArray(),assets).isEmpty());
 }
}
