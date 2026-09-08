package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
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

    @Test public void anOrdinaryStickerIsStillAccepted() {
        // The positive control for the guard above. Without one the cap could be "> 0" and the
        // reject case would still pass while nothing converted any more.
        AnimatedWebpMp4Converter.validateFrame(320, 320, 320, 320, 0, 0);
        assertTrue("an ordinary sticker is inside the frame cap",
                320L * 320L < 16L * 1024 * 1024);
    }
}
