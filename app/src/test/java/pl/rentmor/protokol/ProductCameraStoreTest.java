package pl.rentmor.protokol;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class ProductCameraStoreTest {
    @Before public void clearProviderCache() throws Exception {
        java.lang.reflect.Field cache=androidx.core.content.FileProvider.class.getDeclaredField("sCache");
        cache.setAccessible(true);((java.util.Map<?,?>)cache.get(null)).clear();
    }
    @Test public void cameraOutputPersistsAndDoesNotCollideWithProtocolCamera() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        ProductCameraStore product=new ProductCameraStore(context);
        ProtocolCameraStore protocol=new ProtocolCameraStore(context);
        product.clear();protocol.clear();
        Uri productUri=product.create();
        Uri protocolUri=protocol.create();
        assertEquals("content",productUri.getScheme());
        assertNotEquals(productUri,protocolUri);
        Bitmap photo=Bitmap.createBitmap(700,500,Bitmap.Config.ARGB_8888);
        try(OutputStream stream=context.getContentResolver().openOutputStream(productUri,"w")){
            assertNotNull(stream);
            assertTrue(photo.compress(Bitmap.CompressFormat.JPEG,85,stream));
        }
        assertEquals(productUri,new ProductCameraStore(context).pending());
        try(InputStream stream=context.getContentResolver().openInputStream(productUri)){
            Bitmap decoded=BitmapFactory.decodeStream(stream);
            assertNotNull(decoded);
            assertEquals(700,decoded.getWidth());
            assertEquals(500,decoded.getHeight());
        }
        new ProductCameraStore(context).clear();
        assertNull(product.pending());
        assertEquals(protocolUri,protocol.pending());
        protocol.clear();
    }

    @Test public void draftSurvivesRecreationButCanBeDiscarded() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        ProductCameraStore store=new ProductCameraStore(context);
        store.clear();store.clearDraft();
        store.saveDraft("{\"name\":\"Żyrafa\",\"new_photos\":[]}");
        assertTrue(new ProductCameraStore(context).draftFile().exists());
        assertEquals("{\"name\":\"Żyrafa\",\"new_photos\":[]}",
            new String(java.nio.file.Files.readAllBytes(store.draftFile().toPath()),
                java.nio.charset.StandardCharsets.UTF_8));
        store.create();
        assertNotNull(store.pending());
        store.clear();
        store.clearDraft();
        assertFalse(store.draftFile().exists());
    }
}
