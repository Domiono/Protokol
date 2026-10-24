package pl.rentmor.protokol;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Android runs this job opportunistically, usually no more often than every 15 minutes. */
public final class MessageCheckJob extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        new Thread(()->{
            boolean retry=false;
            try { MessageMonitor.poll(getApplicationContext()); }
            catch(Exception ignored) { retry=true; }
            jobFinished(params,retry);
        },"rentmor-message-check").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
}
