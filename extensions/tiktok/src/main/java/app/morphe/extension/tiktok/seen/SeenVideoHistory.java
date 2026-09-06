/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 * Follows BlueDragon4251/tiktok-patches-for-morphe.
 */
package app.morphe.extension.tiktok.seen;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * A local watch history, used to keep videos you have already seen out of the feed. It
 * never leaves the device and holds nothing but a video id and when it was watched.
 *
 * <p>The feed path never touches SQLite: the stored ids load once on a background thread
 * into an in-memory map, and a newly watched id goes into memory first and is written
 * behind it.</p>
 */
public final class SeenVideoHistory {
    private static final String DATABASE_NAME = "seen_videos.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE = "seen_videos";
    private static final String COLUMN_AID = "aid";
    private static final String COLUMN_LAST_SEEN = "last_seen_ms";

    private static final long UNKNOWN_DURATION_MARK_MS = 2_000L;
    private static final long MIN_MARK_MS = 1_000L;
    private static final long MAX_MARK_MS = 5_000L;
    private static final int MARK_PERCENT = 10;
    private static final int MAX_RECORDS = 10_000;
    private static final Object HISTORY_LOCK = new Object();
    private static int generation;

    private static final ConcurrentHashMap<String, Long> SEEN = new ConcurrentHashMap<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Morphe-SeenVideoHistory");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean LOAD_STARTED = new AtomicBoolean();

    private static volatile Database database;
    /**
     * The history as it was before the last clear. Clearing is one tap with no dialog, so
     * the way back has to be kept until it is plainly no longer wanted: the next clear, or
     * the end of the process.
     */
    private static volatile Map<String, Long> undo;
    private static volatile String callbackAid;
    private static volatile boolean callbackAidMarked;

    private SeenVideoHistory() {
    }

    public static void onPlayProgressChange(String aid, long positionMs, long durationMs) {
        String normalizedAid = normalizeAid(aid);
        if (normalizedAid == null) {
            return;
        }

        if (!normalizedAid.equals(callbackAid)) {
            callbackAid = normalizedAid;
            callbackAidMarked = false;
        }

        if (!Settings.HIDE_SEEN_VIDEOS.get() || callbackAidMarked) {
            return;
        }
        if (!hasReachedSeenThreshold(positionMs, durationMs)) {
            return;
        }

        callbackAidMarked = true;
        markSeen(normalizedAid, System.currentTimeMillis());
    }

    public static boolean shouldHide(String aid) {
        if (!Settings.HIDE_SEEN_VIDEOS.get()) {
            return false;
        }

        String normalizedAid = normalizeAid(aid);
        if (normalizedAid == null) {
            return false;
        }
        ensureLoaded();
        Long lastSeen = SEEN.get(normalizedAid);
        if (lastSeen == null) {
            return false;
        }

        long cutoff = retentionCutoff(System.currentTimeMillis());
        if (cutoff == Long.MIN_VALUE || lastSeen >= cutoff) {
            return true;
        }

        if (SEEN.remove(normalizedAid, lastSeen)) {
            deleteAsync(normalizedAid, lastSeen);
        }
        return false;
    }

    public static void clear() {
        synchronized (HISTORY_LOCK) {
            ensureLoaded();
            undo = new HashMap<>(SEEN);
            generation++;
            SEEN.clear();
            callbackAid = null;
            callbackAidMarked = false;
            IO.execute(() -> {
                try {
                    getDatabase().getWritableDatabase().delete(TABLE, null, null);
                } catch (Throwable throwable) {
                    Logger.printException(() -> "Seen video history clear failed", throwable);
                }
            });
        }
    }

    /** How many videos the last clear removed, or zero when there is nothing to put back. */
    public static int undoSize() {
        Map<String, Long> copy = undo;
        return copy == null ? 0 : copy.size();
    }

