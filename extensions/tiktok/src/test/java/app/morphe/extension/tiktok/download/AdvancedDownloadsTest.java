package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;
import android.os.Environment;
import android.os.Looper;
import android.preference.PreferenceActivity;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.DownloadsPreferenceCategory;
import com.ss.android.ugc.aweme.base.model.UrlModel;
import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AdvancedDownloadsTest {
    public static final class Address extends UrlModel {
        private final String url;
        private final long size;
        Address(String url, long size) { this.url = url; this.size = size; }
        @Override public List<String> getUrlList() { return url == null ? List.of() : List.of(url); }
        @Override public long getSize() { return size; }
    }
    public static final class Gear {
        public final String gearName;
        public final int bitRate;
        public final UrlModel playAddr;
        public String videoExtra;
        Gear(String name, int rate, String url) { gearName = name; bitRate = rate; playAddr = new Address(url, rate * 10L); }
    }
    public static final class VideoData {
        public final List<Gear> bitRate;
        public List<Audio> bitRateAudio = List.of();
        public boolean dash;
        VideoData(List<Gear> gears) { bitRate = gears; }
        public List<Gear> getBitRate() { throw new AssertionError("Must not recurse into playback getter"); }
        public boolean hasDashBitrate() { return dash; }
    }
    public static final class Audio {
        public final AudioMeta audioMeta;
        Audio(String id, int rate) { audioMeta = new AudioMeta(id, rate); }
    }
    public static final class AudioMeta {
        public final String fileId;
        public final long bitrate;
        public final AudioUrls urlList;
        AudioMeta(String id, int rate) { fileId = id; bitrate = rate; urlList = new AudioUrls(id); }
    }
    public static final class AudioUrls {
        public final String mainUrl, backupUrl, fallbackUrl;
        AudioUrls(String id) { mainUrl = "https://example.com/" + id; backupUrl = mainUrl + "/backup"; fallbackUrl = mainUrl + "/fallback"; }
    }
    public static final class Photo {
        public final Address displayImageNoWatermark;
        public final Address thumbnail = new Address("https://example.com/thumb", 50);
        Photo(String url) { displayImageNoWatermark = new Address(url, 100); }
    }
    public static final class Info {
        public List<Photo> imageList;
        Info(List<Photo> photos) { imageList = photos; }
    }
    public static final class Post {
        public final Info photoModeImageInfo;
        Post(List<Photo> photos) { photoModeImageInfo = new Info(photos); }
    }
    public static final class TestActivity extends PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }

    @Test public void qualityUsesValidVideoGearsAndKeepsDownloadSeparateFromPlayback() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Gear low = new Gear("normal_360_0", 100, "https://example.com/low");
        Gear medium = new Gear("normal_720_0", 200, "https://example.com/mid");
        Gear high = new Gear("normal_1080_0", 400, "https://example.com/high");
        Gear missing = new Gear("normal_2160_0", 900, null);
        List<Gear> gears = List.of(medium, missing, high, low);
        assertSame(high, QualitySelector.choose(gears, "highest"));
        assertSame(low, QualitySelector.choose(gears, "lowest"));
        assertSame(medium, QualitySelector.choose(gears, "720"));
        assertSame(low, QualitySelector.choose(gears, "540"));
        assertSame(medium, QualitySelector.choose(List.of(high, medium), "360"));
        assertNull(QualitySelector.choose(gears, "auto"));
        assertNull(QualitySelector.choose(gears, "typo"));
        Settings.DOWNLOAD_VIDEO_QUALITY.save("highest");
        assertSame(high.playAddr, QualitySelector.download(new VideoData(gears)));
        assertEquals(4, gears.size());
    }

    @Test public void photosUseOrderedSourceImagesAndNeverThumbnails() {
        Post post = new Post(List.of(new Photo("https://example.com/one"), new Photo("https://example.com/two")));
        assertEquals(List.of(List.of("https://example.com/one"), List.of("https://example.com/two")), OriginalPhotos.sources(post));
        post.photoModeImageInfo.imageList = List.of(new Photo(null));
        assertTrue(OriginalPhotos.sources(post).isEmpty());
        assertTrue(OriginalPhotos.sources(new Object()).isEmpty());
    }

    @Test public void adaptiveDownloadsPairTheRequestedAudioAndNeverReturnSilentVideoUrl() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.DOWNLOAD_VIDEO_QUALITY.save("highest");
        Gear gear = new Gear("1080p", 400, "https://example.com/video");
        gear.videoExtra = "{\"audio_file_id\":\"matching\"}";
        VideoData video = new VideoData(List.of(gear));
        video.dash = true;
        video.bitRateAudio = List.of(new Audio("unrelated", 200), new Audio("matching", 100));
        assertEquals(List.of("https://example.com/matching", "https://example.com/matching/backup", "https://example.com/matching/fallback"), VideoDownloads.audioUrls(video, gear));
        assertNull(QualitySelector.download(video));
        gear.videoExtra = "{\"audio_file_id\":\"missing\"}";
        assertTrue(VideoDownloads.audioUrls(video, gear).isEmpty());
        gear.videoExtra = null;
        assertEquals("https://example.com/unrelated", VideoDownloads.audioUrls(video, gear).get(0));
    }

    @Test public void muxerCopiesBothCompressedTracksAndRejectsMissingAudio() throws Exception {
        File video = File.createTempFile("mux-video", ".mp4"), audio = File.createTempFile("mux-audio", ".mp4"), output = File.createTempFile("mux-result", ".mp4");
        try {
            var videoSource = org.robolectric.shadows.util.DataSource.toDataSource(video.getAbsolutePath());
            var audioSource = org.robolectric.shadows.util.DataSource.toDataSource(audio.getAbsolutePath());
            org.robolectric.shadows.ShadowMediaExtractor.addTrack(videoSource,
                    android.media.MediaFormat.createVideoFormat("video/avc", 1080, 1920), new byte[]{1, 2, 3});
            assertThrows(java.io.IOException.class, () -> TrackMuxer.combine(video, audio, output));
            org.robolectric.shadows.ShadowMediaExtractor.addTrack(audioSource,
                    android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2), new byte[]{4, 5, 6});
            TrackMuxer.combine(video, audio, output);
            // Robolectric's muxer writes sample payloads directly; real container playback remains a device check.
            assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, Files.readAllBytes(output.toPath()));
        } finally { assertTrue(video.delete()); assertTrue(audio.delete()); assertTrue(output.delete()); }
    }

    @Test public void failedMirrorFallsBackAndGalleryGetsExactOriginalBytes() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");
        ServerSocket server = new ServerSocket(0, 2, java.net.InetAddress.getByName("127.0.0.1"));
        var response = new java.util.concurrent.FutureTask<Void>(() -> {
            for (int i = 0; i < 2; i++) {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                    boolean bad = input.readLine().contains("/bad");
                    String line;
                    while ((line = input.readLine()) != null && !line.isEmpty()) { }
                    var output = socket.getOutputStream();
                    String header = "HTTP/1.1 " + (bad ? "403 Forbidden" : "200 OK")
                            + "\r\nConnection: close\r\nContent-Length: " + (bad ? 0 : png.length) + "\r\n\r\n";
                    output.write(header.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    if (!bad) output.write(png);
                }
            }
            return null;
        });
        Thread responder = new Thread(response);
        responder.setDaemon(true);
        responder.start();
        File temp = File.createTempFile("photo-test", ".tmp");
        try {
            String base = "http://127.0.0.1:" + server.getLocalPort();
            assertEquals("png", RemoteMedia.fetch(List.of(base + "/bad", base + "/photo"), temp, true));
            response.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertArrayEquals(png, Files.readAllBytes(temp.toPath()));
            MediaFileWriter.publish(RuntimeEnvironment.getApplication(), temp, "source.png", "image/png", "DCIM/OriginalPhotosTest", false);
            File saved = new File(Environment.getExternalStorageDirectory(), "DCIM/OriginalPhotosTest/source.png");
            assertArrayEquals(png, Files.readAllBytes(saved.toPath()));
            assertTrue(saved.delete());
        } finally { server.close(); responder.join(1000); assertTrue(temp.delete()); }
    }

    @Test public void advancedPatchAloneShowsBothOptions() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            Utils.setIsDarkModeEnabled(true);
            SettingsStatus.advancedDownloadsEnabled = true;
            Settings.DOWNLOAD_VIDEO_QUALITY.save("auto");
            var screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new DownloadsPreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("download_video_quality"));
            assertNotNull(screen.findPreference("download_original_photos"));
            assertNull(screen.findPreference("down_watermark"));
            assertEquals(Settings.DOWNLOAD_PHOTO_PATH.get(), screen.findPreference("download_photo_path").getSummary());
            activity.setPreferenceScreen(screen);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "download-settings.png");
        }
    }
}
