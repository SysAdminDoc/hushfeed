package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.preference.PreferenceActivity;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.StringSetting;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The range line under a Min/Max row follows the stored value.
 *
 * <p>A restore, a reset and an undo all set the value through {@code setValue} and never open the
 * dialog, so a row that only rebuilt its summary on dialog close kept showing the range it had
 * just replaced.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RangeValueSummaryTest {
    public static final class TestActivity extends PreferenceActivity {}

    @Before
    public void installContext() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    @Test
    public void theRangeLineFollowsAValueSetWithoutTheDialog() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            StringSetting setting =
                    new StringSetting("range_summary_test_views", "20000-1500000");
            RangeValuePreference preference =
                    new RangeValuePreference(context, "Views", "Summary", setting);

            assertEquals("Summary\n20K to 1.5M", preference.getSummary().toString());

            // What Reset settings does: the stored value goes back to its default through the
            // same call the restore path uses.
            preference.setValue("100-2000");
            assertEquals("Summary\n100 to 2K", preference.getSummary().toString());

            preference.setValue("0-" + Long.MAX_VALUE);
            assertEquals("Summary\n0 and above", preference.getSummary().toString());
        }
    }

    @Test
    public void settingTheSameValueAgainLeavesTheLineAlone() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            StringSetting setting =
                    new StringSetting("range_summary_test_likes", "1000-5000");
            RangeValuePreference preference =
                    new RangeValuePreference(context, "Likes", "Summary", setting);

            preference.setValue("1000-5000");
            assertEquals("Summary\n1K to 5K", preference.getSummary().toString());
        }
    }
}
