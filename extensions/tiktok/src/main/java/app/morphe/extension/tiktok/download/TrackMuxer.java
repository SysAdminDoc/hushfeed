package app.morphe.extension.tiktok.download;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Copies compressed samples and original timestamps; it does not re-encode either track. */
final class TrackMuxer {
    private TrackMuxer() {}

    static void combine(File video, File audio, File output) throws IOException {
        MediaBudget.check(null);
        MediaBudget.checkDiskSpace(output.getParentFile(), video.length() + audio.length());
        MediaExtractor picture = new MediaExtractor(), sound = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            picture.setDataSource(video.getAbsolutePath());
            sound.setDataSource(audio.getAbsolutePath());
            MediaFormat videoFormat = select(picture, "video/"), audioFormat = select(sound, "audio/");
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoTrack = muxer.addTrack(videoFormat), audioTrack = muxer.addTrack(audioFormat);
            if (videoFormat.containsKey("rotation-degrees")) muxer.setOrientationHint(videoFormat.getInteger("rotation-degrees"));
            muxer.start();
            copy(picture, muxer, videoTrack);
            copy(sound, muxer, audioTrack);
            muxer.stop();
            MediaBudget.check(null);
        } finally {
            picture.release();
            sound.release();
            if (muxer != null) muxer.release();
        }
        if (output.length() == 0) throw new IOException("Video muxer wrote an empty file");
    }

    /** Copies just the sound into its own MP4 container, which is what an .m4a is. */
    static void audioOnly(File source, File output) throws IOException {
        MediaBudget.check(null);
        MediaBudget.checkDiskSpace(output.getParentFile(), source.length());
        MediaExtractor sound = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            sound.setDataSource(source.getAbsolutePath());
            MediaFormat audioFormat = select(sound, "audio/");
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int audioTrack = muxer.addTrack(audioFormat);
            muxer.start();
            copy(sound, muxer, audioTrack);
            muxer.stop();
            MediaBudget.check(null);
        } finally {
            sound.release();
            if (muxer != null) muxer.release();
        }
        if (output.length() == 0) throw new IOException("Audio muxer wrote an empty file");
    }

    /** Copies just the picture, which is a download of a video with the sound left out. */
    static void videoOnly(File source, File output) throws IOException {
        MediaBudget.check(null);
        MediaBudget.checkDiskSpace(output.getParentFile(), source.length());
        MediaExtractor picture = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            picture.setDataSource(source.getAbsolutePath());
            MediaFormat videoFormat = select(picture, "video/");
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoTrack = muxer.addTrack(videoFormat);
            if (videoFormat.containsKey("rotation-degrees")) {
                muxer.setOrientationHint(videoFormat.getInteger("rotation-degrees"));
            }
            muxer.start();
            copy(picture, muxer, videoTrack);
            muxer.stop();
            MediaBudget.check(null);
        } finally {
            picture.release();
            if (muxer != null) muxer.release();
        }
        if (output.length() == 0) throw new IOException("Video muxer wrote an empty file");
    }

    private static MediaFormat select(MediaExtractor extractor, String prefix) throws IOException {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) {
                extractor.selectTrack(i);
                return format;
            }
        }
        throw new IOException("Download is missing its " + prefix + " track");
    }

    /** Past this a sample is not a frame any more, and a direct buffer that size is its own problem. */
    private static final int MAX_SAMPLE_BYTES = 64 * 1024 * 1024;

    private static void copy(MediaExtractor extractor, MediaMuxer muxer, int track) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int samples = 0;
        while (extractor.getSampleTime() >= 0) {
            MediaBudget.check(null);
            if ((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) throw new IOException("Encrypted media cannot be saved");
            // From API 28 the extractor will say how big the sample is, so the buffer is sized
            // once. Below that it will not, and readSampleData rejects a buffer the sample does
            // not fit in, which threw IllegalArgumentException out of a method declaring
            // IOException. The retry covers those releases and grows one step at a time so a
            // single oversized sample cannot reserve every size on the way up.
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                long size = extractor.getSampleSize();
                if (size > MAX_SAMPLE_BYTES) throw new IOException("A media sample is too large to copy");
                if (size > buffer.capacity()) buffer = ByteBuffer.allocateDirect((int) size);
            }

            int count;
            while (true) {
                buffer.clear();
                try {
                    count = extractor.readSampleData(buffer, 0);
                    break;
                } catch (IllegalArgumentException tooSmall) {
                    int grown = buffer.capacity() * 2;
                    if (grown > MAX_SAMPLE_BYTES) {
                        throw new IOException("A media sample is too large to copy", tooSmall);
                    }
                    buffer = ByteBuffer.allocateDirect(grown);
                }
            }
            if (count < 0) break;
            info.set(0, count, extractor.getSampleTime(),
                    (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
            muxer.writeSampleData(track, buffer, info);
            samples++;
            if (!extractor.advance()) break;
        }
        if (samples == 0) throw new IOException("Download contains no media samples");
    }
}
