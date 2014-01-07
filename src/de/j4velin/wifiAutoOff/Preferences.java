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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

public class Preferences extends PreferenceActivity {

	@SuppressLint("NewApi")
	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.menu, menu);
		if (android.os.Build.VERSION.SDK_INT >= 11) {
			CompoundButton enable = (CompoundButton) menu.findItem(R.id.enable).getActionView();
			if (android.os.Build.VERSION.SDK_INT < 14) {
				enable.setText("Enable");
			}
			enable.setChecked(getPackageManager().getComponentEnabledSetting(new ComponentName(this, Receiver.class)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
			// disable initially if not checked
			if (!enable.isChecked()) {
				@SuppressWarnings("deprecation")
				PreferenceScreen ps = getPreferenceScreen();
				for (int i = 0; i < ps.getPreferenceCount(); i++) {
					ps.getPreference(i).setEnabled(false);
				}
			}			
			enable.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					@SuppressWarnings("deprecation")
					PreferenceScreen ps = getPreferenceScreen();
					for (int i = 0; i < ps.getPreferenceCount(); i++) {
						ps.getPreference(i).setEnabled(isChecked);
					}
					getPackageManager().setComponentEnabledSetting(
							new ComponentName(Preferences.this, Receiver.class),
							isChecked ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
									: PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
					if (!isChecked)
						stopService(new Intent(Preferences.this, ScreenOffDetector.class));
					getPackageManager().setComponentEnabledSetting(
							new ComponentName(Preferences.this, ScreenOffDetector.class),
							isChecked ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
									: PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
				}
			});
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// action bar overflow menu
		switch (item.getItemId()) {
		case R.id.enable:

			break;
		case R.id.action_wifi_adv:
			try {
				startActivity(new Intent(Settings.ACTION_WIFI_IP_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			} catch (Exception e) {
				Toast.makeText(this, R.string.settings_not_found_, Toast.LENGTH_SHORT).show();
			}
			break;
		case R.id.action_apps:
			try {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pub:j4velin"))
						.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET));
			} catch (ActivityNotFoundException anf) {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/developer?id=j4velin"))
						.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET));
			}
			break;
		case R.id.action_donate:
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://j4velin-systems.de/donate.php"))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	/**
	 * Checks if the WiFi sleep policy will keep WiFi when the screen goes off
	 * 
	 * @param c
	 *            Context
	 * @return true if WiFi will be kept on during sleep
	 */
	@SuppressWarnings("deprecation")
	private static boolean keepWiFiOn(Context c) {
		try {
			return ((android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && APILevel17Wrapper
					.sleepPolicySetToNever(c)) || (Settings.System.WIFI_SLEEP_POLICY_NEVER == Settings.System.getInt(
					c.getContentResolver(), Settings.System.WIFI_SLEEP_POLICY)));
		} catch (SettingNotFoundException e) {
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public void onResume() {
		super.onResume();
		Start.start(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		Start.start(this);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

		final CheckBoxPreference screen_off = (CheckBoxPreference) findPreference("off_screen_off");
		screen_off.setSummary(getString(R.string.for_at_least, prefs.getInt("screen_off_timeout", Receiver.TIMEOUT_SCREEN_OFF)));

		final CheckBoxPreference no_network_off = (CheckBoxPreference) findPreference("off_no_network");
		no_network_off.setSummary(getString(R.string.for_at_least, 1));

		if (!keepWiFiOn(this)) {
			screen_off.setChecked(false);
		}

		screen_off.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue) {
					if (!keepWiFiOn(Preferences.this)) {
						new AlertDialog.Builder(Preferences.this).setMessage(R.string.sleep_policy)
								.setPositiveButton(R.string.adv_wifi_settings, new OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										try {
											startActivity(new Intent(Settings.ACTION_WIFI_IP_SETTINGS)
													.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
										} catch (Exception e) {
											Toast.makeText(Preferences.this, R.string.settings_not_found_, Toast.LENGTH_SHORT)
													.show();
										}
									}
								}).setNegativeButton(android.R.string.cancel, new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog, int which) {
										dialog.dismiss();
									}
								}).create().show();
						return false;
					}
					if (android.os.Build.VERSION.SDK_INT >= 11) {
						APILevel11Wrapper.showNumberPicker(Preferences.this, prefs, screen_off, R.string.for_at_least, 1, 60,
								getString(R.string.minutes_before_turning_off_wifi_), "screen_off_timeout",
								Receiver.TIMEOUT_SCREEN_OFF, false);
					} else {
						showPre11NumberPicker(Preferences.this, prefs, screen_off, R.string.for_at_least, 1, 60,
								getString(R.string.minutes_before_turning_off_wifi_), "screen_off_timeout",
								Receiver.TIMEOUT_SCREEN_OFF, false);
					}
				}
				return true;
			}
		});

		final CheckBoxPreference on_at = (CheckBoxPreference) findPreference("on_at");
		on_at.setTitle(getString(R.string.at_summary, prefs.getString("on_at_time", Receiver.ON_AT_TIME)));
		on_at.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue) {
					String[] time = prefs.getString("on_at_time", Receiver.ON_AT_TIME).split(":");
					final TimePickerDialog dialog = new TimePickerDialog(Preferences.this, new OnTimeSetListener() {
						@Override
						public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
							prefs.edit().putString("on_at_time", hourOfDay + ":" + (minute < 10 ? "0" + minute : minute))
									.commit();
							on_at.setTitle(getString(R.string.at_summary, hourOfDay + ":" + (minute < 10 ? "0" + minute : minute)));
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
		off_at.setTitle(getString(R.string.at_summary, prefs.getString("off_at_time", Receiver.OFF_AT_TIME)));
		off_at.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue) {
					String[] time = prefs.getString("off_at_time", Receiver.OFF_AT_TIME).split(":");
					final TimePickerDialog dialog = new TimePickerDialog(Preferences.this, new OnTimeSetListener() {
						@Override
						public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
							prefs.edit().putString("off_at_time", hourOfDay + ":" + (minute < 10 ? "0" + minute : minute))
									.commit();
							off_at.setTitle(getString(R.string.at_summary, hourOfDay + ":"
									+ (minute < 10 ? "0" + minute : minute)));
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

		final CheckBoxPreference on_every = (CheckBoxPreference) findPreference("on_every");
		on_every.setTitle(getString(R.string.every_summary, prefs.getInt("on_every_time", Receiver.ON_EVERY_TIME)));
		on_every.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue) {

					if (android.os.Build.VERSION.SDK_INT >= 11) {
						APILevel11Wrapper.showNumberPicker(Preferences.this, prefs, on_every, R.string.every_summary, 1, 23,
								getString(R.string.turn_wifi_on_every_hour_s_), "on_every_time", Receiver.ON_EVERY_TIME, true);
					} else {
						showPre11NumberPicker(Preferences.this, prefs, on_every, R.string.every_summary, 1, 23,
								getString(R.string.turn_wifi_on_every_hour_s_), "on_every_time", Receiver.ON_EVERY_TIME, true);
					}
				}
				return true;
			}
		});
	}

	private static void showPre11NumberPicker(final Context c, final SharedPreferences prefs, final Preference p,
			final int summary, final int min, final int max, final String title, final String setting, final int def,
			final boolean changeTitle) {
		final EditText np = new EditText(c);
		np.setInputType(InputType.TYPE_CLASS_NUMBER);
		np.setText(String.valueOf(prefs.getInt(setting, def)));
		new AlertDialog.Builder(c).setTitle(title).setView(np).setPositiveButton(android.R.string.ok, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				int number = -1;
				try {
					number = Integer.parseInt(np.getText().toString());
				} catch (Exception e) {
				}
				if (number >= min && number <= max) {
					prefs.edit().putInt(setting, number).commit();
					if (changeTitle)
						p.setTitle(c.getString(summary, number));
					else
						p.setSummary(c.getString(summary, number));
				} else {
					Toast.makeText(c, c.getString(R.string.invalid_input_number_has_to_be_, min, max), Toast.LENGTH_SHORT).show();
				}
			}
		}).create().show();
	}
}