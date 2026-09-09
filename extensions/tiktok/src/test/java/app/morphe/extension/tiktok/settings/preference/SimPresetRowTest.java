package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.preference.PreferenceActivity;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * What the SIM preset row says underneath itself.
 *
 * <p>Three answers: the preset it recognises, nothing chosen at all, and details that match no
 * preset. The row is 313 lines and none of them were reached by a test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@SuppressWarnings("deprecation")
public class SimPresetRowTest {
    public static final class TestActivity extends PreferenceActivity {}

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.SIM_SPOOF_ISO.resetToDefault();
        Settings.SIMSPOOF_MCCMNC.resetToDefault();
        Settings.SIMSPOOF_OP_NAME.resetToDefault();
    }

    @Test
    public void theRowNamesThePresetItRecognises() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            SimPresetPreference row = build(controller.get());

            // The defaults are a real preset, so the row should name it rather than call it custom.
            row.refreshSummary(
                    Settings.SIM_SPOOF_ISO.defaultValue,
                    Settings.SIMSPOOF_MCCMNC.defaultValue,
                    Settings.SIMSPOOF_OP_NAME.defaultValue);
            String named = row.getSummary().toString();
            assertEquals("(" + Settings.SIMSPOOF_MCCMNC.defaultValue + ")",
                    named.substring(named.lastIndexOf('(')));
        }
    }

    @Test
    public void emptyDetailsReadAsNothingChosenAndAMixtureReadsAsCustom() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            SimPresetPreference row = build(controller.get());

            row.refreshSummary("", "", "");
            assertEquals("No preset selected", row.getSummary().toString());

            row.refreshSummary("us", "310260", "Not a real operator");
            assertEquals("Custom SIM details", row.getSummary().toString());
        }
    }

    /**
     * The dialog behind the row, on a German phone.
     *
     * <p>Its title, its helper sentence and the search box were built with plain English
     * strings, so they went out in English however many tables held them: two of them had a
     * German row already and simply never asked for it.
     */
    @Test
    @Config(sdk = 28, qualifiers = "de-rDE")
    public void thePresetDialogReadsGermanOnAGermanPhone() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Utils.setContext(controller.get());
            SimPresetPreference row = build(controller.get());

            java.lang.reflect.Method show =
                    SimPresetPreference.class.getDeclaredMethod("showPresetDialog");
            show.setAccessible(true);
            show.invoke(row);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("the preset dialog did not open", dialog);
            List<String> shown = new ArrayList<>();
            collectText(dialog.getWindow().getDecorView(), shown);

            assertTrue("the dialog title is still English: " + shown,
                    shown.contains("SIM-Landesvorlage") || shown.contains(germanFor(
                            "SIM country preset")));
            assertTrue("the helper sentence is still English: " + shown,
                    shown.contains(germanFor("Choose a preset to fill the SIM details.")));
            assertTrue("the search box hint is still English: " + shown,
                    shown.contains(germanFor("Search countries or operators")));
        }
    }

    /** What the shipped table says, so the test does not repeat the translation. */
    private static String germanFor(String english) {
        String translated = app.morphe.extension.tiktok.settings.L10n.t(
                RuntimeEnvironment.getApplication().createConfigurationContext(
                        germanConfiguration()), english);
        assertNotEquals("no German row for: " + english, english, translated);
        return translated;
    }

    private static android.content.res.Configuration germanConfiguration() {
        android.content.res.Configuration german = new android.content.res.Configuration(
                RuntimeEnvironment.getApplication().getResources().getConfiguration());
        german.setLocale(java.util.Locale.GERMANY);
        return german;
    }

    private static void collectText(android.view.View view, List<String> found) {
        if (view instanceof android.widget.TextView) {
            android.widget.TextView label = (android.widget.TextView) view;
            if (label.getText() != null && label.getText().length() > 0) {
                found.add(label.getText().toString());
            }
            if (label.getHint() != null && label.getHint().length() > 0) {
                found.add(label.getHint().toString());
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectText(group.getChildAt(index), found);
            }
        }
    }

    /**
     * The diagnostics picker on a German phone. It lives in the shared library, which cannot
     * reach a translation table because it is shared with bundles that carry none, so this
     * bundle hands it the words through the hooks the row already used for its own title.
     */
    @Test
    @Config(sdk = 28, qualifiers = "de-rDE")
    public void theDiagnosticsPickerReadsGermanOnAGermanPhone() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            // body below
            Utils.setContext(controller.get());
            // The page only carries this row on a bundle with the diagnostics patch in it.
            app.morphe.extension.tiktok.settings.SettingsStatus.diagnosticsEnabled = true;
            android.preference.PreferenceScreen screen =
                    controller.get().getPreferenceManager().createPreferenceScreen(
                            controller.get());
            new app.morphe.extension.tiktok.settings.preference.categories
                    .DebugPreferenceCategory(controller.get(), screen);

            android.preference.Preference row = screen.findPreference("action_included_diagnostics");
            assertNotNull("the diagnostics picker is not on the page", row);
            row.getOnPreferenceClickListener().onPreferenceClick(row);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("the picker did not open", dialog);
            var shadow = org.robolectric.Shadows.shadowOf((android.app.AlertDialog) dialog);
            assertEquals("the picker title is still English",
                    germanFor("Include diagnostic events"), shadow.getTitle());
            java.util.List<String> items = new ArrayList<>();
            for (CharSequence item : shadow.getItems()) items.add(item.toString());
            assertTrue("the picker options are still English: " + items,
                    items.contains(germanFor("All events")));
            assertEquals("the Apply button is still English",
                    germanFor("Apply"),
                    ((android.app.AlertDialog) dialog)
                            .getButton(android.app.AlertDialog.BUTTON_POSITIVE).getText()
                            .toString());
        } finally {
            app.morphe.extension.tiktok.settings.SettingsStatus.diagnosticsEnabled = false;
        }
    }

    /**
     * The sentence under the diagnostics picker, in German and in English.
     *
     * <p>The shared library lower-cased each kind before joining it into the sentence, which
     * reads right for English and wrong for a language that capitalises its nouns:
     * Einstellungen came out as einstellungen. Overriding that to leave the label alone moved
     * the fault rather than fixing it, because every table capitalises these labels and only
     * German wanted them that way mid-sentence. The list comes after a colon now, so each
     * language keeps its own capital, and both phones are checked here: a German-only test is
     * what let the English regression through.
     */
    @Test
    @Config(sdk = 28, qualifiers = "de-rDE")
    public void theDiagnosticsSummaryKeepsTheCapitalsGermanNounsHave() throws Exception {
        assertSummaryKeepsItsCapitals(SimPresetRowTest::germanFor);
    }

    @Test
    @Config(sdk = 28, qualifiers = "en-rUS")
    public void theDiagnosticsSummaryKeepsTheCapitalsInEnglishToo() throws Exception {
        // No table answers for English, so the label is its own key, capital and all.
        assertSummaryKeepsItsCapitals(english -> english);
    }

    private static void assertSummaryKeepsItsCapitals(
            java.util.function.UnaryOperator<String> translate) throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Utils.setContext(controller.get());
            app.morphe.extension.tiktok.settings.SettingsStatus.diagnosticsEnabled = true;
            app.morphe.extension.shared.settings.BaseSettings.DEBUG_LOG_FILTERS.save(
                    "settings,errors");
            android.preference.PreferenceScreen screen =
                    controller.get().getPreferenceManager().createPreferenceScreen(
                            controller.get());
            new app.morphe.extension.tiktok.settings.preference.categories
                    .DebugPreferenceCategory(controller.get(), screen);

            android.preference.Preference row = screen.findPreference("action_included_diagnostics");
            assertNotNull("the diagnostics picker is not on the page", row);
            String summary = String.valueOf(row.getSummary());

            for (String english : new String[]{"Settings", "Errors"}) {
                String label = translate.apply(english);
                assertTrue("the kind is missing from the sentence: " + summary,
                        summary.contains(label));
                assertFalse("the kind was lower-cased on its way into the sentence: " + summary,
                        summary.contains(label.toLowerCase(java.util.Locale.ROOT)));
            }
        } finally {
            app.morphe.extension.tiktok.settings.SettingsStatus.diagnosticsEnabled = false;
            app.morphe.extension.shared.settings.BaseSettings.DEBUG_LOG_FILTERS.resetToDefault();
        }
    }

    private static SimPresetPreference build(Context context) {
        return new SimPresetPreference(
                context,
                new InputTextPreference(context, "ISO", "", Settings.SIM_SPOOF_ISO),
                new InputTextPreference(context, "MCC/MNC", "", Settings.SIMSPOOF_MCCMNC),
                new InputTextPreference(context, "Operator", "", Settings.SIMSPOOF_OP_NAME));
    }
}
