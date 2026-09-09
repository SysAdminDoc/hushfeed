package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import app.morphe.extension.tiktok.SettingsContextRule;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowMediaExtractor;
import org.robolectric.shadows.ShadowMediaMuxer;
import org.robolectric.shadows.util.DataSource;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = {TrackMuxerTest.SampleExtractor.class, TrackMuxerTest.RecordingMuxer.class})
public class TrackMuxerTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();
    @Rule public final TemporaryFolder files = new TemporaryFolder();

    private static final byte[] SOURCE = {70, 71, 72};
    private static final byte[] VIDEO = {11, 12, 13, 14, 21, 22, 31, 32, 33, 34, 35};
    private static final int[] SAMPLE_SIZES = {4, 2, 5};
    private static final long[] SAMPLE_TIMES = {33_333, 99_999, 155_555};
    private static final int[] SAMPLE_FLAGS = {MediaExtractor.SAMPLE_FLAG_SYNC, 0, MediaExtractor.SAMPLE_FLAG_SYNC};

    @Before public void resetRecording() {
        SampleExtractor.last = null;
        RecordingMuxer.resetRecording();
    }

    @Test public void videoOnlyPreservesRotationBeforeStartingAndCopiesOnlyVideoSamples() throws Exception {
        for (int rotation : new int[]{90, 180, 270}) assertVideoOnly(rotation);
    }

    @Test public void videoOnlyAcceptsZeroAndAbsentRotationWithoutChangingSamples() throws Exception {
        assertVideoOnly(0);
        assertVideoOnly(null);
    }

    @Test public void cancelledVideoOnlyLeavesBothFilesAndOpensNoMedia() throws Exception {
        File source = sourceWithTracks(90), output = files.newFile();
        byte[] existingOutput = {81, 82};
        Files.write(output.toPath(), existingOutput);
        try {
            Thread.currentThread().interrupt();
            InterruptedIOException failure = assertThrows(InterruptedIOException.class,
                    () -> TrackMuxer.videoOnly(source, output));
            assertEquals("Media job cancelled", failure.getMessage());
        } finally {
            Thread.interrupted();
        }
        assertNull(SampleExtractor.last);
        assertTrue(RecordingMuxer.events.isEmpty());
        assertArrayEquals(SOURCE, Files.readAllBytes(source.toPath()));
        assertArrayEquals(existingOutput, Files.readAllBytes(output.toPath()));
        assertTrue(source.delete());
        assertTrue(output.delete());
    }

    private File sourceWithTracks(Integer rotation) throws IOException {
        resetRecording();
        File source = files.newFile();
        Files.write(source.toPath(), SOURCE);
        DataSource dataSource = DataSource.toDataSource(source.getAbsolutePath());
        // Audio comes first so selecting track zero would copy the wrong media.
        ShadowMediaExtractor.addTrack(dataSource,
                MediaFormat.createAudioFormat("audio/mp4a-latm", 44_100, 2), new byte[]{91, 92, 93});
        MediaFormat video = MediaFormat.createVideoFormat("video/avc", 1080, 1920);
        if (rotation != null) video.setInteger("rotation-degrees", rotation);
        ShadowMediaExtractor.addTrack(dataSource, video, VIDEO);
        return source;
    }

    private void assertVideoOnly(Integer rotation) throws Exception {
        File source = sourceWithTracks(rotation), output = files.newFile();
        TrackMuxer.videoOnly(source, output);

        assertEquals(rotation == null ? List.of() : List.of(rotation), RecordingMuxer.rotations);
        assertEquals(rotation == null ? 0 : rotation.intValue(), RecordingMuxer.rotationAtStart);
        assertEquals(MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, RecordingMuxer.outputFormat);
        assertEquals(List.of(1), SampleExtractor.last.selectedTracks);
        assertEquals(1, RecordingMuxer.formats.size());
        Map<String, Object> video = RecordingMuxer.formats.get(0);
        assertEquals("video/avc", video.get(MediaFormat.KEY_MIME));
        assertEquals(1080, video.get(MediaFormat.KEY_WIDTH));
        assertEquals(1920, video.get(MediaFormat.KEY_HEIGHT));

        assertEquals(3, RecordingMuxer.samples.size());
        byte[][] payloads = {{11, 12, 13, 14}, {21, 22}, {31, 32, 33, 34, 35}};
        long[] times = {33_333, 99_999, 155_555};
        int[] flags = {MediaCodec.BUFFER_FLAG_KEY_FRAME, 0, MediaCodec.BUFFER_FLAG_KEY_FRAME};
        for (int i = 0; i < payloads.length; i++) {
            Sample sample = RecordingMuxer.samples.get(i);
            assertEquals(0, sample.track);
            assertArrayEquals(payloads[i], sample.payload);
            assertEquals(times[i], sample.timeUs);
            assertEquals(flags[i], sample.flags);
        }
        // The stock shadow writes compressed payloads directly, not a playable MP4 container.
        assertArrayEquals(VIDEO, Files.readAllBytes(output.toPath()));
        assertArrayEquals(SOURCE, Files.readAllBytes(source.toPath()));
        List<String> expectedEvents = new ArrayList<>(List.of("setup", "add"));
        if (rotation != null) expectedEvents.add("orientation");
        expectedEvents.addAll(List.of("start", "write", "write", "write", "stop", "release"));
        assertEquals(expectedEvents, RecordingMuxer.events);
        assertTrue(SampleExtractor.last.released);
        assertTrue("the input is still owned by its caller", source.delete());
        assertTrue("the completed output descriptor is closed", output.delete());
    }

    /** Adds sample boundaries and metadata to Robolectric's real track/payload fixture support. */
    @Implements(MediaExtractor.class)
    public static class SampleExtractor extends ShadowMediaExtractor {
        static SampleExtractor last;
        final List<Integer> selectedTracks = new ArrayList<>();
        private int sampleIndex;
        private int selectedTrack = -1;
        boolean released;

        @Implementation @Override protected void setDataSource(String path) {
            super.setDataSource(path);
            last = this;
        }

        @Implementation @Override protected void selectTrack(int track) {
            super.selectTrack(track);
            selectedTracks.add(track);
            selectedTrack = track;
        }

        @Implementation protected long getSampleTime() {
            if (selectedTrack < 0 || sampleIndex >= (selectedTrack == 1 ? SAMPLE_TIMES.length : 1)) return -1;
            return selectedTrack == 1 ? SAMPLE_TIMES[sampleIndex] : 17_000;
        }

        @Implementation protected int getSampleFlags() {
            return selectedTrack == 1 ? SAMPLE_FLAGS[sampleIndex] : 0;
        }

        @Implementation protected long getSampleSize() {
            return selectedTrack == 1 ? SAMPLE_SIZES[sampleIndex] : 3;
        }

        @Implementation @Override protected int readSampleData(ByteBuffer buffer, int offset) {
            ByteBuffer sample = buffer.duplicate();
            sample.position(offset);
            sample.limit(offset + (int) getSampleSize());
            return super.readSampleData(sample.slice(), 0);
        }

        @Implementation @Override protected boolean advance() {
            boolean available = super.advance();
            sampleIndex++;
            return available && getSampleTime() >= 0;
        }

        @Implementation protected void release() { released = true; }
    }

    private static final class Sample {
        final int track, flags;
        final long timeUs;
        final byte[] payload;

        Sample(int track, int flags, long timeUs, byte[] payload) {
            this.track = track;
            this.flags = flags;
            this.timeUs = timeUs;
            this.payload = payload;
        }
    }

    /** Records native muxer calls while retaining the stock shadow's file writes and close. */
    @Implements(MediaMuxer.class)
    public static class RecordingMuxer extends ShadowMediaMuxer {
        static final List<String> events = new ArrayList<>();
        static final List<Integer> rotations = new ArrayList<>();
        static final List<Map<String, Object>> formats = new ArrayList<>();
        static final List<Sample> samples = new ArrayList<>();
        static int rotationAtStart, outputFormat;

        static void resetRecording() {
            events.clear();
            rotations.clear();
            formats.clear();
            samples.clear();
            rotationAtStart = 0;
            outputFormat = -1;
        }

        @Implementation protected static long nativeSetup(FileDescriptor file, int format) throws IOException {
            events.add("setup");
            outputFormat = format;
            return ShadowMediaMuxer.nativeSetup(file, format);
        }

        @Implementation protected static int nativeAddTrack(long muxer, String[] keys, Object[] values) {
            events.add("add");
            Map<String, Object> format = new HashMap<>();
            for (int i = 0; i < keys.length; i++) format.put(keys[i], values[i]);
            formats.add(format);
            return ShadowMediaMuxer.nativeAddTrack(muxer, keys, values);
        }

        @Implementation protected static void nativeSetOrientationHint(long muxer, int degrees) {
            assertFalse("rotation must be supplied before muxing starts", events.contains("start"));
            events.add("orientation");
            rotations.add(degrees);
        }

        @Implementation protected static void nativeStart(long muxer) {
            events.add("start");
            rotationAtStart = rotations.isEmpty() ? 0 : rotations.get(rotations.size() - 1);
        }

        @Implementation protected static void nativeWriteSampleData(long muxer, int track, ByteBuffer buffer,
                int offset, int size, long timeUs, int flags) {
            assertTrue(events.contains("start"));
            events.add("write");
            byte[] payload = new byte[size];
            ByteBuffer copy = buffer.duplicate();
            copy.position(offset);
            copy.get(payload);
            samples.add(new Sample(track, flags, timeUs, payload));
            ShadowMediaMuxer.nativeWriteSampleData(muxer, track, buffer, offset, size, timeUs, flags);
        }

        @Implementation protected static void nativeStop(long muxer) {
            ShadowMediaMuxer.nativeStop(muxer);
            events.add("stop");
        }

        @Implementation protected static void nativeRelease(long muxer) { events.add("release"); }
    }
}
