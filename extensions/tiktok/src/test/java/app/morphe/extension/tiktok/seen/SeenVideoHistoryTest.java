package app.morphe.extension.tiktok.seen;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SeenVideoHistoryTest {
    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.HIDE_SEEN_VIDEOS.save(true);
        Settings.SEEN_VIDEO_RETENTION_DAYS.save(30);
        SeenVideoHistory.clear();
        drain();
    }

    @Test public void clearingKeepsAWayBackUntilTheNextClear() throws Exception {
        SeenVideoHistory.onPlayProgressChange("42", 5000, 10000);
        SeenVideoHistory.onPlayProgressChange("43", 5000, 10000);
        drain();
        assertEquals(2, SeenVideoHistory.size());

        SeenVideoHistory.clear();
        drain();
        assertEquals(0, SeenVideoHistory.size());
        assertTrue(SeenVideoHistory.canUndo());
        assertEquals(2, SeenVideoHistory.undoSize());
        assertFalse(SeenVideoHistory.shouldHide("42"));

        assertTrue(SeenVideoHistory.undoClear());
        drain();
        assertEquals(2, SeenVideoHistory.size());
        assertTrue(SeenVideoHistory.shouldHide("42"));
        assertTrue(SeenVideoHistory.shouldHide("43"));
        // The way back is used up once it has been taken.
        assertFalse(SeenVideoHistory.canUndo());
        assertEquals(0, SeenVideoHistory.undoSize());
        assertFalse(SeenVideoHistory.undoClear());
    }

    @Test public void whatWasPutBackSurvivesAReload() throws Exception {
        SeenVideoHistory.onPlayProgressChange("77", 5000, 10000);
        drain();
        SeenVideoHistory.clear();
        drain();
        assertTrue(SeenVideoHistory.undoClear());
        drain();

        // The rows go back to the database, not just to memory: a reload finds them again.
        Field started = SeenVideoHistory.class.getDeclaredField("LOAD_STARTED");
        started.setAccessible(true);
        ((AtomicBoolean) started.get(null)).set(false);
        Field seen = SeenVideoHistory.class.getDeclaredField("SEEN");
        seen.setAccessible(true);
        ((java.util.Map<?, ?>) seen.get(null)).clear();

        // The reload runs on the same worker, so the first read only starts it.
        SeenVideoHistory.size();
        drain();

        assertEquals(1, SeenVideoHistory.size());
        assertTrue(SeenVideoHistory.shouldHide("77"));
    }

    @Test public void nothingToClearMeansNothingToPutBack() throws Exception {
        assertEquals(0, SeenVideoHistory.size());
        SeenVideoHistory.clear();
        drain();
        assertEquals(0, SeenVideoHistory.undoSize());
        assertFalse(SeenVideoHistory.undoClear());
    }

    @Test public void aClearBeforeTheHistoryLoadsStillKeepsAWayBack() throws Exception {
        // The rows are on disk but memory has never been loaded, which is what happens when
        // the feature is off. Clearing has to read the database, not the empty map.
        SeenVideoHistory.onPlayProgressChange("91", 5000, 10000);
        SeenVideoHistory.onPlayProgressChange("92", 5000, 10000);
        drain();

        Field started = SeenVideoHistory.class.getDeclaredField("LOAD_STARTED");
        started.setAccessible(true);
        ((AtomicBoolean) started.get(null)).set(false);
        Field seen = SeenVideoHistory.class.getDeclaredField("SEEN");
        seen.setAccessible(true);
        ((java.util.Map<?, ?>) seen.get(null)).clear();

        SeenVideoHistory.clear();
        drain();
        assertEquals("the copy comes off the database", 2, SeenVideoHistory.undoSize());

        assertTrue(SeenVideoHistory.undoClear());
        drain();
        assertTrue(SeenVideoHistory.shouldHide("91"));
        assertTrue(SeenVideoHistory.shouldHide("92"));
    }

    @Test public void refreshedPageRejectsTheVideoJustWatched() throws Exception {
        SeenVideoHistory.onPlayProgressChange("42", 5000, 10000);
        drain();
        assertTrue(SeenVideoHistory.shouldHide("42"));
    }

    @Test public void disabledFilterPreservesWatchedVideos() throws Exception {
        SeenVideoHistory.onPlayProgressChange("42", 5000, 10000);
        drain();
        Settings.HIDE_SEEN_VIDEOS.save(false);
        assertFalse(SeenVideoHistory.shouldHide("42"));
    }

    @Test public void pendingLoadCannotRestoreClearedHistory() throws Exception {
        database().execSQL("INSERT INTO seen_videos VALUES ('old', ?)",
                new Object[]{System.currentTimeMillis()});
        ((AtomicBoolean) field("LOAD_STARTED")).set(false);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        io().execute(() -> {
            ready.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        try {
            SeenVideoHistory.size();
            SeenVideoHistory.clear();
        } finally { release.countDown(); }
        drain();
        assertEquals(0, SeenVideoHistory.size());
        assertFalse(SeenVideoHistory.shouldHide("old"));
    }

    @Test public void failedInitialOpenCanRetryAndLoadHistory() throws Exception {
        Field databaseField = SeenVideoHistory.class.getDeclaredField("database");
        databaseField.setAccessible(true);
        Object previousDatabase = databaseField.get(null);
        databaseField.set(null, null);
        ((AtomicBoolean) field("LOAD_STARTED")).set(false);

        Field factoryField = SeenVideoHistory.class.getDeclaredField("databaseFactory");
        factoryField.setAccessible(true);
        Object previousFactory = factoryField.get(null);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        factoryField.set(null, (SeenVideoHistory.DatabaseFactory) context ->
                new SeenVideoHistory.Database(context) {
                    @Override public SQLiteDatabase getReadableDatabase() {
                        if (failOnce.compareAndSet(true, false)) {
                            throw new IllegalStateException("injected open failure");
                        }
                        return super.getReadableDatabase();
                    }
                });
        try {
            SeenVideoHistory.size();
            drain();
            assertFalse("a failed open must allow a later retry",
                    ((AtomicBoolean) field("LOAD_STARTED")).get());

            // The failed helper remains usable once the transient open error is gone.
            database().execSQL("INSERT OR REPLACE INTO seen_videos VALUES ('retry', ?)",
                    new Object[]{System.currentTimeMillis()});
            SeenVideoHistory.size();
            drain();
            try (android.database.Cursor cursor = database().rawQuery(
                    "SELECT aid FROM seen_videos WHERE aid = 'retry'", null)) {
                assertEquals("the retried database should still contain the row", 1, cursor.getCount());
            }
            assertEquals("the retried load should see the persisted row", 1, SeenVideoHistory.size());
            assertTrue(SeenVideoHistory.shouldHide("retry"));
        } finally {
            factoryField.set(null, previousFactory);
            databaseField.set(null, previousDatabase);
            failOnce.set(false);
        }
    }

    @Test public void failedInitialOpenAfterClearStillAllowsALaterRetry() throws Exception {
        Field databaseField = SeenVideoHistory.class.getDeclaredField("database");
        databaseField.setAccessible(true);
        Object previousDatabase = databaseField.get(null);
        databaseField.set(null, null);
        Field factoryField = SeenVideoHistory.class.getDeclaredField("databaseFactory");
        factoryField.setAccessible(true);
        Object previousFactory = factoryField.get(null);
        CountDownLatch openStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        factoryField.set(null, (SeenVideoHistory.DatabaseFactory) context ->
                new SeenVideoHistory.Database(context) {
                    @Override public SQLiteDatabase getReadableDatabase() {
                        if (failOnce.compareAndSet(true, false)) {
                            openStarted.countDown();
                            try { release.await(5, TimeUnit.SECONDS); }
                            catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                            }
                            throw new IllegalStateException("injected open failure after clear");
                        }
                        return super.getReadableDatabase();
                    }
                });
        ((AtomicBoolean) field("LOAD_STARTED")).set(false);
        try {
            SeenVideoHistory.size();
            assertTrue(openStarted.await(5, TimeUnit.SECONDS));
            SeenVideoHistory.clear(); // Advances the generation while the first open waits.
            release.countDown();
            drain();
            assertFalse("a failed open after clear must release the retry gate",
                    ((AtomicBoolean) field("LOAD_STARTED")).get());
        } finally {
            release.countDown();
            factoryField.set(null, previousFactory);
            databaseField.set(null, previousDatabase);
            failOnce.set(false);
        }
    }

    @Test public void failedUndoCommitRetainsRecoveryAndReportsFailure() throws Exception {
        SeenVideoHistory.onPlayProgressChange("failed-undo", 5000, 10000);
        drain();
        SeenVideoHistory.clear();
        drain();
        assertEquals(1, SeenVideoHistory.undoSize());

        Field writerField = SeenVideoHistory.class.getDeclaredField("rowWriter");
        writerField.setAccessible(true);
        Object previousWriter = writerField.get(null);
        writerField.set(null, (SeenVideoHistory.RowWriter) (database, values) -> -1L);
        AtomicReference<SeenVideoHistory.UndoResult> result = new AtomicReference<>();
        CountDownLatch callback = new CountDownLatch(1);
        try {
            assertTrue(SeenVideoHistory.undoClear(undoResult -> {
                result.set(undoResult);
                callback.countDown();
            }));
            drain();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(callback.await(5, TimeUnit.SECONDS));
            assertEquals(SeenVideoHistory.UndoResult.FAILED, result.get());
            assertTrue("a rejected insert must keep the recovery copy", SeenVideoHistory.canUndo());
            assertEquals(1, SeenVideoHistory.undoSize());
        } finally {
            writerField.set(null, previousWriter);
        }

        assertTrue(SeenVideoHistory.undoClear());
        drain();
        assertFalse(SeenVideoHistory.canUndo());
        assertTrue(SeenVideoHistory.shouldHide("failed-undo"));
    }

    @Test public void staleExpiryCannotDeleteARefreshedRecord() throws Exception {
        long now = System.currentTimeMillis();
        database().execSQL("INSERT INTO seen_videos VALUES ('42', ?)", new Object[]{now});
        Method delete = SeenVideoHistory.class.getDeclaredMethod("deleteAsync", String.class, long.class);
        delete.setAccessible(true);
        delete.invoke(null, "42", now - 1000);
        drain();
        try (android.database.Cursor c = database().rawQuery("SELECT aid FROM seen_videos", null)) {
            assertEquals(1, c.getCount());
        }
    }

    @Test public void historyHasAHardLimitEvenWithoutAgeRetention() throws Exception {
        Settings.SEEN_VIDEO_RETENTION_DAYS.save(0);
        SQLiteDatabase db = database();
        db.beginTransaction();
        try {
            for (int i = 0; i < 10005; i++) {
                db.execSQL("INSERT INTO seen_videos VALUES (?, ?)", new Object[]{"id" + i, (long) i});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        ((AtomicBoolean) field("LOAD_STARTED")).set(false);
        SeenVideoHistory.size();
        drain();
        assertEquals(10000, SeenVideoHistory.size());
        assertFalse(SeenVideoHistory.shouldHide("id0"));
        assertTrue(SeenVideoHistory.shouldHide("id10004"));
        try (android.database.Cursor c = db.rawQuery("SELECT aid FROM seen_videos", null)) {
            assertEquals(10000, c.getCount());
        }
    }

    @Test public void aTapBeforeTheCopyIsReadKeepsTheWayBack() throws Exception {
        SeenVideoHistory.onPlayProgressChange("91", 5000, 10000);
        SeenVideoHistory.onPlayProgressChange("92", 5000, 10000);
        drain();

        // Hold the worker so the clear queues its read behind the latch, which is the window
        // the toast invites a second tap into.
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        io().execute(() -> {
            ready.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        try {
            SeenVideoHistory.clear();
            assertTrue(SeenVideoHistory.canUndo());
            // The copy is not there yet, so there is nothing to put back this instant, but the
            // way back must survive the attempt.
            assertFalse(SeenVideoHistory.undoClear());
            assertTrue("the offer is not spent by a tap that was too early",
                    SeenVideoHistory.canUndo());
        } finally { release.countDown(); }
        drain();

        assertTrue(SeenVideoHistory.canUndo());
        assertEquals(2, SeenVideoHistory.undoSize());
        assertTrue(SeenVideoHistory.undoClear());
        drain();
        assertTrue(SeenVideoHistory.shouldHide("91"));
        assertTrue(SeenVideoHistory.shouldHide("92"));
    }

    /**
     * An undo that a newer clear overtakes used to return from its worker without telling anyone,
     * so a caller waiting on the callback waited forever and its row stayed offering an undo that
     * would never run.
     */
    @Test public void anUndoOvertakenByANewerClearStillReportsBack() throws Exception {
        SeenVideoHistory.onPlayProgressChange("81", 5000, 10000);
        drain();
        SeenVideoHistory.clear();
        drain();
        assertTrue(SeenVideoHistory.canUndo());

        // Hold the worker so the undo queues, then let a newer clear take the generation.
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        io().execute(() -> {
            ready.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS));

        AtomicReference<SeenVideoHistory.UndoResult> result = new AtomicReference<>();
        CountDownLatch callback = new CountDownLatch(1);
        try {
            assertTrue(SeenVideoHistory.undoClear(undoResult -> {
                result.set(undoResult);
                callback.countDown();
            }));
            SeenVideoHistory.clear();
        } finally {
            release.countDown();
        }
        drain();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue("the overtaken undo never reported back", callback.await(5, TimeUnit.SECONDS));
        assertEquals(SeenVideoHistory.UndoResult.SUPERSEDED, result.get());
    }

    @Test public void puttingTheRecordBackKeepsTheNewerSighting() throws Exception {
        SeenVideoHistory.onPlayProgressChange("55", 5000, 10000);
        drain();
        SeenVideoHistory.clear();
        drain();

        // Watched again after the clear, so memory holds a newer time than the copy does.
        SeenVideoHistory.onPlayProgressChange("55", 5000, 10000);
        drain();
        long watchedAgain = seenAt("55");

        assertTrue(SeenVideoHistory.undoClear());
        drain();
        assertEquals("the older copy must not overwrite it", watchedAgain, seenAt("55"));

        // And what went back to the database is the newer time too, or the next load would
        // quietly undo the merge.
        try (android.database.Cursor c = database().rawQuery(
                "SELECT last_seen_ms FROM seen_videos WHERE aid = '55'", null)) {
            assertTrue(c.moveToFirst());
            assertEquals("the row on disk must match memory", watchedAgain, c.getLong(0));
        }
    }

    private static long seenAt(String aid) throws Exception {
        Field f = SeenVideoHistory.class.getDeclaredField("SEEN");
        f.setAccessible(true);
        Object value = ((java.util.Map<?, ?>) f.get(null)).get(aid);
        assertNotNull("nothing recorded for " + aid, value);
        return (Long) value;
    }

    @Test public void aSchemaChangeKeepsWhatWasAlreadyWatched() throws Exception {
        long watched = System.currentTimeMillis();
        database().execSQL("INSERT INTO seen_videos VALUES ('kept', ?)", new Object[]{watched});

        // What SQLiteOpenHelper calls when the version moves in either direction. Neither is
        // allowed to take the record with it.
        SQLiteOpenHelper helper = (SQLiteOpenHelper) field("database");
        helper.onUpgrade(database(), 1, 2);
        helper.onDowngrade(database(), 2, 1);

        try (android.database.Cursor c = database().rawQuery(
                "SELECT last_seen_ms FROM seen_videos WHERE aid = 'kept'", null)) {
            assertTrue("the row survived the migration", c.moveToFirst());
            assertEquals(watched, c.getLong(0));
        }
    }

    private static Object field(String name) throws Exception {
        Field f = SeenVideoHistory.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }
    private static ExecutorService io() throws Exception { return (ExecutorService) field("IO"); }
    private static void drain() throws Exception { io().submit(() -> {}).get(15, TimeUnit.SECONDS); }
    private static SQLiteDatabase database() throws Exception {
        return ((SQLiteOpenHelper) field("database")).getWritableDatabase();
    }
}
