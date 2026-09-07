package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ShareActionChecklistTest {
    @After public void reset() {
        Settings.SHARE_ACTION_CATALOG.save("");
        Settings.SHARE_HIDDEN_ITEMS.save("");
    }

    @Test public void checklistSearchesObservedActionsAndPreservesUnknownIdentifiers() {
        try (var owner = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.SHARE_ACTION_CATALOG.save("copy\tCopy link\nweird action\tCustom action");
            Settings.SHARE_HIDDEN_ITEMS.save("copy, unknown_key");

            ShareActionChecklistPreference preference = new ShareActionChecklistPreference(activity);
            View view = preference.onCreateDialogView();
            EditText search = view.findViewWithTag("share_action_search");
            assertNotNull(search);
            CheckBox copy = findCheckBox(view, "share_action_copy");
            CheckBox weird = findCheckBox(view, "share_action_weird action");
            assertNotNull(copy);
            assertNotNull(weird);
            assertTrue(copy.isChecked());
            copy.performClick();

            search.setText("custom");
            assertNotNull(findCheckBox(view, "share_action_weird action"));
            weird.performClick();
            preference.onDialogClosed(true);
            assertEquals("unknown_key, weird action", Settings.SHARE_HIDDEN_ITEMS.get());

            ShareActionChecklistPreference reopened = new ShareActionChecklistPreference(activity);
            View reopenedView = reopened.onCreateDialogView();
            assertTrue(findCheckBox(reopenedView, "share_action_weird action").isChecked());
            assertFalse(findCheckBox(reopenedView, "share_action_copy").isChecked());
        }
    }

    private static CheckBox findCheckBox(View view, String tag) {
        if (view instanceof CheckBox && tag.equals(view.getTag())) {
            return (CheckBox) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                CheckBox found = findCheckBox(group.getChildAt(index), tag);
                if (found != null) return found;
            }
        }
        return null;
    }
}
