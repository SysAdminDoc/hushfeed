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
        } finally {
            picture.release();
            sound.release();
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

    private static void copy(MediaExtractor extractor, MediaMuxer muxer, int track) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int samples = 0;
        while (extractor.getSampleTime() >= 0) {
            if ((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) throw new IOException("Encrypted media cannot be saved");
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                long size = extractor.getSampleSize();
                if (size > 64 * 1024 * 1024) throw new IOException("Video sample is too large");
                if (size > buffer.capacity()) buffer = ByteBuffer.allocateDirect((int) size);
            }
            buffer.clear();
            int count = extractor.readSampleData(buffer, 0);
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
