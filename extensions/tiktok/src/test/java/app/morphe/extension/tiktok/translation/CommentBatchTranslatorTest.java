package app.morphe.extension.tiktok.translation;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.junit.runner.RunWith;

/** Request state around the native comment translation bridge. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CommentBatchTranslatorTest {
    private Context context;

    @Before public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        clearTranslatorState();
        NativeManager.reset();
        Settings.COMMENT_BATCH_TRANSLATION.save(true);
    }

    @After public void tearDown() throws Exception {
        Settings.COMMENT_BATCH_TRANSLATION.save(false);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        clearTranslatorState();
        NativeManager.reset();
    }

    @Test public void disablingBeforeTheDelayedCallbackDoesNotDispatch() {
        Anchor anchor = anchor("aid-disabled", "cid-disabled");
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);

        Settings.COMMENT_BATCH_TRANSLATION.save(false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(351));

        assertEquals(0, NativeManager.requests);
    }

    @Test public void concurrentTriggersReserveOnePendingRequest() {
        Anchor anchor = loadedAnchor("aid-pending", "cid-pending");
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);

        assertEquals(1, NativeManager.requests);
    }

    @Test public void aThrownNativeRequestCanBeRetried() {
        Anchor anchor = loadedAnchor("aid-retry", "cid-retry");
        NativeManager.fail = true;
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(1, NativeManager.requests);

        NativeManager.fail = false;
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(2, NativeManager.requests);
    }

    @Test public void failedCompletionRetriesButSuccessfulCompletionStaysDeduplicated() {
        Anchor failed = loadedAnchor("aid-failed-completion", "cid-failed-completion");
        CommentBatchTranslator.registerCommentCell(new View(context), failed);
        CommentBatchTranslator.onNativeBatchComplete(new Runner(null, failed.comment));
        CommentBatchTranslator.registerCommentCell(new View(context), failed);
        assertEquals(2, NativeManager.requests);

        Anchor successful = loadedAnchor("aid-success", "cid-success");
        CommentBatchTranslator.registerCommentCell(new View(context), successful);
        CommentBatchTranslator.onNativeBatchComplete(new Runner(new Object(), successful.comment));
        CommentBatchTranslator.registerCommentCell(new View(context), successful);
        assertEquals(3, NativeManager.requests);
    }

    @Test public void lateFailureCannotRemoveANewerRetryReservation() throws Exception {
        Anchor anchor = loadedAnchor("aid-race", "cid-race");
        NativeManager.blockFirst = true;
        NativeManager.failFirst = true;

        Thread firstRequest = new Thread(() ->
                CommentBatchTranslator.registerCommentCell(new View(context), anchor));
        firstRequest.start();
        assertTrue(NativeManager.firstStarted.await(5, TimeUnit.SECONDS));

        invokePrune(SystemClock.elapsedRealtime() + 16_000L);
        NativeManager.blockFirst = false;
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);

        NativeManager.releaseFirst.countDown();
        firstRequest.join(5_000L);
        assertFalse(firstRequest.isAlive());
        assertEquals(1, pendingRequestCount());
    }

    private static Anchor loadedAnchor(String aid, String cid) {
        Anchor anchor = anchor(aid, cid);
        CommentBatchTranslator.onCommentListLoaded(new CommentItemList(anchor.comment));
        return anchor;
    }

    private static Anchor anchor(String aid, String cid) {
        return new Anchor(new Comment(aid, cid), new TranslationContext(aid));
    }

    private static void invokePrune(long now) throws Exception {
        Field lockField = CommentBatchTranslator.class.getDeclaredField("LOCK");
        lockField.setAccessible(true);
        Object lock = lockField.get(null);
        java.lang.reflect.Method prune = CommentBatchTranslator.class.getDeclaredMethod(
                "pruneLocked", long.class);
        prune.setAccessible(true);
        synchronized (lock) {
            prune.invoke(null, now);
        }
    }

    @SuppressWarnings("unchecked")
    private static int pendingRequestCount() throws Exception {
        Field lockField = CommentBatchTranslator.class.getDeclaredField("LOCK");
        lockField.setAccessible(true);
        Object lock = lockField.get(null);
        Field pendingField = CommentBatchTranslator.class.getDeclaredField("pendingRequests");
        pendingField.setAccessible(true);
        synchronized (lock) {
            return ((Map<String, ?>) pendingField.get(null)).size();
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearTranslatorState() throws Exception {
        Field lockField = CommentBatchTranslator.class.getDeclaredField("LOCK");
        lockField.setAccessible(true);
        Object lock = lockField.get(null);
        synchronized (lock) {
            for (String name : new String[]{"visibleComments", "loadedBatches",
                    "requestedLoadedBatchKeys", "pendingRequests"}) {
                Field field = CommentBatchTranslator.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof Map) ((Map<?, ?>) value).clear();
                else ((java.util.Collection<?>) value).clear();
            }
            Field latest = CommentBatchTranslator.class.getDeclaredField("latestLoadedBatch");
            latest.setAccessible(true);
            latest.set(null, null);
            Field manager = CommentBatchTranslator.class.getDeclaredField("lastManager");
            manager.setAccessible(true);
            manager.set(null, new WeakReference<>(null));
        }
    }

    public static final class Comment {
        private final String aid;
        private final String cid;

        Comment(String aid, String cid) {
            this.aid = aid;
            this.cid = cid;
        }

        public String getAid() { return aid; }
        public String getAwemeId() { return aid; }
        public String getCid() { return cid; }
        public boolean isTranslated() { return false; }
        public String getCommentLanguage() { return "zh"; }
    }

    public static final class CommentItemList {
        public final List<Comment> items;

        CommentItemList(Comment... comments) {
            items = Arrays.asList(comments);
        }
    }

    public static final class TranslationContext {
        public final String LIZIZ;

        TranslationContext(String aid) {
            LIZIZ = aid;
        }
    }

    public static final class Anchor {
        public final Comment comment;
        public final NativeManager nativeManager = new NativeManager();
        public final TranslationContext context;

        Anchor(Comment comment, TranslationContext context) {
            this.comment = comment;
            this.context = context;
        }
    }

    public static final class NativeManager {
        static int requests;
        static boolean fail;
        static boolean blockFirst;
        static boolean failFirst;
        static CountDownLatch firstStarted = new CountDownLatch(1);
        static CountDownLatch releaseFirst = new CountDownLatch(1);

        public static void LJFF(List<Object> comments, TranslationContext context, boolean force) {
            int requestNumber = ++requests;
            if (requestNumber == 1 && blockFirst) {
                firstStarted.countDown();
                try {
                    releaseFirst.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                if (failFirst) throw new IllegalStateException("simulated late failure");
            }
            if (fail) throw new IllegalStateException("simulated translation failure");
        }

        static void reset() {
            requests = 0;
            fail = false;
            blockFirst = false;
            failFirst = false;
            firstStarted = new CountDownLatch(1);
            releaseFirst = new CountDownLatch(1);
        }
    }

    public static final class Task {
        public final List<Comment> LIZ;

        Task(Comment comment) {
            LIZ = new ArrayList<>();
            LIZ.add(comment);
        }
    }

    public static final class Runner {
        public final Object l0;
        public final Task l1;

        Runner(Object results, Comment comment) {
            l0 = results;
            l1 = new Task(comment);
        }
    }
}
