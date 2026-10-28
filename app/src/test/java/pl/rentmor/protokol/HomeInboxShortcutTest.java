package pl.rentmor.protokol;

import android.view.View;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class HomeInboxShortcutTest {
    @Test public void emptyAndUnreadBadgeReflectInboxAndOpenOnTap() {
        AtomicInteger opens=new AtomicInteger();
        HomeInboxShortcut shortcut=new HomeInboxShortcut(
            RuntimeEnvironment.getApplication(),opens::incrementAndGet);
        TextView count=shortcut.findViewWithTag("rentmor_unread_messages");
        assertNotNull(count);
        assertEquals(View.GONE,count.getVisibility());
        assertTrue(shortcut.getContentDescription().toString().contains("Brak nieprzeczytanych"));
        shortcut.setUnread(4);
        assertEquals(View.VISIBLE,count.getVisibility());
        assertEquals("4",count.getText().toString());
        assertTrue(shortcut.getContentDescription().toString().contains("Nieprzeczytane: 4"));
        assertTrue(shortcut.performClick());
        assertEquals(1,opens.get());
        shortcut.setUnread(120);
        assertEquals("99+",count.getText().toString());
        assertTrue(shortcut.getContentDescription().toString().contains("120"));
        shortcut.setUnread(0);
        assertEquals(View.GONE,count.getVisibility());
    }
}
