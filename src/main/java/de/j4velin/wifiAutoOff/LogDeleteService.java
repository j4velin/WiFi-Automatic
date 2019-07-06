package de.j4velin.wifiAutoOff;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;

/**
 * Service to call {@link Log#deleteOldLogs(Context, long)} every {@link Log#KEEP_DURATION} ms
 */
public class LogDeleteService extends JobIntentService {

    private static final int JOB_ID = 42;

    public static void enqueueJob(Context context) {
        enqueueWork(context, LogDeleteService.class, JOB_ID, new Intent());
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.deleteOldLogs(this, Log.KEEP_DURATION);
        ((AlarmManager) getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, System.currentTimeMillis() + Log.KEEP_DURATION, PendingIntent
                        .getService(this, 0, new Intent(this, LogDeleteService.class),
                                PendingIntent.FLAG_UPDATE_CURRENT));
    }
}
