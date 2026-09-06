package app.morphe.extension.tiktok.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.view.View;

import java.lang.reflect.Field;
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
}
