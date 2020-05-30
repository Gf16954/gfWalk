/**
 * Created by gf on 03.05.2015.
 */
package com.gf169.gfwalk;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DB {
    public static final String TABLE_WALKS = "walks";
    public static final String KEY_ID = "_id";
    public static final String KEY_STARTTIME = "startTime";  // milliseconds
    public static final String KEY_TIMEZONE = "timeZone";
    public static final String KEY_COMMENT= "description";
    public static final String KEY_STARTPLACE = "startPlace";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_LENGTH = "length";
    public static final String KEY_DURATIONNETTO = "durationNetto";
    public static final String KEY_LENGTHNETTO = "lengthNetto";
    public static final String KEY_ICONAFID = "iconAFId";
    public static final String KEY_DELETED = "deleted";
    public static final String KEY_ICON = "icon";
    public static final String KEY_SETTINGS = "settings";

    public static final String TABLE_POINTS = "points";
    public static final String KEY_POINTID = "_id";
    public static final String KEY_POINTWALKID = "walkId";
    public static final String KEY_POINTFLAGRESUME = "flagResume";
    public static final String KEY_POINTTIME = "time";
    public static final String KEY_POINTTIMEEND = "timeEnd";
    public static final String KEY_POINTLOCATION = "location";
    public static final String KEY_POINTADDRESS = "address";
    public static final String KEY_POINTDEBUGINFO = "origin";
    public static final String KEY_POINTCOMMENT= "comment";
    public static final String KEY_POINTBATTERYCHARGE= "reserve";
    public static final String KEY_POINTACTIVITYTYPE= "reserve2";

    public static final String TABLE_AFS = "AFs";
    public static final String KEY_AFID = "_id";
    public static final String KEY_AFWALKID = "walkId";
    public static final String KEY_AFPOINTID = "pointId";
    public static final String KEY_AFPOINTNUMBER = "pointNumber";
    public static final String KEY_AFTIME = "time";
    public static final String KEY_AFKIND = "kind";
    public static final String KEY_AFURI = "uri";
    public static final String KEY_AFFILEPATH = "filePath";
    public static final String KEY_AFCOMMENT= "comment";
    public static final String KEY_AFDELETED= "deleted";

    public static  class DatabaseHelper extends SQLiteOpenHelper {
        private static final String TAG = "DatabaseHelper";

        private static final int DATABASE_VERSION = 8;
        private static final String DATABASE_NAME = "db";

        private static final String CREATE_TABLE_WALKS =
                "CREATE TABLE if not exists " + TABLE_WALKS
                        + " (" + KEY_ID + " integer PRIMARY KEY autoincrement"
                        + " ," + KEY_STARTTIME + " integer"
                        + " ," + KEY_TIMEZONE
                        + " ," + KEY_COMMENT
                        + " ," + KEY_STARTPLACE
                        + " ," + KEY_DURATION + " integer"
                        + " ," + KEY_LENGTH + " integer"
                        + " ," + KEY_DURATIONNETTO + " integer"
                        + " ," + KEY_LENGTHNETTO + " integer"
                        + " ," + KEY_ICONAFID + " integer"
                        + " ," + KEY_DELETED + " boolean"
                        + " ," + KEY_ICON + " blob"
                        + " ," + KEY_SETTINGS
                        + " ," + "reserve"
                        + " ," + "reserve2" + " integer"
                        + " ," + "reserve3" + " blob"
                        + " )";

        private static final String CREATE_TABLE_POINTS =
                "CREATE TABLE if not exists " + TABLE_POINTS
                        + " (" + KEY_POINTID + " integer PRIMARY KEY autoincrement"
                        + " ," + KEY_POINTWALKID + " integer"
                        + " ," + KEY_POINTFLAGRESUME + " boolean"
                        + " ," + KEY_POINTTIME + " integer"
                        + " ," + KEY_POINTTIMEEND + " integer"
                        + " ," + KEY_POINTLOCATION
                        + " ," + KEY_POINTADDRESS
                        + " ," + KEY_POINTDEBUGINFO
                        + " ," + KEY_POINTCOMMENT
                        + " ," + "reserve"  + " float"  // Battery charge, 0-1
                        + " ," + "reserve2" + " integer" // Activity type
                        + " ," + "reserve3" + " blob"
                        + " )";

        private static final String CREATE_TABLE_AFS =
                "CREATE TABLE if not exists " + TABLE_AFS
                        + " (" + KEY_AFID + " integer PRIMARY KEY autoincrement"
                        + " ," + KEY_AFWALKID + " integer"
                        + " ," + KEY_AFPOINTID + " integer" //TODO: foreign index POINTID + TIME ?
                        + " ," + KEY_AFPOINTNUMBER + " integer"
                        + " ," + KEY_AFTIME + " integer"
                        + " ," + KEY_AFKIND + " integer"
                        + " ," + KEY_AFURI
                        + " ," + KEY_AFFILEPATH
                        + " ," + KEY_AFCOMMENT
                        + " ," + KEY_AFDELETED + " boolean"
                        + " ," + "reserve"
                        + " ," + "reserve2" + " integer"
                        + " ," + "reserve3" + " blob"
                        + " )";

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE_WALKS);
            db.execSQL(CREATE_TABLE_POINTS);
            db.execSQL(CREATE_TABLE_AFS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_WALKS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_POINTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_AFS);

            onCreate(db);
        }
    }

    public static DatabaseHelper helper;
    public static SQLiteDatabase db=null;

    public static void dbInit(Context context) {
        if (db==null) {
            helper=new DatabaseHelper(context);
            db=helper.getWritableDatabase();
        }
    }
}
