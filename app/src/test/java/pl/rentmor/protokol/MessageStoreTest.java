package pl.rentmor.protokol;

import android.app.Activity;
import android.view.View;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class MessageStoreTest {
    @Test public void preservesReadStatusAndOnlyReturnsNewMessages() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        LocalStore store=new LocalStore(activity);
        store.clearMessages();
        JSONArray first=new JSONArray("[{\"id\":\"msg1\",\"client\":\"Jan\",\"message\":\"Proszę o kontakt\"}]");
        assertEquals(1,store.replaceMessages(first).length());
        assertEquals(1,store.unreadMessagesCount());
        store.markMessageRead("msg1");
        assertEquals(0,store.unreadMessagesCount());
        JSONArray changed=new JSONArray("[{\"id\":\"msg1\",\"client\":\"Jan\",\"message\":\"Zaktualizowana treść\"},{\"id\":\"msg2\",\"client\":\"Anna\",\"message\":\"Wynajem\"}]");
        JSONArray newMessages=store.replaceMessages(changed);
        assertEquals(1,newMessages.length());
        assertEquals("msg2",newMessages.getJSONObject(0).getString("id"));
        assertEquals(1,store.unreadMessagesCount());
        assertEquals("Zaktualizowana treść",store.allMessages().getJSONObject(1).getString("message"));
        assertTrue(store.allMessages().getJSONObject(1).getBoolean("is_read"));
        store.replaceMessages(new JSONArray("[{\"id\":\"msg2\",\"message\":\"Wynajem\"}]"));
        assertEquals(1,store.allMessages().length());
        store.close();
    }
    @Test public void inboxRendersOfflineAndShowsMessages() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        LocalStore store=new LocalStore(activity);store.clearMessages();
        store.replaceMessages(new JSONArray("[{\"id\":\"a\",\"client\":\"Tester\",\"date\":\"2026-10-24\",\"product\":\"Rębak\",\"message\":\"Czy jest dostępny?\",\"contact\":\"733 891 281\"}]"));
        MessageInbox inbox=new MessageInbox(activity,store);
        View view=inbox.view();
        view.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        assertTrue(view.getMeasuredHeight()>0);
        store.close();
    }
}
