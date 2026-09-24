WiFi Automatic
=============

This simple app can help you increase the standby time of your device: <b>WiFi Automatic</b> automatically disable your WiFi radio when you don't need it and thereby lowers the battery consumption.
It is designed to be used with WiFi-only* tablets - these devices normally don't require a constant internet connection if you're not using them and turning WiFi off can save a lot of battery power.

You can also specify to automatically turn on WiFi again, if you turn on your device. Also, the app can regularly scan for available networks to connect to and re-disable WiFi if no suitable network is found. This way, you are always connected to your WiFi network when using the device.

This app has a similiar effect like setting the "WiFi sleep policy" in Android to "always", except that you can now exactly define the timeout between turning the screen off and actually turning off WiFi.


*if your device has a cell radio, it might switch to 2G/3G which may consume more power then staying on WiFi


Download
--------

<b>You can download the app for free from <a href="https://f-droid.org/packages/de.j4velin.wifiAutoOff/">F-Droid</a>.</b>


Why is this app no longer on Google Play?
-----------------------------------------

Since Android 10 (API level 29), apps are no longer allowed to turn WiFi on or off. For every app that targets API level 29 or higher, `WifiManager.setWifiEnabled()` does nothing and always returns `false`. Apps that target an older API level can still use it, even on current Android versions.

Turning WiFi on and off is the whole point of this app, so it must keep targeting API level 28. Google Play, however, only accepts new apps and updates that target a recent API level, far above 28. An up-to-date version of WiFi Automatic therefore can't be published on Google Play, which is why new versions are only released on F-Droid.

The "turn WiFi on when entering a location" feature was removed as well: it relied on Google Play Services (geofencing and Google Maps), which are not available in the F-Droid build.
