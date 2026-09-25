package app.morphe.extension.tiktok.navigation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.ui.FeedRecommendFragment;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FeedRefreshTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();

    /** TikTok's trigger names, which reach the refresh as an enum whose class name R8 assigns. */
    enum Trigger { CLICK_TOP, CLICK_BOTTOM, PULL_DOWN_REFRESH, SOMETHING_NEW }

    /** A For You fragment TikTok might subclass one day. */
    static final class ForYouVariant extends FeedRecommendFragment {}

    /** The Following feed, which shares the refresh panel and keeps its own pull. */
    static final class FeedFollowFragment {}

    @Before public void setUp() {
        resetSwitches();
        HookStatus.clear();
    }

    @After public void tearDown() {
        resetSwitches();
        HookStatus.clear();
    }

    /** A Setting keeps the value it last loaded in memory, whichever test's store it came from. */
    private static void resetSwitches() {
        Settings.KEEP_FOR_YOU_ON_TAB_TAP.resetToDefault();
        Settings.KEEP_FOR_YOU_ON_PULL_DOWN.resetToDefault();
    }

    @Test public void everyRefreshGoesOnWithBothSwitchesOff() {
        for (Trigger trigger : Trigger.values()) assertTrue(trigger.name(), FeedRefresh.allowRefresh(trigger));
    }

    @Test public void aTabTapKeepsForYouWhileAPullDownStillRefreshes() {
        Settings.KEEP_FOR_YOU_ON_TAB_TAP.save(true);
        assertFalse("Home", FeedRefresh.allowRefresh(Trigger.CLICK_BOTTOM));
        assertFalse("For You tab", FeedRefresh.allowRefresh(Trigger.CLICK_TOP));
        assertTrue("pull down", FeedRefresh.allowRefresh(Trigger.PULL_DOWN_REFRESH));
    }

    @Test public void aPullDownOnForYouIsKeptAtThePanelWhileTapsStillRefresh() {
        assertFalse("switch off", FeedRefresh.keepPull(new FeedRecommendFragment()));
        Settings.KEEP_FOR_YOU_ON_PULL_DOWN.save(true);
        assertTrue(FeedRefresh.keepPull(new FeedRecommendFragment()));
        assertTrue("a subclass is still For You", FeedRefresh.keepPull(new ForYouVariant()));
        assertTrue("Home", FeedRefresh.allowRefresh(Trigger.CLICK_BOTTOM));
        assertTrue("For You tab", FeedRefresh.allowRefresh(Trigger.CLICK_TOP));
    }

    @Test public void aPullDownOnAnotherFeedOrWithNoFragmentRefreshes() {
        Settings.KEEP_FOR_YOU_ON_PULL_DOWN.save(true);
        assertFalse("Following", FeedRefresh.keepPull(new FeedFollowFragment()));
        assertFalse("no fragment", FeedRefresh.keepPull(null));
    }

    @Test public void theRefreshMethodLeavesAPullTriggerToThePanel() {
        Settings.KEEP_FOR_YOU_ON_PULL_DOWN.save(true);
        Settings.KEEP_FOR_YOU_ON_TAB_TAP.save(true);
        assertTrue("a refresh asked for with the pull trigger but no pull", FeedRefresh.allowRefresh(Trigger.PULL_DOWN_REFRESH));
    }

    @Test public void aTriggerTheSwitchesDontNameAlwaysRefreshes() {
        Settings.KEEP_FOR_YOU_ON_TAB_TAP.save(true);
        Settings.KEEP_FOR_YOU_ON_PULL_DOWN.save(true);
        assertTrue(FeedRefresh.allowRefresh(Trigger.SOMETHING_NEW));
        assertTrue(FeedRefresh.allowRefresh(null));
        assertTrue("a name alone is not TikTok's trigger", FeedRefresh.allowRefresh("CLICK_BOTTOM"));
    }

    @Test public void theExportNamesTheHookAndWhatItKept() {
        Settings.KEEP_FOR_YOU_ON_TAB_TAP.save(true);
        FeedRefresh.allowRefresh(Trigger.CLICK_BOTTOM);
        String report = String.join("\n", HookStatus.report());
        assertTrue(report, report.contains(FeedRefresh.FAMILY));
    }
}
