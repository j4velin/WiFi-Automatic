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

/**
 * Class for receiving the USER_PRESENT event. Can be disabled if that option is not required
 */
public class UnlockReceiver extends BroadcastReceiver {

    public final static String USER_PRESENT_ACTION = "USER_PRESENT";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (BuildConfig.DEBUG) Logger.log("received: " + action);
        if (Intent.ACTION_USER_PRESENT.equals(action)) {
            context.sendBroadcast(
                    new Intent(context, Receiver.class).setAction(USER_PRESENT_ACTION));
        }
    }
}
