package pl.rentmor.protokol;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class ProtocolCameraStoreTest {
    @org.junit.Before public void resetProviderPathsForSandbox() throws Exception {
        // Robolectric gives each test a different filesDir; AndroidX caches paths by authority.
        java.lang.reflect.Field cache=androidx.core.content.FileProvider.class.getDeclaredField("sCache");
        cache.setAccessible(true);((java.util.Map<?,?>)cache.get(null)).clear();
    }
    @Test public void fullSizeOutputSurvivesRecreationWithoutReturnedIntent() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        ProtocolCameraStore store=new ProtocolCameraStore(context);store.clear();
        Uri output=store.create();assertEquals("content",output.getScheme());
        Bitmap photo=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);
        try(OutputStream stream=context.getContentResolver().openOutputStream(output,"w")) {assertNotNull(stream);assertTrue(photo.compress(Bitmap.CompressFormat.JPEG,90,stream));}
        ProtocolCameraStore recreated=new ProtocolCameraStore(context);assertEquals(output,recreated.pending());
        try(InputStream stream=context.getContentResolver().openInputStream(recreated.pending())) {Bitmap decoded=BitmapFactory.decodeStream(stream);assertNotNull(decoded);assertEquals(640,decoded.getWidth());assertEquals(480,decoded.getHeight());}
        recreated.clear();assertNull(new ProtocolCameraStore(context).pending());
    }
    @Test public void cancellationClearsPendingAndAllowsNextCapture() throws Exception {
        ProtocolCameraStore store=new ProtocolCameraStore(RuntimeEnvironment.getApplication());store.clear();
        Uri first=store.create();store.clear();assertNull(store.pending());Uri next=store.create();assertNotEquals(first,next);store.clear();
    }
}
