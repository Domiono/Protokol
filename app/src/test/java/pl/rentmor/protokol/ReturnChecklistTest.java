package pl.rentmor.protokol;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.CheckBox;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class ReturnChecklistTest {
    @Test public void missingAccessoryRequiresExplicitConfirmation(){
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();
        CheckBox hose=new CheckBox(a);hose.setText("Wąż");
        CheckBox nozzle=new CheckBox(a);nozzle.setText("Dysza");nozzle.setChecked(true);
        int[] saved={0};
        ReturnChecklist.confirm(a,Arrays.asList(hose,nozzle),()->saved[0]++);
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(0,saved[0]);
        String message=((TextView)dialog.findViewById(android.R.id.message)).getText().toString();
        assertTrue(message.contains("Wąż"));assertFalse(message.contains("Dysza"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(0,saved[0]);
        ReturnChecklist.confirm(a,Arrays.asList(hose,nozzle),()->saved[0]++);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(1,saved[0]);assertFalse(hose.isChecked());
        hose.setChecked(true);
        ReturnChecklist.confirm(a,Arrays.asList(hose,nozzle),()->saved[0]++);
        assertEquals(2,saved[0]);
    }
    @Test public void summaryHandlesPartialCompleteAndLegacyEmptyLists(){
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();
        TextView summary=new TextView(a);CheckBox check=new CheckBox(a);check.setText("Przewód");
        ReturnChecklist.refresh(summary,Arrays.asList(check));assertTrue(summary.getText().toString().contains("0 z 1"));
        check.setChecked(true);ReturnChecklist.refresh(summary,Arrays.asList(check));assertTrue(summary.getText().toString().contains("1 z 1"));
        ReturnChecklist.refresh(summary,Collections.emptyList());assertTrue(summary.getText().toString().contains("nie wpisano"));
        int[] saved={0};ReturnChecklist.confirm(a,Collections.emptyList(),()->saved[0]++);assertEquals(1,saved[0]);
    }
}
