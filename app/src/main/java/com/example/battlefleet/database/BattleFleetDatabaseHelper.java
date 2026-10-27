package com.example.battlefleet.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * SQLiteOpenHelper implementation managing the BattleFleet local match history database.
 * Demonstrates Unit 4 Syllabus: Database creation, table schemas, version upgrades, and direct SQL execution.
 */
public class BattleFleetDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "BattleFleetDB";
    public static final String DATABASE_NAME = "battlefleet.db";
    public static final int DATABASE_VERSION = 1;

    // Table Name
    public static final String TABLE_MATCH_HISTORY = "match_history";

    // Column Names
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_ROOM_CODE = "room_code";
    public static final String COLUMN_PLAYER_NAME = "player_name";
    public static final String COLUMN_OPPONENT_NAME = "opponent_name";
    public static final String COLUMN_RESULT = "result"; // "WIN" or "LOSS"
    public static final String COLUMN_STARTED_AT = "started_at";
    public static final String COLUMN_COMPLETED_AT = "completed_at";
    public static final String COLUMN_DURATION_SECONDS = "duration_seconds";
    public static final String COLUMN_SHOTS_FIRED = "shots_fired";
    public static final String COLUMN_HITS = "hits";
    public static final String COLUMN_ACCURACY = "accuracy";

    // SQL Create Table Statement
    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_MATCH_HISTORY + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_ROOM_CODE + " TEXT, " +
                    COLUMN_PLAYER_NAME + " TEXT NOT NULL, " +
                    COLUMN_OPPONENT_NAME + " TEXT NOT NULL, " +
                    COLUMN_RESULT + " TEXT NOT NULL, " +
                    COLUMN_STARTED_AT + " TEXT, " +
                    COLUMN_COMPLETED_AT + " TEXT NOT NULL, " +
                    COLUMN_DURATION_SECONDS + " INTEGER DEFAULT 0, " +
                    COLUMN_SHOTS_FIRED + " INTEGER DEFAULT 0, " +
                    COLUMN_HITS + " INTEGER DEFAULT 0, " +
                    COLUMN_ACCURACY + " REAL DEFAULT 0.0" +
                    ");";

    private static final String SQL_DROP_TABLE =
            "DROP TABLE IF EXISTS " + TABLE_MATCH_HISTORY;

    public BattleFleetDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating SQLite table: " + TABLE_MATCH_HISTORY);
        db.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        db.execSQL(SQL_DROP_TABLE);
        onCreate(db);
    }
}
