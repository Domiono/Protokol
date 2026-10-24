package pl.rentmor.protokol;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class EquipmentDashboardTest {
    private View findDetails(View view) {
        CharSequence description=view.getContentDescription();
        if(description!=null&&description.toString().startsWith("Szczegóły modelu"))return view;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) {
                View match=findDetails(group.getChildAt(i));
                if(match!=null)return match;
            }
        }
        return null;
    }
    private void layout(Activity activity,View content) {
        ScrollView scroller=new ScrollView(activity);
        scroller.addView(content);
        activity.setContentView(scroller);
        scroller.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));
        scroller.layout(0,0,1080,1920);
        assertTrue(content.getMeasuredHeight()>0);
    }
    @Test public void emptyEquipmentScreenRenders() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        EquipmentDashboard dashboard=new EquipmentDashboard(activity,new JSONArray(),new JSONArray(),name->{});
        layout(activity,dashboard.view());
        assertNull(findDetails(dashboard.view()));
    }
    @Test public void modelDetailsExpandWithoutCrashing() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        JSONArray rentals=new JSONArray("[{\"equipment\":\"Puzzi\",\"settled\":true,\"settled_at\":\"2026-10-01\",\"income\":200,\"loss\":10,\"start_date\":\"2026-09-30\",\"end_date\":\"2026-10-01\"}]");
        EquipmentDashboard dashboard=new EquipmentDashboard(activity,rentals,new JSONArray(),name->{});
        layout(activity,dashboard.view());
        View trigger=findDetails(dashboard.view());
        assertNotNull(trigger);
        assertTrue(trigger.performClick());
    }
}
