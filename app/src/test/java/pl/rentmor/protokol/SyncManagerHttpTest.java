package pl.rentmor.protokol;

import android.app.Activity;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public class SyncManagerHttpTest {
    private HttpServer server;
    private String endpoint;
    private SyncManager client;
    private LocalStore store;
    @Before public void setup() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("rentmor",0).edit().putString("cookie","rm_protocol_device=test-secret").commit();
        store=new LocalStore(activity);
        client=new SyncManager(activity,store);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath();
            byte[] body;
            int status;
            String type="application/json; charset=utf-8";
            if(path.equals("/html")){
                status=200;type="text/html; charset=utf-8";
                body="<!DOCTYPE html><html><body>Temporarily unavailable</body></html>".getBytes(StandardCharsets.UTF_8);
            }else if(path.equals("/html401")){
                status=401;type="text/html";
                body="<!DOCTYPE html><html><body>Access denied</body></html>".getBytes(StandardCharsets.UTF_8);
            }else if(path.equals("/redirect")){
                status=302;exchange.getResponseHeaders().add("Location","https://other.example/login");
                body=new byte[0];
            }else if(path.equals("/disabled")){
                status=401;body="{\"ok\":false,\"error\":\"Urządzenie nieaktywne.\"}".getBytes(StandardCharsets.UTF_8);
            }else if(path.equals("/missingResults")){
                status=200;body="{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            }else if(path.equals("/valid")){
                status=200;body="{\"ok\":true,\"results\":[]}".getBytes(StandardCharsets.UTF_8);
            }else{
                status=500;body="server failure".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type",type);
            try(InputStream input=exchange.getRequestBody()){while(input.read()!=-1){}}
            exchange.sendResponseHeaders(status,body.length);
            try(OutputStream o=exchange.getResponseBody()){o.write(body);}
        });
        server.start();
        endpoint="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @After public void cleanup(){if(server!=null)server.stop(0);if(store!=null)store.close();}
    @Test public void rejectsHtmlWithoutExposingRawServerResponse()throws Exception {
        for(String path:new String[]{"/html","/html401"}){
            try{client.request(endpoint+path,"GET",null);fail("Expected validation failure");}
            catch(IOException ex){
                assertTrue(ex.getMessage(),ex.getMessage().contains("stronę HTML"));
                assertFalse(ex.getMessage().contains("<!DOCTYPE"));
            }
        }
    }
    @Test public void stopsCrossDomainRedirectBeforeForwardingAuthentication()throws Exception{
        try{client.request(endpoint+"/redirect","GET",null);fail("Expected redirect warning");}
        catch(IOException ex){assertTrue(ex.getMessage().contains("przekierował"));}
    }
    @Test public void passesStructuredAuthErrorAndAcceptsValidJson() throws Exception{
        try{client.request(endpoint+"/disabled","GET",null);fail("Expected auth error");}
        catch(IOException ex){assertTrue(ex.getMessage().contains("Urządzenie nieaktywne"));}
        assertTrue(client.request(endpoint+"/valid","POST",new JSONObject().put("operations",new JSONArray())).optBoolean("ok"));
    }
    @Test public void rejectsHttp500WithoutJsonObjectConversionError() throws Exception{
        try{client.request(endpoint+"/error","GET",null);fail("Expected server error");}
        catch(IOException ex){assertTrue(ex.getMessage().contains("HTTP 500"));}
    }
}
