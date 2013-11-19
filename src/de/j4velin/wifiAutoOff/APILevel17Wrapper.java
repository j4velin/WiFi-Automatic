package de.j4velin.wifiAutoOff;

import android.annotation.TargetApi;
import android.content.Context;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

@TargetApi(17)
public abstract class APILevel17Wrapper {

	static boolean sleepPolicySetToNever(Context c) throws SettingNotFoundException {
		return Settings.Global.WIFI_SLEEP_POLICY_NEVER == Settings.Global.getInt(c.getContentResolver(),
				Settings.Global.WIFI_SLEEP_POLICY);
	}

}
