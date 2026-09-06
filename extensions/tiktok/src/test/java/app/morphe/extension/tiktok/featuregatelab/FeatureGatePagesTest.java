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
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.UiCapture;
import app.morphe.extension.tiktok.settings.SettingsPagesTest.PageActivity;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w480dp-h960dp-night-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FeatureGatePagesTest {
    @Test public void darkLabSearchAndOverrideEditorWork() throws Exception { exercise("dark"); }
    @Test @Config(qualifiers = "w480dp-h960dp-notnight-mdpi")
    public void lightLabSearchAndOverrideEditorWork() throws Exception { exercise("light"); }

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
