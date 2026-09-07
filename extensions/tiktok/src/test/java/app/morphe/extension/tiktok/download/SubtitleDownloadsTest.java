package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.DownloadsPreferenceCategory;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SubtitleDownloadsTest {
    @After public void tearDown() {
        SettingsStatus.subtitleToolsEnabled = false;
        SettingsStatus.advancedDownloadsEnabled = false;
    }
    public static final class Track {
        public String languageCode, languageName, format, url;
        public boolean isOriginalCaption;
        public List<String> urlList = List.of();
        Track(String lang, String format, boolean original) {
            this.languageCode = lang; this.format = format; this.isOriginalCaption = original; url = "https://example.com/" + lang + "." + format;
        }
    }
    public static final class Model { public List<Track> captionList; Model(Track... tracks) { captionList = List.of(tracks); } }
    public static final class Video { public Model captionModel; Video(Track... tracks) { captionModel = new Model(tracks); } }

    @Test public void convertsSrtVttAndJsonWithUnicodeAndRejectsBrokenTiming() throws Exception {
        String expected = "1\n00:00:01,250 --> 00:00:02,500\nHallo \uD83C\uDF0D\n\n";
        assertEquals(expected, SubtitleFormat.toSrt("WEBVTT\n\nNOTE comment\nIgnore me\n\ncue\n00:01.250 --> 00:02.500 align:center\n<v Voice>Hallo \uD83C\uDF0D</v>\n", "webvtt"));
        assertEquals(expected, SubtitleFormat.toSrt(expected, "srt"));
        assertEquals(expected, SubtitleFormat.toSrt("{\"utterances\":[{\"start_time\":1250,\"end_time\":2500,\"text\":\"Hallo \uD83C\uDF0D\"}]}", "creator_caption"));
        assertThrows(IOException.class, () -> SubtitleFormat.toSrt("1\n00:00:05,000 --> 00:00:02,000\nBackwards", "srt"));
        assertThrows(IOException.class, () -> SubtitleFormat.toSrt("<html>Expired URL</html>", "vtt"));
    }
    @Test public void aCaptionNamingNoLanguageDoesNotSinkTheSave() {
        // The language arrives as free text and is cleaned to letters, digits and dashes, so
        // "_" comes out as a lone dash: not empty, so it used to skip the "und" fallback, and
        // Java gives no parts at all when it splits a string that is only separators.
        Video video = new Video(new Track("_", "srt", false), new Track("en", "srt", true));
        var device = SubtitleDownloads.tracks(video, "device", Locale.US);
        assertEquals(1, device.size());
        assertEquals("en", device.get(0).language);

        // On its own it still saves, under the name the app uses for an unknown language.
        var alone = SubtitleDownloads.tracks(new Video(new Track("_", "srt", false)), "device", Locale.US);
        assertEquals(1, alone.size());
        assertEquals("und", alone.get(0).language);
    }

    @Test public void selectsLanguageAndDeduplicatesFormatsWithoutMutatingMetadata() {
        Video video = new Video(new Track("fr", "webvtt", true), new Track("de-DE", "creator_caption", false), new Track("de-DE", "srt", false));
        assertEquals("fr", SubtitleDownloads.tracks(video, "original", Locale.GERMANY).get(0).language);
        var device = SubtitleDownloads.tracks(video, "device", Locale.GERMANY);
        assertEquals("de-de", device.get(0).language);
        assertEquals("srt", device.get(0).format);
        assertEquals(2, SubtitleDownloads.tracks(video, "all", Locale.US).size());
        assertEquals(3, video.captionModel.captionList.size());
        Track[] many = new Track[25];
        for (int i = 0; i < many.length; i++) many[i] = new Track("lang" + i, "srt", i == 0);
        assertEquals(25, SubtitleDownloads.tracks(new Video(many), "all", Locale.US).size());
    }
    @Test public void actualSaveWritesSubtitleBesideCollisionRenamedVideo() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        String srt = "1\n00:00:00,500 --> 00:00:02,000\nSaved caption\n\n";
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            var response = new java.util.concurrent.FutureTask<Void>(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                    String line;
                    while ((line = input.readLine()) != null && !line.isEmpty()) { }
                    byte[] data = srt.getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: " + data.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(data);
                }
                return null;
            });
            Thread thread = new Thread(response);
            thread.setDaemon(true);
            thread.start();
            File source = File.createTempFile("saved-video", ".mp4", context.getCacheDir());
            Files.write(source.toPath(), new byte[]{1, 2, 3});
            String path = "DCIM/SubtitleDownloadTest";
            String first = MediaFileWriter.publish(context, source, "video.mp4", "video/mp4", path, true);
            String actual = MediaFileWriter.publish(context, source, "video.mp4", "video/mp4", path, true);
            assertEquals("video_2.mp4", actual);
            var track = new SubtitleDownloads.Track("en", "srt", List.of("http://127.0.0.1:" + server.getLocalPort() + "/captions"), true);
            assertEquals(1, SubtitleDownloads.save(context, List.of(track), actual, path));
            response.get(5, java.util.concurrent.TimeUnit.SECONDS);
            File directory = new File(Environment.getExternalStorageDirectory(), path);
            File captions = new File(directory, "video_2.en.srt");
            assertEquals(srt, new String(Files.readAllBytes(captions.toPath()), StandardCharsets.UTF_8));
            assertTrue(captions.delete());
            assertTrue(new File(directory, first).delete());
            assertTrue(new File(directory, actual).delete());
            assertTrue(source.delete());
        }
    }

    @Test public void languageIdentitySurvivesUnicodeAndFilenameSanitization() {
        Track japanese = new Track(null, "vtt", true);
        japanese.languageName = "日本語";
        japanese.url = "https://example.com/japanese.vtt";
        Track japaneseSrt = new Track(null, "srt", false);
        japaneseSrt.languageName = "日本語";
        japaneseSrt.url = "https://example.com/japanese.srt";
        Track korean = new Track(null, "srt", false);
        korean.languageName = "한국어";
        korean.url = "https://example.com/korean.srt";
        Track punctuation = new Track(null, "srt", false);
        punctuation.languageName = "A.B";
        Track plain = new Track(null, "srt", false);
        plain.languageName = "AB";
        Track unknownOne = new Track(null, "srt", false);
        unknownOne.url = "https://example.com/unknown-one.srt";
        Track unknownTwo = new Track(null, "srt", false);
        unknownTwo.url = "https://example.com/unknown-two.srt";
        Video video = new Video(japanese, korean, japaneseSrt, punctuation, plain, unknownOne, unknownTwo);
        var all = SubtitleDownloads.tracks(video, "all", Locale.US);
        assertEquals(6, all.size());
        assertEquals(6, all.stream().map(track -> track.language).distinct().count());
        assertEquals("srt", all.get(0).format);
        assertEquals("https://example.com/japanese.srt", all.get(0).urls.get(0));
        assertTrue(all.get(0).original);
        assertEquals(all.get(0).language, SubtitleDownloads.tracks(video, "original", Locale.US).get(0).language);
    }
    public static class Provider extends ContentProvider {
        final List<ContentValues> inserted = new ArrayList<>();
        final List<Uri> collections = new ArrayList<>();
        final List<File> files = new ArrayList<>();
        boolean rejectPublish;
        int deleted;
        @Override public boolean onCreate() { return true; }
        @Override public Uri insert(Uri uri, ContentValues values) {
            inserted.add(new ContentValues(values)); collections.add(uri);
            try { files.add(File.createTempFile("published", ".tmp", getContext().getCacheDir())); }
            catch (IOException error) { throw new IllegalStateException(error); }
            return Uri.withAppendedPath(uri, String.valueOf(files.size()));
        }
        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
            return ParcelFileDescriptor.open(files.get(Integer.parseInt(uri.getLastPathSegment()) - 1), ParcelFileDescriptor.MODE_READ_WRITE);
        }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
            MatrixCursor cursor = new MatrixCursor(new String[]{MediaStore.MediaColumns.DISPLAY_NAME});
            cursor.addRow(new Object[]{"provider-renamed.mp4"}); return cursor;
        }
        @Override public String getType(Uri uri) { return "video/mp4"; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return rejectPublish ? 0 : 1; }
        @Override public int delete(Uri uri, String selection, String[] args) { deleted++; return 1; }
    }
    @Test @Config(sdk = 30) public void scopedStorageUsesSubtitleCollectionAndProviderAssignedNameAndCleansFailures() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        Provider provider = new Provider();
        var info = new android.content.pm.ProviderInfo();
        info.authority = "media";
        provider.attachInfo(context, info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media", provider);
        File source = File.createTempFile("subtitle", ".srt", context.getCacheDir());
        Files.write(source.toPath(), new byte[]{1, 2, 3});
        try {
            assertEquals("Movies/TikTok", SubtitleDownloads.pairedPath("DCIM/TikTok"));
            assertEquals("provider-renamed.mp4", MediaFileWriter.publish(context, source, "original.mp4", "video/mp4", "Movies/TikTok", true));
            MediaFileWriter.publish(context, source, "provider-renamed.en.srt", "application/x-subrip", "Movies/TikTok", false);
            assertEquals(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), provider.collections.get(1));
            assertEquals("Movies/TikTok", provider.inserted.get(1).getAsString(MediaStore.MediaColumns.RELATIVE_PATH));
            provider.rejectPublish = true;
            assertThrows(IOException.class, () -> MediaFileWriter.publish(context, source, "failed.srt", "application/x-subrip", "Movies/TikTok", false));
            assertEquals(1, provider.deleted);
        } finally {
            assertTrue(source.delete());
            for (File file : provider.files) assertTrue(file.delete());
        }
    }
    @Test public void downloadControlsAreReachable() throws Exception {
        try (var owner = Robolectric.buildActivity(AdvancedDownloadsTest.TestActivity.class).setup()) {
            var activity = owner.get();
            Utils.setContext(activity);
            Utils.setIsDarkModeEnabled(true);
            SettingsStatus.subtitleToolsEnabled = SettingsStatus.advancedDownloadsEnabled = true;
            var screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new DownloadsPreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("download_subtitles"));
            assertNotNull(screen.findPreference("subtitle_language"));
            activity.setPreferenceScreen(screen);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "subtitle-download-settings.png");
        }
    }
}
