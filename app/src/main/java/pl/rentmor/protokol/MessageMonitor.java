package pl.rentmor.protokol;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.util.*;

/** Periodic OS-scheduled, authenticated message checks. No third-party push credentials. */
public final class MessageMonitor {
    public static final int JOB_ID=733891;
    private static final String CHANNEL="rentmor_contact_messages";
    private static final String BASELINE_KEY="contact_inbox_initialized_v1";
    private static final String DELIVERED_KEY="contact_delivered_push_ids_v1";
    private static final Object DELIVERY_LOCK=new Object();
    private static final int DELIVERY_HISTORY_LIMIT=128;
    private static final Object POLL_LOCK=new Object();
    private MessageMonitor(){}

    /** A channel must exist before Google Play services posts background notifications. */
    public static void ensureChannel(Context context) {
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Wiadomości ze strony RentMor",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Nowe zapytania klientów wysłane przez formularz kontaktowy.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    public static void schedule(Context context) {
        if(!"admin".equals(context.getSharedPreferences("rentmor",0).getString("role","")))return;
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null||scheduler.getPendingJob(JOB_ID)!=null)return;
        JobInfo job=new JobInfo.Builder(JOB_ID,
            new ComponentName(context,MessageCheckJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(15L*60L*1000L)
            .build();
        scheduler.schedule(job);
    }
    public static void cancel(Context context) {
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler!=null)scheduler.cancel(JOB_ID);
    }
    public static void reset(Context context) {
        cancel(context);
        context.getSharedPreferences("rentmor",0).edit()
            .remove(BASELINE_KEY).remove(DELIVERED_KEY).remove("last_fcm_message_id").apply();
        new LocalStore(context).clearMessages();
    }
    /** Persist a bounded list of FCM message IDs already notified or opened. */
    public static boolean acknowledgePush(Context context,String messageId) {
        if(messageId==null || messageId.isEmpty() || messageId.length()>128)return false;
        synchronized(DELIVERY_LOCK){
            android.content.SharedPreferences prefs=context.getSharedPreferences("rentmor",0);
            JSONArray history;
            try{history=new JSONArray(prefs.getString(DELIVERED_KEY,"[]"));}
            catch(Exception ignored){history=new JSONArray();}
            for(int i=0;i<history.length();i++)
                if(messageId.equals(history.optString(i)))return false;
            JSONArray updated=new JSONArray();
            for(int i=Math.max(0,history.length()-(DELIVERY_HISTORY_LIMIT-1));i<history.length();i++)
                updated.put(history.optString(i));
            updated.put(messageId);
            // Commit before asynchronous inbox sync starts to suppress a race on notification tap.
            prefs.edit().putString(DELIVERED_KEY,updated.toString()).commit();
            return true;
        }
    }
    /** Once a user reads the message, dismiss redundant local alerts as well. */
    public static void messageRead(Context context,String messageId) {
        acknowledgePush(context,messageId);
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager!=null)manager.cancel(4702);
    }
    /** Requires authenticated admin cookie and calls the server only on a background thread. */
    public static void poll(Context context) throws Exception {poll(context,true);}
    /** Inbox refresh loads data without generating a second notification. */
    public static void poll(Context context,boolean notifyIfNew) throws Exception {
        synchronized(POLL_LOCK) {
            android.content.SharedPreferences prefs=context.getSharedPreferences("rentmor",0);
            if(!"admin".equals(prefs.getString("role",""))||prefs.getString("cookie","").isEmpty())return;
            JSONArray response=new SyncManager(context,new LocalStore(context)).fetchMessages();
            LocalStore store=new LocalStore(context);
            JSONArray fresh=store.replaceMessages(response);
            boolean initialized=prefs.getBoolean(BASELINE_KEY,false);
            if(!initialized) {
                // First authenticated snapshot is history, not a burst of "new" alerts.
                prefs.edit().putBoolean(BASELINE_KEY,true).apply();
                return;
            }
            JSONArray alertable=new JSONArray();
            for(int i=0;i<fresh.length();i++){
                JSONObject m=fresh.optJSONObject(i);
                if(m==null)continue;
                String id=m.optString("id","");
                if(!id.isEmpty() && acknowledgePush(context,id))alertable.put(m);
            }
            // FCM presents notifications in the Android tray. Polling remains a data
            // synchronization fallback, but must not duplicate FCM notifications.
            if(notifyIfNew && alertable.length()>0 && !PushRegistrar.hasRegisteredToken(context))
                notifyNew(context,alertable);
        }
    }
    /** FCM messages contain only a message ID; never display private contact details on the lockscreen. */
    public static void notifyPush(Context context,String messageId) {
        android.content.SharedPreferences p=context.getSharedPreferences("rentmor",0);
        if(!"admin".equals(p.getString("role",""))||p.getString("cookie","").isEmpty())return;
        if(messageId==null||messageId.isEmpty())return;
        if(!acknowledgePush(context,messageId))return;
        p.edit().putString("last_fcm_message_id",messageId).apply();
        notifyNew(context,new JSONArray().put(new JSONObject()));
    }
    private static void notifyNew(Context context,JSONArray fresh) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        if(!manager.areNotificationsEnabled())return;
        ensureChannel(context);
        int count=fresh.length();
        JSONObject newest=fresh.optJSONObject(count-1);
        String sender=newest==null?"klienta":newest.optString("client","klienta").trim();
        if(sender.isEmpty())sender="klienta";
        if(sender.length()>60)sender=sender.substring(0,60);
        String title=count==1?"Nowa wiadomość od "+sender:"Nowe wiadomości ze strony: "+count;
        Intent intent=new Intent(context,MainActivity.class).putExtra("open_messages",true);
        if(newest!=null && !newest.optString("id","").isEmpty())
            intent.putExtra("message_id",newest.optString("id"));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,1002,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?
            new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title)
            .setContentText("Otwórz RentMor, aby przeczytać wiadomość.")
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(pending).setAutoCancel(true);
        manager.notify(4702,b.build());
    }
}
