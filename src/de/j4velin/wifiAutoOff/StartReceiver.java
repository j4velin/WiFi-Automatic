package de.j4velin.wifiAutoOff;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StartReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if (Receiver.LOG)
			android.util.Log.d("WiFiAutoOff", "received: "+intent.getAction());
		Start.start(context);
	}

}
