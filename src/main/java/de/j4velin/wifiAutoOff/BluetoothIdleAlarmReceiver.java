package de.j4velin.wifiAutoOff;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BluetoothIdleAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        BluetoothIdleReceiver.handleTimer(context, Receiver.getSharedPreferences(context));
    }
}
