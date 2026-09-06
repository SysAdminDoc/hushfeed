package app.morphe.extension.tiktok.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The masks live on the Aweme under their real names, so these stand in for the model with
 * the same field shapes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SensitiveWarningsTest {
    public static final class Mask {
        public Boolean showMask;

        Mask(Boolean showMask) {
            this.showMask = showMask;
        }
    }

    public static final class Clip {
        public Mask videoMaskInfo;
        public List<Mask> videoMaskInfos = new ArrayList<>();
        public Mask generalMaskInfo;
        public Mask photosensitiveMaskInfo;
        public Mask reportMaskInfo;
        public Mask contentClassificationMaskInfo;
    }

    public static final class Params {
        public final Clip aweme;

        Params(Clip aweme) {
            this.aweme = aweme;
        }
    }

    private Clip masked() {
        Clip clip = new Clip();
        clip.videoMaskInfo = new Mask(Boolean.TRUE);
        clip.videoMaskInfos.add(new Mask(Boolean.TRUE));
        clip.videoMaskInfos.add(new Mask(Boolean.TRUE));
        clip.generalMaskInfo = new Mask(Boolean.TRUE);
        clip.photosensitiveMaskInfo = new Mask(Boolean.TRUE);
        clip.reportMaskInfo = new Mask(Boolean.TRUE);
        clip.contentClassificationMaskInfo = new Mask(Boolean.TRUE);
        return clip;
    }

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    @After
    public void tearDown() {
        Settings.HIDE_SENSITIVE_WARNINGS.save(false);
    }

    @Test
    public void everyMaskOnTheVideoStopsAskingToBeTappedThrough() {
        Settings.HIDE_SENSITIVE_WARNINGS.save(true);
        Clip clip = masked();

        SensitiveWarnings.clear(new Params(clip));

        assertEquals(Boolean.FALSE, clip.videoMaskInfo.showMask);
        for (Mask mask : clip.videoMaskInfos) {
            assertEquals(Boolean.FALSE, mask.showMask);
        }
        // TikTok's own reset drops these four outright, so a null is a state it handles.
        assertNull(clip.generalMaskInfo);
        assertNull(clip.photosensitiveMaskInfo);
        assertNull(clip.reportMaskInfo);
        assertNull(clip.contentClassificationMaskInfo);
    }

    @Test
    public void theVideoIsUntouchedWhileTheSwitchIsOff() {
        Settings.HIDE_SENSITIVE_WARNINGS.save(false);
        Clip clip = masked();

        SensitiveWarnings.clear(new Params(clip));

        assertEquals(Boolean.TRUE, clip.videoMaskInfo.showMask);
        assertEquals(Boolean.TRUE, clip.videoMaskInfos.get(0).showMask);
        assertNotNull(clip.generalMaskInfo);
    }

    @Test
    public void aVideoWithNoMasksAndAMissingAwemeAreBothHarmless() {
        Settings.HIDE_SENSITIVE_WARNINGS.save(true);
        Clip clip = new Clip();

        SensitiveWarnings.clear(new Params(clip));
        SensitiveWarnings.clear(new Params(null));
        SensitiveWarnings.clear(null);
        SensitiveWarnings.clear(new Object());

        assertNull(clip.videoMaskInfo);
        assertEquals(0, clip.videoMaskInfos.size());
    }
}
