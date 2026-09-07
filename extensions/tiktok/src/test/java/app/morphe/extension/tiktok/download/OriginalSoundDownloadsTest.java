package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The sound a video was made with is a different file from the sound the video makes. Someone
 * saving "that song" wants the first, and reading it means walking a model that is obfuscated,
 * differently shaped between builds, and often simply absent.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class OriginalSoundDownloadsTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    /** A sound entry with its own play list, which is the ordinary case. */
    @SuppressWarnings("unused")
    public static final class Music {
        private final Object playUrl;
        private final String title;

        Music(Object playUrl, String title) {
            this.playUrl = playUrl;
            this.title = title;
        }

        public Object getPlayUrl() { return playUrl; }
        public String getTitle() { return title; }
    }

    @SuppressWarnings("unused")
    public static final class PlayUrl {
        private final List<String> urlList;
        private final String uri;

        PlayUrl(List<String> urlList, String uri) {
            this.urlList = urlList;
            this.uri = uri;
        }

        public List<String> getUrlList() { return urlList; }
        public String getUri() { return uri; }
    }

    @SuppressWarnings("unused")
    public static final class Post {
        private final Object music;

        Post(Object music) { this.music = music; }

        public Object getMusic() { return music; }
        public String getAid() { return "7712345"; }
    }

    /** A post from a build that never had the model at all. */
    @SuppressWarnings("unused")
    public static final class BarePost {
        public String getAid() { return "7712345"; }
    }

    @Test public void everyMirrorIsOfferedInTheOrderTheModelGaveThem() {
        Post post = new Post(new Music(
                new PlayUrl(List.of("https://one.example/sound.m4a",
                        "https://two.example/sound.m4a"), null), "A song"));

        assertEquals(List.of("https://one.example/sound.m4a", "https://two.example/sound.m4a"),
                OriginalSoundDownloads.sourceUrls(post));
    }

    @Test public void aPostWithNoSoundModelAsksForNothing() {
        // The commonest case by far: an ordinary video with only its own audio.
        assertEquals(List.of(), OriginalSoundDownloads.sourceUrls(new BarePost()));
        assertEquals(List.of(), OriginalSoundDownloads.sourceUrls(new Post(null)));
        assertEquals(List.of(), OriginalSoundDownloads.sourceUrls(new Post(new Music(null, "A song"))));
    }

    @Test public void aSoundWithNoUrlsAsksForNothing() {
        // A sound entry with an empty list is a sound that cannot be fetched, and asking for it
        // would be a download of nothing followed by a failure nobody can act on.
        Post empty = new Post(new Music(new PlayUrl(List.of(), null), "A song"));
        assertEquals(List.of(), OriginalSoundDownloads.sourceUrls(empty));

        Post blanks = new Post(new Music(new PlayUrl(java.util.Arrays.asList("", "   "), null), "A song"));
        assertEquals(List.of(), OriginalSoundDownloads.sourceUrls(blanks));
    }

    @Test public void aBuildWithOnlyASingleUriStillWorks() {
        Post single = new Post(new Music(
                new PlayUrl(List.of(), "https://one.example/sound.m4a"), "A song"));

        assertEquals(List.of("https://one.example/sound.m4a"),
                OriginalSoundDownloads.sourceUrls(single));
    }

    @Test public void aUriThatIsNotAnAddressIsNotOffered() {
        // Some builds put an opaque id in that field rather than a URL, and fetching it would
        // be a request to nowhere.
        Post identifier = new Post(new Music(new PlayUrl(List.of(), "v09044g40000abc"), "A song"));

        assertEquals(List.of(), OriginalSoundDownloads.sourceUrls(identifier));
    }

    @Test public void theFileIsNamedAfterTheSound() {
        Post post = new Post(new Music(
                new PlayUrl(List.of("https://one.example/sound.m4a"), null),
                "Sunset Drive (Sped Up)"));

        String name = OriginalSoundDownloads.fileName(post);
        assertTrue(name, name.startsWith("Sunset"));
        assertTrue(name, name.endsWith(".m4a"));
        // The same sound saved from two different posts is one file, not two.
        assertEquals(name, OriginalSoundDownloads.fileName(
                new Post(new Music(new PlayUrl(List.of("https://other.example/s.m4a"), null),
                        "Sunset Drive (Sped Up)"))));
    }

    @Test public void aSoundWithATitleTooLongToBeAFilenameIsCutDown() {
        Post post = new Post(new Music(
                new PlayUrl(List.of("https://one.example/sound.m4a"), null), "a".repeat(400)));

        String name = OriginalSoundDownloads.fileName(post);
        assertTrue(name + " is " + name.length() + " characters", name.length() <= 165);
        assertTrue(name, name.endsWith(".m4a"));
    }

    @Test public void aSoundWithNoTitleFallsBackToThePostsOwnName() {
        Post post = new Post(new Music(
                new PlayUrl(List.of("https://one.example/sound.m4a"), null), ""));

        String name = OriginalSoundDownloads.fileName(post);
        assertTrue(name, name.endsWith(".m4a"));
        assertTrue("a nameless sound got no name at all: " + name, name.length() > 4);
    }
}
