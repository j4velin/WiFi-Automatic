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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class Locations extends Activity {

    private final static int REQUEST_LOCATION = 1, REQUEST_DELETE = 2, REQUEST_BUY = 3;

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
                        PREMIUM_ENABLED =
                                jo.getString("productId").equals("de.j4velin.wifiautomatic.billing.pro") &&
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
                Database db = Database.getInstance(Locations.this);
                db.deleteLocation(locations.remove((int) v.getTag()).coords);
                db.close();
                mAdapter.notifyDataSetChanged();
            } else {
                startActivityForResult(new Intent(Locations.this, Map.class).putExtra("location",
                                locations.get(mRecyclerView.getChildAdapterPosition(v)).coords),
                        REQUEST_DELETE);
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
