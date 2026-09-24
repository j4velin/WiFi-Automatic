package de.j4velin.wifiAutoOff;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.lang.reflect.Method;

public class BluetoothIdleReceiver extends BroadcastReceiver {

    private static final String TIMER_ACTION =
            "de.j4velin.wifiAutoOff.BLUETOOTH_IDLE_TIMER";
    private static final int TIMER_REQUEST_CODE = 6;
    private static final int TIMEOUT_MINUTES = 10;

    private static PendingIntent getTimerIntent(final Context context, int flags) {
        return PendingIntent.getBroadcast(context, TIMER_REQUEST_CODE,
                new Intent(context, BluetoothIdleAlarmReceiver.class).setAction(TIMER_ACTION),
                flags);
    }

    private static void startTimer(final Context context) {
        PendingIntent existing = getTimerIntent(context,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (existing != null) return;

        PendingIntent timer = getTimerIntent(context,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Util.setTimer(context, AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60000L * TIMEOUT_MINUTES, timer);
        Log.insert(context, context.getString(R.string.event_bluetooth_idle_timer,
                TIMEOUT_MINUTES), Log.Type.TIMER);
    }

    private static void stopTimer(final Context context) {
        PendingIntent timer = getTimerIntent(context,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (timer == null) return;

        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(timer);
        timer.cancel();
    }

    static void updateTimer(final Context context, final SharedPreferences prefs) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (prefs.getBoolean("bluetooth_auto_off_idle", false) &&
                adapter != null && adapter.isEnabled() && !isConnected()) {
            startTimer(context);
        } else {
            stopTimer(context);
        }
    }

    static void updateEnabledState(final Context context, final SharedPreferences prefs) {
        boolean enabled = prefs.getBoolean("bluetooth_auto_off_idle", false);
        if (!enabled) stopTimer(context);

        PackageManager packageManager = context.getPackageManager();
        int componentState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(
                new ComponentName(context, BluetoothIdleReceiver.class), componentState,
                PackageManager.DONT_KILL_APP);
        packageManager.setComponentEnabledSetting(
                new ComponentName(context, BluetoothIdleAlarmReceiver.class), componentState,
                PackageManager.DONT_KILL_APP);

        if (enabled) updateTimer(context, prefs);
    }

    @SuppressWarnings("deprecation")
    private static boolean isConnected() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return false;

        try {
            int[] profiles = {
                    BluetoothProfile.HEADSET,
                    BluetoothProfile.A2DP,
                    BluetoothProfile.GATT
            };
            for (int profile : profiles) {
                if (adapter.getProfileConnectionState(profile) ==
                        BluetoothProfile.STATE_CONNECTED) {
                    return true;
                }
            }

            for (BluetoothDevice device : adapter.getBondedDevices()) {
                try {
                    Method method = device.getClass().getMethod("isConnected", (Class[]) null);
                    if ((boolean) method.invoke(device, (Object[]) null)) return true;
                } catch (Exception ignored) {
                    // Hidden API fallback for profiles not exposed above.
                }
            }
        } catch (SecurityException e) {
            // If connection state cannot be checked, keep Bluetooth on.
            if (BuildConfig.DEBUG) Logger.log(e);
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static void disableBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;
        try {
            adapter.disable();
        } catch (SecurityException e) {
            if (BuildConfig.DEBUG) Logger.log(e);
        }
    }

    static void handleTimer(final Context context, final SharedPreferences prefs) {
        stopTimer(context);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (prefs.getBoolean("bluetooth_auto_off_idle", false) &&
                adapter != null && adapter.isEnabled() && !isConnected()) {
            Log.insert(context, context.getString(R.string.event_bluetooth_idle_off,
                    TIMEOUT_MINUTES), Log.Type.TIMER);
            disableBluetooth();
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String action = intent.getAction();
        SharedPreferences prefs = Receiver.getSharedPreferences(context);

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            stopTimer(context);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            updateTimer(context, prefs);
        } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_ON) {
                updateTimer(context, prefs);
            } else if (state == BluetoothAdapter.STATE_TURNING_OFF ||
                    state == BluetoothAdapter.STATE_OFF) {
                stopTimer(context);
            }
        }
    }
}
