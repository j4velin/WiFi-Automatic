package de.j4velin.wifiAutoOff;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Service to call {@link Log#deleteOldLogs(Context, long)} every {@link Log#KEEP_DURATION} ms
 */
public class LogDeleteService extends IntentService {

    public LogDeleteService() {
        super("LogDeleteService");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        Log.deleteOldLogs(this, Log.KEEP_DURATION);
        ((AlarmManager) getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, System.currentTimeMillis() + Log.KEEP_DURATION, PendingIntent
                        .getService(this, 0, new Intent(this, LogDeleteService.class),
                                PendingIntent.FLAG_UPDATE_CURRENT));
    }
}
