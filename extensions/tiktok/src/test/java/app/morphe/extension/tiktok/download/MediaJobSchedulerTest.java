package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Saving media runs on a pool with a hard cap on what may run and what may wait. Without one a
 * long press held down a few times too often would start a download per press. Nothing tested
 * the cap, so a queue quietly resized would have shown up as a phone running out of memory.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class MediaJobSchedulerTest {
    private final List<CountDownLatch> released = new ArrayList<>();

    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        // The pool is process-wide, so an earlier test's jobs would eat this one's capacity.
        drain();
    }

    @After public void tearDown() throws Exception {
        for (CountDownLatch latch : released) latch.countDown();
        released.clear();
        MediaJobScheduler.cancelAll();
        drain();
    }

    private static void drain() throws Exception {
        for (int wait = 0; wait < 250; wait++) {
            if (MediaJobScheduler.runningJobs() == 0 && MediaJobScheduler.queuedJobs() == 0) return;
            Thread.sleep(20);
        }
        throw new IllegalStateException("the media pool never emptied: running="
                + MediaJobScheduler.runningJobs() + " queued=" + MediaJobScheduler.queuedJobs());
    }

    /**
     * Offers more work than the pool can hold and counts what it takes. The pool belongs to the
     * process and every test shares it, and a job finishing frees a slot at any moment, so how
     * deep the queue is at a given instant is not something to assert on. What holds either way
     * is that the pool stops saying yes somewhere at or below its cap, and that everything it did
     * say yes to runs.
     */
    @Test public void theQueueStopsAcceptingAtItsCap() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        released.add(hold);
        AtomicInteger ran = new AtomicInteger();

        int capacity = MediaJobScheduler.MAX_RUNNING_JOBS + MediaJobScheduler.MAX_QUEUED_JOBS;
        int accepted = 0;
        boolean refused = false;
        for (int job = 0; job <= capacity; job++) {
            MediaJobScheduler.JobHandle handle = MediaJobScheduler.submit("job-" + job, () -> {
                ran.incrementAndGet();
                try {
                    hold.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, null);
            if (handle == null) {
                refused = true;
                break;
            }
            accepted++;
        }

        assertTrue("the pool refused the very first job", accepted > 0);
        assertTrue("the pool took " + accepted + " jobs, past its cap of " + capacity,
                accepted <= capacity);
        assertTrue("the pool never refused anything, so nothing caps it", refused);

        hold.countDown();
        int expected = accepted;
        for (int wait = 0; wait < 250 && ran.get() < expected; wait++) Thread.sleep(20);
        assertEquals("a job the pool accepted never ran", expected, ran.get());
    }

    @Test public void cancellingAQueuedJobTellsItsOwner() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        released.add(hold);
        AtomicInteger cancelled = new AtomicInteger();

        List<MediaJobScheduler.JobHandle> handles = new ArrayList<>();
        for (int job = 0; job < MediaJobScheduler.MAX_RUNNING_JOBS + 1; job++) {
            handles.add(MediaJobScheduler.submit("job-" + job, () -> {
                try {
                    hold.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, cancelled::incrementAndGet));
        }

        // The last one is still waiting, so cancelling it runs the queued-cancel callback.
        MediaJobScheduler.JobHandle waiting = handles.get(handles.size() - 1);
        assertNotNull(waiting);
        assertTrue("a waiting job refused to cancel", waiting.cancel());

        hold.countDown();
        for (int wait = 0; wait < 100 && cancelled.get() == 0; wait++) Thread.sleep(20);
        assertEquals("the owner was not told its queued job went away", 1, cancelled.get());
    }
}
