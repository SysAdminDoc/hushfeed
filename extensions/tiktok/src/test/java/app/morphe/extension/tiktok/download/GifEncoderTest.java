package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The GIF the sticker save writes has to be readable by something that is not the writer.
 * Android's own decoder reads the first frame here, which is Skia doing the work rather than
 * anything in this repository, and a reader written from the specification walks the rest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GifEncoderTest {

    @Test
    public void androidReadsTheFirstFrameBackPixelForPixel() {
        // Skia is the check here: it has no idea how this file was written.
        int width = 9, height = 6;
        int[] argb = new int[width * height];
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF, 0xFF123456};
        for (int at = 0; at < argb.length; at++) argb[at] = colors[at % colors.length];

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            GifEncoder.write(out, width, height, List.of(new GifEncoder.Frame(argb, 40)));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        byte[] gif = out.toByteArray();

        Bitmap decoded = BitmapFactory.decodeByteArray(gif, 0, gif.length);
        assertNotNull("Android could not read the GIF back", decoded);
        assertEquals(width, decoded.getWidth());
        assertEquals(height, decoded.getHeight());
        int[] pixels = new int[argb.length];
        decoded.getPixels(pixels, 0, width, 0, 0, width, height);
        assertArrayEquals(argb, pixels);
    }

    @Test
    public void everyPixelSurvivesTheRoundTripWhileTheColoursFit() throws Exception {
        // Four colours over three frames, with a transparent corner on each.
        int width = 7, height = 5;
        List<GifEncoder.Frame> frames = new ArrayList<>();
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};
        for (int index = 0; index < 3; index++) {
            int[] argb = new int[width * height];
            for (int at = 0; at < argb.length; at++) {
                argb[at] = colors[(at + index) % colors.length];
            }
            argb[0] = 0x00000000;
            argb[argb.length - 1] = 0x40FF00FF; // a quarter alpha reads as transparent too
            frames.add(new GifEncoder.Frame(argb, 40 + index * 10));
        }

        Gif gif = encodeAndRead(width, height, frames);
        assertEquals(3, gif.frames.size());
        assertEquals(List.of(4, 5, 6), gif.delays);
        assertTrue("loop block", gif.looping);

        for (int index = 0; index < frames.size(); index++) {
            int[] wanted = frames.get(index).argb;
            int[] decoded = gif.frames.get(index);
            for (int at = 0; at < wanted.length; at++) {
                if ((wanted[at] >>> 24) < 128) {
                    assertEquals("transparent at " + at + " of frame " + index,
                            gif.transparentIndex, decoded[at]);
                } else {
                    assertEquals("colour at " + at + " of frame " + index,
                            wanted[at] & 0xFFFFFF, gif.palette[decoded[at]]);
                }
            }
        }
    }

    @Test
    public void aSingleFrameIsWrittenWithoutTheLoopBlock() throws Exception {
        int[] argb = {0xFF102030, 0xFF102030, 0xFF405060, 0x00000000};
        Gif gif = encodeAndRead(2, 2, List.of(new GifEncoder.Frame(argb, 0)));
        assertEquals(1, gif.frames.size());
        // A zero delay still gets one tick, or a viewer runs the frame as fast as it likes.
        assertEquals(List.of(1), gif.delays);
        assertTrue("no loop block on a still", !gif.looping);
        assertEquals(0x102030, gif.palette[gif.frames.get(0)[0]]);
        assertEquals(gif.transparentIndex, gif.frames.get(0)[3]);
    }

    @Test
    public void tooManyColoursAreQuantisedRatherThanRefused() throws Exception {
        // 4096 distinct colours in one frame, well past what GIF can carry.
        int width = 64, height = 64;
        int[] argb = new int[width * height];
        for (int at = 0; at < argb.length; at++) {
            argb[at] = 0xFF000000 | ((at & 0xF) << 20) | (((at >> 4) & 0xF) << 12) | (((at >> 8) & 0xF) << 4);
        }
        Gif gif = encodeAndRead(width, height, List.of(new GifEncoder.Frame(argb, 100)));
        int[] decoded = gif.frames.get(0);

        Set<Integer> used = new HashSet<>();
        long worst = 0;
        for (int at = 0; at < argb.length; at++) {
            assertTrue("nothing here is transparent", decoded[at] != gif.transparentIndex);
            used.add(decoded[at]);
            int wanted = argb[at] & 0xFFFFFF, got = gif.palette[decoded[at]];
            long distance = 0;
            for (int shift = 0; shift <= 16; shift += 8) {
                long difference = ((wanted >> shift) & 0xFF) - ((got >> shift) & 0xFF);
                distance += difference * difference;
            }
            worst = Math.max(worst, distance);
        }
        assertTrue("colours used " + used.size(), used.size() <= 255);
        // Median cut over an even spread stays close; a broken palette lands far away.
        assertTrue("worst squared distance " + worst, worst < 4000);
    }

    @Test
    public void longRunsCrossTheCodeWidthBoundariesAndStillReadBack() throws Exception {
        // Noise over two colours fills the dictionary, so the writer has to widen its codes
        // several times and reset once it runs out. Getting that boundary wrong loses pixels.
        int width = 200, height = 200;
        Random random = new Random(20260906L);
        int[] argb = new int[width * height];
        for (int at = 0; at < argb.length; at++) {
            argb[at] = random.nextBoolean() ? 0xFF000000 : 0xFFFFFFFF;
        }
        Gif gif = encodeAndRead(width, height, List.of(new GifEncoder.Frame(argb, 30)));
        int[] decoded = gif.frames.get(0);
        int[] read = new int[argb.length];
        for (int at = 0; at < argb.length; at++) read[at] = 0xFF000000 | gif.palette[decoded[at]];
        assertArrayEquals(argb, read);
    }

    @Test
    public void aFrameThatDoesNotMatchTheSizeIsRefused() {
        assertThrows(IOException.class, () -> GifEncoder.write(new ByteArrayOutputStream(), 2, 2,
                List.of(new GifEncoder.Frame(new int[3], 40))));
        assertThrows(IOException.class, () -> GifEncoder.write(new ByteArrayOutputStream(), 2, 2, List.of()));
        assertThrows(IOException.class, () -> GifEncoder.write(new ByteArrayOutputStream(), 0, 2,
                List.of(new GifEncoder.Frame(new int[0], 40))));
    }

    // ------------------------------------------------------------------ a reader of our own

    private static Gif encodeAndRead(int width, int height, List<GifEncoder.Frame> frames) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GifEncoder.write(out, width, height, frames);
        return new Gif(out.toByteArray(), width, height);
    }

    /** A GIF89a reader written against the specification, not against the encoder. */
    private static final class Gif {
        final int[] palette;
        final int transparentIndex;
        final List<int[]> frames = new ArrayList<>();
        final List<Integer> delays = new ArrayList<>();
        boolean looping;

        Gif(byte[] data, int width, int height) {
            assertEquals("GIF89a", new String(data, 0, 6, StandardCharsets.US_ASCII));
            assertEquals("trailer", 0x3B, data[data.length - 1] & 0xFF);
            assertEquals(width, read16(data, 6));
            assertEquals(height, read16(data, 8));
            int packed = data[10] & 0xFF;
            assertTrue("global colour table", (packed & 0x80) != 0);
            int tableSize = 1 << ((packed & 0x07) + 1);
            palette = new int[tableSize];
            int at = 13;
            for (int index = 0; index < tableSize; index++) {
                palette[index] = ((data[at] & 0xFF) << 16) | ((data[at + 1] & 0xFF) << 8) | (data[at + 2] & 0xFF);
                at += 3;
            }

            int transparent = -1;
            while (true) {
                int block = data[at++] & 0xFF;
                if (block == 0x3B) break;
                if (block == 0x21) {
                    int label = data[at++] & 0xFF;
                    if (label == 0xF9) {
                        assertEquals(4, data[at] & 0xFF);
                        int flags = data[at + 1] & 0xFF;
                        delays.add(read16(data, at + 2));
                        assertTrue("transparency flag", (flags & 0x01) != 0);
                        assertEquals("dispose to background", 2, (flags >> 2) & 0x07);
                        transparent = data[at + 4] & 0xFF;
                        at += 5;
                        assertEquals(0, data[at++] & 0xFF);
                    } else if (label == 0xFF) {
                        int size = data[at++] & 0xFF;
                        looping |= "NETSCAPE2.0".equals(new String(data, at, size, StandardCharsets.US_ASCII));
                        at = skipSubBlocks(data, at + size);
                    } else {
                        at = skipSubBlocks(data, at + 1);
                    }
                } else if (block == 0x2C) {
                    assertEquals(0, read16(data, at));
                    assertEquals(0, read16(data, at + 2));
                    assertEquals(width, read16(data, at + 4));
                    assertEquals(height, read16(data, at + 6));
                    assertEquals("no local colour table", 0, data[at + 8] & 0x80);
                    at += 9;
                    int minCodeSize = data[at++] & 0xFF;
                    ByteArrayOutputStream lzw = new ByteArrayOutputStream();
                    while (true) {
                        int size = data[at++] & 0xFF;
                        if (size == 0) break;
                        lzw.write(data, at, size);
                        at += size;
                    }
                    frames.add(decode(lzw.toByteArray(), minCodeSize, width * height));
                } else {
                    throw new IllegalStateException("unknown block 0x" + Integer.toHexString(block));
                }
            }
            transparentIndex = transparent;
        }

        private static int skipSubBlocks(byte[] data, int at) {
            while (true) {
                int size = data[at++] & 0xFF;
                if (size == 0) return at;
                at += size;
            }
        }

        private static int read16(byte[] data, int at) {
            return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8);
        }

        /**
         * GIF's LZW, codes least significant bit first. The reader is one entry behind the
         * writer, because it only learns an entry once the code after it arrives, which is
         * exactly why the writer widens one code later than looks right.
         */
        private static int[] decode(byte[] lzw, int minCodeSize, int pixels) {
            int clear = 1 << minCodeSize, end = clear + 1;
            List<int[]> dictionary = new ArrayList<>();
            int codeSize = minCodeSize + 1;
            int[] out = new int[pixels];
            int written = 0, bitAt = 0;
            int[] previous = null;
            reset(dictionary, clear);

            while (true) {
                int code = 0;
                for (int bit = 0; bit < codeSize; bit++) {
                    int byteAt = (bitAt + bit) >> 3;
                    if (byteAt >= lzw.length) throw new IllegalStateException("ran out of LZW data");
                    code |= ((lzw[byteAt] >> ((bitAt + bit) & 7)) & 1) << bit;
                }
                bitAt += codeSize;

                if (code == clear) {
                    reset(dictionary, clear);
                    codeSize = minCodeSize + 1;
                    previous = null;
                    continue;
                }
                if (code == end) break;

                int[] entry;
                if (code < dictionary.size()) {
                    entry = dictionary.get(code);
                } else if (previous != null) {
                    entry = append(previous, previous[0]);
                } else {
                    throw new IllegalStateException("code " + code + " before anything defined it");
                }
                for (int value : entry) {
                    if (written == out.length) throw new IllegalStateException("more pixels than the frame holds");
                    out[written++] = value;
                }
                if (previous != null && dictionary.size() < 4096) {
                    dictionary.add(append(previous, entry[0]));
                    if (dictionary.size() >= (1 << codeSize) && codeSize < 12) codeSize++;
                }
                previous = entry;
            }
            assertEquals("pixels decoded", out.length, written);
            return out;
        }

        private static void reset(List<int[]> dictionary, int clear) {
            dictionary.clear();
            for (int index = 0; index < clear; index++) dictionary.add(new int[]{index});
            dictionary.add(new int[0]);
            dictionary.add(new int[0]);
        }

        private static int[] append(int[] head, int tail) {
            int[] result = new int[head.length + 1];
            System.arraycopy(head, 0, result, 0, head.length);
            result[head.length] = tail;
            return result;
        }
    }
}
