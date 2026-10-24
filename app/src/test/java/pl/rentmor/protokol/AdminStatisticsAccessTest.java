package pl.rentmor.protokol;

import android.app.Application;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class AdminStatisticsAccessTest {
    private SharedPreferences prefs(){
        return RuntimeEnvironment.getApplication().getSharedPreferences("rentmor",0);
    }
    @After public void clearRole(){
        prefs().edit().remove("role").remove("cookie").apply();
    }
    private MainActivity open(String role){
        prefs().edit().putString("cookie","test-session").putString("role",role).commit();
        return Robolectric.buildActivity(MainActivity.class).setup().get();
    }
    private boolean shows(View view,String text){
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return true;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int n=0;n<group.getChildCount();n++)if(shows(group.getChildAt(n),text))return true;
        }
        return false;
    }
    @Test public void assistantHasNoFinanceNavigation() throws Exception {
        MainActivity activity=open("assistant");
        View screen=activity.findViewById(android.R.id.content);
        assertFalse(shows(screen,"Statystyki najmu"));
        assertFalse(shows(screen,"Pozostałe koszty"));
        Method method=MainActivity.class.getDeclaredMethod("reservationStatsPage");
        method.setAccessible(true);
        method.invoke(activity);
        assertFalse(shows(activity.findViewById(android.R.id.content),"Finanse RentMor"));
        Method equipment=MainActivity.class.getDeclaredMethod("equipmentProfitabilityPage");
        equipment.setAccessible(true);
        equipment.invoke(activity);
        assertFalse(shows(activity.findViewById(android.R.id.content),"Rentowność sprzętu"));
    }
    @Test public void administratorSeesFinanceNavigation() {
        MainActivity activity=open("admin");
        View screen=activity.findViewById(android.R.id.content);
        assertTrue(shows(screen,"Statystyki najmu"));
        assertTrue(shows(screen,"Pozostałe koszty"));
    }
}
