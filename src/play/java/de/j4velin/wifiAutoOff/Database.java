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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Database extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static Database instance;

    private static final AtomicInteger openCounter = new AtomicInteger();

    private Database(final Context context) {
        super(context, "wifiautomatic", null, DATABASE_VERSION);
    }

    public static synchronized Database getInstance(final Context c) {
        if (instance == null) {
            instance = new Database(c.getApplicationContext());
        }
        openCounter.incrementAndGet();
        return instance;
    }

    @Override
    public void close() {
        if (openCounter.decrementAndGet() == 0) {
            super.close();
        }
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS locations (name TEXT, lat DOUBLE, lon DOUBLE);");
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    /**
     * Gets a list of saved locations
     *
     * @return the list of locations, might be empty
     */
    public List<Location> getLocations() {
        Cursor c = getReadableDatabase()
                .query("locations", new String[]{"name", "lat", "lon"}, null, null, null, null,
                        null);
        c.moveToFirst();
        List<Location> re = new ArrayList<>(c.getCount());
        while (!c.isAfterLast()) {
            if (BuildConfig.DEBUG)
                Logger.log("added location: " + c.getString(0) + " " + c.getDouble(1) + " " +
                        c.getDouble(2));
            re.add(new Location(c.getString(0), new LatLng(c.getDouble(1), c.getDouble(2))));
            c.moveToNext();
        }
        c.close();
        return re;
    }

    /**
     * Saves a location
     *
     * @param name   the name (e.g. address or thelike)
     * @param coords the latitude, longitude coordinate
     */
    public void addLocation(final String name, final LatLng coords) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("lat", coords.latitude);
        values.put("lon", coords.longitude);
        getWritableDatabase().insert("locations", null, values);
    }

    /**
     * Deletes a location
     *
     * @param coords the coordinates of the location to delete
     */
    public void deleteLocation(final LatLng coords) {
        if (BuildConfig.DEBUG)
            Logger.log("deleting " + coords.toString() + " contained? " + containsLocation(coords));
        getWritableDatabase().delete("locations", "lat LIKE ? AND lon LIKE ?",
                new String[]{String.valueOf(coords.latitude).substring(0, 8) + "%",
                        String.valueOf(coords.longitude).substring(0, 8) + "%"});
    }

    /**
     * Checks if the given location is added to the app
     *
     * @param coords the location to check
     * @return true, if WiFi should be turned on when entering this location
     */
    public boolean containsLocation(final LatLng coords) {
        String lat = String.valueOf(coords.latitude);
        String lon = String.valueOf(coords.longitude);
        if (lat.length() > 9) lat = lat.substring(0, 8);
        if (lon.length() > 9) lon = lon.substring(0, 8);
        Cursor c = getReadableDatabase()
                .query("locations", new String[]{"name"}, "lat LIKE ? AND lon LIKE ?",
                        new String[]{lat + "%", lon + "%"}, null, null, null);
        boolean re = c.getCount() > 0;
        c.close();
        if (BuildConfig.DEBUG && !re)
            Logger.log("location not in database: " + coords.latitude + "," + coords.longitude);
        return re;
    }

    /**
     * Checks if the given location is within the given range of any saved location
     *
     * @param current the location to test
     * @param range   the range in m
     * @return true, if 'current' is within 'range' meter of any saved location
     */
    public boolean inRangeOfLocation(final android.location.Location current, final int range) {
        // for a coarse filtering, use the first 2 decimals of the coords -> precision ~ 1km
        String lat = String.valueOf(current.getLatitude());
        String lon = String.valueOf(current.getLongitude());
        lat = lat.substring(0, Math.min(lat.indexOf(".") + 3, lat.length()));
        lon = lon.substring(0, Math.min(lon.indexOf(".") + 3, lon.length()));

        Cursor c = getReadableDatabase()
                .query("locations", new String[]{"lat", "lon"}, "lat LIKE ? AND lon LIKE ?",
                        new String[]{lat + "%", lon + "%"}, null, null, null);
        if (BuildConfig.DEBUG) Logger.log(
                c.getCount() + " locations found which might be in range (coarse filter: " + lat + ", " +
                        lon + ")");
        boolean inRange = false;
        if (c.moveToFirst()) {
            android.location.Location location = new android.location.Location("tester");
            while (!inRange && !c.isAfterLast()) {
                location.setLatitude(c.getDouble(0));
                location.setLongitude(c.getDouble(1));
                inRange = location.distanceTo(current) < range;
                if (BuildConfig.DEBUG && inRange) Logger.log(
                        current + " is within range to " + location + ", distance: " +
                                location.distanceTo(current));
                c.moveToNext();
            }
        }
        c.close();
        return inRange;
    }
}
