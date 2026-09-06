package app.morphe.extension.tiktok.seen;

import static org.junit.Assert.*;

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
