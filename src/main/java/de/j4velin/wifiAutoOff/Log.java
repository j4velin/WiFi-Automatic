/*
 * Copyright 2016 Thomas Hoffmann
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
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class Log {

    /**
     * Time to keep the logs in ms
     */
    final static long KEEP_DURATION = 3 * 24 * 60 * 60 * 1000; // 3 days

    public enum Type {
        WIFI_ON(R.drawable.event_wifi_on), WIFI_CONNECTED(R.drawable.event_wifi_on),
        WIFI_OFF(R.drawable.event_wifi_off), WIFI_DISCONNECTED(R.drawable.event_wifi_disconnected),
        LOCATION_ENTERED(R.drawable.event_location_entered), TIMER(R.drawable.event_timer),
        AIRPLANE_MODE(R.drawable.event_airplane_mode), SCREEN_OFF(R.drawable.event_display_off),
        SCREEN_ON(R.drawable.event_display_on), UNLOCKED(R.drawable.event_unlock),
        AC_CONNECTED(R.drawable.event_ac_connected),
        AC_DISCONNECTED(R.drawable.event_ac_disconnected), HOTSPOT(R.drawable.event_hotspot),
        ERROR(R.drawable.event_error), APP_DISABLED(R.drawable.event_app_disabled),
        APP_ENABLED(R.drawable.event_app_enabled);

        public final int drawable;

        Type(int drawable) {
            this.drawable = drawable;
        }
    }

    private Log() {
    }

    /**
     * Inserts an event in the persistent log
     *
     * @param context the context
     * @param text    the text describing the event
     * @param type    the type of the event
     */
    public static void insert(final Context context, final String text, final Type type) {
        ContentValues values = new ContentValues();
        values.put("date", System.currentTimeMillis());
        values.put("info", text);
        values.put("type", type.name());
        Database db = new Database(context);
        try {
            db.getWritableDatabase().insert(Database.DB_NAME, null, values);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
        } finally {
            db.close();
        }
        if (BuildConfig.DEBUG) Logger.log(text);
    }

    /**
     * Inserts an event in the persistent log
     *
     * @param context the context
     * @param text    a string resource id which describes this event
     * @param type    the type of the event
     */
    public static void insert(final Context context, int text, final Type type) {
        insert(context, context.getString(text), type);
    }

    /**
     * Gets the log
     *
     * @param context the context
     * @param num     the maximum number of log entries to load or a number <= 0 to load all entries
     * @return a list containing the log entries in descending date order (newest first)
     */
    public static List<Item> getLog(final Context context, int num) {
        List<Item> result;
        Database db = new Database(context);
        Cursor c = db.getReadableDatabase()
                .query(Database.DB_NAME, new String[]{"date", "info", "type"}, null, null, null,
                        null, "date DESC", num > 0 ? String.valueOf(num) : null);
        if (c != null) {
            result = new ArrayList<>(c.getCount());
            if (c.moveToFirst()) {
                do {
                    result.add(new Item(c.getLong(0), c.getString(1), c.getString(2)));
                } while (c.moveToNext());
            }
            c.close();
        } else {
            result = new ArrayList<>(0);
        }
        db.close();
        return result;
    }

    /**
     * Deletes all log entries which are older then the given duration
     *
     * @param context  the context
     * @param duration the duration in ms
     */
    public static void deleteOldLogs(final Context context, long duration) {
        Database db = new Database(context);
        try {
            int deleted = db.getWritableDatabase().delete(Database.DB_NAME, "date < ?",
                    new String[]{String.valueOf(System.currentTimeMillis() - duration)});
            if (BuildConfig.DEBUG) Logger.log("deleted " + deleted + " old log entries");
        } catch (SQLiteDatabaseLockedException e) {
            if (BuildConfig.DEBUG) Logger.log("cant delete log: database locked");
        } catch (Throwable t) {
            if (BuildConfig.DEBUG) Logger.log("cant delete log: " + t.getMessage());
        } finally {
            db.close();
        }
    }

    /**
     * Container class describing a log item
     */
    public static class Item {

        final long date;
        final String text;
        final Type type;

        public Item(long date, final String text, final String type) {
            this.date = date;
            this.text = text;
            this.type = Type.valueOf(type);
        }
    }

    private static class Database extends SQLiteOpenHelper {

        private final static String DB_NAME = "log";
        private final static int DB_VERSION = 1;

        private Database(final Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + DB_NAME + " (date INTEGER, info TEXT, type TEXT)");
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {

        }
    }

}
