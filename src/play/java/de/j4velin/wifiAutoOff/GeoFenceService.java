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

import android.app.IntentService;
import android.content.Intent;

import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.maps.model.LatLng;

public class GeoFenceService extends IntentService {

    public GeoFenceService() {
        super("WiFiAutomaticGeoFenceService");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (intent == null) return;
        if (intent.hasExtra(FusedLocationProviderApi.KEY_LOCATION_CHANGED)) {
            android.location.Location loc = (android.location.Location) intent.getExtras()
                    .get(FusedLocationProviderApi.KEY_LOCATION_CHANGED);
            if (BuildConfig.DEBUG) Logger.log("Location update received " + loc);
            Database db = Database.getInstance(this);
            if (db.inRangeOfLocation(loc)) {
                sendBroadcast(new Intent(this, Receiver.class)
                        .setAction(Receiver.LOCATION_ENTERED_ACTION));
            }
            db.close();
        } else {
            GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
            // First check for errors
            if (geofencingEvent.hasError()) {
                // Get the error code with a static method
                // Log the error
                if (BuildConfig.DEBUG) Logger.log("Location Services error: " +
                        Integer.toString(geofencingEvent.getErrorCode()));
            } else {
                // Test that a valid transition was reported
                if (geofencingEvent.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER) {
                    Database db = Database.getInstance(this);
                    for (Geofence gf : geofencingEvent.getTriggeringGeofences()) {
                        if (BuildConfig.DEBUG) Logger.log("geofence entered: " + gf.getRequestId());
                        String[] data = gf.getRequestId().split("@");
                        LatLng ll = new LatLng(Double.parseDouble(data[0]),
                                Double.parseDouble(data[1]));
                        String name = db.getNameForLocation(ll);
                        if (name != null) {
                            sendBroadcast(new Intent(this, Receiver.class)
                                    .setAction(Receiver.LOCATION_ENTERED_ACTION)
                                    .putExtra(Receiver.EXTRA_LOCATION_NAME, name));
                            break;
                        }
                    }
                    db.close();
                }
            }
        }
    }
}
