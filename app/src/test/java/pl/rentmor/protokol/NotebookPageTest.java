package pl.rentmor.protokol;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class NotebookPageTest {
    private View named(View view,String label){
        if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            View found=named(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;
        }
        return null;
    }
    private EditText input(View view){
        if(view instanceof EditText)return (EditText)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            EditText found=input(((ViewGroup)view).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    private void click(View view){
        view.performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    @Test public void addValidateEditCompleteRestoreAndConfirmDeletion(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.deleteDatabase("rentmor_notebook.db");
        NotebookStore store=new NotebookStore(activity);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(1);activity.setContentView(root);
        new NotebookPage(activity,root,store,()->{}).show();
        click(named(root,"＋ Dodaj wpis"));
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        click(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        assertTrue(dialog.isShowing());assertEquals(0,store.pendingCount());
        EditText edit=input(dialog.getWindow().getDecorView());edit.setText("Kupić worki");
        click(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        assertEquals(1,store.pendingCount());assertNotNull(named(root,"Kupić worki"));
        click(named(root,"Edytuj"));dialog=ShadowAlertDialog.getLatestAlertDialog();
        input(dialog.getWindow().getDecorView()).setText("Kupić 10 worków");click(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        ((CheckBox)named(root,"Kupić 10 worków")).setChecked(true);assertEquals(0,store.pendingCount());
        ((CheckBox)named(root,"Pokaż zrobione")).setChecked(true);
        ((CheckBox)named(root,"Kupić 10 worków")).setChecked(false);assertEquals(1,store.pendingCount());
        ((CheckBox)named(root,"Pokaż zrobione")).setChecked(false);
        click(named(root,"Usuń"));dialog=ShadowAlertDialog.getLatestAlertDialog();
        click(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));assertEquals(1,store.pendingCount());
        click(named(root,"Usuń"));dialog=ShadowAlertDialog.getLatestAlertDialog();
        click(dialog.getButton(AlertDialog.BUTTON_POSITIVE));assertEquals(0,store.pendingCount());
        store.close();activity.finish();activity.deleteDatabase("rentmor_notebook.db");
    }
}
