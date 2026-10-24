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
        context.getSharedPreferences("rentmor",0).edit().remove(BASELINE_KEY).apply();
        new LocalStore(context).clearMessages();
    }
    /** Requires authenticated admin cookie and calls the server only on a background thread. */
    public static void poll(Context context) throws Exception {
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
            if(fresh.length()>0)notifyNew(context,fresh);
        }
    }
    /** FCM messages contain only a message ID; never display private contact details on the lockscreen. */
    public static void notifyPush(Context context,String messageId) {
        android.content.SharedPreferences p=context.getSharedPreferences("rentmor",0);
        if(!"admin".equals(p.getString("role",""))||p.getString("cookie","").isEmpty())return;
        if(messageId==null||messageId.isEmpty())return;
        if(messageId.equals(p.getString("last_fcm_message_id","")))return;
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
