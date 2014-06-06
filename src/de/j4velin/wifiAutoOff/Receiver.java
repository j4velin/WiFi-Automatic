/*
 * Copyright 2013 Thomas Hoffmann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.j4velin.wifiAutoOff;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.widget.Toast;

/**
 * Class for receiving various events and react on them.
 * 
 */
public class Receiver extends BroadcastReceiver {

	private static final int TIMER_SCREEN_OFF = 1;
	private static final int TIMER_NO_NETWORK = 2;
	static final int TIMER_ON_AT = 3;
	static final int TIMER_OFF_AT = 4;
	static final int TIMER_ON_EVERY = 5;

	static final int TIMEOUT_NO_NETWORK = 1;
	static final int TIMEOUT_SCREEN_OFF = 10;
	static final int ON_EVERY_TIME_MIN = 120;
	static final String ON_AT_TIME = "8:00";
	static final String OFF_AT_TIME = "22:00";

	/**
	 * Starts one of the timers to turn WiFi off
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
	 *            the context
	 * @return default SharedPreferences for given context 
	 */
	private static SharedPreferences getSharedPreferences(Context context)  {
		String prefFileName = context.getPackageName() + "_preferences";
		return context.getSharedPreferences(prefFileName, Context.MODE_PRIVATE | 0x4); // 0x4 ... Context.MODE_MULTI_PROCESS
	}

	/**
	 * Changes the WiFi state
	 * 
	 * @param context
	 * @param on
	 *            true to turn WiFi on, false to turn it off
	 */
	@SuppressWarnings("deprecation")
	private static void changeWiFi(Context context, boolean on) {
		SharedPreferences prefs = getSharedPreferences(context);
		if (on && prefs.getBoolean("airplane", true)) {
			// check for airplane mode
			try {
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 ? APILevel17Wrapper
						.isAirplaneModeOn(context) : Settings.System.getInt(
						context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON) == 1) {
					if (Logger.LOG)
						Logger.log("not turning wifi on because device is in airplane mode");
					return;
				}
			} catch (final SettingNotFoundException e) {
				// not airplane setting found? Handle like not in airplane mode
				// then
				e.printStackTrace();
			}
		}
		if (Logger.LOG)
			Logger.log(on ? "turning wifi on" : "disabling wifi");
		try {
			((WifiManager) context.getSystemService(Context.WIFI_SERVICE)).setWifiEnabled(on);
		} catch (Exception e) {
			Toast.makeText(context, "Can not change WiFi state: " + e.getClass().getName(),
					Toast.LENGTH_LONG).show();
		}
	}

	@SuppressLint("InlinedApi")
	@Override
	public void onReceive(final Context context, final Intent intent) {
		final String action = intent.getAction();
		if (Logger.LOG)
			Logger.log("received: " + action);
		SharedPreferences prefs = getSharedPreferences(context);
		if (ScreenChangeDetector.SCREEN_OFF_ACTION.equals(action) && prefs.getBoolean("off_screen_off", true)) {
			// screen went off -> start TIMER_SCREEN_OFF
			startTimer(context, TIMER_SCREEN_OFF,
					prefs.getInt("screen_off_timeout", TIMEOUT_SCREEN_OFF));
		} else if (Intent.ACTION_USER_PRESENT.equals(action)
				|| ScreenChangeDetector.SCREEN_ON_ACTION.equals(action)) {
			// user unlocked the device -> stop TIMER_SCREEN_OFF, might turn on
			// WiFi
			stopTimer(context, TIMER_SCREEN_OFF);
			if (prefs.getBoolean("on_unlock", true)) {
				stopTimer(context, TIMER_NO_NETWORK);
				changeWiFi(context, true);
			}
		} else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
			final NetworkInfo nwi = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
			if (nwi == null)
				return;
			if (Logger.LOG)
				Logger.log("new state: " + nwi.getState());
			if (nwi.isConnected()) {
				stopTimer(context, TIMER_NO_NETWORK);
				if (!((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isScreenOn()
						&& prefs.getBoolean("off_screen_off", true)) {
					// screen off -> start screen off timer
					startTimer(context, TIMER_SCREEN_OFF,
							prefs.getInt("screen_off_timeout", TIMEOUT_SCREEN_OFF));
				}
			} else if (nwi.getState().equals(NetworkInfo.State.DISCONNECTED)
					|| nwi.getState().equals(NetworkInfo.State.DISCONNECTING)) {
				if (prefs.getBoolean("off_no_network", true)) {
					startTimer(context, TIMER_NO_NETWORK, TIMEOUT_NO_NETWORK);
				}
			}
		} else if (intent.hasExtra("timer")) {
			// one of the timers expired -> turn wifi off
			changeWiFi(context, false);
		} else if (intent.hasExtra("changeWiFi")) {
			// for "ON AT" or "OFF AT" options
			changeWiFi(context, intent.getBooleanExtra("changeWiFi", false));
		} else if (intent.getAction().equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
			// wifi direct connection changed
			NetworkInfo nwi = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
			if (Logger.LOG)
				Logger.log("new state: " + nwi.getState());
			if (nwi.isConnected()) {
				stopTimer(context, TIMER_NO_NETWORK);
			} else {
				if (prefs.getBoolean("off_no_network", true)) {
					startTimer(context, TIMER_NO_NETWORK, TIMEOUT_NO_NETWORK);
				}
			}
		} else if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
			// connected to external power supply
			if (Logger.LOG)
				Logger.log("power connected setting: " + prefs.getBoolean("power_connected", false));
			if (prefs.getBoolean("power_connected", false)) {
				changeWiFi(context, true);
			}
		}
	}
}
