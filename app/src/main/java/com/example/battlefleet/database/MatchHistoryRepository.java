package com.example.battlefleet.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.battlefleet.models.MatchRecord;
import com.example.battlefleet.models.PlayerStats;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository providing asynchronous, thread-safe access to SQLite database operations.
 * Demonstrates Unit 4 Syllabus: Cursor traversal, ContentValues, CRUD, filtering, and aggregation queries.
 */
public class MatchHistoryRepository {
    private static final String TAG = "MatchHistoryRepo";
    private static volatile MatchHistoryRepository instance;

    private final BattleFleetDatabaseHelper dbHelper;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface Callback<T> {
        void onComplete(T result);
        void onError(Exception e);
    }

    private MatchHistoryRepository(Context context) {
        this.dbHelper = new BattleFleetDatabaseHelper(context.getApplicationContext());
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized MatchHistoryRepository getInstance(Context context) {
        if (instance == null) {
            instance = new MatchHistoryRepository(context);
        }
        return instance;
    }

    /**
     * Inserts a completed battle record into SQLite.
     */
    public void insertMatch(MatchRecord record, Callback<Long> callback) {
        executor.execute(() -> {
            SQLiteDatabase db = null;
            try {
                db = dbHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(BattleFleetDatabaseHelper.COLUMN_ROOM_CODE, record.getRoomCode());
                values.put(BattleFleetDatabaseHelper.COLUMN_PLAYER_NAME, record.getPlayerName());
                values.put(BattleFleetDatabaseHelper.COLUMN_OPPONENT_NAME, record.getOpponentName());
                values.put(BattleFleetDatabaseHelper.COLUMN_RESULT, record.getResult());
                values.put(BattleFleetDatabaseHelper.COLUMN_STARTED_AT, record.getStartedAt());
                values.put(BattleFleetDatabaseHelper.COLUMN_COMPLETED_AT, record.getCompletedAt());
                values.put(BattleFleetDatabaseHelper.COLUMN_DURATION_SECONDS, record.getDurationSeconds());
                values.put(BattleFleetDatabaseHelper.COLUMN_SHOTS_FIRED, record.getShotsFired());
                values.put(BattleFleetDatabaseHelper.COLUMN_HITS, record.getHits());
                values.put(BattleFleetDatabaseHelper.COLUMN_ACCURACY, record.getAccuracy());

                long rowId = db.insert(BattleFleetDatabaseHelper.TABLE_MATCH_HISTORY, null, values);
                record.setId(rowId);
                Log.i(TAG, "Inserted match row: " + rowId + " (" + record.getResult() + ")");

                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(rowId));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error inserting match", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e));
                }
            }
        });
    }

    /**
     * Retrieves all recorded matches sorted by completion date descending using an Android Cursor.
     */
    public void getAllMatches(Callback<List<MatchRecord>> callback) {
        getMatchesFiltered(null, null, callback);
    }

    /**
     * Retrieves matches filtered by result ("WIN", "LOSS") and optional date prefix ("YYYY-MM-DD").
     */
    public void getMatchesFiltered(String resultFilter, String dateFilter, Callback<List<MatchRecord>> callback) {
        executor.execute(() -> {
            List<MatchRecord> list = new ArrayList<>();
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = dbHelper.getReadableDatabase();

                StringBuilder selection = new StringBuilder();
                List<String> selectionArgs = new ArrayList<>();

                if (resultFilter != null && !resultFilter.equalsIgnoreCase("ALL")) {
                    selection.append(BattleFleetDatabaseHelper.COLUMN_RESULT).append(" = ?");
                    selectionArgs.add(resultFilter.toUpperCase());
                }

                if (dateFilter != null && !dateFilter.isEmpty()) {
                    if (selection.length() > 0) selection.append(" AND ");
                    selection.append(BattleFleetDatabaseHelper.COLUMN_COMPLETED_AT).append(" LIKE ?");
                    selectionArgs.add(dateFilter + "%");
                }

                String[] args = selectionArgs.isEmpty() ? null : selectionArgs.toArray(new String[0]);
                String whereClause = selection.length() == 0 ? null : selection.toString();

                cursor = db.query(
                        BattleFleetDatabaseHelper.TABLE_MATCH_HISTORY,
                        null,
                        whereClause,
                        args,
                        null,
                        null,
                        BattleFleetDatabaseHelper.COLUMN_ID + " DESC"
                );

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        MatchRecord record = parseRecordFromCursor(cursor);
                        list.add(record);
                    } while (cursor.moveToNext());
                }

                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(list));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading matches", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e));
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        });
    }

    /**
     * Computes player career statistics dynamically using Cursor aggregations.
     */
    public void getStatistics(Callback<PlayerStats> callback) {
        executor.execute(() -> {
            PlayerStats stats = new PlayerStats();
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = dbHelper.getReadableDatabase();

                // Total, wins, losses, shots, hits query
                String sql = "SELECT COUNT(*) as total, " +
                        "SUM(CASE WHEN " + BattleFleetDatabaseHelper.COLUMN_RESULT + " = 'WIN' THEN 1 ELSE 0 END) as wins, " +
                        "SUM(CASE WHEN " + BattleFleetDatabaseHelper.COLUMN_RESULT + " = 'LOSS' THEN 1 ELSE 0 END) as losses, " +
                        "SUM(" + BattleFleetDatabaseHelper.COLUMN_SHOTS_FIRED + ") as total_shots, " +
                        "SUM(" + BattleFleetDatabaseHelper.COLUMN_HITS + ") as total_hits " +
                        "FROM " + BattleFleetDatabaseHelper.TABLE_MATCH_HISTORY;

                cursor = db.rawQuery(sql, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int total = cursor.getInt(cursor.getColumnIndexOrThrow("total"));
                    int wins = cursor.getInt(cursor.getColumnIndexOrThrow("wins"));
                    int losses = cursor.getInt(cursor.getColumnIndexOrThrow("losses"));
                    int shots = cursor.getInt(cursor.getColumnIndexOrThrow("total_shots"));
                    int hits = cursor.getInt(cursor.getColumnIndexOrThrow("total_hits"));

                    stats.setTotalMatches(total);
                    stats.setWins(wins);
                    stats.setLosses(losses);
                    stats.setTotalShots(shots);
                    stats.setTotalHits(hits);

                    if (total > 0) {
                        stats.setWinRate(Math.round(((double) wins / total) * 1000.0) / 10.0);
                    }
                    if (shots > 0) {
                        stats.setOverallAccuracy(Math.round(((double) hits / shots) * 1000.0) / 10.0);
                    }
                }
                if (cursor != null) cursor.close();

                // Recent 5 match form query
                String recentSql = "SELECT " + BattleFleetDatabaseHelper.COLUMN_RESULT +
                        " FROM " + BattleFleetDatabaseHelper.TABLE_MATCH_HISTORY +
                        " ORDER BY " + BattleFleetDatabaseHelper.COLUMN_ID + " DESC LIMIT 5";
                cursor = db.rawQuery(recentSql, null);
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        stats.addRecentResult(cursor.getString(0));
                    } while (cursor.moveToNext());
                }

                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(stats));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calculating statistics", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e));
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        });
    }

    /**
     * Deletes a single match by its primary key ID.
     */
    public void deleteMatch(long id, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                int rows = db.delete(BattleFleetDatabaseHelper.TABLE_MATCH_HISTORY,
                        BattleFleetDatabaseHelper.COLUMN_ID + " = ?",
                        new String[]{String.valueOf(id)});
                boolean success = (rows > 0);
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(success));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting match " + id, e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e));
                }
            }
        });
    }

    /**
     * Clears all match history records.
     */
    public void clearAll(Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.delete(BattleFleetDatabaseHelper.TABLE_MATCH_HISTORY, null, null);
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(true));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error clearing match history", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e));
                }
            }
        });
    }

    private MatchRecord parseRecordFromCursor(Cursor cursor) {
        MatchRecord record = new MatchRecord();
        record.setId(cursor.getLong(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_ID)));
        record.setRoomCode(cursor.getString(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_ROOM_CODE)));
        record.setPlayerName(cursor.getString(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_PLAYER_NAME)));
        record.setOpponentName(cursor.getString(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_OPPONENT_NAME)));
        record.setResult(cursor.getString(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_RESULT)));
        record.setStartedAt(cursor.getString(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_STARTED_AT)));
        record.setCompletedAt(cursor.getString(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_COMPLETED_AT)));
        record.setDurationSeconds(cursor.getInt(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_DURATION_SECONDS)));
        record.setShotsFired(cursor.getInt(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_SHOTS_FIRED)));
        record.setHits(cursor.getInt(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_HITS)));
        record.setAccuracy(cursor.getDouble(cursor.getColumnIndexOrThrow(BattleFleetDatabaseHelper.COLUMN_ACCURACY)));
        return record;
    }
}
