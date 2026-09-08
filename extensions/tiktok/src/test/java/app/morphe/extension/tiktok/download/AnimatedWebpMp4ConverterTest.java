/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * MP4 is the sticker format that ships on by default, and the size it composes at comes out of
 * the file's own header rather than from anything measured.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AnimatedWebpMp4ConverterTest {
    private static final int RED = 0xFFFF0000;

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

}
