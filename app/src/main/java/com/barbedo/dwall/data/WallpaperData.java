/**
 * Copyright 2016 Ricardo Barbedo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.barbedo.dwall.data;

import android.app.WallpaperManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

import com.barbedo.dwall.R;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Manages the SQLite database and provides wrapper methods to access and modify it.
 *
 * @author Ricardo Barbedo
 */
public class WallpaperData {

    private static final String TAG = WallpaperData.class.getSimpleName();

    static final String DB_NAME = "dwall.db";
    static final int DB_VERSION = 1;
    static final String TABLE = "dwall";
    static final String C_POSITION = "position";
    static final String C_NAME = "name";
    static final String C_MODE = "mode";
    static final String C_INFO = "info";
    static final String C_FILENAME = "filename";

    private static final String GET_ALL_ORDER_BY = C_POSITION + " ASC";

    /**
     * Implementation of the SQLite helper
     */
    class DbHelper extends SQLiteOpenHelper {

        static final String TAG = "DbHelper";
        static final String DB_NAME = "dwall.db";
        static final int DB_VERSION = 1;
        static final String TABLE = "dwall";
        static final String C_POSITION = "position";
        static final String C_NAME = "name";
        static final String C_MODE = "mode";
        static final String C_INFO = "info";
        static final String C_FILENAME = "filename";

        public DbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            String sql = "create table " + TABLE + " (" + C_POSITION + " int primary key, "
                    + C_NAME + " text, " + C_MODE + " text, "
                    + C_INFO + " text, " + C_FILENAME + " text)";

            db.execSQL(sql);

