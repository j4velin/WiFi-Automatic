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

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

/**
 * Background service which detects SCREEN_OFF events.
 * 
 * Necessary for the 'turn wifi off if screen is off' option
 * 
 */
public class ScreenChangeDetector extends Service {

	final static String SCREEN_ON_ACTION = "SCREEN_ON";
	final static String SCREEN_OFF_ACTION = "SCREEN_OFF";

	private static BroadcastReceiver br;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		if (br == null) {
			if (Logger.LOG)
				Logger.log("creating screen receiver");
			br = new ScreenOffReceiver();
			IntentFilter intf = new IntentFilter();
			intf.addAction(Intent.ACTION_SCREEN_ON);
			intf.addAction(Intent.ACTION_SCREEN_OFF);
			registerReceiver(br, intf);
		}
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (Logger.LOG)
			Logger.log("destroying screen receiver");
		if (br != null) {
			try {
				unregisterReceiver(br);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		br = null;
	}

	private class ScreenOffReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
				sendBroadcast(new Intent(context, Receiver.class).setAction(SCREEN_OFF_ACTION));
			} else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
				sendBroadcast(new Intent(context, Receiver.class).setAction(SCREEN_ON_ACTION));
			}
		}

	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onTaskRemoved(final Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		// Workaround for "Android 4.4.2 ignoring START_STICKY bug"
		// Restart service in 500 ms
		((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC, System.currentTimeMillis() + 500,
				PendingIntent.getService(this, 0, new Intent(this, ScreenChangeDetector.class), 0));
	}
}
