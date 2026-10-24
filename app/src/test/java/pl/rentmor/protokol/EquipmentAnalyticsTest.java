package pl.rentmor.protokol;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.time.LocalDate;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class EquipmentAnalyticsTest {
    @Test public void groupsEquipmentAcrossCaseAndAccountsCostsByTypeNotNote() throws Exception {
        JSONArray rentals=new JSONArray("[{\"settled\":true,\"settled_at\":\"2026-01-03 13:05\",\"equipment\":\"  PUZZI  \",\"income\":100.10,\"loss\":5.05,\"start_date\":\"2025-12-31\",\"end_date\":\"2026-01-02\"},{\"settled\":true,\"settled_at\":\"2026-01-05\",\"equipment\":\"puzzi\",\"income\":50,\"loss\":0,\"start_date\":\"2026-01-04\",\"end_date\":\"2026-01-04\"}]");
        JSONArray costs=new JSONArray("[{\"date\":\"2026-01-04\",\"equipment_name\":\"Puzzi\",\"amount\":20,\"type\":\"expense\",\"note\":\"Zakup\"},{\"date\":\"2026-01-05\",\"equipment_name\":\"pUzZi\",\"amount\":50,\"type\":\"investment\",\"note\":\"Serwis\"},{\"date\":\"2026-01-06\",\"amount\":12,\"type\":\"expense\"}]");
        EquipmentAnalytics.Snapshot s=EquipmentAnalytics.calculate(rentals,costs,LocalDate.of(2026,1,1),LocalDate.of(2026,1,31));
        assertEquals(1,s.models.size());
        EquipmentAnalytics.Model m=s.sorted(0).get(0);
        assertEquals(2,m.rentals);assertEquals(4,m.knownDays);
        assertEquals(15010,m.income);assertEquals(505,m.losses);
        assertEquals(2000,m.runningCosts);assertEquals(5000,m.investments);
        assertEquals(7505,m.result());assertEquals(12505,m.operatingResult());
        assertEquals(1,s.unassignedExpenses);assertEquals(1200,s.unassignedCost);
        assertEquals(1,m.monthly.size());
        FinanceAnalytics.Report report=FinanceAnalytics.calculate(rentals,costs,LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),2);
        assertEquals(report.income,m.income);
        assertEquals(report.result()+s.unassignedCost,m.result());
    }
    @Test public void excludesPendingAndOutOfRangeSettlementsWithoutEstimatingDays() throws Exception {
        JSONArray rentals=new JSONArray("[{\"settled\":false,\"settled_at\":\"2026-01-07\",\"equipment\":\"Puzzi\",\"income\":999,\"loss\":0},{\"settled\":true,\"settled_at\":\"2025-12-30\",\"equipment\":\"Puzzi\",\"income\":50,\"loss\":0},{\"settled\":true,\"settled_at\":\"2026-01-09\",\"equipment\":\"Puzzi\",\"income\":20,\"loss\":0}]");
        EquipmentAnalytics.Snapshot data=EquipmentAnalytics.calculate(rentals,new JSONArray(),LocalDate.of(2026,1,1),LocalDate.of(2026,1,31));
        assertEquals(1,data.settledRentals);
        assertEquals(2000,data.income);
        assertEquals(1,data.sorted(0).get(0).missingDays);
        assertEquals(0,data.sorted(0).get(0).knownDays);
    }
    @Test public void usesExplicitExpenseTypeEvenWithMisleadingNoteAndShowsExpenseOnlyModel() throws Exception {
        JSONArray expenses=new JSONArray("[{\"date\":\"2026-10-15\",\"equipment_name\":\"Rębak\",\"amount\":250.99,\"type\":\"investment\",\"note\":\"Naprawa\"}]");
        EquipmentAnalytics.Snapshot s=EquipmentAnalytics.calculate(new JSONArray(),expenses,null,null);
        assertEquals(1,s.models.size());
        assertEquals(25099,s.sorted(1).get(0).investments);
        assertEquals(-25099,s.sorted(1).get(0).result());
    }
}
