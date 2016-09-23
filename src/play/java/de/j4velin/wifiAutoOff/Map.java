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
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;

public class Map extends AppCompatActivity implements OnMapReadyCallback {

    private final static int REQUEST_PERMISSIONS = 1;

    private GoogleApiClient mGoogleApiClient;

    private void showMap() {
        setContentView(R.layout.map);
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                .getMapAsync(this);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (PermissionChecker
                .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PermissionChecker.PERMISSION_GRANTED || PermissionChecker
                .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PermissionChecker.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(Map.this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
        } else {
            showMap();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, final String[] permissions,
                                           final int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults[0] == PermissionChecker.PERMISSION_GRANTED &&
                    grantResults[1] == PermissionChecker.PERMISSION_GRANTED) {
                showMap();
            } else {
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @SuppressWarnings("MissingPermission")
    @Override
    public void onMapReady(final GoogleMap mMap) {
        LatLng location = getIntent().getParcelableExtra("location");
        if (location == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(final Bundle bundle) {
                            android.location.Location l = LocationServices.FusedLocationApi
                                    .getLastLocation(mGoogleApiClient);
                            if (l != null && mMap.getCameraPosition().zoom <= 2) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(l.getLatitude(), l.getLongitude()), 16));
                            }
                            mGoogleApiClient.disconnect();
                        }

                        @Override
                        public void onConnectionSuspended(int cause) {
                            if (BuildConfig.DEBUG) Logger.log("connection suspended: " + cause);
                        }
                    }).build();

            mGoogleApiClient.connect();

            mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                @Override
                public void onMapClick(final LatLng center) {
                    getIntent().putExtra("location", center);
                    setResult(RESULT_OK, getIntent());
                    finish();
                }
            });
        } else {
            mMap.addCircle(new CircleOptions().center(location).radius(100).strokeColor(Color.RED)
                    .fillColor(Color.argb(64, 255, 0, 0)));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 16));
        }

        mMap.setMyLocationEnabled(true);
    }
}