    /**
     * Puts the history back as it was before the last clear. True when something was
     * restored. The rows are written again rather than the delete being deferred: a clear
     * that a crash could undo on its own would be worse than no undo at all.
     */
    public static boolean undoClear() {
        synchronized (HISTORY_LOCK) {
            Map<String, Long> copy = undo;
            if (copy == null || copy.isEmpty()) {
                return false;
            }
            undo = null;
            generation++;
            SEEN.putAll(copy);
            trimMemory();

            Map<String, Long> rows = new HashMap<>(copy);
            IO.execute(() -> {
                try {
                    SQLiteDatabase writable = getDatabase().getWritableDatabase();
                    writable.beginTransaction();
                    try {
                        for (Map.Entry<String, Long> row : rows.entrySet()) {
                            ContentValues values = new ContentValues();
                            values.put(COLUMN_AID, row.getKey());
                            values.put(COLUMN_LAST_SEEN, row.getValue());
                            writable.insertWithOnConflict(
                                    TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        writable.setTransactionSuccessful();
                    } finally {
                        writable.endTransaction();
                    }
                } catch (Throwable throwable) {
                    Logger.printException(() -> "Seen video history undo failed", throwable);
                }
            });
            return true;
        }
    }

    public static int size() {
        ensureLoaded();
        return SEEN.size();
    }

    private static void markSeen(String aid, long nowMs) {
        synchronized (HISTORY_LOCK) {
            ensureLoaded();
            SEEN.put(aid, nowMs);
            trimMemory();
            IO.execute(() -> {
                try {
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_AID, aid);
                    values.put(COLUMN_LAST_SEEN, nowMs);
                    getDatabase().getWritableDatabase().insertWithOnConflict(
                            TABLE,
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_REPLACE
                    );
                    pruneDatabase(nowMs);
                } catch (Throwable throwable) {
                    Logger.printException(() -> "Seen video history write failed", throwable);
                }
            });
        }
    }

    private static void ensureLoaded() {
        synchronized (HISTORY_LOCK) {
            if (!LOAD_STARTED.compareAndSet(false, true)) {
                return;
            }
            final int loadGeneration = generation;
            IO.execute(() -> {
                long nowMs = System.currentTimeMillis();
                long cutoff = retentionCutoff(nowMs);
                try {
                    SQLiteDatabase readable = getDatabase().getReadableDatabase();
                    String selection = cutoff == Long.MIN_VALUE ? null : COLUMN_LAST_SEEN + " >= ?";
                    String[] selectionArgs = cutoff == Long.MIN_VALUE
                            ? null
                            : new String[]{String.valueOf(cutoff)};
                    try (Cursor cursor = readable.query(
                            TABLE,
                            new String[]{COLUMN_AID, COLUMN_LAST_SEEN},
                            selection,
                            selectionArgs,
                            null,
                            null,
                            COLUMN_LAST_SEEN + " DESC",
                            String.valueOf(MAX_RECORDS)
                    )) {
                        int aidColumn = cursor.getColumnIndexOrThrow(COLUMN_AID);
                        int seenColumn = cursor.getColumnIndexOrThrow(COLUMN_LAST_SEEN);
                        while (cursor.moveToNext()) {
                            String aid = normalizeAid(cursor.getString(aidColumn));
                            if (aid == null) {
                                continue;
                            }
                            long persisted = cursor.getLong(seenColumn);
                            synchronized (HISTORY_LOCK) {
                                if (generation != loadGeneration) break;
                                SEEN.merge(aid, persisted, Math::max);
                                trimMemory();
                            }
                        }
                    }
                    pruneDatabase(nowMs);
                } catch (Throwable throwable) {
                    Logger.printException(() -> "Seen video history load failed", throwable);
                }
            });
        }
    }

    private static void pruneDatabase(long nowMs) {
        long cutoff = retentionCutoff(nowMs);
        try {
            if (cutoff != Long.MIN_VALUE) {
            getDatabase().getWritableDatabase().delete(
                    TABLE,
                    COLUMN_LAST_SEEN + " < ?",
                    new String[]{String.valueOf(cutoff)}
            );
            }
            getDatabase().getWritableDatabase().execSQL(
                    "DELETE FROM " + TABLE + " WHERE " + COLUMN_AID + " NOT IN (SELECT "
                            + COLUMN_AID + " FROM " + TABLE + " ORDER BY " + COLUMN_LAST_SEEN
                            + " DESC LIMIT " + MAX_RECORDS + ")");
        } catch (Throwable throwable) {
            Logger.printException(() -> "Seen video history prune failed", throwable);
        }

        for (Map.Entry<String, Long> entry : SEEN.entrySet()) {
            Long timestamp = entry.getValue();
            if (timestamp != null && timestamp < cutoff) {
                SEEN.remove(entry.getKey(), timestamp);
            }
        }
    }

    private static void trimMemory() {
        // Called under HISTORY_LOCK. Scanning happens only when a new id reaches the cap.
        while (SEEN.size() > MAX_RECORDS) {
            Map.Entry<String, Long> oldest = null;
            for (Map.Entry<String, Long> entry : SEEN.entrySet()) {
                if (oldest == null || entry.getValue() < oldest.getValue()) oldest = entry;
            }
            if (oldest == null) return;
            SEEN.remove(oldest.getKey(), oldest.getValue());
        }
    }

    private static void deleteAsync(String aid, long expiredTimestamp) {
        IO.execute(() -> {
            try {
                getDatabase().getWritableDatabase().delete(
                        TABLE,
                        COLUMN_AID + " = ? AND " + COLUMN_LAST_SEEN + " <= ?",
                        new String[]{aid, String.valueOf(expiredTimestamp)}
                );
            } catch (Throwable throwable) {
                Logger.printException(() -> "Seen video history delete failed", throwable);
            }
        });
    }

    private static long retentionCutoff(long nowMs) {
        int days = retentionDays();
        if (days <= 0) {
            return Long.MIN_VALUE;
        }
        return nowMs - days * 24L * 60L * 60L * 1_000L;
    }

    /** Zero keeps everything; anything older than this many days is dropped. */
    private static int retentionDays() {
        return Math.max(0, Settings.SEEN_VIDEO_RETENTION_DAYS.get());
    }

    private static boolean hasReachedSeenThreshold(long positionMs, long durationMs) {
        long safePosition = Math.max(0L, positionMs);
        if (durationMs <= 0L) {
            return safePosition >= UNKNOWN_DURATION_MARK_MS;
        }

        long percentThreshold = Math.max(0L, durationMs) * MARK_PERCENT / 100L;
        long threshold = Math.max(MIN_MARK_MS, Math.min(MAX_MARK_MS, percentThreshold));
        return safePosition >= threshold;
    }

    private static String normalizeAid(String aid) {
        if (aid == null) {
            return null;
        }
        String normalized = aid.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Database getDatabase() {
        Database result = database;
        if (result != null) {
            return result;
        }

        synchronized (SeenVideoHistory.class) {
            result = database;
            if (result == null) {
                Context context = app.morphe.extension.shared.Utils.getContext();
                if (context == null) {
                    throw new IllegalStateException("Application context is not available");
                }
                result = new Database(context.getApplicationContext());
                database = result;
            }
            return result;
        }
    }

    private static final class Database extends SQLiteOpenHelper {
        Database(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE " + TABLE + " (" +
                            COLUMN_AID + " TEXT PRIMARY KEY NOT NULL, " +
                            COLUMN_LAST_SEEN + " INTEGER NOT NULL" +
                            ")"
            );
            db.execSQL(
                    "CREATE INDEX IF NOT EXISTS seen_videos_last_seen " +
                            "ON " + TABLE + " (" + COLUMN_LAST_SEEN + ")"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            onCreate(db);
        }
    }
}
