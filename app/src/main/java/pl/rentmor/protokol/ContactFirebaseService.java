package pl.rentmor.protokol;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

/** Data-only FCM messages: show a privacy-safe notification immediately. */
public final class ContactFirebaseService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        PushRegistrar.refreshedToken(getApplicationContext(),token);
    }
    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Map<String,String> data=message.getData();
        if(!"contact_message".equals(data.get("type")))return;
        String id=data.get("message_id");
        if(id==null||id.isEmpty()||id.length()>128)return;
        MessageMonitor.notifyPush(getApplicationContext(),id);
        // Do not fetch private message text or start lengthy network work in FCM callback.
        // The authenticated inbox loads on tap; periodic polling remains a fallback.
    }
}
