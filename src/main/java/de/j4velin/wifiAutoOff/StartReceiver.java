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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * BroadcastReceiver which receives BOOT_COMPLETE & PACKAGE_REPLACED and then
 * starts all necessary timers
 *
 * @see Start
 */
public class StartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (BuildConfig.DEBUG) Logger.log("received: " + intent.getAction());
        if (!Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction()) ||
                intent.getDataString().contains(context.getPackageName())) {
            context.startService(new Intent(context, LogDeleteService.class));
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // AC already connected on boot?
            Intent batteryIntent =
                    context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            if (plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                    plugged == BatteryManager.BATTERY_PLUGGED_USB) {
                context.sendBroadcast(new Intent(context, Receiver.class)
                        .setAction(Intent.ACTION_POWER_CONNECTED));
            }
        }
        Start.start(context);
    }

}
