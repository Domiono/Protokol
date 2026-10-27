package pl.rentmor.protokol;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.IOException;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class SyncManagerHttpTest {
    @Test public void retriesOnlyTemporaryReadErrorsAndNeverReplaysWrites(){
        assertTrue(SyncManager.shouldRetryRead("GET",500,1,3));
        assertTrue(SyncManager.shouldRetryRead("GET",502,2,3));
        assertTrue(SyncManager.shouldRetryRead("GET",503,1,3));
        assertTrue(SyncManager.shouldRetryRead("GET",504,1,3));
        assertFalse(SyncManager.shouldRetryRead("GET",500,3,3));
        assertFalse(SyncManager.shouldRetryRead("POST",500,1,3));
        assertFalse(SyncManager.shouldRetryRead("GET",401,1,3));
        assertFalse(SyncManager.shouldRetryRead("GET",403,1,3));
        assertFalse(SyncManager.shouldRetryRead("GET",302,1,3));
        assertFalse(SyncManager.shouldRetryRead("GET",200,1,3));
    }
    @Test public void htmlOrHostingErrorDoesNotLeakRawMarkup() throws Exception{
        for(int status:new int[]{200,401,403,502}){
            try{
                SyncManager.decodeResponse(status,"text/html; charset=utf-8",
                    "<!DOCTYPE html><html><body>Hosting temporarily unavailable</body></html>");
                fail("Response must be rejected");
            }catch(IOException e){
                assertTrue(e.getMessage(),e.getMessage().contains("stronę HTML"));
                assertFalse(e.getMessage().contains("<!DOCTYPE"));
            }
        }
    }
    @Test public void nonJsonAndEmptyResponsesHaveHumanFriendlyErrors() throws Exception{
        try{SyncManager.decodeResponse(200,"text/plain","some unexpected answer");fail();}
        catch(IOException e){assertTrue(e.getMessage().contains("nieprawidłowe dane"));}
        try{SyncManager.decodeResponse(500,"text/plain","  ");fail();}
        catch(IOException e){assertTrue(e.getMessage().contains("pustą odpowiedź"));}
    }
    @Test public void redirectsAreRejectedBeforeAnyHtmlConversion()throws Exception{
        try{SyncManager.decodeResponse(302,"text/html","<html>Login</html>");fail();}
        catch(IOException e){assertTrue(e.getMessage().contains("przekierował"));}
    }
    @Test public void preservesExactApiAuthorizationMessage()throws Exception{
        try{SyncManager.decodeResponse(401,"application/json",
            "{\"ok\":false,\"error\":\"Urządzenie nieaktywne.\"}");fail();}
        catch(IOException e){assertEquals("Urządzenie nieaktywne.",e.getMessage());}
        JSONObject r=SyncManager.decodeResponse(200,"application/json","{\"ok\":true,\"results\":[]}");
        assertEquals(0,r.getJSONArray("results").length());
        try{SyncManager.decodeResponse(503,"application/json","{\"ok\":false,\"error\":\"Baza jest chwilowo zajęta.\"}");fail();}
        catch(IOException e){assertEquals("Baza jest chwilowo zajęta.",e.getMessage());}
    }
}