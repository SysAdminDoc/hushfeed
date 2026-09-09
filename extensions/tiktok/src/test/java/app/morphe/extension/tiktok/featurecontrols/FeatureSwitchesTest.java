package app.morphe.extension.tiktok.featurecontrols;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.ghostmode.GhostMode;
import app.morphe.extension.tiktok.inbox.InboxControls;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.telemetry.DisableTelemetryPatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The answers TikTok's own code gets back from a switch. Each of these classes is a handful of
 * one-line replies, injected where the host asks a question, and none of them had a test: a
 * reply inverted or left constant would have shown up only on a phone.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class FeatureSwitchesTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    @After public void tearDown() {
        for (var setting : new app.morphe.extension.shared.settings.BooleanSetting[]{
                Settings.GHOST_MODE, Settings.DISABLE_ANALYTICS,
                Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS, Settings.HIDE_INBOX_STORIES,
                Settings.EXPAND_ACTIVITY_LIST, Settings.HIDE_HOMEPAGE_COIN,
                Settings.HIDE_FEED_FOLLOW_BUTTON, Settings.HIDE_FEED_SAVE_BUTTON,
                Settings.HIDE_LIVE_ENTRANCE, Settings.HIDE_FEED_SEARCH_BUTTON,
                Settings.ENABLE_LONG_PRESS_SPEED_LOCK}) {
            setting.save(setting.defaultValue);
        }
    }

    @Test public void ghostModeAnswersEveryReportingCallTogether() {
        Settings.GHOST_MODE.save(false);
        assertFalse(GhostMode.shouldBlockStoryView());
        assertFalse(GhostMode.shouldBlockProfileView());
        assertFalse(GhostMode.shouldBlockTypingStatus());

        Settings.GHOST_MODE.save(true);
        assertTrue(GhostMode.shouldBlockStoryView());
        assertTrue(GhostMode.shouldBlockProfileView());
        assertTrue(GhostMode.shouldBlockTypingStatus());
    }

    @Test public void telemetryStaysOnUntilTheSwitchIsOnAndTheAppIsUp() {
        Settings.DISABLE_ANALYTICS.save(false);
        assertFalse(DisableTelemetryPatch.isTelemetryDisabled());

        Settings.DISABLE_ANALYTICS.save(true);
        assertTrue(DisableTelemetryPatch.isTelemetryDisabled());
    }

    @Test public void anInboxSectionIsBuiltOnlyWhileItsSwitchIsOff() {
        Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS.save(false);
        Settings.HIDE_INBOX_STORIES.save(false);
        Settings.EXPAND_ACTIVITY_LIST.save(false);
        assertTrue(InboxControls.shouldShowSuggestedAccounts());
        assertTrue(InboxControls.shouldShowInboxStories());
        // With the switch off, native expanded and collapsed lists retain their state.
        assertFalse(InboxControls.shouldCollapseActivityList(false));
        assertTrue(InboxControls.shouldCollapseActivityList(true));

        Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS.save(true);
        Settings.HIDE_INBOX_STORIES.save(true);
        Settings.EXPAND_ACTIVITY_LIST.save(true);
        assertFalse(InboxControls.shouldShowSuggestedAccounts());
        assertFalse(InboxControls.shouldShowInboxStories());
        assertFalse(InboxControls.shouldCollapseActivityList(false));
        assertFalse(InboxControls.shouldCollapseActivityList(true));
    }

    /** Only the coin pendant shapes are dropped, and only while the switch is on. */
    @Test public void theCoinPendantGoesAndOtherTouchPointsStay() {
        Object pendant = new Object() {
            @Override public String toString() { return "pendant"; }
        };
        Settings.HIDE_HOMEPAGE_COIN.save(true);
        // Not one of the named ByteDance classes, so it is left alone whatever the switch says.
        assertNotNull(FeatureControls.filterPromotionalTouchPoint(pendant));
        assertNull("a null touch point must stay null",
                FeatureControls.filterPromotionalTouchPoint(null));

        Settings.HIDE_HOMEPAGE_COIN.save(false);
        assertEquals(pendant, FeatureControls.filterPromotionalTouchPoint(pendant));
        assertEquals(pendant, FeatureControls.filterNormalPendant(pendant));
    }

    @Test public void aHiddenFeedButtonIsGoneAndAShownOneKeepsItsVisibility() {
        Settings.HIDE_FEED_FOLLOW_BUTTON.save(false);
        Settings.HIDE_FEED_SAVE_BUTTON.save(false);
        assertEquals(View.VISIBLE, FeatureControls.hideFeedFollowButtonVisibility(View.VISIBLE));
        assertEquals(View.INVISIBLE, FeatureControls.hideFeedSaveButtonVisibility(View.INVISIBLE));

        Settings.HIDE_FEED_FOLLOW_BUTTON.save(true);
        Settings.HIDE_FEED_SAVE_BUTTON.save(true);
        assertEquals(View.GONE, FeatureControls.hideFeedFollowButtonVisibility(View.VISIBLE));
        assertEquals(View.GONE, FeatureControls.hideFeedSaveButtonVisibility(View.VISIBLE));
    }

    /** These two answer "is it enabled", so hiding means returning false rather than GONE. */
    @Test public void theLiveAndSearchButtonsAreDisabledRatherThanHidden() {
        Settings.HIDE_LIVE_ENTRANCE.save(false);
        Settings.HIDE_FEED_SEARCH_BUTTON.save(false);
        assertTrue(FeatureControls.hideFeedLiveButtonEnabled(true));
        assertTrue(FeatureControls.hideFeedSearchButtonEnabled(true));
        // The host's own answer still wins when it was already no.
        assertFalse(FeatureControls.hideFeedLiveButtonEnabled(false));

        Settings.HIDE_LIVE_ENTRANCE.save(true);
        Settings.HIDE_FEED_SEARCH_BUTTON.save(true);
        assertFalse(FeatureControls.hideFeedLiveButtonEnabled(true));
        assertFalse(FeatureControls.hideFeedSearchButtonEnabled(true));
    }

    @Test public void theSpeedLockKeepsTheHostsDistanceAndFillsInAMissingOne() {
        Settings.ENABLE_LONG_PRESS_SPEED_LOCK.save(false);
        assertFalse(FeatureControls.overrideLongPressSpeedUpEnabled(false));
        assertEquals("the host's own distance is untouched while the switch is off",
                0, FeatureControls.overrideLongPressSpeedUpLockDistance(0));

        Settings.ENABLE_LONG_PRESS_SPEED_LOCK.save(true);
        assertTrue(FeatureControls.overrideLongPressSpeedUpEnabled(false));
        assertEquals(40, FeatureControls.overrideLongPressSpeedUpLockDistance(40));
        assertTrue("a distance of zero needs one of ours",
                FeatureControls.overrideLongPressSpeedUpLockDistance(0) > 0);
    }
}
