package pl.rentmor.protokol;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FinanceChartTest {
    private View chart(View view){
        if(view.getClass().getSimpleName().equals("LineChart"))return view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=chart(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    private void verify(boolean withData)throws Exception{
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        JSONArray rentals=new JSONArray();if(withData){JSONObject o=new JSONObject();o.put("settled",true);o.put("settled_at",java.time.LocalDate.now().toString());o.put("income",1400);o.put("loss",399);rentals.put(o);}
        FinanceDashboard dashboard=new FinanceDashboard(activity,rentals,new JSONArray(),()->{},()->{});
        ScrollView screen=new ScrollView(activity);screen.addView(dashboard.view());activity.setContentView(screen);
        int w=1080,h=1920;screen.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));screen.layout(0,0,w,h);
        View plot=chart(screen);assertNotNull(plot);assertTrue("Plot must not collapse inside HorizontalScrollView",plot.getMeasuredWidth()>0);assertTrue(plot.getMeasuredHeight()>0);
        HorizontalScrollView scroll=(HorizontalScrollView)plot.getParent();assertTrue("Daily plot must extend beyond viewport",plot.getWidth()>scroll.getWidth());
        scroll.scrollTo(plot.getWidth(),0);assertTrue("Chart must scroll horizontally",scroll.getScrollX()>0);
        Bitmap bitmap=Bitmap.createBitmap(plot.getWidth(),plot.getHeight(),Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);plot.draw(new Canvas(bitmap));
        int colored=0,series=0;for(int y=0;y<bitmap.getHeight();y+=2)for(int x=0;x<bitmap.getWidth();x+=2){int color=bitmap.getPixel(x,y);if(color!=Color.WHITE)colored++;if(color==0xff2563eb||color==0xff059669)series++;}
        assertTrue("Axes and lines must actually render",colored>100);assertTrue("Financial series must be visible",series>10);bitmap.recycle();
    }
    @Test public void nonEmptyChartDrawsAndScrolls()throws Exception{verify(true);}
    @Test public void zeroDataStillDrawsAxesAndSeries()throws Exception{verify(false);}
}
