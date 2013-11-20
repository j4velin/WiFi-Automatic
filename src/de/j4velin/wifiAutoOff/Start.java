package de.j4velin.wifiAutoOff;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * 
 * Utility class to set all necessary timers / start the background service
 * 
 */
public class Start {

	/**
	 * Sets all necessary timers / starts the background service depending on
	 * the user settings
	 * 
	 * @param c
	 *            the context
	 */
	static void start(Context c) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		if (prefs.getBoolean("off_screen_off", true)) {
			c.startService(new Intent(c, ScreenOffDetector.class).putExtra("start", true));
		} else {
			c.startService(new Intent(c, ScreenOffDetector.class).putExtra("start", false));
		}

		AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);

		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(System.currentTimeMillis());

		if (prefs.getBoolean("on_at", false)) {
			String[] time = prefs.getString("on_at_time", Receiver.ON_AT_TIME).split(":");

			cal.set(Calendar.HOUR_OF_DAY, Integer.valueOf(time[0]));
			cal.set(Calendar.MINUTE, Integer.valueOf(time[1]));
			cal.set(Calendar.SECOND, 0);

			if (cal.getTimeInMillis() < System.currentTimeMillis())
				cal.add(Calendar.DAY_OF_MONTH, 1);

			am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), PendingIntent.getBroadcast(c, Receiver.TIMER_ON_AT,
					new Intent(c, Receiver.class).putExtra("changeWiFi", true).setAction("ON_AT"), 0));
		} else { // stop timer
			am.cancel(PendingIntent.getBroadcast(c, Receiver.TIMER_ON_AT,
					new Intent(c, Receiver.class).putExtra("changeWiFi", true).setAction("ON_AT"), 0));
		}
		if (prefs.getBoolean("off_at", false)) {
			String[] time = prefs.getString("off_at_time", Receiver.OFF_AT_TIME).split(":");

			cal.set(Calendar.HOUR_OF_DAY, Integer.valueOf(time[0]));
			cal.set(Calendar.MINUTE, Integer.valueOf(time[1]));
			cal.set(Calendar.SECOND, 0);

			if (cal.getTimeInMillis() < System.currentTimeMillis())
				cal.add(Calendar.DAY_OF_MONTH, 1);

			am.set(AlarmManager.RTC_WAKEUP,
					cal.getTimeInMillis(),
					PendingIntent.getBroadcast(c, Receiver.TIMER_OFF_AT,
							new Intent(c, Receiver.class).putExtra("changeWiFi", false).setAction("OFF_AT"), 0));
		} else { // stop timer
			am.cancel(PendingIntent.getBroadcast(c, Receiver.TIMER_OFF_AT,
					new Intent(c, Receiver.class).putExtra("changeWiFi", false).setAction("OFF_AT"), 0));
		}
		if (prefs.getBoolean("on_every", false)) {
			am.setInexactRepeating(
					AlarmManager.RTC_WAKEUP,
					System.currentTimeMillis(),
					AlarmManager.INTERVAL_HOUR * prefs.getInt("on_every_time", Receiver.ON_EVERY_TIME),
					PendingIntent.getBroadcast(c, Receiver.TIMER_ON_EVERY,
							new Intent(c, Receiver.class).putExtra("changeWiFi", true).setAction("ON_EVERY"), 0));
		} else { // stop timer
			am.cancel(PendingIntent.getBroadcast(c, Receiver.TIMER_ON_EVERY,
					new Intent(c, Receiver.class).putExtra("changeWiFi", true).setAction("ON_EVERY"), 0));
		}

		if (Receiver.LOG)
			android.util.Log.d("WiFiAutoOff", "all timers set/cleared");
	}
}
