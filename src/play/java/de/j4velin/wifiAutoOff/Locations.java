/*
 * Copyright 2015 Thomas Hoffmann
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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class Locations extends Activity {

    private final static int REQUEST_LOCATION = 1, REQUEST_BUY = 3, REQUEST_PERMISSIONS = 4;

    private IInAppBillingService mService;
    private final ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
            try {
                Bundle ownedItems = mService.getPurchases(3, getPackageName(), "inapp", null);
                if (ownedItems.getInt("RESPONSE_CODE") == 0) {
                    PREMIUM_ENABLED =
                            ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST") != null &&
                                    ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST")
                                            .contains("de.j4velin.wifiautomatic.billing.pro");
                    getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                            .putBoolean("pro", PREMIUM_ENABLED).commit();
                }
            } catch (RemoteException e) {
                Toast.makeText(Locations.this, e.getClass().getName() + ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
    };

    private static boolean PREMIUM_ENABLED = false;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView mRecyclerView;

    private List<Location> locations;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Database db = Database.getInstance(this);
        locations = db.getLocations();
        db.close();

        setContentView(R.layout.locations);

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (PREMIUM_ENABLED || locations.size() < 1) {
                    startActivityForResult(new Intent(Locations.this, Map.class), REQUEST_LOCATION);
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(Locations.this);
                    builder.setMessage(R.string.buy_pro);
                    builder.setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialog, int which) {
                                    try {
                                        Bundle buyIntentBundle =
                                                mService.getBuyIntent(3, getPackageName(),
                                                        "de.j4velin.wifiautomatic.billing.pro",
                                                        "inapp", getPackageName());
                                        if (buyIntentBundle.getInt("RESPONSE_CODE") == 0) {
                                            PendingIntent pendingIntent =
                                                    buyIntentBundle.getParcelable("BUY_INTENT");
                                            startIntentSenderForResult(
                                                    pendingIntent.getIntentSender(), REQUEST_BUY,
                                                    null, 0, 0, 0);
                                        }
                                    } catch (Exception e) {
                                        if (BuildConfig.DEBUG) Logger.log(e);
                                        Toast.makeText(Locations.this,
                                                e.getClass().getName() + ": " + e.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                        e.printStackTrace();
                                    }
                                    dialog.dismiss();
                                }
                            });
                    builder.setNegativeButton(android.R.string.no,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    builder.create().show();
                }
            }
        });

        mRecyclerView = (RecyclerView) findViewById(R.id.locations);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mAdapter = new LocationsAdapter();
        mRecyclerView.setAdapter(mAdapter);

        PREMIUM_ENABLED |=
                getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("pro", false);
        if (!PREMIUM_ENABLED) {
            bindService(new Intent("com.android.vending.billing.InAppBillingService.BIND")
                    .setPackage("com.android.vending"), mServiceConn, Context.BIND_AUTO_CREATE);
        }

        findViewById(R.id.locationsettingswarning).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    Toast.makeText(Locations.this, R.string.settings_not_found_, Toast.LENGTH_SHORT)
                            .show();
                }
            }
        });

        final SharedPreferences prefs = getSharedPreferences("locationPrefs", MODE_PRIVATE);
        CheckBox active = (CheckBox) findViewById(R.id.active);
        final EditText interval = (EditText) findViewById(R.id.scaninterval);
        active.setChecked(prefs.getBoolean("active", false));
        interval.setText(String.valueOf(prefs.getInt("interval", 15)));
        final View intervalLayout = findViewById(R.id.interval);
        intervalLayout.setVisibility(active.isChecked() ? View.VISIBLE : View.GONE);
        active.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, boolean isChecked) {
                intervalLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                prefs.edit().putBoolean("active", isChecked).apply();
            }
        });
        interval.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(final Editable s) {
                try {
                    int minutes = Integer.parseInt(s.toString());
                    prefs.edit().putInt("interval", minutes).apply();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://j4velin.de/faq/index.php?app=wa")));
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean locationAccessEnabled = true;
        try {
            locationAccessEnabled = (Build.VERSION.SDK_INT < 19 && !TextUtils.isEmpty(
                    Settings.Secure.getString(getContentResolver(),
                            Settings.Secure.LOCATION_PROVIDERS_ALLOWED))) ||
                    (Build.VERSION.SDK_INT >= 19 && Settings.Secure
                            .getInt(getContentResolver(), Settings.Secure.LOCATION_MODE) !=
                            Settings.Secure.LOCATION_MODE_OFF);
        } catch (Exception e) {
            e.printStackTrace();
        }
        findViewById(R.id.locationsettingswarning)
                .setVisibility(locationAccessEnabled ? View.GONE : View.VISIBLE);

        if (PermissionChecker
                .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PermissionChecker.PERMISSION_GRANTED || PermissionChecker
                .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PermissionChecker.PERMISSION_GRANTED) {
            findViewById(R.id.permissionswarning).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    ActivityCompat.requestPermissions(Locations.this,
                            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
                }
            });
        } else {
            findViewById(R.id.permissionswarning).setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            unbindService(mServiceConn);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (requestCode == REQUEST_LOCATION) {
            if (resultCode == RESULT_OK) {
                LatLng location = data.getParcelableExtra("location");
                String locationName = "UNKNOWN";
                if (Geocoder.isPresent()) {
                    Geocoder gc = new Geocoder(this);
                    try {
                        List<Address> result =
                                gc.getFromLocation(location.latitude, location.longitude, 1);
                        if (result != null && !result.isEmpty()) {
                            Address address = result.get(0);
                            locationName = address.getAddressLine(0);
                            if (address.getLocality() != null) {
                                locationName += ", " + address.getLocality();
                            }
                        }
                    } catch (IOException e) {
                        if (BuildConfig.DEBUG) Logger.log(e);
                        e.printStackTrace();
                    }
                }
                Database db = Database.getInstance(this);
                db.addLocation(locationName, location);
                db.close();
                locations.add(new Location(locationName, location));
                mAdapter.notifyDataSetChanged();
            }
        } else if (requestCode == REQUEST_BUY) {
            if (resultCode == RESULT_OK) {
                if (data.getIntExtra("RESPONSE_CODE", 0) == 0) {
                    try {
                        JSONObject jo = new JSONObject(data.getStringExtra("INAPP_PURCHASE_DATA"));
                        PREMIUM_ENABLED = jo.getString("productId")
                                .equals("de.j4velin.wifiautomatic.billing.pro") &&
                                jo.getString("developerPayload").equals(getPackageName());
                        getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                                .putBoolean("pro", PREMIUM_ENABLED).commit();
                        if (PREMIUM_ENABLED) {
                            Toast.makeText(this, "Thank you!", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        if (BuildConfig.DEBUG) Logger.log(e);
                        Toast.makeText(this, e.getClass().getName() + ": " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, final String[] permissions,
                                           final int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults[0] == PermissionChecker.PERMISSION_GRANTED &&
                    grantResults[1] == PermissionChecker.PERMISSION_GRANTED) {
                findViewById(R.id.permissionswarning).setVisibility(View.GONE);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private class LocationsAdapter extends
            RecyclerView.Adapter<LocationsAdapter.LocationHolder> implements View.OnClickListener {

        @Override
        public LocationHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
            // create a new view
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.location, parent, false);
            // set the view's size, margins, paddings and layout parameters
            v.setOnClickListener(this);
            LocationHolder h = new LocationHolder(v);
            v.setOnClickListener(this);
            v.findViewById(R.id.delete).setOnClickListener(this);
            return h;
        }

        @Override
        public void onBindViewHolder(final LocationHolder h, int position) {
            if (locations.get(position).name.equals("UNKNOWN")) {
                h.subtext.setText(locations.get(position).coords.latitude + ", " +
                        locations.get(position).coords.longitude);
                h.text.setText("Unknown location");
            } else {
                String[] data = locations.get(position).name.split(",", 2);
                h.text.setText(data[0]);
                h.subtext.setText(data.length > 1 ? data[1] : "");
            }
            h.delete.setTag(position);
        }

        @Override
        public int getItemCount() {
            return locations.size();
        }

        @Override
        public void onClick(final View v) {
            if (v.getId() == R.id.delete) {
                int index = (int) v.getTag();
                if (locations.size() > index) {
                    Database db = Database.getInstance(Locations.this);
                    db.deleteLocation(locations.remove(index).coords);
                    db.close();
                    mAdapter.notifyDataSetChanged();
                } else {
                    v.setVisibility(View.GONE);
                }
            } else {
                startActivity(new Intent(Locations.this, Map.class).putExtra("location",
                        locations.get(mRecyclerView.getChildAdapterPosition(v)).coords));
            }
        }

        public class LocationHolder extends RecyclerView.ViewHolder {
            // each data item is just a string in this case
            final TextView text, subtext;
            final View delete;

            public LocationHolder(final View v) {
                super(v);
                text = (TextView) v.findViewById(R.id.text);
                subtext = (TextView) v.findViewById(R.id.subtext);
                delete = v.findViewById(R.id.delete);
            }
        }
    }
}
