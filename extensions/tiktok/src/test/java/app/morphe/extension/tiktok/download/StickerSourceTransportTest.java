package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;

import android.view.View;

import app.morphe.extension.shared.Utils;
import com.ss.android.ugc.aweme.base.model.UrlModel;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** The save-button entry resolves rich sticker sources before falling back to preview URLs. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class StickerSourceTransportTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    @Test public void registeredRichStickerKeepsItsTrimmedHttpsUrl() {
        View sheet = new View(RuntimeEnvironment.getApplication());
        PreviewModel preview = new PreviewModel(null);
        RichSticker sticker = new RichSticker("  https://cdn.example/sticker.gif  ", "GIF");
        StickerGallerySaver.registerStickerSource(preview, new SourceHolder(sticker));
        StickerGallerySaver.attachSaveImageButton(sheet, preview);

        StickerGallerySaver.StickerAsset asset = resolved(sheet);
        assertTrue("the registered rich sticker getter was not reached", sticker.urlReads > 0);
        assertNotNull("the HTTPS source did not reach the save-button state", asset);
        assertEquals("https://cdn.example/sticker.gif", asset.url);
        assertEquals(List.of("https://cdn.example/sticker.gif"), asset.urls);
        assertTrue("the direct source lost its animation type", asset.animated);
    }

    @Test public void cleartextRichStickerCannotReplaceTheSaveButtonsPreviousAsset() {
        View sheet = new View(RuntimeEnvironment.getApplication());
        PreviewModel preview = new PreviewModel(null);
        StickerGallerySaver.registerStickerSource(preview,
                new SourceHolder(new RichSticker("https://cdn.example/previous.gif", "GIF")));
        StickerGallerySaver.attachSaveImageButton(sheet, preview);
        assertNotNull("the same source shape must work over HTTPS", resolved(sheet));

        RichSticker untrusted = new RichSticker("http://cdn.example/next.gif", "GIF");
        StickerGallerySaver.registerStickerSource(preview, new SourceHolder(untrusted));
        StickerGallerySaver.attachSaveImageButton(sheet, preview);

        assertTrue("the HTTP case never reached the direct source getter", untrusted.urlReads > 0);
        assertNull("a save button could fetch cleartext media or keep the previous sticker", resolved(sheet));
    }

    @Test public void rejectingTheDirectUrlStillAllowsAnHttpsPreviewFallback() {
        View sheet = new View(RuntimeEnvironment.getApplication());
        PreviewModel preview = new PreviewModel(new PreviewUrls("https://cdn.example/preview.png"));
        RichSticker untrusted = new RichSticker("http://cdn.example/source.gif", "GIF");
        StickerGallerySaver.registerStickerSource(preview, new SourceHolder(untrusted));
        StickerGallerySaver.attachSaveImageButton(sheet, preview);

        assertTrue(untrusted.urlReads > 0);
        StickerGallerySaver.StickerAsset asset = resolved(sheet);
        assertNotNull("rejecting the direct source also discarded the usable preview", asset);
        assertEquals("https://cdn.example/preview.png", asset.url);
        assertEquals(List.of("https://cdn.example/preview.png"), asset.urls);
    }

    private static StickerGallerySaver.StickerAsset resolved(View sheet) {
        // Observe the state the real click listener reads. Never seed this map or call the
        // URL helper directly: registration and attachment must choose the asset themselves.
        Map<View, StickerGallerySaver.StickerAsset> sheets = ReflectionHelpers.getStaticField(
                StickerGallerySaver.class, "ATTACHED_SHEETS");
        return sheets.get(sheet);
    }

    public static final class PreviewModel {
        public final UrlModel previewUrl;
        PreviewModel(UrlModel url) { previewUrl = url; }
    }

    /** A supported source holder carries the rich sticker object in LLILLIZIL. */
    public static final class SourceHolder {
        public final RichSticker LLILLIZIL;
        SourceHolder(RichSticker sticker) { LLILLIZIL = sticker; }
    }

    /** IMGiphyInfo exposes a direct getUrl string rather than an UrlModel. */
    public static final class RichSticker {
        private final String url;
        private final String type;
        int urlReads;
        RichSticker(String url, String type) { this.url = url; this.type = type; }
        public String getUrl() { urlReads++; return url; }
        public String getType() { return type; }
    }

    public static final class PreviewUrls extends UrlModel {
        private final List<String> urls;
        PreviewUrls(String url) { urls = List.of(url); }
        @Override public List<String> getUrlList() { return urls; }
    }
}
