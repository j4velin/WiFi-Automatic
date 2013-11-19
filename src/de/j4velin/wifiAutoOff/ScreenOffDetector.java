package de.j4velin.wifiAutoOff;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

public class ScreenOffDetector extends Service {

	final static String SCREEN_OFF_ACTION = "SCREEN_OFF";

	private static BroadcastReceiver br;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		if (intent == null || intent.getBooleanExtra("start", false)) {
			if (br == null) {
				if (Receiver.LOG)
					android.util.Log.d("WiFiAutoOff", "creating screen off receiver");
				br = new ScreenOffReceiver();
				IntentFilter intf = new IntentFilter();
				intf.addAction(Intent.ACTION_SCREEN_OFF);
				registerReceiver(br, intf);
			}
			return START_STICKY;
		} else {
			stopSelf();
			return START_NOT_STICKY;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (Receiver.LOG)
			android.util.Log.d("WiFiAutoOff", "destroying screen off receiver");
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
			}
		}

	}
}
