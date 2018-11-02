package de.j4velin.wifiAutoOff;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

public final class Util {
    private Util() {
    }

    public static void setTimer(final Context context, int type, long time, PendingIntent intent) {
        AlarmManager am = ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
        if (Build.VERSION.SDK_INT >= 23) {
            APILevel23Wrapper.setAlarmWhileIdle(am, type, time, intent);
        } else if (Build.VERSION.SDK_INT >= 19) {
            APILevel19Wrapper.setExactTimer(am, type, time, intent);
        } else {
            am.set(type, time, intent);
        }
    }
}
