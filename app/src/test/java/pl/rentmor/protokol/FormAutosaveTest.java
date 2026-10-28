package pl.rentmor.protokol;

import android.app.Activity;
import android.os.Looper;
import android.widget.EditText;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=29)
public class FormAutosaveTest {
    @Test public void textChangesAreDebouncedAndFlushed() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        EditText text=new EditText(activity);
        AtomicInteger writes=new AtomicInteger();
        FormAutosave saver=new FormAutosave(writes::incrementAndGet);
        saver.watch(text);
        text.setText("Kup");
        text.setText("Kupić");
        assertEquals(0,writes.get());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300));
        assertEquals(1,writes.get());
        text.setText("Kupić papier");
        saver.flush();
        assertEquals(2,writes.get());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300));
        assertEquals(2,writes.get());
        saver.stop();
        text.setText("Nie zapisuj");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300));
        assertEquals(2,writes.get());
    }

    @Test public void protocolDraftSurvivesStoreRecreationAndClears() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        ProtocolCameraStore first=new ProtocolCameraStore(activity);
        first.clearDraft();
        first.saveDraft("{\"kind\":\"issue\",\"client\":\"Test\"}");
        ProtocolCameraStore second=new ProtocolCameraStore(activity);
        assertTrue(second.draftFile().isFile());
        String contents=new String(java.nio.file.Files.readAllBytes(second.draftFile().toPath()),
            java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(contents.contains("\"client\":\"Test\""));
        second.saveDraft("{\"kind\":\"return\"}");
        assertEquals("{\"kind\":\"return\"}",new String(java.nio.file.Files.readAllBytes(second.draftFile().toPath()),
            java.nio.charset.StandardCharsets.UTF_8));
        second.clearDraft();
        assertFalse(second.draftFile().exists());
    }
}
