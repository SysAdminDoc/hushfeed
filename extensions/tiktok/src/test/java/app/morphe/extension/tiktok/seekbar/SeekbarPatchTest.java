/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.seekbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class SeekbarPatchTest {
    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.SHOW_SEEKBAR.save(false);
        Settings.SHOW_SEEKBAR_THUMBNAIL.save(false);
    }

    @After
    public void tearDown() {
        Settings.SHOW_SEEKBAR.save(false);
        Settings.SHOW_SEEKBAR_THUMBNAIL.save(false);
    }

    @Test
    public void isEnabledFollowsTheSetting() {
        assertFalse(SeekbarPatch.isEnabled());
        Settings.SHOW_SEEKBAR.save(true);
        assertTrue(SeekbarPatch.isEnabled());
    }

    @Test
    public void hiddenShowTypesAreRewrittenToZeroWhenEnabled() {
        Settings.SHOW_SEEKBAR.save(true);
        assertEquals(0, SeekbarPatch.overrideSeekbarShowType(3));
        assertEquals(0, SeekbarPatch.overrideSeekbarShowType(4));
    }

    @Test
    public void otherShowTypesPassThroughEvenWhenEnabled() {
        Settings.SHOW_SEEKBAR.save(true);
        assertEquals(0, SeekbarPatch.overrideSeekbarShowType(0));
        assertEquals(1, SeekbarPatch.overrideSeekbarShowType(1));
        assertEquals(2, SeekbarPatch.overrideSeekbarShowType(2));
    }

    @Test
    public void showTypesPassThroughWhenDisabled() {
        assertEquals(3, SeekbarPatch.overrideSeekbarShowType(3));
        assertEquals(4, SeekbarPatch.overrideSeekbarShowType(4));
    }

    @Test
    public void refreshDefeatsStaledZeroEqualityWhenEnabled() {
        Settings.SHOW_SEEKBAR.save(true);
        assertEquals(Integer.MIN_VALUE, SeekbarPatch.forceSeekbarRefresh(0, 0));
    }

    @Test
    public void refreshPassesThroughWhenTypesAlreadyDiffer() {
        Settings.SHOW_SEEKBAR.save(true);
        assertEquals(3, SeekbarPatch.forceSeekbarRefresh(3, 0));
    }

    @Test
    public void refreshPassesThroughWhenDisabled() {
        assertEquals(0, SeekbarPatch.forceSeekbarRefresh(0, 0));
    }

    @Test
    public void refreshPassesThroughWhenRequestIsNotZero() {
        Settings.SHOW_SEEKBAR.save(true);
        assertEquals(1, SeekbarPatch.forceSeekbarRefresh(1, 1));
    }

    @Test
    public void theDraggableFlagIsRaisedOnlyWhenEnabledAndOnlyFromNothing() {
        assertEquals(0, SeekbarPatch.overrideDraftProgressBar(0));
        assertEquals(2, SeekbarPatch.overrideDraftProgressBar(2));
        Settings.SHOW_SEEKBAR.save(true);
        assertEquals(1, SeekbarPatch.overrideDraftProgressBar(0));
        // A server value the gate already accepts is handed on as it came.
        assertEquals(2, SeekbarPatch.overrideDraftProgressBar(2));
    }

    @Test
    public void thumbnailGateIsOverriddenWhenEnabled() {
        Settings.SHOW_SEEKBAR_THUMBNAIL.save(true);
        assertEquals(1, SeekbarPatch.overrideThumbnailGate("seekbar_show_thumbnail_when_drag", 0));
    }

    @Test
    public void thumbnailGatePassesThroughForOtherKeys() {
        Settings.SHOW_SEEKBAR_THUMBNAIL.save(true);
        assertEquals(0, SeekbarPatch.overrideThumbnailGate("other_key", 0));
    }

    @Test
    public void thumbnailGatePassesThroughWhenDisabled() {
        assertEquals(0, SeekbarPatch.overrideThumbnailGate("seekbar_show_thumbnail_when_drag", 0));
    }
}
