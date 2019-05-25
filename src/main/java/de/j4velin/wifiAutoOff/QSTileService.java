package de.j4velin.wifiAutoOff;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

@TargetApi(Build.VERSION_CODES.N)
public class QSTileService extends TileService {
    @Override
    public void onClick() {
        if (BuildConfig.DEBUG) Logger.log("QSTile click -> change enable state");
        boolean isEnabled = getPackageManager().getComponentEnabledSetting(
                new ComponentName(QSTileService.this, Receiver.class)) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        Preferences.changeEnableState(getApplicationContext(), !isEnabled);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        boolean isEnabled = getPackageManager().getComponentEnabledSetting(
                new ComponentName(QSTileService.this, Receiver.class)) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        Tile tile = getQsTile();
        tile.setState(isEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
