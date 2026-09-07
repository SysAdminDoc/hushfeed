package app.morphe.extension.tiktok.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NavigationTabsFilterTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.FEED_NAVIGATION.save(true);
        Settings.FEED_NAVIGATION_TABS.save("HOT");
        Settings.FEED_NAVIGATION_OBSERVED_TABS.save("HOT,EXPLORE");
        Settings.FEED_NAVIGATION_BLOCK_NEW_TABS.save(true);
        Settings.BOTTOM_NAVIGATION.save(true);
        Settings.BOTTOM_NAVIGATION_TABS.save("HOME,PROFILE");
        Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.save("HOME,PROFILE");
        Settings.BOTTOM_NAVIGATION_BLOCK_NEW_TABS.save(true);
    }

    @After public void tearDown() {
        Settings.FEED_NAVIGATION.save(false);
        Settings.FEED_NAVIGATION_TABS.save(NavigationTabOptions.defaultEnabledKeys());
        Settings.FEED_NAVIGATION_OBSERVED_TABS.save(NavigationTabOptions.HOT);
        Settings.FEED_NAVIGATION_BLOCK_NEW_TABS.save(false);
        Settings.BOTTOM_NAVIGATION.save(false);
        Settings.BOTTOM_NAVIGATION_TABS.save(BottomNavigationTabOptions.defaultEnabledKeys());
        Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.save("HOME,PROFILE");
        Settings.BOTTOM_NAVIGATION_BLOCK_NEW_TABS.save(false);
    }

    @Test public void tagGetterAndFallbackGetterKeepOnlyConfiguredTab() {
        GetterTab hot = new GetterTab("For You");
        GetterTab explore = new GetterTab("Explore");
        FallbackTab following = new FallbackTab("Following");
        List<?> result = NavigationTabsFilter.filterTopTabs(Arrays.asList(hot, explore, following));

        assertEquals(1, result.size());
        assertSame(hot, result.get(0));
    }

    @Test public void optionSerializationNormalizesAliasesAndPreservesUnknownTags() {
        assertEquals(NavigationTabOptions.FOLLOWING,
                NavigationTabOptions.normalizeRuntimeTag(" homepage_following "));
        assertEquals("RAW:creator picks",
                NavigationTabOptions.normalizeRuntimeTag(" Creator, Picks "));
        assertEquals("HOT,RAW:creator picks",
                NavigationTabOptions.serializeEnabledKeys(
                        NavigationTabOptions.parseEnabledKeys("raw:Creator Picks")));

        assertTrue(BottomNavigationTabOptions.parseEnabledKeys("inbox").contains(
                BottomNavigationTabOptions.HOME));
        assertTrue(BottomNavigationTabOptions.parseEnabledKeys("inbox").contains(
                BottomNavigationTabOptions.PROFILE));
        assertEquals(BottomNavigationTabOptions.MALL,
                BottomNavigationTabOptions.normalizeRuntimeTag("shop_mall"));
    }

    @Test public void bottomFilterRetainsRequiredTabsWhenEveryOptionalTabIsDisabled() {
        BottomTab home = new BottomTab("home");
        BottomTab inbox = new BottomTab("inbox");
        BottomTab profile = new BottomTab("me");

        List<?> result = NavigationTabsFilter.filterBottomTabs(Arrays.asList(home, inbox, profile));

        assertEquals(2, result.size());
        assertSame(home, result.get(0));
        assertSame(profile, result.get(1));
    }

    @Test public void disabledNavigationReturnsTheOriginalListIdentity() {
        Settings.FEED_NAVIGATION.save(false);
        List<GetterTab> tabs = Arrays.asList(new GetterTab("For You"), new GetterTab("Explore"));

        assertSame(tabs, NavigationTabsFilter.filterTopTabs(tabs));
        assertFalse(NavigationTabOptions.isKnownKey("RAW:unknown"));
    }

    public static class GetterTab {
        private final String value;

        GetterTab(String value) {
            this.value = value;
        }

        public String tag() {
            return value;
        }
    }

    public static class FallbackTab {
        private final String value;

        FallbackTab(String value) {
            this.value = value;
        }

        public String getTag() {
            return value;
        }
    }

    public static class BottomTab {
        private final String value;

        BottomTab(String value) {
            this.value = value;
        }

        public String getTag() {
            return value;
        }
    }
}
