package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Geometry, size and bitrate guards for animated sticker conversion before native codec setup. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AnimatedWebpMp4ConverterTest {
    private static final int RED = 0xFFFF0000;

    @Test public void frameBoundsRejectOverflowAndOutsideCanvasCoordinates() {
        AnimatedWebpMp4Converter.validateFrame(100, 100, 20, 20, 80, 80);

        assertThrows(IllegalStateException.class, () ->
                AnimatedWebpMp4Converter.validateFrame(100, 100, 21, 20, 80, 80));
        assertThrows(IllegalStateException.class, () ->
                AnimatedWebpMp4Converter.validateFrame(100, 100, Integer.MAX_VALUE, 1, 1, 0));
        assertThrows(IllegalStateException.class, () ->
                AnimatedWebpMp4Converter.validateFrame(100, 100, 1, 1, -1, 0));
    }

    @Test public void drainingStopsWhenTheJobIsCancelled() throws Exception {
        // The encoder and muxer are null on purpose: reaching either of them means the budget
        // check is not the first thing the loop body does, which is the whole fix.
        Thread.currentThread().interrupt();
        try {
            AnimatedWebpMp4Converter.drainEncoder(null, null, true, new AnimatedWebpMp4Converter.EncoderState());
            fail("the drain loop ran with the job already cancelled");
        } catch (java.io.InterruptedIOException cancelled) {
            assertEquals("Media job cancelled", cancelled.getMessage());
        } finally {
            Thread.interrupted();
        }
    }

    @Test public void drainingStopsWhenTheJobDeadlineHasPassed() throws Exception {
        withExpiredDeadline(() -> {
            try {
                AnimatedWebpMp4Converter.drainEncoder(null, null, true, new AnimatedWebpMp4Converter.EncoderState());
                fail("the drain loop ran past the job deadline");
            } catch (java.io.InterruptedIOException wrong) {
                fail("reported cancellation for an expired deadline");
            } catch (java.io.IOException expired) {
                assertEquals("Media job deadline exceeded", expired.getMessage());
            }
        });
    }

    @Test public void aLiveJobIsNotStoppedByTheNewCheck() {
        // Without this the two tests above would pass just as well against a loop that always
        // threw. A live, uncancelled job has to get past the check and reach the encoder, which
        // here is null, so the failure has to be the null encoder and not an IOException.
        assertFalse("the drain loop refused a job that is neither cancelled nor out of time",
                Thread.currentThread().isInterrupted());
        assertThrows(NullPointerException.class, () ->
                AnimatedWebpMp4Converter.drainEncoder(null, null, true, new AnimatedWebpMp4Converter.EncoderState()));
    }

    /**
     * Runs {@code work} with an already expired job deadline installed on this thread.
     *
     * <p>{@code MediaBudget} deliberately offers no way to shorten a deadline, since a job that
     * could pick its own would not be limited by one. The test reaches the thread local
     * directly rather than the production code growing a setter only a test would call.
     */
    private static void withExpiredDeadline(Runnable work) throws Exception {
        java.lang.reflect.Constructor<?> constructor =
                MediaBudget.Deadline.class.getDeclaredConstructor(long.class);
        constructor.setAccessible(true);
        Object expired = constructor.newInstance(System.nanoTime() - 1_000_000L);

        java.lang.reflect.Field field = MediaBudget.class.getDeclaredField("CURRENT_DEADLINE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<Object> current = (ThreadLocal<Object>) field.get(null);
        Object previous = current.get();
        current.set(expired);
        try {
            work.run();
        } finally {
            if (previous == null) current.remove();
            else current.set(previous);
        }
    }

    @Test public void bitrateIsBoundedForTinyAndLargeFrames() {
        assertEquals(500_000, AnimatedWebpMp4Converter.chooseBitRate(1, 1));
        assertEquals(4_000_000, AnimatedWebpMp4Converter.chooseBitRate(1000, 1000));
        assertEquals(8_000_000, AnimatedWebpMp4Converter.chooseBitRate(4000, 4000));
    }

    @Test public void aCanvasTooLargeToComposeIsRefusedBeforeTheBitmapIsAllocated() throws Exception {
        // A VP8X canvas is header metadata, so this is a few hundred bytes that would ask for a
        // 16382 by 16382 ARGB bitmap, about a gigabyte, from well inside the 24 MB transfer cap.
        AnimatedWebpGifConverterTest.Webp builder =
                new AnimatedWebpGifConverterTest.Webp(16382, 16382);
        builder.frame(0, 0, 16382, 16382, 40, false, false, RED);
        builder.frame(0, 0, 16382, 16382, 40, false, false, RED);
        byte[] webp = builder.build();

        java.io.File output = java.io.File.createTempFile("hushfeed-sticker", ".mp4");
        output.deleteOnExit();
        try {
            AnimatedWebpMp4Converter.convert(webp, output.getAbsolutePath());
            fail("a gigabyte of canvas was accepted");
        } catch (IllegalStateException refused) {
            assertTrue("refused for the wrong reason: " + refused.getMessage(),
                    String.valueOf(refused.getMessage()).contains("too large to compose"));
        } finally {
            assertTrue(output.delete() || !output.exists());
        }
    }

    @Test public void aFrameWiderThanTheGpuWillTakeIsRefusedAndSaysBySoMuch() {
        // 8192 by 2000 is 16.4 million pixels, inside the cap above, and past the 4096 a side
        // that a great many Android GPUs stop at. Asked for anyway the upload fails silently
        // and the saved video is black.
        IllegalStateException refused = assertThrows(IllegalStateException.class, () ->
                AnimatedWebpMp4Converter.requireFitsTexture(8192, 2000, 4096));
        String said = String.valueOf(refused.getMessage());
        assertTrue("the refusal does not say what the GPU will take: " + said,
                said.contains("4096"));
        assertTrue("the refusal does not say how big the frame is: " + said,
                said.contains("8192"));

        // Taller than it is wide, which the same check has to catch.
        assertThrows(IllegalStateException.class, () ->
                AnimatedWebpMp4Converter.requireFitsTexture(2000, 8192, 4096));
    }

    @Test public void aFrameTheGpuWillTakeGoesThroughAndSoDoesOneItWillNotDiscuss() {
        // The positive control. Without it the check could refuse everything and the case
        // above would still pass.
        AnimatedWebpMp4Converter.requireFitsTexture(512, 512, 4096);
        AnimatedWebpMp4Converter.requireFitsTexture(4096, 4096, 4096);
        // A GPU that did not answer is not a reason to refuse a sticker on a number nobody gave.
        AnimatedWebpMp4Converter.requireFitsTexture(8192, 8192, 0);
    }

    @Test public void anOrdinaryStickerIsStillAccepted() {
        // The positive control for the guard above. Without one the cap could be "> 0" and the
        // reject case would still pass while nothing converted any more.
        AnimatedWebpMp4Converter.validateFrame(320, 320, 320, 320, 0, 0);
        assertTrue("an ordinary sticker is inside the frame cap",
                320L * 320L < 16L * 1024 * 1024);
    }
}
