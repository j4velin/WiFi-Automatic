package de.j4velin.wifiAutoOff;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.widget.Toast;

/**
 * Class for receiving various events and react on them.
 *
 */
public class Receiver extends BroadcastReceiver {

	static final boolean LOG = false;

	private static final int TIMER_SCREEN_OFF = 1;
	private static final int TIMER_NO_NETWORK = 2;
	static final int TIMER_ON_AT = 3;
	static final int TIMER_OFF_AT = 4;
	static final int TIMER_ON_EVERY = 5;

	private static final int TIMEOUT_NO_NETWORK = 1;
	static final int TIMEOUT_SCREEN_OFF = 10;
	static final int ON_EVERY_TIME = 2;
	static final String ON_AT_TIME = "8:00";
	static final String OFF_AT_TIME = "22:00";

	/**
	 * Starts one of the timersto turn WiFi off
	 * 
	 * @param context
	 *            the context
	 * @param id
	 *            TIMER_SCREEN_OFF or TIMER_NO_NETWORK
	 * @param time
	 *            in min
	 */
	private void startTimer(Context context, int id, int time) {
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Intent timerIntent = new Intent(context, Receiver.class).putExtra("timer", true).setAction(
				id == TIMER_SCREEN_OFF ? "SCREEN_OFF_TIMER" : "NO_NETWORK_TIMER");
		am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60000 * time,
				PendingIntent.getBroadcast(context, id, timerIntent, 0));
	}

	/**
	 * Stops the timer
	 * 
	 * @param context
	 *            the context
	 * @param id
	 *            TIMER_SCREEN_OFF or TIMER_NO_NETWORK
	 */
	private void stopTimer(Context context, int id) {
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Intent timerIntent = new Intent(context, Receiver.class).putExtra("timer", true).setAction(
				id == TIMER_SCREEN_OFF ? "SCREEN_OFF_TIMER" : "NO_NETWORK_TIMER");
		am.cancel(PendingIntent.getBroadcast(context, id, timerIntent, 0));
	}

	/**
	 * Changes the WiFi state
	 * 
	 * @param context
	 * @param on true to turn WiFi on, false to turn it off
	 */
	private static void changeWiFi(Context context, boolean on) {
		if (LOG)
			android.util.Log.d("WiFiAutoOff", on ? "turning wifi on" : "disabling wifi");
		try {
			((WifiManager) context.getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(on);
		} catch (Exception e) {
			Toast.makeText(context, "Can not change WiFi state: " + e.getClass().getName(), Toast.LENGTH_LONG).show();
		}
	}

	@SuppressLint("InlinedApi")
	@Override
	public void onReceive(final Context context, final Intent intent) {
		final String action = intent.getAction();
		if (LOG)
			android.util.Log.d("WiFiAutoOff", "received: " + action);
		if (ScreenOffDetector.SCREEN_OFF_ACTION.equals(action)) {
			// screen went off -> start TIMER_SCREEN_OFF
			startTimer(context, TIMER_SCREEN_OFF,
					PreferenceManager.getDefaultSharedPreferences(context).getInt("screen_off_timeout", TIMEOUT_SCREEN_OFF));
		} else if (Intent.ACTION_USER_PRESENT.equals(action)) {
			// user unlocked the device -> stop TIMER_SCREEN_OFF, might turn on WiFi
			stopTimer(context, TIMER_SCREEN_OFF);
			if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("on_unlock", true)) {
				stopTimer(context, TIMER_NO_NETWORK);
				changeWiFi(context, true);
			}
		} else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
			final NetworkInfo nwi = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
			if (nwi == null)
				return;
			if (LOG)
				android.util.Log.d("WiFiAutoOff", "new state: "+nwi.getState());
			if (nwi.isConnected()) {
				stopTimer(context, TIMER_NO_NETWORK);
				if (!((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isScreenOn()
						&& PreferenceManager.getDefaultSharedPreferences(context).getBoolean("off_screen_off", true)) {
					// screen off -> start screen off timer
					startTimer(
							context,
							TIMER_SCREEN_OFF,
							PreferenceManager.getDefaultSharedPreferences(context).getInt("screen_off_timeout",
									TIMEOUT_SCREEN_OFF));
				}
			} else if (nwi.getState().equals(NetworkInfo.State.DISCONNECTED)
					|| nwi.getState().equals(NetworkInfo.State.DISCONNECTING)) {
				if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("off_no_network", true)) {
					startTimer(context, TIMER_NO_NETWORK, TIMEOUT_NO_NETWORK);
				}
			}
		} else if (intent.hasExtra("timer")) {
			// one of the timers expired -> turn wifi off
			changeWiFi(context, false);
		} else if (intent.hasExtra("changeWiFi")) {
			// TODO Is this still necessary?
			changeWiFi(context, intent.getBooleanExtra("changeWiFi", false));
		} else if (intent.getAction().equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
			// wifi direct connection changed
			NetworkInfo nwi = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
			if (LOG)
				android.util.Log.d("WiFiAutoOff", "new state: "+nwi.getState());
			if (nwi.isConnected()) {
				stopTimer(context, TIMER_NO_NETWORK);
			} else {
				if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("off_no_network", true)) {
					startTimer(context, TIMER_NO_NETWORK, TIMEOUT_NO_NETWORK);
				}
			}
		}
	}
}
