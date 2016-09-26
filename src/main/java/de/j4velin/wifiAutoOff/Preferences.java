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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.annotation.LayoutRes;
import android.support.v4.content.PermissionChecker;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.List;

public class Preferences extends PreferenceActivity {

    private final static int[] time_values = {5, 15, 30, 60, 120, 300, 600};

    private StatusPreference status;
    private AppCompatDelegate mDelegate;

    /**
     * whether the location settings should be enabled by the enable/disable app switch
     */
    private boolean disableLocationSettings = false;

    private final Handler handler = new Handler();
    private final Runnable signalUpdater = new Runnable() {
        @Override
        public void run() {
            if (status.updateSignal()) handler.postDelayed(signalUpdater, 1000);
        }
    };
    private final BroadcastReceiver stateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            handler.removeCallbacks(signalUpdater);
            status.update();
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                final NetworkInfo nwi = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (nwi == null) return;
                if (nwi.isConnected()) {
                    // seems to still take some time until NetworkInfo::isConnected returns true
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            status.update();
                        }
                    }, 2000);
                    handler.postDelayed(signalUpdater, 2000);
                }
            }
        }
    };

    @SuppressLint("NewApi")
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        boolean isEnabled = getPackageManager()
                .getComponentEnabledSetting(new ComponentName(Preferences.this, Receiver.class)) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        if (Build.VERSION.SDK_INT < 14) {
            menu.findItem(R.id.enable)
                    .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(final MenuItem menuItem) {
                            boolean isEnabled = getPackageManager().getComponentEnabledSetting(
                                    new ComponentName(Preferences.this, Receiver.class)) !=
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                            changeEnableState(!isEnabled);
                            return true;
                        }
                    });
        } else {
            CompoundButton enable =
                    (CompoundButton) MenuItemCompat.getActionView(menu.findItem(R.id.enable));
            enable.setChecked(isEnabled);
            enable.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    changeEnableState(isChecked);
                }
            });
        }

        // disable initially if not checked
        if (!isEnabled) {
            enableSettings(false);
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private void enableSettings(final boolean enable) {
        PreferenceScreen ps = getPreferenceScreen();
        // start at 1 to skip "status" preference
        for (int i = 1; i < ps.getPreferenceCount(); i++) {
            ps.getPreference(i).setEnabled(enable);
        }
        if (enable && disableLocationSettings) {
            // disable locations again if disableLocationSettings is set
            findPreference("locations").setEnabled(false);
        }
    }

    private void changeEnableState(final boolean enable) {
        enableSettings(enable);
        getPackageManager()
                .setComponentEnabledSetting(new ComponentName(Preferences.this, Receiver.class),
                        enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
        if (!enable) stopService(new Intent(Preferences.this, ScreenChangeDetector.class));
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(Preferences.this, ScreenChangeDetector.class),
                enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // action bar overflow menu
        switch (item.getItemId()) {
            case R.id.enable:
                break;
            case R.id.action_wifi_adv:
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_IP_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.settings_not_found_, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_apps:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://search?q=pub:j4velin"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException anf) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "https://play.google.com/store/apps/developer?id=j4velin"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (ActivityNotFoundException anf2) {
                        Toast.makeText(this,
                                "No browser found to load https://play.google.com/store/apps/developer?id=j4velin",
                                Toast.LENGTH_LONG).show();
                    }
                }
                break;
            case R.id.action_donate:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://j4velin.de/donate.php"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException anf) {
                    Toast.makeText(this, "No browser found to load http://j4velin.de/donate.php",
                            Toast.LENGTH_LONG).show();
                }
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * Checks if the WiFi sleep policy will keep WiFi when the screen goes off
     *
     * @param c Context
     * @return true if WiFi will be kept on during sleep
     */
    @SuppressWarnings("deprecation")
    private static boolean keepWiFiOn(Context c) {
        try {
            return ((android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                    APILevel17Wrapper.sleepPolicySetToNever(c)) ||
                    (Settings.System.WIFI_SLEEP_POLICY_NEVER == Settings.System
                            .getInt(c.getContentResolver(), Settings.System.WIFI_SLEEP_POLICY)));
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        final CheckBoxPreference no_network_off =
                (CheckBoxPreference) findPreference("off_no_network");
        no_network_off.setSummary(getString(R.string.for_at_least,
                PreferenceManager.getDefaultSharedPreferences(this)
                        .getInt("no_network_timeout", Receiver.TIMEOUT_NO_NETWORK)));
        handler.postDelayed(signalUpdater, 1000);
        IntentFilter ifilter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        ifilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(stateChangedReceiver, ifilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        Start.start(this);
        handler.removeCallbacks(signalUpdater);
        unregisterReceiver(stateChangedReceiver);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 23 && PermissionChecker
                .checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PermissionChecker.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }

        status = (StatusPreference) findPreference("status");
        status.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                boolean connected =
                        ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE))
                                .getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
                if (wm.isWifiEnabled() && !connected) {
                    try {
                        startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) {
                        Toast.makeText(Preferences.this, R.string.settings_not_found_,
                                Toast.LENGTH_SHORT).show();
                    }
                } else if (!wm.isWifiEnabled()) {
                    try {
                        wm.setWifiEnabled(true);
                    } catch (SecurityException ex) {
                        ex.printStackTrace();
                        Toast.makeText(Preferences.this, "No permission to enable WiFi",
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    try {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) {
                        Toast.makeText(Preferences.this, R.string.settings_not_found_,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
        });

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        final CheckBoxPreference screen_off = (CheckBoxPreference) findPreference("off_screen_off");
        screen_off.setSummary(getString(R.string.for_at_least,
                prefs.getInt("screen_off_timeout", Receiver.TIMEOUT_SCREEN_OFF)));

        if (!keepWiFiOn(this)) {
            screen_off.setChecked(false);
        }

        screen_off.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                if ((Boolean) newValue) {
                    if (!keepWiFiOn(Preferences.this)) {
                        new AlertDialog.Builder(Preferences.this).setMessage(R.string.sleep_policy)
                                .setPositiveButton(R.string.adv_wifi_settings,
                                        new OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                try {
                                                    startActivity(new Intent(
                                                            Settings.ACTION_WIFI_IP_SETTINGS)
                                                            .addFlags(
                                                                    Intent.FLAG_ACTIVITY_NEW_TASK));
                                                } catch (Exception e) {
                                                    Toast.makeText(Preferences.this,
                                                            R.string.settings_not_found_,
                                                            Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        })
                                .setNegativeButton(android.R.string.cancel, new OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                }).create().show();
                        return false;
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 11) {
                        APILevel11Wrapper.showNumberPicker(Preferences.this, prefs, screen_off,
                                R.string.for_at_least, 1, 60,
                                getString(R.string.minutes_before_turning_off_wifi_),
                                "screen_off_timeout", Receiver.TIMEOUT_SCREEN_OFF, false);
                    } else {
                        showPre11NumberPicker(Preferences.this, prefs, screen_off,
                                R.string.for_at_least, 1, 60,
                                getString(R.string.minutes_before_turning_off_wifi_),
                                "screen_off_timeout", Receiver.TIMEOUT_SCREEN_OFF, false);
                    }
                }
                return true;
            }
        });

        findPreference("off_no_network")
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(final Preference preference,
                                                      final Object newValue) {
                        if ((Boolean) newValue) {
                            if (android.os.Build.VERSION.SDK_INT >= 11) {
                                APILevel11Wrapper
                                        .showNumberPicker(Preferences.this, prefs, preference,
                                                R.string.for_at_least, 1, 60, getString(
                                                        R.string.minutes_before_turning_off_wifi_),
                                                "no_network_timeout", Receiver.TIMEOUT_NO_NETWORK,
                                                false);
                            } else {
                                showPre11NumberPicker(Preferences.this, prefs, preference,
                                        R.string.for_at_least, 1, 60,
                                        getString(R.string.minutes_before_turning_off_wifi_),
                                        "no_network_timeout", Receiver.TIMEOUT_NO_NETWORK, false);
                            }
                        }
                        return true;
                    }
                });

        final CheckBoxPreference on_at = (CheckBoxPreference) findPreference("on_at");
        on_at.setTitle(
                getString(R.string.at_summary, prefs.getString("on_at_time", Receiver.ON_AT_TIME)));
        on_at.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                if ((Boolean) newValue) {
                    String[] time = prefs.getString("on_at_time", Receiver.ON_AT_TIME).split(":");
                    final TimePickerDialog dialog =
                            new TimePickerDialog(Preferences.this, new OnTimeSetListener() {
                                @Override
                                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                    prefs.edit().putString("on_at_time",
                                            hourOfDay + ":" + (minute < 10 ? "0" + minute : minute))
                                            .commit();
                                    on_at.setTitle(getString(R.string.at_summary, hourOfDay + ":" +
                                            (minute < 10 ? "0" + minute : minute)));
                                }
                            }, Integer.parseInt(time[0]), Integer.parseInt(time[1]), true);
                    dialog.setOnCancelListener(new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            on_at.setChecked(false);
                        }
                    });
                    dialog.setTitle(getString(R.string.turn_wifi_on_at_));
                    dialog.show();
                }
                return true;
            }
        });

        final CheckBoxPreference off_at = (CheckBoxPreference) findPreference("off_at");
        off_at.setTitle(getString(R.string.at_summary,
                prefs.getString("off_at_time", Receiver.OFF_AT_TIME)));
        off_at.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                if ((Boolean) newValue) {
                    String[] time = prefs.getString("off_at_time", Receiver.OFF_AT_TIME).split(":");
                    final TimePickerDialog dialog =
                            new TimePickerDialog(Preferences.this, new OnTimeSetListener() {
                                @Override
                                public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                    prefs.edit().putString("off_at_time",
                                            hourOfDay + ":" + (minute < 10 ? "0" + minute : minute))
                                            .commit();
                                    off_at.setTitle(getString(R.string.at_summary, hourOfDay + ":" +
                                            (minute < 10 ? "0" + minute : minute)));
                                }
                            }, Integer.parseInt(time[0]), Integer.parseInt(time[1]), true);
                    dialog.setOnCancelListener(new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            off_at.setChecked(false);
                        }
                    });
                    dialog.setTitle(getString(R.string.turn_wifi_off_at_));
                    dialog.show();
                }
                return true;
            }
        });

        final Preference on_every = findPreference("on_every");
        final String[] time_names = getResources().getStringArray(R.array.time_names);
        // default 2 hours
        on_every.setTitle(
                getString(R.string.every_summary, prefs.getString("on_every_str", time_names[4])));
        on_every.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                if ((Boolean) newValue) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(Preferences.this);
                    builder.setTitle(R.string.turn_wifi_on_every)
                            .setItems(time_names, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.edit().putInt("on_every_time_min", time_values[which])
                                            .putString("on_every_str", time_names[which]).commit();
                                    on_every.setTitle(
                                            getString(R.string.every_summary, time_names[which]));
                                }
                            });
                    builder.create().show();
                }
                return true;
            }
        });

        Preference locations = findPreference("locations");
        if (BuildConfig.FLAVOR.equals("play")) {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK)) {
                locations.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(final Preference preference) {
                        startActivity(new Intent(Preferences.this, Locations.class));
                        return true;
                    }
                });
            } else {
                locations.setEnabled(false);
                disableLocationSettings = true;
            }
        } else {
            locations.setSummary("Not available in F-Droid version");
            locations.setEnabled(false);
            disableLocationSettings = true;
        }

        final Preference power = findPreference("power_connected");
        power.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                if ((boolean) newValue) {
                    Intent battery =
                            registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (battery != null &&
                            battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) > 0) {
                        // already connected to external power
                        prefs.edit().putBoolean("ignore_screen_off", true).commit();
                    }
                } else {
                    prefs.edit().putBoolean("ignore_screen_off", false).commit();
                }
                return true;
            }
        });

        findPreference("log")
                .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(final Preference preference) {
                        getLogDialog().show();
                        return true;
                    }
                });
    }

    private static void showPre11NumberPicker(final Context c, final SharedPreferences prefs,
                                              final Preference p, final int summary, final int min,
                                              final int max, final String title,
                                              final String setting, final int def,
                                              final boolean changeTitle) {
        final EditText np = new EditText(c);
        np.setInputType(InputType.TYPE_CLASS_NUMBER);
        np.setText(String.valueOf(prefs.getInt(setting, def)));
        new AlertDialog.Builder(c).setTitle(title).setView(np)
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int number = -1;
                        try {
                            number = Integer.parseInt(np.getText().toString());
                        } catch (Exception e) {
                        }
                        if (number >= min && number <= max) {
                            prefs.edit().putInt(setting, number).commit();
                            if (changeTitle) p.setTitle(c.getString(summary, number));
                            else p.setSummary(c.getString(summary, number));
                        } else {
                            Toast.makeText(c,
                                    c.getString(R.string.invalid_input_number_has_to_be_, min, max),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }).create().show();
    }

    private Dialog getLogDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        List<Log.Item> items = Log.getLog(this, 50);
        if (items.isEmpty()) {
            builder.setMessage(R.string.log_empty);
        } else {
            RecyclerView list = new RecyclerView(this);
            list.setAdapter(new LogAdapter(items));
            list.setLayoutManager(new LinearLayoutManager(this));
            builder.setView(list);
            builder.setNegativeButton(R.string.delete_log, new OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialogInterface, int i) {
                    Log.deleteOldLogs(Preferences.this, 0);
                    dialogInterface.dismiss();
                }
            });
        }
        return builder.create();
    }

    private AppCompatDelegate getDelegate() {
        if (mDelegate == null) {
            mDelegate = AppCompatDelegate.create(this, null);
        }
        return mDelegate;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getDelegate().onPostCreate(savedInstanceState);
    }

    @Override
    public MenuInflater getMenuInflater() {
        return getDelegate().getMenuInflater();
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        getDelegate().setContentView(layoutResID);
    }

    @Override
    public void setContentView(View view) {
        getDelegate().setContentView(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().setContentView(view, params);
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().addContentView(view, params);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getDelegate().onPostResume();
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        getDelegate().setTitle(title);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getDelegate().onConfigurationChanged(newConfig);
    }

    @Override
    protected void onStop() {
        super.onStop();
        getDelegate().onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getDelegate().onDestroy();
    }

    public void invalidateOptionsMenu() {
        getDelegate().invalidateOptionsMenu();
    }
}