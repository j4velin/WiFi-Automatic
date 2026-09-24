package de.j4velin.wifiAutoOff;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

@TargetApi(Build.VERSION_CODES.O)
public class APILevel26ForegroundService extends Service {

    private static final String CHANNEL_ID = "foregroundService";

    public static void start(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context);
            context.startForegroundService(new Intent(context, APILevel26ForegroundService.class));
        }
    }

    private static void createNotificationChannel(final Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "WiFi Automatic",
                NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        channel.setDescription(context.getString(R.string.notification_desc));
        notificationManager.createNotificationChannel(channel);
    }

    private static final IntentFilter[] EVENT_FILTERS =
            new IntentFilter[]{new IntentFilter("android.net.wifi.STATE_CHANGE"),
                    new IntentFilter("android.net.wifi.WIFI_STATE_CHANGED"),
                    new IntentFilter("android.net.wifi.p2p.CONNECTION_STATE_CHANGE"),
                    new IntentFilter("android.intent.action.ACTION_POWER_CONNECTED"),
                    new IntentFilter("android.intent.action.ACTION_POWER_DISCONNECTED")};

    private final BroadcastReceiver eventReceiver = new Receiver();
    private final BroadcastReceiver userPresentReceiver = new Receiver();
    private final BroadcastReceiver screenReceiver =
            new ScreenChangeDetector.ScreenOffReceiver();

    private boolean eventReceiversRegistered;
    private boolean screenReceiversRegistered;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Logger.log("API26ForegroundService onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(42,
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.icon_black)
                        .setContentTitle("WiFi Automatic")
                        .setContentText(getString(R.string.hide_notification))
                        .setOngoing(true)
                        .setContentIntent(PendingIntent.getActivity(this, 1,
                                new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                                        .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID),
                                PendingIntent.FLAG_IMMUTABLE))
                        .build());

        synchronized (CHANNEL_ID) {
            registerEventReceivers();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            updateScreenReceivers(prefs);

            if (intent == null) {
                replayCurrentScreenState(prefs);
            }
        }
        return START_STICKY;
    }

    private void registerEventReceivers() {
        if (eventReceiversRegistered) return;

        for (IntentFilter filter : EVENT_FILTERS) {
            registerReceiver(eventReceiver, filter);
        }
        eventReceiversRegistered = true;
    }

    private void updateScreenReceivers(final SharedPreferences prefs) {
        boolean needed = prefs.getBoolean("off_screen_off", true) ||
                prefs.getBoolean("on_unlock", true) ||
                prefs.getBoolean("on_screen_on", false);

        if (needed && !screenReceiversRegistered) {
            IntentFilter screenFilter = new IntentFilter();
            screenFilter.addAction(Intent.ACTION_SCREEN_ON);
            screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(screenReceiver, screenFilter);
            registerReceiver(userPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
            screenReceiversRegistered = true;
        } else if (!needed && screenReceiversRegistered) {
            unregisterSafely(screenReceiver);
            unregisterSafely(userPresentReceiver);
            screenReceiversRegistered = false;
        }
    }

    private void replayCurrentScreenState(final SharedPreferences prefs) {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null) return;

        if (powerManager.isInteractive()) {
            if (prefs.getBoolean("on_screen_on", false)) {
                sendBroadcast(new Intent(this, Receiver.class)
                        .setAction(ScreenChangeDetector.SCREEN_ON_ACTION));
            }
        } else if (prefs.getBoolean("off_screen_off", true)) {
            sendBroadcast(new Intent(this, Receiver.class)
                    .setAction(ScreenChangeDetector.SCREEN_OFF_ACTION));
        }
    }

    private void unregisterSafely(final BroadcastReceiver receiver) {
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered.
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (BuildConfig.DEBUG) Logger.log("API26ForegroundService onDestroy");
        synchronized (CHANNEL_ID) {
            if (eventReceiversRegistered) {
                unregisterSafely(eventReceiver);
                eventReceiversRegistered = false;
            }
            if (screenReceiversRegistered) {
                unregisterSafely(screenReceiver);
                unregisterSafely(userPresentReceiver);
                screenReceiversRegistered = false;
            }
        }
    }
}
