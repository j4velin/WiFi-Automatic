package de.j4velin.wifiAutoOff;

import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class StatusPreference extends Preference {

    private ImageView image;
    private TextView title;
    private TextView sub1;
    private TextView sub2;

    public StatusPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(final ViewGroup parent) {
        super.onCreateView(parent);
        LayoutInflater li =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = li.inflate(R.layout.statepreference, parent, false);
        image = v.findViewById(R.id.icon);
        title = v.findViewById(R.id.title);
        sub1 = v.findViewById(R.id.sub1);
        sub2 = v.findViewById(R.id.sub2);
        update();
        return v;
    }

    /**
     * Called to update the status preference
     */
    void update() {
        WifiManager wm = (WifiManager) getContext().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        boolean connected = ((ConnectivityManager) getContext().getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE))
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
        if (title == null) return;
        if (wm.isWifiEnabled()) {
            if (!connected) {
                title.setText(R.string.no_connected);
                image.setColorFilter(Color.DKGRAY);
                sub1.setText(R.string.click_to_select_network);
                sub2.setVisibility(View.GONE);
            } else {
                WifiInfo wi = wm.getConnectionInfo();
                String ssid = wi.getSSID();
                if (ssid.contains("unknown ssid")) {
                    // retrieve the SSID might require location permission on some Android versions
                    ssid = getContext().getString(R.string.event_connected);
                }
                title.setText(ssid);
                image.setColorFilter(getContext().getResources().getColor(R.color.colorAccent));
                updateSignal();
                int ip = wi.getIpAddress();
                sub2.setText(String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff),
                        (ip >> 16 & 0xff), (ip >> 24 & 0xff)));
                sub2.setVisibility(View.VISIBLE);
            }
        } else {
            title.setText(R.string.wifi_not_enabled);
            image.setColorFilter(Color.LTGRAY);
            sub1.setText(R.string.click_to_turn_on);
            sub2.setVisibility(View.GONE);
        }
    }

    /**
     * Called to update the WiFi signal text
     *
     * @return true, if connected to a WiFi network
     */
    boolean updateSignal() {
        WifiManager wm = (WifiManager) getContext().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        boolean connected = ((ConnectivityManager) getContext().getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE))
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();
        if (sub1 == null) return wm.isWifiEnabled() && connected;
        if (wm.isWifiEnabled() && connected) {
            WifiInfo wi = wm.getConnectionInfo();
            try {
                sub1.setText(WifiManager.calculateSignalLevel(wi.getRssi(), 100) + "% - " +
                        wi.getLinkSpeed() + " Mbps");
            } catch (ArithmeticException e) {
                // might happen on Android 2.3 devices: https://code.google.com/p/android/issues/detail?id=2555
                sub1.setText(wi.getLinkSpeed() + " Mbps");
            }
            return true;
        } else {
            return false;
        }
    }
}
