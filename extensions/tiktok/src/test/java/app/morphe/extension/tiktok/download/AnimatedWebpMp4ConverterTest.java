package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** Geometry and bitrate guards for animated sticker conversion before native codec setup. */
public class AnimatedWebpMp4ConverterTest {
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
}
