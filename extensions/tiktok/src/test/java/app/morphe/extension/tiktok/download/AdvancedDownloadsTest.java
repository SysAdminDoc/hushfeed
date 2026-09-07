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
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.After;
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
    @After public void tearDown() {
        SettingsStatus.advancedDownloadsEnabled = false;
    }
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
        public Gear(String name, int rate, String url) { gearName = name; bitRate = rate; playAddr = new Address(url, rate * 10L); }
    }
    public static final class VideoData {
        public final List<Gear> bitRate;
        public List<Audio> bitRateAudio = List.of();
        public boolean dash;
        public Address downloadNoWatermarkAddr, downloadAddr, playAddr;
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
    public static final class Author {
        private final String uniqueId;
        Author(String uniqueId) { this.uniqueId = uniqueId; }
        public String getUniqueId() { return uniqueId; }
        public String getNickname() { return "nickname"; }
    }
    /** A post with the fields the filename tokens are read from. */
    public static final class Item {
        private final Author author;
        private final String aid;
        Item(String creator, String aid) { this.author = new Author(creator); this.aid = aid; }
        public Author getAuthor() { return author; }
        public String getAid() { return aid; }
    }
    /** A profile, carrying only the avatar sizes a given test wants to offer. */
    public static final class Account {
        public Address avatar300, avatarLarger, avatarMedium, avatar168, avatarThumb;
        private final String uniqueId, nickname;
        Account(String uniqueId, String nickname) { this.uniqueId = uniqueId; this.nickname = nickname; }
        public String getUniqueId() { return uniqueId; }
        public String getNickname() { return nickname; }
    }
    /**
     * The profile fetch response, shaped like the patched one: the patch prepends the record
     * call to getUser itself, so a double with a plain getter would hide a reader that goes
     * back through the getter and never stops.
     */
    public static final class Profile {
        public final Account user;
        static int getUserCalls;
        Profile(Account user) { this.user = user; }
        public Account getUser() {
            getUserCalls++;
            ProfileAvatarSaver.recordProfileResponse(this);
            return user;
        }
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

    @Test public void soundIsCopiedIntoItsOwnContainerAndSilentVideoIsRejected() throws Exception {
        File media = File.createTempFile("sound-source", ".mp4"), output = File.createTempFile("sound-result", ".m4a");
        try {
            var source = org.robolectric.shadows.util.DataSource.toDataSource(media.getAbsolutePath());
            org.robolectric.shadows.ShadowMediaExtractor.addTrack(source,
                    android.media.MediaFormat.createVideoFormat("video/avc", 1080, 1920), new byte[]{1, 2, 3});
            // A video with no sound has nothing to save, and that has to say so rather than
            // leaving an empty file behind.
            assertThrows(java.io.IOException.class, () -> TrackMuxer.audioOnly(media, output));
            org.robolectric.shadows.ShadowMediaExtractor.addTrack(source,
                    android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2), new byte[]{7, 8, 9});
            TrackMuxer.audioOnly(media, output);
            // Only the sound is written: the picture track stays behind.
            assertArrayEquals(new byte[]{7, 8, 9}, Files.readAllBytes(output.toPath()));
        } finally { assertTrue(media.delete()); assertTrue(output.delete()); }
    }

    @Test public void thePictureIsCopiedOnItsOwnAndAPictureIsRequired() throws Exception {
        File media = File.createTempFile("muted-source", ".mp4"), output = File.createTempFile("muted-result", ".mp4");
        try {
            var source = org.robolectric.shadows.util.DataSource.toDataSource(media.getAbsolutePath());
            org.robolectric.shadows.ShadowMediaExtractor.addTrack(source,
                    android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2), new byte[]{7, 8, 9});
            // Sound and no picture is not a video, and that has to say so rather than leaving
            // an empty file behind.
            assertThrows(java.io.IOException.class, () -> TrackMuxer.videoOnly(media, output));
            org.robolectric.shadows.ShadowMediaExtractor.addTrack(source,
                    android.media.MediaFormat.createVideoFormat("video/avc", 1080, 1920), new byte[]{1, 2, 3});
            TrackMuxer.videoOnly(media, output);
            // Only the picture is written: the sound stays behind.
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(output.toPath()));
        } finally { assertTrue(media.delete()); assertTrue(output.delete()); }
    }

    @Test public void theSoundTakesTheVideoNameWithAnAudioExtension() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.DOWNLOAD_VIDEO_FILENAME_TEMPLATE.save("{creator}-{video_id}");
        Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.save("photo-{video_id}");
        // Distinct values, so swapping the two tokens fails rather than reading the same.
        Item item = new Item("dancer", "7712345");
        assertEquals("dancer-7712345.mp4", DownloadFilenameFormatter.formatSelectedVideoName(item));
        assertEquals("dancer-7712345.m4a", DownloadFilenameFormatter.formatSelectedAudioName(item));
    }

    /**
     * A creator name long enough to reach the length cap used to take the template's index with
     * it, so every photo of one slideshow resolved to the same taken name and the search for a
     * free one never advanced. The timeout is the point of the test: without the bound it hangs.
     */
    @Test(timeout = 30_000) public void aLongCreatorNameStillNamesEachSlideshowPhotoApart() throws IOException {
        Utils.setContext(RuntimeEnvironment.getApplication());
        String photoTemplate = Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.get();
        try {
            Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.save("{creator}_{index}");
            String creator = "a".repeat(200);
            File folder = Files.createTempDirectory("hushfeed-slideshow").toFile();
            Item post = new Item(creator, "7712345");

            String first = resolveSavedName(folder, "source_1.jpg", post);
            // The saver writes that file, so the next photo of the same post lands on it.
            Files.write(new File(folder, first).toPath(), new byte[]{1});
            String second = resolveSavedName(folder, "source_2.jpg", post);

            assertNotEquals("The second photo would overwrite the first", first, second);
            // Both keep the creator's name, so neither fell back to the source file's own name.
            assertTrue(first, first.startsWith("aaaa"));
            assertTrue(second, second.startsWith("aaaa"));
            assertTrue(first, first.endsWith(".jpg"));
            assertTrue(second, second.endsWith(".jpg"));
            // 160 characters of base name, an underscore, the counter and the extension.
            assertTrue(first + " is too long", first.length() <= 165);
            assertTrue(second + " is too long", second.length() <= 165);
        } finally {
            Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.save(photoTemplate);
        }
    }

    /** An ordinary name is short enough to keep the template's own shape. */
    @Test public void anOrdinaryCreatorNameKeepsTheTemplateShape() throws IOException {
        Utils.setContext(RuntimeEnvironment.getApplication());
        String photoTemplate = Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.get();
        try {
            Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.save("{creator}_{index}");
            File folder = Files.createTempDirectory("hushfeed-slideshow-short").toFile();
            Item post = new Item("dancer", "7712345");

            String first = resolveSavedName(folder, "source_1.jpg", post);
            assertEquals("dancer_1.jpg", first);
            Files.write(new File(folder, first).toPath(), new byte[]{1});
            assertEquals("dancer_2.jpg", resolveSavedName(folder, "source_2.jpg", post));
        } finally {
            Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.save(photoTemplate);
        }
    }

    /**
     * The saver names each image of a slideshow in turn, so the number it is given has to survive
     * the length cap. A creator name long enough to reach that cap used to take the number with
     * it and every photo of the post came out with one name.
     */
    @Test public void everySlideshowPhotoKeepsItsOwnNumberWhenTheCreatorNameIsLong() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        String photoTemplate = Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.get();
        try {
            Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.save("{creator}_{index}");
            Item post = new Item("a".repeat(200), "7712345");

            // Past ten, so the number changing width is covered too.
            Set<String> names = new LinkedHashSet<>();
            for (int index = 1; index <= 12; index++) {
                names.add(DownloadFilenameFormatter.formatOriginalPhotoName(post, index, "jpg"));
            }

            assertEquals("Photos of one slideshow shared a name: " + names, 12, names.size());
            for (String name : names) {
                assertTrue(name, name.startsWith("aaaa") && name.endsWith(".jpg"));
            }
        } finally {
            Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.save(photoTemplate);
        }
    }

    /**
     * Once every name the template can produce is taken the search gives up and keeps the file's
     * own name, rather than counting upwards for as long as the process lives.
     */
    @Test(timeout = 60_000) public void anExhaustedNameSearchKeepsTheOriginalName() throws IOException {
        Utils.setContext(RuntimeEnvironment.getApplication());
        String photoTemplate = Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.get();
        try {
            Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.save("{creator}_{index}");
            File folder = Files.createTempDirectory("hushfeed-slideshow-full").toFile();
            for (int index = 1; index <= 999; index++) {
                Files.write(new File(folder, "dancer_" + index + ".jpg").toPath(), new byte[]{1});
            }

            assertEquals("source_1.jpg", resolveSavedName(folder, "source_1.jpg", new Item("dancer", "7712345")));
        } finally {
            Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.save(photoTemplate);
        }
    }

    /** Runs the real registration path and reports the name the saver would publish. */
    private static String resolveSavedName(File folder, String sourceName, Item post) throws IOException {
        File source = new File(folder, sourceName);
        Files.write(source.toPath(), new byte[]{1});
        DownloadFilenameFormatter.registerDownloadedMediaName(source.getPath(), post);
        return DownloadFilenameFormatter.consumeDestinationName(source.getName());
    }

    @Test public void soundGoesToTheAudioTreeOnlyWhereTheGalleryDemandsIt() {
        // Android 10 and later refuse an audio file in the video collection, so the folder
        // name is mirrored under Music. Older versions write real files side by side.
        int sdk = android.os.Build.VERSION.SDK_INT;
        try {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION.class, "SDK_INT", 28);
            assertEquals("Movies/TikTok", AudioDownloads.audioPath("Movies/TikTok"));
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION.class, "SDK_INT", 29);
            assertEquals("Music/TikTok", AudioDownloads.audioPath("Movies/TikTok"));
            assertEquals("Music/TikTok/Sounds", AudioDownloads.audioPath("Download/TikTok/Sounds"));
            // A destination with no folder of its own still gets one.
            assertEquals("Music/TikTok", AudioDownloads.audioPath("Movies"));
        } finally {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION.class, "SDK_INT", sdk);
        }
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
            assertEquals("png", RemoteMedia.fetch(List.of("https://[bad", base + "/bad", base + "/photo"), temp, true));
            response.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertArrayEquals(png, Files.readAllBytes(temp.toPath()));
            MediaFileWriter.publish(RuntimeEnvironment.getApplication(), temp, "source.png", "image/png", "DCIM/OriginalPhotosTest", false);
            File saved = new File(Environment.getExternalStorageDirectory(), "DCIM/OriginalPhotosTest/source.png");
            assertArrayEquals(png, Files.readAllBytes(saved.toPath()));
            assertTrue(saved.delete());
        } finally { server.close(); responder.join(1000); assertTrue(temp.delete()); }
    }

    @Test public void allMalformedMediaMirrorsReturnOneRedactedFailure() throws Exception {
        File temp = File.createTempFile("media-failure", ".tmp");
        try {
            IOException failure = assertThrows(IOException.class,
                    () -> RemoteMedia.fetch(List.of("https://[bad?token=secret"), temp, true));
            assertFalse(failure.toString().contains("token=secret"));
            assertFalse(failure.toString().contains("https://[bad"));
            assertFalse("an all-invalid fetch must not leave a partial target", temp.exists());
        } finally {
            temp.delete();
        }
    }

    @Test public void theStickerFormatChoicePicksTheContainerAndFallsBackToTheSource() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        // The default is what shipped before the choice existed.
        assertTrue(StickerGallerySaver.MediaFormat.animated("mp4").convertToMp4);
        assertEquals("mp4", StickerGallerySaver.MediaFormat.animated("mp4").extension);

        var gif = StickerGallerySaver.MediaFormat.animated("gif");
        assertTrue(gif.convertToGif);
        assertFalse(gif.convertToMp4);
        assertFalse("a GIF is not a video to the gallery", gif.video);
        assertEquals("image/gif", gif.mimeType);

        // WebP is the sticker exactly as it arrived, so nothing converts it.
        var webp = StickerGallerySaver.MediaFormat.animated("webp");
        assertFalse(webp.convertToGif);
        assertFalse(webp.convertToMp4);
        assertFalse(webp.convertToPng);
        assertEquals("image/webp", webp.mimeType);
        assertEquals("webp", webp.extension);

        // An unknown or missing choice keeps the old behaviour rather than refusing to save.
        assertTrue(StickerGallerySaver.MediaFormat.animated("avif").convertToMp4);
        assertTrue(StickerGallerySaver.MediaFormat.animated(null).convertToMp4);

        // The fallback a failed conversion writes is that same untouched WebP.
        assertEquals("WebP", StickerGallerySaver.MediaFormat.webp().label);
        assertEquals("image/webp", StickerGallerySaver.MediaFormat.webp().mimeType);
    }

    @Test public void aStoryIsFetchedFromTheCleanestAddressItHas() {
        // A story has no save button, so it is saved from whatever address the video carries,
        // preferring the one without a watermark burnt into it.
        VideoData video = new VideoData(List.of());
        video.downloadNoWatermarkAddr = new Address("https://example.com/clean.mp4", 900);
        video.downloadAddr = new Address("https://example.com/watermarked.mp4", 900);
        video.playAddr = new Address("https://example.com/play.mp4", 900);
        assertEquals(List.of("https://example.com/clean.mp4"), VideoDownloads.sourceUrls(video));
        video.downloadNoWatermarkAddr = null;
        assertEquals(List.of("https://example.com/watermarked.mp4"), VideoDownloads.sourceUrls(video));
        video.downloadAddr = new Address(null, 0);
        assertEquals(List.of("https://example.com/play.mp4"), VideoDownloads.sourceUrls(video));
        video.playAddr = null;
        assertTrue(VideoDownloads.sourceUrls(video).isEmpty());
        assertTrue(VideoDownloads.sourceUrls(new Object()).isEmpty());
    }

    @Test public void eachStoryStaysWithThePlayAreaThatBoundIt() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.advancedDownloadsEnabled = true;
            Settings.SAVE_STORY.save(true);

            // The viewer binds the pages either side of the one on screen, so two play areas are
            // live at once and each has to keep its own story.
            Object showing = new Object(), neighbour = new Object();
            android.view.View onScreen = new android.view.View(activity);
            android.view.View offScreen = new android.view.View(activity);
            StoryDownloads.attachPlayArea(showing, onScreen);
            StoryDownloads.attachPlayArea(neighbour, offScreen);

            Post first = new Post(List.of(new Photo("https://example.com/one")));
            Post second = new Post(List.of(new Photo("https://example.com/two")));
            StoryDownloads.recordStory(showing, 3, first);
            StoryDownloads.recordStory(neighbour, 4, second);
            // Binding the neighbour last must not change what the story on screen is.
            assertSame(first, StoryDownloads.storyFor(onScreen));
            assertSame(second, StoryDownloads.storyFor(offScreen));

            // A bind with nothing in it leaves the story that play area is showing alone.
            StoryDownloads.recordStory(showing, 3, null);
            StoryDownloads.recordStory(null, 3, second);
            assertSame(first, StoryDownloads.storyFor(onScreen));
            // A view no play area claimed has no story, rather than somebody else's.
            assertNull(StoryDownloads.storyFor(new android.view.View(activity)));
        } finally {
            Settings.SAVE_STORY.save(false);
        }
    }

    @Test public void theStoryGestureIsOnlyTakenWhenItIsAskedFor() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.advancedDownloadsEnabled = true;

            // Off: TikTok's own press and hold is left exactly as it was.
            Settings.SAVE_STORY.save(false);
            android.view.View view = new android.view.View(activity);
            boolean[] tikTokSawIt = {false};
            view.setOnLongClickListener(anchor -> tikTokSawIt[0] = true);
            StoryDownloads.attachPlayArea(new Object(), view);
            assertTrue(view.performLongClick());
            assertTrue("TikTok's own listener still runs", tikTokSawIt[0]);

            // On: the press is ours, and with no story bound it says so rather than saving.
            Settings.SAVE_STORY.save(true);
            Object component = new Object();
            android.view.View ours = new android.view.View(activity);
            StoryDownloads.attachPlayArea(component, ours);
            org.robolectric.shadows.ShadowToast.reset();
            assertTrue(ours.performLongClick());
            assertEquals(1, org.robolectric.shadows.ShadowToast.shownToastCount());
        } finally {
            Settings.SAVE_STORY.save(false);
        }
    }

    @Test public void theProfilePictureTakesTheLargestSizeThatIsThere() {
        Account account = new Account("dancer", "Dancer");
        account.avatarThumb = new Address("https://example.com/thumb.jpg", 10);
        account.avatar168 = new Address("https://example.com/168.jpg", 20);
        account.avatarMedium = new Address("https://example.com/medium.jpg", 30);
        account.avatarLarger = new Address("https://example.com/larger.jpg", 40);
        account.avatar300 = new Address("https://example.com/300.jpg", 50);
        assertEquals(List.of("https://example.com/300.jpg"), ProfileAvatarSaver.avatarUrls(account));

        // Each size steps down only when the one above it is missing, never past it.
        account.avatar300 = null;
        assertEquals(List.of("https://example.com/larger.jpg"), ProfileAvatarSaver.avatarUrls(account));
        account.avatarLarger = new Address(null, 0);
        assertEquals(List.of("https://example.com/medium.jpg"), ProfileAvatarSaver.avatarUrls(account));
        account.avatarMedium = null;
        assertEquals(List.of("https://example.com/168.jpg"), ProfileAvatarSaver.avatarUrls(account));
        account.avatar168 = null;
        assertEquals(List.of("https://example.com/thumb.jpg"), ProfileAvatarSaver.avatarUrls(account));
        account.avatarThumb = null;
        assertTrue(ProfileAvatarSaver.avatarUrls(account).isEmpty());
        assertTrue(ProfileAvatarSaver.avatarUrls(new Object()).isEmpty());
    }

    @Test public void theProfilePictureIsNamedAfterTheAccount() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        assertEquals("dancer_profile.jpg", ProfileAvatarSaver.avatarName(new Account("dancer", "Dancer")));
        // No handle falls back to the display name.
        assertEquals("Dancer_profile.jpg", ProfileAvatarSaver.avatarName(new Account("  ", "Dancer")));
        // Path separators are replaced rather than dropped, so the name stays legal and unique.
        assertEquals("____profile.jpg", ProfileAvatarSaver.avatarName(new Account(null, "///")));
        // Nothing to go on at all is the only case that falls back to a fixed name.
        assertEquals("profile_profile.jpg", ProfileAvatarSaver.avatarName(new Object()));
    }

    @Test public void theRecordedProfileIsTheOneTheResponseCarries() {
        Account account = new Account("dancer", "Dancer");
        account.avatar300 = new Address("https://example.com/300.jpg", 50);
        Profile.getUserCalls = 0;
        ProfileAvatarSaver.recordProfileResponse(new Profile(account));
        assertSame(account, ProfileAvatarSaver.recordedProfileUser());
        // Reading the field, not the getter: the patch put this call inside getUser, so one
        // trip through the getter would be a stack overflow that the reflection helper eats.
        assertEquals("getUser calls", 0, Profile.getUserCalls);

        // A response with no user leaves the last profile alone rather than blanking it.
        ProfileAvatarSaver.recordProfileResponse(new Profile(null));
        ProfileAvatarSaver.recordProfileResponse(new Object());
        ProfileAvatarSaver.recordProfileResponse(null);
        assertSame(account, ProfileAvatarSaver.recordedProfileUser());
        assertEquals("getUser calls", 0, Profile.getUserCalls);
    }

    @Test public void thePictureIsRefusedWhenTheHandleOnScreenIsSomebodyElse() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            android.widget.LinearLayout header = new android.widget.LinearLayout(activity);
            android.widget.TextView handle = new android.widget.TextView(activity);
            android.view.View avatar = new android.view.View(activity);
            header.addView(handle);
            header.addView(avatar);

            Account onScreen = new Account("dancer", "Dancer");
            Account somebodyElse = new Account("singer", "Singer");
            handle.setText("@dancer");
            assertTrue(ProfileAvatarSaver.matchesProfileOnScreen(avatar, onScreen));
            assertFalse(ProfileAvatarSaver.matchesProfileOnScreen(avatar, somebodyElse));

            // A header with no handle on it cannot tell either way, so it does not refuse.
            handle.setText("Dancer");
            assertTrue(ProfileAvatarSaver.matchesProfileOnScreen(avatar, somebodyElse));
            // Neither can a profile with no handle of its own.
            handle.setText("@dancer");
            assertTrue(ProfileAvatarSaver.matchesProfileOnScreen(avatar, new Account(" ", null)));
        }
    }

    @Test public void aVeryLongCreatorNameStaysInsideWhatTheFilesystemTakes() {
        // 160 Japanese characters is 480 bytes of UTF-8, well past the 255 a filesystem takes,
        // and cutting at a fixed index can land between the halves of a surrogate pair.
        String longHandle = "\u3042".repeat(200);
        String withEmoji = "\uD83C\uDF0D".repeat(120);

        for (String handle : new String[]{longHandle, withEmoji}) {
            String name = DownloadFilenameFormatter.formatProfilePictureName(handle);
            assertTrue("the name must fit a filesystem: " + name.length() + " bytes",
                    name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 255);
            for (int index = 0; index < name.length(); index++) {
                assertFalse("a surrogate was cut in half",
                        Character.isHighSurrogate(name.charAt(index))
                                && (index + 1 == name.length()
                                    || !Character.isLowSurrogate(name.charAt(index + 1))));
            }
        }

        // An ordinary handle is untouched.
        assertEquals("dancer_profile.jpg",
                DownloadFilenameFormatter.formatProfilePictureName("dancer"));
    }

    @Test public void theGestureGoesBackWhenTheSwitchDoes() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.advancedDownloadsEnabled = true;

            // In the activity's own tree: a long press with no listener left asks the parent
            // for a context menu, and a view with no parent has nothing to ask.
            android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
            activity.setContentView(root);

            for (boolean story : new boolean[]{false, true}) {
                android.view.View view = new android.view.View(activity);
                root.addView(view);
                // TikTok's own gesture. Holding a story is how the app pauses it, so a switch
                // that is off must leave this exactly where it is.
                boolean[] theirsRan = {false};
                view.setOnLongClickListener(anchor -> {
                    theirsRan[0] = true;
                    return true;
                });

                Settings.SAVE_PROFILE_PICTURE.save(false);
                Settings.SAVE_STORY.save(false);
                attach(story, view);
                assertTrue("a view we never took still answers to TikTok", view.performLongClick());
                assertTrue(theirsRan[0]);

                // Switched on, the press becomes ours.
                Settings.SAVE_PROFILE_PICTURE.save(true);
                Settings.SAVE_STORY.save(true);
                attach(story, view);

                // Switched off again, the next bind hands the view back empty. Answering false
                // from a listener that is still attached would not: TikTok's own is long gone
                // by then, and only removing ours lets the app install its own again.
                Settings.SAVE_PROFILE_PICTURE.save(false);
                Settings.SAVE_STORY.save(false);
                attach(story, view);
                theirsRan[0] = false;
                assertFalse("ours was taken away rather than left answering false",
                        view.performLongClick());
                assertFalse(theirsRan[0]);
            }
        } finally {
            Settings.SAVE_PROFILE_PICTURE.save(false);
            Settings.SAVE_STORY.save(false);
        }
    }

    private static void attach(boolean story, android.view.View view) {
        if (story) StoryDownloads.attachPlayArea(new Object(), view);
        else ProfileAvatarSaver.attachAvatar(view);
    }

    @Test public void advancedPatchAloneShowsItsOwnOptions() throws Exception {
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
            assertNotNull(screen.findPreference("download_audio_track"));
            assertNull(screen.findPreference("down_watermark"));
            assertEquals(Settings.DOWNLOAD_PHOTO_PATH.get(), screen.findPreference("download_photo_path").getSummary());
            activity.setPreferenceScreen(screen);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "download-settings.png");
        }
    }
}
