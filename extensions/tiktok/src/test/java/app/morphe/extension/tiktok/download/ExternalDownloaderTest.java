package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.DownloadsPreferenceCategory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Where the save button sends a video when another app is meant to have it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ExternalDownloaderTest {
    public static final class TestActivity extends PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }

    /** A video that knows its own link, which is what TikTok's model gives. */
    public static final class Shared {
        public final String shareUrl;
        Shared(String url) { shareUrl = url; }
    }

    public static final class Author {
        public final String uniqueId;
        Author(String handle) { uniqueId = handle; }
    }

    /** A video with no link of its own, only a handle and an id to build one from. */
    public static final class Unshared {
        public final Author author;
        public final String aid;
        Unshared(String handle, String id) { author = new Author(handle); aid = id; }
    }

    @Test public void onlyARealPackageNameCountsAsAnApp() {
        try {
            for (String typed : new String[]{"com.dv.adm", "  com.dv.adm  ", "a.b", "com.a_b.c9"}) {
                Settings.EXTERNAL_DOWNLOADER_PACKAGE.save(typed);
                assertEquals(typed.trim(), ExternalDownloader.packageName());
            }
            // Anything that is not a package name would go somewhere unintended, or nowhere.
            for (String typed : new String[]{"", "   ", "adm", "com.dv.adm/x", "com dv adm",
                    "com..adm", ".com.adm", "com.adm.", "1com.adm", "com.adm;rm"}) {
                Settings.EXTERNAL_DOWNLOADER_PACKAGE.save(typed);
                assertEquals("", ExternalDownloader.packageName());
            }
        } finally {
            Settings.EXTERNAL_DOWNLOADER_PACKAGE.save("");
        }
    }

    @Test public void theLinkIsTikToksOwnOrOneBuiltFromTheHandle() {
        assertEquals("https://www.tiktok.com/@dancer/video/7712345",
                ExternalDownloader.shareUrl(new Shared("https://www.tiktok.com/@dancer/video/7712345")));
        // No link of its own: the handle and the id say the same thing.
        assertEquals("https://www.tiktok.com/@dancer/video/7712345",
                ExternalDownloader.shareUrl(new Unshared("dancer", "7712345")));
        // Nothing to build one from is reported rather than guessed at.
        assertNull(ExternalDownloader.shareUrl(new Unshared(null, "7712345")));
        assertNull(ExternalDownloader.shareUrl(new Unshared("dancer", null)));
        assertNull(ExternalDownloader.shareUrl(new Object()));
    }

    @Test public void theLinkGoesToTheAppThatWasNamedAndNowhereElse() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            Shared video = new Shared("https://www.tiktok.com/@dancer/video/7712345");

            // No app named: the save stays here and nothing is sent.
            Settings.EXTERNAL_DOWNLOADER_PACKAGE.save("");
            assertFalse(ExternalDownloader.handOff(video, activity));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());

            Settings.EXTERNAL_DOWNLOADER_PACKAGE.save("com.dv.adm");
            assertTrue(ExternalDownloader.handOff(video, activity));
            Intent sent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(sent);
            assertEquals(Intent.ACTION_SEND, sent.getAction());
            assertEquals("com.dv.adm", sent.getPackage());
            assertEquals("text/plain", sent.getType());
            assertEquals("https://www.tiktok.com/@dancer/video/7712345",
                    sent.getStringExtra(Intent.EXTRA_TEXT));

            // A video with no link is left to TikTok rather than sent as an empty message.
            assertFalse(ExternalDownloader.handOff(new Object(), activity));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertFalse(ExternalDownloader.handOff(video, null));
            assertFalse(ExternalDownloader.handOff(null, activity));
        } finally {
            Settings.EXTERNAL_DOWNLOADER_PACKAGE.save("");
        }
    }

    @Test public void bothControlsAreReachable() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.advancedDownloadsEnabled = true;
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new DownloadsPreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("download_without_sound"));
            assertNotNull(screen.findPreference("external_downloader_package"));
        } finally {
            SettingsStatus.advancedDownloadsEnabled = false;
        }
    }
}
