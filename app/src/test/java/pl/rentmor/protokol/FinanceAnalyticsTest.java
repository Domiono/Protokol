package pl.rentmor.protokol;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import java.time.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class FinanceAnalyticsTest {
    private LocalDate d(String s){return LocalDate.parse(s);}
    @Test public void usesSettlementDateAndIncludesAllCosts() throws Exception {
        JSONArray rentals=new JSONArray("[{\"settled\":true,\"month\":\"2025-12\",\"settled_at\":\"2026-01-02 12:00:00\",\"income\":100.10,\"loss\":5.05,\"equipment\":\"Puzzi\"}]");
        JSONArray costs=new JSONArray("[{\"date\":\"2026-01-02\",\"amount\":10.05,\"type\":\"expense\"},{\"date\":\"2026-01-03\",\"amount\":120,\"type\":\"investment\"}]");
        FinanceAnalytics.Report r=FinanceAnalytics.calculate(rentals,costs,d("2026-01-01"),d("2026-01-04"),0);
        assertEquals(10010,r.income);assertEquals(13510,r.costs());assertEquals(-3500,r.result());assertEquals(1,r.rentals);assertEquals(4,r.buckets.size());assertEquals(0,r.buckets.get(0).income);assertEquals(10010,r.buckets.get(1).income);
        assertEquals(0,FinanceAnalytics.calculate(rentals,costs,d("2025-12-01"),d("2025-12-31"),2).income);
    }
    @Test public void weeklyAndMonthlyBucketsConserveTotalsAcrossYearBoundary() throws Exception {
        JSONArray a=new JSONArray("[{\"settled\":true,\"settled_at\":\"2025-12-31\",\"income\":20,\"loss\":0},{\"settled\":true,\"settled_at\":\"2026-01-05\",\"income\":30,\"loss\":2}]");
        for(int grain=0;grain<3;grain++){FinanceAnalytics.Report r=FinanceAnalytics.calculate(a,new JSONArray(),d("2025-12-30"),d("2026-01-06"),grain);long sum=0;for(FinanceAnalytics.Bucket b:r.buckets)sum+=b.result();assertEquals(4800,sum);assertEquals(r.result(),sum);assertEquals(d("2025-12-30"),r.buckets.get(0).start);}
        assertEquals(d("2025-12-29"),FinanceAnalytics.bucketStart(d("2026-01-01"),1));
    }
    @Test public void skipsMissingDatesAndAmountsInsteadOfInventingValues() throws Exception {
        JSONArray a=new JSONArray("[{\"settled\":true,\"income\":200,\"loss\":0},{\"settled\":true,\"settled_at\":\"2026-01-02\",\"loss\":0},{\"settled\":false,\"start_date\":\"2026-01-03\",\"income\":300}]");
        FinanceAnalytics.Report r=FinanceAnalytics.calculate(a,new JSONArray(),d("2026-01-01"),d("2026-01-04"),0);assertEquals(0,r.income);assertEquals(1,r.undated);assertEquals(1,r.invalid);assertEquals(1,r.pending);
    }
    @Test public void pendingOfflineCostRemainsInReportsAfterServerRefresh() throws Exception {
        android.app.Activity activity=org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        LocalStore store=new LocalStore(activity);
        JSONObject cost=new JSONObject().put("id","repair-1").put("month","2026-10")
            .put("date","2026-10-25").put("name","Serwis Puzzi").put("amount","75")
            .put("type","expense").put("equipment_name","Puzzi");
        store.saveExpense(cost);store.enqueue("expense_save",cost);
        store.replaceServerExpenses(new JSONArray());
        assertEquals(1,store.allExpenses().length());
        FinanceAnalytics.Report report=FinanceAnalytics.calculate(new JSONArray(),store.allExpenses(),d("2026-10-01"),d("2026-10-31"),2);
        assertEquals(7500,report.cost);
        EquipmentAnalytics.Snapshot models=EquipmentAnalytics.calculate(new JSONArray(),store.allExpenses(),d("2026-10-01"),d("2026-10-31"));
        assertEquals(7500,models.sorted(0).get(0).runningCosts);
        store.close();
    }
    @Test public void financeReportIsBySettlementMonthNotBookingMonth()throws Exception {
        JSONArray a=new JSONArray("[{\"id\":\"x\",\"settled\":true,\"start_date\":\"2026-09-30\",\"end_date\":\"2026-10-01\",\"settled_at\":\"2026-10-02 13:10:00\",\"income\":199.99,\"loss\":9.99,\"equipment\":\"Puzzi\"}]");
        FinanceAnalytics.Report september=FinanceAnalytics.calculate(a,new JSONArray(),d("2026-09-01"),d("2026-09-30"),2);
        FinanceAnalytics.Report october=FinanceAnalytics.calculate(a,new JSONArray(),d("2026-10-01"),d("2026-10-31"),2);
        assertEquals(0,september.income);assertEquals(19999,october.income);
        assertEquals(999,october.loss);assertEquals(1,october.rentals);
    }
    @Test public void dashboardHandlesEmptyAndNegativeData() throws Exception {
        android.content.Context context=org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        FinanceDashboard dashboard=new FinanceDashboard(context,new JSONArray(),new JSONArray(),()->{},()->{});
        android.view.View view=dashboard.view();view.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));view.layout(0,0,1080,view.getMeasuredHeight());assertTrue(view.getMeasuredHeight()>0);
    }
}
