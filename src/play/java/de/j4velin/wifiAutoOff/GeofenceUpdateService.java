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
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.PermissionChecker;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.List;

public class GeofenceUpdateService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private final static int LOCATION_RANGE_METER = 200;
    private GoogleApiClient mLocationClient;

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (mLocationClient == null) {
            mLocationClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API)
                    .addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
            mLocationClient.connect();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onConnected(final Bundle bundle) {
        if (mLocationClient == null) return; // should not happen?
        if (BuildConfig.DEBUG) Logger.log("removing all fences");
        PendingIntent pi = PendingIntent
                .getService(this, 0, new Intent(this, GeoFenceService.class),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        LocationServices.GeofencingApi.removeGeofences(mLocationClient, pi);
        LocationServices.FusedLocationApi.removeLocationUpdates(mLocationClient, pi);
        Database database = Database.getInstance(this);
        List<Location> locations = database.getLocations();
        database.close();

        SharedPreferences prefs = getSharedPreferences("locationPrefs", MODE_PRIVATE);

        if (BuildConfig.DEBUG) Logger.log("re-adding all " + locations.size() + " fences");
        if (!locations.isEmpty()) {
            GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
            builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
            for (Location l : locations) {
                try {
                    builder.addGeofence(new Geofence.Builder()
                            .setCircularRegion(l.coords.latitude, l.coords.longitude,
                                    LOCATION_RANGE_METER)
                            .setRequestId(l.coords.latitude + "@" + l.coords.longitude)
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER).build());
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Logger.log(e);
                }
            }
            try {
                if (PermissionChecker
                        .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PermissionChecker.PERMISSION_GRANTED && PermissionChecker
                        .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PermissionChecker.PERMISSION_GRANTED) {
                    LocationServices.GeofencingApi
                            .addGeofences(mLocationClient, builder.build(), pi);
                    if (prefs.getBoolean("active", false)) {
                        LocationRequest mLocationRequest = new LocationRequest();
                        mLocationRequest.setInterval(prefs.getInt("interval", 15) * 60000);
                        mLocationRequest.setFastestInterval(5000);
                        mLocationRequest.setSmallestDisplacement(50f);
                        mLocationRequest
                                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
                        LocationServices.FusedLocationApi
                                .requestLocationUpdates(mLocationClient, mLocationRequest, pi);
                    }
                } else {
                    if (BuildConfig.DEBUG) Logger.log("no location permission");
                }
            } catch (Exception iae) {
                if (BuildConfig.DEBUG) Logger.log(iae);
            }
        }
        disconnect();
    }

    @Override
    public void onConnectionSuspended(int i) {
        disconnect();
    }

    private void disconnect() {
        if (BuildConfig.DEBUG) Logger.log("GeofenceUpdateService disconnect");
        if (mLocationClient != null) mLocationClient.disconnect();
        mLocationClient = null;
        stopSelf();
    }

    @Override
    public void onConnectionFailed(final ConnectionResult connectionResult) {
        disconnect();
    }
}
