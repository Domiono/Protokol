package pl.rentmor.protokol;
import org.junit.Test;
import java.net.URI;
import static org.junit.Assert.*;
public class ApkDownloadsTest {
    @Test public void everyDownloadHasFreshServerUrlAndNoHardcodedRelease()throws Exception{
        String first=ApkDownloads.url(1000),next=ApkDownloads.url(1001);
        assertNotEquals(first,next);URI uri=new URI(first);
        assertEquals("https",uri.getScheme());assertEquals("rentmor.pl",uri.getHost());
        assertEquals("/download-app.php",uri.getPath());assertEquals("request=1000",uri.getQuery());
        assertFalse(first.contains("RentMor.apk"));assertFalse(first.contains("1.7."));
    }
}
