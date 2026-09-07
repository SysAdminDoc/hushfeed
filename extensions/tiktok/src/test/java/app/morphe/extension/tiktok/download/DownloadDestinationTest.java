package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import app.morphe.extension.shared.Utils;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DownloadDestinationTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    @Test public void allowedRootsAreCanonicalAndTraversalIsRejected() {
        assertEquals("DCIM/TikTok", DownloadDestination.validate(
                "/storage/emulated/0/dcim//TikTok/", DownloadDestination.Kind.VIDEO));
        assertEquals("Movies/Clips", DownloadDestination.validate(
                "movies/Clips", DownloadDestination.Kind.VIDEO));
        assertEquals("Pictures/Clips", DownloadDestination.validate(
                "photos/Clips", DownloadDestination.Kind.PHOTO));
        assertThrows(IllegalArgumentException.class, () -> DownloadDestination.validate(
                "DCIM/../Movies", DownloadDestination.Kind.VIDEO));
        assertThrows(IllegalArgumentException.class, () -> DownloadDestination.validate(
                "Pictures/Clips", DownloadDestination.Kind.VIDEO));
        assertEquals(DownloadDestination.DEFAULT_PATH,
                DownloadDestination.resolve("outside/Clips", DownloadDestination.Kind.STICKER));
    }

    @Test public void collectionUriUsesFilesForSharedRootsOnModernAndroid() {
        int originalSdk = android.os.Build.VERSION.SDK_INT;
        try {
            ReflectionHelpers.setStaticField(android.os.Build.VERSION.class, "SDK_INT", 28);
            assertEquals(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    DownloadDestination.collectionUri("Movies/TikTok", true));
            assertEquals(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    DownloadDestination.collectionUri("Pictures/TikTok", false));

            ReflectionHelpers.setStaticField(android.os.Build.VERSION.class, "SDK_INT", 29);
            Uri files = android.provider.MediaStore.Files.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
            assertEquals(files, DownloadDestination.collectionUri("Download/TikTok", true));
            assertEquals(files, DownloadDestination.collectionUri("Documents/TikTok", false));
        } finally {
            ReflectionHelpers.setStaticField(android.os.Build.VERSION.class, "SDK_INT", originalSdk);
        }
    }
}