            Log.d(TAG, "onCreated sql: " + sql);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("drop table if exists " + TABLE);
            Log.d(TAG, "onUpgrade");
            onCreate(db);
        }
    }

    // Final assures that there is only one instance of the database helper when the app is running
    private final DbHelper dbHelper;


    /**
     * Constructor.
     *
     * @param context The current context.
     */
    public WallpaperData(Context context) {
        this.dbHelper = new DbHelper(context);
        Log.d(TAG, "Initialized data");
    }


    // TODO: See if its really needed
    public void close() {
        this.dbHelper.close();
    }


    /**
     * Inserts the specified wallpaper object at the end of the database.
     *
     * @param wallpaper The desired wallpaper
     */
    public void insertWallpaper(Wallpaper wallpaper) {
        SQLiteDatabase db = this.dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(DbHelper.C_POSITION, wallpaper.position);
        values.put(DbHelper.C_NAME, wallpaper.name);
        values.put(DbHelper.C_MODE, wallpaper.mode);
        values.put(DbHelper.C_INFO, wallpaper.info);
        values.put(DbHelper.C_FILENAME, wallpaper.filename);

        try {
            db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            Log.d(TAG, "Added wallpaper " + wallpaper.name);
        } catch (SQLException e) {
            Log.d(TAG, "SQLException");
        }

        db.close();
    }


    /**
     * Clears the database and fills it with the specified list
     *
     * @param wallpaperList The desired list of wallpapers
     */
    public void clearAndInsertWallpaperList(List<Wallpaper> wallpaperList) {

        SQLiteDatabase db = this.dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        db.delete(TABLE, null, null);

        for (Wallpaper wallpaper : wallpaperList) {
            values.clear();
            values.put(DbHelper.C_POSITION, wallpaper.position);
            values.put(DbHelper.C_NAME, wallpaper.name);
            values.put(DbHelper.C_MODE, wallpaper.mode);
            values.put(DbHelper.C_INFO, wallpaper.info);
            values.put(DbHelper.C_FILENAME, wallpaper.filename);

            try {
                db.insertOrThrow(DbHelper.TABLE, null, values);
                Log.d(TAG, "Added wallpaper " + wallpaper.name);
            } catch (SQLException e) {
                Log.d(TAG, "SQLException");
            }
        }

        db.close();
    }


    /**
     * @return The list of wallpaper objects represented in the database
     */
    public List<Wallpaper> getWallpaperList() {
        SQLiteDatabase db = this.dbHelper.getReadableDatabase();
        List<Wallpaper> wallpaperList = new ArrayList<Wallpaper>();
        Wallpaper wallpaper;

        Cursor cursor = db.query(TABLE, null, null, null, null, null, GET_ALL_ORDER_BY);

        while (cursor.moveToNext()) {
            wallpaper = new Wallpaper(cursor.getInt(0), cursor.getString(1),
                    cursor.getString(2), cursor.getString(3), cursor.getString(4));
            wallpaperList.add(wallpaper);
        }

        db.close();
        return wallpaperList;
    }


    /**
     * This method returns a list of the wallpapers that meet the criteria to be active.
     * The list is ordered by the priority of the wallpaper, the same order they are displayed
     * on the ListActivity.
     *
     * @param context  The current context, to retrieve the WifiManager.
     * @return         A list of the wallpapers that meet the criteria to be active.
     */
    public List<Wallpaper> getActiveWallpaperList(Context context) {
        List<Wallpaper> wallpaperList = getWallpaperList();
        List<Wallpaper> activeList = new ArrayList<Wallpaper>();

        // Wifi information
        WifiManager wifiManager = (WifiManager)
                context.getSystemService(Context.WIFI_SERVICE);
        String wifiName = wifiManager.getConnectionInfo().getSSID().replace("\"", "");


        for (Wallpaper wallpaper : wallpaperList) {

            // Wifi filter
            if (wallpaper.getMode().equals("Wi-Fi") &&
                    wallpaper.getInfo().equals(wifiName)) {
                activeList.add(wallpaper);
                Log.d(TAG, "active: " + wallpaper.toString());
            }

            // Time filter
            if (wallpaper.getMode().equals("Time")) {
                String[] times = wallpaper.getInfo().split("\\s+");
                SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
                Date now = new Date();
                String currentTime = dateFormat.format(now);
                boolean isInInterval = false;

                try {
                    isInInterval = isTimeInInterval(times[0], times[1], currentTime);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                if (isInInterval) {
                    activeList.add(wallpaper);
                    Log.d(TAG, "active: " + wallpaper.toString());
                }
            }
        }

        return activeList;
    }


    /**
     * This functions determines if the specified time is between a start time (inclusive) and an
     * end time (exclusive).
     * All the times are strings with the format HH:mm.
     *
     * @param argStartTime    Start time string
     * @param argEndTime      End time string
     * @param argCurrentTime  Current time string
     * @return                True if the current time is in the interval, false otherwise
     * @throws ParseException
     */
    private static boolean isTimeInInterval(String argStartTime,
                                               String argEndTime,
                                               String argCurrentTime) throws ParseException {
        boolean valid = false;

        // Start Time
        Date startTime = new SimpleDateFormat("HH:mm")
                .parse(argStartTime);
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTime(startTime);

        // Current Time
        Date currentTime = new SimpleDateFormat("HH:mm")
                .parse(argCurrentTime);
        Calendar currentCalendar = Calendar.getInstance();
        currentCalendar.setTime(currentTime);

        // End Time
        Date endTime = new SimpleDateFormat("HH:mm")
                .parse(argEndTime);
        Calendar endCalendar = Calendar.getInstance();
        endCalendar.setTime(endTime);


        if (currentTime.compareTo(endTime) < 0) {
            currentCalendar.add(Calendar.DATE, 1);
            currentTime = currentCalendar.getTime();
        }

        if (startTime.compareTo(endTime) < 0) {
            startCalendar.add(Calendar.DATE, 1);
            startTime = startCalendar.getTime();
        }

        if (currentTime.before(startTime)) {
            valid = false;

        } else {

            if (currentTime.after(endTime)) {
                endCalendar.add(Calendar.DATE, 1);
                endTime = endCalendar.getTime();
            }

            if (currentTime.before(endTime)) {
                valid = true;
            } else {
                valid = false;
            }

        }

        return valid;
    }
}
