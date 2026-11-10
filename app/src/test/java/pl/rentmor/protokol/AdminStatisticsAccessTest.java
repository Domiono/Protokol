package pl.rentmor.protokol;

import android.app.Application;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import javax.crypto.spec.SecretKeySpec;
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
    @Before public void fakeKey(){
        byte[] key=new byte[32];for(int i=0;i<32;i++)key[i]=(byte)i;
        SecurePrefs.setRobolectricTestKey(new SecretKeySpec(key,"AES"));
    }
    @After public void clearRole(){
        SecurePrefs.remove(RuntimeEnvironment.getApplication(),"cookie");
        SecurePrefs.setRobolectricTestKey(null);
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
    private HomeInboxShortcut findInboxShortcut(View view){
        if(view instanceof HomeInboxShortcut)return (HomeInboxShortcut)view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int index=0;index<group.getChildCount();index++){
                HomeInboxShortcut found=findInboxShortcut(group.getChildAt(index));
                if(found!=null)return found;
            }
        }
        return null;
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
    @Test public void assistantCannotOpenWebsiteCatalogueOrProductEditor() throws Exception {
        MainActivity activity=open("assistant");
        View screen=activity.findViewById(android.R.id.content);
        assertFalse(shows(screen,"Strona"));
        Method site=MainActivity.class.getDeclaredMethod("sitePage");
        site.setAccessible(true);site.invoke(activity);
        assertFalse(shows(activity.findViewById(android.R.id.content),"STRONA RENTMOR"));
        Method list=MainActivity.class.getDeclaredMethod("siteProductsPage");
        list.setAccessible(true);list.invoke(activity);
        assertFalse(shows(activity.findViewById(android.R.id.content),"LISTA SPRZĘTU NA STRONIE"));
        Method editor=MainActivity.class.getDeclaredMethod("productForm",org.json.JSONObject.class);
        editor.setAccessible(true);editor.invoke(activity,new Object[]{null});
        assertFalse(shows(activity.findViewById(android.R.id.content),"DODAJ SPRZĘT NA STRONĘ"));
        Method stats=MainActivity.class.getDeclaredMethod("siteStatsPage");
        stats.setAccessible(true);stats.invoke(activity);
        assertFalse(shows(activity.findViewById(android.R.id.content),"STATYSTYKI STRONY"));
    }
    @Test public void contactInboxIsAdministratorOnly() throws Exception {
        MainActivity assistant=open("assistant");
        assertNull(findInboxShortcut(assistant.findViewById(android.R.id.content)));
        Method inbox=MainActivity.class.getDeclaredMethod("messageInboxPage");
        inbox.setAccessible(true);
        inbox.invoke(assistant);
        assertFalse(shows(assistant.findViewById(android.R.id.content),"Wiadomości klientów"));
        assistant.finish();
        MainActivity admin=open("admin");
        View screen=admin.findViewById(android.R.id.content);
        HomeInboxShortcut shortcut=findInboxShortcut(screen);
        assertNotNull("Administrator should have a top-right inbox button",shortcut);
        assertTrue(shortcut.getContentDescription().toString().contains("Wiadomości klientów"));
        assertFalse("Bottom contact section should be removed",shows(screen,"KONTAKT Z KLIENTAMI"));
        assertFalse("Old text menu item should be removed",shows(screen,"Wiadomości klientów"));
    }
    @Test public void administratorSeesFinanceNavigation() {
        MainActivity activity=open("admin");
        View screen=activity.findViewById(android.R.id.content);
        assertTrue(shows(screen,"Statystyki najmu"));
        assertTrue(shows(screen,"Pozostałe koszty"));
    }
}
