package app.morphe.extension.tiktok.featuregatelab;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Fragment;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.UiCapture;
import app.morphe.extension.tiktok.settings.SettingsPagesTest.PageActivity;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w480dp-h960dp-night-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FeatureGatePagesTest {
    @Before public void resetSharedState() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        FeatureGateCatalog.awaitForTests();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        FeatureGateCatalog.resetForTests();
        FeatureGateLabSession.resetForTests();
        FeatureGateLabUndo.resetForTests();
        SettingsManagerObservationRecorder.clear();
    }

    @Test public void darkLabSearchAndOverrideEditorWork() throws Exception { exercise("dark"); }
    @Test @Config(qualifiers = "w480dp-h960dp-notnight-mdpi")
    public void lightLabSearchAndOverrideEditorWork() throws Exception { exercise("light"); }

    /**
     * The state column on the right of a Lab row held three stacked 12sp labels in a fixed
     * 116dp. Density scales that number; text size does not, so at double text size the
     * labels have twice the width to fit into the same box. An ellipsis in a value the reader
     * came to the Lab to read is the whole point of the screen going missing.
     */
    @Test @Config(qualifiers = "w360dp-h640dp-night-mdpi", fontScale = 2)
    public void aLabRowKeepsItsStateColumnReadableAtDoubleTextSize() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            // Without this the rest of the test proves nothing: it would be measuring a row
            // at ordinary text size and finding, correctly, that nothing is cut off.
            assertEquals("the font scale did not take",
                    2f, activity.getResources().getConfiguration().fontScale, 0.01f);
            FeatureGateLabStore.resetAllLabData();
            FeatureGateLabSession.begin();
            // A gate that has never been read, which is the row that says "No current value",
            // and the state the item names.
            var entry = new FeatureGateCatalog.Entry("3p_login_optimization", "3p Login Optimization",
                    "abmock", "STRING", true, false, List.of(), List.of(), List.of(), "", "", false, null, null);
            var cached = FeatureGateCatalog.class.getDeclaredField("cachedSnapshot");
            cached.setAccessible(true);
            cached.set(null, new FeatureGateCatalog.Snapshot(List.of(entry), Map.of(entry.identity(), entry), 0, 0, true));
            FeatureGateLabStore.saveRule("abmock", entry.key, "STRING", "control_group_b", false);
            FeatureGateLabFragment lab = new FeatureGateLabFragment();
            attach(activity, lab);
            var tabs = FeatureGateLabFragment.class.getDeclaredField("viewTabs");
            tabs.setAccessible(true);
            ((ViewGroup) tabs.get(lab)).getChildAt(1).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            ListView list = find(lab.getView(), ListView.class);
            assertEquals(1, list.getAdapter().getCount());
            View row = list.getAdapter().getView(0, null, list);
            int width = View.MeasureSpec.makeMeasureSpec(
                    activity.getResources().getDisplayMetrics().widthPixels,
                    View.MeasureSpec.EXACTLY);
            row.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());

            List<TextView> shown = labels(row);
            assertTrue("no labels were measured", shown.size() >= 3);
            assertTrue("the row is not in the state the item describes: "
                            + shown.stream().map(label -> label.getText().toString())
                            .collect(java.util.stream.Collectors.joining(" | ")),
                    shown.stream().anyMatch(label -> "Override off".contentEquals(label.getText()))
                            && shown.stream().anyMatch(label ->
                            label.getText().toString().startsWith("Saved ")));
            for (TextView label : shown) {
                android.text.Layout laidOut = label.getLayout();
                assertNotNull("\"" + label.getText() + "\" was never laid out", laidOut);
                for (int line = 0; line < laidOut.getLineCount(); line++) {
                    assertEquals("\"" + label.getText() + "\" is cut off at double text size",
                            0, laidOut.getEllipsisCount(line));
                }
                // A label wider than the box it sits in is clipped whether or not the
                // ellipsis machinery ran, which is what a fixed width column does to a
                // label that has no room to shorten.
                assertTrue("\"" + label.getText() + "\" is " + laidOut.getWidth()
                                + "px of text in a " + label.getWidth() + "px label",
                        label.getWidth() >= laidOut.getWidth());
            }
            UiCapture.save(row, "pages/dark/lab-row-large-text.png");
        }
    }

    private static List<TextView> labels(View view) {
        List<TextView> found = new java.util.ArrayList<>();
        if (view instanceof TextView) {
            TextView label = (TextView) view;
            // The key line is meant to shorten; it is an identifier, shown middle-truncated.
            if (label.getVisibility() == View.VISIBLE && label.length() > 0
                    && label.getEllipsize() != android.text.TextUtils.TruncateAt.MIDDLE) {
                found.add(label);
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                found.addAll(labels(group.getChildAt(index)));
            }
        }
        return found;
    }

    private void exercise(String theme) throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            FeatureGateLabStore.resetAllLabData();
            FeatureGateLabSession.begin();
            var entry = new FeatureGateCatalog.Entry("3p_login_optimization", "3p Login Optimization",
                    "abmock", "INT", true, true, List.of("0", "1"), List.of(), List.of(), "", "", false, null, null);
            var cached = FeatureGateCatalog.class.getDeclaredField("cachedSnapshot");
            cached.setAccessible(true);
            cached.set(null, new FeatureGateCatalog.Snapshot(List.of(entry), Map.of(entry.identity(), entry), 0, 0, true));
            FeatureGateLabFragment lab = new FeatureGateLabFragment();
            attach(activity, lab);
            var tabs = FeatureGateLabFragment.class.getDeclaredField("viewTabs");
            tabs.setAccessible(true);
            ((ViewGroup) tabs.get(lab)).getChildAt(1).performClick();
            UiCapture.save(lab.getView(), "pages/" + theme + "/lab.png");
            EditText search = find(lab.getView(), EditText.class);
            search.setText("no-such-gate-12345");
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
            ListView list = find(lab.getView(), ListView.class);
            assertEquals(0, list.getAdapter().getCount());
            UiCapture.save(lab.getView(), "pages/" + theme + "/lab-empty.png");
            search.setText("");
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
            assertEquals(1, list.getAdapter().getCount());
            FeatureGateDetailFragment detail = FeatureGateDetailFragment.forEntry("abmock", entry.key, "INT");
            attach(activity, detail);
            UiCapture.save(detail.getView(), "pages/" + theme + "/gate_details.png");
            assertFalse(find(detail.getView(), Switch.class).isEnabled());
            FeatureGateLabStore.setMasterEnabled(true);
            detail = FeatureGateDetailFragment.forEntry("abmock", entry.key, "INT");
            attach(activity, detail);
            Switch control = find(detail.getView(), Switch.class);
            assertTrue(control.isEnabled());
            control.performClick();
            // Saving an override goes through the journal now, off this thread and back.
            Utils.awaitBackgroundTasksForTests();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(FeatureGateLabStore.rule("abmock", entry.key, "INT").enabled);
            UiCapture.save(detail.getView(), "pages/" + theme + "/gate-details-enabled.png");
            attach(activity, FeatureGateDetailFragment.forEntry("abmock", "missing", "INT"));
            UiCapture.save(activity.getFragmentManager().findFragmentById(android.R.id.content).getView(),
                    "pages/" + theme + "/gate-details-unavailable.png");
        }
    }
    private static void attach(Activity activity, Fragment fragment) {
        activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
