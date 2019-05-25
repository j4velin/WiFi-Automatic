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
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;

@TargetApi(Build.VERSION_CODES.O)
public class APILevel26ForegroundService extends Service {

    private final static String CHANNEL_ID = "foregroundService";

    public static void start(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context);
            context.startForegroundService(new Intent(context, APILevel26ForegroundService.class));
        }
    }

    private static void createNotificationChannel(final Context context) {
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "WiFi Automatic",
                NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        channel.setDescription(context.getString(R.string.notification_desc));
        mNotificationManager.createNotificationChannel(channel);
    }

    private final static BroadcastReceiver RECEIVER = new Receiver();
    private final static IntentFilter[] FILTERS =
            new IntentFilter[]{new IntentFilter("android.net.wifi.STATE_CHANGE"),
                    new IntentFilter("android.net.wifi.WIFI_STATE_CHANGED"),
                    new IntentFilter("android.net.wifi.p2p.CONNECTION_STATE_CHANGE"),
                    new IntentFilter("android.intent.action.ACTION_POWER_CONNECTED"),
                    new IntentFilter("android.intent.action.ACTION_POWER_DISCONNECTED")};

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(42,
                new NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.icon_black)
                        .setContentTitle("WiFi Automatic")
                        .setContentText(getString(R.string.hide_notification)).setContentIntent(
                        PendingIntent.getActivity(this, 1,
                                new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                                        .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID), 0))
                        .build());
        for (IntentFilter filter : FILTERS) {
            registerReceiver(RECEIVER, filter);
        }
        return START_STICKY;
    }
}
