package de.j4velin.wifiAutoOff;

import android.net.wifi.p2p.WifiP2pInfo;

/**
 * Created by j4velin on 28.08.2015.
 */
public class APILevel14Wrapper {
    public static boolean groupFormed(final WifiP2pInfo info) {
        return info.groupFormed;
    }
}
