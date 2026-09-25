package app.morphe.extension.tiktok.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StartPageTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();

    /** TikTok's main activity as the hook sees it: something whose intent started the app. */
    static final class Host extends Activity {
        Host(Intent intent) {
            setIntent(intent);
        }
    }

    /** An activity that fails when asked for its intent, which the start page must survive. */
    static final class BrokenHost extends Activity {
        @Override public Intent getIntent() {
            throw new IllegalStateException("no intent");
        }
    }

    @Before public void setUp() {
        resetSettings();
        HookStatus.clear();
    }

    @After public void tearDown() {
        resetSettings();
        HookStatus.clear();
    }

    /** A Setting keeps the value it last loaded in memory, whichever test's store it came from. */
    private static void resetSettings() {
        Settings.START_PAGE.resetToDefault();
        Settings.BOTTOM_NAVIGATION.resetToDefault();
        Settings.BOTTOM_NAVIGATION_TABS.resetToDefault();
        Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.resetToDefault();
        Settings.FEED_NAVIGATION.resetToDefault();
        Settings.FEED_NAVIGATION_TABS.resetToDefault();
    }

    private static Intent launcher() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    }

    private static String start(Intent intent, String tag) {
        return StartPage.coldStartTag(new Host(intent), tag, null);
    }

    @Test public void tikTokKeepsItsOwnTabByDefault() {
        assertEquals("tiktok", Settings.START_PAGE.get());
        assertEquals("HOME", start(launcher(), "HOME"));
        assertEquals("SHOP_MALL", start(launcher(), "SHOP_MALL"));
    }

    @Test public void eachChoiceNamesTheTagTikTokUsesForThatTab() {
        Settings.START_PAGE.save(StartPage.INBOX);
        assertEquals("NOTIFICATION", start(launcher(), "HOME"));
        Settings.START_PAGE.save(StartPage.PROFILE);
        assertEquals("USER", start(launcher(), "HOME"));
        Settings.START_PAGE.save(StartPage.FOR_YOU);
        assertEquals("a landing rule's Shop gives way to For You", "HOME", start(launcher(), "SHOP_MALL"));
        Settings.START_PAGE.save("somewhere_new");
        assertEquals("a value this build has no tab for", "HOME", start(launcher(), "HOME"));
    }

    @Test public void friendsFollowsWhereThisPhoneShowedTheFriendsTab() {
        Settings.START_PAGE.save(StartPage.FRIENDS);
        Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.save("HOME,FRIENDS,PUBLISH,INBOX,PROFILE");
        assertEquals("a bottom tab", "FRIENDS_TAB", start(launcher(), "HOME"));
        Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.save("HOME,INBOX,PROFILE");
        assertEquals("a feed along the top", "FRIENDS_FEED", start(launcher(), "HOME"));
    }

    @Test public void onlyAPlainLauncherStartChanges() {
        Settings.START_PAGE.save(StartPage.INBOX);
        assertEquals("a restored activity", "HOME",
                StartPage.coldStartTag(new Host(launcher()), "HOME", new Bundle()));
        assertEquals("a link", "HOME",
                start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@someone")), "HOME"));
        assertEquals("a launcher category on a link", "HOME",
                start(launcher().setData(Uri.parse("snssdk1233://feed")), "HOME"));
        assertEquals("a notification's tab", "USER",
                start(launcher().putExtra(StartPage.PUSH_TAB, "USER"), "USER"));
        assertEquals("a main action without the launcher category", "HOME",
                start(new Intent(Intent.ACTION_MAIN), "HOME"));
        assertEquals("no intent at all", "HOME", start(null, "HOME"));
        assertEquals("no activity", "HOME", StartPage.coldStartTag(null, "HOME", null));
    }

    @Test public void aTabTheFeedTabsPageHidesIsNotOpened() {
        Settings.BOTTOM_NAVIGATION.save(true);
        Settings.BOTTOM_NAVIGATION_TABS.save("HOME,PROFILE");
        Settings.START_PAGE.save(StartPage.INBOX);
        assertEquals("a hidden Inbox", "HOME", start(launcher(), "HOME"));
        Settings.START_PAGE.save(StartPage.PROFILE);
        assertEquals("Profile always stays", "USER", start(launcher(), "HOME"));
        Settings.START_PAGE.save(StartPage.FRIENDS);
        Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.save("HOME,FRIENDS,INBOX,PROFILE");
        assertEquals("a hidden Friends tab", "HOME", start(launcher(), "HOME"));

        Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.save("HOME,INBOX,PROFILE");
        Settings.FEED_NAVIGATION.save(true);
        Settings.FEED_NAVIGATION_TABS.save("HOT,FOLLOWING");
        assertEquals("a hidden Friends feed", "HOME", start(launcher(), "HOME"));
        Settings.FEED_NAVIGATION_TABS.save("HOT,FRIENDS");
        assertEquals("a shown Friends feed", "FRIENDS_FEED", start(launcher(), "HOME"));

        Settings.BOTTOM_NAVIGATION.save(false);
        Settings.START_PAGE.save(StartPage.INBOX);
        assertEquals("the bottom filter off shows every tab", "NOTIFICATION", start(launcher(), "HOME"));
    }

    @Test public void aFailingActivityLeavesTikTokItsTab() {
        Settings.START_PAGE.save(StartPage.INBOX);
        assertEquals("HOME", StartPage.coldStartTag(new BrokenHost(), "HOME", null));
    }

    @Test public void theExportCountsTheStartAndTheTabItOpened() {
        int[] found = new int[1];
        HookStatus.setLineWriter((family, count, missing, truncated, firstMiss) -> {
            if (StartPage.FAMILY.equals(family)) found[0] = count;
            return family;
        });
        try {
            start(launcher(), "HOME");
            HookStatus.report();
            assertEquals("the start alone", 1, found[0]);
            Settings.START_PAGE.save(StartPage.PROFILE);
            start(launcher(), "HOME");
            HookStatus.report();
            assertEquals("the start and the tab it opened", 2, found[0]);
        } finally {
            HookStatus.setLineWriter(null);
        }
    }

    @Test public void theLauncherTestReadsActionCategoryDataAndTheNotificationTab() {
        assertTrue(StartPage.isLauncherStart(launcher()));
        assertFalse(StartPage.isLauncherStart(launcher().setAction(Intent.ACTION_VIEW)));
        assertFalse(StartPage.isLauncherStart(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));
        assertFalse(StartPage.isLauncherStart(null));
    }
}
