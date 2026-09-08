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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        com.ss.android.ugc.aweme.translation.service.TranslationLangKevaServiceImpl.reset();
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

    @Test public void aBatchNobodyAskedForCostsNothingWhileTheFeatureIsOff() {
        // This is injected at index 0 of TikTok's own completion method, so it runs for every
        // native translation batch in the app, including the ones this feature never asked for.
        // It used to walk the declared fields of two objects and take the global lock for all of
        // them.
        Settings.COMMENT_BATCH_TRANSLATION.save(false);
        int handled = CommentBatchTranslator.completionsHandledForTests();

        for (int batch = 0; batch < 50; batch++) {
            CommentBatchTranslator.onNativeBatchComplete(
                    new Runner(new Object(), new Comment("aid-idle", "cid-idle-" + batch)));
        }

        assertEquals("a batch this feature never asked for was walked anyway",
                handled, CommentBatchTranslator.completionsHandledForTests());
    }

    @Test public void aRequestStillInFlightIsFinishedAfterTheSwitchGoesOff() {
        // The other half: turning the switch off while a request is out must not strand it.
        Anchor anchor = loadedAnchor("aid-inflight", "cid-inflight");
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals("the request never went out", 1, NativeManager.requests);

        Settings.COMMENT_BATCH_TRANSLATION.save(false);
        int handled = CommentBatchTranslator.completionsHandledForTests();
        CommentBatchTranslator.onNativeBatchComplete(new Runner(new Object(), anchor.comment));

        assertEquals("a request still in flight was dropped when the switch went off",
                handled + 1, CommentBatchTranslator.completionsHandledForTests());
    }

    @Test public void commentsAlreadyInTheCurrentLanguageAreNotDispatched() {
        android.content.res.Configuration configuration =
                new android.content.res.Configuration(context.getResources().getConfiguration());
        java.util.Locale previous = configuration.getLocales().isEmpty()
                ? java.util.Locale.getDefault() : configuration.getLocales().get(0);
        try {
            configuration.setLocale(java.util.Locale.forLanguageTag("zh-CN"));
            context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
            registerCommentCellAndWait(anchor("aid-same-language", "cid-same-language"));
            assertEquals(0, NativeManager.requests);
        } finally {
            configuration.setLocale(previous);
            context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
        }
    }

    private static void registerCommentCellAndWait(Anchor anchor) {
        CommentBatchTranslator.onCommentListLoaded(new CommentItemList(anchor.comment));
        CommentBatchTranslator.registerCommentCell(new View(RuntimeEnvironment.getApplication()), anchor);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(351));
    }

    @Test public void concurrentTriggersReserveOnePendingRequest() {
        Anchor anchor = loadedAnchor("aid-pending", "cid-pending");
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);

        assertEquals(1, NativeManager.requests);
    }

    @Test public void concurrentDistinctBatchesKeepUniqueRequestGenerations() throws Exception {
        Anchor first = loadedAnchor("aid-generation-first", "cid-generation-first");
        Anchor second = loadedAnchor("aid-generation-second", "cid-generation-second");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRequest = executor.submit(() ->
                    CommentBatchTranslator.registerCommentCell(new View(context), first));
            Future<?> secondRequest = executor.submit(() ->
                    CommentBatchTranslator.registerCommentCell(new View(context), second));
            firstRequest.get(5, TimeUnit.SECONDS);
            secondRequest.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Field pendingField = CommentBatchTranslator.class.getDeclaredField("pendingRequests");
        pendingField.setAccessible(true);
        Field generationField = Class.forName(
                "app.morphe.extension.tiktok.translation.CommentBatchTranslator$PendingRequest"
        ).getDeclaredField("generation");
        generationField.setAccessible(true);
        java.util.Set<Long> generations = new java.util.HashSet<>();
        synchronized (getTranslatorLock()) {
            for (Object pending : ((Map<?, ?>) pendingField.get(null)).values()) {
                generations.add(generationField.getLong(pending));
            }
        }
        assertEquals(2, generations.size());
        assertEquals(2, pendingRequestCount());
    }

    @Test public void aThrownNativeRequestCanBeRetried() {
        Anchor anchor = loadedAnchor("aid-retry", "cid-retry");
        NativeManager.fail = true;
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(1, NativeManager.requests);

        NativeManager.fail = false;
        // Past the first backoff window. A throw used to be retried on the very next bind, which
        // is milliseconds later and hundreds of times over while a comment list scrolls.
        idleFor(2_100L);
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(2, NativeManager.requests);
    }

    @Test public void failedCompletionRetriesButSuccessfulCompletionStaysDeduplicated() {
        Anchor failed = loadedAnchor("aid-failed-completion", "cid-failed-completion");
        CommentBatchTranslator.registerCommentCell(new View(context), failed);
        CommentBatchTranslator.onNativeBatchComplete(new Runner(null, failed.comment));
        idleFor(2_100L); // The first backoff window, which this test predates.
        CommentBatchTranslator.registerCommentCell(new View(context), failed);
        assertEquals(2, NativeManager.requests);

        Anchor successful = loadedAnchor("aid-success", "cid-success");
        CommentBatchTranslator.registerCommentCell(new View(context), successful);
        CommentBatchTranslator.onNativeBatchComplete(new Runner(new Object(), successful.comment));
        CommentBatchTranslator.registerCommentCell(new View(context), successful);
        assertEquals(3, NativeManager.requests);
    }

    @Test public void expiredLoadedAndVisibleBatchesCanBeRequestedAgain() throws Exception {
        Anchor anchor = loadedAnchor("aid-expired", "cid-expired");
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(1, NativeManager.requests);
        CommentBatchTranslator.onNativeBatchComplete(new Runner(new Object(), anchor.comment));

        invokePrune(SystemClock.elapsedRealtime() + 61_000L);
        assertEquals(0, loadedBatchCount());
        Anchor reloaded = loadedAnchor("aid-expired-reloaded", "cid-expired-reloaded");
        CommentBatchTranslator.registerCommentCell(new View(context), reloaded);
        assertEquals(2, NativeManager.requests);
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

    @Test public void lateCompletionCannotRemoveANewerRetryReservation() throws Exception {
        Anchor anchor = loadedAnchor("aid-completion-race", "cid-completion-race");
        NativeManager.blockFirst = true;

        Thread firstRequest = new Thread(() ->
                CommentBatchTranslator.registerCommentCell(new View(context), anchor));
        firstRequest.start();
        assertTrue(NativeManager.firstStarted.await(5, TimeUnit.SECONDS));

        invokePrune(SystemClock.elapsedRealtime() + 16_000L);
        NativeManager.blockFirst = false;
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);

        CommentBatchTranslator.onNativeBatchComplete(new Runner(new Object(), anchor.comment));
        assertEquals(1, pendingRequestCount());

        NativeManager.releaseFirst.countDown();
        firstRequest.join(5_000L);
        assertFalse(firstRequest.isAlive());
        CommentBatchTranslator.onNativeBatchComplete(new Runner(new Object(), anchor.comment));
        assertEquals(0, pendingRequestCount());

        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(2, NativeManager.requests);
    }

    @Test public void twoFailuresInARowStopTheThirdRequestUntilTheBackoffIsOver() {
        Anchor anchor = loadedAnchor("aid-backoff", "cid-backoff");

        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(1, NativeManager.requests);
        CommentBatchTranslator.onNativeBatchComplete(new Runner(null, anchor.comment));

        idleFor(2_100L);
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(2, NativeManager.requests);
        CommentBatchTranslator.onNativeBatchComplete(new Runner(null, anchor.comment));

        // A comment list binds cells many times a second. None of these may reach the host.
        for (int bind = 0; bind < 20; bind++) {
            idleFor(100L);
            CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        }
        assertEquals("the third try went out inside the eight second window",
                2, NativeManager.requests);

        idleFor(6_500L);
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals("the third try never went out at all", 3, NativeManager.requests);
    }

    @Test public void theLastFailureGivesUpOnTheBatchRatherThanWaitingLonger() throws Exception {
        // Three waits sit between four tries, and the last of them is the thirty second one. It
        // used to be unreachable: the give-up test fired one try early, so the delay the class
        // and the changelog both advertised was never used.
        // The waits are the real windows and nothing is idled after the last failure. Reaching
        // the cap remembers the key rather than starting another window, so a bind straight
        // after proves the give-up rather than a backoff. Waiting instead used to idle past the
        // sixty seconds a loaded batch lives for, and a batch that had aged out would not have
        // been dispatched whatever the attempt count said.
        Anchor anchor = loadedAnchor("aid-give-up", "cid-give-up");
        long[] waits = {2_100L, 8_500L, 30_500L};
        for (int attempt = 1; attempt <= 4; attempt++) {
            CommentBatchTranslator.registerCommentCell(new View(context), anchor);
            assertEquals("try " + attempt + " never reached the host", attempt, NativeManager.requests);
            CommentBatchTranslator.onNativeBatchComplete(new Runner(null, anchor.comment));
            if (attempt < 4) idleFor(waits[attempt - 1]);
        }
        assertEquals("the batch aged out before the give-up could be seen", 1, loadedBatchCount());
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals("a batch that failed four times is still being asked for",
                4, NativeManager.requests);
    }

    @Test public void theThirtySecondWindowIsRealRatherThanAdvertised() {
        Anchor anchor = loadedAnchor("aid-third-window", "cid-third-window");
        for (int attempt = 1; attempt <= 3; attempt++) {
            CommentBatchTranslator.registerCommentCell(new View(context), anchor);
            assertEquals(attempt, NativeManager.requests);
            CommentBatchTranslator.onNativeBatchComplete(new Runner(null, anchor.comment));
            if (attempt < 3) idleFor(attempt == 1 ? 2_100L : 8_500L);
        }

        idleFor(20_000L);
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals("the fourth try went out inside the thirty second window",
                3, NativeManager.requests);

        idleFor(11_000L);
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals("the fourth try never went out at all", 4, NativeManager.requests);
    }

    @Test public void aHostThatAnswersWithoutTranslatingAnythingIsNotTakenForProgress() {
        // Returned but never marked translated: the comment stays in the next batch, the key is
        // unchanged, and treating any answer at all as progress cleared the attempt count every
        // round. Forty binds, forty requests, no backoff, the same three comments each time.
        Comment[] all = {new Comment("aid-loop", "cid-loop-0"), new Comment("aid-loop", "cid-loop-1"),
                new Comment("aid-loop", "cid-loop-2")};
        CommentBatchTranslator.onCommentListLoaded(new CommentItemList(all));
        Anchor anchor = new Anchor(all[0], new TranslationContext("aid-loop"));

        for (int bind = 0; bind < 12; bind++) {
            CommentBatchTranslator.registerCommentCell(new View(context), anchor);
            CommentBatchTranslator.onNativeBatchComplete(
                    new Runner(Arrays.asList(all[0]), Arrays.asList(all)));
        }
        assertTrue("a host that translates nothing was asked over and over: "
                + NativeManager.requests + " requests", NativeManager.requests <= 4);
    }

    @Test public void aBatchAnsweredAFewCommentsAtATimeIsSeenThroughToTheEnd() {
        // The key is built from the whole loaded list, so every round carries the same one.
        // Counting those rounds as failures abandoned a long list part way through.
        Comment[] all = new Comment[9];
        for (int index = 0; index < all.length; index++) {
            all[index] = new Comment("aid-drip", "cid-drip-" + index);
        }
        CommentBatchTranslator.onCommentListLoaded(new CommentItemList(all));
        Anchor anchor = new Anchor(all[0], new TranslationContext("aid-drip"));

        // Three at a time, which is more rounds than the attempt cap allows for a failure.
        for (int round = 1; round <= 3; round++) {
            CommentBatchTranslator.registerCommentCell(new View(context), anchor);
            assertEquals("round " + round + " was never dispatched", round, NativeManager.requests);
            List<Comment> translated = new ArrayList<>();
            for (int index = (round - 1) * 3; index < round * 3; index++) {
                all[index].translated = true;
                translated.add(all[index]);
            }
            CommentBatchTranslator.onNativeBatchComplete(
                    new Runner(translated, Arrays.asList(all)));
        }
        assertEquals("a list translated three at a time was abandoned part way through",
                3, NativeManager.requests);
    }

    @Test public void aCommentListLoadingAgainAsksForItsBatchAgain() {
        Anchor anchor = loadedAnchor("aid-reload", "cid-reload");
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(1, NativeManager.requests);
        CommentBatchTranslator.onNativeBatchComplete(new Runner(new Object(), anchor.comment));

        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals("a settled batch was asked for twice", 1, NativeManager.requests);

        // The same comments arriving again is a fresh ask, which is what the class claims by
        // saying a batch is left alone "until the list reloads".
        CommentBatchTranslator.onCommentListLoaded(new CommentItemList(anchor.comment));
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals("reloading the comment list did not start the batch over",
                2, NativeManager.requests);
    }

    @Test public void aBatchThatComesBackWithOneOfThreeTranslatedIsAskedForAgain() {
        Comment first = new Comment("aid-partial", "cid-partial-1");
        Comment second = new Comment("aid-partial", "cid-partial-2");
        Comment third = new Comment("aid-partial", "cid-partial-3");
        CommentBatchTranslator.onCommentListLoaded(new CommentItemList(first, second, third));
        Anchor anchor = new Anchor(first, new TranslationContext("aid-partial"));

        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(1, NativeManager.requests);

        // One of the three came back translated. The batch used to be marked done on any answer
        // at all, so the other two were never asked for again.
        CommentBatchTranslator.onNativeBatchComplete(
                new Runner(Arrays.asList(first), Arrays.asList(first, second, third)));

        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals("the two comments that came back untranslated were never asked for again",
                2, NativeManager.requests);
        assertTrue("the second request did not carry the untranslated comments",
                NativeManager.lastRequestedCids.containsAll(
                        Arrays.asList("cid-partial-2", "cid-partial-3")));
    }

    /**
     * The control for the partial case above. It passed before the change too, which is the
     * point: it is here so the new "did the whole batch come back" test cannot be tightened
     * into refusing an answer that was in fact complete.
     */
    @Test public void aWholeBatchComingBackTranslatedIsNotAskedForAgain() {
        Comment first = new Comment("aid-whole", "cid-whole-1");
        Comment second = new Comment("aid-whole", "cid-whole-2");
        CommentBatchTranslator.onCommentListLoaded(new CommentItemList(first, second));
        Anchor anchor = new Anchor(first, new TranslationContext("aid-whole"));

        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(1, NativeManager.requests);
        CommentBatchTranslator.onNativeBatchComplete(
                new Runner(Arrays.asList(first, second), Arrays.asList(first, second)));

        idleFor(31_000L);
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals("a batch that came back complete was asked for a second time",
                1, NativeManager.requests);
    }

    @Test public void aHostWithNoResultsFieldStandsTheFeatureDownInsteadOfRetrying() {
        Anchor anchor = loadedAnchor("aid-no-field", "cid-no-field");
        CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        assertEquals(1, NativeManager.requests);

        CommentBatchTranslator.onNativeBatchComplete(new RunnerWithoutResults(anchor.comment));

        for (int bind = 0; bind < 5; bind++) {
            idleFor(31_000L);
            CommentBatchTranslator.registerCommentCell(new View(context), anchor);
        }
        assertEquals("a host with no results field was asked again anyway",
                1, NativeManager.requests);
    }

    /** Robolectric advances SystemClock.elapsedRealtime as the paused looper is idled. */
    private static void idleFor(long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
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
        Object lock = getTranslatorLock();
        Field pendingField = CommentBatchTranslator.class.getDeclaredField("pendingRequests");
        pendingField.setAccessible(true);
        synchronized (lock) {
            return ((Map<String, ?>) pendingField.get(null)).size();
        }
    }

    @SuppressWarnings("unchecked")
    private static int loadedBatchCount() throws Exception {
        Field field = CommentBatchTranslator.class.getDeclaredField("loadedBatches");
        field.setAccessible(true);
        synchronized (getTranslatorLock()) {
            return ((Map<String, ?>) field.get(null)).size();
        }
    }

    private static Object getTranslatorLock() throws Exception {
        Field lockField = CommentBatchTranslator.class.getDeclaredField("LOCK");
        lockField.setAccessible(true);
        return lockField.get(null);
    }

    @SuppressWarnings("unchecked")
    /**
     * The language service is looked up once per process, so a test that wants to watch the
     * lookup has to put the translator back to never having tried.
     */
    private static void forgetLanguageServiceLookup() throws Exception {
        for (String name : new String[]{"nativeLanguageService", "nativeTargetLanguageGetter",
                "nativeLanguageSettings", "nativeDoNotTranslateGetter"}) {
            Field field = CommentBatchTranslator.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, null);
        }
        for (String name : new String[]{"nativeTargetLanguageLookedUp",
                "nativeDoNotTranslateLookedUp"}) {
            Field field = CommentBatchTranslator.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(null, false);
        }
    }

    private static Object callPrivate(String name) throws Exception {
        java.lang.reflect.Method method =
                CommentBatchTranslator.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(null);
    }

    @Test public void theLanguageServiceIsBuiltOnceEvenWhenItCarriesNothingUseful()
            throws Exception {
        // The lookup used to be remembered only when it found something, so a build whose
        // service has no target language getter built the Keva-backed service again for every
        // comment, and did it holding the lock TikTok needs to hand a finished batch back.
        forgetLanguageServiceLookup();

        for (int call = 0; call < 100; call++) {
            callPrivate("getNativeTranslationTargetLanguage");
            callPrivate("getNativeDoNotTranslateLanguages");
        }

        assertEquals("the language service was built again after coming up empty", 2,
                com.ss.android.ugc.aweme.translation.service.TranslationLangKevaServiceImpl
                        .constructions);
    }

    @Test public void aMethodThatCannotHoldTheLanguageListIsNeverCalled() throws Exception {
        // Finding the do-not-translate list means calling a method on the host to get at it.
        // Only a method whose declared return type has the list is worth calling; anything else
        // is reaching into TikTok to see what happens.
        forgetLanguageServiceLookup();

        for (int call = 0; call < 100; call++) {
            callPrivate("getNativeDoNotTranslateLanguages");
        }

        assertEquals("the translator called a host method that cannot hold the list", 0,
                com.ss.android.ugc.aweme.translation.service.TranslationLangKevaServiceImpl
                        .strayCalls);
    }

    @Test public void anEmptyLanguageServiceStillLeavesTheDeviceLanguageInCharge()
            throws Exception {
        // The positive control for the two above: coming up empty has to mean falling back, not
        // failing. Without this they would both pass against a translator that gave up entirely.
        forgetLanguageServiceLookup();

        Object target = callPrivate("getNativeTranslationTargetLanguage");
        assertEquals(java.util.Locale.getDefault().toLanguageTag(), target);
        assertEquals(0, ((String[]) callPrivate("getNativeDoNotTranslateLanguages")).length);
    }

    private static void clearTranslatorState() throws Exception {
        Field lockField = CommentBatchTranslator.class.getDeclaredField("LOCK");
        lockField.setAccessible(true);
        Object lock = lockField.get(null);
        synchronized (lock) {
            for (String name : new String[]{"visibleComments", "loadedBatches",
                    "requestedLoadedBatchKeys", "pendingRequests", "retiredRequests",
                    "retryStates"}) {
                Field field = CommentBatchTranslator.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof Map) ((Map<?, ?>) value).clear();
                else ((java.util.Collection<?>) value).clear();
            }
            for (String name : new String[]{"outstandingRequests", "completionsHandledForTests"}) {
                Field counter = CommentBatchTranslator.class.getDeclaredField(name);
                counter.setAccessible(true);
                counter.set(null, 0);
            }
            Field latest = CommentBatchTranslator.class.getDeclaredField("latestLoadedBatch");
            latest.setAccessible(true);
            latest.set(null, null);
            Field manager = CommentBatchTranslator.class.getDeclaredField("lastManager");
            manager.setAccessible(true);
            manager.set(null, new WeakReference<>(null));
            Field generation = CommentBatchTranslator.class.getDeclaredField("nextRequestGeneration");
            generation.setAccessible(true);
            generation.setLong(null, 0L);
            Field disabled = CommentBatchTranslator.class.getDeclaredField("disabledForSession");
            disabled.setAccessible(true);
            disabled.setBoolean(null, false);
        }
    }

    public static final class Comment {
        private final String aid;
        private final String cid;
        boolean translated;

        Comment(String aid, String cid) {
            this.aid = aid;
            this.cid = cid;
        }

        public String getAid() { return aid; }
        public String getAwemeId() { return aid; }
        public String getCid() { return cid; }
        public boolean isTranslated() { return translated; }
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

        static List<String> lastRequestedCids = new ArrayList<>();

        public static void LJFF(List<Object> comments, TranslationContext context, boolean force) {
            List<String> cids = new ArrayList<>();
            for (Object comment : comments) cids.add(((Comment) comment).getCid());
            lastRequestedCids = cids;
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
            lastRequestedCids = new ArrayList<>();
            fail = false;
            blockFirst = false;
            failFirst = false;
            firstStarted = new CountDownLatch(1);
            releaseFirst = new CountDownLatch(1);
        }
    }

    public static final class Task {
        public final List<Comment> LIZ;

        Task(List<Comment> comments) {
            LIZ = new ArrayList<>(comments);
        }
    }

    public static final class Runner {
        public final Object l0;
        public final Task l1;

        Runner(Object results, Comment comment) {
            this(results, Arrays.asList(comment));
        }

        Runner(Object results, List<Comment> requested) {
            l0 = results;
            l1 = new Task(requested);
        }
    }

    /** A host build that renamed the field the results arrive in. */
    public static final class RunnerWithoutResults {
        public final Task l1;

        RunnerWithoutResults(Comment comment) {
            l1 = new Task(Arrays.asList(comment));
        }
    }
}
