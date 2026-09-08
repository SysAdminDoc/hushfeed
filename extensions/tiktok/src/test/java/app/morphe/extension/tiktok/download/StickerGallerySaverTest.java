package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Which sticker the Save button on a reused preview sheet is holding. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StickerGallerySaverTest {
    /** A UrlModel the saver can read, which is what the source sticker hands back. */
    public static final class Urls extends com.ss.android.ugc.aweme.base.model.UrlModel {
        private final java.util.List<String> urls;
        Urls(String url) { urls = java.util.List.of(url); }
        @Override public java.util.List<String> getUrlList() { return urls; }
        @Override public String getUri() { return urls.get(0); }
    }

    /** Stands in for the source sticker, whose accessors kept their names. */
    public static final class Sticker {
        private final Urls urls;
        Sticker(String url) { urls = new Urls(url); }
        public com.ss.android.ugc.aweme.base.model.UrlModel getStaticUrl() { return urls; }
    }

    /** The preview model TikTok binds to the sheet; the source is registered against it. */
    public static final class PreviewModel {
    }

    /**
     * Below Android 10 a sticker is written straight to external storage rather than handed
     * to MediaStore. That path built a File from the display name and opened it, so the
     * second save of the same sticker replaced the first, and its cleanup deleted whatever
     * was at that name whether or not this save had put it there.
     */
    @Test public void savingTheSameStickerTwiceLeavesTwoFiles() throws Exception {
        app.morphe.extension.shared.Utils.setContext(RuntimeEnvironment.getApplication());
        android.graphics.Bitmap sticker =
                android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888);
        sticker.eraseColor(android.graphics.Color.RED);

        Method save = StickerGallerySaver.class.getDeclaredMethod(
                "saveBitmapWithLegacyStorage", android.content.Context.class,
                android.graphics.Bitmap.class, String.class);
        save.setAccessible(true);

        java.io.File first = (java.io.File) save.invoke(
                null, RuntimeEnvironment.getApplication(), sticker, "sticker.png");
        java.io.File second = (java.io.File) save.invoke(
                null, RuntimeEnvironment.getApplication(), sticker, "sticker.png");

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals("the second save replaced the first",
                first.getAbsolutePath(), second.getAbsolutePath());
        assertTrue("the first file went away", first.isFile());
        assertTrue("the second file was not written", second.isFile());
        assertTrue("the first file is empty", first.length() > 0);
        assertTrue("the second file is empty", second.length() > 0);
        assertEquals("sticker.png", first.getName());
        assertEquals("sticker_2.png", second.getName());

        // And a third, so the suffix counts on rather than sticking at _2.
        java.io.File third = (java.io.File) save.invoke(
                null, RuntimeEnvironment.getApplication(), sticker, "sticker.png");
        assertEquals("sticker_3.png", third.getName());
        assertTrue(first.isFile() && second.isFile() && third.isFile());
    }

    /**
     * The path a save reports has to be the file that was written.
     *
     * <p>The display name handed to MediaStore is a request. A duplicate gets a suffix of the
     * provider's choosing, and reporting the requested name points the reader at a file that is
     * not theirs. The provider is stood in for here, because there is no MediaStore under a unit
     * test; what is under test is whether the answer it gives is the one reported.
     */
    @Test public void theReportedPathIsTheNameTheGalleryUsed() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        app.morphe.extension.shared.Utils.setContext(context);

        android.net.Uri uri = android.net.Uri.withAppendedPath(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "42");
        org.robolectric.fakes.RoboCursor cursor = new org.robolectric.fakes.RoboCursor();
        cursor.setColumnNames(java.util.List.of(
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME));
        cursor.setResults(new Object[][]{{"sticker_2.png"}});
        org.robolectric.Shadows.shadowOf(context.getContentResolver()).setCursor(uri, cursor);

        String reported = (String) savedPath().invoke(null, context, uri, "sticker.png", false);
        assertTrue("the reported path is the name that was asked for, not the one used: "
                + reported, reported.endsWith("/sticker_2.png"));
    }

    /** A provider that will not say leaves the reader with the best guess rather than nothing. */
    @Test public void anUnreadableRowStillReportsSomewhere() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        app.morphe.extension.shared.Utils.setContext(context);

        android.net.Uri missing = android.net.Uri.withAppendedPath(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "404404");
        String reported = (String) savedPath().invoke(null, context, missing, "sticker.png", false);
        assertTrue(reported, reported.endsWith("/sticker.png"));
    }

    private static Method savedPath() throws Exception {
        Method method = StickerGallerySaver.class.getDeclaredMethod(
                "savedPath", android.content.Context.class, android.net.Uri.class,
                String.class, boolean.class);
        method.setAccessible(true);
        return method;
    }

    /** A name with no extension is a name, not a reason to build sticker_2 out of nothing. */
    @Test public void aNameWithNoExtensionStillGetsItsOwnFile() throws Exception {
        java.io.File directory = new java.io.File(
                RuntimeEnvironment.getApplication().getCacheDir(), "claim-test");
        assertTrue(directory.mkdirs() || directory.isDirectory());
        assertEquals("sticker", MediaFileWriter.claim(directory, "sticker").getName());
        assertEquals("sticker_2", MediaFileWriter.claim(directory, "sticker").getName());
        assertEquals("a.b.png", MediaFileWriter.claim(directory, "a.b.png").getName());
        assertEquals("a.b_2.png", MediaFileWriter.claim(directory, "a.b.png").getName());
    }

    @Test public void aReusedSheetHoldsTheStickerItIsShowingNow() throws Exception {
        app.morphe.extension.shared.Utils.setContext(RuntimeEnvironment.getApplication());
        View sheet = new View(RuntimeEnvironment.getApplication());

        PreviewModel first = new PreviewModel();
        StickerGallerySaver.registerStickerSource(first, new Sticker("https://example.invalid/first.png"));
        // The sheet has already been given its button, which is the state a second bind meets.
        attached().put(sheet, findAsset(first));
        assertEquals("https://example.invalid/first.png", url(attached().get(sheet)));

        // The reader closes it and opens a different sticker. TikTok binds the same sheet.
        PreviewModel second = new PreviewModel();
        StickerGallerySaver.registerStickerSource(second, new Sticker("https://example.invalid/second.png"));
        StickerGallerySaver.attachSaveImageButton(sheet, second);

        // The button reads this when it is pressed, so it has to be the sticker on screen.
        assertEquals("https://example.invalid/second.png", url(attached().get(sheet)));
    }

    @SuppressWarnings("unchecked")
    private static Map<View, Object> attached() throws Exception {
        Field f = StickerGallerySaver.class.getDeclaredField("ATTACHED_SHEETS");
        f.setAccessible(true);
        return (Map<View, Object>) f.get(null);
    }

    private static Object findAsset(Object model) throws Exception {
        var method = StickerGallerySaver.class.getDeclaredMethod("findStickerAsset", Object.class);
        method.setAccessible(true);
        Object asset = method.invoke(null, model);
        assertNotNull("the test double must be readable as a sticker", asset);
        return asset;
    }

    private static String url(Object asset) throws Exception {
        assertNotNull("nothing recorded for the sheet", asset);
        Field f = asset.getClass().getDeclaredField("url");
        f.setAccessible(true);
        return (String) f.get(asset);
    }

    @Test public void aCleartextStickerMirrorIsNotFetchedFrom() {
        // The bytes behind these addresses reach a native WebP decoder, so an unauthenticated
        // mirror is a body anyone on the network can choose, handed to a parser written in C.
        assertEquals(java.util.List.of("https://cdn.example/sticker.webp"),
                StickerGallerySaver.usableUrlList(java.util.Arrays.asList(
                        "http://cdn.example/sticker.webp",
                        "https://cdn.example/sticker.webp",
                        null,
                        "")));
        assertEquals(java.util.List.of(), StickerGallerySaver.usableUrlList(
                java.util.List.of("http://cdn.example/sticker.webp")));
    }
}
