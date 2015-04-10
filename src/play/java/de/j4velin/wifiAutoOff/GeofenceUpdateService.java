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

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.util.List;

public class GeofenceUpdateService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

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
        if (BuildConfig.DEBUG) Logger.log("removing all fences");
        PendingIntent pi = PendingIntent
                .getService(this, 0, new Intent(this, GeoFenceService.class),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        LocationServices.GeofencingApi.removeGeofences(mLocationClient, pi);
        Database database = Database.getInstance(this);
        List<Location> locations = database.getLocations();
        database.close();

        if (BuildConfig.DEBUG) Logger.log("re-adding all " + locations.size() + " fences");
        if (!locations.isEmpty()) {
            GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
            for (Location l : locations) {
                try {
                    builder.addGeofence(new Geofence.Builder()
                            .setCircularRegion(l.coords.latitude, l.coords.longitude, 100)
                            .setRequestId(l.coords.latitude + "@" + l.coords.longitude)
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER).build());
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Logger.log(e);
                }
            }
            try {
                LocationServices.GeofencingApi.addGeofences(mLocationClient, builder.build(), pi);
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
        mLocationClient.disconnect();
        mLocationClient = null;
        stopSelf();
    }

    @Override
    public void onConnectionFailed(final ConnectionResult connectionResult) {
        disconnect();
    }
}
